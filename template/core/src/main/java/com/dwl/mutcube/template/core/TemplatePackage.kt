package com.dwl.mutcube.template.core

import kotlinx.serialization.json.*

enum class TemplateCapabilityTier { BASIC, SENSITIVE }

/** Package v1 is distinct from the existing Action protocolVersion=2. */
object TemplateHostCapabilities {
    val supported: Map<String, TemplateCapabilityTier> = mapOf(
        "host.permissions" to TemplateCapabilityTier.BASIC,
        "project.info" to TemplateCapabilityTier.BASIC,
        "ai.generate" to TemplateCapabilityTier.BASIC,
        "data.records" to TemplateCapabilityTier.BASIC,
        "ui.theme" to TemplateCapabilityTier.BASIC,
        "ui.notice" to TemplateCapabilityTier.BASIC,
        "ui.confirm" to TemplateCapabilityTier.BASIC,
        "navigation.openChat" to TemplateCapabilityTier.BASIC,
        "file.pick" to TemplateCapabilityTier.BASIC,
        "media.image.pick" to TemplateCapabilityTier.SENSITIVE,
        "clipboard.read" to TemplateCapabilityTier.SENSITIVE,
        "network.fetch" to TemplateCapabilityTier.SENSITIVE,
        "speech.speak" to TemplateCapabilityTier.SENSITIVE,
        "speech.recognize" to TemplateCapabilityTier.SENSITIVE,
    )
}

data class TemplateDeveloper(val name: String, val website: String?, val license: String?)

data class TemplatePackage(
    val manifest: TemplateManifest,
    val minHostVersion: String,
    val developer: TemplateDeveloper,
    val permissions: Set<String>,
    val networkDomains: Set<String>,
    val resources: Map<String, String>,
) {
    companion object {
        const val FORMAT_VERSION = 1
        private val versionPattern = Regex("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
        private val pathPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9_./-]{0,199}")
        private val domainLabelPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
        private fun validDomain(domain: String): Boolean {
            val labels = domain.split('.')
            return domain.length <= 253 && labels.size >= 2 && labels.last().any(Char::isLetter) &&
                labels.all { it.matches(domainLabelPattern) }
        }

        fun parse(text: String): TemplatePackage {
            require(text.length <= 150_000) { "Template package manifest is too large" }
            val root = Json.parseToJsonElement(text).jsonObject
            require(root.keys == setOf("packageVersion", "minHostVersion", "developer", "permissions", "networkDomains", "resources", "template")) {
                "Invalid package v1 fields"
            }
            require(root.getValue("packageVersion").jsonPrimitive.int == FORMAT_VERSION)
            val minHostVersion = root.getValue("minHostVersion").jsonPrimitive.content
            require(minHostVersion.matches(versionPattern))
            val developerJson = root.getValue("developer").jsonObject
            require("name" in developerJson && developerJson.keys.all { it in setOf("name", "website", "license") })
            val name = developerJson.getValue("name").jsonPrimitive.content.trim()
            require(name.isNotEmpty() && name.length <= 100)
            val website = developerJson["website"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            require(website == null || (website.startsWith("https://") && website.length <= 300))
            val license = developerJson["license"]?.jsonPrimitive?.content
            require(license == null || license.matches(Regex("[A-Za-z0-9.+-]{1,50}")))
            val permissions = root.getValue("permissions").jsonArray.map { it.jsonPrimitive.content }.toSet()
            require(permissions.size == root.getValue("permissions").jsonArray.size)
            require(permissions.all { it in TemplateHostCapabilities.supported }) { "Unsupported template capability" }
            if (permissions.any { it.startsWith("speech.") }) {
                require(minHostVersion.split('.').map(String::toInt).zip(listOf(0, 1, 3))
                    .firstOrNull { (current, minimum) -> current != minimum }?.let { (current, minimum) -> current > minimum } ?: true) {
                    "Speech capabilities require MutCube 0.1.3 or newer"
                }
            }
            val domains = root.getValue("networkDomains").jsonArray.map { it.jsonPrimitive.content }.toSet()
            require(domains.size == root.getValue("networkDomains").jsonArray.size && domains.size <= 10 && domains.all(::validDomain))
            require(domains.isEmpty() || "network.fetch" in permissions)
            val resources = root.getValue("resources").jsonObject.mapValues { it.value.jsonPrimitive.content.lowercase() }
            require(resources.isNotEmpty() && resources.size <= 128)
            resources.forEach { (path, hash) ->
                require(path.matches(pathPattern) && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." } && path != "manifest.json")
                require(hash.matches(Regex("[0-9a-f]{64}")))
            }
            val manifest = TemplateManifest.parse(root.getValue("template").toString())
            require(manifest.entry in resources) { "Missing template entry" }
            require(resources.keys.all { it.startsWith("templates/${manifest.entry.split('/')[1]}/") || it.startsWith("assets/") || it.startsWith("schemas/") || it.startsWith("skills/") || it == "README.md" })
            require("data.records" in permissions)
            require(manifest.actions.none { it.mode == ActionMode.GENERATE } || "ai.generate" in permissions)
            require("navigation.openChat" !in manifest.capabilities || "navigation.openChat" in permissions)
            return TemplatePackage(manifest, minHostVersion, TemplateDeveloper(name, website, license), permissions, domains, resources)
        }
    }
}
