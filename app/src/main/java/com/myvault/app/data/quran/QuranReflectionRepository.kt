package com.myvault.app.data.quran

import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.NoteEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

const val QURAN_REFLECTION_FOLDER_NAME = "Quran Reflections"

data class QuranReflectionSummary(
    val count: Int = 0,
    val latestReference: String = "",
)

data class QuranReflectionItem(
    val noteId: String,
    val title: String,
    val surahName: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val arabicPreview: String,
    val translationPreview: String,
    val reflectionPreview: String,
    val reflectionBody: String,
    val updatedAt: Long,
)

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class QuranReflectionRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val quranCatalogRepository: QuranCatalogRepository,
    private val quranTextRepository: QuranTextRepository,
) {
    fun observeReflectionSummary(): Flow<QuranReflectionSummary> =
        observeReflections().mapLatest { reflections ->
            QuranReflectionSummary(
                count = reflections.size,
                latestReference = reflections.firstOrNull()?.referenceLabel.orEmpty(),
            )
        }

    fun observeReflectionItems(): Flow<List<QuranReflectionItem>> =
        observeReflections().mapLatest { reflections ->
            withContext(Dispatchers.IO) {
                reflections.map { reflection ->
                    val ayah = quranTextRepository.getSurahAyahs(reflection.surahNumber)
                        .firstOrNull { it.ayahNumber == reflection.ayahNumber }
                    val surah = quranCatalogRepository.surah(reflection.surahNumber)
                    QuranReflectionItem(
                        noteId = reflection.note.id,
                        title = reflection.note.title,
                        surahName = surah?.name ?: reflection.sourceSurahName,
                        surahNumber = reflection.surahNumber,
                        ayahNumber = reflection.ayahNumber,
                        verseKey = "${reflection.surahNumber}:${reflection.ayahNumber}",
                        arabicPreview = ayah?.arabicText.orEmpty(),
                        translationPreview = ayah?.translation.orEmpty(),
                        reflectionPreview = reflection.note.bodyPlainText
                            .reflectionBodyPreview(ayah?.arabicText.orEmpty(), ayah?.translation.orEmpty()),
                        reflectionBody = reflection.note.bodyPlainText
                            .reflectionBody(ayah?.arabicText.orEmpty(), ayah?.translation.orEmpty()),
                        updatedAt = reflection.note.updatedAt,
                    )
                }
            }
        }

    private fun observeReflections(): Flow<List<ParsedReflection>> =
        combine(folderDao.observeAll(), noteDao.observeAll()) { folders, notes ->
            val reflectionFolderIds = folders
                .filter {
                    it.mode == FOLDER_MODE_STUDY &&
                        it.name.equals(QURAN_REFLECTION_FOLDER_NAME, ignoreCase = true)
                }
                .map { it.id }
                .toSet()
            if (reflectionFolderIds.isEmpty()) {
                emptyList()
            } else {
                notes
                    .asSequence()
                    .filter { it.folderId in reflectionFolderIds }
                    .mapNotNull { note -> note.toParsedReflection() }
                    .sortedByDescending { it.note.updatedAt }
                    .toList()
            }
        }

    private fun NoteEntity.toParsedReflection(): ParsedReflection? {
        val match = SourceRegex.find(bodyPlainText) ?: return null
        val surahNumber = match.groupValues[2].toIntOrNull() ?: return null
        val ayahNumber = match.groupValues[3].toIntOrNull() ?: return null
        if (surahNumber <= 0 || ayahNumber <= 0) return null
        return ParsedReflection(
            note = this,
            sourceSurahName = match.groupValues[1].trim(),
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
        )
    }

    private data class ParsedReflection(
        val note: NoteEntity,
        val sourceSurahName: String,
        val surahNumber: Int,
        val ayahNumber: Int,
    ) {
        val referenceLabel: String = "$sourceSurahName $surahNumber:$ayahNumber"
    }

    private companion object {
        val SourceRegex = Regex("""Source:\s*(.*?)\s+(\d{1,3}):(\d{1,3})""")
    }
}

private fun String.reflectionBodyPreview(arabic: String, translation: String): String {
    var value = reflectionBody(arabic, translation)
    if (value.isBlank()) {
        value = lines().map { it.trim() }.filter { it.isNotBlank() }.takeLast(2).joinToString(" ")
    }
    return value.replace(Regex("\\s+"), " ").trim()
}

private fun String.reflectionBody(arabic: String, translation: String): String =
    this
        .replace(Regex("""(?m)^Source:\s*.*$"""), "")
        .replace(arabic, "")
        .replace(translation, "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .drop(1)
        .joinToString("\n\n")
        .trim()
