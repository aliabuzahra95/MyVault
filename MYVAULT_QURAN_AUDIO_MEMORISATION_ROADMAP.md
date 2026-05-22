# MyVault Qur'an Audio, Tafsir, and Memorisation Roadmap

This document records the audit and migration plan for bringing the remaining Qur'anic Threads Qur'an features into MyVault's Islamic Corpus workspace.

The purpose of this phase is planning only. Do not blindly merge Qur'anic Threads into MyVault. The goal is near feature parity while preserving MyVault's stability, backup safety, Google Drive sync model, premium UI, and existing Study/Library/Personal systems.

## Current MyVault Status

MyVault already has:

- Workspace shell: Personal and Islamic Corpus
- Islamic Corpus tabs: Study, Library, Qur'an
- Reader-first Qur'an flow
- Uthmani text rendering
- Tajweed toggle and rendering foundation
- Translation toggle and translation font scaling
- Tafsir expansion using the currently migrated source
- Bookmarks foundation
- Reflection popup linked to ayat
- Continue Reading and Recent Surahs
- Library Continue Reading and recent document flow
- Google Drive incremental sync and manual `.vaultbackup` recovery

## Qur'anic Threads Systems Audited

Source project:

`/Users/aliah/Desktop/Finished Projects/QuranicThreads copy`

Important audited files:

- `data/repository/QuranAudioRepository.kt`
- `data/audio/QuranAudioPlayer.kt`
- `data/audio/QuranAudioDownloadService.kt`
- `data/audio/QuranAudioNotificationService.kt`
- `data/audio/QuranAudioPlayerRegistry.kt`
- `ui/components/QuranMiniPlayer.kt`
- `ui/screens/AudioDownloadsSheet.kt`
- `ui/screens/ReciterPickerSheet.kt`
- `data/repository/QuranAssetRepository.kt`
- `ui/screens/QuranReaderViewModel.kt`
- `ui/screens/AyahMemorizationSheet.kt`
- `ui/screens/MemorizationDashboardScreen.kt`
- `data/repository/MemorizationRepository.kt`
- `data/repository/MemorizedSurahRepository.kt`
- `data/settings/MemorizationProgressStore.kt`
- `data/local/MemorizationRecordEntity.kt`
- `data/local/MemorizedSurahEntity.kt`
- `res/xml/backup_rules.xml`
- `res/xml/data_extraction_rules.xml`

## 1. Audio Architecture Audit

Qur'anic Threads has two distinct audio flows.

### A. Stream/Play From Ayah

The reader can play from a selected ayah. The system:

- Loads supported reciters from the Quran.com API through a Cloudflare worker.
- Fetches chapter audio metadata with verse timestamps, word timings, and verse audio URLs.
- Prefers verse-by-verse audio when available so playback starts exactly at the selected ayah.
- Falls back to full-surah audio plus timestamps when verse files are unavailable.
- Uses local cached files when present.
- Downloads missing playback files on demand.
- Tracks active verse, reciter, playback position, duration, speed, and loading state.
- Shows a mini-player with playback controls.

Main reusable classes:

- `QuranAudioRepository`
- `QuranAudioPlayer`
- `QuranMiniPlayer`
- `ReciterPickerSheet`

### B. Manual Audio Download Management

This is separate from tap-to-play streaming.

The user opens an Audio Downloads sheet, chooses a reciter, sees the Surah list, and manually downloads specific Surahs for offline playback.

The system:

- Keeps downloads separated by reciter.
- Tracks per-reciter/per-surah download state.
- Supports queued downloads.
- Uses a foreground service for long downloads.
- Writes `.complete` marker files after successful downloads.
- Stores metadata JSON beside audio files.
- Uses temp `.part` files and atomic replacement to avoid corrupted audio files.

Main reusable classes:

- `AudioDownloadsSheet`
- `QuranAudioDownloadService`
- `QuranAudioRepository.SurahDownloadState`

### Playback Speeds

Qur'anic Threads exposes:

- `0.5x`
- `1x`
- `1.5x`
- `2x`

`QuranAudioPlayer` uses `MediaPlayer.playbackParams` on Android M+.

