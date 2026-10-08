package com.dwl.mutcube.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLibraryStoreTest {
    @Test
    fun resolvesActiveModeAndMatchingKnowledgeOnly() {
        val state = ContextLibraryState(
            modes = listOf(ContextMode("mode", "简洁", "", "直接给结论", active = true)),
            knowledgeEntries = listOf(
                KnowledgeEntry("match", "训练档案", "用户偏好三分化", listOf("训练")),
                KnowledgeEntry("miss", "旅行档案", "用户喜欢徒步", listOf("旅行")),
                KnowledgeEntry("always", "通用偏好", "使用中文"),
            ),
        )

        val result = resolveContextInstructions(state, "请调整今天的训练计划").joinToString("\n")

        assertTrue(result.contains("当前对话模式：简洁"))
        assertTrue(result.contains("知识条目：训练档案"))
        assertTrue(result.contains("知识条目：通用偏好"))
        assertFalse(result.contains("知识条目：旅行档案"))
    }

    @Test
    fun injectedContextIsBounded() {
        val state = ContextLibraryState(
            knowledgeEntries = List(20) { index ->
                KnowledgeEntry(index.toString(), "条目$index", "x".repeat(10_000))
            },
        )

        val result = resolveContextInstructions(state, "")

        assertTrue(result.size <= 8)
        assertTrue(result.sumOf(String::length) <= 24_000)
    }
}
