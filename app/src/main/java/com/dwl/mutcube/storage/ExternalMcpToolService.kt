package com.dwl.mutcube.storage

import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.extensions.ExtensionPermission
import com.dwl.mutcube.core.extensions.McpClientService
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.extensions.McpAuthType
import com.dwl.mutcube.core.extensions.McpServerStore
import com.dwl.mutcube.core.extensions.McpTool
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.feature.chat.ExternalToolResult
import com.dwl.mutcube.feature.chat.ExternalToolService
import org.json.JSONObject
import com.dwl.mutcube.core.extensions.ExtensionAuditEvent
import com.dwl.mutcube.core.extensions.ExtensionAuditStore
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ExternalMcpToolService(
    private val store: McpServerStore,
    private val client: McpClientService,
    private val credentials: CredentialStore,
    private val oauthManager: McpOAuthManager,
    private val auditStore: ExtensionAuditStore,
    private val approvalCoordinator: ToolApprovalCoordinator,
) : ExternalToolService {
    override suspend fun definitions(): List<ModelToolDefinition> = store.read().flatMap { server ->
        if (!server.enabled) return@flatMap emptyList()
        val tools = if (server.tools.any { it.enabled && it.permission != ExtensionPermission.DENY && !it.hasJsonSchema() }) {
            try {
                withTimeoutOrNull(5_000) {
                    val token = when (server.authType) {
                        McpAuthType.NONE -> null
                        McpAuthType.BEARER_TOKEN -> credentials.read("mcp.${server.id}")
                        McpAuthType.OAUTH2 -> oauthManager.freshAccessToken(server)
                    }
                    val refreshed = client.inspect(
                        server,
                        token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty(),
                    )
                    val merged = store.read().map { current ->
                        if (current.id == server.id) mergeMcpDiscovery(server, current, refreshed) else current
                    }
                    store.write(merged)
                    merged.firstOrNull { it.id == server.id && it.enabled }?.tools.orEmpty()
                } ?: server.tools
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A stale or unreachable MCP server must not prevent ordinary chat generation.
                server.tools
            }
        } else server.tools
        tools.filter { it.enabled && it.permission != ExtensionPermission.DENY && it.hasJsonSchema() }
            .map { tool -> ModelToolDefinition(tool.externalName(server.id), tool.description.orEmpty(), tool.inputSchemaJson) }
    }

    override suspend fun execute(call: ModelToolCall): ExternalToolResult {
        val server = store.read().firstOrNull { candidate ->
            candidate.tools.any { it.externalName(candidate.id) == call.name }
        }
            ?: return ExternalToolResult("{\"error\":\"MCP 服务不存在\"}", true)
        val tool = server.tools.firstOrNull { it.externalName(server.id) == call.name }
            ?: return ExternalToolResult("{\"error\":\"MCP 工具不存在或已停用\"}", true)
        val started = System.nanoTime()
        var approvalWaitNanos = 0L
        var decision = ToolApprovalDecision.ALLOW_ONCE
        if (!server.enabled || !tool.enabled || tool.permission == ExtensionPermission.DENY) {
            return rejected(server, tool, call, started, "工具没有执行权限")
        }
        if (tool.permission == ExtensionPermission.ASK) {
            val approval = approvalCoordinator.requestTimed(server.name, tool.name, tool.description, call.argumentsJson)
            approvalWaitNanos = approval.waitDurationNanos
            decision = approval.decision
            if (decision == ToolApprovalDecision.DENY) {
                return rejected(server, tool, call, started, "用户拒绝了工具调用", approvalWaitNanos)
            }
        }
        // The user can change or revoke a server while its approval dialog is open.
        val currentServers = store.read()
        val currentServer = currentServers.firstOrNull { it.id == server.id }
        val currentTool = currentServer?.tools?.firstOrNull { it.name == tool.name }
        if (!isMcpInvocationStillAuthorized(server, tool, currentServer, currentTool)) {
            return rejected(server, tool, call, started, "服务或工具配置已变更，请重新发起调用", approvalWaitNanos)
        }
        if (decision == ToolApprovalDecision.ALWAYS_ALLOW) {
            store.write(currentServers.map { current ->
                if (current.id != server.id) current else current.copy(tools = current.tools.map {
                    if (it.name == tool.name) it.copy(permission = ExtensionPermission.ALLOW) else it
                })
            })
        }
        val result = runCatching {
            withTimeoutOrNull(60_000) {
                val token = when (server.authType) {
                    McpAuthType.NONE -> null
                    McpAuthType.BEARER_TOKEN -> credentials.read("mcp.${server.id}")
                    McpAuthType.OAUTH2 -> oauthManager.freshAccessToken(server)
                }
                client.call(server, tool, call.argumentsJson, token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty())
            } ?: error("MCP 工具调用超时")
        }.fold(
            onSuccess = { ExternalToolResult(it.text, it.isError, approvalWaitNanos) },
            onFailure = {
                if (it is CancellationException) throw it
                ExternalToolResult("{\"error\":${JSONObject.quote(it.message ?: "MCP 调用失败")}}", true, approvalWaitNanos)
            },
        )
        auditStore.append(
            ExtensionAuditEvent(
                id = UUID.randomUUID().toString(), serverId = server.id, capabilityName = tool.name,
                argumentsPreview = call.argumentsJson, resultPreview = result.output,
                succeeded = !result.isError, occurredAt = System.currentTimeMillis(),
                durationMs = (System.nanoTime() - started - approvalWaitNanos).coerceAtLeast(0) / 1_000_000,
            ),
        )
        return result
    }

    private suspend fun rejected(
        server: McpServer,
        tool: com.dwl.mutcube.core.extensions.McpTool,
        call: ModelToolCall,
        started: Long,
        reason: String,
        approvalWaitNanos: Long = 0,
    ): ExternalToolResult {
        val result = ExternalToolResult("{\"error\":${JSONObject.quote(reason)}}", true, approvalWaitNanos)
        auditStore.append(
            ExtensionAuditEvent(
                id = UUID.randomUUID().toString(),
                serverId = server.id,
                capabilityName = tool.name,
                argumentsPreview = call.argumentsJson,
                resultPreview = reason,
                succeeded = false,
                occurredAt = System.currentTimeMillis(),
                durationMs = (System.nanoTime() - started - approvalWaitNanos).coerceAtLeast(0) / 1_000_000,
            ),
        )
        return result
    }
}