## 2. Tafsir Architecture Audit

Qur'anic Threads supports multiple tafsir sources.

Important behavior:

- Local abridged tafsir is source `-1`.
- Remote tafsir sources are fetched from the Quran.com resources endpoint via the Cloudflare worker.
- Desired remote tafsirs are filtered from the larger resource list.
- Tafsir content is cached in memory by `verseKey:tafsirId`.
- Tafsir expansion is per ayah.
- Tafsir source selection can be per ayah.
- If a remote tafsir request fails or data is missing, the UI can safely fall back.

Main reusable pieces:

- `QuranAssetRepository.getAvailableTafsirSources`
- `QuranAssetRepository.getTafsir`
- `TafsirSourceUiModel`
- Existing tafsir state patterns in `QuranReaderViewModel`

MyVault already has basic tafsir expansion, so the next tafsir step should be source selection rather than replacing the entire tafsir layer.

## 3. Memorisation Architecture Audit

Qur'anic Threads memorisation has two levels:

### Ayah-Level Memorisation

Data model:

- `verseKey`
- `surahNumber`
- `ayahNumber`
- `startedAt`
- `lastReviewedAt`
- `reviewCount`
- `memorizedAt`
- `isRevision`
- `isWeak`
- `updatedAt`

Actions:

- Start memorising ayah
- Mark ayah memorised
- Mark for revision
- Mark difficult/weak
- Repeat ayah 3x, 5x, 10x, or until stopped
- Conceal quarter, half, three-quarters, or all of ayah

### Surah-Level Memorisation

Data model:

- `surahNumber`
- `isMemorized`
- `needsReview`
- `markedMemorizedAt`
- `markedNeedsReviewAt`
- `updatedAt`

### Memorisation UI

Qur'anic Threads has:

- `AyahMemorizationSheet`
- `MemorizationDashboardScreen`
- Metrics for Started, Memorised ayahs, Revision, Difficult, Surahs memorised
- Continue Memorising card
- Revision focus card
- Group filters: All, Started, Ayahs, Surahs, Revision, Difficult

The visual structure is reusable, but the data layer must be migrated carefully into MyVault Room and backup systems.

## 4. Reusable Systems

Safe to reuse with adaptation:

- `QuranAudioRepository` networking, metadata parsing, local audio path strategy, and download state model
- `QuranAudioPlayer` playback state and speed logic
- `QuranMiniPlayer` UI structure, adapted to MyVault colors/components
- `ReciterPickerSheet` UI structure
- `AudioDownloadsSheet` UI structure
- `QuranAudioDownloadService` queue/progress model, with package/action names changed
- `QuranAudioNotificationService`, adapted to MyVault notification/channel naming
- `TafsirSourceUiModel` and tafsir-source selection logic
- `MemorizationRecordEntity`, `MemorizedSurahEntity`, DAOs, and repositories
- `AyahMemorizationSheet` UI and concepts
- `MemorizationDashboardScreen` UI and grouping model

Should remain isolated or heavily adapted:

- Qur'anic Threads backup manager
- Qur'anic Threads Supabase repositories
- Qur'anic Threads app navigation root
- Qur'anic Threads theme globals
- Qur'anic Threads widget integration
- Any hardcoded `com.example.quranicthreads` service actions/package references

Do not migrate:

- Supabase auth/sync
- Separate Room database
- Whole app navigation shell
- Whole Qur'anic Threads theme system
- Existing Qur'anic Threads backup architecture

## 5. Risky Systems and Coupling

High-risk areas:

- Foreground services require manifest entries, notification permissions, channel setup, and package/action renaming.
- `MediaPlayer` can leak if not lifecycle-owned and released reliably.
- Background downloads can fail if the process is killed unless handled as a service/WorkManager-style operation.
- Full-surah audio files are large; backup/sync can become huge if included.
- Verse-by-verse downloads can create many small files; file count and Drive restore time can explode if backed up.
- Multiple tafsir remote calls can become slow or fail; must be cached and graceful.
- Memorisation writes can become noisy if state is saved too often during repeat/conceal toggles.
- Qur'anic Threads currently has memorisation widget code; adding widgets now would increase scope and app complexity.
- Qur'anic Threads uses its own backup manager and Supabase assumptions; do not import these.

