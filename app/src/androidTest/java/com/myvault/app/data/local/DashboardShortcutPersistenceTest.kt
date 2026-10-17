package com.myvault.app.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.repository.DashboardActivityRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DashboardShortcutPersistenceTest {
    @Test fun folderPinSurvivesRepositoryRecreationAndNeverDeletesContent() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "dashboard-disposable-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(preferenceName, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(target, VaultDatabase::class.java).build()
        fun repository() = DashboardActivityRepository(isolated, db.noteDao(), db.folderDao(), db.attachmentDao(),
            db.courseDao(), db.pdfReadingProgressDao())
        val folder = FolderEntity("folder", null, "Aqeedah", orderIndex = 0, isFavourite = true, mode = "study", createdAt = 1, updatedAt = 1)
        try {
            db.folderDao().upsertAll(listOf(folder))
            repository().setFolderPinned(folder.id, true)
            repository().setFolderPinned(folder.id, true)
            assertEquals(listOf(folder), repository().pinnedFolders.first())
            repository().setFolderPinned(folder.id, false)
            assertTrue(repository().pinnedFolders.first().isEmpty())
            assertEquals(listOf(folder), db.folderDao().getAll())
            repository().setFolderPinned(folder.id, true)
            db.folderDao().upsertAll(listOf(folder.copy(deletedAt = 2)))
            assertTrue(repository().pinnedFolders.first().isEmpty())
        } finally {
            db.close()
            target.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
