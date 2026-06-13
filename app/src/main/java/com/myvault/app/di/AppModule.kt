package com.myvault.app.di

import android.content.Context
import androidx.room.Room
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.FolderStickyNoteDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.NoteVersionDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, "my_vault.db")
            .addMigrations(
                VaultDatabase.MIGRATION_1_2,
                VaultDatabase.MIGRATION_2_3,
                VaultDatabase.MIGRATION_3_4,
                VaultDatabase.MIGRATION_4_5,
                VaultDatabase.MIGRATION_5_6,
                VaultDatabase.MIGRATION_6_7,
                VaultDatabase.MIGRATION_7_8,
                VaultDatabase.MIGRATION_8_9,
                VaultDatabase.MIGRATION_9_10,
                VaultDatabase.MIGRATION_10_11,
                VaultDatabase.MIGRATION_11_12,
                VaultDatabase.MIGRATION_12_13,
                VaultDatabase.MIGRATION_13_14,
                VaultDatabase.MIGRATION_14_15,
                VaultDatabase.MIGRATION_15_16,
            )
            .build()

    @Provides
    fun provideFolderDao(database: VaultDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideFolderStickyNoteDao(database: VaultDatabase): FolderStickyNoteDao = database.folderStickyNoteDao()

    @Provides
    fun provideNoteDao(database: VaultDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideBlockDao(database: VaultDatabase): BlockDao = database.blockDao()

    @Provides
    fun provideTagDao(database: VaultDatabase): TagDao = database.tagDao()

    @Provides
    fun provideAttachmentDao(database: VaultDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideSearchDao(database: VaultDatabase): SearchDao = database.searchDao()

    @Provides
    fun provideNoteTableDao(database: VaultDatabase): NoteTableDao = database.noteTableDao()

    @Provides
    fun provideNoteVersionDao(database: VaultDatabase): NoteVersionDao = database.noteVersionDao()

    @Provides
    fun provideAiConversationDao(database: VaultDatabase): AiConversationDao = database.aiConversationDao()

    @Provides
    fun providePdfReadingProgressDao(database: VaultDatabase): PdfReadingProgressDao = database.pdfReadingProgressDao()

    @Provides
    fun providePdfAnnotationDao(database: VaultDatabase): PdfAnnotationDao = database.pdfAnnotationDao()

    @Provides
    fun provideSourceBacklinkDao(database: VaultDatabase): SourceBacklinkDao = database.sourceBacklinkDao()

    @Provides
    fun provideKnowledgeTagDao(database: VaultDatabase): KnowledgeTagDao = database.knowledgeTagDao()
}