Medium-risk areas:

- Reciter metadata depends on a Cloudflare worker and Quran.com API behavior.
- Playback speed support depends on Android API level.
- Full-surah timestamp seeking may be less exact than verse-by-verse files.
- Per-ayah UI state can cause recomposition jank if combined into one giant reader state without stable keys.

## 6. Recommended Migration Order

### Phase 1: Audio Foundation, No UI Overload

Add the core audio data layer and player:

- Audio reciter model
- Audio metadata model
- Audio repository
- Audio player controller
- Reader ViewModel state for selected/playing ayah
- Reciter picker sheet
- Play button on ayah cards

Acceptance:

- Tap ayah play.
- Choose reciter.
- Audio plays inside MyVault.
- Mini-player appears.
- Pause/resume/stop works.
- Reader remains smooth.

### Phase 2: Mini-Player and Notification Hardening

Add:

- Mini-player matching Qur'anic Threads
- Speed controls: `0.5x`, `1x`, `1.5x`, `2x`
- Previous/next ayah
- 10-second rewind/forward
- Seek slider
- Foreground playback notification

Acceptance:

- Playback continues when app is backgrounded.
- Notification controls work.
- Player releases cleanly.

### Phase 3: Manual Audio Downloads

Add dedicated Audio Downloads flow inside Qur'an settings/filter sheet:

- Audio Downloads menu item
- Reciter selection
- Surah list
- Per-surah download state
- Foreground download service or WorkManager-backed downloader
- Download progress and retry
- Delete downloaded audio action

Acceptance:

- User can manually download Surahs for one reciter.
- Downloads are resumable enough not to corrupt existing files.
- Offline playback uses local files.

### Phase 4: Multiple Tafsir Sources

Add:

- Tafsir source list
- Tafsir source selector in Qur'an settings or per expanded tafsir block
- Persist default tafsir source
- Per-ayah source override only if it stays uncluttered
- Caching and graceful fallback

Acceptance:

- User can switch tafsir source.
- Tafsir expansion remains smooth.
- Missing remote tafsir does not crash or corrupt the reader.

### Phase 5: Memorisation Data Layer

Add Room entities and backup support:

- `quran_memorization_records`
- `quran_memorized_surahs`
- memorisation settings/progress in `VaultPreferences` or Room
- DAOs and repositories
- backup/restore JSON inclusion
- Google Drive metadata inclusion

Acceptance:

- Records persist and restore.
- No UI yet beyond minimal developer-safe hooks.

### Phase 6: Ayah Memorisation Sheet

Add:

- Memorise action on ayah
- Qur'anic Threads-style memorisation sheet
- Repeat modes
- Conceal modes
- Start/memorised/revision/difficult toggles

Acceptance:

- Works inside reader without navigation churn.
- Does not destabilise audio.

### Phase 7: Memorisation Dashboard

Add Islamic Corpus Memorisation destination:

- Either fourth Islamic Corpus tab only if UI remains balanced, or an entry from Qur'an settings/header.
- Dashboard copied closely from Qur'anic Threads.
- Tap dashboard item jumps to ayah.

Acceptance:

- Dashboard gives a coherent home for memorisation without turning MyVault into a crowded dashboard app.

### Phase 8: Optional Widget Later

Do not migrate widget now. Revisit only after memorisation is stable.

## 7. Offline Audio and Download Strategy

Recommended local folder:

`filesDir/quran_audio/<reciterId>/...`

Use:

- `surah_<number>.mp3` for full-surah mode
- `surah_<number>/<verseKey>.mp3` for verse-by-verse mode
- `surah_<number>.metadata.json`
- `surah_<number>.complete`
- `.part` temp files during download

Rules:

- Do not overwrite completed audio until the temp file fully downloads.
- Use completion marker files as source of truth for "downloaded".
- Revalidate file existence on app startup or when opening Audio Downloads.
- Keep download state in memory plus marker files, not only UI state.
- Provide delete downloaded Surah action later.

## 8. Backup Recommendation for Audio Files

