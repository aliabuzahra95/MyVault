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
) {
    val isMemorized: Boolean
        get() = memorizedAt != null
}

data class MemorizationOverview(
    val startedCount: Int = 0,
    val memorizedCount: Int = 0,
    val revisionCount: Int = 0,
    val difficultCount: Int = 0,
    val memorizedSurahCount: Int = 0,
)

data class MemorizationDashboardItem(
    val record: MemorizationRecord,
    val surah: SurahInfo,
) {
    val title: String = "${surah.name} ${surah.num}:${record.ayahNumber}"
    val subtitle: String = buildString {
        append(
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

enum class MemorizationDashboardGroup(val label: String) {
    All("All"),
    Started("Started"),
    Memorised("Ayahs"),
    Surahs("Surahs"),
    Revision("Revision"),
    Difficult("Difficult"),
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
    val selectedGroup: MemorizationDashboardGroup = MemorizationDashboardGroup.All,
    val selectedSurah: SurahInfo = quranCatalog.first(),
    val selectedAyah: Int = 1,
) {
    val memorizedSurahs: List<MemorizedSurahDashboardItem>
        get() = buildMemorizedSurahs(records)

    val overview: MemorizationOverview
        get() {
            val memorizedSurahCount = memorizedSurahs.size
            return MemorizationOverview(
                startedCount = records.size,
                memorizedCount = records.count { it.isMemorized },
                revisionCount = records.count { it.isRevision },
                difficultCount = records.count { it.isWeak },
                memorizedSurahCount = memorizedSurahCount,
            )
        }

    val dashboardItems: List<MemorizationDashboardItem>
        get() {
            val fullyMemorizedSurahs = memorizedSurahs.map { it.surah.num }.toSet()
            return records
            .filter { record ->
                when (selectedGroup) {
                    MemorizationDashboardGroup.All -> record.surahNumber !in fullyMemorizedSurahs || !record.isMemorized
                    MemorizationDashboardGroup.Started -> !record.isMemorized
                    MemorizationDashboardGroup.Memorised -> record.isMemorized && record.surahNumber !in fullyMemorizedSurahs
                    MemorizationDashboardGroup.Surahs -> false
                    MemorizationDashboardGroup.Revision -> record.isRevision
                    MemorizationDashboardGroup.Difficult -> record.isWeak
                }
            }
            .sortedWith(compareByDescending<MemorizationRecord> { it.updatedAt }.thenBy { it.surahNumber }.thenBy { it.ayahNumber })
            .mapNotNull { record ->
                quranCatalog.firstOrNull { it.num == record.surahNumber }?.let { surah ->
                    MemorizationDashboardItem(record, surah)
                }
            }
        }

    val continueItem: MemorizationDashboardItem?
        get() = records
            .sortedWith(compareByDescending<MemorizationRecord> { it.isWeak }.thenByDescending { it.updatedAt })
            .firstOrNull { !it.isMemorized }
            ?.let { record ->
                quranCatalog.firstOrNull { it.num == record.surahNumber }?.let { surah ->
                    MemorizationDashboardItem(record, surah)
                }
            }
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
).joinToString("|")

fun String.toMemorizationRecordOrNull(): MemorizationRecord? {
    val parts = split('|')
    if (parts.size != 10) return null
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
    )
}
