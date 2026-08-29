# MyVault Android Current-State Production Audit

Audit date: 2026-08-29 AEST
Repository: `/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`
Scope: inspection, build, tests, runtime measurement, documentation, and prioritisation only
Application source was not modified during this audit.

## Remediation update - 2026-08-29 AEST

The focused Phases 1-3 remediation was completed after this audit. The original findings below remain as the historical audit baseline; their current disposition is:

| Finding | Current disposition |
|---|---|
| P1-01 Dashboard-start root navigation | **RESOLVED**. Root destinations now use explicit Dashboard-rooted navigation. Dashboard remains the real graph start; no hidden Home trampoline was added. |
| P2-02 legacy Study/Course folder route | **RESOLVED for active callers**. Explorer and Search reveal folders in the approved Study/Course hierarchy. The generic route remains only as an isolated restored-navigation compatibility fallback. |
| P1-02 Drive sync metadata shared across accounts | **RESOLVED IN SOURCE/AUTOMATION**. Sync timestamps are device-local and keyed by normalized Google account email. Unknown legacy globals are not assigned to an account. Live A/B cloud acceptance remains blocked by the absence of disposable authenticated accounts. |
| P3-01/P3-02 Search paths | **RESOLVED IN SOURCE/AUTOMATION**. Note results build full real folder ancestry and use a clean `Study / Unfiled` fallback. |
| P3-03 Reflections Hub chrome | **ALREADY RESOLVED IN CURRENT SOURCE**. The current screen has one Back control; no remediation change was required. |
| P2-01 incoming-share first frame | **STILL OPEN**. It remains the next isolated startup-routing task and was outside Phases 1-3. |

Remediation starting checkpoint: `24c078cb45cc312217c47323e6526368859008c7`, protected by pushed tag `current-state-remediation-start-20260829`.

Current remediation evidence is documented in `docs/MYVAULT_CURRENT_STATE_REMEDIATION.md`.

## 1. Executive decision

The current branch builds and its automated suite is green, but the app is **not ready for release acceptance**.

Two high-priority findings require remediation before the blocked runtime matrix can be completed:

1. Dashboard is now the true navigation start destination, but several root-navigation callbacks still try to pop to the old `home` destination. From a normal Dashboard launch, Explorer selections for Study, Library, Courses, Qur'an, and Memorise do not open their destinations. Dashboard Qur'an Continue and several related return/navigation callbacks are affected by the same assumption.
2. Google Drive's last-synced manifest timestamp is global rather than scoped to the selected Google account. Changing accounts clears the displayed email but does not reset or partition this timestamp. Conflict and "up to date" decisions can therefore be made using another account's sync history.

The first issue was reproduced on both emulator and physical hardware and blocked normal-shell runtime exercise of several feature areas. Those areas are reported as **BLOCKED/NOT REVERIFIED**, never as PASS merely because their code or older documentation exists. Physical testing also found that Study and Course folders opened from Explorer still use an older generic folder presentation instead of the approved compact hierarchy screen.

## 2. Evidence vocabulary

| Status | Meaning |
|---|---|
| PASS - RUNTIME | Exercised in the currently installed APK during this audit |
| PASS - AUTOMATED | Covered by a currently passing unit/contract test, but not necessarily exercised on a device |
| SOURCE PRESENT | Current source contains the capability and wiring; runtime behaviour was not proved |
| FAIL - RUNTIME | Reproduced failure in the currently installed APK |
| BLOCKED | Could not be reached or completed because of an identified blocker or missing test environment |
| NOT TESTABLE | Required external account, credentials, media, fixture, physical sensor, or device was unavailable |
| DEFERRED | Intentionally outside the implemented product or release contract |

## 3. Git and build baseline

| Item | Result |
|---|---|
| Branch | `frozen-design-master-port` |
| Starting/current HEAD | `24c078cb45cc312217c47323e6526368859008c7` (`Make Dashboard the true startup destination`) |
| Remote | `git@github.com:aliabuzahra95/MyVault.git` |
| Local versus `origin/frozen-design-master-port` | `0 ahead / 0 behind` at audit start |
| Local `main`, remote `main`, current branch | All pointed to the same commit at audit start |
| Recoverable tag | `current-state-audit-start-20260829`, annotated and pushed; peeled commit is the starting HEAD |
| Tracked working tree | Clean before the audit |
| Pre-existing untracked files | Numerous `artifacts/` captures and release `.idsig` files; left untouched |
| Java | JetBrains Runtime 21.0.11 |
| Build command | Complete unit suite + `lintDebug` + `assembleDebug` |
| Build result | PASS |
| Unit result | 408 tests in 68 suites; 0 failures, 0 errors, 0 skipped |
| Lint result | 0 errors, 64 warnings, 2 hints |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Debug APK SHA-256 | `2dd1d51de53e7a3d0a973188e3da45e8f7ff83cee3ff72449fb1c00ceb1704d7` |
| Debug APK size | 144 MB |
| Package | `com.myvault.app` |
| Version | `0.1.0` (`versionCode` 1) |
| SDK range | min 29, target 36 |
| Room database version | 29 |

