package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.extensions.ExtensionPermission
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.extensions.McpServerStore
import com.dwl.mutcube.core.extensions.McpTool
import com.dwl.mutcube.core.extensions.McpAuthType
import com.dwl.mutcube.core.extensions.McpOAuthConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AndroidMcpServerStore(context: Context) : McpServerStore {
    private val preferences = context.applicationContext.getSharedPreferences("mcp_servers", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val servers = state.asStateFlow()

    override suspend fun read() = state.value

    override suspend fun write(servers: List<McpServer>) {
        val normalized = servers.distinctBy { it.id }.map { server ->
            server.copy(name = server.name.trim().take(60), endpoint = server.endpoint.trim())
        }
        val array = JSONArray()
        normalized.forEach { server -> array.put(server.toJson()) }
        check(preferences.edit().putString("servers", array.toString()).commit()) { "无法保存 MCP 配置" }
        state.value = normalized
    }

    private fun load(): List<McpServer> = runCatching {
        val array = JSONArray(preferences.getString("servers", "[]"))
        List(array.length()) { index -> array.getJSONObject(index).toServer() }
    }.getOrDefault(emptyList())
}

private fun McpServer.toJson() = JSONObject()
    .put("id", id).put("name", name).put("endpoint", endpoint).put("enabled", enabled).put("authType", authType.name)
    .put("oauth", oauth?.let { JSONObject().put("discoveryEndpoint", it.discoveryEndpoint).put("clientId", it.clientId).put("scopes", it.scopes) })
    .put("tools", JSONArray().also { array -> tools.forEach { array.put(it.toJson()) } })

private fun McpTool.toJson() = JSONObject().put("name", name).put("description", description)
    .put("schema", inputSchemaJson).put("permission", permission.name).put("enabled", enabled)

private fun JSONObject.toServer() = McpServer(
    id = getString("id"), name = getString("name"), endpoint = getString("endpoint"),
    enabled = optBoolean("enabled", true),
    authType = runCatching { McpAuthType.valueOf(optString("authType", McpAuthType.NONE.name)) }.getOrDefault(McpAuthType.NONE),
    oauth = optJSONObject("oauth")?.let { McpOAuthConfig(it.getString("discoveryEndpoint"), it.getString("clientId"), it.optString("scopes", "openid profile")) },
    tools = optJSONArray("tools")?.let { array -> List(array.length()) { array.getJSONObject(it).toTool() } }.orEmpty(),
)

private fun JSONObject.toTool() = McpTool(
    name = getString("name"), description = optString("description").takeIf(String::isNotBlank),
    inputSchemaJson = optString("schema", "{\"type\":\"object\"}"),
    permission = runCatching { ExtensionPermission.valueOf(optString("permission")) }.getOrDefault(ExtensionPermission.ASK),
    enabled = optBoolean("enabled", true),
)
