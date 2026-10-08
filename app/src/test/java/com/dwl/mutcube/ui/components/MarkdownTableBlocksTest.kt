package com.dwl.mutcube.ui.components

import org.junit.Assert.*
import org.junit.Test

class MarkdownTableBlocksTest {
    @Test fun separatesTableAndPreservesSurroundingMarkdown() {
        val input = "正文\n\n| 动作 | 说明 |\n| --- | --- |\n| 卧推 | **长说明** |\n\n后文"
        val blocks = markdownDisplayBlocks(input)
        assertEquals(3, blocks.size)
        assertEquals(listOf(listOf("动作", "说明"), listOf("卧推", "**长说明**")), blocks[1].table)
        assertTrue(blocks.first().markdown.contains("正文"))
        assertTrue(blocks.last().markdown.contains("后文"))
    }
    @Test fun fencedTableIsNotRenderedAsTable() {
        assertNull(markdownDisplayBlocks("```\n| a | b |\n|---|---|\n|1|2|\n```").single().table)
    }
    @Test fun escapedPipesRemainWithinCells() {
        assertEquals(listOf("a\\|b", "c"), markdownDisplayBlocks("| a | b |\n|---|---|\n|a\\|b|c|").single().table!!.last())
    }
    @Test fun appendingRowsKeepsTableIdentityAndSupportsEmptyCells() {
        val prefix = "前文\n\n| a | b |\n|---|---|\n|1| |"
        val before = markdownDisplayBlocks(prefix).last()
        val after = markdownDisplayBlocks(prefix + "\n|2|3|").last()
        assertEquals(before.offset, after.offset)
        assertEquals("", before.table!!.last()[1])
        assertEquals(3, after.table!!.size)
    }

    @Test fun rendererPreflightKeepsMalformedStreamingMarkdownFromCrashing() {
        assertTrue(canRenderMarkdown("**Valid** [link](https://example.com)"))
        // Incomplete reference links can arrive before their definitions during streaming.
        assertFalse(canRenderMarkdown("[source]["))
    }
}