Release signing is not configured in `app/build.gradle.kts`: Gradle's signing report reports `Config: null` for release. The audit did not build or sign a release artifact. This does not prove that the established external signing procedure is invalid; it means release identity was not reverified here.

The 64 lint warnings are non-blocking. They are dominated by dependency-version notices, Compose modifier-parameter ordering, and obsolete SDK checks. Cached Bouncy Castle dependency classes also produce trust-manager warnings; no app-source permissive trust manager was established by this audit. Review these as maintenance/supply-chain debt, not as a proven TLS bypass.

## 4. Device and runtime baseline

| Item | Result |
|---|---|
| Physical Android device | Samsung `SM-F966B`, serial `RFCY70CMWZR`, Android 16 / API 36 |
| Physical display | 1080 x 2520 at density 420, approximately 411 x 960 dp; font scale 1.0; locale en-AU |
| Accessibility state | TalkBack/touch exploration off; Bitwarden and FlashDim were the only enabled accessibility services |
| Emulator | `emulator-5554`, API 36 / Android 16, model `sdk_gphone64_arm64` |
| Emulator build | Current debug APK, package `com.myvault.app`, version 0.1.0 |
| Physical installed build | Production-signed `com.myvault.app`, version 0.1.0/code 1 |
| Physical APK SHA-256 | `65f39bbcc79d185510b4e02493aca0e466675acc6f9226f3c2f626d6c98c4d36` |
| Physical signing certificate | DN `CN=Ali`; SHA-256 `5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3` |
| Cold-start recordings | `/tmp/current-audit-startup.mp4` and `/tmp/current-audit-startup-contact.png` |
| Startup screenshots | Runtime screenshots under `/tmp/current-audit-*.png` |

The installed physical APK does not match the current debug APK, and no matching local APK was found among the recent local artifacts. Its exact source-commit provenance therefore cannot be cryptographically established. The audit did not replace it because the debug and production signatures differ and doing so would have required uninstalling or clearing the user's installed app.

Physical runtime evidence below applies to that installed signed artifact. Current source inspection independently confirms the same root-navigation behavior. No app data was cleared, deleted, restored, or modified beyond temporarily switching the existing workspace from Islamic Corpus to Personal and back.

## 5. Coverage total

The supplied audit request contains **376 individual checklist bullets**. All 376 were triaged against current source, current automated tests, and the runtime environment. They are represented below by subsystem and evidence status.

This number is not a claim that 376 device interactions passed. The root-navigation failure and unavailable physical/external environments prevented full runtime execution. The distinction is deliberate.

## 6. Severity summary

| Severity | Count | Meaning in this audit |
|---|---:|---|
| P0 | 0 | No currently proven active exploit, irreversible data loss, or security catastrophe |
| P1 | 2 | Critical navigation failure; high-risk Drive account-isolation defect |
| P2 | 2 | Incoming-share first-frame issue; legacy Study/Course folder presentation and route |
| P3 | 5 | Search path defects, Reflections Hub header, local-only pinned expansion, and release traceability debt |

Verification gates are listed separately and are not inflated into product-defect counts.

## 7. Proven regressions and issues

### P1-01: Dashboard-start root navigation is broken

**Evidence:** FAIL - RUNTIME and source-confirmed.

`VaultNavHost` correctly declares Dashboard as `startDestination`. Shared root selection still calls `popBackStack("home", false)` whenever the current route is not Home. Because Home is no longer in a normal startup back stack, that pop returns false and does not navigate anywhere.

Reproduced from the installed current APK:

- Dashboard -> Explorer -> Study: drawer closes; Dashboard remains.
- Dashboard -> Explorer -> Library: drawer closes; Dashboard remains.
- Dashboard -> Explorer -> Courses: drawer closes; Dashboard remains.
- Dashboard Qur'an Continue: Dashboard remains instead of opening the saved verse.

Source impact includes:

- all root modes selected by `selectRootMode`, including Qur'an and Memorise;
- Dashboard Qur'an Continue;
- Dashboard Reflection navigation;
- Note delete completion in Reading and Editing modes;
- Global Search Course result navigation;
- Reflections Hub -> exact Qur'an verse navigation.

