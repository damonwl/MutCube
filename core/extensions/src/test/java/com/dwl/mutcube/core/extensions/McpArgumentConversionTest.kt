package com.dwl.mutcube.core.extensions

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class McpArgumentConversionTest {
    @Test fun quotedScalarsStayStrings() {
        assertEquals("123", Json.parseToJsonElement("\"123\"").toKotlinValue())
        assertEquals("true", Json.parseToJsonElement("\"true\"").toKotlinValue())
        assertEquals(123L, Json.parseToJsonElement("123").toKotlinValue())
        assertEquals(true, Json.parseToJsonElement("true").toKotlinValue())
    }
}