internal fun isMcpInvocationStillAuthorized(
    originalServer: McpServer,
    originalTool: McpTool,
    currentServer: McpServer?,
    currentTool: McpTool?,
): Boolean = currentServer != null && currentTool != null && currentServer.enabled && currentTool.enabled &&
    currentServer.id == originalServer.id && currentTool.name == originalTool.name &&
    currentTool.permission != ExtensionPermission.DENY && currentServer.endpoint == originalServer.endpoint &&
    currentServer.authType == originalServer.authType && currentServer.oauth == originalServer.oauth &&
    currentTool.inputSchemaJson == originalTool.inputSchemaJson

internal fun mergeMcpDiscovery(original: McpServer, current: McpServer, discovered: List<McpTool>): McpServer {
    if (current.id != original.id || current.endpoint != original.endpoint ||
        current.authType != original.authType || current.oauth != original.oauth) return current
    return current.copy(tools = discovered.map { remote ->
        val latest = current.tools.firstOrNull { it.name == remote.name }
        val permission = when {
            latest == null -> ExtensionPermission.ASK
            latest.permission == ExtensionPermission.DENY -> ExtensionPermission.DENY
            latest.inputSchemaJson != remote.inputSchemaJson -> ExtensionPermission.ASK
            else -> latest.permission
        }
        remote.copy(enabled = latest?.enabled ?: true, permission = permission)
    })
}

internal fun McpTool.hasJsonSchema() = runCatching { Json.parseToJsonElement(inputSchemaJson).jsonObject }.isSuccess
internal fun McpTool.externalName(serverId: String): String = "mcp_" +
    MessageDigest.getInstance("SHA-256").digest("$serverId\u0000$name".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }.take(60)