**User impact:** Most major product destinations are inaccessible from the normal first screen. Several return callbacks can leave the user on a deleted or stale destination.

**Required fix boundary:** Replace the old Home-back-stack assumption with explicit root navigation that works when Dashboard is the graph start. Preserve Dashboard as the true first frame and preserve explicit Note/PDF/Qur'an/import destinations. Add runtime/navigation tests, not only source-string contract tests.

### P1-02: Google Drive sync metadata is not account-scoped

**Evidence:** Source-confirmed risk; live two-account test NOT TESTABLE.

`prepareSignInIntent()` signs out and clears `googleDriveAccountEmail`, but leaves `lastGoogleDriveManifestAt` and `lastGoogleDriveSyncAt` intact. `pushToDrive()` and `checkForRemoteUpdates()` compare the current account's remote manifest against the globally stored `lastGoogleDriveManifestAt`.

Drive folder/file IDs are rediscovered from the current account on each operation, which is correct. The manifest timestamp is the remaining account-isolation problem.

**Risk:** After A -> B account switching, account B can be described as up to date using account A's timestamp. A push can also bypass the intended remote-newer conflict guard if B's different backup has an older timestamp than A's last sync. This is a credible backup overwrite/conflict-detection risk even though no destructive live account test was performed.

**Required fix boundary:** Design a non-destructive, account-scoped sync metadata strategy or reset the comparison state safely on confirmed account change. Prove A -> B -> A discovery and conflict handling with disposable accounts before release.

### P2-01: Incoming share is a post-render navigation

**Evidence:** Source-confirmed; runtime cold-share launch not exercised.

The manifest supports incoming `ACTION_SEND` for text/plain, text/html, and text/markdown. `MainActivity` mounts the normal navigation host and performs the import asynchronously; it sets `pendingSharedNoteId` only after the note is created. Dashboard can therefore render before navigation to the imported note.

**User impact:** An incoming Send-to-MyVault launch is not guaranteed to display the explicit imported note as the first normal content frame.

**Required fix boundary:** Resolve the initial destination/import gate before mounting ordinary app content, while retaining safe process recreation and avoiding a fake screen overlay.

### P2-02: Study and Course folders still open a legacy folder screen

**Evidence:** FAIL - RUNTIME on the physical device.

Opening a Study folder or Course folder from Explorer enters the older generic `FolderView` presentation. It has a large empty header area, overlapping/redundant Menu and Back controls, blue filled folder icons, note timestamps, and a generic `My Vault` breadcrumb. This does not match the approved compact Corpus Browser or Course hierarchy language.

The folder contents and note routes remain functional: a Course Note opened the Stage 4 reader and Back returned to the correct Course folder. The defect is the route/presentation used for folder browsing, not proven data loss.

**Evidence:** `/tmp/myvault-current-audit-phone/34-course-folder-screen.png` and `/tmp/myvault-current-audit-phone/40-study-folder-screen.png`.

**Required fix boundary:** Route Explorer folder selections through the approved Stage 2/Stage 6 hierarchy presentations while preserving the existing folder IDs, CRUD, Course semantics, and Stage 4 Note route. Do not restyle the legacy screen as a competing third hierarchy implementation.

### P3-01: Global Search exposes only the immediate folder name

**Evidence:** Source-confirmed; current emulator did not reproduce vertical clipping.

The search query joins a note to its immediate folder and returns `folderName`; it does not construct a full nested path. Runtime results displayed metadata such as `Study / test` without clipping, but deep results cannot distinguish a complete hierarchy such as `Study / Parent / Child`.

### P3-02: Global Search renders a duplicate separator for unfiled notes

**Evidence:** FAIL - RUNTIME on the physical device.

An unfiled result is rendered as `Study / / Unfiled`. The older reported vertical clipping was not reproduced: normal result paths were fully visible on the phone. The remaining defects are path construction and the duplicate separator for the unfiled state.

**Evidence:** `/tmp/myvault-current-audit-phone/11-search-he.png`.

### P3-03: Reflections Hub has redundant navigation chrome

**Evidence:** FAIL - RUNTIME on the physical device.

The full Reflections Hub opens and displays real reflections, but its header shows both Menu and Back controls with excessive top whitespace. Tapping a reflection does not reach the verse because of P1-01.

**Evidence:** `/tmp/myvault-current-audit-phone/07-reflections-hub.png`.

### P3-04: Pinned-strip expansion is local-only

**Evidence:** Source and documentation confirmed.

`pinnedExpandedByMode` is stored in DataStore and controls display state, but is not represented in backup/restore. Content and pin semantics are unaffected. This remains low-impact compatibility debt unless cross-device UI expansion state is intentionally promoted into the backup contract.

