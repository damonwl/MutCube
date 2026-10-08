package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.SpaceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId

internal data class ToolExecutionResult(val output: String, val isError: Boolean)

internal class BuiltInToolService(
    private val repository: ConversationRepository,
    private val now: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun definitions(memoryWriteEnabled: Boolean): List<ModelToolDefinition> = buildList {
        add(
            ModelToolDefinition(
                "current_time",
                "读取用户设备当前时区的日期和时间。",
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",
            ),
        )
        add(
            ModelToolDefinition(
                "memory_search",
                "搜索用户拥有的全局记忆和当前项目记忆。回答用户历史偏好、事实或项目上下文前应调用。",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}," +
                    "\"required\":[\"query\"],\"additionalProperties\":false}",
            ),
        )
        if (memoryWriteEnabled) {
            add(
                ModelToolDefinition(
                    "memory_save",
                    "保存用户明确提供且未来有帮助的稳定事实或偏好到当前项目记忆；无项目时保存为全局记忆。不要保存密钥、密码或支付信息。",
                    "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"}}," +
                        "\"required\":[\"content\"],\"additionalProperties\":false}",
                ),
            )
        }
    }

    suspend fun execute(
        call: ModelToolCall,
        spaceId: SpaceId?,
        conversationId: ConversationId,
        memoryWriteEnabled: Boolean,
    ): ToolExecutionResult = runCatching {
        val arguments = json.parseToJsonElement(call.argumentsJson.ifBlank { "{}" }).jsonObject
        when (call.name) {
            "current_time" -> buildJsonObject {
                put("dateTime", JsonPrimitive(now().atZone(zoneId()).toOffsetDateTime().toString()))
                put("zoneId", JsonPrimitive(zoneId().id))
            }.toString()
            "memory_search" -> {
                val query = arguments.string("query").take(200)
                val memories = repository.searchMemories(spaceId, query, 8)
                buildJsonObject {
                    put("results", buildJsonArray {
                        memories.forEach { memory ->
                            add(buildJsonObject {
                                put("id", JsonPrimitive(memory.id.value))
                                put("content", JsonPrimitive(memory.content))
                                put("scope", JsonPrimitive(if (memory.spaceId == null) "global" else "project"))
                            })
                        }
                    })
                }.toString()
            }
            "memory_save" -> {
                require(memoryWriteEnabled) { "Memory writing is disabled" }
                val content = arguments.string("content")
                require(!content.containsSensitiveSecret()) { "Sensitive credentials cannot be stored in memory" }
                val id = repository.saveMemory(spaceId, content, conversationId)
                buildJsonObject {
                    put("saved", JsonPrimitive(true))
                    put("id", JsonPrimitive(id.value))
                    put("scope", JsonPrimitive(if (spaceId == null) "global" else "project"))
                }.toString()
            }
            else -> error("Tool is not allowed: ${call.name}")
        }
    }.fold(
        onSuccess = { ToolExecutionResult(it, false) },
        onFailure = { ToolExecutionResult(errorJson(it.message ?: "Tool execution failed"), true) },
    )

    private fun JsonObject.string(name: String): String =
        get(name)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Missing required argument: $name")

    private fun String.containsSensitiveSecret(): Boolean {
        val normalized = lowercase()
        return normalized.contains("api key") || normalized.contains("apikey") ||
            normalized.contains("password") || normalized.contains("密码") ||
            Regex("sk-[a-z0-9_-]{12,}", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    private fun errorJson(message: String) = buildJsonObject {
        put("error", JsonPrimitive(message.take(300)))
    }.toString()
}
