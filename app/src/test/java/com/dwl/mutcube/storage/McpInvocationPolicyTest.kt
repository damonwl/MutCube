package com.dwl.mutcube.storage

import com.dwl.mutcube.core.extensions.ExtensionPermission
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.extensions.McpTool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class McpInvocationPolicyTest {
    private val tool = McpTool("read", "Read", "{}")
    private val server = McpServer("test", "Server", "https://example.org/mcp", tools = listOf(tool))

    @Test fun refreshPreservesRevocationsAndRejectsChangedEndpoints() {
        val revoked = tool.copy(enabled = false, permission = ExtensionPermission.DENY)
        val latest = server.copy(tools = listOf(revoked))
        assertEquals(revoked, mergeMcpDiscovery(server, latest, listOf(tool)).tools.single())
        val changed = latest.copy(endpoint = "https://different.example/mcp")
        assertEquals(changed, mergeMcpDiscovery(server, changed, listOf(tool)))
        val allowed = server.copy(tools = listOf(tool.copy(permission = ExtensionPermission.ALLOW)))
        assertEquals(ExtensionPermission.ASK,
            mergeMcpDiscovery(server, allowed, listOf(tool.copy(inputSchemaJson = "{\"required\":[\"path\"]}")))
                .tools.single().permission)
    }

    @Test fun toolAliasesDoNotCollideAfterSanitizationOrTruncation() {
        assertNotEquals(tool.externalName("same-prefix-aaa"), tool.externalName("same-prefix-bbb"))
        assertNotEquals(tool.copy(name = "file.read").externalName("test"), tool.copy(name = "file/read").externalName("test"))
        assertNotEquals(tool.copy(name = "x".repeat(100) + "a").externalName("test"),
            tool.copy(name = "x".repeat(100) + "b").externalName("test"))
        assertTrue(tool.externalName("test").matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        assertEquals(tool.externalName("test"), tool.externalName("test"))
    }

    @Test fun changesDuringApprovalInvalidateThePendingInvocation() {
        assertTrue(isMcpInvocationStillAuthorized(server, tool, server, tool))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, null, null))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, server.copy(enabled = false), tool))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, server.copy(endpoint = "https://other.example/mcp"), tool))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, server, tool.copy(enabled = false)))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, server, tool.copy(permission = ExtensionPermission.DENY)))
        assertFalse(isMcpInvocationStillAuthorized(server, tool, server, tool.copy(inputSchemaJson = "{\"required\":[\"new\"]}")))
    }
}
