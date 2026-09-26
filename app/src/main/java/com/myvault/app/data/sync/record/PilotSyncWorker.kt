package com.myvault.app.data.sync.record

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
internal class PilotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pilot: RecordSyncPilot,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        pilot.syncNow()
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalArgumentException) {
        Log.w("PilotSyncWorker", "Test-note sync needs attention", error)
        Result.failure()
    } catch (error: Exception) {
        Log.w("PilotSyncWorker", "Test-note sync will retry", error)
        Result.retry()
    }
}
