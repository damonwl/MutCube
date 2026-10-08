package com.dwl.mutcube.template.builtin

import com.dwl.mutcube.template.core.TemplateManifest
import kotlinx.serialization.json.*

/** A new generic service sample; no business-specific logic is installed in the host. */
object NotesTemplate {
    private fun scalar(type: String) = buildJsonObject { put("type", type) }
    private fun obj(vararg fields: Pair<String, JsonObject>, optional: Set<String> = emptySet()) = buildJsonObject {
        put("type", "object"); put("properties", JsonObject(fields.toMap()))
        put("required", JsonArray(fields.filter { it.first !in optional }.map { JsonPrimitive(it.first) }))
        put("additionalProperties", false)
    }
    fun manifest(): TemplateManifest {
        val text = scalar("string"); val integer = scalar("integer")
        val note = obj("text" to text)
        val publicRequest = obj("request" to text, "baseRevision" to integer)
        val fullRequest = obj("request" to text, "baseRevision" to integer, "note" to note)
        val version = obj("key" to text, "expectedRevision" to integer)
        val page = obj("beforeTime" to integer, "beforeKey" to text, optional = setOf("beforeTime", "beforeKey"))
        return TemplateManifest.parse(buildJsonObject {
            put("protocolVersion", 2); put("id", "mutcube.notes"); put("version", "2.0.0")
            put("name", "智能笔记"); put("entry", "templates/notes/index.html")
            put("capabilities", JsonArray(listOf("action.run", "navigation.openChat", "navigation.close").map(::JsonPrimitive)))
            put("collections", buildJsonArray {
                for ((id, schema, policy) in listOf(Triple("notes", note, "MUTABLE"), Triple("requests", fullRequest, "IMMUTABLE_HISTORY"), Triple("documents", note, "VERSIONED"))) {
                    add(buildJsonObject { put("id", id); put("schema", schema); put("policy", policy) })
                }
            })
            put("actions", buildJsonArray {
                fun action(id: String, description: String, mode: String, collection: String, schema: JsonObject, chat: Boolean = false) {
                    add(buildJsonObject {
                        put("id", id); put("description", description); put("mode", mode); put("collection", collection)
                        put("inputSchema", schema); put("channels", buildJsonArray { add("GUI"); if (chat) add("CHAT") })
                        mapOf("notes.list" to ("查询笔记" to "请查看这个项目的笔记"),
                            "document.current" to ("查看当前文稿" to "请查看当前启用的文稿"),
                            "document.list" to ("查询文稿历史" to "请查看候选与历史文稿"),
                            "document.generate" to ("整理笔记为文稿" to "请将最近的笔记整理成清晰的文稿候选"))[id]
                            ?.let { (title, example) -> put("title", title); put("example", example) }
                        if (mode == "GENERATE") {
                            put("requestCollection", "requests")
                            put("instruction", "按照 request 整理 note 中的用户笔记，使用中文；只返回包含 text 字段的 JSON 对象。不编造用户事实。")
                            put("bindings", buildJsonArray { add(buildJsonObject {
                                put("field", "note"); put("collection", "notes"); put("source", "LATEST"); put("required", true)
                            }) })
                        }
                    })
                }
                action("notes.save", "保存笔记", "APPEND", "notes", note)
                action("notes.list", "分页读取当前项目笔记", "LIST", "notes", page, chat = true)
                action("notes.update", "修改笔记，需要原记录 revision", "UPDATE", "notes", obj("key" to text, "expectedRevision" to integer, "data" to note))
                action("notes.delete", "删除笔记，需要原记录 revision 和原生确认", "DELETE", "notes", version)
                action("document.current", "读取已确认文稿及 revision", "CURRENT", "documents", obj(), chat = true)
                action("document.list", "查看历史候选文稿", "LIST", "documents", page, chat = true)
                action("document.generate", "整理最近笔记为文稿候选。先读取 document.current，传 baseRevision；候选需用户在模板内确认，不自动生效。", "GENERATE", "documents", publicRequest, chat = true)
                action("document.confirm", "确认候选文稿，必须使用原生确认", "SELECT_VERSION", "documents", version)
            })
        }.toString())
    }
}
