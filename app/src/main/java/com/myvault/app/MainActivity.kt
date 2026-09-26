package com.myvault.app

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.ui.navigation.VaultNavHost
import com.myvault.app.ui.screens.parseRichImport
import com.myvault.app.ui.screens.toJsonArrayString
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.widget.quran.QuranWidgetContract
import com.myvault.app.widget.quran.QuranWidgetProvider
import com.myvault.app.widget.quran.QuranWidgetStateStore
import com.myvault.app.widget.quran.validatedWidgetLocation
import com.myvault.app.widget.note.NoteWidgetContract
import com.myvault.app.widget.note.QuickNoteLaunchGuard
import com.myvault.app.widget.note.shouldCreateQuickNote
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var preferences: VaultPreferences
    @Inject lateinit var noteRepository: NoteRepository
    private var promptShowing = false
    private var lastPausedAt = 0L
    private var pendingSharedNoteId by mutableStateOf<String?>(null)
    private var pendingWidgetQuranVerseKey by mutableStateOf<String?>(null)
    private var pendingWidgetNoteOpen by mutableStateOf<PendingWidgetNoteOpen?>(null)
    private var pendingQuickNoteCreate by mutableStateOf(false)
    private var quickNoteCreationInFlight = false
    private lateinit var quickNoteLaunchGuard: QuickNoteLaunchGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickNoteLaunchGuard = QuickNoteLaunchGuard(this)
        if (savedInstanceState != null) {
            lastPausedAt = savedInstanceState.getLong("lastPausedAt", 0L)
            pendingSharedNoteId = savedInstanceState.getString("pendingSharedNoteId")
            pendingWidgetQuranVerseKey = savedInstanceState.getString("pendingWidgetQuranVerseKey")
            savedInstanceState.getString("pendingWidgetNoteId")?.let { noteId ->
                pendingWidgetNoteOpen = PendingWidgetNoteOpen(
                    noteId = noteId,
                    courseId = savedInstanceState.getString("pendingWidgetCourseId"),
                    quickFocus = savedInstanceState.getBoolean("pendingWidgetQuickFocus"),
                )
            }
            pendingQuickNoteCreate = savedInstanceState.getBoolean("pendingQuickNoteCreate")
        }
        handleSharedIntent(intent)
        handleQuranWidgetIntent(intent)
        handleNoteWidgetIntent(intent)

        setContent {
            val loadedPreferences by preferences.userPreferences.collectAsStateWithLifecycle(initialValue = null)
            val userPreferences = loadedPreferences ?: VaultUserPreferences(theme = VaultThemeMode.Dark)
            val lifecycleOwner = LocalLifecycleOwner.current
            var unlocked by rememberSaveable { mutableStateOf(false) }
            var lockGeneration by rememberSaveable { mutableLongStateOf(0L) }
            var promptedGeneration by rememberSaveable { mutableLongStateOf(-1L) }
            var promptMessage by remember { mutableStateOf<String?>(null) }

            fun requestUnlock(force: Boolean = false) {
                if (loadedPreferences == null || !userPreferences.securityLockEnabled || unlocked) return
                if (!force && promptedGeneration == lockGeneration) return
                promptedGeneration = lockGeneration
                showVaultAuthenticationPrompt(
                    onAuthenticated = {
                        unlocked = true
                        lastPausedAt = System.currentTimeMillis()
                        promptMessage = null
                    },
                    onUnavailable = { message ->
                        promptMessage = message
                    },
                )
            }

            fun lockAndPrompt() {
                if (loadedPreferences == null || !userPreferences.securityLockEnabled) return
                if (unlocked) {
                    unlocked = false
                    lockGeneration += 1
                    promptMessage = null
                }
                requestUnlock()
            }

            LaunchedEffect(loadedPreferences != null, userPreferences.securityLockEnabled) {
                if (loadedPreferences == null) return@LaunchedEffect
                if (userPreferences.securityLockEnabled) {
                    lockAndPrompt()
                } else {
                    unlocked = true
                    promptMessage = null
                }
            }

            LaunchedEffect(unlocked, pendingQuickNoteCreate) {
                if (!shouldCreateQuickNote(unlocked, pendingQuickNoteCreate, quickNoteCreationInFlight)) {
                    return@LaunchedEffect
                }
                quickNoteCreationInFlight = true
                runCatching { noteRepository.createNote(folderId = null) }
                    .onSuccess { noteId ->
                        pendingWidgetNoteOpen = PendingWidgetNoteOpen(
                            noteId = noteId,
                            courseId = null,
                            quickFocus = true,
                        )
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@MainActivity,
                            error.message ?: "Unable to create note",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                pendingQuickNoteCreate = false
                quickNoteCreationInFlight = false
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
                                lockAndPrompt()
                            } else if (userPreferences.securityLockEnabled && !unlocked) {
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
                materialYouEnabled = userPreferences.materialYouEnabled,
            ) {
                val locked = loadedPreferences != null && userPreferences.securityLockEnabled && !unlocked
                val colors = VaultThemeTokens.colors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bg),
                ) {
                    if (loadedPreferences != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (locked) Modifier.blur(18.dp) else Modifier),
                        ) {
                            VaultNavHost(
                                pendingOpenNoteId = if (unlocked) {
                                    pendingWidgetNoteOpen?.noteId ?: pendingSharedNoteId
                                } else {
                                    null
                                },
                                pendingOpenNoteCourseId = pendingWidgetNoteOpen?.courseId,
                                pendingOpenNoteQuickFocus = pendingWidgetNoteOpen?.quickFocus == true,
                                onPendingOpenNoteConsumed = {
                                    pendingWidgetNoteOpen = null
                                    pendingSharedNoteId = null
                                },
                                pendingOpenQuranVerseKey = pendingWidgetQuranVerseKey,
                                onPendingOpenQuranConsumed = { pendingWidgetQuranVerseKey = null },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = locked,
                        enter = fadeIn(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                    ) {
                        VaultLockOverlay(
                            message = promptMessage,
                            onUnlockClick = { requestUnlock(force = true) },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("lastPausedAt", lastPausedAt)
        outState.putString("pendingSharedNoteId", pendingSharedNoteId)
        outState.putString("pendingWidgetQuranVerseKey", pendingWidgetQuranVerseKey)
        outState.putString("pendingWidgetNoteId", pendingWidgetNoteOpen?.noteId)
        outState.putString("pendingWidgetCourseId", pendingWidgetNoteOpen?.courseId)
        outState.putBoolean("pendingWidgetQuickFocus", pendingWidgetNoteOpen?.quickFocus == true)
        outState.putBoolean("pendingQuickNoteCreate", pendingQuickNoteCreate)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
        handleQuranWidgetIntent(intent)
        handleNoteWidgetIntent(intent)
    }

    private fun handleNoteWidgetIntent(intent: Intent?) {
        val source = intent ?: return
        when (source.action) {
            NoteWidgetContract.ACTION_OPEN_NOTE -> {
                val noteId = source.getStringExtra(NoteWidgetContract.EXTRA_NOTE_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                pendingWidgetNoteOpen = PendingWidgetNoteOpen(
                    noteId = noteId,
                    courseId = source.getStringExtra(NoteWidgetContract.EXTRA_COURSE_ID),
                    quickFocus = false,
                )
                source.action = Intent.ACTION_MAIN
            }
            NoteWidgetContract.ACTION_QUICK_CREATE_NOTE -> {
                if (
                    !pendingQuickNoteCreate &&
                    !quickNoteCreationInFlight &&
                    quickNoteLaunchGuard.accept()
                ) {
                    pendingQuickNoteCreate = true
                }
                source.action = Intent.ACTION_MAIN
            }
        }
    }

    private fun handleQuranWidgetIntent(intent: Intent?) {
        val source = intent ?: return
        if (!source.hasExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER)) return
        val location = validatedWidgetLocation(
            surahNumber = source.getIntExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, 1),
            ayahNumber = source.getIntExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, 1),
        )
        val appWidgetId = source.getIntExtra(
            QuranWidgetContract.EXTRA_WIDGET_ID,
            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        QuranWidgetStateStore(this).setAnchor(appWidgetId, location.surahNumber, location.ayahNumber)
        if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            QuranWidgetProvider.updateWidget(
                this,
                android.appwidget.AppWidgetManager.getInstance(this),
                appWidgetId,
            )
        }
        pendingWidgetQuranVerseKey = location.verseKey
        // The pending target now owns delivery through unlock/navigation.
        source.removeExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER)
        source.removeExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER)
        source.removeExtra(QuranWidgetContract.EXTRA_WIDGET_ID)
        source.data = null
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

private data class PendingWidgetNoteOpen(
    val noteId: String,
    val courseId: String?,
    val quickFocus: Boolean,
)

@Composable
private fun VaultLockOverlay(
    message: String?,
    onUnlockClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.985f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )
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
