# MyVault Commercial Stabilisation Audit

Baseline audited: `/Users/aliah/Desktop/MyVault - Complete BACKUP BEFORE AI`

Safety copy created: `/Users/aliah/Desktop/MyVault - Commercial Audit Safety Backup`

AI/RAG status: the failed Ask MyVault/RAG implementation is not present in this baseline. Existing note-level AI and narration code remains because it existed in the backup baseline.

## 1. Build Health

Commands run from the baseline folder:

| Check | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin` | Pass |
| `./gradlew :app:assembleDebug` | Pass |
| `./gradlew testDebugUnitTest` | Pass |
| `./gradlew :app:lintDebug` | Fails, 3 errors, 48 warnings, 4 hints |

Build blockers:

- `AttachmentViewerScreen.kt:166-168` calls `PdfViewerFragment.toolboxView`, a restricted AndroidX PDF API. Lint treats this as an error.

Important warnings:

- Google Sign-In API is deprecated in `GoogleDriveIncrementalSyncRepository.kt`.
- Biometric prompt uses deprecated `setDeviceCredentialAllowed`.
- Many icon references need AutoMirrored variants.
- `LocalClipboardManager` is deprecated in editor/AI/Quran screens.
- AndroidX PDF listener parameter names mismatch supertype names.
- Native Azure and AndroidX libraries are packaged unstripped, increasing build size.
- Lint reports a provider export warning around AndroidX Startup manifest merge.
- Lint reports a draw allocation in `AttachmentViewerScreen.kt`.

Dependency notes:

- Lint reports newer versions for Gradle, Android Gradle Plugin, Kotlin, Compose BOM, Core, Lifecycle, WorkManager, Firebase BOM, Google Services, Material, Play Services Auth, and Hilt.
- Do not mass-upgrade dependencies before stabilisation. Safe first upgrades are patch-level only after lint/build fixes.
- Avoid AGP 9.x/Kotlin major upgrades until the app has stronger regression tests.

## 2. Large Kotlin File Report

Files over 3000 lines:

| File | Lines | Risk | Recommendation |
| --- | ---: | --- | --- |
| `QuranShellScreen.kt` | 3895 | Very high | Split later by reader surface, search overlay, audio sheets, bookmarks, reflections, memorisation UI. Do not split before Quran smoke tests exist. |
| `EditorScreen.kt` | 3018 | Very high | Split later by editor core, AI sheets, attachment previews, table editor, color dialogs, import helpers. Preserve current state contract first. |
| `AttachmentViewerScreen.kt` | 3004 | Critical | Split soon after lint fix: PDF fragment bridge, PDF overlays, dialogs, image/document viewers, preview cache utilities. |

Files over 2000 lines:

| File | Lines | Risk | Recommendation |
| --- | ---: | --- | --- |
| `LibraryScreen.kt` | 2100 | High | Split into root/folder/archive/search/dialog components after ViewModel risks are understood. |

Files over 1000 lines:

| File | Lines | Risk | Recommendation |
| --- | ---: | --- | --- |
| `HomeScreen.kt` | 1769 | High | Split action dialogs, selection bar, pinned sheet, inline search, attachment card UI. |
| `BackupRepository.kt` | 1719 | Critical | Keep behavior stable. Extract JSON codecs and validators only after backup roundtrip tests. |
| `NoteAiRepository.kt` | 1565 | High | Existing feature. Do not expand. Later split providers, chunking, streaming, HTML cleanup. |
| `VaultNavHost.kt` | 1452 | High | Split navigation graph only after route constants and smoke tests. |
| `ReadingScreen.kt` | 1354 | Medium-high | Split note body, source refs, backlinks, narration/read-along UI. |
| `NoteViewModel.kt` | 1232 | High | Split only with compatibility layer. It mixes note CRUD, rich text, tables, AI, narration, attachments, links. |
| `SettingsScreen.kt` | 1142 | Medium-high | Split backup settings, Azure settings, AI login, deleted items, storage/security sections. |

Files over 500 lines:

- `NoteRepository.kt` 786
- `QuranReaderViewModel.kt` 713
- `CoursesScreen.kt` 710
- `GoogleDriveIncrementalSyncRepository.kt` 710
- `NarrationPlayerManager.kt` 709
- `VaultRichText.kt` 706
- `LibraryViewModel.kt` 699
- `AiPromptBuilder.kt` 689
- `FolderViewScreen.kt` 648
- `MemoriseShellScreen.kt` 638
- `FolderTreeRow.kt` 560
- `AskAiScreen.kt` 549

Large file rule:

- Do not split by line count alone.
- First fix build/lint, then add tests around backup, navigation, PDF opening, and note editing.
- Split screens into pure UI components before splitting ViewModels or repositories.

## 3. Architecture Risk Map

Highest-risk areas:

1. `AttachmentViewerScreen.kt`: PDF fragment, native view callbacks, overlays, dialogs, preview cache, annotations, zoom canvas, text extraction all in one file.
2. `BackupRepository.kt`: export, restore, validation, JSON encoding/decoding, file restore, emergency backup, and preference restore in one class.
3. `VaultNavHost.kt`: route registration, state collection, root shell, bottom navigation, argument parsing, and screen wiring in one file.
4. `NoteViewModel.kt`: editor state, rich text, note tables, AI, selected text AI, narration, attachment hydration, source references.
5. `LibraryViewModel.kt`: folder tree, files, annotations, tags, import/replace/export, secondary hydration.
6. `QuranShellScreen.kt`: reader, reflections, memorisation, audio download, audio player, search, bookmarks, selectors.
7. `GoogleDriveIncrementalSyncRepository.kt`: Drive auth, folder layout, upload/download API, manifests, file hashing, conflict handling.

Architecture boundaries are workable but not commercial-clean. The app is feature-rich, but too much behaviour lives in huge UI files and broad repositories.

## 4. Backup and Restore Integrity Matrix

| Data type | Backed up | Restored | Relationships restored | Files restored | Tested | Risk | Required fix |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Study folders | Yes | Yes | Parent hierarchy validated | n/a | Partial | Medium | Add nested folder roundtrip test. |
| Folder descriptions | Yes | Yes | Yes | n/a | No | Low | Add backup assertion. |
| Folder sticky notes | Yes | Yes | folderId filtered | n/a | No | Medium | Add roundtrip test. |
| Notes | Yes | Yes | folder/parent note sanitized | n/a | Partial | Medium | Add note tree roundtrip test. |
| Rich text blocks | Yes | Yes | noteId filtered | n/a | No | High | Add rich-text export/restore test. |
| Tables | Yes | Yes | noteId filtered | n/a | No | Medium | Add table roundtrip test. |
| Note versions | Yes | Yes | noteId filtered | n/a | No | Medium | Add version roundtrip test. |
| Tags and note tags | Yes | Yes | noteId filtered | n/a | No | Medium | Add tag roundtrip test. |
| Attachments | Yes | Yes | note/library folder filtered | Yes if file exists | Partial | High | Add real file roundtrip test. |
| Library folders/files | Yes | Yes | folder/file links filtered | Yes | Partial | High | Add library file roundtrip test. |
| PDF reading progress | Yes | Yes | attachmentId filtered | n/a | No | Medium | Add PDF progress restore test. |
| PDF annotations | Yes | Yes | attachment/folder IDs filtered | n/a | Partial | High | Add annotation roundtrip with bounds. |
| Source backlinks | Yes | Yes | note/attachment/annotation filtered | n/a | No | Medium | Add backlink roundtrip test. |
| Courses | Yes | Yes | root folder and last note validated | n/a | No | Medium | Add course roundtrip test. |
| Course folders | Yes | Yes | courseId filtered | n/a | No | Medium | Add nested course test if nested model exists. |
| Course notes | Yes | Yes | courseId/folderId filtered | n/a | No | Medium | Add course note roundtrip test. |
| Course sticky notes | Yes | Yes | courseId filtered | n/a | No | Medium | Add sticky note test. |
| Concept cards | Yes | Yes | courseId filtered | n/a | No | Medium | Add concept card test. |
| AI conversations/messages | Yes | Yes | note/conversation IDs filtered | n/a | No | Low-medium | Existing baseline feature, add basic roundtrip or exclude intentionally later. |
| Knowledge tags/links | Yes | Yes | target IDs filtered | n/a | No | Medium | Add mixed target roundtrip test. |
| Quran recent locations | Yes, via settings | Yes | n/a | n/a | No | Medium | Add preferences backup test. |
| Quran memorisation | Yes, via settings | Yes | verse key validation | n/a | No | High | Add memorisation backup test. |
| Quran reflections | Indirect via notes/folders | Indirect | note links only | n/a | No | Medium | Confirm reflection storage path. |
| Azure settings | Yes, via settings | Yes | n/a | n/a | No | High | Decide whether API key should be included in backups. |
| Narration cache | No | No | n/a | No | No | Low | Keep excluded unless explicitly desired. |
| App preferences | Yes | Yes | n/a | n/a | No | High | Add preferences roundtrip test. |

Backup strengths:

- Creates emergency backup before restore.
- Validates manifest, IDs, folder hierarchy, course links, notes, blocks, attachments, PDF progress, annotations, and memorisation records.
- Skips missing attachment files safely.
- Uses a database transaction for core database restore.

Backup risks:

- Restore is merge-based, not clean-replace. This protects existing data but can leave stale data if a backup intentionally removed something.
- Preference restore occurs after the database transaction, so database restore and preferences restore are not atomic together.
- Actual full roundtrip tests are missing.
- Azure API key appears to be included in backed-up settings. This may be intended for a private app, but for commercial-grade privacy it needs an explicit decision.

## 5. Room and Database Audit

Status:

- Room database version: 20.
- Schema exports exist for versions 16, 17, 18, 19, 20.
- Migration chain test verifies no registered migration gaps from 1 to 20.
- Many important indexes exist on notes, folders, attachments, PDF annotations, tags, course notes, and source backlinks.

Risks:

- Entities mostly do not declare foreign keys. The app enforces relationships manually. This allows flexible restore but increases orphan risk.
- DAOs often expose whole-table flows and whole-table reads. This is acceptable for small vaults but risky for large user data.
- Some cascading delete/cleanup is implemented in repositories, not database constraints. Missing one call path can create orphans.
- Migration tests verify chain continuity, not schema correctness or data preservation.

Recommended database improvements:

- Add DAO orphan-audit queries before adding foreign keys.
- Add indexes only where query evidence shows scans or repeated large loads.
- Add migration tests that seed v16/v17/v18 schemas with representative data and verify v20 content.
- Keep manual relationship filtering during restore until tests are stronger.

## 6. Performance Risk List

Top risks:

1. Huge Compose screens increase recomposition blast radius.
2. `QuranShellScreen.kt` performs many remembered calculations and large list UI in one file.
3. `AttachmentViewerScreen.kt` mixes AndroidX PDF view, overlays, preview generation, and annotation interaction.
4. PDF overlay allocates during draw/layout according to lint.
5. Large repository whole-table reads can become slow as data grows.
6. `LibraryViewModel.kt` combines multiple broad flows and secondary hydration layers.
7. Thumbnail/PDF preview work must stay off main thread consistently.
8. Google Drive sync builds and zips many files in one broad repository.
9. Narration player manager is large and stateful.
10. Navigation collects many ViewModels and state flows in one host.

Safe optimisations:

- Fix lint draw allocation in PDF overlay.
- Ensure lazy list keys exist everywhere important.
- Move repeated folder flattening/path calculations into memoized repository-level snapshot builders where needed.
- Split visual components without changing state ownership.
- Add lightweight timing logs around backup/export, Drive sync, PDF open, Quran search.
- Add restore/export roundtrip tests before changing backup internals.
- Make large file operations report progress and never block the main thread.
- Use primitive Compose state types where lint suggests.
- Avoid loading full attachment previews unless visible.
- Avoid dependency major upgrades until lint and tests are green.

## 7. UI and Compose Consistency

Findings:

- The app has a premium component language (`VaultModal`, `VaultTopBar`, `FloatingActionMenu`, theme tokens).
- Several screens still carry large one-off UI implementations.
- Settings has several distinct modal/dialog patterns and is over 1100 lines.
- PDF viewer has custom overlays and dialogs in one file.
- Quran reader has many sheets and overlays in one file.
- Some composables have modifier parameters out of recommended order.
- Several old icon APIs should use AutoMirrored variants.

Recommendation:

- Standardise dialogs through `VaultModal` after behaviour tests.
- Extract screen subcomponents, but do not redesign visual language.
- Start with lint-safe UI cleanups before design polish.

## 8. Navigation Audit

`VaultNavHost.kt` is 1452 lines and mixes:

- route definitions
- root shell
- bottom nav
- ViewModel collection
- pending deep/open actions
- per-screen route arguments
- screen wiring
- helper formatting functions

Proposed split later:

- `navigation/VaultRoutes.kt`
- `navigation/VaultNavHost.kt`
- `navigation/StudyNavGraph.kt`
- `navigation/LibraryNavGraph.kt`
- `navigation/QuranNavGraph.kt`
- `navigation/EditorNavGraph.kt`
- `navigation/SettingsNavGraph.kt`
- `navigation/RootShell.kt`

Do not split until route smoke tests or a manual screen checklist exists.

## 9. Security and Privacy

Strengths:

- `android:allowBackup="false"`.
- Android data extraction rules exclude files, databases, preferences, and external data.
- FileProvider is not exported.
- Drive service is not exported.

Risks:

- `local.properties` contains an OpenAI API key in the baseline folder. Treat this key as compromised if the folder is shared or committed.
- Supabase anon key is in `gradle.properties`. This is expected for anon keys, but project policy must rely on RLS and storage policies.
- Azure Speech API key is stored in preferences and likely backed up. Decide whether this is acceptable.
- `NoteAiRepository` writes AI debug traces under app files in some paths. Make sure debug traces are disabled or gated for release.
- Lint flags AndroidX Startup provider as potentially exported by default; manifest should explicitly review provider merge output.
- Google Drive auth uses deprecated Google Sign-In APIs.

## 10. Testing Gaps

Existing tests:

- Migration chain continuity.
- Backup attachment missing-file helper behaviour.
- Note relationship graph.
- PDF annotation repository helper behaviour.
- Rich text deletion helper behaviour.

Missing critical tests:

- Full backup export/restore roundtrip.
- Restore with nested folders, notes, tags, blocks, tables, attachments, PDF annotations, courses, Quran memorisation/preferences.
- Database migration data preservation.
- Repository transaction cleanup/orphan tests.
- PDF open smoke test.
- Library import/open/export tests.
- Settings persistence tests.
- Navigation smoke tests.

## 11. Staged Implementation Plan

Critical:

1. Fix lint-blocking PDF restricted API.
   - Files: `AttachmentViewerScreen.kt`.
   - Benefit: commercial build hygiene.
   - Rollback: revert single PDF bridge change.
   - Test: compile, assemble, lint, open PDF.

2. Remove or secure exposed local OpenAI key.
   - Files: `local.properties`, release process.
   - Benefit: prevents secret leakage.
   - Rollback: restore local-only config from private source.
   - Test: AI/narration config still works when key supplied privately.

3. Add full backup roundtrip tests.
   - Files: tests around `BackupRepository`, database builders.
   - Benefit: protects user data before refactors.
   - Rollback: remove tests only, no app behaviour change.
   - Test: `testDebugUnitTest`.

High:

4. Backup matrix fixes and restore edge cases.
   - Files: `BackupRepository.kt`, tests.
   - Benefit: safer restore and clearer missing data behaviour.
   - Rollback: revert repository and tests together.
   - Test: roundtrip tests, missing file tests.

5. Extract `BackupJsonCodecs` and `BackupValidators`.
   - Files: new backup package files plus `BackupRepository.kt`.
   - Benefit: reduces critical 1719-line class without changing format.
   - Rollback: restore previous repository.
   - Test: backup verification and roundtrip tests.

6. Split `AttachmentViewerScreen.kt` after lint fix.
   - Files: PDF bridge, PDF overlays, dialogs, image/document viewers.
   - Benefit: reduce highest UI risk.
   - Rollback: revert split commit.
   - Test: PDF open, image open, document text, annotation panel.

7. Add navigation route constants and manual smoke checklist.
   - Files: `VaultRoutes.kt`, docs/checklist.
   - Benefit: prepares safe nav split.
   - Rollback: revert constants.
   - Test: navigate all main screens.

Medium:

8. Split `VaultNavHost.kt` into graph files.
9. Extract UI-only subcomponents from Home, Library, Settings, Reading.
10. Move Note AI HTML/chunk utilities out of `NoteAiRepository`.
11. Add orphan audit queries and repository cleanup tests.
12. Standardise remaining modals/dialogs on `VaultModal`.

Low:

13. AutoMirrored icon migration.
14. Obsolete SDK check cleanup.
15. KTX style cleanup.
16. Monochrome launcher icon.
17. Modifier parameter order cleanup.

## 12. Recommendation

Do not start broad refactoring yet.

Start with:

1. Fix lint hard errors.
2. Add backup roundtrip tests.
3. Fix security hygiene around local secrets.
4. Then split `BackupRepository.kt` carefully.
5. Then split `AttachmentViewerScreen.kt`.

This order protects user data before structure work.
