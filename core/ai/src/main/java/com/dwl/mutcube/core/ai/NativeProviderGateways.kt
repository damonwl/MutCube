package com.dwl.mutcube.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal abstract class SseNativeGateway(
    protected val client: OkHttpClient,
    protected val json: Json,
) : ModelGateway, ProviderModelCatalog {
    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> =
        streamEvents(request, credential).filterIsInstance<ModelStreamEvent.TextDelta>().map { it.text }

    protected fun sse(request: Request, parse: (String) -> List<ModelStreamEvent>): Flow<ModelStreamEvent> = callbackFlow {
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(ModelProviderException(ModelErrorKind.NETWORK, null, "Unable to reach model provider", e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!response.isSuccessful) {
                            val detail = response.body.string().take(1_000)
                            throw ModelProviderException(response.code.errorKind(), response.code, "Provider request failed (${response.code}): $detail")
                        }
                        val source = response.body.source()
                        responseLoop@ while (!call.isCanceled() && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            if (payload.isEmpty()) continue
                            for (event in parse(payload)) {
                                if (!trySendBlocking(event).isSuccess) break@responseLoop
                            }
                        }
                    }
                    close()
                } catch (error: Exception) {
                    close(error)
                }
            }
        })
        awaitClose { call.cancel() }
    }

    protected fun Int.errorKind() = when (this) {
        401, 403 -> ModelErrorKind.AUTHENTICATION
        429 -> ModelErrorKind.RATE_LIMIT
        in 500..599 -> ModelErrorKind.SERVER
        else -> ModelErrorKind.REQUEST
    }

    protected companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal class GoogleGenerativeLanguageGateway(client: OkHttpClient, json: Json) : SseNativeGateway(client, json) {
    override suspend fun listModels(baseUrl: String, credential: ApiCredential, protocol: ProviderProtocol): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("${baseUrl.trimEnd('/')}/models")
                .header("x-goog-api-key", credential.value).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ModelProviderException(response.code.errorKind(), response.code, "Provider model discovery failed (${response.code})")
                json.parseToJsonElement(response.body.string()).jsonObject["models"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
                    .distinct().sorted()
            }
        }

    override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> {
        val endpoint = "${(request.baseUrl ?: "https://generativelanguage.googleapis.com/v1beta").trimEnd('/')}/models/${request.modelId}:streamGenerateContent?alt=sse"
        val body = buildJsonObject {
            val system = request.messages.filter { it.role == ModelMessageRole.SYSTEM }.joinToString("\n") { it.content }
            if (system.isNotBlank()) put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(system)) }) })
            })
            put("contents", buildJsonArray {
                request.messages.filter { it.role != ModelMessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == ModelMessageRole.ASSISTANT) "model" else "user"))
                        put("parts", buildJsonArray {
                            if (message.content.isNotBlank()) add(buildJsonObject { put("text", JsonPrimitive(message.content)) })
                            message.images.forEach { image ->
                                val (mime, data) = image.dataUrl.substringAfter("data:").split(";base64,", limit = 2).let {
                                    it.firstOrNull().orEmpty() to it.getOrElse(1) { "" }
                                }
                                add(buildJsonObject { put("inlineData", buildJsonObject {
                                    put("mimeType", JsonPrimitive(mime)); put("data", JsonPrimitive(data))
                                }) })
                            }
                            message.toolCalls.forEach { call -> add(buildJsonObject { put("functionCall", buildJsonObject {
                                put("name", JsonPrimitive(call.name)); put("args", json.parseToJsonElement(call.argumentsJson))
                            }) }) }
                            if (message.role == ModelMessageRole.TOOL) add(buildJsonObject { put("functionResponse", buildJsonObject {
                                val toolName = request.messages.asSequence().flatMap { it.toolCalls.asSequence() }
                                    .firstOrNull { it.id == message.toolCallId }?.name ?: "tool"
                                put("name", JsonPrimitive(toolName)); put("response", buildJsonObject { put("result", JsonPrimitive(message.content)) })
                            }) })
                        })
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                request.temperature?.let { put("temperature", JsonPrimitive(it)) }
                request.topP?.let { put("topP", JsonPrimitive(it)) }
                request.maxOutputTokens?.let { put("maxOutputTokens", JsonPrimitive(it)) }
                if (request.reasoningLevel != ReasoningLevel.AUTO) put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", JsonPrimitive(request.reasoningLevel != ReasoningLevel.OFF))
                    if (request.reasoningLevel == ReasoningLevel.OFF) put("thinkingBudget", JsonPrimitive(0))
                    else put("thinkingLevel", JsonPrimitive(request.reasoningLevel.apiValue!!))
                })
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            request.tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", JsonPrimitive(tool.name))
                                    put("description", JsonPrimitive(tool.description))
                                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                                })
                            }
                        })
                    })
                })
            }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return sse(Request.Builder().url(endpoint).header("x-goog-api-key", credential.value).post(body).build()) { payload ->
            val root = json.parseToJsonElement(payload).jsonObject
            buildList {
                (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray.orEmpty().forEachIndexed { index, element ->
                        val part = element.jsonObject
                        part["text"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                            add(if (part["thought"]?.jsonPrimitive?.contentOrNull == "true") ModelStreamEvent.ReasoningDelta(it) else ModelStreamEvent.TextDelta(it))
                        }
                        (part["functionCall"] as? JsonObject)?.let { call -> add(ModelStreamEvent.ToolCallDelta(
                            index, "google-call-$index", call["name"]?.jsonPrimitive?.contentOrNull, call["args"]?.toString().orEmpty()
                        )) }
                    }
                (root["usageMetadata"] as? JsonObject)?.let { usage -> add(ModelStreamEvent.Usage(
                    usage["promptTokenCount"]?.jsonPrimitive?.longOrNull,
                    usage["candidatesTokenCount"]?.jsonPrimitive?.longOrNull,
                    usage["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull,
                )) }
            }
        }
    }
}

