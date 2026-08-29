# Dashboard Qur'an Data Hooks

Status: **IMPLEMENTED FROM FROZEN PHYSICAL-DEVICE REFINEMENT**

The frozen Physical Device Refinement approved the Dashboard Qur'an and Reflections sections. They are now wired to the production sources below without introducing new persistence or sample data.

## Qur'an Continue

Authoritative persisted source:

- `VaultPreferences.userPreferences`
- `VaultUserPreferences.quranLastReadSurah`
- `VaultUserPreferences.quranLastReadAyah`
- writes occur through `VaultPreferences.setQuranReadingPosition()` from `QuranReaderViewModel.updateLastReadPosition()`.

Implemented Dashboard state owner:

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

Implemented Dashboard wiring:

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

## Implementation Result

- The compact section order is Continue, Qur'an, Recent, Reflections, Pinned.
- Sections render only when backed by real production state.
- Qur'an uses the saved Surah/ayah and the existing exact-verse navigation handoff.
- Reflections uses the newest one to three repository items and the existing exact-verse handoff.
- The startup snapshot contains only the derived display state needed to avoid a transient empty Dashboard.
- No Qur'an preference, reflection entity, repository contract, or canonical data changed.
