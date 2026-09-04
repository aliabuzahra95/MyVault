# MyVault Android conservative obsolete-code cleanup

Cleanup date: 2026-09-04 AEST

Repository: `/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`

Branch: `frozen-design-master-port`

Remote: `git@github.com:aliabuzahra95/MyVault.git`

## 1. Outcome and safety boundary

The approved obsolete-code cleanup is complete through Batches 1–4 and safe Category B items B1–B3. The launcher-resource move in B4 was attempted locally, could not be visually verified because both available emulators were unstable, and was fully reverted before any commit.

No intentional application behavior, Room schema, migration, backup format, restore compatibility, historical rich-text reader, incoming Share path, or restored-navigation route was changed.

This cleanup is **not declared release-ready** because no Samsung device was connected for the required physical-device smoke test.

## 2. Recovery checkpoint

- Starting commit: `1e513a03089bf358c6068f453cf064b86cd6e8a6`
- Annotated tag: `pre-obsolete-code-cleanup-20260904`
- Tag object: `73b576b0d683332df52982f74bb085a164a115b3`
- Tag target: `1e513a03089bf358c6068f453cf064b86cd6e8a6`
- Remote verification: both the tag object and peeled target are present on `origin`.

Rollback to the exact pre-cleanup tree is therefore available through the tag.

## 3. Batch 1 — unused configuration, assets, APIs, and SDK guards

Commit: `d4e9fb8e92653471b93105af9f5ff781a576623c`

Message: `cleanup: remove unused config and APIs`

Removed:

- Unused custom BuildConfig copies: `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, and `GEMINI_API_KEY`, including their unused Gradle value plumbing.
- Unreferenced assets: `app/src/main/assets/quran-data.xml` and `app/src/main/assets/tajweed_manifest.json`.
- Dead `ComponentStyle.kt` / `vaultBorder` helper.
- Unused DAO methods: `BlockDao.deleteForNote`, `BlockDao.deleteTypesForNote`, and `FolderDao.updateParent`.
- Unused methods in formatting, narration, Qur'an, attachment, backup, folder, note, relationship-graph, and Google Drive repository/service surfaces identified by the approved audit.
- Obsolete `minSdk` guards in `DriveSyncWorker`, `NarrationPlayerManager`, `NoteNarrationTextPreparer`, `QuranAudioDownloadService`, and `QuranAudioPlayer`.

Conservative exception:

- `CourseDao` was left completely untouched. Although the audit listed method-level candidates, this DAO is intertwined with protected legacy course storage/conversion and delete cleanup. Leaving the entire DAO intact avoided crossing the Category C boundary.

Validation:

- Debug Kotlin compilation passed.
- 251 unit tests passed; 0 failures, 0 errors, 0 skipped.
- Android lint passed with 0 errors, 63 warnings, and 2 hints (9 fewer warnings than the audit baseline).
- Debug APK passed.
- Release/R8 APK passed.
- Room schema and migration files were unchanged.
- Unsigned release APK: 53,674,330 bytes.

## 4. Batch 2 — superseded whole UI files

Commit: `1c74e031128ead2c113ee722b17741ea9154e242`

Message: `cleanup: remove superseded UI files`

Removed files:

- `app/src/main/java/com/myvault/app/ui/screens/PlaceholderScreen.kt`
- `app/src/main/java/com/myvault/app/ui/screens/MemoriseShellScreen.kt`
- `app/src/main/java/com/myvault/app/ui/quran/QuranAyahCard.kt`
- `app/src/main/java/com/myvault/app/ui/quran/QuranSurahTestSheet.kt`

Direct orphan removed:

- `QuranAyahReflectionCard` from `QuranReflectionSheets.kt`; its sole caller was the deleted `AyahRow` path.

Retained active replacements:

- `FrozenMemoriseScreen`
- `FrozenMemoriseSession`
- `FrozenQuranAyah`
- `QuranReaderSurface`
- `ReflectionEditorSheet`

Validation:

- Debug Kotlin compilation passed.
- Focused Qur'an, Memorise, reflection, navigation, compatibility, and dashboard contract tests passed.
- Full 251-test suite passed; 0 failures, 0 errors, 0 skipped.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Release APK size was unchanged, consistent with R8 already excluding the dead UI paths.

## 5. Batch 3 — dead file-local UI branches and ViewModel APIs

Commit: `bc477f6f8ffd2be7b658dd0a37e9ed85f2c4416d`

Message: `cleanup: remove dead local UI branches`

Removed approved file-local branches and their direct private orphans from:

- `SettingsScreen.kt`: `LegacySettingsScreen`, `FrozenSelectionRow`, and the private legacy-only card/dialog/row cascade.
- `CoursesScreen.kt`: `CourseSwitcher`, `CourseActionMenu`, `EmptyCoursesScreen`, and `CourseMenuItem`.
- `EditorScreen.kt`: local `runStructureOnlyLocally`, `EditorTopActionButton`, `EditorBreadcrumb`, `TextColorDialog`, and `EditorActionRow`.
- `HomeScreen.kt`: the audited unused Study, empty-state, attachment, reflection, inline-search, and pinned-sheet branches, plus their private result/icon cascade.
- `LibraryScreen.kt`: the audited unused mobile-web, search-overlay, annotation/reference, grid, and empty-state branches plus their direct private cascade.
- `ReadingScreen.kt`: `ListenModeChoice`, `AttachmentReadingPreview`, and its direct preview/card/bitmap cascade.
- `AttachmentViewerScreen.kt`: local `saveDragHighlight` and `openTextBoxAt`.
- `VaultRichText.kt`: wrapper-only `applyBulletList` and `applyNumberedList`; active transform implementations remain.

Removed audited ViewModel methods:

- `HomeViewModel.createNoteFromSharedText`
- `LibraryViewModel.importFile` (plural active import methods remain)
- The audited unused `NoteViewModel` block/narration/formatting methods
- The audited unused `MemoriseViewModel` selection/status methods
- `QuranReaderViewModel.increaseArabicFont`, `decreaseArabicFont`, `setMemorizationRepeatMode`, and `stopMemorizationRepeat`
- `SettingsViewModel.checkGoogleDriveUpdates`

Validation:

- Protected database, backup, restore, navigation, and Share files had no diff.
- Debug Kotlin compilation passed.
- Focused Settings, dashboard, Study, Library, Courses, editor, rich-text, PDF, narration, and compatibility tests passed.
- Full 251-test suite passed; 0 failures, 0 errors, 0 skipped.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Release APK size was unchanged, consistent with prior R8 exclusion.

## 6. Batch 4 — unreachable Android proxy-sync scaffold

Commit: `fe0460d6713ae77e0eb1e61811a6a328f2793b87`

Message: `cleanup: remove unused proxy sync client`

Removed files:

- `app/src/main/java/com/myvault/app/data/sync/SyncRepository.kt`
- `app/src/main/java/com/myvault/app/data/sync/SyncApiClient.kt`
- `app/src/main/java/com/myvault/app/data/sync/SyncDtos.kt`

Removed Android build configuration:

- `SYNC_PROXY_URL`
- `SYNC_PROXY_TOKEN`
- Corresponding empty `MYVAULT_SYNC_PROXY_URL` and `MYVAULT_SYNC_PROXY_TOKEN` Gradle properties.

Explicitly retained unchanged:

- `GoogleDriveIncrementalSyncRepository`
- `DriveSyncWorker`
- `GoogleDriveRestoreController`
- Root `sync-worker/`

Validation:

- Obsolete proxy symbols and fields had no remaining Android references.
- Debug Kotlin compilation and Hilt generation passed.
- Focused Google Drive, restore-controller, and backup-compatibility tests passed.
- Full 251-test suite passed; 0 failures, 0 errors, 0 skipped.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Release APK size was unchanged, consistent with R8 already excluding the unreachable cluster.

## 7. Category B1 — preview/sample isolation

Commit: `7f9f19ca96df15d633b1e4b50384ddc19598efa8`

Message: `cleanup: isolate preview data from production`

Changes:

- Moved the production-used `AttachmentSample` data class into `ui/model/AttachmentSample.kt`.
- Moved Home preview functions and fixtures to `app/src/debug/.../HomeScreenPreviews.kt`.
- Removed obsolete sample-only `Pass4Samples.kt` content.
- Retained other small component-local Compose previews because they remain useful design tools and moving all of them would be a broad refactor outside this cleanup.

Validation:

- Both debug and release Kotlin source sets compiled.
- Full 251-test suite passed.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Release APK decreased by 16,384 bytes because Home preview fixtures no longer compile into production.

## 8. Category B2 — dead Qur'an memorisation callback plumbing

Commit: `3d12f91f7bd4332628632d3ac0211a49d681fdec`

Message: `cleanup: remove dead Quran memorisation callbacks`

Changes:

- Removed all eleven audited unused callback parameters from `QuranShellScreen` and their matching `VaultNavHost` arguments.
- Removed the ten `QuranReaderViewModel` methods that became orphaned.
- Kept `startMemorizingAyah`, because the active “Memorise from here” transition still invokes it directly before selecting the Memorise tab.

Validation:

- Focused Qur'an, Memorise, memorise-from-here, whole-Surah, score/status, navigation, and resume tests passed.
- Full 251-test suite passed.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Protected route definitions were unchanged.

## 9. Category B3 — canonical Qur'an corpus cache

Commit: `e361f33a406924adb5ca33342d0ef9e71ccfcb1a`

Message: `refactor: share canonical Quran text cache`

Changes:

- `QuranTextRepository` is now the only Android source that opens and parses `qpc_hafs.json`.
- The singleton repository lazily builds and caches the selector’s verse-key/Arabic-text map from the already cached canonical JSON object.
- `QuranSelectorSheets` no longer opens or parses the asset independently.
- Loading remains deferred until the selector is visible.
- Added `QuranAyahSearchIndexTest` for verse-key identity and trailing Arabic verse-number stripping.

Corpus evidence:

- 114 surahs.
- Exactly 6,236 ayat.
- 0 malformed verse keys.
- Every per-surah asset count matches `QuranCatalog.kt`.
- `2:255` is present and non-empty.
- The source asset was not modified.

Validation:

- Focused canonical-index, English/Arabic reference search, selector/navigation, and reader-contract tests passed.
- Full suite increased to 252 tests; 0 failures, 0 errors, 0 skipped.
- Android lint passed with 0 errors, 63 warnings, and 1 hint.
- Debug APK and release/R8 APK passed.
- Release APK size was unchanged.

## 10. Category B4 — launcher resource qualifier deferred

Lint correctly reports that `mipmap-anydpi-v26` is redundant with `minSdk = 29`. The two adaptive-icon XML files were copied unchanged to an unversioned `mipmap-anydpi` folder in an uncommitted test and the debug APK built successfully.

Runtime verification was not trustworthy:

- `Medium_Phone_API_36.1` installed the APK, but its System UI became repeatedly unresponsive while opening the launcher drawer.
- `Pixel_9_Pro` installed the APK, but emulator screenshots were black even though Android reported the launcher focused and the display awake.

Because the instruction required visual device/emulator icon verification, the relocation was completely reverted. The tracked tree returned exactly to `e361f33`; no B4 code/resource commit was made. This remains a future low-risk candidate when a stable emulator or connected device is available.

## 11. Protected Category C code retained

The following remain intentionally intact:

- Every Room migration, database version, `ALL_MIGRATIONS`, exported schema, entity/table identity, and migration-chain test.
- Legacy course tables and conversion: `course_folders`, `course_notes`, `course_sticky_notes`, bulk legacy reads/writes, converted-course behavior, backup/restore compatibility, and active `deleteCourse` cleanup.
- Old backup readers/writers, optional/defaulted fields, retired metadata handling, old attachment forms, manifest and geometry validation, source backlinks, and old settings values.
- Old rich-text fields/readers and fallbacks: `rich_text`, `rich_html`, `rich_body`, `parseLegacyRichBodyText`, HTML, block, and plain-text fallbacks.
- Theme/settings fallback compatibility.
- Restored navigation routes `home`, `folder/{folderId}`, and `FolderView` registration.
- Incoming Share handling, `MainActivity` entry behavior, and `RichImportParser`.
- One-time legacy safety-backup cleanup.
- Framework/runtime entries: application, launcher, FileProvider, data-extraction rules, services, WorkManager/Hilt wiring, and active Compose screens.

## 12. Remaining Category D / deliberately deferred items

- Root `sync-worker/` deployment source.
- `supabase/` deployment-linked source.
- Pre-existing untracked screenshots, artifacts, `.idsig` files, release evidence, and unknown non-source artifacts.
- Launcher qualifier relocation, pending stable runtime icon verification.
- Remaining component-local Compose previews, intentionally retained.
- `CourseDao` method-level cleanup, deliberately deferred to avoid legacy compatibility risk.

## 13. Metrics

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Tracked files, including this report | 606 | 598 | -8 |
| Kotlin/Java source files | 248 | 241 | -7 |
| Kotlin/Java source lines | 69,157 | 62,878 | -6,279 |
| Cleanup code/test diff, excluding this report | — | 135 additions / 8,020 deletions | Net -7,885 |
| Whole tracked files removed | 0 | 11 | +11 removed |
| Unit tests | 251 | 252 | +1 regression test |
| Lint | 0 errors, 72 warnings, 2 hints | 0 errors, 63 warnings, 1 hint | -9 warnings, -1 hint |
| Unsigned release APK | 53,687,555 bytes | 53,657,946 bytes | -29,609 bytes |
| Release/R8 | Passed | Passed | Passed after every committed batch |

The size reduction is small because R8 was already removing much of the unreachable bytecode. The main benefit is a smaller, clearer, and safer-maintained source tree.

## 14. Final regression evidence and limits

Automated evidence covers Dashboard, Study, Library, Courses, notes/editor/rich text, PDF/attachments, Qur'an, Memorise, Settings/theme compatibility, navigation contracts, Google Drive/restore-controller behavior, backup compatibility, formatting, narration-related source compilation, Recently Deleted source compilation, and all application dependency-injection/build paths.

Final automated state at `e361f33`:

- Debug Kotlin compilation: passed.
- Unit tests: 252 passed; 0 failures, 0 errors, 0 skipped.
- Android lint: passed; 0 errors, 63 warnings, 1 hint.
- Debug APK: passed.
- Release APK with R8/resource shrinking: passed.
- Room schema/migration identity: unchanged.
- Backup format: unchanged.
- Protected Category C files: retained.

Limits:

- No Samsung device was connected, so cold launch, unlock, real Dashboard/Study/Library/Course/Qur'an/Memorise/Settings navigation, Backup trigger, and incoming Share were not physically smoke-tested.
- No destructive Restore was performed.
- Google Drive network/account behavior was not changed and was not exercised against a live account during this cleanup.
- Therefore this is a verified source/build cleanup checkpoint, **not a release-ready physical-device sign-off**.

## 15. Issues encountered

- Android Studio’s bundled Java 25 runtime was incompatible with this Gradle build; validation used the installed JBR 21 runtime as required.
- Full lint initially needed bounded worker/JVM settings to avoid host-memory pressure; complete lint then passed for each batch.
- The two available emulators were unsuitable for trustworthy launcher-icon visual verification, so B4 was reverted rather than accepted on build evidence alone.
- Existing untracked artifact/release files were continuously excluded and never staged or modified.

## 16. Commit sequence and push status

1. `d4e9fb8` — unused configuration and APIs
2. `1c74e03` — superseded UI files
3. `bc477f6` — dead local UI branches
4. `fe0460d` — unused Android proxy-sync client
5. `7f9f19c` — preview data isolated from production
6. `3d12f91` — dead Qur'an memorisation callbacks
7. `e361f33` — canonical Qur'an text cache

Every code checkpoint above was pushed successfully to `origin/frozen-design-master-port` before the next batch began.
