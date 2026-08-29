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
import java.util.UUID
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
    private var trackedWorkId: UUID? = null

    init {
        workManager.getWorkInfosForUniqueWorkFlow(DriveSyncWorker.UniqueWorkName)
            .onEach { infos ->
                val selected = selectDriveWorkInfo(infos, trackedWorkId)
                if (selected != null) {
                    trackedWorkId = selected.id
                    _state.value = selected.toDriveRestoreState()
                } else if (!_state.value.active) {
                    _state.value = DriveRestoreState()
                }
            }
            .launchIn(scope)
    }

    fun startPush(onComplete: (DriveSyncResult) -> Unit = {}) {
        enqueue(DriveSyncWorker.OperationPush, "Drive backup started. You can leave MyVault while it continues.", onComplete)
    }

    fun startForcePush(onComplete: (DriveSyncResult) -> Unit = {}) {
        enqueue(DriveSyncWorker.OperationForcePush, "Drive force push started. You can leave MyVault while it continues.", onComplete)
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
        if (!canStartDriveOperation(_state.value)) {
            onComplete(DriveSyncResult.Skipped("Google Drive sync is already running."))
            return
        }
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInputData(workDataOf(DriveSyncWorker.KeyOperation to operation))
            .addTag(DriveSyncWorker.Tag)
            .build()
        trackedWorkId = request.id
        _state.value = DriveRestoreState(
            active = true,
            progress = DriveRestoreProgress(
                stage = DriveRestoreStage.Preparing,
                message = operation.progressTitle(),
            ),
            operation = operation.toDriveSyncOperation(),
        )
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

internal fun selectDriveWorkInfo(infos: List<WorkInfo>, trackedWorkId: UUID?): WorkInfo? =
    trackedWorkId?.let { id -> infos.firstOrNull { it.id == id } }
        ?: infos.firstOrNull { !it.state.isFinished }

internal fun canStartDriveOperation(state: DriveRestoreState): Boolean = !state.active

internal fun WorkInfo.toDriveRestoreState(): DriveRestoreState {
    val progressStage = progress.getString(DriveSyncWorker.KeyStage)?.toDriveRestoreStage() ?: DriveRestoreStage.Preparing
    val outputStage = outputData.getString(DriveSyncWorker.KeyStage)?.toDriveRestoreStage()
    val stage = outputStage ?: progressStage
    val outputMessage = outputData.getString(DriveSyncWorker.KeyMessage)
    val progressMessage = progress.getString(DriveSyncWorker.KeyMessage).orEmpty()
    val message = outputMessage ?: progressMessage
    val operation = (outputData.getString(DriveSyncWorker.KeyOperation)
        ?: progress.getString(DriveSyncWorker.KeyOperation))
        .toDriveSyncOperation()
    val current = if (state.isFinished) {
        outputData.getInt(DriveSyncWorker.KeyCurrent, 0)
    } else {
        progress.getInt(DriveSyncWorker.KeyCurrent, 0)
    }
    val total = if (state.isFinished) {
        outputData.getInt(DriveSyncWorker.KeyTotal, 0)
    } else {
        progress.getInt(DriveSyncWorker.KeyTotal, 0)
    }
    val completedAt = outputData.getLong(DriveSyncWorker.KeyCompletedAt, 0L)
    return when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED,
        -> DriveRestoreState(
            active = true,
            progress = DriveRestoreProgress(stage = stage, message = message, current = current, total = total),
            operation = operation,
        )
        WorkInfo.State.SUCCEEDED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(
                stage = outputStage ?: DriveRestoreStage.Complete,
                message = message,
                current = current,
                total = total,
            ),
            message = message,
            operation = operation,
            completedAt = completedAt,
        )
        WorkInfo.State.FAILED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(stage = DriveRestoreStage.Failed, message = message.ifBlank { "Google Drive sync failed." }),
            message = message.ifBlank { "Google Drive sync failed." },
            operation = operation,
        )
        WorkInfo.State.CANCELLED -> DriveRestoreState(
            active = false,
            progress = DriveRestoreProgress(stage = DriveRestoreStage.Failed, message = "Google Drive sync was cancelled."),
            message = "Google Drive sync was cancelled.",
            operation = operation,
        )
    }
}

private fun String.toDriveRestoreStage(): DriveRestoreStage =
    DriveRestoreStage.entries.firstOrNull { it.name == this } ?: DriveRestoreStage.Preparing

private fun String?.toDriveSyncOperation(): DriveSyncOperation =
    when (this) {
        DriveSyncWorker.OperationPush,
        DriveSyncWorker.OperationForcePush,
        -> DriveSyncOperation.Backup
        DriveSyncWorker.OperationPull -> DriveSyncOperation.Restore
        else -> DriveSyncOperation.None
    }

private fun String.progressTitle(): String =
    if (this == DriveSyncWorker.OperationPull) "Starting Google Drive restore" else "Starting Google Drive backup"
