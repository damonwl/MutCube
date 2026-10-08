package com.dwl.mutcube.core.ai

import kotlinx.serialization.json.*

/** Audio wire format is selected by configuration, never by model name. */
object SpeechProtocols {
    fun resolveTts(profile: ProviderProfile, modelId: String): TtsProtocol =
        profile.models.firstOrNull { it.id == modelId }?.ttsProtocol ?: profile.ttsProtocol

    fun chatSpeechRequest(model: String, text: String, voice: String, streaming: Boolean = false): String = buildJsonObject {
        put("model", model)
        put("stream", streaming)
        putJsonArray("messages") { addJsonObject { put("role", "assistant"); put("content", text) } }
        putJsonObject("audio") { put("format", if (streaming) "pcm16" else "wav"); put("voice", voice) }
    }.toString()

    fun speechBase64(response: String): String = Json.parseToJsonElement(response).jsonObject["choices"]
        ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("audio")?.jsonObject
        ?.get("data")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("TTS 服务未返回音频")

    fun speechChunk(response: String): String? {
        val root = Json.parseToJsonElement(response).jsonObject
        require(root["error"] == null) { "TTS 流式服务返回错误" }
        return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
            ?.get("audio")?.takeIf { it != JsonNull }?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
    }

    fun speechFinished(response: String): Boolean = Json.parseToJsonElement(response).jsonObject["choices"]
        ?.jsonArray?.any { it.jsonObject["finish_reason"]?.jsonPrimitive?.contentOrNull == "stop" } ?: false

    fun resolveAsr(profile: ProviderProfile, modelId: String): AsrProtocol =
        profile.models.firstOrNull { it.id == modelId }?.asrProtocol ?: profile.asrProtocol

    fun chatAudioRequest(model: String, base64Wav: String, language: String): String = buildJsonObject {
        put("model", model)
        put("stream", false)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "input_audio")
                        putJsonObject("input_audio") { put("data", "data:audio/wav;base64,$base64Wav") }
                    }
                }
            }
        }
        putJsonObject("asr_options") {
            put("language", language.substringBefore('-').takeIf { it in listOf("zh", "en") } ?: "auto")
        }
    }.toString()

    fun transcript(protocol: AsrProtocol, response: String): String {
        val root = Json.parseToJsonElement(response).jsonObject
        val value = when (protocol) {
            AsrProtocol.AUDIO_TRANSCRIPTIONS -> root["text"]
            AsrProtocol.CHAT_INPUT_AUDIO -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")
        }
        return value?.jsonPrimitive?.contentOrNull.orEmpty().trim().takeIf(String::isNotEmpty)
            ?: error("ASR 服务未返回文本")
    }
}
