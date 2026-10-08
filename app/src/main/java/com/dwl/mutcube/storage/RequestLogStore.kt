package com.dwl.mutcube.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RequestLogEntry(
    val id: String,
    val startedAt: Long,
    val durationMs: Long,
    val modelId: String,
    val providerUrl: String,
    val protocol: String,
    val messageCount: Int,
    val inputCharacters: Int,
    val toolCount: Int,
    val responseCharacters: Int,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val error: String?,
)

data class RequestLogState(
    val enabled: Boolean = false,
    val entries: List<RequestLogEntry> = emptyList(),
)

fun interface RequestLogSink {
    fun append(entry: RequestLogEntry)
}

class RequestLogStore(context: Context) : RequestLogSink {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(load())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun setEnabled(enabled: Boolean) = persist(mutableState.value.copy(enabled = enabled))

    @Synchronized
    override fun append(entry: RequestLogEntry) {
        if (!mutableState.value.enabled) return
        persist(mutableState.value.copy(entries = (listOf(entry) + mutableState.value.entries).take(MAX_ENTRIES)))
    }

    @Synchronized
    fun clear() = persist(mutableState.value.copy(entries = emptyList()))

    private fun persist(state: RequestLogState) {
        val array = JSONArray()
        state.entries.forEach { entry ->
            array.put(
                JSONObject().put("id", entry.id).put("startedAt", entry.startedAt)
                    .put("durationMs", entry.durationMs).put("modelId", entry.modelId)
                    .put("providerUrl", entry.providerUrl).put("protocol", entry.protocol)
                    .put("messageCount", entry.messageCount).put("inputCharacters", entry.inputCharacters)
                    .put("toolCount", entry.toolCount).put("responseCharacters", entry.responseCharacters)
                    .put("inputTokens", entry.inputTokens).put("outputTokens", entry.outputTokens)
                    .put("error", entry.error),
            )
        }
        check(preferences.edit().putBoolean(KEY_ENABLED, state.enabled).putString(KEY_ENTRIES, array.toString()).commit())
        mutableState.value = state
    }

    private fun load(): RequestLogState = runCatching {
        val array = JSONArray(preferences.getString(KEY_ENTRIES, "[]").orEmpty())
        RequestLogState(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            entries = List(array.length()) { index ->
                val row = array.getJSONObject(index)
                RequestLogEntry(
                    id = row.getString("id"), startedAt = row.getLong("startedAt"),
                    durationMs = row.getLong("durationMs"), modelId = row.getString("modelId"),
                    providerUrl = row.getString("providerUrl"), protocol = row.getString("protocol"),
                    messageCount = row.getInt("messageCount"), inputCharacters = row.getInt("inputCharacters"),
                    toolCount = row.getInt("toolCount"), responseCharacters = row.getInt("responseCharacters"),
                    inputTokens = row.optLongOrNull("inputTokens"), outputTokens = row.optLongOrNull("outputTokens"),
                    error = row.optStringOrNull("error"),
                )
            },
        )
    }.getOrDefault(RequestLogState())

    companion object {
        const val PREFERENCES = "request_logs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}

private fun JSONObject.optLongOrNull(key: String) = if (isNull(key)) null else getLong(key)
private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else getString(key)
