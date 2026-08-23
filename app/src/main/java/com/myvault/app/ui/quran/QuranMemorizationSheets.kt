package com.myvault.app.ui.quran

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.myvault.app.data.quran.memorization.QuranMemorizationAnalysis
import com.myvault.app.data.quran.memorization.QuranMemorizationAnalysisEngine
import com.myvault.app.data.quran.memorization.QuranMemorizationAttempt
import com.myvault.app.data.quran.memorization.QuranMemorizationAttemptFactory
import com.myvault.app.data.quran.memorization.QuranMemorizationAttemptHistory
import com.myvault.app.data.quran.memorization.QuranMemorizationRecorder
import com.myvault.app.data.quran.memorization.QuranMemorizationRecording
import com.myvault.app.data.quran.memorization.QuranMemorizationWordAnalysis
import com.myvault.app.data.quran.memorization.QuranMemorizationWordState
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
internal fun QuranAiListenSheet(
    ayah: QuranAyah?,
    surahName: String,
    selectedProviderType: SpeechRecognitionProviderType,
    onSelectedProviderTypeChange: (SpeechRecognitionProviderType) -> Unit,
    onAttemptCompleted: (QuranMemorizationAttempt) -> Unit,
    onDismiss: () -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recorder = remember { QuranMemorizationRecorder(context) }
    val speechProvider = remember(selectedProviderType) { selectedProviderType.createProvider() }
    var stage by remember(ayah.verseKey) { mutableStateOf(QuranAiListenStage.Ready) }
    var elapsedMs by remember(ayah.verseKey) { mutableStateOf(0L) }
    var recording by remember(ayah.verseKey) { mutableStateOf<QuranMemorizationRecording?>(null) }
    var transcriptionResult by remember(ayah.verseKey) { mutableStateOf<SpeechRecognitionResult?>(null) }
    var memorizationAnalysis by remember(ayah.verseKey) { mutableStateOf<QuranMemorizationAnalysis?>(null) }
    var memorizationAttempt by remember(ayah.verseKey) { mutableStateOf<QuranMemorizationAttempt?>(null) }
    var isPlayingBack by remember(ayah.verseKey) { mutableStateOf(false) }
    var statusMessage by remember(ayah.verseKey) { mutableStateOf<String?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var autoStartRequested by remember(ayah.verseKey) { mutableStateOf(false) }

    fun startRecording() {
        runCatching {
            recorder.stopPlayback()
            recorder.start(ayah.surahNumber, ayah.ayahNumber)
            recording = null
            transcriptionResult = null
            memorizationAnalysis = null
            memorizationAttempt = null
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

    fun analyzeRecording(finishedRecording: QuranMemorizationRecording) {
        recorder.stopPlayback()
        isPlayingBack = false
        statusMessage = null
        transcriptionResult = null
        memorizationAnalysis = null
        memorizationAttempt = null
        stage = QuranAiListenStage.Transcribing
        coroutineScope.launch {
            val result = speechProvider.transcribe(
                SpeechRecognitionRequest(
                    audioFile = finishedRecording.file,
                    audioUri = finishedRecording.uri,
                    surahNumber = finishedRecording.surahNumber,
                    ayahNumber = finishedRecording.ayahNumber,
                    verseKey = finishedRecording.verseKey,
                    expectedText = ayah.arabicText,
                    durationMs = finishedRecording.durationMs,
                    recordedAtMs = finishedRecording.createdAt,
                ),
            )
            if (BuildConfig.DEBUG && result.technicalErrorMessage != null) {
                Log.w("QuranSpeech", result.technicalErrorMessage)
            }
            val analysis = if (result.isSuccess) {
                QuranMemorizationAnalysisEngine.analyze(ayah, result)
            } else {
                null
            }
            val attempt = QuranMemorizationAttemptFactory.from(
                ayah = ayah,
                durationMs = finishedRecording.durationMs,
                speechResult = result,
                analysis = analysis,
            )
            QuranMemorizationAttemptHistory.record(attempt)
            onAttemptCompleted(attempt)
            recording = finishedRecording
            transcriptionResult = result
            memorizationAnalysis = analysis
            memorizationAttempt = attempt
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

    DisposableEffect(ayah.verseKey) {
        onDispose { recorder.release() }
    }

    fun requestStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(ayah.verseKey) {
        if (!autoStartRequested) {
            autoStartRequested = true
            requestStartRecording()
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
                    text = "AI Listen",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900, letterSpacing = 0.sp),
                    color = colors.text,
                )
                Text(
                    text = "Surah $surahName — Ayah ${ayah.ayahNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                    color = colors.textSecondary,
                )
            }

            QuranAiListenMic(stage = stage)
            QuranSpeechProviderSelector(
                selectedProviderType = selectedProviderType,
                onSelectedProviderTypeChange = onSelectedProviderTypeChange,
                enabled = stage != QuranAiListenStage.Transcribing,
            )

            when (stage) {
                QuranAiListenStage.Ready -> {
                    Text(
                        text = "Preparing recorder...",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
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
                                val completedRecording = recorder.stop(ayah.surahNumber, ayah.ayahNumber)
                                recording = completedRecording
                                transcriptionResult = null
                                memorizationAnalysis = null
                                memorizationAttempt = null
                                if (completedRecording != null) {
                                    analyzeRecording(completedRecording)
                                } else {
                                    statusMessage = "Recording could not be saved. Please re-record the ayah."
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
                        QuranAiListenActionButton(
                            label = if (isPlayingBack) "Playing" else "Playback",
                            selected = isPlayingBack,
                            onClick = {
                                if (isPlayingBack) {
                                    recorder.stopPlayback()
                                    isPlayingBack = false
                                } else if (completedRecording != null) {
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
                        QuranAiListenActionButton(
                            label = "Re-record",
                            selected = false,
                            onClick = {
                                recorder.stopPlayback()
                                transcriptionResult = null
                                memorizationAnalysis = null
                                memorizationAttempt = null
                                requestStartRecording()
                            },
                            modifier = Modifier.weight(1f),
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
                    transcriptionResult?.let { result ->
                        QuranAiListenAnalysisResult(
                            result = result,
                            analysis = memorizationAnalysis,
                            attempt = memorizationAttempt,
                        )
                    }
                }

                QuranAiListenStage.Transcribing -> {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = "Transcribing...",
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuranAiListenAnalysisResult(
    result: SpeechRecognitionResult,
    analysis: QuranMemorizationAnalysis?,
    attempt: QuranMemorizationAttempt?,
) {
    val colors = VaultThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.elevated)
            .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (analysis != null) "Analysis" else "Transcription issue",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W900),
            color = if (analysis != null) colors.accent else colors.textSecondary,
        )

        if (analysis != null) {
            QuranAiListenAnalysisStats(analysis)
            Text(
                text = "Official Qur'an text",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                color = colors.textSecondary,
            )
            QuranAiListenOfficialWordAnalysis(analysis)
        } else {
            Text(
                text = result.errorMessage ?: "Google Speech could not return a usable transcript.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700, lineHeight = 18.sp),
                color = colors.textSecondary,
            )
        }

        if (BuildConfig.DEBUG && attempt != null) {
            QuranAiListenAttemptDiagnostics(attempt)
        }

        HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
        QuranAiListenSpeechTranscript(result)
    }
}

@Composable
private fun QuranAiListenAttemptDiagnostics(attempt: QuranMemorizationAttempt) {
    val colors = VaultThemeTokens.colors
    var expanded by remember(attempt.attemptId) { mutableStateOf(false) }
    val recentAttemptCount = remember(attempt.attemptId) {
        QuranMemorizationAttemptHistory.recent().size
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface.copy(alpha = 0.55f))
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                color = colors.textSecondary,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer {
                    Text(
                        text = buildAttemptDiagnosticsText(attempt, recentAttemptCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700, lineHeight = 18.sp),
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

private fun buildAttemptDiagnosticsText(
    attempt: QuranMemorizationAttempt,
    recentAttemptCount: Int,
): String = buildString {
    appendLine("attempt: ${attempt.attemptId}")
    appendLine("recent attempts kept: $recentAttemptCount")
    appendLine("provider: ${attempt.providerName} / ${attempt.modelName}")
    appendLine("latency: ${formatSpeechLatency(attempt.latencyMs)}")
    appendLine("duration: ${formatQuranRecordingDuration(attempt.durationMs)}")
    appendLine("transcription succeeded: ${attempt.transcriptionSucceeded}")
    appendLine("perfect match: ${attempt.perfectMatch}")
    attempt.errorMessage?.let { appendLine("error: $it") }
    appendLine("counts: recognised=${attempt.recognizedCount}, missing=${attempt.missingCount}, extra=${attempt.extraCount}, repeated=${attempt.repeatedCount}, unknown=${attempt.unknownCount}")
    appendLine("confidence: ${attempt.confidence?.let(::formatSpeechConfidence) ?: "unknown"}")
    appendLine("expected word IDs: ${attempt.expectedWordIds.joinToString()}")
    appendLine("matched word IDs: ${attempt.matchedWordIds.joinToString().ifBlank { "none" }}")
    appendLine("missing word IDs: ${attempt.missingWordIds.joinToString().ifBlank { "none" }}")
    appendLine("extra transcript words: ${attempt.extraTranscriptWords.joinToString().ifBlank { "none" }}")
    appendLine("repeated transcript words: ${attempt.repeatedTranscriptWords.joinToString().ifBlank { "none" }}")
    appendLine()
    appendLine("Expected comparison keys:")
    attempt.expectedComparisonKeys.forEach {
        appendLine("${it.wordId}: ${it.displayedWord} | normalized=${it.normalizedDisplayForm} | key=${it.comparisonKey}")
    }
    appendLine()
    appendLine("Transcript comparison keys:")
    if (attempt.transcriptComparisonKeys.isEmpty()) {
        appendLine("none")
    } else {
        attempt.transcriptComparisonKeys.forEach {
            appendLine("${it.transcriptIndex}: ${it.originalWord} | normalized=${it.normalizedForm} | key=${it.comparisonKey}")
        }
    }
    appendLine()
    appendLine("Failed words:")
    if (attempt.diagnostics.isEmpty()) {
        appendLine("none")
    } else {
        attempt.diagnostics.forEach { diagnostic ->
            appendLine("${diagnostic.category}: wordId=${diagnostic.expectedWordId ?: "none"} quran=${diagnostic.displayedQuranWord ?: "none"} expectedKey=${diagnostic.expectedComparisonKey ?: "none"} transcript=${diagnostic.transcriptWord ?: "none"} normalizedTranscript=${diagnostic.normalizedTranscriptWord ?: "none"} transcriptKey=${diagnostic.transcriptComparisonKey ?: "none"} reason=${diagnostic.reason}")
        }
    }
    appendLine()
    appendLine("Alignment path:")
    if (attempt.alignmentPath.isEmpty()) {
        appendLine("none")
    } else {
        attempt.alignmentPath.forEachIndexed { index, step ->
            appendLine(
                "$index: ${step.action} ayah=${step.expectedAyahNumber ?: "none"} " +
                    "wordId=${step.expectedWordId ?: "none"} quran=${step.expectedDisplayedWord ?: "none"} " +
                    "expectedKeys=${step.expectedComparisonKeys.joinToString("|").ifBlank { "none" }} " +
                    "transcriptIndex=${step.transcriptIndex ?: "none"} transcript=${step.transcriptWord ?: "none"} " +
                    "transcriptKey=${step.transcriptComparisonKey ?: "none"} " +
                    "boundaryTieBreak=${step.ayahBoundaryTieBreakUsed} " +
                    "guardedSimilarity=${step.matchedByGuardedSimilarity} reason=${step.reason}",
            )
        }
    }
    appendLine()
    appendLine("Transcript:")
    append(attempt.transcript.ifBlank { "none" })
}

@Composable
private fun QuranAiListenAnalysisStats(analysis: QuranMemorizationAnalysis) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Recognised",
                value = "${analysis.recognizedWordCount} / ${analysis.expectedWordCount} words",
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Missing",
                value = analysis.missingWordCount.toString(),
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
                value = analysis.extraWordCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.EXTRA),
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Repeated",
                value = analysis.repeatedWordCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.REPEATED),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuranAiListenAnalysisMetric(
                label = "Confidence",
                value = analysis.confidence?.let(::formatSpeechConfidence) ?: "Unknown",
                modifier = Modifier.weight(1f),
            )
            QuranAiListenAnalysisMetric(
                label = "Unknown",
                value = analysis.unknownWordCount.toString(),
                valueColor = quranAnalysisStateColor(QuranMemorizationWordState.UNKNOWN),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuranAiListenOfficialWordAnalysis(analysis: QuranMemorizationAnalysis) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            analysis.expectedWords.forEach { wordAnalysis ->
                QuranAiListenAnalysedWord(wordAnalysis)
            }
        }
    }
}

@Composable
private fun QuranAiListenAnalysedWord(wordAnalysis: QuranMemorizationWordAnalysis) {
    val colors = VaultThemeTokens.colors
    val renderedWord = remember(wordAnalysis.word.wordId, wordAnalysis.word.arabicText, colors) {
        buildQuranArabicText(
            text = wordAnalysis.word.arabicText,
            annotations = emptyList(),
            tajweedEnabled = false,
            isDark = colors == DarkVaultColors,
        )
    }
    Text(
        text = renderedWord,
        style = quranArabicTextStyle(25.sp),
        color = quranAnalysisStateColor(wordAnalysis.state),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 1.dp),
    )
}
