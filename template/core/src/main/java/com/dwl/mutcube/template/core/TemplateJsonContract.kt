package com.dwl.mutcube.template.core

import kotlinx.serialization.json.*

/** Deliberately bounded contract subset; unsupported schema keywords fail closed. */
object TemplateJsonContract {
    private val keywords = setOf("type", "properties", "required", "additionalProperties", "items", "title", "description")

    fun validateSchema(schema: JsonObject, depth: Int = 0) {
        require(depth <= 12 && schema.keys.all { it in keywords }) { "Unsupported collection schema" }
        require(schema["type"]?.jsonPrimitive?.content in setOf("object", "array", "string", "number", "integer", "boolean"))
        when (schema["type"]?.jsonPrimitive?.content) {
            "object" -> {
                val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
                require(properties.size <= 100)
                properties.values.forEach { validateSchema(it.jsonObject, depth + 1) }
                schema["required"]?.jsonArray?.forEach { require(it.jsonPrimitive.content in properties) }
                require(schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull == false) { "Object contracts must prohibit undeclared fields" }
            }
            "array" -> validateSchema(requireNotNull(schema["items"]).jsonObject, depth + 1)
        }
    }

    fun validate(schemaJson: String, valueJson: String) {
        val schema = Json.parseToJsonElement(schemaJson).jsonObject
        validateSchema(schema)
        validateValue(schema, Json.parseToJsonElement(valueJson), 0)
    }

    private fun validateValue(schema: JsonObject, value: JsonElement, depth: Int) {
        require(depth <= 12 && value != JsonNull)
        when (schema.getValue("type").jsonPrimitive.content) {
            "object" -> {
                require(value is JsonObject)
                val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
                require(value.keys.all { it in properties }) { "Undeclared data field" }
                schema["required"]?.jsonArray?.forEach { require(it.jsonPrimitive.content in value) { "Required field missing" } }
                value.forEach { (key, field) -> validateValue(properties.getValue(key).jsonObject, field, depth + 1) }
            }
            "array" -> {
                require(value is JsonArray && value.size <= 1_000)
                value.forEach { validateValue(schema.getValue("items").jsonObject, it, depth + 1) }
            }
            "string" -> require(value is JsonPrimitive && value.isString)
            "boolean" -> require(value is JsonPrimitive && !value.isString && value.booleanOrNull != null)
            "number" -> require(value is JsonPrimitive && !value.isString && value.doubleOrNull?.isFinite() == true)
            "integer" -> require(value is JsonPrimitive && !value.isString && value.longOrNull != null)
        }
    }
}
