package com.dwl.mutcube.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextRedactorTest {
    @Test fun hidesCommonCredentialShapes() {
        val raw = "Bearer abc123 token=abc sk-abcdefghijklmnopqrstuvwxyz AKIAABCDEFGHIJKLMNOP eyJhbGci.eyJzdWI.eyJzaWc " +
            "{\"apiKey\":\"json-secret\",\"password\":\"two words\"}"
        val redacted = SensitiveTextRedactor.redact(raw)
        listOf("abc123", "sk-abcdefghijklmnopqrstuvwxyz", "AKIAABCDEFGHIJKLMNOP", "eyJhbGci", "json-secret", "two words").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("[已隐藏]"))
    }
}
