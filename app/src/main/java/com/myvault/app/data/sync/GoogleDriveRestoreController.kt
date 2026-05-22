package com.myvault.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRestoreController @Inject constructor(
    private val googleDriveSyncRepository: GoogleDriveIncrementalSyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DriveRestoreState())
    val state: StateFlow<DriveRestoreState> = _state.asStateFlow()

    fun startRestore(onComplete: (DriveSyncResult) -> Unit = {}) {
        if (_state.value.active) {
            onComplete(DriveSyncResult.Skipped("Drive restore is already running."))
            return
        }
        scope.launch {
            _state.value = DriveRestoreState(
                active = true,
                progress = DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Starting Google Drive restore"),
            )
            val result = googleDriveSyncRepository.pullLatestFromDrive { progress ->
                _state.value = DriveRestoreState(active = true, progress = progress)
            }
            val finalStage = if (result is DriveSyncResult.Success) DriveRestoreStage.Complete else DriveRestoreStage.Failed
            _state.value = DriveRestoreState(
                active = false,
                progress = DriveRestoreProgress(stage = finalStage, message = result.displayMessage()),
                message = result.displayMessage(),
            )
            withContext(Dispatchers.Main.immediate) {
                onComplete(result)
            }
        }
    }

    fun dismissMessage() {
        _state.update {
            if (it.active) it else it.copy(message = null)
        }
    }
}

fun DriveSyncResult.displayMessage(): String =
    when (this) {
        is DriveSyncResult.Success -> message
        is DriveSyncResult.Conflict -> message
        is DriveSyncResult.Skipped -> message
        is DriveSyncResult.Failure -> message
    }
