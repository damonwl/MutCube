package com.dwl.mutcube.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.ai.ModelToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SkillStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun standardPackageIsManualAndProjectScoped() = runBlocking {
        val store = SkillStore(context)
        val name = "test-skill-${UUID.randomUUID().toString().take(8)}"
        val project = SpaceId(UUID.randomUUID().toString())
        val installed = store.installZip(ByteArrayInputStream(zip(name,
            "---\nname: $name\ndescription: Review workout records when requested.\n---\nDo the review.",
            "references/DETAILS.md" to "Only use verified records.")), projectId = project.value)
        try {
            assertTrue(store.catalog(project, "workout").isEmpty())
            assertNotNull(store.openExplicit(project, name))
            assertNull(store.openExplicit(SpaceId("another-project"), name))
            store.setInvocation(installed.id, SkillInvocation.AUTO)
            assertEquals(installed.id, store.catalog(project, "workout").single().id)
            assertTrue(store.catalog(SpaceId("another-project"), "workout").isEmpty())
            val reference = store.execute(project, null, ModelToolCall("read", "skill_read",
                """{"id":"${installed.id}","path":"references/DETAILS.md"}"""))
            assertFalse(reference.isError)
            assertTrue(reference.output.contains("verified records"))
            val blocked = store.execute(project, null, ModelToolCall("read", "skill_read",
                """{"id":"${installed.id}","path":"../../secret.txt"}"""))
            assertTrue(blocked.isError)
            assertFalse(installed.hash.isBlank())
        } finally {
            store.uninstall(installed.id)
        }
    }

    @Test
    fun zipTraversalIsRejected() {
        val name = "test-skill-${UUID.randomUUID().toString().take(8)}"
        val packageBytes = zip(name, "---\nname: $name\ndescription: A valid description.\n---\nBody",
            "../outside.txt" to "bad")
        val failure = runCatching { SkillStore(context).installZip(ByteArrayInputStream(packageBytes)) }.exceptionOrNull()
        assertNotNull(failure)
    }

    private fun zip(name: String, manifest: String, vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { archive ->
            (listOf("SKILL.md" to manifest) + files).forEach { (path, content) ->
                archive.putNextEntry(ZipEntry("$name/$path"))
                archive.write(content.toByteArray())
                archive.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
