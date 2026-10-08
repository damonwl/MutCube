package com.dwl.mutcube.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessagePartId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.nio.file.Files

data class StorageSummary(
    val attachmentCount: Int,
    val attachmentBytes: Long,
    val cacheBytes: Long,
)

class AttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "attachments")

    suspend fun import(uri: Uri): MessageContentPart.Attachment = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                    name to size
                }
            }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = metadata?.first?.takeIf(String::isNotBlank) ?: "附件"
        metadata?.second?.let { require(it <= MAX_ATTACHMENT_BYTES) { "单个附件不能超过 20 MB" } }
        directory.mkdirs()
        val extension = (MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: displayName.substringAfterLast('.', "")).lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,24}")) }
        val target = File(directory, UUID.randomUUID().toString() + extension?.let { ".$it" }.orEmpty())
        var copied = 0L
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取附件" }
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_ATTACHMENT_BYTES) { "单个附件不能超过 20 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        attachment(target, mimeType, displayName, copied)
    }

    suspend fun saveCameraImage(bitmap: Bitmap): MessageContentPart.Attachment = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        FileOutputStream(target).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "无法保存照片" }
        }
        attachment(target, "image/jpeg", "照片.jpg", target.length())
    }

    suspend fun saveText(text: String, displayName: String = "粘贴的长文本.txt"): MessageContentPart.Attachment =
        withContext(Dispatchers.IO) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.isNotEmpty()) { "长文本不能为空" }
            require(bytes.size <= MAX_ATTACHMENT_BYTES) { "长文本不能超过 20 MB" }
            directory.mkdirs()
            val target = File(directory, "${UUID.randomUUID()}.txt")
            try {
                FileOutputStream(target).use { it.write(bytes) }
            } catch (error: Exception) {
                target.delete()
                throw error
            }
            attachment(target, "text/plain", displayName, bytes.size.toLong())
        }

    suspend fun deleteDraft(attachment: MessageContentPart.Attachment): Boolean = withContext(Dispatchers.IO) {
        val file = attachment.localFileOrNull() ?: return@withContext false
        file.delete() || !file.exists()
    }

    suspend fun cleanupUnreferenced(referencedUris: Set<String>): Int = withContext(Dispatchers.IO) {
        val referencedPaths = referencedUris.mapNotNullTo(mutableSetOf()) { uri ->
            runCatching { File(java.net.URI(uri)).canonicalPath }.getOrNull()
        }
        directory.listFiles()?.filter(File::isFile)?.count { file ->
            file.canonicalPath !in referencedPaths && file.delete()
        } ?: 0
    }

    suspend fun inspectStorage(): StorageSummary = withContext(Dispatchers.IO) {
        val attachments = directory.listFiles()?.filter(File::isFile).orEmpty()
        StorageSummary(
            attachmentCount = attachments.size,
            attachmentBytes = attachments.sumOf(File::length),
            cacheBytes = appContext.cacheDir.safeTreeBytes(),
        )
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val cacheRoot = appContext.cacheDir.canonicalFile
        val before = cacheRoot.safeTreeBytes()
        cacheRoot.listFiles().orEmpty().forEach { it.safeDeleteWithin(cacheRoot) }
        before - cacheRoot.safeTreeBytes()
    }

    private fun MessageContentPart.Attachment.localFileOrNull(): File? = runCatching {
        val root = directory.canonicalFile
        val file = File(java.net.URI(uri)).canonicalFile
        file.takeIf { it.isFile && it.parentFile == root }
    }.getOrNull()

    private fun attachment(
        file: File,
        mimeType: String,
        displayName: String,
        size: Long,
    ) = MessageContentPart.Attachment(
        id = MessagePartId(UUID.randomUUID().toString()),
        kind = when {
            mimeType.startsWith("image/") -> AttachmentKind.IMAGE
            mimeType.startsWith("audio/") -> AttachmentKind.AUDIO
            mimeType.startsWith("video/") -> AttachmentKind.VIDEO
            else -> AttachmentKind.DOCUMENT
        },
        uri = Uri.fromFile(file).toString(),
        mimeType = mimeType,
        displayName = displayName.take(120),
        sizeBytes = size,
    )

    private fun File.safeTreeBytes(): Long {
        if (Files.isSymbolicLink(toPath())) return 0
        if (isFile) return length()
        return listFiles().orEmpty().sumOf { it.safeTreeBytes() }
    }

    private fun File.safeDeleteWithin(root: File) {
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = canonicalFile
        require(canonicalTarget != canonicalRoot && canonicalTarget.path.startsWith(canonicalRoot.path + File.separator))
        if (Files.isSymbolicLink(toPath())) {
            delete()
            return
        }
        listFiles()?.forEach { it.safeDeleteWithin(canonicalRoot) }
        delete()
    }

    companion object {
        const val MAX_ATTACHMENT_COUNT = 8
        private const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    }
}
