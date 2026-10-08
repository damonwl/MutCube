package com.dwl.mutcube.template.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal val textSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
internal fun serviceDocument(): String = buildJsonObject {
    put("protocolVersion", 2); put("id", "test.service"); put("version", "2.0.0"); put("name", "测试")
    put("entry", "templates/test/index.html"); put("capabilities", buildJsonArray { add("action.run") })
    put("collections", buildJsonArray {
        for (id in listOf("inputs", "results")) add(buildJsonObject {
            put("id", id); put("policy", "IMMUTABLE_HISTORY"); put("schema", Json.parseToJsonElement(textSchema))
        })
    })
    put("actions", buildJsonArray { add(buildJsonObject {
        put("id", "text.generate"); put("description", "整理文本"); put("mode", "GENERATE")
        put("channels", buildJsonArray { add("GUI"); add("CHAT") }); put("inputSchema", Json.parseToJsonElement(textSchema))
        put("collection", "results"); put("requestCollection", "inputs"); put("instruction", "Return JSON")
    }) })
}.toString()

class TemplateActionManifestTest {
    @Test fun explicitServiceHasNoImplicitActions() {
        val service = TemplateManifest.parse(serviceDocument())
        assertEquals(2, service.collections.size)
        assertEquals(1, service.actions.size)
        assertEquals(setOf(ActionChannel.GUI, ActionChannel.CHAT), service.action("text.generate").channels)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsProtocolOne() { TemplateManifest.parse(serviceDocument().replace("\"protocolVersion\":2", "\"protocolVersion\":1")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsLegacyCapability() { TemplateManifest.parse(serviceDocument().replace("action.run", "interaction.run")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsLegacyDefinitionFields() {
        val root = Json.parseToJsonElement(serviceDocument()).jsonObject
        TemplateManifest.parse(JsonObject(root + ("schema" to Json.parseToJsonElement(textSchema))).toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownCollection() { TemplateManifest.parse(serviceDocument().replace("\"collection\":\"results\"", "\"collection\":\"other\"")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsRemoteEntry() { TemplateManifest.parse(serviceDocument().replace("templates/test/index.html", "https://example.com")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateActions() {
        val root = Json.parseToJsonElement(serviceDocument()).jsonObject
        val actions = root.getValue("actions").jsonArray
        TemplateManifest.parse(JsonObject(root + ("actions" to JsonArray(actions + actions))).toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownMode() { TemplateManifest.parse(serviceDocument().replace("GENERATE", "SQL")) }
}
