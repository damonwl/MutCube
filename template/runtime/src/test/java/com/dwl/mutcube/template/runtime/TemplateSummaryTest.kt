package com.dwl.mutcube.template.runtime

import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.TemplateRecordEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TemplateSummaryTest {
    private val credential = ApiCredential.from("test-credential")
    private val request = GenerationRequest(messages = emptyList())
    private fun rows() = (1..12).map {
        TemplateRecordEntity("project", "template", "results", "$it", "{\"text\":\"record-$it\"}", 1, 1, "AI", it.toLong(), it.toLong())
    }
    private fun plan() = TemplateSummary.plan(rows(), TemplateContext.recentRecords(rows()))
    private fun gateway(block: (GenerationRequest) -> Flow<String>) = object : ModelGateway {
        override fun stream(request: GenerationRequest, credential: ApiCredential) = block(request)
    }

    @Test fun compressesOnlyOlderRecordsAndFingerprintTracksFacts() {
        val rows = rows()
        val plan = plan()
        assertEquals(listOf("4", "3", "2", "1"), plan.sources.map { it.jsonObject.getValue("key").jsonPrimitive.content })
        assertEquals(0, plan.omitted)
        assertEquals(plan.digest, TemplateSummary.plan(rows.reversed(), TemplateContext.recentRecords(rows)).digest)
        val changed = rows.map { if (it.recordKey == "1") it.copy(json = "{}") else it }
        assertNotEquals(plan.digest, TemplateSummary.plan(changed, TemplateContext.recentRecords(changed)).digest)
    }

    @Test fun validatesSummaryAndUsesIndependentInstruction() = runBlocking {
        val gateway = gateway {
            assertEquals(2, it.messages.size)
            assertEquals(0.0, it.temperature!!, 0.0)
            assertTrue(it.tools.isEmpty())
            flowOf("{\"summary\":", "\"历史记录摘要 [1,2,3,4]\"}")
        }
        assertEquals("历史记录摘要 [1,2,3,4]", TemplateSummary.generate(gateway, request, credential, plan()))
    }

    @Test fun invalidOutputAndNetworkFailureFallBack() = runBlocking {
        for (output in listOf("not-json", "{\"summary\":1}", "{\"summary\":\"\"}", "{\"summary\":\"ok\",\"extra\":true}")) {
            assertNull(TemplateSummary.generate(gateway { flowOf(output) }, request, credential, plan()))
        }
        assertNull(TemplateSummary.generate(gateway { flow { throw IllegalStateException("network") } }, request, credential, plan()))
    }

    @Test fun localTimeoutFallsBackButCancellationPropagates() = runBlocking {
        assertNull(TemplateSummary.generate(gateway { flow { delay(1000); emit("{}") } }, request, credential, plan(), 10))
        var cancelled = false
        try {
            TemplateSummary.generate(gateway { flow { throw CancellationException("left page") } }, request, credential, plan())
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun noHistoryDoesNotCallModel() = runBlocking {
        val plan = TemplateSummary.plan(emptyList(), TemplateContext.recentRecords(emptyList()))
        assertNull(TemplateSummary.generate(gateway { error("Should not call") }, request, credential, plan))
    }
}