### P3-05: Maintenance and release-process debt

**Evidence:** Build output.

Lint has 64 warnings and 2 hints; the release variant has no source-defined signing config; the app still reports version 0.1.0/versionCode 1. The installed signed APK could not be matched to a local artifact or source commit. None caused the observed runtime failures, but all should be reviewed before a formal store/release candidate is declared.

## 8. Startup, shell, Dashboard, and Explorer

### Startup

- PASS - RUNTIME: normal cold launch showed Dashboard with no visible Study, Library, or other MyVault destination first on both emulator and physical phone.
- PASS - SOURCE: Dashboard is the actual NavHost start destination, not a post-launch redirect.
- Startup `am start -W` total times across five emulator runs: 1054, 782, 662, 687, and 650 ms.
- Physical force-stop cold-start totals were 96 ms and 99 ms. A frame-by-frame recording confirmed Dashboard as the first normal application content frame.
- Physical evidence: `/tmp/myvault-current-audit-phone/47-cold-launch-dashboard.png`, `/tmp/myvault-current-audit-phone/47-cold-launch-dashboard.mp4`, and `/tmp/myvault-current-audit-phone/47-cold-launch-dashboard-contact.png`.
- NOT TESTABLE: lock/auth startup because Security lock is disabled and the audit did not change the user's setting.
- NOT SUPPORTED: no manifest intent filters currently expose external Note, PDF, or Qur'an deep links. Internal explicit navigation exists.
- FAIL/SOURCE: incoming share is resolved after the normal content host mounts, as described in P2-01.

### Dashboard

- PASS - SOURCE: paired Continue-card layout, PDF extension stripping, three-line PDF title area, Recent limit 4, Reflection limit 8, Reflection tap callback, View all callback, and compact Pinned strip exist.
- PASS - RUNTIME: Dashboard rendered and scrolled smoothly with real physical-device data, including one Qur'an Continue tile, up to eight Reflections with unclipped previews, and the compact Pinned strip.
- PASS - RUNTIME: 360, 390, 412, and 430 dp screenshots showed no horizontal overflow in the tested state.
- FAIL - RUNTIME: Qur'an Continue and individual Reflection taps do not leave Dashboard because of P1-01.
- PASS - RUNTIME: Reflections `View all` opens the full Reflections Hub.
- BLOCKED: no PDF continuation existed in the installed physical dataset, so paired-card balance and short/medium/long PDF titles were not runtime verified.
- BLOCKED: Light/Dark/OLED full dataset comparison was not completed.

Physical evidence: `/tmp/myvault-current-audit-phone/02-dashboard-clean.png`.

Responsive evidence:

- `/tmp/current-audit-dashboard-360dp-final.png`
- `/tmp/current-audit-dashboard-390dp-final.png`
- `/tmp/current-audit-dashboard-412dp-final.png`
- `/tmp/current-audit-dashboard-430dp-final.png`

### Explorer

- PASS - RUNTIME: Application section order is Dashboard, Search, Settings.
- PASS - RUNTIME: Knowledge section begins Qur'an, Memorise, Study, Library, Courses, followed by Favourites and Workspace Attachments.
- PASS - SOURCE: expanded-node keys are persisted and selected-node ancestors can be expanded.
- PASS - RUNTIME: workspace chooser switched Islamic Corpus -> Personal -> Islamic Corpus using existing state; the final state was restored to Islamic Corpus.
- FAIL - RUNTIME: root destination selection from Dashboard is inert due to P1-01.
- FAIL - RUNTIME: selecting a Study or Course folder from Explorer opens the legacy folder route described in P2-02.
- BLOCKED: root-screen CRUD synchronisation, count accuracy, restart expansion persistence, and complete long-tree comparison.
- Runtime evidence: `/tmp/myvault-current-audit-phone/03-explorer.png`, `/tmp/myvault-current-audit-phone/04-after-study-tap.png`, and `/tmp/myvault-current-audit-phone/24-workspace-chooser.png`.

## 9. Study, Library, and Courses

### Study

- SOURCE PRESENT: folders, nested folders, notes, sub-notes, sticky notes, create/rename/move/reorder/delete, workspace pin, folder-local pin, favourite, Recently Deleted, attachment handling, search, title preferences, and Explorer state all remain wired through current ViewModels/repositories.
- PASS - AUTOMATED: current unit/contract suite covering Study state and backup semantics is green.
- FAIL - RUNTIME: Study root remains inaccessible from Dashboard/Explorer because of P1-01.
- PASS - RUNTIME: a Study folder could be opened directly from Explorer and its notes were present.
- FAIL - RUNTIME: that folder used the legacy screen described in P2-02, including timestamps and non-Frozen folder styling.
- BLOCKED: current root CRUD/persistence/Explorer synchronisation exercise.
- Important semantic boundary remains: `isPinned` is workspace-wide; `isFolderPinned` is folder-local. No audit change merged these states.

