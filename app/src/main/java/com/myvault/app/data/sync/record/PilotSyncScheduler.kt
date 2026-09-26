package com.myvault.app.data.sync.record

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PilotSyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun scheduleAfterEdit(noteId: String) {
        if (noteId in registeredIds()) enqueue(RecordSyncDebounceMs)
    }

    fun scheduleWhenAppOpens() {
        if (registeredIds().isNotEmpty()) enqueue(0)
    }

    private fun registeredIds(): Set<String> = context
        .getSharedPreferences("record_sync_pilot", Context.MODE_PRIVATE)
        .getStringSet("noteIds", emptySet()).orEmpty()
        .filter(::isPilotNoteId).toSet()

    private fun enqueue(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<PilotSyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WorkName, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WorkName = "record-sync-pilot-notes"
    }
}
