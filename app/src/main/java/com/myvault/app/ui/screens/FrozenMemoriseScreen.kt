@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.memorization.AyahMemorizationStatus
import com.myvault.app.data.quran.memorization.MemorizationRecord
import com.myvault.app.data.quran.memorization.MemorizationUiState
import com.myvault.app.data.quran.memorization.QuranMemorizationSavedAttempt
import com.myvault.app.data.quran.memorization.QuranSurahMemorizationSavedAttempt
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.MemoriseSessionUiState
import com.myvault.app.ui.viewmodel.MemoriseStatusChoice
import java.text.DateFormat
import java.util.Date

private enum class MemoriseDestination { Overview, Attempts, AttemptDetail }

private sealed interface MemoriseAttemptEntry {
    val id: String
    val timestampMs: Long
    val score: Int?
    val title: String
    val summary: String

    data class Ayah(val attempt: QuranMemorizationSavedAttempt) : MemoriseAttemptEntry {
        override val id = attempt.attemptId
        override val timestampMs = attempt.timestampMs
        override val score = attempt.overallScore.takeIf { attempt.transcriptionSucceeded }
        override val title = "${surahName(attempt.surahNumber)} · ${attempt.verseKey}"
        override val summary = attemptSummary(
            attempt.recognizedCount,
            attempt.missingCount,
            attempt.extraCount,
            attempt.repeatedCount,
            attempt.unknownCount,
            attempt.errorMessage,
        )
    }

    data class Surah(val attempt: QuranSurahMemorizationSavedAttempt) : MemoriseAttemptEntry {
        override val id = attempt.attemptId
        override val timestampMs = attempt.timestampMs
        override val score = attempt.overallScore.takeIf { attempt.transcriptionSucceeded }
        override val title = "${attempt.surahName} · Whole Surah"
        override val summary = if (attempt.transcriptionSucceeded) {
            "${attempt.ayahResults.count { it.status == AyahMemorizationStatus.PASSED }} passed · ${attempt.ayahsNeedingReview.size} need attention"
        } else {
            attempt.errorMessage ?: "Attempt could not be analysed"
        }
    }
}

@Composable
fun FrozenMemoriseScreen(
    uiState: MemorizationUiState,
    sessionState: MemoriseSessionUiState,
    onOpenNavigation: () -> Unit,
    onOpenSession: (String, Boolean) -> Unit,
    onOpenSurah: (Int, Int?) -> Unit,
    onOpenWholeSurah: (Int) -> Unit,
    onCloseSession: () -> Unit,
    onNextAyah: () -> Boolean,
    onConsumeAutoRecord: () -> Unit,
    onSetStatus: (String, MemoriseStatusChoice) -> Unit,
    onRecordAttempt: (com.myvault.app.data.quran.memorization.QuranMemorizationAttempt) -> Unit,
    onRecordSurahAttempt: (com.myvault.app.data.quran.memorization.QuranSurahMemorizationAttempt) -> Unit,
    onSurahPositionChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destinationName by rememberSaveable { mutableStateOf(MemoriseDestination.Overview.name) }
    var selectedAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = runCatching { MemoriseDestination.valueOf(destinationName) }.getOrDefault(MemoriseDestination.Overview)
    val attempts = remember(uiState.attempts, uiState.surahAttempts) {
        (uiState.attempts.map { MemoriseAttemptEntry.Ayah(it) } + uiState.surahAttempts.map { MemoriseAttemptEntry.Surah(it) })
            .sortedByDescending { it.timestampMs }
    }

    BackHandler(enabled = sessionState.active || destination != MemoriseDestination.Overview) {
        when {
            sessionState.active -> onCloseSession()
            destination == MemoriseDestination.AttemptDetail -> destinationName = MemoriseDestination.Attempts.name
            else -> destinationName = MemoriseDestination.Overview.name
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            sessionState.active -> FrozenMemoriseSession(
                sessionState = sessionState,
                onBack = onCloseSession,
                onNextAyah = onNextAyah,
                onConsumeAutoRecord = onConsumeAutoRecord,
                onSetStatus = onSetStatus,
                onAttemptCompleted = onRecordAttempt,
                onSurahAttemptCompleted = onRecordSurahAttempt,
                onSurahPositionChanged = onSurahPositionChanged,
            )
            destination == MemoriseDestination.Attempts -> MemoriseAttemptHistoryScreen(
                attempts = attempts,
                onBack = { destinationName = MemoriseDestination.Overview.name },
                onOpen = {
                    selectedAttemptId = it.id
                    destinationName = MemoriseDestination.AttemptDetail.name
                },
            )
            destination == MemoriseDestination.AttemptDetail -> MemoriseAttemptDetailScreen(
                attempt = attempts.firstOrNull { it.id == selectedAttemptId },
                onBack = { destinationName = MemoriseDestination.Attempts.name },
            )
            else -> MemoriseOverviewScreen(
                uiState = uiState,
                onOpenNavigation = onOpenNavigation,
                onOpenSession = { onOpenSession(it, false) },
                onOpenSurah = onOpenSurah,
                onOpenAttempts = { destinationName = MemoriseDestination.Attempts.name },
                onWholeSurah = onOpenWholeSurah,
                onSetStatus = onSetStatus,
            )
        }
    }
}

