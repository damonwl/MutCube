package com.dwl.mutcube.feature.chat

import com.dwl.mutcube.core.context.DocumentTextExtractor
import com.dwl.mutcube.core.context.ProjectContextException

import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessagePartId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentTextExtractorTest {
    @Test
    fun extractsUtf16TextWithBomAndRejectsMissingPlainText() {
        val file = newAttachmentFile("txt")
        try {
            file.writeBytes("\uFEFF中文训练记录".toByteArray(Charsets.UTF_16LE))
            assertEquals("中文训练记录", DocumentTextExtractor.extract(file.attachment("text/plain", "记录.txt")))
            file.delete()
            assertTrue(runCatching { DocumentTextExtractor.extract(file.attachment("text/plain", "记录.txt")) }.exceptionOrNull() is ProjectContextException)
        } finally { file.delete() }
    }

    @Test
    fun extractsBoundedUtf8PlainText() {
        val file = newAttachmentFile("txt").apply {
            writeText("训练记录\n" + "字".repeat(DocumentTextExtractor.MAX_EXTRACTED_CHARACTERS + 100))
        }

        val text = DocumentTextExtractor.extract(file.attachment("text/plain", "记录.txt"))

        assertEquals(DocumentTextExtractor.MAX_EXTRACTED_CHARACTERS, text?.length)
        assertTrue(text.orEmpty().startsWith("训练记录"))
        file.delete()
    }

    @Test
    fun extractsTextFromDocxDocumentXml() {
        val file = newAttachmentFile("docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """<w:document xmlns:w="urn:test"><w:body><w:p><w:r><w:t>第一段</w:t></w:r></w:p><w:p><w:r><w:t>第二段</w:t></w:r></w:p></w:body></w:document>"""
                    .encodeToByteArray(),
            )
            zip.closeEntry()
        }

        val text = DocumentTextExtractor.extract(
            file.attachment("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "测试.docx"),
        )

        assertEquals("第一段\n第二段", text)
        file.delete()
    }

    @Test
    fun rejectsFilesOutsidePrivateAttachmentDirectoryAndSymlinks() {
        val secret = File.createTempFile("mutcube-secret", ".txt")
        val link = newAttachmentFile("txt")
        try {
            secret.writeText("private-token")
            assertTrue(runCatching {
                DocumentTextExtractor.extract(secret.attachment("text/plain", "private.txt"))
            }.exceptionOrNull() is ProjectContextException)
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            assertTrue(runCatching {
                DocumentTextExtractor.extract(link.attachment("text/plain", "linked.txt"))
            }.exceptionOrNull() is ProjectContextException)
        } finally {
            Files.deleteIfExists(link.toPath())
            secret.delete()
        }
    }

    private fun File.attachment(mimeType: String, displayName: String) = MessageContentPart.Attachment(
        id = MessagePartId("attachment"),
        kind = AttachmentKind.DOCUMENT,
        uri = toURI().toString(),
        mimeType = mimeType,
        displayName = displayName,
        sizeBytes = length(),
    )

    private fun newAttachmentFile(extension: String): File {
        val directory = File.createTempFile("mutcube", "-test").let { temporary ->
            temporary.delete()
            File(temporary, "files/attachments").apply { check(mkdirs()) }
        }
        return File(directory, "${java.util.UUID.randomUUID()}.$extension")
    }
}
