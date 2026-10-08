package com.dwl.mutcube.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class QuickPrompt(val id: String, val title: String, val content: String)

data class ContextMode(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val active: Boolean = false,
)

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val content: String,
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true,
)

data class ContextLibraryState(
    val quickPrompts: List<QuickPrompt> = emptyList(),
    val modes: List<ContextMode> = emptyList(),
    val knowledgeEntries: List<KnowledgeEntry> = emptyList(),
)

class ContextLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(load())
    val state = mutableState.asStateFlow()

    fun read(): ContextLibraryState = mutableState.value

    fun saveQuickPrompt(prompt: QuickPrompt) = update { current ->
        current.copy(quickPrompts = current.quickPrompts.upsert(prompt) { it.id })
    }

    fun deleteQuickPrompt(id: String) = update { current ->
        current.copy(quickPrompts = current.quickPrompts.filterNot { it.id == id })
    }

    fun saveMode(mode: ContextMode) = update { current ->
        current.copy(
            modes = current.modes
                .map { if (mode.active) it.copy(active = false) else it }
                .upsert(mode) { it.id },
        )
    }

    fun deleteMode(id: String) = update { current ->
        current.copy(modes = current.modes.filterNot { it.id == id })
    }

    fun saveKnowledgeEntry(entry: KnowledgeEntry) = update { current ->
        current.copy(knowledgeEntries = current.knowledgeEntries.upsert(entry) { it.id })
    }

    fun deleteKnowledgeEntry(id: String) = update { current ->
        current.copy(knowledgeEntries = current.knowledgeEntries.filterNot { it.id == id })
    }

    /** Resolves bounded, user-controlled context for one request. Empty keyword lists mean always inject. */
    fun buildContextInstructions(latestUserText: String): List<String> =
        resolveContextInstructions(read(), latestUserText)

    private fun update(transform: (ContextLibraryState) -> ContextLibraryState) {
        val updated = transform(mutableState.value)
        val root = JSONObject()
            .put("quickPrompts", JSONArray().also { array ->
                updated.quickPrompts.forEach { prompt ->
                    array.put(JSONObject().put("id", prompt.id).put("title", prompt.title).put("content", prompt.content))
                }
            })
            .put("modes", JSONArray().also { array ->
                updated.modes.forEach { mode ->
                    array.put(
                        JSONObject().put("id", mode.id).put("name", mode.name)
                            .put("description", mode.description).put("instructions", mode.instructions)
                            .put("active", mode.active),
                    )
                }
            })
            .put("knowledgeEntries", JSONArray().also { array ->
                updated.knowledgeEntries.forEach { entry ->
                    array.put(
                        JSONObject().put("id", entry.id).put("title", entry.title)
                            .put("content", entry.content).put("keywords", JSONArray(entry.keywords))
                            .put("enabled", entry.enabled),
                    )
                }
            })
        check(preferences.edit().putString(KEY_STATE, root.toString()).commit())
        mutableState.value = updated
    }

    private fun load(): ContextLibraryState = runCatching {
        val root = JSONObject(preferences.getString(KEY_STATE, "{}").orEmpty())
        ContextLibraryState(
            quickPrompts = root.optJSONArray("quickPrompts").toList { row ->
                QuickPrompt(row.getString("id"), row.getString("title"), row.getString("content"))
            },
            modes = root.optJSONArray("modes").toList { row ->
                ContextMode(
                    row.getString("id"), row.getString("name"), row.optString("description"),
                    row.getString("instructions"), row.optBoolean("active", false),
                )
            }.let { modes ->
                var foundActive = false
                modes.map { mode ->
                    mode.copy(active = mode.active && !foundActive.also { if (mode.active) foundActive = true })
                }
            },
            knowledgeEntries = root.optJSONArray("knowledgeEntries").toList { row ->
                KnowledgeEntry(
                    row.getString("id"), row.getString("title"), row.getString("content"),
                    row.optJSONArray("keywords").toStringList(), row.optBoolean("enabled", true),
                )
            },
        )
    }.getOrDefault(ContextLibraryState())

    companion object {
        const val PREFERENCES = "context_library"
        private const val KEY_STATE = "state"
    }
}

internal fun resolveContextInstructions(snapshot: ContextLibraryState, latestUserText: String): List<String> {
    val normalizedInput = latestUserText.lowercase()
    return buildList {
        snapshot.modes.firstOrNull { it.active }?.let { mode ->
            add("当前对话模式：${mode.name}\n${mode.instructions.take(MAX_ENTRY_CHARACTERS)}")
        }
        snapshot.knowledgeEntries.asSequence()
            .filter { it.enabled }
            .filter { entry ->
                entry.keywords.isEmpty() || entry.keywords.any { it.lowercase() in normalizedInput }
            }
            .take(MAX_MATCHED_KNOWLEDGE)
            .forEach { entry -> add("知识条目：${entry.title}\n${entry.content.take(MAX_ENTRY_CHARACTERS)}") }
    }.boundedTo(MAX_TOTAL_CONTEXT_CHARACTERS)
}

private const val MAX_ENTRY_CHARACTERS = 8_000
private const val MAX_TOTAL_CONTEXT_CHARACTERS = 24_000
private const val MAX_MATCHED_KNOWLEDGE = 8

private fun <T> List<T>.upsert(value: T, id: (T) -> String): List<T> {
    val index = indexOfFirst { id(it) == id(value) }
    return if (index < 0) this + value else toMutableList().also { it[index] = value }
}

private fun <T> JSONArray?.toList(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { index -> getString(index) }

private fun List<String>.boundedTo(maxCharacters: Int): List<String> {
    var remaining = maxCharacters
    return mapNotNull { value ->
        if (remaining <= 0) return@mapNotNull null
        value.take(remaining).also { remaining -= it.length }
    }
}
