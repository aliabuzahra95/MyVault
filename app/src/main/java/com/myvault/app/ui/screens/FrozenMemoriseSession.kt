@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.myvault.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myvault.app.R
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.memorization.QuranMemorizationAnalysis
import com.myvault.app.data.quran.memorization.QuranMemorizationAnalysisEngine
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranMemorizationAttemptFactory
import com.myvault.app.data.quran.memorization.QuranMemorizationRecorder
import com.myvault.app.data.quran.memorization.QuranMemorizationRecording
import com.myvault.app.data.quran.memorization.QuranMemorizationScoreEngine
import com.myvault.app.data.quran.memorization.QuranMemorizationWordAnalysis
import com.myvault.app.data.quran.memorization.QuranMemorizationWordState
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAnalysis
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttemptFactory
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationTestEngine
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationTestMode
import com.myvault.app.data.quran.speech.SpeechRecognitionProviderType
import com.myvault.app.data.quran.speech.SpeechRecognitionRequest
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.MemoriseSessionUiState
import com.myvault.app.ui.viewmodel.MemoriseStatusChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MemoriseListenStage {
    Ready, Recording, Paused, Review, Analyzing, Results, RecordingError, TranscriptionError, AnalysisError, Empty, SurahComplete
}

private enum class MemoriseSessionSheet { Options, Status, Provider, Permission, PermissionDenied, PermissionSettings, Details }
private enum class MemoriseHideMode { Off, Half, All }

private val MemoriseQuranFont = FontFamily(Font(R.font.uthmani_hafs))

