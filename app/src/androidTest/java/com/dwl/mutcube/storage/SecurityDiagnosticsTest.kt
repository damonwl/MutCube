package com.dwl.mutcube.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dwl.mutcube.MutCubeApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurityDiagnosticsTest {
    @Test
    fun reportDisablesBackupAndFindsUnreferencedPrivateAttachment() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val orphan = File(directory, "diagnostic-orphan.tmp").apply { writeText("test") }
        try {
            val container = (context.applicationContext as MutCubeApplication).container
            val report = container.securityDiagnostics.inspect()
            assertTrue(report.backupDisabled)
            assertTrue(report.attachmentFiles >= 1)
            assertTrue(report.orphanAttachmentFiles >= 1)
            assertEquals(true, report.databaseBytes >= 0)
        } finally {
            orphan.delete()
        }
    }
}
