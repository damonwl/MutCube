package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.extensions.ExtensionAuditEvent
import com.dwl.mutcube.core.extensions.ExtensionAuditStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class AndroidExtensionAuditStore(context: Context) : ExtensionAuditStore {
    private val preferences = context.applicationContext.getSharedPreferences("extension_audit", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    private val mutex = Mutex()
    override val events = state.asStateFlow()

    override suspend fun append(event: ExtensionAuditEvent) = mutex.withLock {
        val updated = (listOf(event.sanitized()) + state.value).take(200)
        persist(updated)
    }

    override suspend fun clear() = mutex.withLock { persist(emptyList()) }

    private fun persist(events: List<ExtensionAuditEvent>) {
        val array = JSONArray()
        events.forEach { event -> array.put(JSONObject().put("id", event.id).put("serverId", event.serverId)
            .put("capability", event.capabilityName).put("arguments", event.argumentsPreview).put("result", event.resultPreview)
            .put("succeeded", event.succeeded).put("occurredAt", event.occurredAt).put("durationMs", event.durationMs)) }
        check(preferences.edit().putString("events", array.toString()).commit())
        state.value = events
    }

    private fun load() = runCatching {
        val array = JSONArray(preferences.getString("events", "[]"))
        List(array.length()) { index -> array.getJSONObject(index).let { row -> ExtensionAuditEvent(
            row.getString("id"), row.optNullableString("serverId"), row.getString("capability"),
            row.optString("arguments"), row.optNullableString("result"), row.optBoolean("succeeded"),
            row.optLong("occurredAt"), row.optLong("durationMs"),
        ) } }
    }.getOrDefault(emptyList())
}

private fun ExtensionAuditEvent.sanitized() = copy(
    argumentsPreview = SensitiveTextRedactor.redact(argumentsPreview).take(1_000),
    resultPreview = resultPreview?.let(SensitiveTextRedactor::redact)?.take(1_000),
)

private fun JSONObject.optNullableString(key: String) =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
