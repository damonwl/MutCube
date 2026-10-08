package com.dwl.mutcube.core.context

import com.dwl.mutcube.core.model.MessageContentPart
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import java.nio.file.Files
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

object DocumentTextExtractor {
    const val MAX_EXTRACTED_CHARACTERS = 24_000
    private const val MAX_XML_ENTRIES = 256
    private const val MAX_XML_BYTES = 4L * 1024 * 1024

    fun extract(attachment: MessageContentPart.Attachment): String? {
        val extension = attachment.displayName.substringAfterLast('.', "").lowercase()
        val plainText = attachment.mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS
        val file = runCatching { File(java.net.URI(attachment.uri)) }.getOrNull()
            ?.takeIf(::isTrustedAttachment)
        if (file == null) {
            if (plainText) throw ProjectContextException("无法读取文本附件「${attachment.displayName}」，请重新添加后发送。")
            return null
        }
        if (plainText) return try {
            file.inputStream().buffered().use { input ->
                input.mark(3)
                val head = ByteArray(3)
                val size = input.read(head)
                input.reset()
                val charset = when {
                    size >= 2 && head[0] == 0xff.toByte() && head[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                    size >= 2 && head[0] == 0xfe.toByte() && head[1] == 0xff.toByte() -> Charsets.UTF_16BE
                    else -> Charsets.UTF_8
                }
                input.reader(charset).use { it.readBounded() }.removePrefix("\uFEFF").trim()
            }
        } catch (error: Exception) {
            throw ProjectContextException("读取文本附件「${attachment.displayName}」失败，请重新添加后发送。", error)
        }
        return runCatching {
            when {
                extension == "docx" -> extractOoxml(file) { it == "word/document.xml" }
                extension == "pptx" -> extractOoxml(file) {
                    it.startsWith("ppt/slides/slide") && it.endsWith(".xml") ||
                        it.startsWith("ppt/notesSlides/notesSlide") && it.endsWith(".xml")
                }
                extension == "xlsx" -> extractOoxml(file) {
                    it == "xl/sharedStrings.xml" || it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")
                }
                else -> null
            }
        }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun isTrustedAttachment(file: File): Boolean = runCatching {
        val canonical = file.canonicalFile
        val parent = canonical.parentFile
        canonical.isFile && !Files.isSymbolicLink(file.toPath()) &&
            parent?.name == "attachments" &&
            parent.parentFile?.name == "files" &&
            canonical.name.matches(Regex("[a-f0-9-]{36}(\\.[A-Za-z0-9]{1,24})?"))
    }.getOrDefault(false)

    private fun extractOoxml(file: File, include: (String) -> Boolean): String {
        val result = StringBuilder()
        var parsedEntries = 0
        var parsedBytes = 0L
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements() && result.length < MAX_EXTRACTED_CHARACTERS) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !include(entry.name)) continue
                parsedEntries += 1
                require(parsedEntries <= MAX_XML_ENTRIES) { "Too many document entries" }
                zip.getInputStream(entry).use { input ->
                    val bytes = input.readLimited(MAX_XML_BYTES - parsedBytes)
                    parsedBytes += bytes.size
                    val text = extractXmlText(
                        ByteArrayInputStream(bytes),
                        MAX_EXTRACTED_CHARACTERS - result.length,
                    )
                    if (text.isNotBlank()) result.append(text).append('\n')
                }
            }
        }
        return result.toString().take(MAX_EXTRACTED_CHARACTERS)
    }

    private fun extractXmlText(input: InputStream, limit: Int): String {
        val result = StringBuilder()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        val handler = object : DefaultHandler() {
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (result.length >= limit) throw ExtractionLimitReached()
                val allowed = minOf(length, limit - result.length)
                result.append(ch, start, allowed)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val name = localName?.ifBlank { qName.orEmpty() }.orEmpty()
                if (name in BREAK_ELEMENTS && result.lastOrNull() != '\n') result.append('\n')
            }
        }
        try {
            factory.newSAXParser().parse(input, handler)
        } catch (_: ExtractionLimitReached) {
            // Reaching the explicit output limit is a successful bounded extraction.
        }
        return result.toString()
    }

    private fun java.io.Reader.readBounded(): String {
        val buffer = CharArray(4_096)
        val result = StringBuilder()
        while (result.length < MAX_EXTRACTED_CHARACTERS) {
            val count = read(buffer, 0, minOf(buffer.size, MAX_EXTRACTED_CHARACTERS - result.length))
            if (count < 0) break
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun InputStream.readLimited(remainingBytes: Long): ByteArray {
        require(remainingBytes > 0) { "Document content is too large" }
        val output = ByteArrayOutputStream(minOf(remainingBytes, 32_768).toInt())
        val buffer = ByteArray(8_192)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= remainingBytes) { "Document content is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class ExtractionLimitReached : SAXException()

    private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "csv", "json", "xml", "yaml", "yml", "log")
    private val BREAK_ELEMENTS = setOf("p", "tr", "row", "si")
}