Downloaded audio files should be excluded from Google Drive backup and `.vaultbackup` by default.

Reason:

- Audio files are large.
- Full Qur'an downloads for multiple reciters can be many gigabytes.
- Including audio would make Drive sync slow, expensive, and fragile.
- Restore would take much longer and may fail on poor connections.
- Audio is re-downloadable from source.
- Qur'anic Threads already excludes `file/audio/` in Android backup rules.

What should be backed up:

- Selected/default reciter ID
- Playback settings
- Download manifest metadata only if useful:
  - reciter ID
  - surah number
  - downloaded-at timestamp
  - file mode

Restore behavior:

- Restore settings and metadata.
- If audio files are missing, show "Not downloaded on this device".
- Allow one-tap re-download.

Optional future setting:

- "Include offline audio in manual export" as an advanced explicit option only, not default.

## 9. Media Playback Strategy

Initial recommendation:

- Reuse `MediaPlayer` approach first because Qur'anic Threads already works with it and it is smaller than introducing a new media stack.
- Wrap it in a MyVault-owned singleton/controller with lifecycle-aware release.
- Keep playback state in a `StateFlow`.
- Use foreground service for background playback notification.
- Add robust stop/release paths on app shutdown and when switching reciters/surahs.

Future option:

- Consider migrating to Media3/ExoPlayer only after the Qur'anic Threads parity feature set is stable.
- Media3 would be stronger long-term, but it is a larger integration and should not block near-term parity.

## 10. Performance and Memory Concerns

Audio:

- Do not load audio into memory; always stream to/from file paths.
- Avoid reading full MP3 files into byte arrays.
- Avoid downloading on the main thread.
- Use `.part` files to avoid corrupted completed downloads.
- Avoid updating Compose state too frequently from playback progress; throttle progress updates.
- Keep mini-player state small.

Tafsir:

- Cache selected tafsir source list.
- Cache fetched tafsir by `verseKey:sourceId`.
- Do not fetch tafsir for all ayat eagerly.
- Expand/fetch only selected ayah.
- Keep LazyColumn stable keys.

Memorisation:

- Store records in Room, not giant DataStore strings, for scale and backup clarity.
- Use DataStore only for current active/conceal/repeat state if needed.
- Avoid writing progress on every tiny UI frame.
- Keep dashboard calculations in ViewModel/repository, not composables.

Reader:

- Do not add audio/download/memorisation state directly to every ayah if it causes full-list recomposition.
- Prefer maps keyed by `verseKey` and stable item keys.
- Keep action sheets separate from row state.

## 11. Remaining Qur'anic Threads Parity Gaps

Known remaining gaps after current MyVault state:

- Multiple tafsir source picker
- Tafsir source persistence
- Ayah play button
- Reciter picker
- Mini-player
- Speed controls
- Previous/next ayah playback
- Seek/skip controls
- Background playback notification
- Manual Audio Downloads sheet
- Foreground download queue
- Offline download delete/retry states
- Word audio and word timing features
- Memorise ayah sheet
- Repeat ayah modes
- Conceal ayah modes
- Memorisation dashboard
- Surah memorised/revision states
- Memorisation backup/restore
- Optional memorisation widget

## 12. Safe Integration Boundaries

MyVault-owned modules should be named around `quran` and stay inside the existing app:

- `data/quran/audio`
- `data/quran/memorization`
- `data/quran/tafsir`
- `ui/quran`

Do not introduce a second app database. Add MyVault Room tables only when required.

Do not import Qur'anic Threads navigation. Wire new screens into Islamic Corpus only.

Do not import Qur'anic Threads Supabase or backup managers.

Do not change Google Drive backup architecture except to add metadata entities/settings when implementation reaches those phases.

## 13. Final Recommendation

The safest path is:

1. Audio playback from ayah
2. Mini-player and notification
3. Manual audio downloads
4. Multiple tafsir sources
5. Memorisation persistence
6. Memorisation sheet
7. Memorisation dashboard

Keep downloaded audio excluded from backups. Back up only the user's settings, reciter choice, playback preferences, and memorisation data. This preserves the vault's recoverability without turning every backup and restore into a multi-gigabyte operation.