internal class AnthropicMessagesGateway(client: OkHttpClient, json: Json) : SseNativeGateway(client, json) {
    override suspend fun listModels(baseUrl: String, credential: ApiCredential, protocol: ProviderProtocol): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("${baseUrl.trimEnd('/')}/models")
                .header("x-api-key", credential.value).header("anthropic-version", "2023-06-01").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ModelProviderException(response.code.errorKind(), response.code, "Provider model discovery failed (${response.code})")
                json.parseToJsonElement(response.body.string()).jsonObject["data"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.distinct().sorted()
            }
        }

    override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> {
        val body = buildJsonObject {
            put("model", JsonPrimitive(request.modelId)); put("stream", JsonPrimitive(true))
            val thinkingBudget = when (request.reasoningLevel) {
                ReasoningLevel.LOW -> 1024
                ReasoningLevel.MEDIUM -> 4096
                ReasoningLevel.HIGH -> 8192
                ReasoningLevel.XHIGH -> 16384
                else -> null
            }
            put(
                "max_tokens",
                JsonPrimitive(
                    thinkingBudget?.let { maxOf(request.maxOutputTokens ?: 4096, it + 1024) }
                        ?: (request.maxOutputTokens ?: 4096),
                ),
            )
            request.messages.filter { it.role == ModelMessageRole.SYSTEM }.joinToString("\n") { it.content }
                .takeIf(String::isNotBlank)?.let { put("system", JsonPrimitive(it)) }
            thinkingBudget?.let { budget -> put("thinking", buildJsonObject {
                put("type", JsonPrimitive("enabled")); put("budget_tokens", JsonPrimitive(budget))
            }) }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != ModelMessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == ModelMessageRole.ASSISTANT) "assistant" else "user"))
                        put("content", buildJsonArray {
                            if (message.content.isNotBlank()) add(buildJsonObject {
                                put("type", JsonPrimitive("text")); put("text", JsonPrimitive(message.content))
                            })
                            message.images.forEach { image ->
                                val pieces = image.dataUrl.substringAfter("data:").split(";base64,", limit = 2)
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("image"))
                                    put("source", buildJsonObject {
                                        put("type", JsonPrimitive("base64"))
                                        put("media_type", JsonPrimitive(pieces.firstOrNull().orEmpty()))
                                        put("data", JsonPrimitive(pieces.getOrElse(1) { "" }))
                                    })
                                })
                            }
                            message.toolCalls.forEach { call -> add(buildJsonObject {
                                put("type", JsonPrimitive("tool_use")); put("id", JsonPrimitive(call.id))
                                put("name", JsonPrimitive(call.name)); put("input", json.parseToJsonElement(call.argumentsJson))
                            }) }
                            if (message.role == ModelMessageRole.TOOL) add(buildJsonObject {
                                put("type", JsonPrimitive("tool_result")); put("tool_use_id", JsonPrimitive(message.toolCallId.orEmpty()))
                                put("content", JsonPrimitive(message.content))
                            })
                        })
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                            put("input_schema", json.parseToJsonElement(tool.parametersJson))
                        })
                    }
                })
            }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val endpoint = "${(request.baseUrl ?: "https://api.anthropic.com/v1").trimEnd('/')}/messages"
        return sse(Request.Builder().url(endpoint).header("x-api-key", credential.value).header("anthropic-version", "2023-06-01").post(body).build()) { payload ->
            val root = json.parseToJsonElement(payload).jsonObject
            val delta = root["delta"] as? JsonObject
            val block = root["content_block"] as? JsonObject
            buildList {
                when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let { add(ModelStreamEvent.TextDelta(it)) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.let { add(ModelStreamEvent.ReasoningDelta(it)) }
                    "input_json_delta" -> add(ModelStreamEvent.ToolCallDelta(root["index"]?.jsonPrimitive?.intOrNull ?: 0, null, null, delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                }
                if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") add(ModelStreamEvent.ToolCallDelta(root["index"]?.jsonPrimitive?.intOrNull ?: 0, block["id"]?.jsonPrimitive?.contentOrNull, block["name"]?.jsonPrimitive?.contentOrNull, ""))
                (root["message"]?.jsonObject?.get("usage") as? JsonObject)?.let { add(ModelStreamEvent.Usage(it["input_tokens"]?.jsonPrimitive?.longOrNull, it["output_tokens"]?.jsonPrimitive?.longOrNull)) }
                (root["usage"] as? JsonObject)?.let { add(ModelStreamEvent.Usage(null, it["output_tokens"]?.jsonPrimitive?.longOrNull)) }
            }
        }
    }
}
