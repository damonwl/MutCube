package com.dwl.mutcube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MutCubeDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeMemories(): Flow<List<MemoryEntryEntity>>

    @Query(
        """
        SELECT * FROM memory_entries
        WHERE ((:spaceId IS NULL AND spaceId IS NULL) OR (:spaceId IS NOT NULL AND (spaceId IS NULL OR spaceId = :spaceId)))
          AND (:query = '' OR content LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchMemories(spaceId: String?, query: String, limit: Int): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entry: MemoryEntryEntity)

    @Query("SELECT * FROM memory_entries WHERE spaceId IS :spaceId AND content = :content LIMIT 1")
    suspend fun findExactMemory(spaceId: String?, content: String): MemoryEntryEntity?

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemory(id: String): Int

    @Query("UPDATE memory_entries SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMemory(id: String, content: String, updatedAt: Long): Int

    @Query("SELECT * FROM conversation_checkpoints WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getConversationCheckpoint(conversationId: String): ConversationCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversationCheckpoint(checkpoint: ConversationCheckpointEntity)

    @Query("DELETE FROM conversation_checkpoints WHERE conversationId = :conversationId")
    suspend fun deleteConversationCheckpoint(conversationId: String)

    @Query("UPDATE memory_entries SET spaceId = NULL WHERE spaceId = :spaceId")
    suspend fun detachMemoriesFromSpace(spaceId: String)

    @Query("SELECT uri FROM message_parts WHERE kind = 'ATTACHMENT' AND uri IS NOT NULL")
    suspend fun getAllAttachmentUris(): List<String>
    @Query("SELECT * FROM spaces ORDER BY pinned DESC, name COLLATE NOCASE")
    fun observeSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM conversations WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedConversations(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        LEFT JOIN message_nodes n ON n.conversationId = c.id
        LEFT JOIN message_variants v ON v.nodeId = n.id
        LEFT JOIN message_parts p ON p.variantId = v.id
        WHERE c.deletedAt IS NULL
          AND (:query = '' OR c.title LIKE '%' || :query || '%' COLLATE NOCASE
               OR p.text LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT v.id AS messageId, n.conversationId AS conversationId,
               c.title AS conversationTitle, v.role AS role,
               COALESCE(GROUP_CONCAT(p.text, char(10)), '') AS text,
               v.createdAt AS createdAt
        FROM message_variants v
        JOIN message_nodes n ON n.id = v.nodeId
        JOIN conversations c ON c.id = n.conversationId
        LEFT JOIN message_parts p ON p.variantId = v.id AND p.kind = 'TEXT'
        WHERE v.favorite = 1 AND c.deletedAt IS NULL
        GROUP BY v.id
        ORDER BY v.createdAt DESC
        """,
    )
    fun observeFavoriteMessages(): Flow<List<FavoriteMessageEntity>>

    @Transaction
    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId ORDER BY sortOrder, id")
    fun observeMessageNodes(conversationId: String): Flow<List<MessageNodeWithVariants>>

    @Transaction
    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId ORDER BY sortOrder, id")
    suspend fun getMessageNodes(conversationId: String): List<MessageNodeWithVariants>

    @Query("SELECT * FROM conversations WHERE id = :conversationId AND deletedAt IS NULL")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSpace(space: SpaceEntity)

    @Query(
        """
        SELECT s.* FROM spaces s
        JOIN conversations c ON c.spaceId = s.id
        WHERE c.id = :conversationId
        """,
    )
    suspend fun getConversationSpace(conversationId: String): SpaceEntity?

    @Query("UPDATE spaces SET name = :name WHERE id = :spaceId")
    suspend fun renameSpace(spaceId: String, name: String): Int

    @Query("UPDATE spaces SET pinned = :pinned WHERE id = :spaceId")
    suspend fun setSpacePinned(spaceId: String, pinned: Boolean): Int

    @Query("UPDATE spaces SET modelProfileId = :profileId WHERE id = :spaceId")
    suspend fun setSpaceModelProfile(spaceId: String, profileId: String?): Int

    @Query(
        """
        UPDATE spaces SET
            modelIdOverride = :modelId,
            systemPromptOverride = :systemPrompt,
            maxContextMessagesOverride = :maxContextMessages,
            imageInputEnabledOverride = :imageInputEnabled,
            temperatureOverride = :temperature,
            topPOverride = :topP,
            maxOutputTokensOverride = :maxOutputTokens
        WHERE id = :spaceId
        """,
    )
    suspend fun updateSpaceModelSettings(
        spaceId: String,
        modelId: String?,
        systemPrompt: String?,
        maxContextMessages: Int?,
        imageInputEnabled: Boolean?,
        temperature: Double?,
        topP: Double?,
        maxOutputTokens: Int?,
    ): Int

    @Query("DELETE FROM spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: String): Int

    @Query("UPDATE conversations SET spaceId = NULL WHERE spaceId = :spaceId")
    suspend fun detachConversationsFromSpace(spaceId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageNode(node: MessageNodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageVariant(variant: MessageVariantEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessagePart(part: MessagePartEntity)

    @Query(
        """
        UPDATE message_nodes
        SET selectedVariantId = :variantId
        WHERE id = :nodeId
          AND conversationId = :conversationId
          AND EXISTS (
              SELECT 1 FROM message_variants WHERE id = :variantId AND nodeId = :nodeId
          )
        """,
    )
    suspend fun selectMessageVariant(conversationId: String, nodeId: String, variantId: String): Int

    @Query("SELECT COUNT(*) FROM message_nodes WHERE id = :nodeId AND conversationId = :conversationId")
    suspend fun countMessageNode(conversationId: String, nodeId: String): Int

    @Query("DELETE FROM message_nodes WHERE conversationId = :conversationId AND id IN (:nodeIds)")
    suspend fun deleteMessageNodes(conversationId: String, nodeIds: List<String>): Int

    @Query(
        """
        UPDATE message_variants SET favorite = :favorite
        WHERE id = :messageId
          AND nodeId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId)
        """,
    )
    suspend fun setMessageFavorite(conversationId: String, messageId: String, favorite: Boolean): Int

    @Query(
        """
        UPDATE message_variants SET translation = :translation
        WHERE id = :messageId
          AND nodeId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId)
        """,
    )
    suspend fun setMessageTranslation(conversationId: String, messageId: String, translation: String?): Int

    @Query(
        """
        UPDATE message_variants
        SET status = :status
        WHERE id = :messageId
          AND role = 'AI'
          AND nodeId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId)
        """,
    )
    suspend fun updateMessage(
        conversationId: String,
        messageId: String,
        status: String,
    ): Int

    @Query("DELETE FROM message_parts WHERE variantId = :messageId")
    suspend fun deleteMessageParts(messageId: String)

    @Query(
        """
        SELECT CASE
            WHEN COALESCE(MAX(sortOrder), -1) >= :timestamp THEN MAX(sortOrder) + 1
            ELSE :timestamp
        END
        FROM message_nodes
        WHERE conversationId = :conversationId
        """,
    )
    suspend fun nextMessageSortOrder(conversationId: String, timestamp: Long): Long

    @Query("SELECT COUNT(*) FROM spaces WHERE id = :spaceId")
    suspend fun countSpace(spaceId: String): Int

    @Query("UPDATE conversations SET spaceId = :spaceId WHERE id = :conversationId")
    suspend fun updateConversationSpace(conversationId: String, spaceId: String?): Int

    @Query("UPDATE conversations SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationSystemPrompt(conversationId: String, systemPrompt: String?, updatedAt: Long): Int

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun renameConversation(conversationId: String, title: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun setConversationPinned(conversationId: String, pinned: Boolean, updatedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = :deletedAt WHERE id = :conversationId AND deletedAt IS NULL")
    suspend fun trashConversation(conversationId: String, deletedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun restoreConversation(conversationId: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = :deletedAt WHERE deletedAt IS NULL")
    suspend fun trashAllConversations(deletedAt: Long): Int

    @Query("UPDATE conversations SET deletedAt = NULL, updatedAt = :updatedAt WHERE deletedAt IS NOT NULL")
    suspend fun restoreAllConversations(updatedAt: Long): Int

    @Query("DELETE FROM conversations WHERE id = :conversationId AND deletedAt IS NOT NULL")
    suspend fun permanentlyDeleteConversation(conversationId: String): Int

    @Query("DELETE FROM conversations WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash(): Int

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: String, updatedAt: Long)
}
