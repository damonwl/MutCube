package com.dwl.mutcube.storage

import com.dwl.mutcube.core.extensions.McpTool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSchemaTest {
    @Test fun previouslyStoredDataClassTextIsRejected() {
        assertFalse(McpTool("search", null, "ToolSchema(schema=null, properties={})").hasJsonSchema())
    }

    @Test fun validJsonSchemaIsAccepted() {
        assertTrue(McpTool("search", null, """{"type":"object","properties":{}}""").hasJsonSchema())
    }
}
