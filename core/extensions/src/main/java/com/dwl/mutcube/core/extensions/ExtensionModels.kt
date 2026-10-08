package com.dwl.mutcube.core.extensions

import kotlinx.coroutines.flow.StateFlow

enum class ExtensionPermission { ASK, ALLOW, DENY }

enum class McpConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
enum class McpAuthType { NONE, BEARER_TOKEN, OAUTH2 }

data class McpOAuthConfig(
    val discoveryEndpoint: String,
    val clientId: String,
    val scopes: String = "openid profile",
)

data class McpTool(
    val name: String,
    val description: String?,
    val inputSchemaJson: String,
    val permission: ExtensionPermission = ExtensionPermission.ASK,
    val enabled: Boolean = true,
)

data class McpServer(
    val id: String,
    val name: String,
    val endpoint: String,
    val enabled: Boolean = true,
    val authType: McpAuthType = McpAuthType.NONE,
    val oauth: McpOAuthConfig? = null,
    val connectionState: McpConnectionState = McpConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val tools: List<McpTool> = emptyList(),
)

data class ExtensionAuditEvent(
    val id: String,
    val serverId: String?,
    val capabilityName: String,
    val argumentsPreview: String,
    val resultPreview: String?,
    val succeeded: Boolean,
    val occurredAt: Long,
    val durationMs: Long = 0,
)

interface ExtensionAuditStore {
    val events: StateFlow<List<ExtensionAuditEvent>>
    suspend fun append(event: ExtensionAuditEvent)
    suspend fun clear()
}

interface McpServerStore {
    val servers: StateFlow<List<McpServer>>
    suspend fun read(): List<McpServer>
    suspend fun write(servers: List<McpServer>)
}
