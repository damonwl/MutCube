package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.extensions.ExtensionPermission
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.extensions.McpTool
import com.dwl.mutcube.core.extensions.McpAuthType
import com.dwl.mutcube.core.extensions.McpOAuthConfig
import com.dwl.mutcube.core.extensions.ExtensionAuditEvent
import java.util.UUID

@Composable
fun McpSettingsPage(
    servers: List<McpServer>, busyServerId: String?, onBack: () -> Unit,
    onSaveServer: (McpServer, String) -> Unit, onDeleteServer: (McpServer) -> Unit,
    onInspect: (McpServer) -> Unit, onUpdateTool: (McpServer, McpTool) -> Unit,
    onAuthorize: (McpServer) -> Unit,
    auditEvents: List<ExtensionAuditEvent>, onClearAudit: () -> Unit,
) {
    var editor by remember { mutableStateOf<McpServer?>(null) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("MCP 与工具", onBack)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (servers.isEmpty()) item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有 MCP 服务", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "添加 Streamable HTTP 服务后，可发现并按工具授权。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(servers, key = McpServer::id) { server ->
                    McpServerCard(
                        server = server, busy = busyServerId == server.id, onSave = onSaveServer,
                        onDelete = onDeleteServer, onInspect = onInspect, onUpdateTool = onUpdateTool,
                        onEdit = { editor = server }, onAuthorize = onAuthorize,
                    )
                }
                if (auditEvents.isNotEmpty()) item(key = "audit") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("工具调用记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearAudit) { Text("清空") }
                            }
                            auditEvents.take(20).forEach { event ->
                                Text(
                                    "${if (event.succeeded) "成功" else "失败"} · ${event.capabilityName} · ${event.durationMs}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (event.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Text(event.argumentsPreview, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
        }
        Button(
            onClick = { editor = McpServer(UUID.randomUUID().toString(), "", "") },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        ) { Icon(Icons.Rounded.Add, null); Text("添加 MCP 服务", Modifier.padding(start = 8.dp)) }
    }
    editor?.let { server ->
        McpServerEditor(server, { editor = null }) { value, token -> onSaveServer(value, token); editor = null }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer, busy: Boolean, onSave: (McpServer, String) -> Unit, onDelete: (McpServer) -> Unit,
    onInspect: (McpServer) -> Unit, onUpdateTool: (McpServer, McpTool) -> Unit, onEdit: () -> Unit,
    onAuthorize: (McpServer) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(server.enabled, { onSave(server.copy(enabled = it), "") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = { onInspect(server) }, enabled = !busy && server.enabled) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Text("发现工具", Modifier.padding(start = 6.dp))
                }
                if (server.authType == McpAuthType.OAUTH2) TextButton(onClick = { onAuthorize(server) }) { Text("授权") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(server) }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
            }
            server.tools.forEach { tool ->
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(tool.name); tool.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) } }
                        Switch(tool.enabled, { onUpdateTool(server, tool.copy(enabled = it)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ExtensionPermission.ASK to "询问", ExtensionPermission.ALLOW to "允许", ExtensionPermission.DENY to "拒绝").forEach { (value, label) ->
                            FilterChip(tool.permission == value, { onUpdateTool(server, tool.copy(permission = value)) }, { Text(label) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerEditor(initial: McpServer, onDismiss: () -> Unit, onSave: (McpServer, String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var endpoint by remember(initial) { mutableStateOf(initial.endpoint) }
    var token by remember(initial) { mutableStateOf("") }
    var authType by remember(initial) { mutableStateOf(initial.authType) }
    var discoveryEndpoint by remember(initial) { mutableStateOf(initial.oauth?.discoveryEndpoint.orEmpty()) }
    var clientId by remember(initial) { mutableStateOf(initial.oauth?.clientId.orEmpty()) }
    var scopes by remember(initial) { mutableStateOf(initial.oauth?.scopes ?: "openid profile") }
    val valid = name.isNotBlank() && (authType != McpAuthType.OAUTH2 || (discoveryEndpoint.isNotBlank() && clientId.isNotBlank())) && runCatching {
        val uri = java.net.URI(endpoint.trim())
        uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
    }.getOrDefault(false)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial.name.isBlank()) "添加 MCP 服务" else "编辑 MCP 服务") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Streamable HTTP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(McpAuthType.NONE to "无认证", McpAuthType.BEARER_TOKEN to "Token", McpAuthType.OAUTH2 to "OAuth 2.0").forEach { (type, label) ->
                    FilterChip(authType == type, { authType = type }, { Text(label) })
                }
            }
            if (authType == McpAuthType.BEARER_TOKEN) OutlinedTextField(token, { token = it }, label = { Text("Bearer Token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            if (authType == McpAuthType.OAUTH2) {
                OutlinedTextField(discoveryEndpoint, { discoveryEndpoint = it }, label = { Text("OAuth Discovery URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                OutlinedTextField(scopes, { scopes = it }, label = { Text("Scopes（空格分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            Text("远程服务必须使用 HTTPS；模拟器可访问 10.0.2.2。Token 使用系统密钥库保存。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { TextButton(onClick = { onSave(initial.copy(name = name.trim(), endpoint = endpoint.trim(), authType = authType, oauth = if (authType == McpAuthType.OAUTH2) McpOAuthConfig(discoveryEndpoint.trim(), clientId.trim(), scopes.trim()) else null), token) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
