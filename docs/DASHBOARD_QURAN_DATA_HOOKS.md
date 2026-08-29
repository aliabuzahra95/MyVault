# Dashboard Qur'an Data Hooks

Status: **DATA AUDIT ONLY - NO DASHBOARD UI CHANGES**

The Dashboard Qur'an and Reflections visuals remain pending in the separate UI Mockup work. The production sources and exact existing navigation hooks are recorded here so the approved design can be wired without inventing data.

## Qur'an Continue

Authoritative persisted source:

- `VaultPreferences.userPreferences`
- `VaultUserPreferences.quranLastReadSurah`
- `VaultUserPreferences.quranLastReadAyah`
- writes occur through `VaultPreferences.setQuranReadingPosition()` from `QuranReaderViewModel.updateLastReadPosition()`.

Recommended Dashboard state owner:

- Extend `HomeViewModel`/`HomeUiState` with a small immutable Qur'an Continue state derived from the already-combined `VaultPreferences.userPreferences` flow.
- Keep the stored Surah and ayah numbers authoritative. Resolve a display name through the existing Qur'an catalog only when the frozen Dashboard contract requires it.
- Do not create another last-read preference or Dashboard-owned reading position.

Exact existing navigation path:

1. Build the verse key as `<surah>:<ayah>`.
2. Set `VaultNavHost.pendingQuranVerseKey`.
3. Switch to the Islamic Corpus workspace if required.
4. Return to the Home shell and select `VaultRootMode.Quran`.
5. The Qur'an route consumes the key and calls `QuranReaderViewModel.openBookmarkedAyah(verseKey)`, which loads the exact Surah and ayah.

## Recent Reflections

Authoritative source:

- `QuranReflectionRepository.observeReflectionItems()`.
- Items are sorted newest first by the backing reflection note's `updatedAt`.
- Each `QuranReflectionItem` already contains `surahName`, `surahNumber`, `ayahNumber`, `verseKey`, `reflectionPreview`, Arabic/translation previews, note ID, and timestamp.
- `HomeViewModel` already combines this flow and exposes the newest eight items as `HomeUiState.quranReflectionItems` plus `quranReflectionSummary`.

Recommended Dashboard wiring:

- Reuse `HomeUiState.quranReflectionItems`; do not query notes again from the Dashboard.
- The approved card can use the existing verse reference and `reflectionPreview` without sample content.
- Exact verse navigation should reuse the same `pendingQuranVerseKey` flow used by the Reflections Hub: set `reflection.verseKey`, select the Islamic Corpus Qur'an root, then let `QuranReaderViewModel.openBookmarkedAyah()` consume it.
- If the final design opens the reflection note instead of the verse, use the existing `noteId` and Stage 4 Note route. The visual amendment must decide this; production must not guess.

## Qur'an Bookmarks

Available persisted source if later approved:

- `VaultUserPreferences.quranBookmarkedVerses` is the authoritative set of stable `<surah>:<ayah>` keys.
- `QuranReaderUiState.bookmarkedVerseKeys` is the live reader projection.
- Display metadata can be resolved through the existing `QuranCatalogRepository` and `QuranTextRepository` only when required.
- Navigation reuses `pendingQuranVerseKey` and `QuranReaderViewModel.openBookmarkedAyah()`.

## Implementation Boundary

No Dashboard composable, navigation control, repository contract, preference, snapshot format, or visual styling was changed during this audit. The future implementation should wire these existing sources into the frozen Dashboard amendment and add snapshot fields only if the approved startup experience requires them.
