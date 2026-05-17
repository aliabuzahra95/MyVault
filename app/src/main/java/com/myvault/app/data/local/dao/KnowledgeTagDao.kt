package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.KnowledgeTagEntity
import com.myvault.app.data.local.entity.KnowledgeTagLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeTagDao {
    @Query("SELECT * FROM knowledge_tags ORDER BY name ASC")
    fun observeTags(): Flow<List<KnowledgeTagEntity>>

    @Query("SELECT * FROM knowledge_tags ORDER BY name ASC")
    suspend fun getAllTags(): List<KnowledgeTagEntity>

    @Query("SELECT * FROM knowledge_tag_links ORDER BY createdAt DESC")
    fun observeLinks(): Flow<List<KnowledgeTagLinkEntity>>

    @Query("SELECT * FROM knowledge_tag_links ORDER BY createdAt DESC")
    suspend fun getAllLinks(): List<KnowledgeTagLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<KnowledgeTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<KnowledgeTagLinkEntity>)

    @Query("DELETE FROM knowledge_tag_links WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun deleteLinksForTargets(targetType: String, targetIds: List<String>)

    @Query("DELETE FROM knowledge_tag_links WHERE tagId = :tagId AND targetType = :targetType AND targetId = :targetId")
    suspend fun deleteLink(tagId: String, targetType: String, targetId: String)
}
