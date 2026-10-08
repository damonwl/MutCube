package com.dwl.mutcube.template.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishReaderPackageTest {
    @Test fun sdkExamplePassesRealHostManifestValidation() {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "template-sdk/examples/english-reader/manifest.json") }
            .first(File::isFile)
        val root = Json.parseToJsonElement(source.readText()).jsonObject
        val resources = buildJsonObject { put("templates/english/index.html", "0".repeat(64)) }
        val manifest = TemplatePackage.parse(JsonObject(root + ("resources" to resources)).toString())
        assertEquals("example.english-reader", manifest.manifest.id)
        assertEquals(setOf("en.wiktionary.org", "zh.wiktionary.org"), manifest.networkDomains)
        assertTrue(manifest.manifest.actions.any { it.id == "review.generate" })
    }
}
