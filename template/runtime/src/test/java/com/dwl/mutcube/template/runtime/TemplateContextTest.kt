package com.dwl.mutcube.template.runtime

import com.dwl.mutcube.core.database.TemplateRecordEntity
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TemplateContextTest {
    private fun row(key: String, json: String, time: Long = 1) =
        TemplateRecordEntity("project", "template", "results", key, json, 1, 1, "AI", time, time)

    @Test fun keepsCompleteJsonAndSkipsOversizedRecords() {
        val large = row("large", buildJsonObject { put("text", "x".repeat(1000)) }.toString(), 3)
        val small = row("small", "{\"text\":\"完整内容\"}", 2)
        val context = TemplateContext.recentRecords(listOf(small, large), 300)
        assertEquals(1, context.getValue("omitted").jsonPrimitive.int)
        val record = context.getValue("records").jsonArray.single().jsonObject
        assertEquals("small", record.getValue("key").jsonPrimitive.content)
        assertEquals("完整内容", record.getValue("data").jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(context, Json.parseToJsonElement(context.toString()))
    }

    @Test fun limitsCountAndOrdersNewestFirst() {
        val context = TemplateContext.recentRecords((1..20).map { row("$it", "{}", it.toLong()) })
        assertEquals(8, context.getValue("records").jsonArray.size)
        assertEquals("20", context.getValue("records").jsonArray.first().jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals(12, context.getValue("omitted").jsonPrimitive.int)
    }
}
