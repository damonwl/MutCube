package com.dwl.mutcube.template.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class TemplateSafeNetworkTest {
    @Test fun onlyExactAuthorizedHttpsDestinationsAreAccepted() {
        val domains = setOf("example.org")
        assertEquals("example.org", validateTemplateNetworkUrl("https://example.org/words?q=cat", domains).host)
        listOf(
            "http://example.org/words",
            "https://example.org:443/words",
            "https://user@example.org/words",
            "https://example.org/words#fragment",
            "https://sub.example.org/words",
            "https://example.org.evil.test/words",
            "https://127.0.0.1/words",
        ).forEach { url ->
            assertThrows("accepted $url", Exception::class.java) { validateTemplateNetworkUrl(url, domains) }
        }
    }

    @Test fun metadataAndPrivateDnsAnswersAreRejected() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "198.18.0.1", "0.0.0.0", "::1", "fc00::1", "fe80::1").forEach {
            assertFalse("accepted $it", isPublicAddress(InetAddress.getByName(it)))
        }
        assertTrue(isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test fun redirectResponsesAreRejectedRatherThanExposedAsSuccessfulFetches() {
        listOf(301, 302, 307, 308).forEach { status ->
            assertThrows(IllegalArgumentException::class.java) { requireNonRedirect(status) }
        }
        requireNonRedirect(200)
        requireNonRedirect(404)
    }
}
