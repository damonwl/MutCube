package com.dwl.mutcube.template.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TemplatePackageTest {
    private val digest = "0".repeat(64)
    private val action = """{"id":"memo.list","description":"List","mode":"LIST","channels":["GUI"],"collection":"memos","inputSchema":{"type":"object","properties":{"beforeTime":{"type":"integer"},"beforeKey":{"type":"string"}},"required":[],"additionalProperties":false}}"""
    private val manifest = """{"protocolVersion":2,"id":"example.memo","version":"1.0.0","name":"Memo","entry":"templates/memo/index.html","collections":[{"id":"memos","policy":"MUTABLE","schema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}}],"actions":[$action],"capabilities":["action.run"]}"""
    private fun pack(path: String = "templates/memo/index.html", permissions: String = "[\"data.records\"]", domains: String = "[]", minHostVersion: String = "0.1.2") =
        """{"packageVersion":1,"minHostVersion":"$minHostVersion","developer":{"name":"Example"},"permissions":$permissions,"networkDomains":$domains,"resources":{"$path":"$digest"},"template":$manifest}"""

    @Test fun acceptsValidPackage() {
        assertEquals("example.memo", TemplatePackage.parse(pack()).manifest.id)
        assertEquals(setOf("data.records", "speech.speak", "speech.recognize"), TemplatePackage.parse(pack(
            permissions = "[\"data.records\",\"speech.speak\",\"speech.recognize\"]", minHostVersion = "0.1.3")).permissions)
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(
            permissions = "[\"data.records\",\"speech.speak\"]")) }
    }

    @Test fun rejectsTraversalAndUndeclaredSensitiveCapability() {
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack("templates/memo/../index.html")) }
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack("templates/memo//index.html")) }
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(permissions = "[\"data.records\",\"shell.exec\"]")) }
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(permissions = "[\"data.records\",\"media.audio.record\"]")) }
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(domains = "[\"example.com\"]")) }
        listOf("-example.com", "example-.com", "example..com", "127.0.0.1").forEach { domain ->
            assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(
                permissions = "[\"data.records\",\"network.fetch\"]", domains = "[\"$domain\"]")) }
        }
        assertEquals(setOf("example.com"), TemplatePackage.parse(pack(
            permissions = "[\"data.records\",\"network.fetch\"]", domains = "[\"example.com\"]")).networkDomains)
    }

    @Test fun rejectsUnboundedVersionsBeforeInstallerComparison() {
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack(minHostVersion = "9999999999.0.0")) }
        assertThrows(IllegalArgumentException::class.java) { TemplatePackage.parse(pack().replace("\"version\":\"1.0.0\"", "\"version\":\"9999999999.0.0\"")) }
    }
}
