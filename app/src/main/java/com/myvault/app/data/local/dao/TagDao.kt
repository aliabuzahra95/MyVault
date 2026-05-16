package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM note_tags ORDER BY noteId ASC, tagName ASC")
    suspend fun getAllRefs(): List<NoteTagCrossRef>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tags ON tags.name = note_tags.tagName
        WHERE note_tags.noteId = :noteId
        ORDER BY tags.name ASC
        """,
    )
    fun observeForNote(noteId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRefs(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId IN (:noteIds)")
    suspend fun deleteRefsForNotes(noteIds: List<String>)
}