@Composable
internal fun FrozenMemoriseSession(
    sessionState: MemoriseSessionUiState,
    onBack: () -> Unit,
    onNextAyah: () -> Boolean,
    onConsumeAutoRecord: () -> Unit,
    onSetStatus: (String, MemoriseStatusChoice) -> Unit,
    onAttemptCompleted: (QuranMemorizationAttempt) -> Unit,
    onSurahAttemptCompleted: (QuranSurahMemorizationAttempt) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetKey = if (sessionState.wholeSurah) "${sessionState.surah.num}:surah" else sessionState.ayah?.verseKey.orEmpty()
    val recorder = remember(targetKey) { QuranMemorizationRecorder(context) }
    var providerName by rememberSaveable { mutableStateOf(SpeechRecognitionProviderType.GoogleChirp.name) }
    val providerType = SpeechRecognitionProviderType.fromName(providerName)
    var hideModeName by rememberSaveable(targetKey) { mutableStateOf(MemoriseHideMode.Off.name) }
    val hideMode = runCatching { MemoriseHideMode.valueOf(hideModeName) }.getOrDefault(MemoriseHideMode.Off)
    // The recorder cannot survive process recreation, so active recording state must not be saveable.
    var stageName by remember(targetKey) { mutableStateOf(MemoriseListenStage.Ready.name) }
    val stage = runCatching { MemoriseListenStage.valueOf(stageName) }.getOrDefault(MemoriseListenStage.Ready)
    var sheetName by rememberSaveable { mutableStateOf<String?>(null) }
    val sheet = sheetName?.let { runCatching { MemoriseSessionSheet.valueOf(it) }.getOrNull() }
    var elapsedMs by remember(targetKey) { mutableLongStateOf(0L) }
    var recording by remember(targetKey) { mutableStateOf<QuranMemorizationRecording?>(null) }
    var playingRecording by remember(targetKey) { mutableStateOf(false) }
    var speechResult by remember(targetKey) { mutableStateOf<SpeechRecognitionResult?>(null) }
    var ayahAnalysis by remember(targetKey) { mutableStateOf<QuranMemorizationAnalysis?>(null) }
    var ayahAttempt by remember(targetKey) { mutableStateOf<QuranMemorizationAttempt?>(null) }
    var surahAnalysis by remember(targetKey) { mutableStateOf<QuranSurahMemorizationAnalysis?>(null) }
    var surahAttempt by remember(targetKey) { mutableStateOf<QuranSurahMemorizationAttempt?>(null) }
    var errorMessage by remember(targetKey) { mutableStateOf<String?>(null) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    fun startRecording() {
        val ayah = sessionState.ayah ?: return
        runCatching {
            recorder.stopPlayback()
            recorder.start(sessionState.surah.num, if (sessionState.wholeSurah) 0 else ayah.ayahNumber)
            elapsedMs = 0L
            recording = null
            playingRecording = false
            speechResult = null
            ayahAnalysis = null
            ayahAttempt = null
            surahAnalysis = null
            surahAttempt = null
            errorMessage = null
            stageName = MemoriseListenStage.Recording.name
        }.onFailure {
            errorMessage = it.message ?: "Recording could not start."
            stageName = MemoriseListenStage.RecordingError.name
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequested = true
        if (granted) {
            sheetName = null
            startRecording()
        } else {
            val permanentlyDenied = context.findActivity()?.let { !it.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) } == true
            sheetName = if (permanentlyDenied) MemoriseSessionSheet.PermissionSettings.name else MemoriseSessionSheet.PermissionDenied.name
        }
    }

    fun requestRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            sheetName = MemoriseSessionSheet.Permission.name
        }
    }

    fun stopForReview() {
        val ayah = sessionState.ayah ?: return
        elapsedMs = recorder.elapsedMs()
        val completed = recorder.stop(sessionState.surah.num, if (sessionState.wholeSurah) 0 else ayah.ayahNumber)
        recording = completed
        when {
            completed == null -> {
                errorMessage = "The recording could not be saved."
                stageName = MemoriseListenStage.RecordingError.name
            }
            completed.durationMs < 300L || completed.file.length() <= 44L -> {
                errorMessage = "No speech was captured."
                stageName = MemoriseListenStage.Empty.name
            }
            else -> stageName = MemoriseListenStage.Review.name
        }
    }

    fun analyzeRecording() {
        val captured = recording ?: return
        val ayah = sessionState.ayah ?: return
        recorder.stopPlayback()
        playingRecording = false
        stageName = MemoriseListenStage.Analyzing.name
        errorMessage = null
        scope.launch {
            try {
                val provider = providerType.createProvider()
                val result = provider.transcribe(
                    SpeechRecognitionRequest(
                        audioFile = captured.file,
                        audioUri = captured.uri,
                        surahNumber = captured.surahNumber,
                        ayahNumber = captured.ayahNumber,
                        verseKey = if (sessionState.wholeSurah) "${sessionState.surah.num}:surah" else ayah.verseKey,
                        expectedText = if (sessionState.wholeSurah) sessionState.ayahs.joinToString(" ") { it.arabicText } else ayah.arabicText,
                        durationMs = captured.durationMs,
                        recordedAtMs = captured.createdAt,
                    ),
                )
                speechResult = result
                if (result.isSuccess && result.transcript.isBlank()) {
                    errorMessage = "No speech was detected."
                    stageName = MemoriseListenStage.Empty.name
                    return@launch
                }
                if (sessionState.wholeSurah) {
                    val analysis = QuranSurahMemorizationTestEngine.analyze(
                        sessionState.surah,
                        sessionState.ayahs,
                        result,
                        QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
                    )
                    val attempt = QuranSurahMemorizationAttemptFactory.from(
                        sessionState.surah,
                        sessionState.ayahs,
                        captured.durationMs,
                        result,
                        analysis,
                        QuranSurahMemorizationTestMode.FULL_SURAH_TEST,
                    )
                    surahAnalysis = analysis
                    surahAttempt = attempt
                    onSurahAttemptCompleted(attempt)
                } else {
                    val analysis = if (result.isSuccess) QuranMemorizationAnalysisEngine.analyze(ayah, result) else null
                    val attempt = QuranMemorizationAttemptFactory.from(ayah, captured.durationMs, result, analysis)
                    ayahAnalysis = analysis
                    ayahAttempt = attempt
                    onAttemptCompleted(attempt)
                }
                if (result.isSuccess) {
                    stageName = MemoriseListenStage.Results.name
                } else {
                    errorMessage = result.errorMessage ?: "Speech recognition failed."
                    stageName = MemoriseListenStage.TranscriptionError.name
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                errorMessage = "The captured recitation could not be analysed."
                stageName = MemoriseListenStage.AnalysisError.name
            }
        }
    }

    LaunchedEffect(stage) {
        while (stageName == MemoriseListenStage.Recording.name) {
            elapsedMs = recorder.elapsedMs()
            delay(200L)
        }
    }

    LaunchedEffect(sessionState.autoRecordRequestId, sessionState.ayah?.verseKey) {
        if (sessionState.autoRecordRequestId != 0L && sessionState.ayah != null) {
            requestRecording()
            onConsumeAutoRecord()
        }
    }

    DisposableEffect(targetKey) {
        onDispose { recorder.release() }
    }

    val canvas = if (colors.bg.luminance() > 0.7f) Color(0xFFFBFAF5) else colors.bg
    Column(Modifier.fillMaxSize().background(canvas)) {
        MemoriseSessionHeader(
            sessionState = sessionState,
            onBack = onBack,
            onMore = { sheetName = MemoriseSessionSheet.Options.name },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                sessionState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = colors.accent, strokeWidth = 2.dp)
                sessionState.errorMessage != null -> Text(sessionState.errorMessage, Modifier.align(Alignment.Center).padding(28.dp), color = colors.textSecondary, textAlign = TextAlign.Center)
                stage == MemoriseListenStage.SurahComplete -> MemoriseSurahComplete(Modifier.align(Alignment.Center))
                else -> MemoriseAyahCanvas(
                    sessionState = sessionState,
                    hideMode = hideMode,
                    analysis = ayahAnalysis,
                    resultVisible = stage == MemoriseListenStage.Results,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (!sessionState.loading && sessionState.ayah != null) {
            MemoriseSessionControls(
                stage = stage,
                hideMode = hideMode,
                provider = providerType,
                elapsedMs = elapsedMs,
                recording = recording,
                playingRecording = playingRecording,
                ayahAttempt = ayahAttempt,
                surahAttempt = surahAttempt,
                errorMessage = errorMessage,
                onHide = { hideModeName = it.name },
                onStart = ::requestRecording,
                onPause = { recorder.pause(); elapsedMs = recorder.elapsedMs(); stageName = MemoriseListenStage.Paused.name },
                onResume = { recorder.resume(); stageName = MemoriseListenStage.Recording.name },
                onStop = ::stopForReview,
                onPlayback = {
                    val captured = recording ?: return@MemoriseSessionControls
                    if (playingRecording) {
                        recorder.stopPlayback(); playingRecording = false
                    } else {
                        recorder.play(captured, { playingRecording = false }, { playingRecording = false; errorMessage = "Playback failed." })
                        playingRecording = true
                    }
                },
                onReRecord = ::requestRecording,
                onAnalyze = ::analyzeRecording,
                onRetry = ::analyzeRecording,
                onTryAgain = { stageName = MemoriseListenStage.Ready.name },
                onNext = {
                    if (sessionState.wholeSurah || !onNextAyah()) stageName = MemoriseListenStage.SurahComplete.name
                    else stageName = MemoriseListenStage.Ready.name
                },
                onDone = onBack,
                onDetails = { sheetName = MemoriseSessionSheet.Details.name },
                onProvider = { sheetName = MemoriseSessionSheet.Provider.name },
            )
        }
    }

    sheet?.let {
        MemoriseSessionBottomSheet(
            mode = it,
            sessionState = sessionState,
            provider = providerType,
            permissionRequested = permissionRequested,
            ayahAttempt = ayahAttempt,
            surahAttempt = surahAttempt,
            speechResult = speechResult,
            onDismiss = { sheetName = null },
            onOpen = { sheetName = it.name },
            onSelectProvider = { providerName = it.name; sheetName = null },
            onSelectStatus = { status ->
                sessionState.ayah?.verseKey?.let { onSetStatus(it, status) }
                sheetName = null
            },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                sheetName = null
            },
        )
    }
}

