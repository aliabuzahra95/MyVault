package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.AiConversationEntity
import com.myvault.app.data.local.entity.AiMessageEntity

@Dao
interface AiConversationDao {
    @Query("SELECT * FROM ai_conversations WHERE id = :conversationId LIMIT 1")
    suspend fun conversationById(conversationId: String): AiConversationEntity?

    @Query("SELECT * FROM ai_conversations WHERE noteId = :noteId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestConversationForNote(noteId: String): AiConversationEntity?

    @Query("SELECT * FROM ai_conversations WHERE noteId = :noteId ORDER BY updatedAt DESC")
    suspend fun conversationsForNote(noteId: String): List<AiConversationEntity>

    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversations(): List<AiConversationEntity>

    @Query("SELECT * FROM ai_messages ORDER BY noteId ASC, createdAt ASC")
    suspend fun getAllMessages(): List<AiMessageEntity>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun messagesForConversation(conversationId: String): List<AiMessageEntity>

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM ai_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: AiConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<AiConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: AiMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<AiMessageEntity>)

    @Query("DELETE FROM ai_messages WHERE noteId = :noteId")
    suspend fun deleteMessagesForNote(noteId: String)

    @Query("DELETE FROM ai_conversations WHERE noteId = :noteId")
    suspend fun deleteConversationsForNote(noteId: String)

    @Query("DELETE FROM ai_messages WHERE noteId IN (:noteIds)")
    suspend fun deleteMessagesForNotes(noteIds: List<String>)

    @Query("DELETE FROM ai_conversations WHERE noteId IN (:noteIds)")
    suspend fun deleteConversationsForNotes(noteIds: List<String>)
}