### Library

- SOURCE PRESENT: folders, imports, duplicate handling, move/rename/pin/delete, save to device, tags, PDF Activity, reading progress, file metadata, and Explorer integration.
- PASS - AUTOMATED: current unit/contract suite covering Library and PDF persistence is green.
- SOURCE PRESENT: legacy Library view-mode values remain parsed/backed up while the Frozen Library UI ignores them; no selector is exposed.
- PASS - RUNTIME: an existing Library folder route opened from Explorer, but the sampled folders contained no files.
- BLOCKED: current import/duplicate/file CRUD, metadata truthfulness, Save to device, PDF opening, and Explorer synchronisation because no accessible PDF fixture was present.
- Physical evidence: `/tmp/myvault-current-audit-phone/17-library-folder.png` and `/tmp/myvault-current-audit-phone/20-workspace-attachments.png`.

### Courses

- SOURCE PRESENT: course CRUD, nested hierarchy, note count, `lastOpenedNoteId` Continue semantics, Course Notes, Sticky Notes, Concept Cards, and Stage 4 note route.
- PASS - AUTOMATED: current Course contract tests are green.
- FAIL - RUNTIME: Courses root cannot be opened from Dashboard Explorer because of P1-01.
- PASS - RUNTIME: an existing Course folder and Course Note opened from Explorer; the note used the Stage 4 reader and Back returned to the exact Course folder.
- FAIL - RUNTIME: the Course folder itself used the legacy presentation described in P2-02.
- Physical evidence: `/tmp/myvault-current-audit-phone/34-course-folder-screen.png` and `/tmp/myvault-current-audit-phone/37-course-note-reading.png`.
- No completion percentage, Course search, or fabricated course description was found in the intended Frozen source path.

## 10. Note Reader and Editor

- PASS - RUNTIME: a Search result and a Course Note opened in Reading mode; title, breadcrumb, body, mixed Arabic/English content, actions, and Edit affordance rendered.
- PASS - RUNTIME: Editing mode opened with Saved/Editing state and visible formatting controls. The keyboard opened with the toolbar attached above the IME; the note body remained visible and no collision occurred.
- Runtime evidence: `/tmp/myvault-current-audit-phone/12-note-reading-from-search.png`, `/tmp/myvault-current-audit-phone/13-note-editing.png`, `/tmp/myvault-current-audit-phone/14-note-edit-keyboard.png`, and `/tmp/myvault-current-audit-phone/37-course-note-reading.png`.
- SOURCE PRESENT: autosave, rich text, selection, headings, quote, lists, tables, links, attachments, knowledge/references, versions, export, narration, and formatting-provider actions.
- PASS - AUTOMATED: serialization, formatting, editor presentation contracts, and relevant repository tests are green.
- BLOCKED/NOT REVERIFIED: actual edits, save/reopen, long-note responsiveness, selection, attachment sizing, table editing, versions, export, narration, and process recreation. No Note attachments existed in the reachable dataset.
- SOURCE RISK: Note-delete completion still pops to old Home and is affected by P1-01.

## 11. PDF Reader

- SOURCE PRESENT: continuous reader, zoom/pan state, true text selection, Copy, Note, Study link, selected-text highlight compatibility, one-shot rectangular Draw Highlight, colour preset, annotation H/N pill, local annotation sheet, View all/PDF Activity, page notes, tags, narration, reading progress, Explorer hamburger, and PDF-route edge-gesture suppression.
- PASS - AUTOMATED: one-shot Draw Highlight contract; historic `text_box` preservation; multi-rectangle geometry; representative legacy rectangle fallback; selected-text preservation; annotation/source relationship tests.
- SOURCE PRESENT: rectangle completion saves immediately and returns draw mode to false; no persistent Done/Exit toolbar is required.
- BLOCKED: no PDF file or attachment was present in the reachable physical-device dataset. Sampled Library folders and Workspace Attachments were empty.
- NOT TESTABLE: physical multi-touch pinch, pan-after-zoom, annotation geometry alignment, real selection handles, palette touch/z-order, narration audio, and Light/Dark/OLED physical rendering.
- Current conclusion: there is no newly proven PDF data regression in automated evidence, but PDF release acceptance is **not established** without the physical fixture pass.

## 12. Qur'an Reader

