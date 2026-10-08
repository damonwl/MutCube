package com.dwl.mutcube.template.core

import kotlinx.serialization.json.*

enum class CollectionPolicy { IMMUTABLE_HISTORY, MUTABLE, VERSIONED }
data class TemplateCollection(val id: String, val schema: String, val policy: CollectionPolicy)

/** Protocol 2 only: explicitly declared service data and operations, no implicit main interaction. */
data class TemplateManifest(
    val id: String, val version: String, val name: String, val entry: String,
    val collections: List<TemplateCollection>, val actions: List<TemplateAction>, val capabilities: Set<String>,
) {
    fun action(id: String) = requireNotNull(actions.find { it.id == id }) { "Undeclared action" }
    fun collection(id: String) = requireNotNull(collections.find { it.id == id }) { "Undeclared collection" }
    fun preparedSchema(action: TemplateAction): String {
        val input = Json.parseToJsonElement(action.inputSchema).jsonObject
        val properties = input.getValue("properties").jsonObject.toMutableMap()
        val required = input.getValue("required").jsonArray.toMutableList()
        action.bindings.forEach {
            properties[it.field] = Json.parseToJsonElement(collection(it.collection).schema)
            if (it.required) required.add(JsonPrimitive(it.field))
        }
        return JsonObject(input + mapOf("properties" to JsonObject(properties), "required" to JsonArray(required))).toString()
    }
    companion object {
        fun parse(text: String): TemplateManifest {
            require(text.length <= 100_000)
            val root = Json.parseToJsonElement(text).jsonObject
            require(root.keys == setOf("protocolVersion", "id", "version", "name", "entry", "collections", "actions", "capabilities")) { "Protocol 2 fields required; legacy definitions are not supported" }
            require(!root.getValue("protocolVersion").jsonPrimitive.isString && root.getValue("protocolVersion").jsonPrimitive.intOrNull == 2) { "Only template protocol 2 is supported" }
            fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.let { require(it.isString); it.content }
            fun identity(value: String) { require(value.matches(Regex("[a-z][a-z0-9_.-]{0,79}"))) }
            fun schema(value: JsonElement): String {
                TemplateJsonContract.validateSchema(value.jsonObject)
                require(value.jsonObject["type"]?.jsonPrimitive?.content == "object")
                return value.toString()
            }
            val id = root.string("id").also(::identity)
            val version = root.string("version").also { require(it.matches(Regex("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}"))) }
            val name = root.string("name").also { require(it.isNotBlank() && it.length <= 100) }
            val entry = root.string("entry").also { require(it.matches(Regex("templates/[a-z0-9-]+/index\\.html"))) }
            val capabilities = root.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content }.toSet()
            require("action.run" in capabilities && capabilities.all { it in setOf("action.run", "navigation.openChat", "navigation.close") })
            val collections = root.getValue("collections").jsonArray.map {
                val definition = it.jsonObject
                require(definition.keys == setOf("id", "schema", "policy"))
                TemplateCollection(definition.string("id").also(::identity), schema(definition.getValue("schema")), CollectionPolicy.valueOf(definition.string("policy")))
            }
            require(collections.size in 1..30 && collections.map { it.id }.distinct().size == collections.size)
            val actions = root.getValue("actions").jsonArray.map {
                val definition = it.jsonObject
                val required = setOf("id", "description", "mode", "channels", "inputSchema", "collection")
                require(definition.keys.containsAll(required) && definition.keys.all { it in required + setOf("requestCollection", "instruction", "bindings", "title", "example") })
                val bindings = definition["bindings"]?.jsonArray.orEmpty().map { item ->
                    val binding = item.jsonObject
                    require(binding.keys == setOf("field", "collection", "source", "required"))
                    ActionBinding(binding.string("field").also(::identity), binding.string("collection"),
                        BindingSource.valueOf(binding.string("source")), binding.getValue("required").jsonPrimitive.boolean)
                }
                val channels = definition.getValue("channels").jsonArray.map { channel -> ActionChannel.valueOf(channel.jsonPrimitive.content) }.toSet()
                require(channels.isNotEmpty())
                TemplateAction(definition.string("id").also(::identity), definition.string("description").also { description -> require(description.isNotBlank() && description.length <= 2000) },
                    ActionMode.valueOf(definition.string("mode")), channels, schema(definition.getValue("inputSchema")), definition.string("collection"),
                    definition["requestCollection"]?.jsonPrimitive?.content, definition["instruction"]?.jsonPrimitive?.content, bindings,
                    definition["title"]?.jsonPrimitive?.content?.also { require(it.isNotBlank() && it.length <= 80) },
                    definition["example"]?.jsonPrimitive?.content?.also { require(it.isNotBlank() && it.length <= 500) })
            }
            require(actions.size in 1..50 && actions.map { it.id }.distinct().size == actions.size)
            return TemplateManifest(id, version, name, entry, collections, actions, capabilities).also { manifest ->
                actions.forEach { action ->
                    val target = manifest.collection(action.collection)
                    require(action.bindings.map { it.field }.distinct().size == action.bindings.size)
                    val properties = Json.parseToJsonElement(action.inputSchema).jsonObject.getValue("properties").jsonObject
                    val input = Json.parseToJsonElement(action.inputSchema).jsonObject
                    val requiredFields = input.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
                    action.bindings.forEach { binding ->
                        require(binding.field !in properties)
                        val source = manifest.collection(binding.collection)
                        require(binding.source != BindingSource.CURRENT || source.policy == CollectionPolicy.VERSIONED)
                    }
                    when (action.mode) {
                        ActionMode.GENERATE -> {
                            require(!action.instruction.isNullOrBlank())
                            val requests = manifest.collection(requireNotNull(action.requestCollection))
                            require(requests.id != target.id && requests.policy == CollectionPolicy.IMMUTABLE_HISTORY)
                            require(Json.parseToJsonElement(requests.schema) == Json.parseToJsonElement(manifest.preparedSchema(action)))
                            if (target.policy == CollectionPolicy.VERSIONED) {
                                require(properties["baseRevision"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "integer")
                                require(Json.parseToJsonElement(action.inputSchema).jsonObject.getValue("required").jsonArray.any { field -> field.jsonPrimitive.content == "baseRevision" })
                            }
                        }
                        ActionMode.APPEND -> require(Json.parseToJsonElement(manifest.preparedSchema(action)) == Json.parseToJsonElement(target.schema))
                        ActionMode.UPDATE, ActionMode.DELETE -> require(target.policy == CollectionPolicy.MUTABLE)
                        ActionMode.SELECT_VERSION, ActionMode.CURRENT -> require(target.policy == CollectionPolicy.VERSIONED)
                        ActionMode.LIST -> Unit
                    }
                    when (action.mode) {
                        ActionMode.UPDATE, ActionMode.DELETE, ActionMode.SELECT_VERSION -> {
                            val fields = if (action.mode == ActionMode.UPDATE) setOf("key", "expectedRevision", "data") else setOf("key", "expectedRevision")
                            require(properties.keys == fields && requiredFields == fields && action.bindings.isEmpty())
                            require(properties.getValue("key").jsonObject.getValue("type").jsonPrimitive.content == "string")
                            require(properties.getValue("expectedRevision").jsonObject.getValue("type").jsonPrimitive.content == "integer")
                            if (action.mode == ActionMode.UPDATE) require(properties.getValue("data") == Json.parseToJsonElement(target.schema))
                        }
                        ActionMode.LIST -> {
                            require(properties.keys == setOf("beforeTime", "beforeKey") && requiredFields.isEmpty() && action.bindings.isEmpty())
                            require(properties.getValue("beforeTime").jsonObject.getValue("type").jsonPrimitive.content == "integer")
                            require(properties.getValue("beforeKey").jsonObject.getValue("type").jsonPrimitive.content == "string")
                        }
                        ActionMode.CURRENT -> require(properties.isEmpty() && requiredFields.isEmpty() && action.bindings.isEmpty())
                        else -> Unit
                    }
                    if (action.mode != ActionMode.GENERATE) require(action.requestCollection == null && action.instruction == null)
                    require(action.mode !in setOf(ActionMode.APPEND, ActionMode.UPDATE, ActionMode.DELETE, ActionMode.SELECT_VERSION) || ActionChannel.CHAT !in action.channels) { "Chat mutations must use a declared AI proposal, not direct writes" }
                }
            }
        }
    }
}
