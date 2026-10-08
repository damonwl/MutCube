package com.dwl.mutcube.core.extensions

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class McpToolSchemaTest {
    @Test fun discoveredSchemaIsJsonInsteadOfKotlinDataClassText() {
        val schema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject { put("type", "string") })
            },
            required = listOf("query"),
        )

        val encoded = Json.parseToJsonElement(encodeToolSchema(schema)).jsonObject
        assertEquals("object", encoded.getValue("type").jsonPrimitive.content)
        assertEquals("string", encoded.getValue("properties").jsonObject.getValue("query")
            .jsonObject.getValue("type").jsonPrimitive.content)
    }
}
