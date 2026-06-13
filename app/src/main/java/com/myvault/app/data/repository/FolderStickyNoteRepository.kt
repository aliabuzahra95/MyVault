package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.FolderStickyNoteDao
import com.myvault.app.data.local.entity.FolderStickyNoteEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderStickyNoteRepository @Inject constructor(
    private val stickyNoteDao: FolderStickyNoteDao,
) {
    fun observeForFolder(folderId: String) = stickyNoteDao.observeForFolder(folderId)

    suspend fun create(folderId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val now = System.currentTimeMillis()
        stickyNoteDao.upsert(FolderStickyNoteEntity(UUID.randomUUID().toString(), folderId, cleanText, now, now))
    }

    suspend fun update(id: String, text: String) {
        val current = stickyNoteDao.getById(id) ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        stickyNoteDao.upsert(current.copy(text = cleanText, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        stickyNoteDao.deleteById(id)
    }
}
