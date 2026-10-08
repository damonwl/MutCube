package com.dwl.mutcube.core.extensions

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.encodeToString

class McpClientService {
    private val sessionLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, Session>()
    private fun lock(serverId: String) = sessionLocks.computeIfAbsent(serverId) { Mutex() }

    suspend fun inspect(server: McpServer, headers: Map<String, String> = emptyMap()): List<McpTool> =
        session(server, headers).client.listTools().tools.map { tool ->
            val previous = server.tools.firstOrNull { it.name == tool.name }
            McpTool(
                name = tool.name,
                description = tool.description,
                inputSchemaJson = encodeToolSchema(tool.inputSchema),
                permission = previous?.permission ?: ExtensionPermission.ASK,
                enabled = previous?.enabled ?: true,
            )
        }

    suspend fun call(
        server: McpServer,
        tool: McpTool,
        argumentsJson: String,
        headers: Map<String, String> = emptyMap(),
    ): McpCallResult {
        require(server.enabled) { "MCP 服务已停用" }
        require(tool.enabled) { "工具已停用" }
        require(tool.permission != ExtensionPermission.DENY) { "工具权限被拒绝" }
        val arguments = Json.parseToJsonElement(argumentsJson).jsonObject.mapValues { it.value.toKotlinValue() }
        val result = session(server, headers).client.callTool(tool.name, arguments)
        return McpCallResult(
            text = result.content.joinToString("\n") { content ->
                (content as? TextContent)?.text ?: content.toString()
            },
            isError = result.isError == true,
            structuredJson = result.structuredContent?.toString(),
        )
    }

    suspend fun disconnect(serverId: String) {
        lock(serverId).withLock { sessions.remove(serverId)?.close() }
    }

    suspend fun disconnectAll() {
        sessions.keys.toList().forEach { disconnect(it) }
    }

    private suspend fun session(server: McpServer, headers: Map<String, String>): Session = lock(server.id).withLock {
        sessions[server.id]?.takeIf { it.endpoint == server.endpoint && it.headers == headers }?.let { return@withLock it }
        sessions.remove(server.id)?.close()
        val http = HttpClient(OkHttp) { install(SSE) }
        val client = Client(Implementation(name = "MutCube", version = "0.1.0"))
        val transport = StreamableHttpClientTransport(http, server.endpoint) {
            headers { headers.forEach { (name, value) -> append(name, value) } }
        }
        try {
            withTimeout(20_000) { client.connect(transport) }
        } catch (error: Exception) {
            http.close()
            throw error
        }
        Session(server.endpoint, headers, http, client).also { sessions[server.id] = it }
    }

    private data class Session(
        val endpoint: String,
        val headers: Map<String, String>,
        val http: HttpClient,
        val client: Client,
    ) {
        suspend fun close() {
            runCatching { client.close() }
            http.close()
        }
    }
}

internal fun encodeToolSchema(schema: ToolSchema): String = Json.encodeToString(schema)

data class McpCallResult(val text: String, val isError: Boolean, val structuredJson: String?)

internal fun kotlinx.serialization.json.JsonElement.toKotlinValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { it.value.toKotlinValue() }
    is JsonArray -> map { it.toKotlinValue() }
    is JsonPrimitive -> if (isString) contentOrNull else booleanOrNull ?: longOrNull ?: doubleOrNull ?: contentOrNull
}