@Composable
private fun MemoriseOverviewScreen(
    uiState: MemorizationUiState,
    onOpenNavigation: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSurah: (Int, Int?) -> Unit,
    onOpenAttempts: () -> Unit,
    onWholeSurah: (Int) -> Unit,
    onSetStatus: (String, MemoriseStatusChoice) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var statusTarget by remember { mutableStateOf<String?>(null) }
    val continueItem = uiState.continueItem
    val statusTargets = remember(uiState.records, continueItem) {
        mapOf(
            MemoriseStatusChoice.Memorised to uiState.records.firstOrNull { it.isMemorized }?.verseKey,
            MemoriseStatusChoice.InProgress to (continueItem?.record?.verseKey ?: uiState.records.firstOrNull { it.isMemorising }?.verseKey),
            MemoriseStatusChoice.Revision to uiState.records.firstOrNull { it.isRevision || it.isNeedsRevision }?.verseKey,
            MemoriseStatusChoice.Incorrect to uiState.records.firstOrNull { it.isIncorrect }?.verseKey,
            MemoriseStatusChoice.Difficult to uiState.records.firstOrNull { it.isWeak }?.verseKey,
        )
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        MemoriseOverviewHeader(
            metadata = "${uiState.overview.memorizedCount} memorised · ${uiState.overview.startedCount} in progress",
            onOpenNavigation = onOpenNavigation,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            continueItem?.let { item ->
                item {
                    MemoriseContinueCard(
                        title = item.surah.name,
                        subtitle = "${item.record.verseKey} · ${statusLabel(item.record)}",
                        onClick = { onOpenSurah(item.surah.num, item.record.ayahNumber) },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    MemoriseSectionLabel("Status")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val stats = listOf(
                            Triple(uiState.overview.memorizedCount, "Memorised", MemoriseStatusChoice.Memorised),
                            Triple(uiState.overview.startedCount, "In progress", MemoriseStatusChoice.InProgress),
                            Triple(uiState.overview.revisionCount, "Revision", MemoriseStatusChoice.Revision),
                            Triple(uiState.overview.incorrectCount, "Incorrect", MemoriseStatusChoice.Incorrect),
                            Triple(uiState.overview.difficultCount, "Difficult", MemoriseStatusChoice.Difficult),
                        )
                        stats.forEachIndexed { index, (value, label, status) ->
                            MemoriseStat("$value", label, { statusTargets[status]?.let(onOpenSession) }, Modifier.weight(1f))
                            if (index < stats.lastIndex) Box(Modifier.width(1.dp).height(34.dp).background(colors.border))
                        }
                    }
                    val target = continueItem?.record?.verseKey ?: uiState.records.firstOrNull()?.verseKey
                    if (target != null) {
                        MemoriseTargetRow(
                            title = "${surahName(target.substringBefore(':').toIntOrNull() ?: 1)} · $target",
                            subtitle = "${uiState.records.firstOrNull { it.verseKey == target }?.let(::statusLabel) ?: "In progress"} · tap to set status",
                            onClick = { statusTarget = target },
                        )
                    }
                }
            }
            if (uiState.inProgressSurahs.isNotEmpty() || uiState.memorizedSurahs.isNotEmpty()) {
                item { MemoriseSectionLabel("Surahs") }
                items(uiState.inProgressSurahs, key = { "progress-${it.surah.num}" }) { progress ->
                    MemoriseSurahProgressRowFrozen(
                        title = progress.surah.name,
                        meta = "${progress.memorizedCount} of ${progress.totalAyahs} ayat",
                        status = when {
                            progress.incorrectCount > 0 -> "Incorrect"
                            progress.difficultCount > 0 -> "Difficult"
                            progress.needsRevisionCount > 0 -> "Revision"
                            else -> "In progress"
                        },
                        progress = progress.memorizedCount.toFloat() / progress.totalAyahs.coerceAtLeast(1),
                        onClick = { onOpenSurah(progress.surah.num, null) },
                        onWholeSurah = { onWholeSurah(progress.surah.num) },
                    )
                }
                items(uiState.memorizedSurahs, key = { "complete-${it.surah.num}" }) { progress ->
                    MemoriseSurahProgressRowFrozen(
                        title = progress.surah.name,
                        meta = "${progress.memorizedCount} of ${progress.surah.ayat} ayat",
                        status = "Memorised",
                        progress = 1f,
                        onClick = { onOpenSurah(progress.surah.num, null) },
                        onWholeSurah = { onWholeSurah(progress.surah.num) },
                    )
                }
            }
            item {
                Column {
                    MemoriseSecondaryRow(Icons.Outlined.History, "Attempts", "Stored recitation results", onOpenAttempts)
                    HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
                    val surah = uiState.inProgressSurahs.firstOrNull()?.surah ?: uiState.selectedSurah
                    MemoriseSecondaryRow(Icons.Outlined.MenuBook, "Recite Surah", surah.name) { onWholeSurah(surah.num) }
                }
            }
        }
    }

    statusTarget?.let { target ->
        MemoriseStatusSheet(
            target = target,
            current = currentStatus(uiState.records.firstOrNull { it.verseKey == target }),
            onDismiss = { statusTarget = null },
            onSelect = {
                onSetStatus(target, it)
                statusTarget = null
            },
        )
    }
}

