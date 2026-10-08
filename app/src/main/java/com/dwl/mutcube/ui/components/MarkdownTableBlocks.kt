package com.dwl.mutcube.ui.components

import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

internal data class MarkdownDisplayBlock(
    val offset: Int,
    val markdown: String,
    val table: List<List<String>>? = null,
)

/** AST offsets preserve fenced code, escaped pipes and inline formatting without regex parsing. */
internal fun markdownDisplayBlocks(markdown: String): List<MarkdownDisplayBlock> {
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    val blocks = mutableListOf<MarkdownDisplayBlock>()
    var cursor = 0
    tree.children.filter { it.type == GFMElementTypes.TABLE }.forEach { node ->
        val rows = node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
            .map { row -> row.children.filter { it.type == GFMTokenTypes.CELL }
                .map { markdown.substring(it.startOffset, it.endOffset).trim() } }
        val columns = rows.firstOrNull()?.size ?: 0
        if (columns > 0) {
            if (node.startOffset > cursor) blocks += MarkdownDisplayBlock(cursor, markdown.substring(cursor, node.startOffset))
            blocks += MarkdownDisplayBlock(node.startOffset, markdown.substring(node.startOffset, node.endOffset),
                rows.map { row -> List(columns) { row.getOrElse(it) { "" } } })
            cursor = node.endOffset
        }
    }
    if (cursor < markdown.length) blocks += MarkdownDisplayBlock(cursor, markdown.substring(cursor))
    return blocks.filter { it.markdown.isNotBlank() }
}
