package com.myvault.app.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRestoreController @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DriveRestoreState())
    val state: StateFlow<DriveRestoreState> = _state.asStateFlow()

    init {
        workManager.getWorkInfosForUniqueWorkFlow(DriveSyncWorker.UniqueWorkName)
            .onEach { infos ->
                _state.value = infos.firstOrNull()?.toDriveRestoreState() ?: DriveRestoreState()
            }
            .launchIn(scope)
    }

    fun startPush(onComplete: (DriveSyncResult) -> Unit = {}) {
        enqueue(DriveSyncWorker.OperationPush, "Drive backup started. You can leave MyVault while it continues.", onComplete)
    }

    fun startRestore(onComplete: (DriveSyncResult) -> Unit = {}) {
        enqueue(DriveSyncWorker.OperationPull, "Drive restore started. You can leave MyVault while it continues.", onComplete)
    }

    fun dismissMessage() {
        _state.update {
            if (it.active) it else it.copy(message = null)
        }
    }

    private fun enqueue(operation: String, startedMessage: String, onComplete: (DriveSyncResult) -> Unit) {
        if (_state.value.active) {
            onComplete(DriveSyncResult.Skipped("Google Drive sync is already running."))
            return
        }
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInputData(workDataOf(DriveSyncWorker.KeyOperation to operation))
            .addTag(DriveSyncWorker.Tag)
            .build()
        workManager.enqueueUniqueWork(DriveSyncWorker.UniqueWorkName, ExistingWorkPolicy.KEEP, request)
        onComplete(DriveSyncResult.Success(startedMessage))
    }
}

fun DriveSyncResult.displayMessage(): String =
    when (this) {
        is DriveSyncResult.Success -> message
        is DriveSyncResult.Conflict -> message
        is DriveSyncResult.Skipped -> message
        is DriveSyncResult.Failure -> message
    }

private fun WorkInfo.toDriveRestoreState(): DriveRestoreState {
    val progressStage = progress.getString(DriveSyncWorker.KeyStage)?.toDriveRestoreStage() ?: DriveRestoreStage.Preparing
    val outputStage = outputData.getString(DriveSyncWorker.KeyStage)?.toDriveRestoreStage()
    val stage = outputStage ?: progressStage
    val outputMessage = outputData.getString(DriveSyncWorker.KeyMessage)
    val progressMessage = progress.getString(DriveSyncWorker.KeyMessage).orEmpty()
    val message = outputMessage ?: progressMessage
    val current = progress.getInt(DriveSyncWorker.KeyCurrent, 0)
    val total = progress.getInt(DriveSyncWorker.KeyTotal, 0)
    return when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED,
        -> DriveRestoreState(
            active = true,
            progress = DriveRestoreProgress(stage = stage, message = message, current = current, total = total),
        )
        WorkInfo.State.SUCCEEDED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(stage = outputStage ?: DriveRestoreStage.Complete, message = message),
            message = message,
        )
        WorkInfo.State.FAILED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(stage = DriveRestoreStage.Failed, message = message.ifBlank { "Google Drive sync failed." }),
            message = message.ifBlank { "Google Drive sync failed." },
        )
        WorkInfo.State.CANCELLED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(stage = DriveRestoreStage.Failed, message = "Google Drive sync was cancelled."),
            message = "Google Drive sync was cancelled.",
        )
    }
}

private fun String.toDriveRestoreStage(): DriveRestoreStage =
    DriveRestoreStage.entries.firstOrNull { it.name == this } ?: DriveRestoreStage.Preparing
