package com.myvault.app.ui.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.quran.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReflectionRepositoryFlowTest {
    @Test fun existingRepositoryEmitsAddedEditedAndDeletedReflectionsWithoutRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
        val updates = Channel<List<QuranReflectionItem>>(Channel.UNLIMITED)
        val repository = QuranReflectionRepository(db.folderDao(), db.noteDao(), QuranCatalogRepository(), QuranTextRepository(context))
        val collector = launch { repository.observeReflectionItems().collect { updates.send(it) } }
        suspend fun await(predicate: (List<QuranReflectionItem>) -> Boolean) = withTimeout(15_000) {
            var value = updates.receive()
            while (!predicate(value)) value = updates.receive()
            value
        }
        try {
            assertTrue(await { it.isEmpty() }.isEmpty())
            db.folderDao().upsertAll(listOf(FolderEntity("fixture-reflections", null, QURAN_REFLECTION_FOLDER_NAME,
                orderIndex = 0, isFavourite = false, createdAt = 1, updatedAt = 1)))
            fun body(text: String) = "Reflection on Al-Hajj 22:11\nSource: Al-Hajj 22:11\n\nReflection:\n$text"
            val note = NoteEntity("fixture-reflection", "fixture-reflections", title = "Reflection", bodyPlainText = body("Original reflection"),
                isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
            db.noteDao().upsertAll(listOf(note))
            val added = await { it.size == 1 }.single()
            assertEquals(note.id, added.noteId)
            assertEquals("22:11", added.verseKey)
            assertEquals("Original reflection", added.reflectionBody)
            assertTrue(added.arabicPreview.isNotBlank())
            db.noteDao().updateBodyPlainText(note.id, body("Updated reflection تأمل"), 2)
            assertEquals(note.id, await { it.singleOrNull()?.reflectionBody == "Updated reflection تأمل" }.single().noteId)
            db.noteDao().updateDeletedAt(listOf(note.id), 3, 3)
            assertTrue(await { it.isEmpty() }.isEmpty())
            assertEquals(1, db.noteDao().getAllIncludingDeleted().size)
        } finally {
            collector.cancelAndJoin()
            updates.close()
            db.close()
        }
    }
}
