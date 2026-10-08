package com.dwl.mutcube.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boswelja.markdown.material3.MarkdownDocument
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * Application boundary for rendering AI-authored Markdown.
 *
 * Keeping the third-party renderer behind this component lets chat styling and
 * renderer implementations evolve independently.
 */
@Composable
fun MarkdownMessage(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { markdownDisplayBlocks(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            key(block.offset) {
                if (block.table == null) SafeMarkdownDocument(block.markdown)
                else ScrollableMarkdownTable(block.table)
            }
        }
    }
}

@Composable
private fun SafeMarkdownDocument(markdown: String, modifier: Modifier = Modifier, sectionSpacing: androidx.compose.ui.unit.Dp = 8.dp) {
    // The renderer can throw on an incomplete reference link while the model is streaming.
    // Keep the text visible until a later delta forms valid Markdown instead of crashing the app.
    val renderable = remember(markdown) { canRenderMarkdown(markdown) }
    if (renderable) MarkdownDocument(markdown = markdown, modifier = modifier, sectionSpacing = sectionSpacing)
    else Text(markdown, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
}

internal fun canRenderMarkdown(markdown: String): Boolean = runCatching {
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    fun isSupported(node: ASTNode): Boolean {
        if (node.type == MarkdownElementTypes.FULL_REFERENCE_LINK ||
            node.type == MarkdownElementTypes.SHORT_REFERENCE_LINK) {
            // The third-party renderer assumes both children exist and throws otherwise.
            if (node.children.none { it.type == MarkdownElementTypes.LINK_TEXT } ||
                node.children.none { it.type == MarkdownElementTypes.LINK_LABEL }) return false
        }
        return node.children.all(::isSupported)
    }
    isSupported(tree)
}.getOrDefault(false)

@Composable
private fun ScrollableMarkdownTable(rows: List<List<String>>) {
    val scroll = rememberScrollState() // Stable block offset keeps this state during streamed row updates.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyLarge
    val widths = remember(rows, density, style) {
        rows.first().indices.map { column ->
            val pixels = rows.maxOf { row ->
                measurer.measure(row[column].take(1500), style, softWrap = false, maxLines = 1).size.width
            }
            with(density) { pixels.toDp() }.plus(24.dp).coerceIn(88.dp, 320.dp)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        val extra = ((maxWidth - widths.fold(0.dp) { sum, width -> sum + width }) / widths.size).coerceAtLeast(0.dp)
        Column(Modifier.horizontalScroll(scroll).semantics { contentDescription = "表格，左右滑动查看" }
            .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            rows.forEachIndexed { index, cells ->
                Row(if (index == 0) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier) {
                    cells.forEachIndexed { column, cell ->
                        SafeMarkdownDocument(markdown = if (index == 0) "**$cell**" else cell,
                            modifier = Modifier.width(widths[column] + extra).padding(12.dp), sectionSpacing = 4.dp)
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider(Modifier.width(widths.fold(0.dp) { sum, width -> sum + width } + extra * widths.size))
            }
        }
    }
}
