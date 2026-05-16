package com.myvault.app.di

import android.content.Context
import androidx.room.Room
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.SearchDao
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
            )
            .build()

    @Provides
    fun provideFolderDao(database: VaultDatabase): FolderDao = database.folderDao()

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
    fun provideAiConversationDao(database: VaultDatabase): AiConversationDao = database.aiConversationDao()
}