@Composable
private fun MemoriseSessionHeader(sessionState: MemoriseSessionUiState, onBack: () -> Unit, onMore: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Memorise", tint = colors.textSecondary) }
        Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
            Text(sessionState.surah.name, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.W800)
            Text(
                if (sessionState.wholeSurah) "Whole Surah · current ${sessionState.surah.num}:${sessionState.targetAyahNumber}" else "${sessionState.surah.num}:${sessionState.targetAyahNumber} · Memorise session",
                color = colors.textMuted,
                fontSize = 9.5.sp,
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.MoreVert, "Memorise options", tint = colors.textSecondary) }
    }
}

@Composable
private fun MemoriseAyahCanvas(
    sessionState: MemoriseSessionUiState,
    hideMode: MemoriseHideMode,
    analysis: QuranMemorizationAnalysis?,
    resultVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val ayah = sessionState.ayah ?: return
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (sessionState.wholeSurah) "Whole Surah · Ayah ${ayah.ayahNumber}" else "Ayah ${ayah.ayahNumber} of ${sessionState.surah.ayat}", color = colors.textMuted, fontSize = 9.5.sp)
            Text(sessionState.surah.arabic, color = colors.textMuted, fontSize = 10.sp)
        }
        if (resultVisible) MemoriseResultSummary(analysis)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val analyses = analysis?.expectedWords?.associateBy { it.word.wordId }.orEmpty()
                ayah.words.forEachIndexed { index, word ->
                    val hidden = !resultVisible && when (hideMode) {
                        MemoriseHideMode.Off -> false
                        MemoriseHideMode.Half -> index % 2 == 1
                        MemoriseHideMode.All -> true
                    }
                    MemoriseWord(word.arabicText, hidden, analyses[word.wordId]?.state.takeIf { resultVisible })
                    Spacer(Modifier.width(5.dp))
                }
                Text("﴿${ayah.ayahNumber}﴾", color = colors.accent, fontFamily = MemoriseQuranFont, fontSize = 16.sp)
            }
        }
        Text(ayah.translation, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        if (resultVisible && analysis?.extraWords?.isNotEmpty() == true) {
            Surface(shape = RoundedCornerShape(6.dp), color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Extra", color = colors.warning, fontSize = 9.sp, fontWeight = FontWeight.W800)
                    Text(analysis.extraWords.joinToString(" ") { it.recognizedWord.text }, color = colors.text, fontFamily = MemoriseQuranFont, fontSize = 18.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Text("Spoken addition · shown separately from the ayah", color = colors.textMuted, fontSize = 9.sp)
                }
            }
        }
        if (!resultVisible) Text("Recite from memory. Hidden words keep their place to support visual recall.", color = colors.textMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MemoriseWord(text: String, hidden: Boolean, state: QuranMemorizationWordState?) {
    val colors = VaultThemeTokens.colors
    val display = if (hidden) "          " else text
    val borderColor = when (state) {
        QuranMemorizationWordState.MISSING -> Color(0xFFC25C63)
        QuranMemorizationWordState.EXTRA -> Color(0xFFC98A32)
        QuranMemorizationWordState.REPEATED -> Color(0xFF557FC3)
        QuranMemorizationWordState.UNKNOWN -> colors.textMuted
        else -> Color.Transparent
    }
    Text(
        display,
        modifier = Modifier
            .then(if (hidden) Modifier.background(colors.elevated, RoundedCornerShape(3.dp)).height(28.dp) else Modifier)
            .then(if (state != null && state != QuranMemorizationWordState.CORRECT) Modifier.border(1.dp, borderColor, RoundedCornerShape(3.dp)).padding(horizontal = 2.dp) else Modifier),
        color = colors.text,
        fontFamily = MemoriseQuranFont,
        fontSize = 27.sp,
        lineHeight = 44.sp,
        textDecoration = if (state == QuranMemorizationWordState.CORRECT) TextDecoration.Underline else TextDecoration.None,
    )
}

@Composable
private fun MemoriseResultSummary(analysis: QuranMemorizationAnalysis?) {
    val colors = VaultThemeTokens.colors
    val score = analysis?.let {
        QuranMemorizationScoreEngine.score(
            expectedWordCount = it.expectedWordCount,
            recognizedCount = it.recognizedWordCount,
            missingCount = it.missingWordCount,
            extraCount = it.extraWordCount,
            repeatedCount = it.repeatedWordCount,
            unknownCount = it.unknownWordCount,
            transcriptionSucceeded = true,
            analysisSucceeded = true,
        )
    }
    Surface(shape = RoundedCornerShape(7.dp), color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = colors.success, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(score?.let { "${it.overallScore}% · ${it.grade.label}" } ?: "Recitation result", color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.W800)
                    Text("Review missing, extra, repeated and unclear words", color = colors.textMuted, fontSize = 9.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Correct", "Missing", "Extra", "Repeated", "Unclear").forEach { Text(it, color = colors.textSecondary, fontSize = 8.sp) }
            }
        }
    }
}

@Composable
private fun MemoriseSessionControls(
    stage: MemoriseListenStage,
    hideMode: MemoriseHideMode,
    provider: SpeechRecognitionProviderType,
    elapsedMs: Long,
    recording: QuranMemorizationRecording?,
    playingRecording: Boolean,
    ayahAttempt: QuranMemorizationAttempt?,
    surahAttempt: QuranSurahMemorizationAttempt?,
    errorMessage: String?,
    onHide: (MemoriseHideMode) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPlayback: () -> Unit,
    onReRecord: () -> Unit,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    onTryAgain: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onDetails: () -> Unit,
    onProvider: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text("Hide", color = colors.textMuted, fontSize = 9.sp, modifier = Modifier.padding(end = 8.dp))
            MemoriseHideMode.entries.forEach { mode ->
                val label = when (mode) { MemoriseHideMode.Off -> "Off"; MemoriseHideMode.Half -> "1/2"; MemoriseHideMode.All -> "All" }
                Text(
                    label,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (hideMode == mode) colors.accentSoft else Color.Transparent).clickable { onHide(mode) }.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = if (hideMode == mode) colors.accent else colors.textSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = colors.bg, border = BorderStroke(1.dp, colors.border)) {
            when (stage) {
                MemoriseListenStage.Ready -> MemoriseListenRow(Icons.Outlined.Mic, "AI Listen ready", "${provider.displayName} · recite the selected passage") {
                    MemoriseActionButton("Start", true, onStart)
                }
                MemoriseListenStage.Recording -> MemoriseListenRow(Icons.Outlined.Mic, "Listening…", "${formatDuration(elapsedMs)} · keep reciting") {
                    MemoriseActionButton("Pause", false, onPause); MemoriseIconAction(Icons.Outlined.Stop, "Stop", onStop)
                }
                MemoriseListenStage.Paused -> MemoriseListenRow(Icons.Outlined.Pause, "Recording paused", "${formatDuration(elapsedMs)} captured") {
                    MemoriseActionButton("Resume", false, onResume); MemoriseIconAction(Icons.Outlined.Stop, "Stop", onStop)
                }
                MemoriseListenStage.Review -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoriseListenRow(Icons.Outlined.PlayArrow, "Recording complete", "${formatDuration(recording?.durationMs ?: elapsedMs)} · captured recitation") {}
                    Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border)) { Box(Modifier.fillMaxWidth(if (playingRecording) 0.68f else 0.18f).height(2.dp).background(colors.accent)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MemoriseActionButton(if (playingRecording) "Stop playback" else "Play recording", false, onPlayback, Modifier.weight(1f))
                        MemoriseActionButton("Re-record", false, onReRecord, Modifier.weight(1f))
                        MemoriseActionButton("Analyse", true, onAnalyze, Modifier.weight(1f))
                    }
                }
                MemoriseListenStage.Analyzing -> MemoriseListenRow(null, "Transcribing and analysing…", "${provider.displayName} · comparing ordered words", leading = { CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp) }) {}
                MemoriseListenStage.Results -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { MemoriseActionButton("Details", false, onDetails) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoriseActionButton("Try again", false, onTryAgain, Modifier.weight(1f))
                        MemoriseActionButton("Next ayah", true, onNext, Modifier.weight(1f))
                    }
                }
                MemoriseListenStage.RecordingError -> MemoriseErrorControls("Recording failed", errorMessage ?: "The selected target is preserved", "Retry", onStart, null, null)
                MemoriseListenStage.TranscriptionError -> MemoriseErrorControls("Transcription failed", errorMessage ?: "Captured audio is still available", "Retry", onRetry, "Re-record", onReRecord)
                MemoriseListenStage.AnalysisError -> MemoriseErrorControls("Analysis failed", errorMessage ?: "Captured audio is still available", "Retry", onRetry, "Re-record", onReRecord)
                MemoriseListenStage.Empty -> MemoriseErrorControls("No speech detected", "No score was created", "Re-record", onReRecord, null, null)
                MemoriseListenStage.SurahComplete -> Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) { MemoriseActionButton("Back to Memorise", true, onDone) }
            }
        }
        if (stage == MemoriseListenStage.Ready) {
            Text(provider.displayName, Modifier.align(Alignment.End).clickable(onClick = onProvider).padding(4.dp), color = colors.textMuted, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun MemoriseListenRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (leading != null) leading() else if (icon != null) Icon(icon, null, tint = colors.accent, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f)) { Text(title, color = colors.text, fontSize = 10.5.sp, fontWeight = FontWeight.W800); Text(subtitle, color = colors.textMuted, fontSize = 8.5.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun MemoriseErrorControls(title: String, subtitle: String, primary: String, onPrimary: () -> Unit, secondary: String?, onSecondary: (() -> Unit)?) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoriseListenRow(Icons.Outlined.Info, title, subtitle) {}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            secondary?.let { MemoriseActionButton(it, false, onSecondary ?: {}) }
            if (secondary != null) Spacer(Modifier.width(7.dp))
            MemoriseActionButton(primary, true, onPrimary)
        }
    }
}

