# MyVault Android Frozen Design Master Production Port Plan

Status: audit and plan only. No Frozen Design Master UI has been implemented.

Date: 2026-08-26

## 1. Authority and Scope

The Frozen Design Master is the visual contract for this port. The production
Android application remains authoritative for data, persistence, domain logic,
backup compatibility, and device behavior.

Authoritative Android project:

`/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`

Authoritative frozen reference, read-only:

`/Users/aliah/Documents/Codex/2026-08-25/i-have-a/work/myvault-ui-prototype`

The implementation must use the design documentation, frozen prototype, and all
50 master screenshots together. It must not modify the reference directory or
change production data architecture for presentation convenience.

## 2. Recovery and Baseline

- Application ID/package: `com.myvault.app`
- Original branch: `android-web-mobile-ui-redesign`
- Baseline checkpoint commit: `ebda1db328a3e220a4f6b58af79069b8367f4f1a`
- Checkpoint message: `Checkpoint Android before frozen design master port`
- Recovery tag: `pre-frozen-design-master-port`
- Working branch: `frozen-design-master-port`
- Remote: `git@github.com:aliabuzahra95/MyVault.git`
- External filesystem backup:
  `/Users/aliah/Desktop/Current Projects/MyVault Android Recovery Before Frozen Port 2026-08-26-140645`
- Backup verification: rsync dry-run matched the checkpoint source after
  excluding generated build caches and outputs.
- Baseline build command:
  `JAVA_HOME=/Users/aliah/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug`
- Baseline build result: PASS, 44 tasks, 9 seconds.
- Baseline unit test command:
  `JAVA_HOME=/Users/aliah/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew testDebugUnitTest`
- Baseline unit test result: PASS, 34 tasks, 1 second.
- Environment note: the machine default JDK 25.0.2 is not accepted by the
  current Kotlin toolchain. JBR 21 is the known-good build JDK.
- Existing unrelated untracked artifact retained untouched:
  `release/MyVault-20260824-1834-web-explorer-redesign-signed.apk.idsig`

## 3. Frozen Reference Audit

The following documents were read in full:

1. `DESIGN_SPEC.md`
2. `COMPONENT_INVENTORY.md`
3. `ANDROID_COMPONENT_MAP.md`
4. `MOTION_SPEC.md`
5. `DESIGN_MASTER_README.md`

The frozen prototype implementation was inspected in `app/page.tsx` and
`app/globals.css`. All 50 screenshots in `design-master/screenshots` were
inspected. The final acceptance viewport is 412 x 892 logical pixels.

Key frozen contracts:

- One shared Explorer drawer; no bottom navigation.
- One shared Corpus Browser presentation for Study and Library.
- Compact ordinary folder and leaf rows, neutral outlined icons, and restrained
  use of accent color.
- Pinned content is compact and appears only when content exists.
- Contextual FABs, shared creation sheets, long-press action sheets, hierarchical
  Move, and compact Rename.
- A document-canvas Note Editor using the production rich-text engine.
- A white PDF paper surface in every theme using the production PDF engine.
- Resume-first Qur'an reader with a Surah picker rather than a Surah-list home.
- Compact, non-gamified Memorise presentation around the production engine.
- Shared semantic theme, typography, spacing, radius, motion, and haptic tokens.
- Restrained state motion only; no animated whole-page replacement.

Frozen dimensions that must become shared Compose tokens include:

- Explorer width: `min(viewport - 46, 366)`
- Standard header: 72 dp
- Qur'an/Memorise header: 66 dp
- PDF header: 58 dp
- Screen edge spacing: approximately 14 dp
- Corpus folder row minimum: 38 dp
- Corpus leaf row minimum: 35 dp
- Pinned item: 153 x 45 dp
- Inline search: 50 dp
- FAB: 54 x 54 dp
- Sheet top radius: 24 dp

## 4. Production Architecture Baseline

### Application Shell

- `MainActivity.kt` owns theme setup, app lock, incoming shared-note import, and
  the root `VaultNavHost`.
- `MyVaultApplication.kt` owns Hilt, WorkManager integration, and PDFBox setup.
- `VaultNavHost.kt` currently owns navigation, workspace switching, root modes,
  Explorer state, screen wiring, CRUD action hosting, and the narration mini
  player. It currently includes disabled-swipe pager roots and page transitions.
- `VaultMobileWebShell.kt`, `VaultExplorerActionHost.kt`,
  `FolderTreeRow.kt`, and `CompactWorkspaceHeader.kt` are the main existing shell
  components to refine or replace with shared frozen equivalents.

