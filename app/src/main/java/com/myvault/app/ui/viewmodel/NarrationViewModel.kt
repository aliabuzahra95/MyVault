package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.myvault.app.data.narration.NarrationController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NarrationViewModel @Inject constructor(
    private val narrationController: NarrationController,
) : ViewModel() {
    val narrationState = narrationController.state

    fun togglePlayback() {
        narrationController.toggle()
    }

    fun stop() {
        narrationController.stop()
    }

    fun setSpeed(speed: Float) {
        narrationController.setSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        narrationController.seekTo(positionMs)
    }

    fun skipBy(deltaMs: Long) {
        narrationController.skipBy(deltaMs)
    }

    fun refreshProgress() {
        narrationController.refreshProgress()
    }

    fun saveProgress() {
        narrationController.saveProgress()
    }

    fun restartWithVoice(voice: String) {
        narrationController.restartWithVoice(voice)
    }
}
