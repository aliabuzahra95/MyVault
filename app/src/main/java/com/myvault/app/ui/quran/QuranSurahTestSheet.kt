package com.myvault.app.ui.quran

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myvault.app.BuildConfig
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.memorization.AyahMemorizationStatus
import com.myvault.app.data.quran.memorization.QuranMemorizationRecorder
import com.myvault.app.data.quran.memorization.QuranMemorizationRecording
import com.myvault.app.data.quran.memorization.QuranMemorizationWordState
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAnalysis
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttemptFactory
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahResult
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahWordResult
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationSavedAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationTestEngine
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationTestMode
import com.myvault.app.data.quran.speech.SpeechRecognitionProviderType
import com.myvault.app.data.quran.speech.SpeechRecognitionRequest
import com.myvault.app.data.quran.speech.SpeechRecognitionResult
import com.myvault.app.ui.components.buildQuranArabicText
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun QuranSurahTestSheet(
    visible: Boolean,
    surah: SurahInfo,
    ayahs: List<QuranAyah>,
    restoredAttempt: QuranSurahMemorizationSavedAttempt?,
    selectedProviderType: SpeechRecognitionProviderType,
    onSelectedProviderTypeChange: (SpeechRecognitionProviderType) -> Unit,
    onAttemptCompleted: (QuranSurahMemorizationAttempt) -> Unit,
    onOpenAyah: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recorder = remember { QuranMemorizationRecorder(context) }
    val speechProvider = remember(selectedProviderType) { selectedProviderType.createProvider() }
    val expectedWordCount = remember(ayahs) { ayahs.sumOf { it.words.size } }
    val isSupported = ayahs.isNotEmpty() && surah.ayat <= SURAH_TEST_MAX_AYAHS && expectedWordCount <= SURAH_TEST_MAX_WORDS
    var stage by remember(surah.num) { mutableStateOf(QuranAiListenStage.Ready) }
    var elapsedMs by remember(surah.num) { mutableStateOf(0L) }
    var recording by remember(surah.num) { mutableStateOf<QuranMemorizationRecording?>(null) }
    var transcriptionResult by remember(surah.num) { mutableStateOf<SpeechRecognitionResult?>(null) }
    var surahAnalysis by remember(surah.num) { mutableStateOf<QuranSurahMemorizationAnalysis?>(null) }
    var surahAttempt by remember(surah.num) { mutableStateOf<QuranSurahMemorizationAttempt?>(null) }
    var selectedAyahResult by remember(surah.num) {
        mutableStateOf<com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahResult?>(null)
    }
    var testMode by rememberSaveable(surah.num) { mutableStateOf(QuranSurahMemorizationTestMode.CONTINUE_REVISION.name) }
    val selectedTestMode = remember(testMode) {
        runCatching { QuranSurahMemorizationTestMode.valueOf(testMode) }
            .getOrDefault(QuranSurahMemorizationTestMode.CONTINUE_REVISION)
    }
    var isPlayingBack by remember(surah.num) { mutableStateOf(false) }
    var statusMessage by remember(surah.num) { mutableStateOf<String?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }

    fun startRecording() {
        if (!isSupported) return
        runCatching {
            recorder.stopPlayback()
            recorder.start(surah.num, 0)
            recording = null
            transcriptionResult = null
            surahAnalysis = null
            surahAttempt = null
            selectedAyahResult = null
            elapsedMs = 0L
            isPlayingBack = false
            statusMessage = null
            stage = QuranAiListenStage.Recording
        }.onFailure {
            statusMessage = "Recording could not start. Please check microphone access."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startAfterPermission = true
        } else {
            statusMessage = "Microphone permission is needed to record your recitation."
        }
    }

    fun requestStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun analyzeRecording(finishedRecording: QuranMemorizationRecording) {
        recorder.stopPlayback()
        isPlayingBack = false
        statusMessage = null
        transcriptionResult = null
        surahAnalysis = null
        surahAttempt = null
        selectedAyahResult = null
        stage = QuranAiListenStage.Transcribing
        coroutineScope.launch {
            val result = speechProvider.transcribe(
                SpeechRecognitionRequest(
                    audioFile = finishedRecording.file,
                    audioUri = finishedRecording.uri,
                    surahNumber = surah.num,
                    ayahNumber = 0,
                    verseKey = "${surah.num}:surah",
                    expectedText = ayahs.joinToString(separator = " ") { it.arabicText },
                    durationMs = finishedRecording.durationMs,
                    recordedAtMs = finishedRecording.createdAt,
                ),
            )
            if (BuildConfig.DEBUG && result.technicalErrorMessage != null) {
                Log.w("QuranSurahTest", result.technicalErrorMessage)
            }
            val analysis = QuranSurahMemorizationTestEngine.analyze(surah, ayahs, result, selectedTestMode)
            val attempt = QuranSurahMemorizationAttemptFactory.from(
                surah = surah,
                ayahs = ayahs,
                durationMs = finishedRecording.durationMs,
                speechResult = result,
                analysis = analysis,
                testMode = selectedTestMode,
            )
            onAttemptCompleted(attempt)
            recording = finishedRecording
            transcriptionResult = result
            surahAnalysis = analysis
            surahAttempt = attempt
            selectedAyahResult = null
            statusMessage = result.errorMessage
            stage = QuranAiListenStage.Finished
        }
    }

    LaunchedEffect(startAfterPermission) {
        if (startAfterPermission) {
            startAfterPermission = false
            startRecording()
        }
    }

    LaunchedEffect(stage) {
        while (stage == QuranAiListenStage.Recording) {
            elapsedMs = recorder.elapsedMs()
            delay(250L)
        }
    }

    DisposableEffect(surah.num) {
        onDispose { recorder.release() }
    }

    BackHandler(enabled = visible && selectedAyahResult != null) {
        selectedAyahResult = null
    }

    LaunchedEffect(visible, restoredAttempt?.attemptId) {
        if (visible && restoredAttempt != null) {
            recorder.stopPlayback()
            recording = null
            transcriptionResult = restoredAttempt.toRestoredSpeechResult()
            surahAnalysis = restoredAttempt.toRestoredAnalysis()
            surahAttempt = restoredAttempt.toRestoredAttempt()
            selectedAyahResult = null
            elapsedMs = restoredAttempt.durationMs
            isPlayingBack = false
            statusMessage = restoredAttempt.errorMessage
            stage = QuranAiListenStage.Finished
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            recorder.release()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (selectedTestMode == QuranSurahMemorizationTestMode.FULL_SURAH_TEST) {
                        "Surah ${surah.name} Test"
                    } else {
                        "Surah ${surah.name} Revision"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900, letterSpacing = 0.sp),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${surah.ayat} ayahs · $expectedWordCount words",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }

            if (!isSupported) {
                QuranAiListenMic(stage = QuranAiListenStage.Ready)
                Text(
                    text = "Full-surah testing for long surahs will be added later.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800, lineHeight = 22.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "For now, use Surah Test with shorter surahs, or practise this surah one ayah at a time.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            QuranAiListenMic(stage = stage)
            QuranSurahTestModeSelector(
                selectedMode = selectedTestMode,
                onSelectedModeChange = { testMode = it.name },
                enabled = stage == QuranAiListenStage.Ready || stage == QuranAiListenStage.Finished,
            )
            QuranSpeechProviderSelector(
                selectedProviderType = selectedProviderType,
                onSelectedProviderTypeChange = onSelectedProviderTypeChange,
                enabled = stage != QuranAiListenStage.Transcribing,
            )

            when (stage) {
                QuranAiListenStage.Ready -> {
                    Text(
                        text = selectedTestMode.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800, lineHeight = 22.sp),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    QuranAiListenActionButton(
                        label = if (selectedTestMode == QuranSurahMemorizationTestMode.FULL_SURAH_TEST) "Start Full Test" else "Start Revision",
                        selected = true,
                        onClick = ::requestStartRecording,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                QuranAiListenStage.Recording,
                QuranAiListenStage.Paused -> {
                    Text(
                        text = if (stage == QuranAiListenStage.Paused) "Paused" else "Recording...",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                        color = if (stage == QuranAiListenStage.Paused) colors.textSecondary else colors.accent,
                    )
                    Text(
                        text = formatQuranRecordingDuration(elapsedMs),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuranAiListenActionButton(
                            label = if (stage == QuranAiListenStage.Paused) "Resume" else "Pause",
                            selected = false,
                            onClick = {
                                if (stage == QuranAiListenStage.Paused) {
                                    recorder.resume()
                                    stage = QuranAiListenStage.Recording
                                } else {
                                    recorder.pause()
                                    elapsedMs = recorder.elapsedMs()
                                    stage = QuranAiListenStage.Paused
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuranAiListenActionButton(
                            label = "Stop",
                            selected = true,
                            onClick = {
                                elapsedMs = recorder.elapsedMs()
                                val completedRecording = recorder.stop(surah.num, 0)
                                recording = completedRecording
                                transcriptionResult = null
                                surahAnalysis = null
                                surahAttempt = null
                                if (completedRecording != null) {
                                    analyzeRecording(completedRecording)
                                } else {
                                    statusMessage = "Recording could not be saved. Please re-record the surah."
                                    stage = QuranAiListenStage.Finished
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                QuranAiListenStage.Finished -> {
                    val completedRecording = recording
                    Text(
                        text = "Recording complete",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.accent,
                    )
                    Text(
                        text = "Duration ${formatQuranRecordingDuration(completedRecording?.durationMs ?: elapsedMs)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (completedRecording != null) {
                            QuranAiListenActionButton(
                                label = if (isPlayingBack) "Playing" else "Playback",
                                selected = isPlayingBack,
                                onClick = {
                                    if (isPlayingBack) {
                                        recorder.stopPlayback()
                                        isPlayingBack = false
                                    } else {
                                        recorder.play(
                                            recording = completedRecording,
                                            onCompleted = { isPlayingBack = false },
                                            onError = {
                                                isPlayingBack = false
                                                statusMessage = "Playback failed."
                                            },
                                        )
                                        isPlayingBack = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        QuranAiListenActionButton(
                            label = if (completedRecording != null) "Re-record" else "Start new test",
                            selected = false,
                            onClick = {
                                recorder.stopPlayback()
                                transcriptionResult = null
                                surahAnalysis = null
                                surahAttempt = null
                                selectedAyahResult = null
                                requestStartRecording()
                            },
                            modifier = if (completedRecording != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        )
                    }
                    if (transcriptionResult?.isSuccess == false && completedRecording != null) {
                        QuranAiListenActionButton(
                            label = "Retry analysis",
                            selected = true,
                            onClick = { analyzeRecording(completedRecording) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val analysedWithSelectedProvider = transcriptionResult?.let {
                        it.providerName == speechProvider.providerName && it.modelName == speechProvider.modelName
                    } ?: true
                    if (completedRecording != null && !analysedWithSelectedProvider) {
                        QuranAiListenActionButton(
                            label = "Analyse with ${selectedProviderType.shortName}",
                            selected = true,
                            onClick = { analyzeRecording(completedRecording) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    QuranSurahTestResult(
                        attempt = surahAttempt,
                        analysis = surahAnalysis,
                        speechResult = transcriptionResult,
                        selectedAyahResult = selectedAyahResult,
                        onSelectAyahResult = { selectedAyahResult = it },
                        onBackToResults = { selectedAyahResult = null },
                        onOpenAyah = onOpenAyah,
                    )
                }

                QuranAiListenStage.Transcribing -> {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = "Transcribing surah...",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.accent,
                    )
                    Text(
                        text = "${speechProvider.providerName} · ${speechProvider.modelName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun QuranSurahMemorizationSavedAttempt.toRestoredSpeechResult(): SpeechRecognitionResult =
    SpeechRecognitionResult(
        transcript = transcript,
        normalizedTranscript = normalizedTranscript,
        providerName = providerName,
        modelName = modelName,
        confidence = null,
        wordTimestamps = emptyList(),
        latencyMs = latencyMs,
        errorMessage = errorMessage,
    )

private fun QuranSurahMemorizationSavedAttempt.toRestoredAttempt(): QuranSurahMemorizationAttempt =
    QuranSurahMemorizationAttempt(
        attemptId = attemptId,
        timestampMs = timestampMs,
        surahNumber = surahNumber,
        surahName = surahName,
        totalAyahs = totalAyahs,
        durationMs = durationMs,
        providerName = providerName,
        modelName = modelName,
        latencyMs = latencyMs,
        transcript = transcript,
        normalizedTranscript = normalizedTranscript,
        overallScore = overallScore,
        grade = grade,
        recognizedPercentage = recognizedPercentage,
        scoreCalculationVersion = scoreCalculationVersion,
        transcriptionSucceeded = transcriptionSucceeded,
        errorMessage = errorMessage,
        ayahResults = ayahResults,
        ayahsNeedingReview = ayahsNeedingReview,
        missingWordIds = missingWordIds,
        extraTranscriptWords = extraTranscriptWords,
        repeatedTranscriptWords = repeatedTranscriptWords,
        testMode = testMode,
    )

private fun QuranSurahMemorizationSavedAttempt.toRestoredAnalysis(): QuranSurahMemorizationAnalysis =
    QuranSurahMemorizationAnalysis(
        surahNumber = surahNumber,
        surahName = surahName,
        totalAyahs = totalAyahs,
        totalExpectedWords = ayahResults.sumOf { it.expectedWordCount },
        recognizedCount = ayahResults.sumOf { it.recognizedCount },
        missingCount = ayahResults.sumOf { it.missingCount },
        extraCount = ayahResults.sumOf { it.extraCount },
        repeatedCount = ayahResults.sumOf { it.repeatedCount },
        unknownCount = ayahResults.sumOf { it.unknownCount },
        confidence = null,
        overallScore = overallScore,
        grade = grade,
        recognizedPercentage = recognizedPercentage,
        scoreCalculationVersion = scoreCalculationVersion,
        ayahResults = ayahResults,
        ayahsNeedingReview = ayahsNeedingReview,
        missingWordIds = missingWordIds,
        extraTranscriptWords = extraTranscriptWords,
        repeatedTranscriptWords = repeatedTranscriptWords,
        testMode = testMode,
    )

@Composable
private fun QuranSurahTestResult(
    attempt: QuranSurahMemorizationAttempt?,
    analysis: QuranSurahMemorizationAnalysis?,
    speechResult: SpeechRecognitionResult?,
    selectedAyahResult: com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahResult?,
    onSelectAyahResult: (com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahResult) -> Unit,
    onBackToResults: () -> Unit,
    onOpenAyah: (Int) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    if (speechResult == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevated)
            .border(1.dp, colors.border.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
            color = colors.accent,
        )
        if (!speechResult.isSuccess || attempt == null || analysis == null) {
            Text(
                text = "Surah analysis could not be completed.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
        } else {
            Text(
                text = "Overall: ${attempt.grade.label}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900),
                color = colors.text,
            )
            Text(
                text = "Score: ${attempt.overallScore}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                color = colors.textSecondary,
            )
            QuranSurahTestMetrics(analysis)
            if (selectedAyahResult != null) {
                QuranSurahTestAyahDetail(
                    result = selectedAyahResult,
                    onBack = onBackToResults,
                    onOpenAyah = { onOpenAyah(selectedAyahResult.ayahNumber) },
                )
            } else {
                Text(
                    text = if (analysis.ayahsNeedingReview.isEmpty()) {
                        "Ayahs needing review: none"
                    } else {
                        "Ayahs needing review: ${analysis.ayahsNeedingReview.joinToString()}"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800, lineHeight = 19.sp),
                    color = if (analysis.ayahsNeedingReview.isEmpty()) colors.textSecondary else Color(0xFFFFA726),
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    analysis.ayahResults.forEach { result ->
                        QuranSurahTestAyahResultRow(
                            result = result,
                            onClick = { onSelectAyahResult(result) },
                        )
                    }
                }
            }
        }
        QuranAiListenSpeechTranscript(speechResult)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranReaderAyahReviewSheet(
    result: QuranSurahMemorizationAyahResult?,
    onDismiss: () -> Unit,
) {
    if (result == null) return
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.borderStrong.copy(alpha = 0.45f)),
            )
            Text(
                text = "Ayah ${result.ayahNumber} Review",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            QuranSurahTestAyahDetail(
                result = result,
                onBack = onDismiss,
                onOpenAyah = null,
                backLabel = "Close",
            )
        }
    }
}

@Composable
private fun QuranSurahTestAyahDetail(
    result: QuranSurahMemorizationAyahResult,
    onBack: () -> Unit,
    onOpenAyah: (() -> Unit)?,
    backLabel: String = "Back to results",
    openLabel: String = "Open in reader",
) {
    val colors = VaultThemeTokens.colors
    val passed = result.status == AyahMemorizationStatus.PASSED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.74f))
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Ayah ${result.ayahNumber}",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W900),
            color = colors.text,
        )
        Text(
            text = if (passed) "Passed" else "Needs Review",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
            color = if (passed) Color(0xFF31D07F) else Color(0xFFFFA726),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Score",
                value = "${result.overallScore}%",
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Recognised",
                value = "${result.recognizedCount} / ${result.expectedWordCount}",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Missing",
                value = result.missingCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.MISSING),
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Extra",
                value = result.extraCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.EXTRA),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Repeated",
                value = result.repeatedCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.REPEATED),
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Unknown",
                value = result.unknownCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.UNKNOWN),
                modifier = Modifier.weight(1f),
            )
        }
        if (result.wordResults.isNotEmpty()) {
            Text(
                text = "Official Qur'an text",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                color = colors.textSecondary,
            )
            QuranSurahTestOfficialWordAnalysis(result.wordResults)
        }
        if (result.missingWordIds.isNotEmpty()) {
            Text(
                text = "Missing word IDs: ${result.missingWordIds.joinToString()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700, lineHeight = 18.sp),
                color = colors.textSecondary,
            )
        }
        if (result.extraTranscriptWords.isNotEmpty()) {
            Text(
                text = "Extra heard: ${result.extraTranscriptWords.joinToString()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800, lineHeight = 18.sp),
                color = quranAnalysisStateColor(QuranMemorizationWordState.EXTRA),
            )
        }
        if (result.repeatedTranscriptWords.isNotEmpty()) {
            Text(
                text = "Repeated heard: ${result.repeatedTranscriptWords.joinToString()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800, lineHeight = 18.sp),
                color = quranAnalysisStateColor(QuranMemorizationWordState.REPEATED),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenActionButton(
                label = backLabel,
                selected = false,
                onClick = onBack,
                modifier = if (onOpenAyah == null) Modifier.fillMaxWidth() else Modifier.weight(1f),
            )
            if (onOpenAyah != null) {
                QuranAiListenActionButton(
                    label = openLabel,
                    selected = true,
                    onClick = onOpenAyah,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuranSurahTestOfficialWordAnalysis(
    words: List<com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahWordResult>,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { word ->
                QuranSurahTestAnalysedWord(word)
            }
        }
    }
}

@Composable
private fun QuranSurahTestAnalysedWord(
    word: com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahWordResult,
) {
    val colors = VaultThemeTokens.colors
    val renderedWord = remember(word.wordId, word.displayedWord, colors) {
        buildQuranArabicText(
            text = word.displayedWord,
            annotations = emptyList(),
            tajweedEnabled = false,
            isDark = colors == DarkVaultColors,
        )
    }
    Text(
        text = renderedWord,
        style = quranArabicTextStyle(25.sp),
        color = quranAnalysisStateColor(word.state),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 1.dp),
    )
}

@Composable
private fun QuranSurahTestMetrics(analysis: QuranSurahMemorizationAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Recognised",
                value = "${analysis.recognizedCount} / ${analysis.totalExpectedWords} words",
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Missing",
                value = analysis.missingCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.MISSING),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Extra",
                value = analysis.extraCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.EXTRA),
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Repeated",
                value = analysis.repeatedCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.REPEATED),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuranSurahTestAyahResultRow(
    result: com.myvault.app.data.quran.memorization.QuranSurahMemorizationAyahResult,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val passed = result.status == AyahMemorizationStatus.PASSED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Ayah ${result.ayahNumber}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W900),
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (passed) "Passed" else "Needs Review",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
            color = if (passed) Color(0xFF31D07F) else Color(0xFFFFA726),
        )
    }
}
