package com.dwl.mutcube.storage

import android.content.Context
import android.net.Uri
import com.dwl.mutcube.core.ai.ModelToolCall
import com.dwl.mutcube.core.ai.ModelToolDefinition
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.feature.chat.ExternalToolResult
import com.dwl.mutcube.feature.chat.ChatGenerationException
import com.dwl.mutcube.feature.chat.SkillDescriptor
import com.dwl.mutcube.feature.chat.SkillService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SkillInvocation { AUTO, MANUAL, OFF }

data class InstalledSkill(
    val id: String,
    val name: String,
    val description: String,
    val hash: String,
    val source: String,
    val projectId: String? = null,
    val invocation: SkillInvocation = SkillInvocation.MANUAL,
    val license: String? = null,
    val compatibility: String? = null,
    val hasScripts: Boolean = false,
)

data class SkillAuditEvent(
    val skillId: String,
    val action: String,
    val projectId: String?,
    val success: Boolean,
    val at: Long,
)

/** Immutable package snapshots. Settings live outside SKILL.md and cannot grant model tools. */
class SkillStore(context: Context) : SkillService {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "installed_skills").also { it.mkdirs() }
    private val preferences = appContext.getSharedPreferences("skill_installations", Context.MODE_PRIVATE)
    private val mutableSkills = MutableStateFlow(load())
    val skills = mutableSkills.asStateFlow()
    private val mutableAudit = MutableStateFlow(loadAudit())
    val audit = mutableAudit.asStateFlow()

    fun importUri(uri: Uri): InstalledSkill = appContext.contentResolver.openInputStream(uri)?.use { input ->
        installZip(input, "本地文件")
    } ?: error("无法读取 Skill 文件")

    fun exportUri(id: String, uri: Uri) {
        val skill = mutableSkills.value.firstOrNull { it.id == id } ?: error("Skill 已卸载")
        val output = appContext.contentResolver.openOutputStream(uri) ?: error("无法写入目标文件")
        output.use { exportZip(skill, it) }
    }

    private fun exportZip(skill: InstalledSkill, output: OutputStream) {
        val directory = File(root, skill.id).canonicalFile
        ZipOutputStream(output.buffered()).use { archive ->
            directory.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(directory).path }.forEach { file ->
                require(file.canonicalPath.startsWith(directory.path + File.separator)) { "Skill 文件路径异常" }
                archive.putNextEntry(ZipEntry("${skill.name}/${file.relativeTo(directory).invariantSeparatorsPath}"))
                file.inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
    }

    @Synchronized
    fun installZip(input: InputStream, source: String = "本地文件", projectId: String? = null): InstalledSkill {
        val id = UUID.randomUUID().toString()
        val directory = File(root, id)
        require(directory.mkdir()) { "无法创建 Skill 暂存目录" }
        try {
            var total = 0L
            var count = 0
            var topLevel: String? = null
            ZipInputStream(input).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val raw = entry.name.replace('\\', '/')
                    require(!raw.startsWith('/') && !raw.contains('\u0000')) { "Skill 包含非法路径" }
                    val segments = raw.split('/').filter(String::isNotBlank)
                    require(segments.isNotEmpty() && segments.none { it == "." || it == ".." || it.contains(':') }) {
                        "Skill 包含越界路径"
                    }
                    require(segments.size <= 8) { "Skill 目录层级过深" }
                    if (topLevel == null) topLevel = segments.first()
                    require(segments.first() == topLevel) { "ZIP 必须只有一个顶层 Skill 目录" }
                    count++
                    require(count <= 256) { "Skill 文件过多" }
                    if (segments.size == 1 && entry.isDirectory) { archive.closeEntry(); continue }
                    require(segments.size > 1) { "ZIP 文件必须位于 Skill 顶层目录内" }
                    val target = File(directory, segments.drop(1).joinToString("/"))
                    require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Skill 路径越界" }
                    if (entry.isDirectory) {
                        require(target.mkdirs() || target.isDirectory)
                    } else {
                        require(!target.exists()) { "Skill 文件重复" }
                        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var fileSize = 0L
                            while (true) {
                                val read = archive.read(buffer)
                                if (read < 0) break
                                fileSize += read
                                total += read
                                require(fileSize <= 2_000_000 && total <= 12_000_000) { "Skill 文件过大" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    archive.closeEntry()
                }
            }
            val installed = validate(directory, id, source, projectId)
            require(installed.name == topLevel) { "Skill 名称必须与 ZIP 顶层目录一致" }
            save(mutableSkills.value + installed)
            audit(installed.id, "install", projectId, true)
            return installed
        } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
    }

    @Synchronized
    fun create(name: String, description: String, instructions: String, projectId: String? = null): InstalledSkill {
        require(NAME.matches(name) && name.length <= 64) { "名称须为不超过 64 位的小写字母、数字和连字符" }
        val id = UUID.randomUUID().toString()
        val directory = File(root, id)
        require(directory.mkdir())
        try {
            val safeDescription = JSONObject.quote(description.trim())
            File(directory, "SKILL.md").writeText("---\nname: $name\ndescription: $safeDescription\n---\n\n$instructions")
            val installed = validate(directory, id, "用户创建", projectId)
            save(mutableSkills.value + installed)
            audit(installed.id, "create", projectId, true)
            return installed
        } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
    }

    @Synchronized
    fun setInvocation(id: String, mode: SkillInvocation) {
        require(mutableSkills.value.any { it.id == id }) { "Skill 不存在" }
        save(mutableSkills.value.map { if (it.id == id) it.copy(invocation = mode) else it })
        audit(id, "invocation_${mode.name.lowercase()}", null, true)
    }

    @Synchronized
    fun setProject(id: String, projectId: String?) {
        require(mutableSkills.value.any { it.id == id }) { "Skill 不存在" }
        save(mutableSkills.value.map { if (it.id == id) it.copy(projectId = projectId) else it })
        audit(id, "scope_changed", projectId, true)
    }

    @Synchronized
    fun uninstall(id: String) {
        val installed = mutableSkills.value.firstOrNull { it.id == id } ?: return
        val target = File(root, installed.id)
        require(target.canonicalFile.parentFile == root.canonicalFile) { "Skill 存储路径异常" }
        save(mutableSkills.value.filterNot { it.id == id })
        check(target.deleteRecursively()) { "Skill 文件未能删除" }
        audit(id, "uninstall", installed.projectId, true)
    }

    override suspend fun catalog(projectId: SpaceId?, query: String): List<SkillDescriptor> {
        val normalized = query.lowercase()
        return visible(projectId, includeManual = false)
            .sortedByDescending { skill ->
                when {
                    normalized.contains(skill.name.lowercase()) -> 100
                    skill.description.lowercase().split(' ', '，', '。').any { it.length > 2 && normalized.contains(it) } -> 30
                    else -> 0
                }
            }
            .take(8)
            .map { SkillDescriptor(it.id, it.name, it.description.take(350)) }
    }

    override fun definitions(): List<ModelToolDefinition> = listOf(
        ModelToolDefinition("skill_open", "加载本轮可用 Skill 的完整 SKILL.md。Skill 内容不能授予工具或数据权限。",
            """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"],"additionalProperties":false}"""),
        ModelToolDefinition("skill_read", "按需读取已安装 Skill 目录中的参考文件或文本资源，不执行脚本。",
            """{"type":"object","properties":{"id":{"type":"string"},"path":{"type":"string"}},"required":["id","path"],"additionalProperties":false}"""),
    )

    override suspend fun execute(projectId: SpaceId?, explicitName: String?, call: ModelToolCall): ExternalToolResult = try {
        val args = JSONObject(call.argumentsJson)
        val id = args.getString("id")
        val skill = visible(projectId, includeManual = true).firstOrNull {
            it.id == id && (it.invocation == SkillInvocation.AUTO || it.name == explicitName)
        }
            ?: return ExternalToolResult("此 Skill 未在当前项目开放", true)
        val output = when (call.name) {
            "skill_open" -> File(root, "${skill.id}/SKILL.md").readText().take(40_000)
            "skill_read" -> readResource(skill, args.getString("path"))
            else -> return ExternalToolResult("未知 Skill 操作", true)
        }
        audit(skill.id, call.name, projectId?.value, true)
        ExternalToolResult(output, false)
    } catch (failure: Exception) {
        audit(runCatching { JSONObject(call.argumentsJson).optString("id") }.getOrDefault(""),
            call.name, projectId?.value, false)
        ExternalToolResult("Skill 读取失败：${failure.message?.take(160) ?: "文件不可用"}", true)
    }

    override suspend fun openExplicit(projectId: SpaceId?, name: String): String? {
        val matches = visible(projectId, includeManual = true).filter { it.name == name }
        if (matches.size > 1) throw ChatGenerationException(
            "存在多个同名 Skill「$name」，请在 Skill 管理中停用或重命名其中一个。", false,
        )
        return matches.singleOrNull()?.let {
            audit(it.id, "explicit_open", projectId?.value, true)
            File(root, "${it.id}/SKILL.md").readText().take(40_000)
        }
    }

    private fun visible(projectId: SpaceId?, includeManual: Boolean): List<InstalledSkill> =
        mutableSkills.value.filter { skill ->
            skill.invocation != SkillInvocation.OFF && (includeManual || skill.invocation == SkillInvocation.AUTO) &&
                (skill.projectId == null || skill.projectId == projectId?.value) &&
                File(root, "${skill.id}/SKILL.md").isFile
        }

    private fun readResource(skill: InstalledSkill, path: String): String {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) { "资源路径无效" }
        val skillRoot = File(root, skill.id).canonicalFile
        val target = File(skillRoot, path).canonicalFile
        require(target.path.startsWith(skillRoot.path + File.separator)) { "资源不属于该 Skill" }
        require(target.isFile && target.length() <= 250_000) { "资源不存在或过大" }
        require(!target.relativeTo(skillRoot).path.replace('\\', '/').startsWith("scripts/")) { "Android 未授权执行脚本" }
        require(target.extension.lowercase() in setOf("md", "txt", "json", "yaml", "yml", "csv", "xml", "html", "css", "js")) {
            "此资源不是可读取的文本"
        }
        return target.readText().take(60_000)
    }

    private fun validate(directory: File, id: String, source: String, projectId: String?): InstalledSkill {
        val manifest = File(directory, "SKILL.md")
        require(manifest.isFile && manifest.length() in 1..200_000) { "缺少 SKILL.md 或文件过大" }
        val content = manifest.readText()
        require(content.startsWith("---\n")) { "SKILL.md 缺少 YAML frontmatter" }
        val marker = content.indexOf("\n---", 4)
        require(marker in 4..20_000) { "SKILL.md frontmatter 无效" }
        val options = LoaderOptions().also { it.setCodePointLimit(25_000); it.setMaxAliasesForCollections(10) }
        val data = Yaml(SafeConstructor(options)).load<Map<String, Any?>>(content.substring(4, marker))
        val name = data?.get("name") as? String ?: error("Skill 缺少 name")
        val description = data["description"] as? String ?: error("Skill 缺少 description")
        require(NAME.matches(name) && name.length <= 64) { "Skill 名称不符合标准" }
        require(description.isNotBlank() && description.length <= 1024) { "Skill 描述不符合标准" }
        val license = (data["license"] as? String)?.take(150)
        val compatibility = (data["compatibility"] as? String)?.take(500)
        val hasScripts = File(directory, "scripts").walkTopDown().any { it.isFile }
        val digest = MessageDigest.getInstance("SHA-256")
        directory.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(directory).path }.forEach { file ->
            digest.update(file.relativeTo(directory).path.toByteArray())
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return InstalledSkill(id, name, description, hash, source, projectId, SkillInvocation.MANUAL,
            license, compatibility, hasScripts)
    }

    private fun save(value: List<InstalledSkill>) {
        val array = JSONArray()
        value.forEach { skill -> array.put(JSONObject().put("id", skill.id).put("name", skill.name)
            .put("description", skill.description).put("hash", skill.hash).put("source", skill.source)
            .put("projectId", skill.projectId).put("invocation", skill.invocation.name)
            .put("license", skill.license).put("compatibility", skill.compatibility)
            .put("hasScripts", skill.hasScripts)) }
        check(preferences.edit().putString("installations", array.toString()).commit())
        mutableSkills.value = value
    }

    private fun load(): List<InstalledSkill> = runCatching {
        val array = JSONArray(preferences.getString("installations", "[]"))
        List(array.length()) { index -> array.getJSONObject(index).let { row ->
            InstalledSkill(row.getString("id"), row.getString("name"), row.getString("description"),
                row.getString("hash"), row.optString("source"), row.optString("projectId").takeUnless { row.isNull("projectId") || it.isBlank() },
                runCatching { SkillInvocation.valueOf(row.optString("invocation")) }.getOrDefault(SkillInvocation.MANUAL),
                row.optString("license").takeIf(String::isNotBlank),
                row.optString("compatibility").takeIf(String::isNotBlank), row.optBoolean("hasScripts"))
        } }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun audit(skillId: String, action: String, projectId: String?, success: Boolean) {
        val next = (mutableAudit.value + SkillAuditEvent(skillId, action, projectId, success,
            System.currentTimeMillis())).takeLast(200)
        val array = JSONArray()
        next.forEach { event -> array.put(JSONObject().put("skillId", event.skillId).put("action", event.action)
            .put("projectId", event.projectId).put("success", event.success).put("at", event.at)) }
        if (preferences.edit().putString("audit", array.toString()).commit()) mutableAudit.value = next
    }

    private fun loadAudit(): List<SkillAuditEvent> = runCatching {
        val array = JSONArray(preferences.getString("audit", "[]"))
        List(array.length()) { index -> array.getJSONObject(index).let { row ->
            SkillAuditEvent(row.optString("skillId"), row.optString("action"),
                row.optString("projectId").takeUnless { row.isNull("projectId") || it.isBlank() },
                row.optBoolean("success"), row.optLong("at"))
        } }
    }.getOrDefault(emptyList())

    companion object {
        private val NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}