### State and Data

- Hilt ViewModels expose production state to Compose.
- Room `VaultDatabase` is version 27 and contains folders, notes, blocks,
  attachments, tags, tables, PDF annotations/progress, knowledge links,
  versions, Courses, Sticky Notes, and Concept Cards.
- Repository contracts already exist for notes, folders, courses, attachments,
  search, storage, snapshots, backups, PDF annotations/progress, knowledge,
  Qur'an, narration, formatting, Supabase auth, and Google Drive sync.
- This port must not alter Room entities, migrations, IDs, repository contracts,
  note serialization, or backup representations.

### Production Engines to Preserve

- Note Editor: native rich text, style marks, note links, blocks, tables,
  attachments, versions, export, narration, and formatting actions.
- PDF: AndroidX PDF/fallback rendering, zoom, progress, highlight geometry,
  annotations, page notes, text boxes, extraction, export, and narration.
- Qur'an: canonical corpus, word metadata, Tajweed, translations, Tafsir,
  bookmarks, reflections, reciters, audio, download state, reading position,
  and memorisation state.
- Memorise: session state, conceal modes, recording, speech recognition,
  analysis, results, and persisted attempts.
- Courses: course hierarchy, notes, folders, Sticky Notes, Concept Cards,
  continuation/progress, and all CRUD.
- Drive/backup: incremental sync, restore controller, manifest handling,
  compatibility policy, WorkManager, and foreground sync behavior.

### Theme Baseline

- Existing shared files: `VaultTheme.kt`, `VaultColors.kt`, `VaultShapes.kt`,
  `VaultTypography.kt`.
- Existing modes are Light, Dark, and Auto.
- The frozen contract requires Material You plus Light, Dark, OLED, Follow
  system + Dark, and Follow system + OLED. This expansion belongs only to Stage
  3 after placement and behavior approval.

## 5. Port Mapping

| Area | Current production presentation | Frozen component | Proposed Compose ownership | Existing engine/state |
| --- | --- | --- | --- | --- |
| Global shell | `VaultNavHost`, pager roots, mobile-web shell, optional old bottom bar | Shared app shell and Explorer | One shared shell plus one shared Explorer component | `ShellPreferencesViewModel`, root ViewModels, Navigation Compose |
| Explorer | `VaultMobileWebShell`, `FolderTreeRow`, `VaultExplorerActionHost` | Profile, APPLICATION, KNOWLEDGE, Drive/theme/footer, expandable hierarchy | Shared `MyVaultExplorer` and shared contextual sheets | Home, Library, Courses state and existing CRUD callbacks |
| Study | `HomeScreen`, `FolderViewScreen` | Shared Corpus Browser | Shared browser primitives configured for notes | `HomeViewModel`, `FolderViewModel`, folder/note repositories |
| Library | `LibraryScreen`, attachment routes | Shared Corpus Browser | Same browser primitives configured for documents | `LibraryViewModel`, attachment/PDF repositories |
| Settings | `SettingsScreen` with production preferences and services | Frozen grouped Settings layout | Shared section/row/theme controls around current callbacks | `SettingsViewModel`, `VaultPreferences`, backup/Drive/auth repositories |
| Note Editor | `EditorScreen`, `ReadingScreen`, `EditorToolbar`, `VaultRichText` | Reading/editing canvas and formatting chrome | Frozen shell around existing editor implementation | `NoteViewModel`, note/attachment/knowledge/narration/formatting repositories |
| PDF Reader | `AttachmentViewerScreen`, `PdfActivityFeedScreen` | Reader chrome, selection, annotation, immersive modes | Frozen PDF chrome/sheets around current renderer | `AttachmentViewerViewModel`, PDF repositories, AndroidX PDF/PDFBox |
| Courses | `CoursesScreen` | Root cards, workspace hierarchy, creation sheet | Frozen course surfaces using shared hierarchy/action primitives | `CoursesViewModel`, `CourseRepository` |
| Qur'an | `QuranShellScreen` and `ui/quran/*` | Resume-first reader, picker, settings, contextual sheets, audio | Frozen reader composition around current Qur'an components | `QuranReaderViewModel`, Qur'an repositories/audio/preferences |
| Memorise | `MemoriseShellScreen`, shared Qur'an memorisation sheets | Overview, session, hide, record, analyze, results | Frozen state surfaces around current engine | `MemoriseViewModel`, Qur'an state, speech/analysis engines |
| Dashboard | Current root/home behavior varies by workspace | Explorer link only; no frozen screen reference | Unresolved; cannot redesign without approval | Current root state and snapshots |
| Global Search | `SearchScreen` | Explorer link only; only contextual search is visually specified | Unresolved; cannot redesign without approval | `SearchViewModel`, `SearchRepository` |
| Personal workspace | Existing Personal root and workspace switching | Not represented | Unresolved; Stage 1 shell placement requires approval | workspace preference and existing Personal state |