@Composable
private fun MemoriseActionButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VaultThemeTokens.colors
    Text(
        label,
        modifier.clip(RoundedCornerShape(6.dp)).background(if (primary) colors.accent else colors.elevated).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        color = if (primary) Color.White else colors.textSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MemoriseIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(colors.elevated).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MemoriseSurahComplete(modifier: Modifier = Modifier) {
    val colors = VaultThemeTokens.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, tint = colors.success, modifier = Modifier.size(25.dp))
        Text("Surah complete", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.W800)
        Text("Whole-Surah attempt finished", color = colors.textSecondary, fontSize = 10.sp)
        Text("Review your recitation results or return to Memorise.", color = colors.textMuted, fontSize = 9.5.sp)
    }
}

@Composable
private fun MemoriseSessionBottomSheet(
    mode: MemoriseSessionSheet,
    sessionState: MemoriseSessionUiState,
    provider: SpeechRecognitionProviderType,
    permissionRequested: Boolean,
    ayahAttempt: QuranMemorizationAttempt?,
    surahAttempt: QuranSurahMemorizationAttempt?,
    speechResult: SpeechRecognitionResult?,
    onDismiss: () -> Unit,
    onOpen: (MemoriseSessionSheet) -> Unit,
    onSelectProvider: (SpeechRecognitionProviderType) -> Unit,
    onSelectStatus: (MemoriseStatusChoice) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val title = when (mode) {
        MemoriseSessionSheet.Options -> "Memorise options"
        MemoriseSessionSheet.Status -> "Set status"
        MemoriseSessionSheet.Provider -> "Speech recognition"
        MemoriseSessionSheet.Permission -> "Microphone access"
        MemoriseSessionSheet.PermissionDenied -> "Microphone denied"
        MemoriseSessionSheet.PermissionSettings -> "Allow microphone"
        MemoriseSessionSheet.Details -> "Result details"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        dragHandle = { Box(Modifier.padding(vertical = 8.dp).size(36.dp, 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${sessionState.surah.name} · ${sessionState.surah.num}:${sessionState.targetAyahNumber}", color = colors.textMuted, fontSize = 9.5.sp)
                    Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.W800)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary) }
            }
            when (mode) {
                MemoriseSessionSheet.Options -> {
                    MemoriseSheetRow(Icons.Outlined.CheckCircle, "Set status", "Update this ayah") { onOpen(MemoriseSessionSheet.Status) }
                    MemoriseSheetRow(Icons.Outlined.Mic, "Speech recognition", provider.displayName) { onOpen(MemoriseSessionSheet.Provider) }
                }
                MemoriseSessionSheet.Status -> MemoriseStatusChoice.entries.forEach { status ->
                    MemoriseSheetRow(null, status.label, null) { onSelectStatus(status) }
                }
                MemoriseSessionSheet.Provider -> SpeechRecognitionProviderType.entries.forEach { item ->
                    MemoriseSheetRow(if (item == SpeechRecognitionProviderType.GoogleChirp) Icons.Outlined.Cloud else Icons.Outlined.Mic, item.displayName, "Speech recognition", selected = item == provider) { onSelectProvider(item) }
                }
                MemoriseSessionSheet.Permission -> MemorisePermissionContent(
                    icon = Icons.Outlined.Mic,
                    message = "Allow microphone access to capture this recitation for AI Listen.",
                    detail = "After permission is granted, recording starts automatically. Recordings remain device-local.",
                    primary = "Continue",
                    onPrimary = onRequestPermission,
                    secondary = "Not now",
                    onSecondary = onDismiss,
                )
                MemoriseSessionSheet.PermissionDenied -> MemorisePermissionContent(
                    icon = Icons.Outlined.Lock,
                    message = "Microphone permission was denied.",
                    detail = "The selected ayah is preserved. Grant access to start recording.",
                    primary = "Try again",
                    onPrimary = onRequestPermission,
                    secondary = "Cancel",
                    onSecondary = onDismiss,
                )
                MemoriseSessionSheet.PermissionSettings -> MemorisePermissionContent(
                    icon = Icons.Outlined.Settings,
                    message = "Enable microphone access in Android Settings.",
                    detail = "Return here afterward; the session target remains selected.",
                    primary = "Open Android Settings",
                    onPrimary = onOpenSettings,
                    secondary = "Cancel",
                    onSecondary = onDismiss,
                )
                MemoriseSessionSheet.Details -> MemoriseDetails(ayahAttempt, surahAttempt, speechResult)
            }
        }
    }
}