- SOURCE PRESENT: resume and exact-reference targets, Surah picker/filter/Juz, canonical Arabic, Tajweed, translations, Maududi footnotes, Tafsir, bookmarks, recents, reflections, selected-ayah actions, reciter selection, floating audio player, speed/seek, downloads/offline paths, and Memorise handoff.
- PASS - AUTOMATED: Dashboard exact-reference target and stale-ayah fallback tests; footnote same-marker toggle and different-marker replacement helper; current reciter source contract.
- FAIL - RUNTIME: Dashboard exact-location Continue and Reflection item taps remain on their current screens because of P1-01. This is a navigation failure, not evidence that the saved verse parser is wrong.
- BLOCKED: direct reader runtime, canonical rendering comparison, picker typography, Maududi marker lifecycle, Tafsir dismissal, reflection routing, immediate reciter restart, audio/download/offline, and themes.
- Canonical Qur'an assets/source were not modified.

## 13. Memorise

- SOURCE PRESENT: overview/statuses, single-ayah practice, exact-ayah handoff, permission/auto-record path, WAV recording, pause/resume, review/playback, Google Chirp and OpenAI Transcribe paths, deterministic analysis, word states, retries, attempts/detail, whole-Surah continuous mode, manual stop, and whole-Surah results.
- PASS - AUTOMATED: analysis, scoring, attempt persistence, and whole-Surah engine tests are green.
- SOURCE PRESENT: whole-Surah mode loads the complete canonical Surah, records continuously, and does not auto-stop at ayah boundaries.
- SOURCE PRESENT: dormant repeat values 3x/5x/10x/Until Stopped remain compatibility-only and are not exposed.
- SOURCE PRESENT: live WAV, active recording, concealment, STT cache, and active analysis remain device-local/transient; persisted statuses and attempts remain in backup scope.
- BLOCKED: all current physical runtime because Memorise cannot be opened from the normal Dashboard Explorer route.
- NOT TESTABLE: microphone permission, real recording, pause/resume timing, captured playback, provider credentials, STT network, and physical audio.

## 14. Global Search

- PASS - RUNTIME: Search opened on the physical device; query `he` returned real Notes.
- PASS - RUNTIME: metadata such as `Study / Qur'an Reflections` was fully visible without the previously reported vertical clipping.
- PASS - RUNTIME: a Note result opened the Stage 4 reader.
- SOURCE PRESENT: Notes, folders, files/PDFs, and courses are included in search result composition.
- P3: note metadata contains only immediate folder, not complete ancestry.
- P3: unfiled metadata contains a duplicate separator: `Study / / Unfiled`.
- SOURCE RISK: Course result navigation uses the old Home pop and is affected by P1-01.
- BLOCKED: Files/PDF results were absent from current data; Course-result routing, full result-type matrix, empty state, Back priority, and Light/Dark/OLED were not fully exercised.
- Runtime evidence: `/tmp/myvault-current-audit-phone/11-search-he.png` and `/tmp/myvault-current-audit-phone/44-search-report-lower.png`.

## 15. Settings, theme, narration, and security

### Settings/theme

- PASS - RUNTIME: Settings opened on the physical phone and displayed Theme, Accent, Material You, Dashboard/Note font size, Note preview, full-title toggles, Default note view, Listen provider, Azure status, Security, Storage, Recently Deleted, Google Drive, Backup, and Formatting account.
- PASS - RUNTIME: the Settings root used a clean Menu-only header. Recently Deleted displayed real folders/notes and existing Restore/Delete/Clear all controls; no destructive action was pressed.
- Runtime evidence: `/tmp/myvault-current-audit-phone/21-settings.png`, `/tmp/myvault-current-audit-phone/22-settings-lower.png`, and `/tmp/myvault-current-audit-phone/46-recently-deleted.png`.
- PASS - AUTOMATED: legacy theme plus optional `themeModeV2` compatibility, invalid/missing V2 fallback, and Settings backup validation tests are green.
- SOURCE PRESENT: Light, Dark, OLED, Follow system + Dark, and Follow system + OLED; Material You remains device-local.
- BLOCKED: setting mutation and persistence across restart, system theme change, OLED physical display, destructive storage/deleted actions, and formatting session flow.

### Narration

- SOURCE PRESENT: Note/PDF narration engines and global player; Qur'an audio remains separate.
- BLOCKED/NOT TESTABLE: Device TTS audio, Azure/OpenAI playback, seek/pause/resume/stop, navigation persistence, collision checks, and speaker output.

### App lock/security

- SOURCE PRESENT: app-lock and timeout preferences plus authentication route.
- NOT TESTABLE: physical fingerprint, password fallback, immediate lock, no-unlocked-frame guarantee, and post-unlock Dashboard because Security lock was disabled and the audit did not change it.