## 6. Shared Compose Components Planned

Names are provisional implementation names, not design changes:

- semantic spacing, icon, typography, radius, elevation, motion, and haptic
  tokens under `ui/theme` or `ui/designsystem`;
- one shared Explorer;
- one compact header family;
- one Study/Library Corpus Browser row family;
- one inline search presentation;
- one contextual FAB;
- one shared creation sheet;
- one shared long-press action sheet;
- one hierarchical Move sheet;
- one compact Rename sheet;
- shared sheet scaffolding and reduced-motion handling.

These components will accept existing state and callbacks. They will not own or
replace repositories, ViewModels, persistence, or backup logic.

## 7. Missing or Conflicting Production Functionality

The Frozen Design Master does not visibly specify the following existing
production features. They must not be removed or assigned new UI silently.

### Stage 1 blockers

1. Personal workspace switching and its Personal Explorer hierarchy.
2. The global narration mini-player currently hosted by `VaultNavHost`.
3. Dashboard screen presentation after selecting Dashboard in Explorer.
4. Global Search screen presentation after selecting Search in Explorer.

### Stage 3 approved implementation contract

The complete production inventory, frozen placement hierarchy, compatibility
mapping, and approved production-specific deviations are recorded in
`docs/STAGE_3_SETTINGS_AMENDMENT.md`. Stage 3 Settings + Theme is authorized.
The canonical cross-stage deferred list remains
`docs/DEFERRED_REQUIREMENTS.md`.

1. App lock, biometric/security options, and lock timeout.
2. Local backup and restore controls.
3. Google Drive connection, push/pull, force-push, conflicts, and status.
4. Azure/device narration provider settings and Azure credentials.
5. Supabase formatting login/logout.
6. Storage usage displays the truthful production total only; representative
   category bars are intentionally omitted.
7. Recently Deleted displays only production-supported deleted notes/folders,
   retaining existing restore, permanent delete, and supported clear-all.
8. Release-readiness information.
9. Dashboard/note font and title/preview/view preferences not shown explicitly.

### Stage 4 approved resolution

The complete pre-Stage-4 audit and approved placement hierarchy are recorded in
`docs/STAGE_4_EDITOR_AMENDMENT.md`. The frozen Editor amendment resolves the
document canvas, toolbar, overflow, attachments, knowledge, history, export,
formatting-provider and narration entry surfaces. The production-specific
Listen mapping includes Device TTS, Azure Speech TTS and the existing OpenAI
TTS provider. The global narration mini-player remains a separate deferred
requirement.

### Stage 5 blockers

1. PDF activity feed.
2. Page notes, text boxes, annotation tags, and linked Study-note actions.
3. PDF narration, extraction, export, replace, and delete placement.

### Stage 7 blockers

1. Separate Qur'an Reflections Hub destination.
2. Bookmark/recent-location management surfaces beyond the shown reader state.
3. Audio-download management and some repeat/memorisation controls not shown in
   the frozen screenshots.

### System entry points not requiring redesign unless surfaced

Incoming ACTION_SEND note import, app-lock enforcement, background WorkManager,
and the Qur'an audio foreground service can remain operational beneath the new
presentation. Any visible surface change for them still requires approval.

## 8. Android-Native Compatibility Audit

- System bars and display cutouts: the frozen safe-area contract can be
  implemented with `WindowInsets` without a planned visual deviation.
- Keyboard: `adjustResize` and Compose IME insets can preserve the editor
  screenshots, but must be validated at the reference viewport.
- Back: native/predictive Back can close sheets, search, Explorer, and nested
  destinations in order. No extra visible back affordance will be invented.
- Accessibility: visible row density is below a typical 48 dp touch target.
  Larger invisible semantic hit areas should preserve the frozen visuals. Any
  unavoidable visible enlargement requires approval.
- Font scaling: the 412 x 892 references represent normal font scale. Additional
  checks at larger font scales are required; collisions must be reported rather
  than solved by unapproved redesign.
