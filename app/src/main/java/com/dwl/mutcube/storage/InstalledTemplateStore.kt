package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.template.core.TemplatePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class InstalledTemplate(val definition: TemplatePackage, val directory: File, val packageHash: String)
data class TemplatePackageInspection(val definition: TemplatePackage, val totalBytes: Long)

/** User-owned packages are stored separately from Room data; uninstall never deletes records. */
class InstalledTemplateStore(context: Context, private val repository: TemplateRepository, private val reservedIds: Set<String> = emptySet()) {
    private val hostVersion = requireNotNull(context.packageManager.getPackageInfo(context.packageName, 0).versionName)
    private val root = File(context.filesDir, "installed_templates").also { check(it.isDirectory || it.mkdirs()) }
    private val mutex = Mutex()
    private val mutableTemplates = MutableStateFlow(scan())
    val templates = mutableTemplates.asStateFlow()

    suspend fun inspect(input: InputStream): TemplatePackageInspection = withContext(Dispatchers.IO) {
        val hashes = linkedMapOf<String, String>()
        var manifestText: String? = null
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                require(name.length in 1..200 && !name.startsWith('/') && !name.split('/').any { it.isEmpty() || it == "." || it == ".." || ':' in it }) { "模板包路径无效" }
                require(!entry.isDirectory && name !in hashes && hashes.size < 129) { "模板包文件重复或过多" }
                val digest = MessageDigest.getInstance("SHA-256")
                val manifest = if (name == "manifest.json") java.io.ByteArrayOutputStream() else null
                var fileBytes = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    fileBytes += count; total += count
                    require(fileBytes <= MAX_FILE_BYTES && total <= MAX_PACKAGE_BYTES) { "模板包过大" }
                    digest.update(buffer, 0, count)
                    manifest?.write(buffer, 0, count)
                }
                hashes[name] = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                if (manifest != null) manifestText = manifest.toString(Charsets.UTF_8.name())
            }
        }
        val definition = TemplatePackage.parse(requireNotNull(manifestText) { "模板包缺少 manifest.json" })
        require(definition.manifest.id !in reservedIds) { "模板 ID 已被内置模板占用" }
        require(hashes.keys == definition.resources.keys + "manifest.json") { "模板包文件与 Manifest 不一致" }
        require(definition.resources.all { (path, expected) -> hashes[path] == expected }) { "模板包资源校验失败" }
        require(compareVersions(definition.minHostVersion, hostVersion.substringBefore('-')) <= 0) { "当前 MutCube 版本低于模板要求" }
        TemplatePackageInspection(definition, total)
    }

    suspend fun install(input: InputStream, expected: TemplatePackage? = null): InstalledTemplate = withContext(Dispatchers.IO) {
        mutex.withLock {
            val staging = File(root, ".incoming-${UUID.randomUUID()}")
            check(staging.mkdir())
            try {
                val seen = mutableSetOf<String>()
                var bytes = 0L
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        require(name.length in 1..200 && !name.startsWith('/') && !name.split('/').any { it.isEmpty() || it == "." || it == ".." || ':' in it }) { "模板包路径无效" }
                        require(!entry.isDirectory) { "模板包只允许文件条目" }
                        require(seen.add(name) && seen.size <= 129) { "模板包文件重复或过多" }
                        val target = File(staging, name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                        check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                        var fileBytes = 0L
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val size = zip.read(buffer)
                                if (size < 0) break
                                fileBytes += size
                                bytes += size
                                require(fileBytes <= MAX_FILE_BYTES && bytes <= MAX_PACKAGE_BYTES) { "模板包过大" }
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                }
                require("manifest.json" in seen)
                val packageText = File(staging, "manifest.json").readText()
                val definition = TemplatePackage.parse(packageText)
                require(expected == null || definition == expected) { "模板包在预览后发生变化，请重新导入" }
                require(definition.manifest.id !in reservedIds) { "模板 ID 已被内置模板占用" }
                require(seen == definition.resources.keys + "manifest.json") { "模板包文件与 Manifest 不一致" }
                definition.resources.forEach { (path, expected) ->
                    require(File(staging, path).sha256() == expected) { "模板包资源校验失败：$path" }
                }
                require(compareVersions(definition.minHostVersion, hostVersion.substringBefore('-')) <= 0) { "当前 MutCube 版本低于模板要求" }
                val existing = mutableTemplates.value.firstOrNull { it.definition.manifest.id == definition.manifest.id }
                require(existing != null || mutableTemplates.value.size < 64) { "最多安装 64 个第三方模板" }
                if (existing != null) {
                    require(compareVersions(definition.manifest.version, existing.definition.manifest.version) > 0) { "只能安装更新版本" }
                    require(definition.developer.name == existing.definition.developer.name) { "开发者名称已变化，不能作为原模板升级" }
                    // Package v1 allows UI/Action upgrades but not silent alteration of existing collection schemas.
                    existing.definition.manifest.collections.forEach { old ->
                        require(definition.manifest.collections.any { it.id == old.id && it.policy == old.policy && it.schema == old.schema }) {
                            "集合结构变更需要未来的显式迁移协议"
                        }
                    }
                }
                val destination = File(root, definition.manifest.id)
                val previous = File(root, ".previous-${UUID.randomUUID()}")
                if (destination.exists()) check(destination.renameTo(previous))
                try {
                    check(staging.renameTo(destination))
                } catch (failure: Exception) {
                    if (previous.exists()) check(previous.renameTo(destination))
                    throw failure
                }
                if (previous.exists()) check(previous.deleteRecursively())
                val installed = InstalledTemplate(definition, destination, File(destination, "manifest.json").sha256())
                mutableTemplates.value = scan()
                installed
            } finally {
                if (staging.exists()) staging.deleteRecursively()
            }
        }
    }

    suspend fun uninstall(id: String, deleteData: Boolean = false, confirmedId: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val installed = mutableTemplates.value.firstOrNull { it.definition.manifest.id == id } ?: return@withLock
            check(installed.directory.canonicalFile.parentFile == root.canonicalFile)
            if (deleteData) require(confirmedId == id) { "请确认模板 ID" }
            val removing = File(root, ".removing-${UUID.randomUUID()}")
            check(installed.directory.renameTo(removing))
            var dataCommitted = false
            try {
                if (deleteData) repository.deleteInstalledTemplateData(id, requireNotNull(confirmedId))
                else repository.userBindings.first().filter { it.templateId == id && it.enabled }.forEach { binding ->
                    repository.revoke(TemplateAccessContext(binding.projectId, id))
                    repository.bind(binding.copy(enabled = false))
                }
                dataCommitted = true
                mutableTemplates.value = scan()
                check(removing.deleteRecursively())
            } catch (failure: Exception) {
                if (!dataCommitted && removing.exists()) check(removing.renameTo(installed.directory))
                throw failure
            }
        }
    }

    fun resource(id: String, path: String): File? {
        val installed = mutableTemplates.value.firstOrNull { it.definition.manifest.id == id } ?: return null
        if (path !in installed.definition.resources) return null
        val source = File(installed.directory, path)
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) return null
        return source.canonicalFile.takeIf {
            it.isFile && it.path.startsWith(installed.directory.canonicalPath + File.separator)
        }
    }

    private fun scan(): List<InstalledTemplate> = root.listFiles().orEmpty().filter {
        it.isDirectory && !it.name.startsWith('.') && !java.nio.file.Files.isSymbolicLink(it.toPath())
    }
        .mapNotNull { directory -> runCatching {
            val manifestFile = File(directory, "manifest.json")
            require(!java.nio.file.Files.isSymbolicLink(manifestFile.toPath()))
            val definition = TemplatePackage.parse(manifestFile.readText())
            require(definition.manifest.id == directory.name && definition.manifest.id !in reservedIds)
            require(definition.resources.all { (path, hash) ->
                val file = File(directory, path)
                !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.canonicalPath.startsWith(directory.canonicalPath + File.separator) && file.sha256() == hash
            })
            InstalledTemplate(definition, directory, manifestFile.sha256())
        }.getOrNull() }

    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 12L * 1024 * 1024
        private fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun compareVersions(left: String, right: String): Int = left.split('.').map(String::toInt)
            .zip(right.split('.').map(String::toInt)).firstOrNull { it.first != it.second }
            ?.let { it.first.compareTo(it.second) } ?: 0
    }
}