## 16. Google Drive, backup, and persistence

### Google Drive/account switching

- PASS - SOURCE: current Google account email is displayed; Change account signs out before opening account selection; Drive hierarchy IDs are rediscovered per current account rather than persistently cached.
- FAIL - SOURCE: sync/manifest timestamps are global, causing P1-02.
- PASS - RUNTIME: physical Settings showed the connected account `aahforex@gmail.com` and the Google Drive subpage displayed the connected state and Change account action.
- NOT TESTABLE: account A -> B -> A identity, backup discovery, upload target, account switching, and stale account state. Change account and Backup/Restore were deliberately not executed against the user's account.
- Runtime evidence: `/tmp/myvault-current-audit-phone/23-google-drive.png`.

### Backup/restore

- PASS - AUTOMATED: current backup repository, settings validation, theme compatibility, PDF geometry, historic annotations, folder-colour mappings, notes/folders/courses/Qur'an/Memorise serialization tests are green.
- SOURCE PRESENT: optional `themeModeV2` is additive; legacy `theme` remains light/dark/auto; Material You is not backed up.
- SOURCE PRESENT: PDF parent annotation remains available with additive ordered segment geometry.
- SOURCE PRESENT: Memorise persisted attempts/statuses are backed up; live audio/session data is not.
- P3: pinned-strip expansion remains local-only.
- NOT TESTABLE: current destructive Android backup/restore, Web-origin restore, real Drive upload/discovery/restore, attachments on a clean install, or A/B account isolation.
- No backup format, Room schema, or repository contract was changed during this audit.

## 17. Performance, logs, crashes, and motion

### Performance measurements

- Startup total time across five emulator starts: minimum 650 ms, median 687 ms, maximum 1054 ms.
- Physical cold-start totals: 96 ms and 99 ms.
- Post-launch memory snapshot: total PSS approximately 148,384 KB; total RSS approximately 248,868 KB.
- Physical post-audit memory snapshot: total PSS approximately 237,165 KB; total RSS approximately 351,620 KB.
- After four controlled Dashboard scroll cycles on the physical phone: 825 frames, 2 janky frames (0.24%); 50th/90th/95th percentiles 9 ms and 99th percentile 10 ms.
- Dashboard, Search, Note Reader/Edit, Settings, and Explorer interactions were responsive in the available run.
- BLOCKED: large Study/Library trees, PDF open/scroll, long Qur'an Surah, audio recomposition, long editor, and backup UI.

### Crash/log audit

- No fatal exception or ANR from `com.myvault.app` was observed in the captured audit log window.
- No database corruption or migration crash was observed on launch.
- Physical logs contained Samsung/driver warnings but no app fatal exception, ANR, SQLite corruption, or out-of-memory failure.
- Media, Drive, PDF renderer, and recorder error paths were not exercised deeply enough to declare them clean.
- Physical evidence: `/tmp/myvault-current-audit-phone/45-dashboard-gfxinfo.txt` and `/tmp/myvault-current-audit-phone/45-app-logcat.txt`.

### Motion/responsive/accessibility

- PASS - RUNTIME: Dashboard fit at 360/390/412/430 dp in the sparse dataset.
- SOURCE PRESENT: directional NavHost transitions, separate drawer/sheet motion, PDF edge gesture suppression, and reduced-motion handling exist.
- BLOCKED: forward/reverse direction in every route, RTL reversal, selected-ayah and footnote motion, TalkBack ordering, enlarged font scale, and comprehensive physical hit-target review.

## 18. Deferred and compatibility inventory

The following remain intentionally deferred or compatibility-only; this audit did not implement them:

- outgoing Study Share: new functionality not currently implemented;
- outgoing Library Share: new functionality not currently implemented;
- pinned-strip expanded state backup: low-impact display-state compatibility debt;
- Material You portability: device-local, not backed up;
- hidden legacy general font-size preference;
- hidden automatic-tag-suggestions preference;
- legacy Library view-mode values: parsed/backed up, no visible selector;
- dormant Memorise repeat modes: compatibility-only, no visible controls;
- active Memorise recording/session state: intentionally transient;
- external Note/PDF/Qur'an deep links: not declared in current manifest;
- current live Android/Web destructive round trip and real Drive account-switch acceptance;
- physical-device PDF geometry/pinch, Qur'an audio, Memorise recording/STT, biometric lock, and release-signing acceptance;
- exact source provenance for the signed APK currently installed on the physical phone.

## 19. PASS summary

The strongest current PASS evidence is:

