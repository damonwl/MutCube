package com.dwl.mutcube.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {
    @Test
    fun conversationIsGlobalByDefault() {
        val conversation = Conversation(
            id = ConversationId("conversation-1"),
            title = "New chat",
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertNull(conversation.spaceId)
    }

    @Test
    fun messageNodeExposesSelectedStructuredVariant() {
        val first = MessageVariant(
            id = MessageId("variant-1"),
            role = MessageRole.AI,
            parts = listOf(MessageContentPart.Text(MessagePartId("part-1"), "第一版")),
            createdAt = 1L,
        )
        val second = MessageVariant(
            id = MessageId("variant-2"),
            role = MessageRole.AI,
            parts = listOf(
                MessageContentPart.Reasoning(MessagePartId("part-2"), "推理过程"),
                MessageContentPart.Text(MessagePartId("part-3"), "第二版"),
            ),
            createdAt = 2L,
        )
        val node = MessageNode(
            id = MessageNodeId("node-1"),
            conversationId = ConversationId("conversation-1"),
            sortOrder = 1L,
            parentVariantId = null,
            variants = listOf(first, second),
            selectedVariantId = second.id,
        )

        assertEquals(second, node.selectedVariant)
        assertEquals(2, node.selectedVariant.parts.size)
    }
}
