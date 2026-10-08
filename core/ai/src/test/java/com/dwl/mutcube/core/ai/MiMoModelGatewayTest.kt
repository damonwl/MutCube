package com.dwl.mutcube.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MiMoModelGatewayTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun streamParsesDeltasAndSendsRedactedCredentialOnlyInHeader() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    """
                    data: {"choices":[{"delta":{"content":"你"}}]}

                    data: {"choices":[{"delta":{"content":"好"}}]}

                    data: [DONE]

                    """.trimIndent(),
                )
                .build(),
        )
        val gateway = OpenAiCompatibleModelGateway(
            client = OkHttpClient(),
            baseUrl = server.url("/v1").toString(),
        )

        val chunks = gateway.stream(
            GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试"))),
            ApiCredential.from("sk-secret"),
        ).toList()

        assertEquals(listOf("你", "好"), chunks)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer sk-secret", recorded.headers["Authorization"])
        val requestBody = recorded.body?.utf8().orEmpty()
        assertTrue(requestBody.contains("mimo-v2.5-pro"))
        assertTrue(requestBody.contains("\"include_usage\":true"))
        assertFalse(requestBody.contains("sk-secret"))
    }

    @Test
    fun multimodalMessageUsesOpenAiCompatibleContentParts() = runBlocking {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: [DONE]\n").build(),
        )
        val gateway = gateway()
        gateway.stream(
            GenerationRequest(
                listOf(ModelMessage(ModelMessageRole.USER, "看图", listOf(ModelImage("data:image/jpeg;base64,AQID")))),
            ),
            ApiCredential.from("sk-secret"),
        ).toList()

        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"type\":\"text\""))
        assertTrue(body.contains("\"type\":\"image_url\""))
        assertTrue(body.contains("data:image/jpeg;base64,AQID"))
    }

    @Test
    fun requestCanSelectProviderEndpointAtRuntime() = runBlocking {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: [DONE]\n").build(),
        )
        val gateway = OpenAiCompatibleModelGateway(client = OkHttpClient(), baseUrl = "https://unused.example/v1")

        gateway.stream(
            GenerationRequest(
                listOf(ModelMessage(ModelMessageRole.USER, "测试")),
                baseUrl = server.url("/compatible/v1").toString(),
                temperature = 0.4,
                topP = 0.8,
                maxOutputTokens = 2048,
            ),
            ApiCredential.from("sk-secret"),
        ).toList()

        val recorded = server.takeRequest()
        assertEquals("/compatible/v1/chat/completions", recorded.url.encodedPath)
        val body = recorded.body?.utf8().orEmpty()
        assertTrue(body.contains("\"temperature\":0.4"))
        assertTrue(body.contains("\"top_p\":0.8"))
        assertTrue(body.contains("\"max_tokens\":2048"))
    }

    @Test
    fun mimo25MapsReasoningSelectionToThinkingSwitch() = runBlocking {
        repeat(2) { server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: [DONE]\n").build()) }
        val gateway = gateway()

        gateway.stream(GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试")), modelId = "mimo-v2.5", reasoningLevel = ReasoningLevel.OFF), ApiCredential.from("sk-secret")).toList()
        gateway.stream(GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试")), modelId = "mimo-v2.5-pro", reasoningLevel = ReasoningLevel.HIGH), ApiCredential.from("sk-secret")).toList()

        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("\"thinking\":{\"type\":\"enabled\"}"))
    }

    @Test
    fun googleProtocolUsesNativeEndpointAndParsesText() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream")
            .body("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"你好\"}]}}]}\n\n").build())

        val result = gateway().stream(GenerationRequest(
            listOf(ModelMessage(ModelMessageRole.USER, "测试")), modelId = "gemini-test",
            baseUrl = server.url("/v1beta").toString(), protocol = ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE,
        ), ApiCredential.from("google-secret")).toList()

        assertEquals(listOf("你好"), result)
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:streamGenerateContent", recorded.url.encodedPath)
        assertEquals("google-secret", recorded.headers["x-goog-api-key"])
    }

    @Test
    fun anthropicProtocolUsesMessagesEndpointAndParsesText() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream")
            .body("data: {\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}\n\n").build())

        val result = gateway().stream(GenerationRequest(
            listOf(ModelMessage(ModelMessageRole.USER, "测试")), modelId = "claude-test",
            baseUrl = server.url("/v1").toString(), protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
            reasoningLevel = ReasoningLevel.OFF, maxOutputTokens = 8,
        ), ApiCredential.from("claude-secret")).toList()

        assertEquals(listOf("你好"), result)
        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.url.encodedPath)
        assertEquals("claude-secret", recorded.headers["x-api-key"])
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"max_tokens\":8"))
    }

    @Test
    fun credentialNeverRevealsSecretInToString() {
        assertFalse(ApiCredential.from("sk-secret").toString().contains("sk-secret"))
    }

    @Test
    fun toolDefinitionsAndCallsUseOpenAiCompatibleProtocol() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"memory_search","arguments":"{\"query\":"}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"训练\"}"}}]}}]}

                    data: [DONE]

                    """.trimIndent(),
                )
                .build(),
        )
        val request = GenerationRequest(
            messages = listOf(
                ModelMessage(
                    ModelMessageRole.ASSISTANT,
                    "",
                    toolCalls = listOf(ModelToolCall("old-call", "memory_search", "{\"query\":\"计划\"}")),
                    reasoningContent = "先检索已有记忆",
                ),
                ModelMessage(ModelMessageRole.TOOL, "没有结果", toolCallId = "old-call"),
            ),
            tools = listOf(
                ModelToolDefinition(
                    "memory_search",
                    "搜索记忆",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                ),
            ),
        )

        val events = gateway().streamEvents(request, ApiCredential.from("sk-secret")).toList()

        assertEquals(2, events.filterIsInstance<ModelStreamEvent.ToolCallDelta>().size)
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("\"tool_call_id\":\"old-call\""))
        assertTrue(body.contains("\"name\":\"memory_search\""))
        assertTrue(body.contains("\"reasoning_content\":\"先检索已有记忆\""))
    }

    @Test
    fun structuredStreamParsesProviderTokenUsage() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":9," +
                        "\"prompt_tokens_details\":{\"cached_tokens\":30}}}\n\n" +
                        "data: [DONE]\n",
                )
                .build(),
        )

        val usage = gateway().streamEvents(
            GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试"))),
            ApiCredential.from("sk-secret"),
        ).toList().filterIsInstance<ModelStreamEvent.Usage>().single()

        assertEquals(42L, usage.inputTokens)
        assertEquals(9L, usage.outputTokens)
        assertEquals(30L, usage.cachedInputTokens)
    }

    @Test
    fun modelCatalogUsesProviderEndpointAndReturnsSortedUniqueIds() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("{\"data\":[{\"id\":\"model-z\"},{\"id\":\"model-a\"},{\"id\":\"model-a\"}]}")
                .build(),
        )

        val models = gateway().listModels(server.url("/compatible/v1").toString(), ApiCredential.from("sk-secret"))

        assertEquals(listOf("model-a", "model-z"), models)
        val recorded = server.takeRequest()
        assertEquals("/compatible/v1/models", recorded.url.encodedPath)
        assertEquals("Bearer sk-secret", recorded.headers["Authorization"])
    }

    @Test
    fun authenticationFailureIsClassifiedAndNotRetryable() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("invalid key").build())
        val gateway = gateway()

        val failure = runCatching {
            gateway.stream(
                GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试"))),
                ApiCredential.from("sk-secret"),
            ).toList()
        }.exceptionOrNull() as ModelProviderException

        assertEquals(ModelErrorKind.AUTHENTICATION, failure.kind)
        assertFalse(failure.kind.retryable)
        assertEquals(401, failure.statusCode)
    }

    @Test
    fun cancellingCollectorCancelsBlockedHttpCall() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body("data: ${"x".repeat(30)}")
                .throttleBody(1, 20, TimeUnit.MILLISECONDS)
                .build(),
        )
        val gateway = gateway()
        val collection = launch(Dispatchers.Default) {
            gateway.stream(
                GenerationRequest(listOf(ModelMessage(ModelMessageRole.USER, "测试"))),
                ApiCredential.from("sk-secret"),
            ).collect {}
        }
        server.takeRequest()

        withTimeout(300) {
            collection.cancelAndJoin()
        }
    }

    private fun gateway() = OpenAiCompatibleModelGateway(
        client = OkHttpClient(),
        baseUrl = server.url("/v1").toString(),
    )
}