@Composable
private fun MemoriseSheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, subtitle: String?, selected: Boolean = false, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) { Text(title, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.W700); subtitle?.let { Text(it, color = colors.textMuted, fontSize = 9.5.sp) } }
        if (selected) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(17.dp)) else Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MemorisePermissionContent(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, detail: String, primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).background(colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = colors.accent, modifier = Modifier.size(21.dp)) }
        Text(message, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.W700, textAlign = TextAlign.Center)
        Text(detail, color = colors.textMuted, fontSize = 9.5.sp, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MemoriseActionButton(secondary, false, onSecondary)
            Spacer(Modifier.width(8.dp))
            MemoriseActionButton(primary, true, onPrimary)
        }
    }
}

@Composable
private fun MemoriseDetails(ayahAttempt: QuranMemorizationAttempt?, surahAttempt: QuranSurahMemorizationAttempt?, result: SpeechRecognitionResult?) {
    val attemptProvider = ayahAttempt?.providerName ?: surahAttempt?.providerName ?: result?.providerName
    val model = ayahAttempt?.modelName ?: surahAttempt?.modelName ?: result?.modelName
    val transcript = ayahAttempt?.transcript ?: surahAttempt?.transcript ?: result?.transcript
    val confidence = ayahAttempt?.confidence ?: result?.confidence
    val latency = ayahAttempt?.latencyMs ?: surahAttempt?.latencyMs ?: result?.latencyMs
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MemoriseDetailLine("Transcript", transcript?.takeIf { it.isNotBlank() } ?: "Not available")
        MemoriseDetailLine("Confidence", confidence?.let { "${(it * 100).toInt()}%" } ?: "Not available")
        MemoriseDetailLine("Provider", listOfNotNull(attemptProvider, model).joinToString(" · ").ifBlank { "Not available" })
        MemoriseDetailLine("Timing", latency?.let { "${it} ms processing" } ?: "Not available")
    }
}

@Composable
private fun MemoriseDetailLine(label: String, value: String) {
    val colors = VaultThemeTokens.colors
    Column { Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.W700); Text(value, color = colors.text, fontSize = 11.5.sp) }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