@Composable
private fun MemoriseOverviewHeader(metadata: String, onOpenNavigation: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenNavigation, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.Menu, "Open navigation", tint = colors.textSecondary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Memorise", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.W800)
            Text(metadata, color = colors.textMuted, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun MemoriseContinueCard(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoriseSectionLabel("CONTINUE")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.W800)
                    Text(subtitle, color = colors.textSecondary, fontSize = 10.5.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
            Text("Continue session", color = colors.accent, fontSize = 10.5.sp, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun MemoriseStat(value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.W800)
        Text(label, color = colors.textMuted, fontSize = 8.5.sp, maxLines = 1)
    }
}

@Composable
private fun MemoriseTargetRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Description, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.W700)
            Text(subtitle, color = colors.textMuted, fontSize = 9.5.sp)
        }
        Text("···", color = colors.textMuted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemoriseSurahProgressRowFrozen(
    title: String,
    meta: String,
    status: String,
    progress: Float,
    onClick: () -> Unit,
    onWholeSurah: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.MenuBook, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.W700)
            Text(meta, color = colors.textMuted, fontSize = 9.5.sp)
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(colors.accent, RoundedCornerShape(2.dp)))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(status, color = colors.textSecondary, fontSize = 9.5.sp)
            IconButton(onClick = onWholeSurah, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Mic, "Recite $title continuously", tint = colors.accent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun MemoriseSecondaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.W700)
            Text(subtitle, color = colors.textMuted, fontSize = 9.5.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MemoriseStatusSheet(
    target: String,
    current: MemoriseStatusChoice,
    onDismiss: () -> Unit,
    onSelect: (MemoriseStatusChoice) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        dragHandle = { Box(Modifier.padding(vertical = 8.dp).size(36.dp, 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${surahName(target.substringBefore(':').toIntOrNull() ?: 1)} · $target", color = colors.textMuted, fontSize = 9.5.sp)
                    Text("Set status", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.W800)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary) }
            }
            MemoriseStatusChoice.entries.forEach { status ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(status) }.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.label, Modifier.weight(1f), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.W600)
                    if (status == current) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MemoriseAttemptHistoryScreen(attempts: List<MemoriseAttemptEntry>, onBack: () -> Unit, onOpen: (MemoriseAttemptEntry) -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        MemorisePageHeader("Attempts", "Latest recitations", onBack)
        if (attempts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.History, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                    Text("No attempts yet", color = colors.text, fontWeight = FontWeight.W700, modifier = Modifier.padding(top = 8.dp))
                    Text("Completed recitation results will appear here.", color = colors.textMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                item { Text("Recent recitation attempts. Recordings remain on this device only.", color = colors.textMuted, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                items(attempts, key = { it.id }) { attempt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(attempt) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.History, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(attempt.title, color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.W700)
                            Text(formatAttemptDate(attempt.timestampMs), color = colors.textMuted, fontSize = 9.5.sp)
                            Text(attempt.summary, color = colors.textSecondary, fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        attempt.score?.let { Text("$it%", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.W800) }
                    }
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun MemoriseAttemptDetailScreen(attempt: MemoriseAttemptEntry?, onBack: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        MemorisePageHeader("Attempt detail", "Recitation result", onBack)
        if (attempt == null) return@Column
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(attempt.score?.let { "$it%" } ?: "—", color = colors.accent, fontSize = 32.sp, fontWeight = FontWeight.W800)
                Text(if (attempt.score == null) "No score" else attemptGradeLabel(attempt), color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            MemoriseDetailRow("Target", attempt.title)
            MemoriseDetailRow("Recorded", formatAttemptDate(attempt.timestampMs))
            MemoriseDetailRow("Result", attempt.summary)
            when (attempt) {
                is MemoriseAttemptEntry.Ayah -> {
                    MemoriseDetailRow("Provider", "${attempt.attempt.providerName} · ${attempt.attempt.modelName}")
                    if (attempt.attempt.transcript.isNotBlank()) MemoriseDetailRow("Transcript", attempt.attempt.transcript)
                    attempt.attempt.confidence?.let { MemoriseDetailRow("Confidence", "${(it * 100).toInt()}%") }
                }
                is MemoriseAttemptEntry.Surah -> {
                    MemoriseDetailRow("Provider", "${attempt.attempt.providerName} · ${attempt.attempt.modelName}")
                    MemoriseDetailRow("Mode", attempt.attempt.testMode.label)
                }
            }
        }
    }
}

@Composable
private fun MemorisePageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.textSecondary) }
        Column { Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.W800); Text(subtitle, color = colors.textMuted, fontSize = 10.5.sp) }
    }
}

@Composable
private fun MemoriseDetailRow(label: String, value: String) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.W700)
        Text(value, color = colors.text, fontSize = 12.sp)
    }
}

