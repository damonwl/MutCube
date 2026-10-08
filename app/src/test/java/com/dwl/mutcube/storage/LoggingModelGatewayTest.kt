package com.dwl.mutcube.storage

import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.ai.ModelMessage
import com.dwl.mutcube.core.ai.ModelMessageRole
import com.dwl.mutcube.core.ai.ModelStreamEvent
import com.dwl.mutcube.core.ai.ProviderModelCatalog
import com.dwl.mutcube.core.ai.ProviderProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LoggingModelGatewayTest {
    @Test fun failingLogSinkDoesNotBreakSuccessfulGeneration() = runBlocking {
        val delegate = FakeGateway()
        val gateway = LoggingModelGateway(delegate, delegate) { error("disk unavailable") }
        assertEquals(2, gateway.streamEvents(GenerationRequest(emptyList()), ApiCredential.from("test")).toList().size)
    }

    @Test fun failingLogSinkPreservesOriginalProviderFailure() = runBlocking {
        val failure = IllegalStateException("provider unavailable")
        val delegate = object : ModelGateway {
            override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flow { throw failure }
        }
        val gateway = LoggingModelGateway(delegate, FakeGateway()) { error("disk unavailable") }
        val result = runCatching { gateway.streamEvents(GenerationRequest(emptyList()), ApiCredential.from("test")).toList() }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun recordsMetadataWithoutMessageContent() = runBlocking {
        val captured = mutableListOf<RequestLogEntry>()
        val delegate = FakeGateway()
        val gateway = LoggingModelGateway(delegate, delegate, captured::add)
        val secretText = "这是不应进入日志的私密正文"

        gateway.streamEvents(
            GenerationRequest(
                messages = listOf(ModelMessage(ModelMessageRole.USER, secretText)),
                modelId = "model-a",
                baseUrl = "https://example.com/v1",
            ),
            ApiCredential.from("secret-key"),
        ).toList()

        val entry = captured.single()
        assertEquals(secretText.length, entry.inputCharacters)
        assertEquals(2, entry.responseCharacters)
        assertEquals(12L, entry.inputTokens)
        assertEquals(3L, entry.outputTokens)
        assertNull(entry.error)
        assertEquals(false, entry.toString().contains(secretText))
    }
}

private class FakeGateway : ModelGateway, ProviderModelCatalog {
    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> = flowOf("回答")
    override fun streamEvents(request: GenerationRequest, credential: ApiCredential) = flowOf(
        ModelStreamEvent.TextDelta("回答"),
        ModelStreamEvent.Usage(12, 3),
    )

    override suspend fun listModels(baseUrl: String, credential: ApiCredential, protocol: ProviderProtocol) =
        listOf("model-a")
}
