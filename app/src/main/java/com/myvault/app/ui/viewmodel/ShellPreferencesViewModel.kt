package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.sync.GoogleDriveRestoreController
import com.myvault.app.data.sync.displayMessage
import com.myvault.app.ui.theme.VaultThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ShellPreferencesViewModel @Inject constructor(
    private val preferences: VaultPreferences,
    private val googleDriveRestoreController: Provider<GoogleDriveRestoreController>,
) : ViewModel() {
    val userPreferences: StateFlow<VaultUserPreferences> =
        preferences.userPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VaultUserPreferences(),
        )

    fun setTheme(mode: VaultThemeMode) {
        viewModelScope.launch { preferences.setTheme(mode) }
    }

    fun setWorkspace(workspace: String) {
        viewModelScope.launch { preferences.setWorkspace(workspace) }
    }

    fun pushGoogleDriveSync(onComplete: (String) -> Unit) {
        googleDriveRestoreController.get().startPush { result ->
            onComplete(result.displayMessage())
        }
    }
}
