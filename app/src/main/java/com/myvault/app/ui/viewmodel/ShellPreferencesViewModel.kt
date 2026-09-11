package com.myvault.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.preferences.DrawerProfilePreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.myvault.app.ui.components.DrawerGoogleProfile
import com.myvault.app.ui.components.DrawerIdentity
import com.myvault.app.ui.components.drawerAccountKey
import com.myvault.app.ui.components.resolveDrawerIdentity
import com.myvault.app.data.sync.GoogleDriveRestoreController
import com.myvault.app.data.sync.displayMessage
import com.myvault.app.ui.theme.VaultThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@HiltViewModel
class ShellPreferencesViewModel @Inject constructor(
    private val preferences: VaultPreferences,
    private val googleDriveRestoreController: Provider<GoogleDriveRestoreController>,
    private val drawerProfiles: DrawerProfilePreferences,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val driveRestoreState = googleDriveRestoreController.get().state

    val userPreferences: StateFlow<VaultUserPreferences> =
        preferences.userPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            preferences.cachedStartupPreferences(),
        )

    private val googleProfile = MutableStateFlow<DrawerGoogleProfile?>(null)
    val drawerIdentity: StateFlow<DrawerIdentity> = combine(userPreferences, drawerProfiles.names, googleProfile) { prefs, names, google ->
        resolveDrawerIdentity(prefs.googleDriveAccountEmail, names, google)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerIdentity())

    init {
        viewModelScope.launch {
            userPreferences.map { it.googleDriveAccountEmail }.distinctUntilChanged().collect { refreshDrawerProfile() }
        }
    }

    fun refreshDrawerProfile() {
        viewModelScope.launch {
            googleProfile.value = withContext(Dispatchers.IO) {
                runCatching {
                    GoogleSignIn.getLastSignedInAccount(context)?.let { account ->
                        DrawerGoogleProfile(account.email.orEmpty(), account.displayName, account.photoUrl?.toString())
                    }
                }.getOrNull()
            }
        }
    }

    fun setDrawerDisplayName(accountKey: String, name: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = runCatching {
                check(drawerAccountKey(preferences.userPreferences.first().googleDriveAccountEmail) == accountKey)
                drawerProfiles.setName(accountKey, name)
            }.isSuccess
            onComplete(success)
        }
    }

    fun setTheme(mode: VaultThemeMode) {
        viewModelScope.launch { preferences.setTheme(mode) }
    }

    fun setWorkspace(workspace: String) {
        viewModelScope.launch { preferences.setWorkspace(workspace) }
    }

    fun setExplorerExpandedKeys(keys: Set<String>) {
        viewModelScope.launch { preferences.setExplorerExpandedKeys(keys) }
    }

    fun pushGoogleDriveSync(onComplete: (String) -> Unit) {
        googleDriveRestoreController.get().startPush { result ->
            onComplete(result.displayMessage())
        }
    }
}
