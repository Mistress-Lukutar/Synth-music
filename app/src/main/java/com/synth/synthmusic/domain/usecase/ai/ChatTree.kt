package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiChatMessage

/**
 * Navigation helpers over the conversation tree. Messages form a forest
 * keyed by [AiChatMessage.parentId]; siblings sharing a parent and a role
 * are alternative variants of the same turn produced by regenerate/edit.
 */
object ChatTree {

    /**
     * Variant position of one message among its role siblings, for the
     * `< k/n >` switcher in the transcript.
     */
    data class Variants(
        val index: Int,
        val count: Int,
        val siblingIds: List<Long>
    )

    /**
     * The chain of messages currently shown for a chat: walking parents from
     * the active leaf (or, when unpinned/unknown, the newest leaf) up to the
     * root, root first.
     */
    fun activePath(
        all: List<AiChatMessage>,
        activeLeafId: Long?
    ): List<AiChatMessage> {
        if (all.isEmpty()) return emptyList()
        val byId = all.associateBy { it.id }
        val leaf = activeLeafId?.let { byId[it] }
            ?: all.maxByOrNull { it.id }
            ?: return emptyList()
        val path = ArrayDeque<AiChatMessage>()
        var current: AiChatMessage? = leaf
        val visited = mutableSetOf<Long>()
        while (current != null && visited.add(current.id)) {
            path.addFirst(current)
            current = current.parentId?.let { byId[it] }
        }
        return path.toList()
    }

    /**
     * Variant groups for the given (visible path) message ids: for each id,
     * its position among all messages sharing the same parent and role.
     */
    fun variantsOf(
        all: List<AiChatMessage>,
        pathIds: Collection<Long>
    ): Map<Long, Variants> {
        if (all.isEmpty() || pathIds.isEmpty()) return emptyMap()
        val groups = all.groupBy { it.parentId to it.role }
        val result = mutableMapOf<Long, Variants>()
        for (id in pathIds) {
            val message = all.firstOrNull { it.id == id } ?: continue
            val siblings = groups[message.parentId to message.role]
                ?.sortedBy { it.id }
                ?: listOf(message)
            if (siblings.size < 2) continue
            val index = siblings.indexOfFirst { it.id == id }
            result[id] = Variants(
                index = index,
                count = siblings.size,
                siblingIds = siblings.map { it.id }
            )
        }
        return result
    }

    /**
     * Newest leaf of the subtree rooted at [rootId]: the deepest branch end
     * with the highest id, used when the user switches to a mid-branch
     * variant and the conversation should jump to that branch's latest turn.
     * Returns [rootId] itself when it has no children.
     */
    fun newestDescendant(all: List<AiChatMessage>, rootId: Long): Long {
        val children = all.groupBy { it.parentId }
        var newest = rootId
        val queue = ArrayDeque<Long>()
        queue.add(rootId)
        val visited = mutableSetOf<Long>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!visited.add(id)) continue
            if (id > newest) newest = id
            children[id]?.forEach { child ->
                queue.add(child.id)
            }
        }
        return newest
    }
}
