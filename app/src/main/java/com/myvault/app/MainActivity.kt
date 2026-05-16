package com.myvault.app

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.ui.navigation.VaultNavHost
import com.myvault.app.ui.screens.parseRichImport
import com.myvault.app.ui.screens.toJsonArrayString
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeTokens
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: VaultPreferences
    @Inject lateinit var noteRepository: NoteRepository
    private var promptShowing = false
    private var lastPausedAt = 0L
    private var pendingSharedNoteId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedIntent(intent)

        setContent {
            val loadedPreferences by preferences.userPreferences.collectAsState(initial = null)
            val userPreferences = loadedPreferences ?: VaultUserPreferences()
            val lifecycleOwner = LocalLifecycleOwner.current
            var unlocked by rememberSaveable { mutableStateOf(!userPreferences.securityLockEnabled) }
            var promptMessage by remember { mutableStateOf<String?>(null) }

            fun requestUnlock() {
                showVaultAuthenticationPrompt(
                    onAuthenticated = {
                        unlocked = true
                        promptMessage = null
                    },
                    onUnavailable = { message ->
                        promptMessage = message
                    },
                )
            }

            LaunchedEffect(loadedPreferences, userPreferences.securityLockEnabled) {
                if (loadedPreferences == null) return@LaunchedEffect
                if (userPreferences.securityLockEnabled) {
                    unlocked = false
                    requestUnlock()
                } else {
                    unlocked = true
                    promptMessage = null
                }
            }

            DisposableEffect(
                lifecycleOwner,
                userPreferences.securityLockEnabled,
                userPreferences.securityLockTimeoutMs,
            ) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> lastPausedAt = System.currentTimeMillis()
                        Lifecycle.Event.ON_RESUME -> {
                            val shouldRelock = userPreferences.securityLockEnabled &&
                                System.currentTimeMillis() - lastPausedAt > userPreferences.securityLockTimeoutMs
                            if (shouldRelock) {
                                unlocked = false
                                requestUnlock()
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            VaultTheme(
                mode = userPreferences.theme,
                accentColorHex = userPreferences.accentColor,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (loadedPreferences != null && unlocked) {
                        VaultNavHost(
                            pendingOpenNoteId = pendingSharedNoteId,
                            onPendingOpenNoteConsumed = { pendingSharedNoteId = null },
                        )
                    }
                    if (loadedPreferences != null && userPreferences.securityLockEnabled && !unlocked) {
                        VaultLockOverlay(
                            message = promptMessage,
                            onUnlockClick = ::requestUnlock,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
        val plain = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (html.isNullOrBlank() && plain.isNullOrBlank()) return

        lifecycleScope.launch {
            val imported = parseRichImport(html = html, plainText = plain)
            val text = imported.document.text
            val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: text.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(80)
                ?: "Imported note"
            val noteId = noteRepository.createImportedRichTextNote(
                title = title,
                text = text,
                styleMarksJson = imported.document.styleMarks.toJsonArrayString(),
            )
            pendingSharedNoteId = noteId
            Toast.makeText(
                this@MainActivity,
                if (imported.formattingPreserved) "Imported formatted note" else "Imported note as plain text",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showVaultAuthenticationPrompt(
        onAuthenticated: () -> Unit,
        onUnavailable: (String) -> Unit,
    ) {
        if (promptShowing) return
        promptShowing = true

        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Unlock My Vault")
            .setSubtitle("Confirm it is you to continue")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        runCatching {
            prompt.authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        promptShowing = false
                        onAuthenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        promptShowing = false
                        val message = errString?.toString().orEmpty().ifBlank {
                            "Unable to unlock with this device"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        onUnavailable(message)
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }.onFailure { error ->
            promptShowing = false
            onUnavailable(error.message ?: "Unable to show the device lock prompt")
        }
    }

}

@Composable
private fun VaultLockOverlay(
    message: String?,
    onUnlockClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accent)
            Text(
                text = "My Vault is locked",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Button(onClick = onUnlockClick) {
                Text("Unlock")
            }
        }
    }
}
