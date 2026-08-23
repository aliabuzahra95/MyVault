package com.myvault.app.data.quran.memorization

import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog

data class MemorizationRecord(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val startedAt: Long,
    val lastReviewedAt: Long,
    val reviewCount: Int,
    val memorizedAt: Long?,
    val isRevision: Boolean,
    val isWeak: Boolean,
    val updatedAt: Long,
    val isNeedsRevision: Boolean = false,
    val isIncorrect: Boolean = false,
    val isMemorising: Boolean = true,
) {
    val isMemorized: Boolean
        get() = memorizedAt != null
}

data class MemorizationOverview(
    val startedCount: Int = 0,
    val memorizedCount: Int = 0,
    val revisionCount: Int = 0,
    val needsReviewCount: Int = 0,
    val incorrectCount: Int = 0,
    val difficultCount: Int = 0,
    val memorizedSurahCount: Int = 0,
    val inProgressSurahCount: Int = 0,
)

enum class AyahMemorizationStatus {
    NOT_ATTEMPTED,
    ATTEMPTED,
    PASSED,
    NEEDS_REVIEW,
    INCORRECT,
    DIFFICULT,
    UNKNOWN,
}

data class AyahMemorizationStatusSnapshot(
    val verseKey: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val status: AyahMemorizationStatus,
    val lastAttemptAtMs: Long? = null,
    val lastAttemptId: String? = null,
    val correctWordCount: Int? = null,
    val expectedWordCount: Int? = null,
)

data class MemorizationDashboardItem(
    val record: MemorizationRecord,
    val surah: SurahInfo,
    val latestAttempt: QuranMemorizationSavedAttempt? = null,
) {
    val title: String = "${surah.name} ${surah.num}:${record.ayahNumber}"
    val subtitle: String = buildString {
        latestAttempt?.let { attempt ->
            append("Latest result: ${attempt.grade.label}")
            append(" • ")
            append(attempt.overallScore)
            append("%")
        } ?: append(
            when {
                record.isMemorized -> "Memorised"
                record.isWeak -> "Difficult"
                record.isRevision -> "Revision"
                else -> "In progress"
            },
        )
        append(" • Reviewed ${record.reviewCount}x")
    }
}

data class MemorizedSurahDashboardItem(
    val surah: SurahInfo,
    val memorizedCount: Int,
    val completedAt: Long,
) {
    val title: String = "${surah.name} complete"
    val subtitle: String = "$memorizedCount ayahs memorised"
}

data class SurahProgressDashboardItem(
    val surah: SurahInfo,
    val memorizedCount: Int,
    val totalAyahs: Int,
    val needsRevisionCount: Int,
    val incorrectCount: Int,
    val difficultCount: Int,
    val lastPractisedAt: Long,
    val nextAyahNumber: Int,
) {
    val title: String = surah.name
    val progressText: String = "$memorizedCount / $totalAyahs ayahs memorised"
    val subtitle: String = buildList {
        if (needsRevisionCount > 0) add("$needsRevisionCount revision")
        if (incorrectCount > 0) add("$incorrectCount incorrect")
        if (difficultCount > 0) add("$difficultCount difficult")
        add("Next: ${surah.num}:$nextAyahNumber")
    }.joinToString(" • ")
}

enum class MemorizationDashboardGroup(val label: String) {
    InProgress("In Progress"),
    NeedsReview("Needs Revision"),
    Incorrect("Incorrect"),
    Difficult("Difficult"),
    Surahs("Memorised Surahs"),
}

enum class MemorizationConcealAmount(
    val label: String,
    val badgeLabel: String,
    val concealedFraction: Float,
) {
    Quarter(label = "Hide quarter", badgeLabel = "1/4 hidden", concealedFraction = 0.25f),
    Half(label = "Hide half", badgeLabel = "1/2 hidden", concealedFraction = 0.5f),
    ThreeQuarters(label = "Hide 3/4", badgeLabel = "3/4 hidden", concealedFraction = 0.75f),
    Full(label = "Hide all", badgeLabel = "Hidden", concealedFraction = 1f),
}

enum class MemorizationRepeatMode(
    val label: String,
    val repeatCount: Int?,
) {
    Three(label = "3x", repeatCount = 3),
    Five(label = "5x", repeatCount = 5),
    Ten(label = "10x", repeatCount = 10),
    UntilStopped(label = "Until stopped", repeatCount = null),
}