- PDF: native renderer ownership can preserve the frozen chrome, but PDF paper
  and gesture handling must remain renderer-controlled to prevent rerendering or
  annotation drift.
- RTL: hierarchy indentation and directional controls need semantic start/end
  layout while mixed Arabic/English content retains correct direction.
- Motion: current whole-page slide transitions conflict with the frozen motion
  rule and are planned for removal in Stage 1. State animations must remain
  interruptible and use the frozen timing tokens.
- Screenshot comparison: no screenshot-regression harness currently exists.
  Emulator capture plus deterministic crop/overlay/diff tooling is required.

## 9. Risk Register

| Risk | Impact | Control |
| --- | --- | --- |
| Central `VaultNavHost` owns too many concerns | Navigation or workspace regression | Extract presentation incrementally; preserve routes and callbacks; Stage 1 commit only |
| Workspace presentation absent from master | Silent feature loss or visual invention | Block Stage 1 until user decides placement |
| Missing Settings actions | Backup/security/auth functionality could disappear | Preserve current UI until each placement is approved; Stage 3 gate |
| Rich-text chrome replacement | Serialization or formatting regression | Keep `NoteViewModel` and rich-text engine; test Android/Web-compatible payloads |
| PDF chrome recomposition | White flashes, lost zoom, annotation drift | Keep renderer instance/state stable; isolate chrome; geometry regression tests |
| Qur'an mock/reference confusion | Canonical text or Tajweed regression | Use only production Qur'an repositories; prototype supplies presentation only |
| Memorise state presentation | Recording/session state loss | Map every production state explicitly; never replace analysis engine |
| Theme expansion | Stored preference migration or unreadable surfaces | Add backward-compatible preference mapping and shared semantic colors |
| Dense rows vs accessibility | Insufficient touch targets or TalkBack order | Invisible hit expansion and semantics; report any visible conflict |
| Screenshot overfitting | Poor behavior on other phones | Validate smaller, 412 x 892 reference, and larger phone widths |
| Backup compatibility | Data loss across Android/Web | No schema/serialization change; run existing and end-to-end backup tests |

## 10. Staged Implementation Plan

No stage begins until the preceding approval gate is satisfied.

### Stage 1 - Global Shell and Explorer

Expected production files:

- `ui/navigation/VaultNavHost.kt`
- `ui/components/VaultMobileWebShell.kt`
- `ui/components/VaultExplorerActionHost.kt`
- `ui/components/FolderTreeRow.kt`
- `ui/components/CompactWorkspaceHeader.kt`
- new shared shell/Explorer/token components as needed
- targeted shell contract tests

Frozen references: 01-03 and contextual interaction references 43-50.

Preserve: all destinations, root selection, workspace state, Explorer hierarchy,
CRUD callbacks, Back, Drive status, theme selection, and narration behavior.

Tests: build, unit tests, Explorer expand/collapse, root navigation, long-press,
create/move/rename/delete/pin, Back, process recreation, TalkBack order, and
reference screenshots at 412 x 892 plus small/large widths.

Approval needed before Stage 1: Personal workspace switch placement, narration
mini-player placement, and treatment of Dashboard/global Search destinations.

### Stage 2 - Study and Library Shared Corpus Browser

Approved temporary access amendment: the Study root FAB sheet contains a
visually separate `TOOLS` section linking to the existing Workspace Attachments,
Aggregate Favourites, and Qur'an Reflections Hub destinations. This is temporary
placement only; each final Frozen destination remains deferred. The primary
Study screen receives no permanent control, rail, or additional FAB for these
features. See `docs/STAGE_2_INTERACTION_AMENDMENT.md`.

Expected production files:

- `ui/screens/HomeScreen.kt`
- `ui/screens/FolderViewScreen.kt`
- `ui/screens/LibraryScreen.kt`
- shared Corpus Browser/header/search/FAB/action-sheet components
- `ui/navigation/VaultNavHost.kt` only for route wiring
- targeted Study/Library UI and behavior tests

Frozen references: 04-09 and 43-50.

Preserve: nested hierarchy, create, import, rename, move, reorder, delete,
pin/unpin, favorite, selection, subnotes, attachments, title settings, search,
view persistence, open note, and open PDF.

Tests: full CRUD in root/nested folders, persistence, search, selection, pinning,
file imports and duplicates, open Note/PDF, small/reference/large screenshots.

### Stage 3 - Settings and Theme

Expected production files:

- `ui/screens/SettingsScreen.kt`
- `ui/viewmodel/SettingsViewModel.kt` only where presentation state adapters are
  required
- `ui/theme/VaultTheme.kt`
- `ui/theme/VaultColors.kt`
- `ui/theme/VaultShapes.kt`
- `ui/theme/VaultTypography.kt`
- `data/preferences/VaultPreferences.kt` for backward-compatible theme values
- shared settings/theme components and tests

Frozen references: 10-13.

Preserve: every current setting and service callback. Stage cannot start until
the missing-setting placement decisions in section 7 are approved.

Tests: all theme modes, system theme changes, persistence, settings callbacks,
security, Drive/backup, narration, auth, deleted items, contrast, and screenshots.

### Stage 4 - Note Editor

Expected production files:

- `ui/screens/EditorScreen.kt`
- `ui/screens/ReadingScreen.kt`
- `ui/components/EditorToolbar.kt`
- `ui/screens/VaultRichText.kt`
- `ui/viewmodel/NoteViewModel.kt` only for presentation adapters
- shared editor chrome/sheets and tests

Frozen references: 14-17.

Pre-stage audit: `docs/STAGE_4_EDITOR_AMENDMENT.md` inventories 63 production
capabilities. The final Frozen Editor amendment resolves the action hierarchy.
The approved production-specific Listen-sheet mapping retains Device and Azure
and adds the existing OpenAI TTS provider as a third restrained row. Stage 4 is
authorised without changing editor, narration, persistence or backup engines.

Preserve: rich text and style marks, selection, undo/redo, blocks, tables,
attachments, links, tags, versions, export, narration, formatting, autosave, and
serialization.

Tests: open restored/new/course notes, edit/save/reopen, formatting round-trip,
undo/redo, IME, long note scroll, attachments, Android/Web backup payload, and
screenshots with/without keyboard.

Implementation status: the shared Reading/Editing shell, primary and secondary
formatting surfaces, Note overflow, Note info, Knowledge & references,
Attachments, Version history, export, formatting-provider entry and the three
approved narration providers are integrated with existing production state.
No Room schema, repository contract, note serialization or backup-format change
was introduced.

### Stage 5 - PDF Reader

Expected production files:

- `ui/screens/AttachmentViewerScreen.kt`
- `ui/screens/PdfActivityFeedScreen.kt` after placement approval
- `ui/viewmodel/AttachmentViewerViewModel.kt` only for presentation adapters
- shared PDF chrome/selection/annotation sheets and tests

Frozen references: 18-22.

Preserve: renderer lifecycle, cached document, vertical scroll, zoom/pan,
selection, highlights, geometry, annotations, progress, extraction, export,
narration, file actions, and linked-note behavior.

Tests: repeated document switching, drawer/annotation panel resize without blank
rerender, zoom stability, annotation geometry at multiple zooms, progress,
offline cache, dark/OLED paper, and screenshots.

### Stage 6 - Courses

Expected production files:

- `ui/screens/CoursesScreen.kt`
- shared course card/hierarchy/creation components
- `ui/viewmodel/CoursesViewModel.kt` only for presentation adapters
- targeted course tests

Frozen references: 23-26 and relevant context sheets 45-50.

Preserve: course CRUD, nested folders and notes, Sticky Notes, Concept Cards,
continue lesson/progress, pin/favorite, reorder, move, and Explorer integration.

Tests: all course content types and CRUD, hierarchy persistence, continue flow,
open course note, Explorer parity, and screenshots.

### Stage 7 - Qur'an

Expected production files:

- `ui/screens/QuranShellScreen.kt`
- `ui/screens/QuranReflectionsHubScreen.kt` after placement approval
- `ui/quran/*` presentation files
- `ui/components/QuranTajweedText.kt`
- `ui/viewmodel/QuranReaderViewModel.kt` only for presentation adapters
- targeted Qur'an tests

Frozen references: 27-34 and 49.

Preserve: canonical text, word/Tajweed data, translations, Tafsir, bookmarks,
reflections, audio/reciters/downloads, reading position, contextual actions, and
memorisation integration. Preserve the approved warmer Light reading canvas.

Tests: corpus integrity, RTL/mixed direction, Tajweed, translation/Tafsir,
reflection zero/one/multiple preview, audio lifecycle, resume, picker, process
recreation, and screenshots.

### Stage 8 - Memorise

Expected production files:

