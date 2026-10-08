package com.dwl.mutcube.template.runtime

import com.dwl.mutcube.core.database.TemplateRecordEntity
import kotlinx.serialization.json.*

/** Lossless bounded selection: omitted records remain in user storage, never masquerade as summaries. */
object TemplateContext {
    fun recentRecords(rows: List<TemplateRecordEntity>, limit: Int = 12_000): JsonObject {
        require(limit in 100..40_000)
        var remaining = limit
        val selected = mutableListOf<JsonElement>()
        rows.sortedWith(compareByDescending<TemplateRecordEntity> { it.updatedAt }.thenBy { it.recordKey }).forEach { row ->
            val value = buildJsonObject {
                put("collection", row.collection)
                put("key", row.recordKey)
                put("revision", row.revision)
                put("data", Json.parseToJsonElement(row.json))
            }
            val size = value.toString().length + 1
            if (selected.size < 8 && size <= remaining) {
                selected.add(value)
                remaining -= size
            }
        }
        return buildJsonObject {
            put("records", JsonArray(selected))
            put("omitted", rows.size - selected.size)
            put("policy", "recent-full-records-v1")
        }
    }
}