data class MemorizationUiState(
    val records: List<MemorizationRecord> = emptyList(),
    val attempts: List<QuranMemorizationSavedAttempt> = emptyList(),
    val surahAttempts: List<QuranSurahMemorizationSavedAttempt> = emptyList(),
    val selectedGroup: MemorizationDashboardGroup = MemorizationDashboardGroup.InProgress,
    val selectedSurah: SurahInfo = quranCatalog.first(),
    val selectedAyah: Int = 1,
) {
    val memorizedSurahs: List<MemorizedSurahDashboardItem>
        get() = buildMemorizedSurahs(records)

    val inProgressSurahs: List<SurahProgressDashboardItem>
        get() = buildSurahProgressItems(records, attempts, surahAttempts)

    val overview: MemorizationOverview
        get() {
            val memorizedSurahCount = memorizedSurahs.size
            val latestAttempts = attempts.latestByVerse()
            val latestStatuses = buildLatestStatusesByVerse(attempts, surahAttempts)
            val recordsByVerse = records.associateBy { it.verseKey }
            fun statusFor(verseKey: String): AyahMemorizationStatus? =
                recordsByVerse[verseKey]?.manualStatus() ?: latestStatuses[verseKey]?.status
            return MemorizationOverview(
                startedCount = records.count { it.isMemorising && !it.isMemorized },
                memorizedCount = records.count { it.isMemorized },
                revisionCount = records.count { it.isRevision },
                needsReviewCount = allDashboardRecords().count { record -> statusFor(record.verseKey) == AyahMemorizationStatus.NEEDS_REVIEW },
                incorrectCount = allDashboardRecords().count { record -> statusFor(record.verseKey) == AyahMemorizationStatus.INCORRECT },
                difficultCount = records.count { it.isWeak },
                memorizedSurahCount = memorizedSurahCount,
                inProgressSurahCount = inProgressSurahs.size,
            )
        }

    val dashboardItems: List<MemorizationDashboardItem>
        get() {
            val fullyMemorizedSurahs = memorizedSurahs.map { it.surah.num }.toSet()
            val latestAttempts = attempts.latestByVerse()
            val latestStatuses = buildLatestStatusesByVerse(attempts, surahAttempts)
            val recordsByVerse = records.associateBy { it.verseKey }
            return allDashboardRecords()
            .filter { record ->
                val latestAttempt = latestAttempts[record.verseKey]
                val status = recordsByVerse[record.verseKey]?.manualStatus() ?: latestStatuses[record.verseKey]?.status
                when (selectedGroup) {
                    MemorizationDashboardGroup.InProgress -> false
                    MemorizationDashboardGroup.NeedsReview -> status == AyahMemorizationStatus.NEEDS_REVIEW
                    MemorizationDashboardGroup.Incorrect -> status == AyahMemorizationStatus.INCORRECT
                    MemorizationDashboardGroup.Surahs -> false
                    MemorizationDashboardGroup.Difficult -> record.isWeak
                }
            }
            .sortedWith(compareByDescending<MemorizationRecord> { it.updatedAt }.thenBy { it.surahNumber }.thenBy { it.ayahNumber })
            .mapNotNull { record ->
                quranCatalog.firstOrNull { it.num == record.surahNumber }?.let { surah ->
                    MemorizationDashboardItem(
                        record = record,
                        surah = surah,
                        latestAttempt = latestAttempts[record.verseKey],
                    )
                }
            }
        }

    val continueItem: MemorizationDashboardItem?
        get() {
            val recordsByVerse = records.associateBy { it.verseKey }
            val activeSurah = inProgressSurahs.firstOrNull()
            if (activeSurah != null) {
                val verseKey = "${activeSurah.surah.num}:${activeSurah.nextAyahNumber}"
                val latestAttempt = attempts.latestByVerse()[verseKey]
                val record = recordsByVerse[verseKey] ?: MemorizationRecord(
                    verseKey = verseKey,
                    surahNumber = activeSurah.surah.num,
                    ayahNumber = activeSurah.nextAyahNumber,
                    startedAt = activeSurah.lastPractisedAt,
                    lastReviewedAt = activeSurah.lastPractisedAt,
                    reviewCount = 0,
                    memorizedAt = null,
                    isRevision = false,
                    isWeak = false,
                    updatedAt = activeSurah.lastPractisedAt,
                    isMemorising = true,
                )
                return MemorizationDashboardItem(
                    record = record,
                    surah = activeSurah.surah,
                    latestAttempt = latestAttempt,
                )
            }
            return records
                .sortedWith(compareByDescending<MemorizationRecord> { it.updatedAt })
                .firstOrNull { it.isMemorising && !it.isMemorized }
                ?.let { record ->
                quranCatalog.firstOrNull { it.num == record.surahNumber }?.let { surah ->
                    MemorizationDashboardItem(record, surah)
                }
            }
        }

    private fun allDashboardRecords(): List<MemorizationRecord> {
        val recordsByVerse = records.associateBy { it.verseKey }
        val aiAttemptRecords = buildLatestStatusesByVerse(attempts, surahAttempts)
            .values
            .mapNotNull { latest ->
                if (recordsByVerse.containsKey(latest.verseKey)) return@mapNotNull null
                if (latest.status != AyahMemorizationStatus.NEEDS_REVIEW && latest.status != AyahMemorizationStatus.INCORRECT) return@mapNotNull null
                val (surah, ayah) = latest.verseKey.split(':').let { parts ->
                    (parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to
                        (parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null)
                }
                MemorizationRecord(
                    verseKey = latest.verseKey,
                    surahNumber = surah,
                    ayahNumber = ayah,
                    startedAt = latest.timestampMs,
                    lastReviewedAt = latest.timestampMs,
                    reviewCount = 1,
                    memorizedAt = null,
                    isRevision = latest.status == AyahMemorizationStatus.NEEDS_REVIEW,
                    isWeak = false,
                    updatedAt = latest.timestampMs,
                    isNeedsRevision = latest.status == AyahMemorizationStatus.NEEDS_REVIEW,
                    isIncorrect = latest.status == AyahMemorizationStatus.INCORRECT,
                    isMemorising = false,
                )
            }
        return records + aiAttemptRecords
    }
}

private data class LatestMemorizationStatus(
    val verseKey: String,
    val status: AyahMemorizationStatus,
    val timestampMs: Long,
)

private fun List<QuranMemorizationSavedAttempt>.latestByVerse(): Map<String, QuranMemorizationSavedAttempt> =
    groupBy { it.verseKey }
        .mapValues { (_, attempts) -> attempts.maxBy { it.timestampMs } }

private fun buildLatestStatusesByVerse(
    attempts: List<QuranMemorizationSavedAttempt>,
    surahAttempts: List<QuranSurahMemorizationSavedAttempt>,
): Map<String, LatestMemorizationStatus> {
    val ayahStatuses = attempts.map {
        LatestMemorizationStatus(
            verseKey = it.verseKey,
            status = it.status,
            timestampMs = it.timestampMs,
        )
    }
    val surahStatuses = surahAttempts.flatMap { attempt ->
        attempt.ayahResults.map { result ->
            LatestMemorizationStatus(
                verseKey = result.verseKey,
                status = result.status,
                timestampMs = attempt.timestampMs,
            )
        }
    }
    return (ayahStatuses + surahStatuses)
        .groupBy { it.verseKey }
        .mapValues { (_, statuses) -> statuses.maxBy { it.timestampMs } }
}

private fun QuranMemorizationSavedAttempt.toMemorizationRecord(): MemorizationRecord =
    MemorizationRecord(
        verseKey = verseKey,
        surahNumber = surahNumber,
        ayahNumber = ayahNumber,
        startedAt = timestampMs,
        lastReviewedAt = timestampMs,
        reviewCount = 1,
        memorizedAt = null,
        isRevision = false,
        isWeak = false,
        updatedAt = timestampMs,
        isNeedsRevision = status == AyahMemorizationStatus.NEEDS_REVIEW,
        isIncorrect = status == AyahMemorizationStatus.INCORRECT,
        isMemorising = false,
    )

private fun MemorizationRecord.manualStatus(): AyahMemorizationStatus? =
    when {
        isIncorrect -> AyahMemorizationStatus.INCORRECT
        isNeedsRevision || isRevision -> AyahMemorizationStatus.NEEDS_REVIEW
        isMemorized -> AyahMemorizationStatus.PASSED
        isMemorising -> AyahMemorizationStatus.ATTEMPTED
        else -> null
    }

private fun buildMemorizedSurahs(records: List<MemorizationRecord>): List<MemorizedSurahDashboardItem> =
    records
        .filter { it.isMemorized }
        .groupBy { it.surahNumber }
        .mapNotNull { (surahNumber, surahRecords) ->
            val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return@mapNotNull null
            val memorizedAyahs = surahRecords.map { it.ayahNumber }.toSet()
            if (memorizedAyahs.size < surah.ayat) return@mapNotNull null
            MemorizedSurahDashboardItem(
                surah = surah,
                memorizedCount = surah.ayat,
                completedAt = surahRecords.maxOfOrNull { it.memorizedAt ?: it.updatedAt } ?: 0L,
            )
        }
        .sortedByDescending { it.completedAt }

private fun buildSurahProgressItems(
    records: List<MemorizationRecord>,
    attempts: List<QuranMemorizationSavedAttempt>,
    surahAttempts: List<QuranSurahMemorizationSavedAttempt>,
): List<SurahProgressDashboardItem> {
    val latestStatuses = buildLatestStatusesByVerse(attempts, surahAttempts)
    val recordsByVerse = records.associateBy { it.verseKey }
    val activeSurahNumbers = (
        records
            .asSequence()
            .filter { it.isMemorising || it.isWeak || it.isNeedsRevision || it.isIncorrect || it.isRevision || it.isMemorized }
            .map { it.surahNumber } +
            latestStatuses.values
                .asSequence()
                .filter { it.status == AyahMemorizationStatus.NEEDS_REVIEW || it.status == AyahMemorizationStatus.INCORRECT }
                .mapNotNull { it.verseKey.substringBefore(':').toIntOrNull() }
        ).toSet()

    return activeSurahNumbers
        .mapNotNull { surahNumber ->
            val surah = quranCatalog.firstOrNull { it.num == surahNumber } ?: return@mapNotNull null
            val surahRecords = records.filter { it.surahNumber == surahNumber }
            val memorizedAyahs = surahRecords.filter { it.isMemorized }.map { it.ayahNumber }.toSet()
            if (memorizedAyahs.size >= surah.ayat) return@mapNotNull null

            fun statusFor(ayahNumber: Int): AyahMemorizationStatus? {
                val verseKey = "$surahNumber:$ayahNumber"
                return recordsByVerse[verseKey]?.manualStatus() ?: latestStatuses[verseKey]?.status
            }

            val nextAyah = (1..surah.ayat).firstOrNull { ayah ->
                val status = statusFor(ayah)
                ayah !in memorizedAyahs && (status == AyahMemorizationStatus.INCORRECT || status == AyahMemorizationStatus.NEEDS_REVIEW)
            } ?: (1..surah.ayat).firstOrNull { it !in memorizedAyahs } ?: 1

            val lastPractisedAt = maxOf(
                surahRecords.maxOfOrNull { it.updatedAt } ?: 0L,
                latestStatuses.values
                    .filter { it.verseKey.substringBefore(':').toIntOrNull() == surahNumber }
                    .maxOfOrNull { it.timestampMs } ?: 0L,
            )
            SurahProgressDashboardItem(
                surah = surah,
                memorizedCount = memorizedAyahs.size,
                totalAyahs = surah.ayat,
                needsRevisionCount = (1..surah.ayat).count { statusFor(it) == AyahMemorizationStatus.NEEDS_REVIEW },
                incorrectCount = (1..surah.ayat).count { statusFor(it) == AyahMemorizationStatus.INCORRECT },
                difficultCount = surahRecords.count { it.isWeak },
                lastPractisedAt = lastPractisedAt,
                nextAyahNumber = nextAyah,
            )
        }
        .sortedByDescending { it.lastPractisedAt }
}

fun MemorizationRecord.toPreferenceEntry(): String = listOf(
    verseKey,
    surahNumber.toString(),
    ayahNumber.toString(),
    startedAt.toString(),
    lastReviewedAt.toString(),
    reviewCount.toString(),
    memorizedAt?.toString().orEmpty(),
    isRevision.toString(),
    isWeak.toString(),
    updatedAt.toString(),
    isNeedsRevision.toString(),
    isIncorrect.toString(),
    isMemorising.toString(),
).joinToString("|")

fun String.toMemorizationRecordOrNull(): MemorizationRecord? {
    val parts = split('|')
    if (parts.size != 10 && parts.size != 13) return null
    val verseKey = parts[0].takeIf { it.contains(':') } ?: return null
    return MemorizationRecord(
        verseKey = verseKey,
        surahNumber = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return null,
        ayahNumber = parts[2].toIntOrNull()?.coerceAtLeast(1) ?: return null,
        startedAt = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: return null,
        lastReviewedAt = parts[4].toLongOrNull()?.coerceAtLeast(0L) ?: return null,
        reviewCount = parts[5].toIntOrNull()?.coerceAtLeast(0) ?: return null,
        memorizedAt = parts[6].takeIf { it.isNotBlank() }?.toLongOrNull(),
        isRevision = parts[7].toBooleanStrictOrNull() ?: false,
        isWeak = parts[8].toBooleanStrictOrNull() ?: false,
        updatedAt = parts[9].toLongOrNull()?.coerceAtLeast(0L) ?: return null,
        isNeedsRevision = parts.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
        isIncorrect = parts.getOrNull(11)?.toBooleanStrictOrNull() ?: false,
        isMemorising = parts.getOrNull(12)?.toBooleanStrictOrNull() ?: true,
    )
}