@Composable
private fun MemoriseSectionLabel(text: String) = Text(text, color = VaultThemeTokens.colors.text, fontSize = 9.sp, fontWeight = FontWeight.W700)

private fun currentStatus(record: MemorizationRecord?): MemoriseStatusChoice = when {
    record?.isMemorized == true -> MemoriseStatusChoice.Memorised
    record?.isIncorrect == true -> MemoriseStatusChoice.Incorrect
    record?.isWeak == true -> MemoriseStatusChoice.Difficult
    record?.isRevision == true || record?.isNeedsRevision == true -> MemoriseStatusChoice.Revision
    else -> MemoriseStatusChoice.InProgress
}

private fun statusLabel(record: MemorizationRecord): String = currentStatus(record).label

private fun surahName(number: Int): String = com.myvault.app.data.quran.quranCatalog.firstOrNull { it.num == number }?.name ?: "Surah $number"

private fun attemptSummary(correct: Int, missing: Int, extra: Int, repeated: Int, unknown: Int, error: String?): String =
    error ?: buildList {
        add("Correct $correct")
        if (missing > 0) add("Missing $missing")
        if (extra > 0) add("Extra $extra")
        if (repeated > 0) add("Repeated $repeated")
        if (unknown > 0) add("Unclear $unknown")
    }.joinToString(" · ")

private fun formatAttemptDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun attemptGradeLabel(attempt: MemoriseAttemptEntry): String = when (attempt) {
    is MemoriseAttemptEntry.Ayah -> attempt.attempt.grade.label
    is MemoriseAttemptEntry.Surah -> attempt.attempt.grade.label
}
