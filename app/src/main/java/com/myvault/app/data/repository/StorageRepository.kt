package com.myvault.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun vaultStorageLabel(): String = withContext(Dispatchers.IO) {
        val databaseSize = listOf(
            context.getDatabasePath("my_vault.db"),
            context.getDatabasePath("my_vault.db-wal"),
            context.getDatabasePath("my_vault.db-shm"),
        ).sumOf { it.safeLength() }
        val appFileSize = context.filesDir.directorySize()
        (databaseSize + appFileSize).toSizeLabel()
    }
}

private fun File.directorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { it.directorySize() }
}

private fun File.safeLength(): Long = if (exists()) length() else 0L

private fun Long.toSizeLabel(): String =
    when {
        this >= 1_000_000_000L -> "${(this / 100_000_000) / 10.0} GB"
        this >= 1_000_000L -> "${(this / 100_000) / 10.0} MB"
        this >= 1_000L -> "${this / 1_000} KB"
        else -> "$this B"
    }
