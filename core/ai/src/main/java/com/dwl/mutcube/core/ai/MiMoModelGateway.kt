package com.dwl.mutcube.core.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleModelGateway(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ModelGateway, ProviderModelCatalog {
    private val googleGateway by lazy { GoogleGenerativeLanguageGateway(client, json) }
    private val anthropicGateway by lazy { AnthropicMessagesGateway(client, json) }

    override suspend fun listModels(
        baseUrl: String,
        credential: ApiCredential,
        protocol: ProviderProtocol,
    ): List<String> = when (protocol) {
        ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE -> googleGateway.listModels(baseUrl, credential, protocol)
        ProviderProtocol.ANTHROPIC_MESSAGES -> anthropicGateway.listModels(baseUrl, credential, protocol)
        ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/models")
                .header("Authorization", "Bearer ${credential.value}")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body.string().take(MAX_ERROR_LENGTH)
                        throw ModelProviderException(
                            response.code.toErrorKind(),
                            response.code,
                            "Provider model discovery failed (${response.code}): $detail",
                        )
                    }
                    val root = json.parseToJsonElement(response.body.string()).jsonObject
                    root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                        item.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
                    }.distinct().sorted().take(MAX_DISCOVERED_MODELS)
                }
            } catch (error: ModelProviderException) {
                throw error
            } catch (error: IOException) {
                throw ModelProviderException(ModelErrorKind.NETWORK, null, "Unable to reach model provider", error)
            } catch (error: Exception) {
                throw ModelProviderException(ModelErrorKind.INVALID_RESPONSE, null, "Invalid model catalog", error)
            }
        }
    }

    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> =
        streamEvents(request, credential).filterIsInstance<ModelStreamEvent.TextDelta>().map { it.text }

    override fun streamEvents(
        request: GenerationRequest,
        credential: ApiCredential,
    ): Flow<ModelStreamEvent> = when (request.protocol) {
        ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE -> googleGateway.streamEvents(request, credential)
        ProviderProtocol.ANTHROPIC_MESSAGES -> anthropicGateway.streamEvents(request, credential)
        ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> streamOpenAiEvents(request, credential)
    }

    private fun streamOpenAiEvents(
        request: GenerationRequest,
        credential: ApiCredential,
    ): Flow<ModelStreamEvent> = callbackFlow {
        val endpoint = "${(request.baseUrl ?: baseUrl).trimEnd('/')}/chat/completions"
        val body = request.toJson().toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${credential.value}")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(ModelProviderException(ModelErrorKind.NETWORK, null, "Unable to reach model provider", e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    // OkHttp invokes this on its worker thread. Backpressure must wait for the
                    // consumer rather than treating a full channel as the end of the response.
                    readResponse(call, response) { event -> trySendBlocking(event).isSuccess }
                    close()
                } catch (error: Exception) {
                    close(error)
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun readResponse(call: Call, response: Response, onEvent: (ModelStreamEvent) -> Boolean) {
        try {
            response.use {
                if (!response.isSuccessful) {
                    val detail = response.body.string().take(MAX_ERROR_LENGTH)
                    throw ModelProviderException(
                        kind = response.code.toErrorKind(),
                        statusCode = response.code,
                        message = "Provider request failed (${response.code}): $detail",
                    )
                }

                val source = response.body.source()
                while (!call.isCanceled() && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith(DATA_PREFIX)) continue
                    val payload = line.removePrefix(DATA_PREFIX).trim()
                    if (payload == DONE_MARKER) break
                    val events = parseEvents(payload)
                    if (events.any { !onEvent(it) }) break
                }
            }
        } catch (error: IOException) {
            if (call.isCanceled()) return
            throw ModelProviderException(ModelErrorKind.NETWORK, null, "Unable to reach model provider", error)
        }
    }

    private fun GenerationRequest.toJson(): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(modelId))
        put("stream", JsonPrimitive(true))
        put("stream_options", buildJsonObject { put("include_usage", JsonPrimitive(true)) })
        temperature?.let { put("temperature", JsonPrimitive(it)) }
        topP?.let { put("top_p", JsonPrimitive(it)) }
        maxOutputTokens?.let { put("max_tokens", JsonPrimitive(it)) }
        when {
            modelId.startsWith("mimo-v2.5", ignoreCase = true) -> put("thinking", buildJsonObject {
                put("type", JsonPrimitive(if (reasoningLevel == ReasoningLevel.OFF) "disabled" else "enabled"))
            })
            reasoningLevel.apiValue != null -> put("reasoning_effort", JsonPrimitive(reasoningLevel.apiValue))
        }
        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                            put("parameters", json.parseToJsonElement(tool.parametersJson))
                        })
                    })
                }
            })
        }
        put("messages", buildJsonArray {
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(message.role.name.lowercase()))
                    message.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                    message.reasoningContent?.takeIf(String::isNotBlank)?.let {
                        put("reasoning_content", JsonPrimitive(it))
                    }
                    if (message.images.isEmpty()) {
                        put("content", JsonPrimitive(message.content))
                    } else {
                        put("content", buildJsonArray {
                            if (message.content.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("text"))
                                    put("text", JsonPrimitive(message.content))
                                })
                            }
                            message.images.forEach { image ->
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("image_url"))
                                    put("image_url", buildJsonObject {
                                        put("url", JsonPrimitive(image.dataUrl))
                                    })
                                })
                            }
                        })
                    }
                    if (message.toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            message.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(call.id))
                                    put("type", JsonPrimitive("function"))
                                    put("function", buildJsonObject {
                                        put("name", JsonPrimitive(call.name))
                                        put("arguments", JsonPrimitive(call.argumentsJson))
                                    })
                                })
                            }
                        })
                    }
                })
            }
        })
    }

    private fun parseEvents(payload: String): List<ModelStreamEvent> = runCatching {
        val root = json.parseToJsonElement(payload).jsonObject
        buildList {
            val usage = root["usage"] as? JsonObject
            if (usage != null) {
                add(
                    ModelStreamEvent.Usage(
                        usage["prompt_tokens"]?.jsonPrimitive?.longOrNull,
                        usage["completion_tokens"]?.jsonPrimitive?.longOrNull,
                        (usage["prompt_tokens_details"] as? JsonObject)
                            ?.get("cached_tokens")?.jsonPrimitive?.longOrNull,
                    ),
                )
            }
            val choices = root["choices"] as? JsonArray ?: return@buildList
            val choice = choices.firstOrNull() as? JsonObject ?: return@buildList
            val delta = choice["delta"] as? JsonObject ?: return@buildList
            delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                add(ModelStreamEvent.TextDelta(it))
            }
            delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                add(ModelStreamEvent.ReasoningDelta(it))
            }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val call = element.jsonObject
                val function = call["function"] as? JsonObject
                add(
                    ModelStreamEvent.ToolCallDelta(
                        index = call["index"]?.jsonPrimitive?.intOrNull ?: 0,
                        id = call["id"]?.jsonPrimitive?.contentOrNull,
                        name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                        argumentsDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                )
            }
        }
    }.getOrElse { error ->
        throw ModelProviderException(ModelErrorKind.INVALID_RESPONSE, null, "Invalid provider streaming response", error)
    }

    private fun Int.toErrorKind(): ModelErrorKind = when (this) {
        401, 403 -> ModelErrorKind.AUTHENTICATION
        429 -> ModelErrorKind.RATE_LIMIT
        in 500..599 -> ModelErrorKind.SERVER
        else -> ModelErrorKind.REQUEST
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1"
        const val DEFAULT_MODEL_ID = "mimo-v2.5-pro"
        private const val DATA_PREFIX = "data:"
        private const val DONE_MARKER = "[DONE]"
        private const val MAX_ERROR_LENGTH = 1_000
        private const val MAX_DISCOVERED_MODELS = 500
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Deprecated("Use OpenAiCompatibleModelGateway")
typealias MiMoModelGateway = OpenAiCompatibleModelGateway
