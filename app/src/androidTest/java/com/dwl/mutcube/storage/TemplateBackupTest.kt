package com.dwl.mutcube.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.TemplateAccessContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject

class TemplateBackupTest {
    @Test fun backupsContainWholeTransactionsDuringConcurrentWrites() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(base.cacheDir.toPath(), "backup-concurrency-test-").toFile()
        val context = IsolatedContext(base, root)
        val database = MutCubeDatabase.create(context)
        try {
            val repository = RoomConversationRepository(database)
            val service = BackupService(context, database)
            coroutineScope {
                val writer = launch(Dispatchers.IO) {
                    repeat(30) { index ->
                        database.withTransaction {
                            val project = repository.createSpace("transaction-$index")
                            repository.createWithFirstUserMessage("paired-message", project)
                        }
                        delay(5)
                    }
                }
                repeat(5) {
                    val bytes = ByteArrayOutputStream().also { service.create(it) }.toByteArray()
                    val preview = service.inspect(ByteArrayInputStream(bytes))
                    assertEquals("Partial database transaction in backup", preview.projectCount, preview.conversationCount)
                }
                writer.join()
            }
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test fun validChecksumsDoNotAllowMalformedRoomSchemaToReplaceUserData() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(base.cacheDir.toPath(), "backup-schema-test-").toFile()
        val context = IsolatedContext(base, root)
        val database = MutCubeDatabase.create(context)
        try {
            val repository = RoomConversationRepository(database)
            repository.createSpace("Keep existing data")
            val service = BackupService(context, database)
            val backup = ByteArrayOutputStream().also { service.create(it) }
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(backup.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            val corrupt = File(root, "corrupt.db")
            corrupt.writeBytes(requireNotNull(entries["database/mutcube.db"]))
            android.database.sqlite.SQLiteDatabase.openDatabase(corrupt.path, null, 0).use {
                // Keep the original room_master_table: identity hashes alone must not pass.
                it.execSQL("DROP TABLE memory_entries")
            }
            entries["database/mutcube.db"] = corrupt.readBytes()
            val manifest = JSONObject(String(requireNotNull(entries["manifest.json"])))
            manifest.getJSONObject("checksums").put("database/mutcube.db",
                MessageDigest.getInstance("SHA-256").digest(corrupt.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 255) })
            entries["manifest.json"] = manifest.toString().toByteArray()
            val invalidBackup = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { (name, bytes) ->
                        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                }
            }.toByteArray()
            assertTrue(runCatching { service.inspect(ByteArrayInputStream(invalidBackup)) }.isFailure)
            assertTrue(runCatching { service.restore(ByteArrayInputStream(invalidBackup)) }.isFailure)
            assertEquals(1, service.inspect(ByteArrayInputStream(backup.toByteArray())).projectCount)
            assertEquals("Keep existing data", repository.spaces.first().single().name)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test fun isolatedBackupRestoresFactsAndSnapshotButRevokesGrants() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(base.cacheDir.toPath(), "template-backup-test-").toFile()
        val context = IsolatedContext(base, root)
        var database = MutCubeDatabase.create(context)
        try {
            val conversations = RoomConversationRepository(database)
            val project = conversations.createSpace("isolated-test")
            val access = TemplateAccessContext(project.value, "test.service")
            val repository = TemplateRepository(database)
            val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""
            repository.enableWithGrants(TemplateBindingEntity(project.value, "test.service", "1.0.0", true),
                listOf(TemplateCollectionEntity("test.service", "results", 1, schema, "IMMUTABLE_HISTORY")))
            repository.beginRun(TemplateRunEntity("one", project.value, "test.service", "main", "{}", null, "provider", "model", "RUNNING", null, 1, 1), "1.0.0")
            repository.saveRunContext(access, "1.0.0", "one", "{\"version\":1}", listOf("results"))
            repository.saveSummary(access, "1.0.0", "one", TemplateSummaryEntity(project.value, "test.service", "results", "a".repeat(64), "derived-summary", "provider", "model", 1))
            repository.finishRunWithRecord(access, "one", "1.0.0", "results", "{\"text\":\"saved-fact\"}")
            repository.grant(TemplatePermissionEntity(project.value, "test.service", "results", "SELECT_VERSION", false))
            repository.selectVersion(access, "1.0.0", "results", "one", 0)
            val avatar = File(context.filesDir, "user_profile/123e4567-e89b-12d3-a456-426614174000.jpg")
            avatar.parentFile!!.mkdirs()
            avatar.writeText("avatar-data")
            val skillId = "123e4567-e89b-12d3-a456-426614174001"
            val skillFile = File(context.filesDir, "installed_skills/$skillId/SKILL.md")
            skillFile.parentFile!!.mkdirs()
            skillFile.writeText("skill-data")
            val installedTemplate = File(context.filesDir, "installed_templates/example.backup/manifest.json")
            installedTemplate.parentFile!!.mkdirs()
            val page = File(installedTemplate.parentFile, "templates/backup/index.html")
            page.parentFile!!.mkdirs()
            page.writeText("<html><body>Restored template</body></html>")
            val pageHash = MessageDigest.getInstance("SHA-256").digest(page.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val installedManifest = """{"packageVersion":1,"minHostVersion":"0.1.2","developer":{"name":"Backup test"},"permissions":["data.records"],"networkDomains":[],"resources":{"templates/backup/index.html":"$pageHash"},"template":{"protocolVersion":2,"id":"example.backup","version":"1.0.0","name":"Backup","entry":"templates/backup/index.html","collections":[{"id":"items","policy":"MUTABLE","schema":$schema}],"actions":[{"id":"item.list","description":"List","mode":"LIST","channels":["GUI"],"collection":"items","inputSchema":{"type":"object","properties":{"beforeTime":{"type":"integer"},"beforeKey":{"type":"string"}},"required":[],"additionalProperties":false}}],"capabilities":["action.run"]}}"""
            installedTemplate.writeText(installedManifest)
            val skillPreferences = File(root, "shared_prefs/skill_installations.xml")
            skillPreferences.parentFile!!.mkdirs()
            skillPreferences.writeText("""<?xml version="1.0" encoding="utf-8"?><map><string name="installations">[{"id":"$skillId","invocation":"AUTO"}]</string></map>""")
            val output = ByteArrayOutputStream()
            val service = BackupService(context, database)
            service.create(output)
            val preview = service.inspect(ByteArrayInputStream(output.toByteArray()))
            assertEquals(19, preview.databaseVersion)
            assertEquals(1, preview.projectCount)
            assertEquals(1, preview.templateRecordCount)
            assertEquals(1, preview.avatarCount)
            assertEquals(1, preview.skillCount)
            assertEquals(1, preview.installedTemplateCount)
            assertFalse(preview.encrypted)
            val legacy = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { source ->
                ZipOutputStream(legacy).use { destination ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        val original = source.readBytes()
                        val omitted = entry.name.startsWith("user_profile/") || entry.name.startsWith("installed_skills/") ||
                            entry.name.startsWith("installed_templates/") ||
                            entry.name == "preferences/skill_installations.xml"
                        if (!omitted) {
                            destination.putNextEntry(ZipEntry(entry.name))
                            val bytes = if (entry.name == "manifest.json") {
                                JSONObject(String(original)).apply {
                                    put("schemaVersion", 1)
                                    put("databaseVersion", 18)
                                    getJSONObject("checksums").apply {
                                        keys().asSequence().filter { it.startsWith("user_profile/") || it.startsWith("installed_skills/") ||
                                            it.startsWith("installed_templates/") ||
                                            it == "preferences/skill_installations.xml" }.toList().forEach(::remove)
                                    }
                                }.toString().toByteArray()
                            } else original
                            destination.write(bytes)
                            destination.closeEntry()
                        }
                        source.closeEntry()
                    }
                }
            }
            val legacyPreview = service.inspect(ByteArrayInputStream(legacy.toByteArray()))
            assertEquals(1, legacyPreview.schemaVersion)
            assertTrue(legacyPreview.legacyVersionWarning)
            val encrypted = ByteArrayOutputStream()
            val backupPassword = "isolated-backup-password"
            service.create(encrypted, backupPassword)
            assertTrue(service.inspect(ByteArrayInputStream(encrypted.toByteArray()), backupPassword).encrypted)
            assertTrue(runCatching { service.inspect(ByteArrayInputStream(encrypted.toByteArray()), "incorrect-password") }.isFailure)
            avatar.writeText("changed")
            skillFile.writeText("changed")
            installedTemplate.writeText("changed")
            val corrupt = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { source ->
                ZipOutputStream(corrupt).use { destination ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        val bytes = source.readBytes()
                        destination.putNextEntry(ZipEntry(entry.name))
                        destination.write(if (entry.name.startsWith("user_profile/")) "tampered".toByteArray() else bytes)
                        destination.closeEntry()
                        source.closeEntry()
                    }
                }
            }
            assertTrue(runCatching { service.restore(ByteArrayInputStream(corrupt.toByteArray())) }.isFailure)
            assertEquals("changed", avatar.readText())
            assertTrue(runCatching {
                service.inspect(ByteArrayInputStream(encrypted.toByteArray().copyOf(encrypted.size() - 1)), backupPassword)
            }.isFailure)
            val restored = service.restore(ByteArrayInputStream(encrypted.toByteArray()), backupPassword)
            assertTrue(restored.restored)
            assertEquals("avatar-data", avatar.readText())
            assertEquals("skill-data", skillFile.readText())
            assertEquals(installedManifest, installedTemplate.readText())
            assertTrue(skillPreferences.readText().contains("OFF"))
            database = MutCubeDatabase.create(context)
            val imported = TemplateRepository(database)
            val restoredTemplates = InstalledTemplateStore(context, imported)
            assertEquals("example.backup", restoredTemplates.templates.value.single().definition.manifest.id)
            assertNotNull(restoredTemplates.resource("example.backup", "templates/backup/index.html"))
            assertEquals("{\"text\":\"saved-fact\"}", imported.userRecords.first().single().json)
            assertEquals("{\"version\":1}", imported.runs(access).first().single().contextJson)
            assertTrue(imported.userPermissions.first().isEmpty())
            assertTrue(runCatching { imported.list(access, "results") }.isFailure)
            assertTrue(runCatching { imported.summary(access, "results") }.isFailure)
            assertTrue(runCatching { imported.currentVersion(access, "results") }.isFailure)
            imported.grant(TemplatePermissionEntity(project.value, "test.service", "results", "LIST", false))
            assertEquals("derived-summary", imported.summary(access, "results")!!.summary)
            imported.grant(TemplatePermissionEntity(project.value, "test.service", "results", "READ", false))
            assertEquals("one", imported.currentVersion(access, "results")!!.recordKey)
            assertTrue(runCatching { imported.selectVersion(access, "1.0.0", "results", "one", 1) }.isFailure)
        } finally {
            database.close()
            // Exact private fixture directory, never the real application database or user files.
            check(root.canonicalPath.startsWith(base.cacheDir.canonicalPath + File.separator))
            check(!Files.isSymbolicLink(root.toPath()))
            check(root.deleteRecursively())
        }
    }

    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String) =
            (if (File(name).isAbsolute) File(name) else File(root, "databases/$name")).also { it.parentFile!!.mkdirs() }
        override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
        override fun getCacheDir() = File(root, "cache").also { it.mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no_backup").also { it.mkdirs() }
        override fun getApplicationInfo() = ApplicationInfo(super.getApplicationInfo()).apply { dataDir = root.absolutePath }
    }
}
