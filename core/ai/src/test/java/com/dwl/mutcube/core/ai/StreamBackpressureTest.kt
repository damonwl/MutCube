package com.dwl.mutcube.core.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamBackpressureTest {
    @Test fun slowConsumersReceiveEveryDeltaForAllProtocols() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (protocol in ProviderProtocol.entries) {
                val expected = (0 until 256).map { "chunk-$it" }
                val body = expected.joinToString("\n\n", postfix = "\n\ndata: [DONE]\n\n") { text ->
                    val payload = when (protocol) {
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> """{"choices":[{"delta":{"content":"$text"}}]}"""
                        ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE -> """{"candidates":[{"content":{"parts":[{"text":"$text"}]}}]}"""
                        ProviderProtocol.ANTHROPIC_MESSAGES -> """{"type":"content_block_delta","delta":{"type":"text_delta","text":"$text"}}"""
                    }
                    "data: $payload"
                }
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(body).build())
                val received = mutableListOf<String>()
                withTimeout(10_000) {
                    OpenAiCompatibleModelGateway().stream(
                        GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "test")),
                            baseUrl = server.url("/v1").toString(), protocol = protocol),
                        ApiCredential.from("test-credential"),
                    ).collect { received += it; delay(2) }
                }
                assertEquals(protocol.name, expected, received)
            }
        }
    }
}
