package com.dwl.mutcube.storage

import android.content.Context
import androidx.room.withTransaction
import com.dwl.mutcube.core.database.MutCubeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray
import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(context: Context, private val database: MutCubeDatabase) {
    private val appContext = context.applicationContext
    private val operationMutex = Mutex()

    suspend fun create(output: OutputStream, password: String? = null): BackupSummary = operationMutex.withLock { withContext(Dispatchers.IO) {
        val databaseVersion = database.openHelper.readableDatabase.version
        val staging = newStagingDirectory("create")
        try {
            val entries = linkedMapOf<String, File>()
            val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
            require(databaseFile.isFile) { "数据库文件不存在" }
            val databaseCopy = File(staging, "database/$DATABASE_NAME").also { it.parentFile?.mkdirs() }
            snapshotDatabase(databaseFile, databaseCopy)
            entries["database/$DATABASE_NAME"] = databaseCopy

            EXPORT_PREFERENCE_FILES.forEach { name ->
                val source = File(appContext.applicationInfo.dataDir, "shared_prefs/$name.xml")
                if (source.isFile) {
                    val copy = File(staging, "preferences/$name.xml").also { it.parentFile?.mkdirs() }
                    source.copyTo(copy, overwrite = true)
                    entries["preferences/$name.xml"] = copy
                }
            }
            addFiles(entries, File(appContext.filesDir, "attachments"), "attachments")
            addFiles(entries, File(appContext.filesDir, "user_profile"), "user_profile")
            addFiles(entries, File(appContext.filesDir, "installed_skills"), "installed_skills")
            addFiles(entries, File(appContext.filesDir, "installed_templates"), "installed_templates")
            require(entries.values.fold(0L) { total, file ->
                total + file.length().also { require(it >= 0 && it <= MAX_UNCOMPRESSED_BYTES - total) { "备份数据超过 4 GB" } }
            } <= MAX_UNCOMPRESSED_BYTES) { "备份数据超过 4 GB" }

            // Hash and archive the same bytes, even if an avatar or package changes meanwhile.
            entries.toMap().forEach { (path, source) ->
                if (!source.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    val snapshot = File(staging, path).also { it.parentFile?.mkdirs() }
                    source.copyTo(snapshot, overwrite = true)
                    entries[path] = snapshot
                }
            }

            val checksums = JSONObject()
            entries.forEach { (path, file) -> checksums.put(path, file.sha256()) }
            val manifest = JSONObject()
                .put("schemaVersion", BACKUP_SCHEMA)
                .put("databaseVersion", databaseVersion)
                .put("createdAt", System.currentTimeMillis())
                .put("containsCredentials", false)
                .put("format", "mutcube-portable-backup")
                .put("dataCategories", JSONArray(listOf("database", "preferences", "attachments", "user_profile", "installed_skills", "installed_templates")))
                .put("checksums", checksums)
            ZipOutputStream(BackupEncryption.output(output.buffered(), password)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_PATH))
                zip.write(manifest.toString(2).toByteArray())
                zip.closeEntry()
                entries.forEach { (path, file) ->
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            BackupSummary(entries.size, entries.values.sumOf(File::length), false)
        } finally {
            staging.safeDeleteWithin(appContext.cacheDir)
        }
    } }

    suspend fun inspect(input: InputStream, password: String? = null): BackupPreview = operationMutex.withLock { withContext(Dispatchers.IO) {
        val staging = newStagingDirectory("inspect")
        try { readArchive(input, staging, password).preview }
        finally { staging.safeDeleteWithin(appContext.cacheDir) }
    } }

    suspend fun restore(input: InputStream, password: String? = null): BackupSummary = operationMutex.withLock { withContext(Dispatchers.IO) {
        val staging = newStagingDirectory("restore")
        try {
            val archive = readArchive(input, staging, password)
            val entries = archive.entries
            val restoredDatabase = requireNotNull(entries["database/$DATABASE_NAME"])
            // Imported template records are retained, but prior AI read grants never cross a restore boundary.
            SQLiteDatabase.openDatabase(restoredDatabase.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { imported ->
                imported.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'template_permissions'", null).use { cursor ->
                    if (cursor.moveToFirst()) imported.execSQL("UPDATE template_permissions SET revoked = 1")
                }
            }
            entries["preferences/skill_installations.xml"]?.let(::disableRestoredSkills)
            database.checkpointForBackup()
            val rollback = File(staging, "rollback").apply { check(mkdirs()) }
            val targets = replacementTargets(archive.preview.schemaVersion)
            targets.forEach { (path, target) -> snapshotTarget(target, File(rollback, path)) }
            database.close()
            try {
                targets.forEach { (path, target) ->
                    if (path == "database/$DATABASE_NAME-wal" || path == "database/$DATABASE_NAME-shm") {
                        check(!target.exists() || target.delete()) { "无法替换数据库日志" }
                    } else if (path.startsWith("preferences/")) {
                        entries[path]?.let { replaceFile(it, target) } ?: check(!target.exists() || target.delete()) { "无法清除旧设置：$path" }
                    } else if (path == "database/$DATABASE_NAME") {
                        replaceFile(restoredDatabase, target)
                    } else {
                        replaceDirectory(entries, path, target, staging)
                    }
                }
            } catch (failure: Throwable) {
                var rollbackComplete = true
                targets.forEach { (path, target) ->
                    runCatching { restoreSnapshot(File(rollback, path), target) }.onFailure {
                        rollbackComplete = false
                        failure.addSuppressed(it)
                    }
                }
                // Room.close() is terminal. Even after a successful file rollback, callers must
                // restart before allowing database-backed actions on the old container.
                throw BackupRestoreRequiresRestartException(rollbackComplete, failure)
            }
            BackupSummary(entries.size, entries.values.sumOf(File::length), true)
        } finally {
            staging.safeDeleteWithin(appContext.cacheDir)
        }
    } }

    private data class ParsedArchive(val entries: Map<String, File>, val preview: BackupPreview)

    private suspend fun snapshotDatabase(source: File, destination: File) {
        // Room serializes writers for this transaction. Keep the WAL with the main file;
        // checkpoint-then-copy alone loses writes committed between those two operations.
        database.withTransaction {
            source.copyTo(destination, overwrite = true)
            val wal = File(source.path + "-wal")
            if (wal.isFile) wal.copyTo(File(destination.path + "-wal"), overwrite = true)
        }
        SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READWRITE).use { snapshot ->
            snapshot.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "无法创建一致的数据库快照" }
            }
        }
    }

    private suspend fun readArchive(input: InputStream, staging: File, password: String?): ParsedArchive {
        val extracted = linkedMapOf<String, File>()
        var totalBytes = 0L
        val (archiveInput, encrypted) = BackupEncryption.input(input, password)
        ZipInputStream(archiveInput).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "备份包含无效目录条目" }
                require(entry.name == MANIFEST_PATH || entry.name.isAllowedBackupPath(BACKUP_SCHEMA)) { "备份包含未知内容" }
                require(entry.name !in extracted) { "备份包含重复文件：${entry.name}" }
                require(extracted.size < MAX_ENTRIES) { "备份文件条目过多" }
                val target = File(staging, entry.name).canonicalFile
                require(target.path.startsWith(staging.canonicalPath + File.separator)) { "备份路径不安全" }
                check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "备份解压后过大" }
                        if (entry.name == MANIFEST_PATH) require(target.length() + count <= MAX_MANIFEST_BYTES) { "备份清单过大" }
                        output.write(buffer, 0, count)
                    }
                }
                extracted[entry.name] = target
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(requireNotNull(extracted[MANIFEST_PATH]) { "备份缺少清单" }.readText())
        val schemaVersion = manifest.getInt("schemaVersion")
        require(schemaVersion in 1..BACKUP_SCHEMA) { "不支持此备份版本" }
        if (schemaVersion >= 2) require(manifest.optString("format") == "mutcube-portable-backup") { "未知备份格式" }
        require(!manifest.optBoolean("containsCredentials", true)) { "拒绝导入包含凭据的备份" }
        val checksums = manifest.getJSONObject("checksums")
        val entries = extracted.filterKeys { it != MANIFEST_PATH }
        require(entries.keys == checksums.keys().asSequence().toSet()) { "备份清单与内容不一致" }
        require(entries.keys.all { it.isAllowedBackupPath(schemaVersion) }) { "此版本的备份包含不支持的文件" }
        entries.forEach { (path, file) -> require(file.sha256() == checksums.getString(path)) { "备份校验失败：$path" } }
        val restoredDatabase = requireNotNull(entries["database/$DATABASE_NAME"]) { "备份缺少数据库" }
        val supportedDatabaseVersion = database.openHelper.readableDatabase.version
        val declaredVersion = manifest.getInt("databaseVersion")
        val preview = SQLiteDatabase.openDatabase(restoredDatabase.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { imported ->
            require(imported.version in 1..supportedDatabaseVersion) { "备份来自更高版本的 MutCube" }
            val legacyVersionBug = schemaVersion == 1 && declaredVersion == 18 && imported.version == 19
            require(imported.version == declaredVersion || legacyVersionBug) { "备份数据库版本与清单不一致" }
            imported.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "备份数据库完整性校验失败" }
            }
            BackupPreview(
                schemaVersion = schemaVersion,
                databaseVersion = imported.version,
                createdAt = manifest.getLong("createdAt"),
                conversationCount = imported.countRows("conversations"),
                projectCount = imported.countRows("spaces"),
                memoryCount = imported.countRows("memory_entries"),
                templateRecordCount = imported.countRows("template_records"),
                attachmentCount = entries.keys.count { it.startsWith("attachments/") },
                avatarCount = entries.keys.count { it.startsWith("user_profile/") },
                skillCount = entries.keys.mapNotNull { it.takeIf { path -> path.startsWith("installed_skills/") }?.split('/')?.getOrNull(1) }.distinct().size,
                installedTemplateCount = entries.keys.mapNotNull { it.takeIf { path -> path.startsWith("installed_templates/") }?.split('/')?.getOrNull(1) }.distinct().size,
                entryCount = entries.size,
                bytes = entries.values.sumOf(File::length),
                legacyVersionWarning = legacyVersionBug,
                encrypted = encrypted,
            )
        }
        // Do not trust an imported Room identity hash: it can survive schema corruption.
        // Without the identity table Room validates the actual schema and recreates the hash.
        SQLiteDatabase.openDatabase(restoredDatabase.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE IF EXISTS room_master_table")
        }
        val validationDatabase = MutCubeDatabase.create(appContext, restoredDatabase.absolutePath)
        try {
            validationDatabase.openHelper.writableDatabase
            validationDatabase.checkpointForBackup()
        } finally {
            validationDatabase.close()
        }
        return ParsedArchive(entries, preview)
    }

    private fun SQLiteDatabase.countRows(table: String): Int = runCatching {
        rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }.getOrDefault(0)

    private fun addFiles(entries: MutableMap<String, File>, root: File, prefix: String) {
        if (!root.exists()) return
        require(root.isDirectory && !java.nio.file.Files.isSymbolicLink(root.toPath())) { "数据目录不安全：$prefix" }
        root.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) &&
                (prefix != "installed_templates" || !it.relativeTo(root).invariantSeparatorsPath.startsWith("."))
            }.forEach { source ->
                val path = "$prefix/${source.relativeTo(root).invariantSeparatorsPath}"
                require(path.isAllowedBackupPath(BACKUP_SCHEMA)) { "数据文件路径不安全：$path" }
                require(entries.size < MAX_ENTRIES) { "备份文件过多" }
                entries[path] = source
            }
    }

    private fun replacementTargets(schemaVersion: Int): LinkedHashMap<String, File> = linkedMapOf<String, File>().apply {
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        put("database/$DATABASE_NAME", databaseFile)
        put("database/$DATABASE_NAME-wal", File(databaseFile.path + "-wal"))
        put("database/$DATABASE_NAME-shm", File(databaseFile.path + "-shm"))
        (if (schemaVersion >= 2) PREFERENCE_FILES else LEGACY_PREFERENCE_FILES).forEach { name ->
            put("preferences/$name.xml", File(appContext.applicationInfo.dataDir, "shared_prefs/$name.xml"))
        }
        put("attachments", File(appContext.filesDir, "attachments"))
        if (schemaVersion >= 2) {
            put("user_profile", File(appContext.filesDir, "user_profile"))
            put("installed_skills", File(appContext.filesDir, "installed_skills"))
        }
        if (schemaVersion >= 3) put("installed_templates", File(appContext.filesDir, "installed_templates"))
    }

    private fun snapshotTarget(target: File, backup: File) {
        if (!target.exists()) return
        require(!java.nio.file.Files.isSymbolicLink(target.toPath())) { "现有数据包含链接：${target.name}" }
        backup.parentFile?.mkdirs()
        if (target.isDirectory) {
            target.walkTopDown().forEach { require(!java.nio.file.Files.isSymbolicLink(it.toPath())) { "现有数据包含链接：${it.name}" } }
            check(target.copyRecursively(backup, overwrite = false)) { "无法暂存现有数据：${target.name}" }
        } else target.copyTo(backup, overwrite = false)
    }

    private fun restoreSnapshot(backup: File, target: File) {
        if (target.exists()) target.safeDeleteWithin(requireNotNull(target.parentFile))
        if (!backup.exists()) return
        target.parentFile?.mkdirs()
        if (backup.isDirectory) check(backup.copyRecursively(target, overwrite = false)) { "无法回滚 ${target.name}" }
        else backup.copyTo(target, overwrite = false)
    }

    private fun replaceDirectory(entries: Map<String, File>, prefix: String, target: File, staging: File) {
        val replacement = File(staging, "replacement/$prefix").apply { check(mkdirs()) }
        entries.filterKeys { it.startsWith("$prefix/") }.forEach { (path, source) ->
            val destination = File(replacement, path.removePrefix("$prefix/"))
            check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
            source.copyTo(destination)
        }
        if (target.exists()) target.safeDeleteWithin(appContext.filesDir)
        target.parentFile?.mkdirs()
        check(replacement.renameTo(target)) { "无法恢复 $prefix" }
    }

    private fun disableRestoredSkills(file: File) {
        val values = linkedMapOf<String, String>()
        file.inputStream().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "string") {
                    val key = requireNotNull(parser.getAttributeValue(null, "name"))
                    values[key] = parser.nextText()
                }
            }
        }
        val installations = JSONArray(values["installations"] ?: "[]")
        for (index in 0 until installations.length()) {
            installations.getJSONObject(index).put("invocation", SkillInvocation.OFF.name)
        }
        values["installations"] = installations.toString()
        file.outputStream().use { output ->
            val serializer = Xml.newSerializer().apply { setOutput(output, "UTF-8") }
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "map")
            values.forEach { (key, value) ->
                serializer.startTag(null, "string")
                serializer.attribute(null, "name", key)
                serializer.text(value)
                serializer.endTag(null, "string")
            }
            serializer.endTag(null, "map")
            serializer.endDocument()
        }
    }

    private fun replaceFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        val replacement = File(target.parentFile, "${target.name}.restore-${UUID.randomUUID()}")
        source.copyTo(replacement, overwrite = true)
        check(replacement.renameTo(target) || run { replacement.copyTo(target, overwrite = true); replacement.delete() }) {
            "无法恢复 ${target.name}"
        }
    }

    private fun newStagingDirectory(operation: String) =
        File(appContext.cacheDir, "mutcube-backup-$operation-${UUID.randomUUID()}").apply { check(mkdirs()) }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.isAllowedBackupPath(schemaVersion: Int) =
        this == "database/$DATABASE_NAME" ||
            (startsWith("preferences/") && endsWith(".xml") && removePrefix("preferences/").removeSuffix(".xml") in
                if (schemaVersion >= 2) PREFERENCE_FILES else LEGACY_PREFERENCE_FILES) ||
            (startsWith("attachments/") && substringAfter("attachments/").matches(SAFE_FILE_NAME)) ||
            (schemaVersion >= 2 && startsWith("user_profile/") && substringAfter("user_profile/").matches(SAFE_FILE_NAME)) ||
            (schemaVersion >= 2 && startsWith("installed_skills/") && removePrefix("installed_skills/").split('/').let { parts ->
                parts.size in 2..8 && parts.first().matches(SKILL_ID) && parts.drop(1).all { part ->
                    part.isNotBlank() && part != "." && part != ".." && part.length <= 160 &&
                        part.none { it == '\\' || it == ':' || it.code < 32 }
                }
            }) ||
            (schemaVersion >= 3 && startsWith("installed_templates/") && removePrefix("installed_templates/").split('/').let { parts ->
                parts.size in 2..8 && parts.first().matches(TEMPLATE_ID) && parts.drop(1).all { part ->
                    part.matches(SAFE_FILE_NAME)
                }
            })

    private fun File.safeDeleteWithin(root: File) {
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = canonicalFile
        require(canonicalTarget != canonicalRoot && canonicalTarget.path.startsWith(canonicalRoot.path + File.separator))
        if (java.nio.file.Files.isSymbolicLink(toPath())) {
            check(delete()) { "无法删除备份临时链接" }
            return
        }
        listFiles()?.forEach { it.safeDeleteWithin(canonicalRoot) }
        check(delete()) { "无法清理备份临时文件：${name}" }
    }

    companion object {
        private const val BACKUP_SCHEMA = 3
        private const val DATABASE_NAME = "mutcube.db"
        private const val MANIFEST_PATH = "manifest.json"
        private const val MAX_ENTRIES = 10_000
        private const val MAX_MANIFEST_BYTES = 1_000_000L
        private const val MAX_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]{1,160}")
        private val SKILL_ID = Regex("[a-f0-9-]{36}")
        private val TEMPLATE_ID = Regex("[a-z][a-z0-9_.-]{0,79}")
        private val LEGACY_PREFERENCE_FILES = setOf(
            "model_settings", "provider_profiles", "display_settings", "mcp_servers", "extension_audit",
            ContextLibraryStore.PREFERENCES,
            RequestLogStore.PREFERENCES,
            RemoteBackupConfigStore.PREFERENCES,
            BackupReminderStore.PREFERENCES,
            SpeechServiceSettingsStore.PREFERENCES,
        )
        private val PREFERENCE_FILES = LEGACY_PREFERENCE_FILES + "skill_installations"
        // Older backups remain restorable, but diagnostics are never exported by new backups.
        private val EXPORT_PREFERENCE_FILES = PREFERENCE_FILES - setOf("extension_audit", RequestLogStore.PREFERENCES)
    }
}

class BackupRestoreRequiresRestartException(val rollbackComplete: Boolean, cause: Throwable) :
    IllegalStateException(if (rollbackComplete) "恢复失败，已回滚；请重新启动应用" else
        "恢复失败且回滚不完整；请保留原备份并重新启动应用后检查数据", cause)

data class BackupSummary(val entryCount: Int, val bytes: Long, val restored: Boolean)

data class BackupPreview(
    val schemaVersion: Int,
    val databaseVersion: Int,
    val createdAt: Long,
    val conversationCount: Int,
    val projectCount: Int,
    val memoryCount: Int,
    val templateRecordCount: Int,
    val attachmentCount: Int,
    val avatarCount: Int,
    val skillCount: Int,
    val installedTemplateCount: Int,
    val entryCount: Int,
    val bytes: Long,
    val legacyVersionWarning: Boolean,
    val encrypted: Boolean,
)
