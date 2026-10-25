package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the conversation-tree navigation helpers behind chat
 * branching (retry/edit variants).
 */
class ChatTreeTest {

    private fun message(
        id: Long,
        role: AiRole,
        parentId: Long? = null
    ) = AiChatMessage(
        id = id,
        chatId = 1L,
        role = role,
        parentId = parentId,
        parts = listOf(AiMessagePart.Text("m$id")),
        status = AiMessageStatus.COMPLETE,
        inputTokens = null,
        outputTokens = null,
        createdAt = id
    )

    @Test
    fun `linear chain walks from leaf to root`() {
        // 1 (user) -> 2 (assistant) -> 3 (user), a backfilled legacy chain.
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.USER, 2)
        )
        val path = ChatTree.activePath(all, activeLeafId = 3)
        assertEquals(listOf(1L, 2L, 3L), path.map { it.id })
    }

    @Test
    fun `null leaf falls back to newest message`() {
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1)
        )
        val path = ChatTree.activePath(all, activeLeafId = null)
        assertEquals(listOf(1L, 2L), path.map { it.id })
    }

    @Test
    fun `unknown leaf falls back to newest message`() {
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1)
        )
        val path = ChatTree.activePath(all, activeLeafId = 99)
        assertEquals(listOf(1L, 2L), path.map { it.id })
    }

    @Test
    fun `regenerated sibling branch keeps only its own path`() {
        // 1 (user) has two assistant variants: 2 (original with follow-up 4)
        // and 3 (regenerated). Active leaf 3 shows 1 -> 3 only.
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.ASSISTANT, 1),
            message(4, AiRole.USER, 2)
        )
        val path = ChatTree.activePath(all, activeLeafId = 3)
        assertEquals(listOf(1L, 3L), path.map { it.id })

        // Switching back to the original branch ends at its newest leaf.
        assertEquals(4L, ChatTree.newestDescendant(all, 2))
        val originalPath = ChatTree.activePath(all, activeLeafId = 4)
        assertEquals(listOf(1L, 2L, 4L), originalPath.map { it.id })
    }

    @Test
    fun `edited user message branches from same parent`() {
        // 2 is an edit-variant of user message 1 (both parent null), each
        // with its own assistant reply.
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.USER),
            message(4, AiRole.ASSISTANT, 3)
        )
        val path = ChatTree.activePath(all, activeLeafId = 4)
        assertEquals(listOf(3L, 4L), path.map { it.id })
    }

    @Test
    fun `newestDescendant follows tool chains to the latest turn`() {
        // 1 -> 2 (assistant) -> 3 (tool) -> 4 (assistant); leaf of branch = 4.
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.TOOL, 2),
            message(4, AiRole.ASSISTANT, 3)
        )
        assertEquals(4L, ChatTree.newestDescendant(all, 1))
        assertEquals(4L, ChatTree.newestDescendant(all, 2))
        assertEquals(4L, ChatTree.newestDescendant(all, 4))
    }

    @Test
    fun `newestDescendant picks the leaf with the highest id`() {
        // Two sub-branches under 2: 3 (deep chain to 6) and 4 (newest id).
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.USER, 2),
            message(4, AiRole.USER, 2),
            message(5, AiRole.ASSISTANT, 3),
            message(6, AiRole.ASSISTANT, 5)
        )
        // Deepest chain ends at 6, which also carries the highest id.
        assertEquals(6L, ChatTree.newestDescendant(all, 2))
    }

    @Test
    fun `variants group siblings by parent and role only`() {
        val all = listOf(
            message(1, AiRole.USER),
            message(2, AiRole.ASSISTANT, 1),
            message(3, AiRole.ASSISTANT, 1),
            message(4, AiRole.USER, 2),
            // A tool sibling under the same parent is not an assistant variant.
            message(5, AiRole.TOOL, 1)
        )
        val variants = ChatTree.variantsOf(all, pathIds = listOf(1L, 2L, 3L, 4L))
        assertEquals(2, variants[2L]?.count)
        assertEquals(0, variants[2L]?.index)
        assertEquals(listOf(2L, 3L), variants[2L]?.siblingIds)
        assertEquals(1, variants[3L]?.index)
        assertEquals(null, variants[1L])
        assertEquals(null, variants[4L])
    }

    @Test
    fun `empty chat yields empty path`() {
        assertEquals(emptyList<AiChatMessage>(), ChatTree.activePath(emptyList(), 1L))
    }
}
