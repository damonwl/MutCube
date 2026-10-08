package com.dwl.mutcube.storage

import android.content.Context
import android.content.pm.ApplicationInfo
import com.dwl.mutcube.core.database.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore

data class SecurityDiagnosticReport(
    val backupDisabled: Boolean,
    val databasePresent: Boolean,
    val databaseBytes: Long,
    val keystoreKeyPresent: Boolean,
    val encryptedCredentialPairs: Int,
    val attachmentFiles: Int,
    val attachmentBytes: Long,
    val orphanAttachmentFiles: Int,
)

class SecurityDiagnostics(
    context: Context,
    private val repository: ConversationRepository,
) {
    private val appContext = context.applicationContext

    suspend fun inspect(): SecurityDiagnosticReport = withContext(Dispatchers.IO) {
        val database = appContext.getDatabasePath("mutcube.db")
        val attachmentDirectory = File(appContext.filesDir, "attachments")
        val attachmentFiles = attachmentDirectory.listFiles()?.filter(File::isFile).orEmpty()
        val referenced = repository.getAllAttachmentUris().mapNotNullTo(mutableSetOf()) { uri ->
            runCatching { File(java.net.URI(uri)).canonicalPath }.getOrNull()
        }
        val credentials = appContext.getSharedPreferences("encrypted_credentials", Context.MODE_PRIVATE).all.keys
        val valueIds = credentials.filter { it.endsWith(".value") }.map { it.removeSuffix(".value") }.toSet()
        val ivIds = credentials.filter { it.endsWith(".iv") }.map { it.removeSuffix(".iv") }.toSet()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        SecurityDiagnosticReport(
            backupDisabled = appContext.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0,
            databasePresent = database.isFile,
            databaseBytes = database.takeIf(File::isFile)?.length() ?: 0,
            keystoreKeyPresent = keyStore.containsAlias("mutcube.credentials.v1"),
            encryptedCredentialPairs = valueIds.intersect(ivIds).size,
            attachmentFiles = attachmentFiles.size,
            attachmentBytes = attachmentFiles.sumOf(File::length),
            orphanAttachmentFiles = attachmentFiles.count { it.canonicalPath !in referenced },
        )
    }
}
