package com.myvault.app.data.sync

import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.TagDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val tagDao: TagDao,
    private val attachmentDao: AttachmentDao,
    private val noteTableDao: NoteTableDao,
    private val syncApiClient: SyncApiClient,
) {
    suspend fun pushLocalSnapshot(): SyncResult =
        syncApiClient.push(
            noteDao.getAllIncludingDeleted().let { notes ->
                val noteIds = notes.map { it.id }.toSet()
                SyncSnapshot(
                    folders = folderDao.getAllIncludingDeleted(),
                    notes = notes,
                    blocks = blockDao.getAll().filter { it.noteId in noteIds },
                    tags = tagDao.getAll(),
                    noteTags = tagDao.getAllRefs().filter { it.noteId in noteIds },
                    tables = noteTableDao.getAll().filter { it.noteId in noteIds },
                    attachments = attachmentDao.getAllIncludingDeleted().filter { it.noteId in noteIds },
                )
            },
        )

    suspend fun pullRemoteSnapshotRaw(): Result<String> = syncApiClient.pullRaw()
}