- exact repository/branch/remote alignment at audit start;
- recoverable pushed audit tag;
- JBR 21 build, 408 tests, lint with no errors, debug APK;
- Dashboard is the true startup destination and no Study/Library startup flash occurred on emulator;
- physical cold launch also showed Dashboard as the first normal application frame;
- current Explorer visual ordering;
- real Dashboard rendering, scrolling, Reflections, Pinned content, and sparse responsive widths;
- Search rendering without the previously reported metadata clipping on the physical phone;
- Note Reading, Editing, mixed Arabic/English rendering, and keyboard-toolbar layout on the physical phone;
- Course Note -> Stage 4 reader -> exact Course folder return;
- Settings, Recently Deleted, connected Drive account presentation, and workspace switching;
- physical Dashboard frame pacing with 0.24% janky frames in the controlled sample;
- automated compatibility coverage for theme, backup mappings, PDF annotations/geometry, Qur'an navigation helpers, Courses, and Memorise analysis/attempts;
- no fatal app crash or ANR in the emulator or physical-device captured runtime windows.

These PASS items do not cancel the root-navigation or Drive account-isolation findings.

## 20. Release verification gates

The following are not counted as proven product defects but block a truthful release-ready declaration:

1. The signed APK installed on the physical phone could not be matched to a local artifact/source commit.
2. No disposable Google Drive A/B account pair was available; the connected user account was not altered.
3. Root navigation prevented normal runtime entry to Study, Library, Courses, Qur'an, and Memorise.
4. No representative PDF fixture was present in the reachable installed dataset.
5. No microphone/audio/STT provider exercise was possible because Memorise and Qur'an were blocked.
6. No biometric/app-lock exercise was possible because Security lock was disabled and the audit did not change it.
7. Release signing identity and Google OAuth certificate coverage were not reverified against the intended source release candidate.
8. No current destructive Android/Web backup/restore round trip was run.

## 21. Recommended remediation order

### Phase 1: Restore core navigation, then rerun the blocked shell matrix

- Correct all old-Home pop assumptions while keeping Dashboard as the graph start.
- Cover Explorer root modes, Dashboard Qur'an/Reflection, Search Course result, Reflections Hub result, and Note-delete completion.
- Add navigation-state tests that execute a real NavController/back stack.
- Reinstall and verify Dashboard -> every root mode before any other remediation.

### Phase 2: Remove the legacy Study/Course folder route

- Route Explorer Study and Course folder selections through the already approved Stage 2/Stage 6 hierarchy screens.
- Preserve folder IDs, Course semantics, CRUD, and Stage 4 Note routing.
- Verify both root entry and deep selected-folder entry, including Back and Explorer expansion state.

### Phase 3: Make Drive sync comparison state account-safe

- Decide the non-destructive account-scoping/reset strategy before code changes.
- Test A -> B -> A with disposable accounts and distinct manifests.
- Verify current account identity, discovery, conflict detection, push target, pull target, and local-vault preservation.

### Phase 4: Gate incoming share startup correctly

- Ensure a cold incoming share/import does not render Dashboard before the imported note.
- Preserve normal Dashboard first frame when no explicit launch request exists.

### Phase 5: Complete physical-device product regression

- Study/Library/Courses CRUD and Explorer synchronisation.
- Note autosave, rich text, attachments, keyboard, versions, export, and narration.
- PDF real pinch/pan/selection/one-shot rectangle/palette/geometry/reopen.
- Qur'an footnotes/Tafsir/reciter/audio/download/offline/exact navigation.
- Memorise single/whole-Surah recording, STT, attempts, and interruption.
- Settings persistence, security/biometric, themes, responsive widths, RTL, font scale, and TalkBack.

### Phase 6: Controlled compatibility and release acceptance

- Destructive backup/restore on disposable data.
- Android/Web additive-field compatibility.
- Real Drive account-switch and backup/restore.
- Release signing/OAuth identity check and final signed candidate smoke.

## 22. Final audit verdict

**Current state: NOT RELEASE-READY.**

There is no proven P0 issue in this audit. The app has a green build, a clean Dashboard first frame, good measured Dashboard scrolling, and substantial source/automated coverage. It is still not release-ready: the normal Dashboard shell cannot open core root destinations, Explorer Study/Course folders use a legacy presentation, and Google Drive account-switch metadata requires a safe account-isolation correction before real backup testing. Fix the two P1 items first, then the legacy folder-route regression, and repeat the blocked physical-device and compatibility matrix with a traceable release-candidate APK and controlled fixtures.

The physical phone was left on Dashboard in the Islamic Corpus workspace. No user content was created, edited, deleted, restored, or cleared; no Drive account was changed; and no backup/restore operation was started.
