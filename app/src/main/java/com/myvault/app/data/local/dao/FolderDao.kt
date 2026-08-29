package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY parentId IS NOT NULL, orderIndex ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY parentId IS NOT NULL, orderIndex ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY parentId IS NOT NULL, orderIndex ASC")
    suspend fun getAllIncludingDeleted(): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY parentId IS NOT NULL, orderIndex ASC")
    fun observeAllIncludingDeleted(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId AND deletedAt IS NULL ORDER BY orderIndex ASC")
    fun observeChildren(parentId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL AND name COLLATE NOCASE LIKE :pattern ESCAPE char(92) ORDER BY name ASC LIMIT :limit")
    fun searchActive(pattern: String, limit: Int): Flow<List<FolderEntity>>

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, updatedAt: Long)

    @Query("UPDATE folders SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: String, name: String, description: String?, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateParent(id: String, parentId: String?, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :parentId, orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateParentAndOrder(id: String, parentId: String?, orderIndex: Int, updatedAt: Long)

    @Query("UPDATE folders SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int, updatedAt: Long)

    @Query("UPDATE folders SET colorKey = :colorKey, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColorKey(id: String, colorKey: String?, updatedAt: Long)

    @Query("UPDATE folders SET mode = :mode, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateMode(ids: List<String>, mode: String, updatedAt: Long)

    @Query("UPDATE folders SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateDeletedAt(ids: List<String>, deletedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)
}