- `ui/screens/MemoriseShellScreen.kt`
- memorisation presentation components under `ui/quran`
- `ui/viewmodel/MemoriseViewModel.kt` only for presentation adapters
- targeted memorisation tests

Frozen references: 35-42.

Preserve: overview state, group/surah/ayah selection, session, Hide 1/2, Hide
All, recording, pause/resume, speech recognition, analysis, scoring, results,
revision/weak/incorrect states, and persisted attempts.

Tests: every frozen state transition, interruption, permission denial, audio
focus, process recreation, persisted result, reduced motion, and screenshots.

### Stage 9 - Cross-Screen Consistency

Expected files: shared theme/design-system/components already introduced in
Stages 1-8, plus narrowly scoped affected screens.

Checks: spacing, typography, colors, icons, radii, elevation, motion, haptics,
sheets, RTL, insets, font scaling, accessibility, and responsiveness. No feature
or visual redesign is permitted.

### Stage 10 - Full Regression and Acceptance

Expected changes: tests and documentation only unless a specific regression fix
is approved.

Checks: clean build, complete unit suite, instrumentation/semantic tests,
navigation, Explorer, all CRUD, Settings/theme, Note, PDF, Courses, Qur'an,
Memorise, Drive, backup, restore, Web compatibility, offline behavior, RTL,
keyboard, predictive Back, process recreation, and real-device smoke testing.

## 11. Visual Acceptance Method

At every stage:

1. Build and run relevant automated tests.
2. Launch a deterministic emulator at the closest correct 412 x 892 logical
   viewport and normal font/display scale.
3. Exercise production state and capture the matching frozen states.
4. Produce side-by-side reference and Android renders.
5. Produce aligned overlays/diffs where practical.
6. Correct fixable mismatches in spacing, dimensions, typography, icons,
   surfaces, and state presentation.
7. Repeat at smaller and larger phone widths, including approximately 360, 390,
   412, and 430 dp.
8. Report frozen references, Android screenshots, visual differences,
   functional tests, and unresolved issues.
9. Stop for explicit approval.

## 12. Commit and Stop Contract

- One reviewable commit per approved stage, with smaller commits where needed.
- Build and test before every stage checkpoint.
- Do not mix repository/data refactors into presentation commits.
- Do not modify the Frozen Design Master.
- Do not begin the next stage automatically.
- If the master does not place an existing function, Android requires a visible
  deviation, or two references conflict: stop, report, and wait.

## 13. Decisions Required Before Stage 1

The initial audit is complete, but Stage 1 is not authorized and cannot be
implemented faithfully until these design gaps are resolved:

1. Where and how the existing Personal/Islamic workspace switch appears in the
   frozen Explorer profile/header.
2. Where the production narration mini-player appears in the frozen shell.
3. Whether Dashboard and global Search retain their current screen presentation
   temporarily, are excluded from this port, or require new approved frozen
   references before they are touched.

All later-stage gaps in section 7 remain separate approval gates. No UI has been
changed as part of this audit.

## 14. Approved Stage 1 Design Amendments

Approved on 2026-08-26. These amendments resolve only the three Stage 1 blockers
listed above. They do not resolve any later-stage placement gap.

### Workspace switching

The existing Frozen Design Master Explorer profile/header is the workspace
switching entry point. Its visual composition remains the approved avatar, user
name, and active workspace label. Tapping the complete profile/header region
opens a compact native bottom sheet using the frozen sheet language. The sheet
contains only Islamic Corpus and Personal, with the current workspace indicated
subtly. Selection invokes the existing production workspace state and switching
logic. No permanent switcher, top-bar icon, bottom-navigation item, account
action, or Settings action is added.

### Narration mini-player

The current production narration mini-player presentation, placement, and
behavior remain unchanged during Stage 1. It is a deliberate temporary legacy
exception. If it collides with a frozen FAB, gesture inset, PDF control, Qur'an
audio bar, Memorise control, bottom sheet, keyboard, editor toolbar, or other
contextual surface, implementation stops for placement approval.

### Dashboard and global Search

Dashboard and global Search retain their current production screen
implementations and visual presentation. Stage 1 may make only the minimum
navigation wiring required to open them from the new Explorer. Contextual
Study/Library search is not a reference for global Search. Their temporary
visual inconsistency is approved until dedicated frozen references or another
explicit design amendment exists.

### Authorization boundary

Stage 1, Global Shell and Explorer, is authorized with these amendments. Stage
2 and every unresolved later-stage feature remain unauthorized. The frozen
prototype directory remains read-only.
