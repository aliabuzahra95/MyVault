# Stage 9 Real-Device Refinement Plan

Status: **BATCHES A1, A2, B, C1, C2, C3, AND C4 IMPLEMENTED AND VERIFIED**

This document records the pre-Stage-9 production audit performed against the
approved Stage 8 commit
`33730fca22a37020a8aee07b991f03a670d98b17`. No application source was changed.
The Frozen Design Master and all approved amendments remain authoritative.

## 1. Baseline

- Branch: `frozen-design-master-port`
- Approved Stage 8 commit: `33730fca22a37020a8aee07b991f03a670d98b17`
- Recoverable tag: `stage-8-approved` (pushed; dereferences to the approved commit)
- Java: JBR 21 at
  `/Users/aliah/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home`
- Baseline checks: `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- Tracked working tree before this audit: clean. Existing untracked visual
  artifacts and release by-products were preserved and not added.

## 2. Classification Summary

The pre-implementation audit identified 29 requirements. The 12 direct items
and eight final-amendment items are now resolved, leaving **9 active
requirements**:

| Classification | Count |
|---|---:|
| A. Direct functional fix | 0 active / 5 resolved |
| B. Direct consistency refinement | 0 active / 7 resolved |
| C. Requires Frozen design amendment | 2 active / 8 resolved |
| D. Stage 10 / compatibility | 7 |
| **Total active** | **9** |

## 2.1 Authorized Direct-Batch Result

The implementation was performed from audit commit
`5f0dc161955960933465460cf025f6d31941556e` without modifying the read-only
Frozen prototype, Room, backup formats, repositories, canonical Qur'an data,
PDF rendering, or note serialization.

- Reading and Editing now render real Note attachments as compact rows in the
  document flow. Runtime proof used a restored Note with two PDF attachments.
- Explorer drawer gestures are disabled only on the PDF reader route; the
  hamburger remains wired and all non-PDF routes keep their drawer gesture.
- Tafsir now uses the approved modal sheet with fixed header/X, independent
  body scrolling, swipe dismissal, and Tafsir-first Android Back handling.
- Reader Settings, player chrome, persistence, and playback now share one
  selected reciter. Stale preparation is cancelled/ignored. Runtime proof
  switched an active Al-Baqara ayah 4 from Abdul Basit (Murattal) to
  Abdur-Rahman as-Sudais and restarted the same ayah immediately.
- Surah picker, editor toolbar, PDF activity/annotation surfaces, annotation
  type cues, and Explorer primary labels received only the approved restrained
  typography/spacing adjustments.

Runtime captures are stored under `artifacts/stage-9/runtime/`. They include
Reading/Edit attachments, compact toolbar, Surah picker, Tafsir, active reciter
player, PDF reader/activity, and Explorer states.

The previously reported Qur'an-to-Memorise exact-ayah handoff is not included
in the active count. Stage 8 runtime evidence confirms it is resolved.

## 3. Complete Active Inventory

### A. Direct Functional Fixes - Resolved

| # | Issue and evidence | Current cause | Desired behavior | Risk | Runtime tests |
|---:|---|---|---|---|---|
| 1 | Qur'an Tafsir dismissal. `FrozenQuranReader.kt` currently expands `FrozenTafsir` inline inside the ayah list; `QuranReaderSurface.kt` Back handling has no Tafsir-first dismissal. The Frozen Qur'an amendment specifies a Tafsir sheet. | Long Tafsir shares the reader's scroll owner and its toggle can leave the viewport. There is no persistent close control. | Use the already-approved Tafsir sheet: fixed sheet header and X, independently scrolling body, Back closes Tafsir first, modal swipe-down where supported. | Medium: source/loading states and reader scroll position must remain stable. | Long Arabic and English Tafsir; X at every scroll position; Back; swipe-down; source switch; loading/error/retry; reader position unchanged. |
| 2 | Qur'an reciter synchronization. `QuranReaderViewModel` has one `selectedAudioReciter`, but `playWithReciter` prepares the new file before the existing playback is replaced. | UI preference is shared, but an old playback/request may remain active while the new reciter file is resolved, allowing delayed or stale playback. | One authoritative selected-reciter command/state. Update both surfaces immediately, cancel/ignore stale requests, and restart the same playing ayah with the new reciter. | High: asynchronous playback races and cache/download state. | Change from Reader Settings and player while stopped, playing, paused, and loading; same verse restarts; stale request cannot win; relaunch persistence. |
| 3 | Note attachments missing in Reading mode. `ReadingScreen.kt` receives attachment state and exposes an Attachments sheet/count, but its document list does not render attachment rows. The Frozen Editor amendment explicitly requires compact document-adjacent rows. | Stage 4 omitted attachments from the reading document flow. | Render real attachments compactly after the note body/tables without requiring Edit. | Medium: attachment opening, mixed content, and long-note scroll. | No/one/many attachments; image/file types; open; restored note; long body; Light/Dark/OLED. |
| 4 | Note attachments dominate Edit mode. `EditorScreen.kt` conditionally inserts previews only when the body is unfocused and gives image previews up to 320 dp height inside a separately constrained layout. | Focus-dependent insertion plus large previews causes reflow and can squeeze the editable body, resembling a takeover rather than document flow. | Keep body primary; use compact stable attachment rows in document flow; remove focus-triggered layout jumps while preserving add/open/remove handlers. | High: IME, focus, selection, and scroll ownership. | Keyboard open/close; focus transitions; large and multiple images; long note; cursor visibility; attachment CRUD; save/reopen. |
| 5 | PDF Explorer edge-swipe conflict. `VaultMobileWebShell.kt` enables `ModalNavigationDrawer` gestures globally; the PDF route has no suppression input. | Global drawer gesture competes with horizontal PDF pan/zoom. | Disable drawer edge gesture only while the PDF reader route is active; retain hamburger opening and all other routes' edge gesture. | Medium: shared shell route state and Back behavior. | Zoomed/unzoomed horizontal pan near both edges; hamburger; Back; rotate/recompose; leave PDF and confirm gesture returns. |

### B. Direct Consistency Refinements - Resolved

| # | Issue and evidence | Current values/cause | Approved refinement | Risk | Runtime tests |
|---:|---|---|---|---|---|
| 6 | Surah picker typography. `QuranSelectorSheets.kt` uses approximately 13 sp English, 20 sp Arabic, and 10.5 sp metadata. | Real-device reading is smaller than intended even though row density is acceptable. | Test one restrained visual step: English about 14 sp, Arabic 21 sp, metadata 11.5 sp. Preserve row height unless wrapping proves necessary. | Low/Medium: long names and accessibility scaling. | 360/412/430 dp; longest English/Arabic names; Meccan/Medinan and Juz metadata; 1.0x and larger font scale. |
| 7 | Editor paragraph control. `EditorToolbar.kt` shows the full `Paragraph` label. | The label consumes excessive horizontal space. | Display compact style tokens `P`, `H1`, `H2`, `H3`, `H4`; keep the existing style menu and handlers. | Low. | Every style selection, selected state, mixed RTL, narrow width, TalkBack label remains descriptive. |
| 8 | Editor toolbar spacing. Toolbar items use visible 32 dp minima plus internal horizontal padding and gaps. | Combined visible padding makes the control rail loose on a phone. | Tighten visible spacing/padding while preserving expanded invisible hit targets and every formatting action. | Medium: touch targets and horizontal scrolling. | 360/412/430 dp; primary and overflow actions; selected states; keyboard; TalkBack targets. |
| 9 | PDF Activity typography. The Frozen activity implementation uses 11 sp primary rows and 8 sp metadata; its header file label and filter labels are also 8 sp. | Real-device Arabic excerpts and secondary content are too small. | Increase row title/excerpt to about 12 sp and metadata/filter text to about 9.5-10 sp, using shared semantic tokens where possible. | Medium: two-line excerpts and sheet density. | English, Arabic, mixed excerpts; long note titles; all filters; empty state; 412 dp height. |
| 10 | Current-page PDF annotation-sheet typography. `FrozenSheetRow` uses 12 sp/9 sp while several section labels remain 8 sp. | Annotation content and metadata are difficult to scan. | Increase excerpts/note text and small labels one restrained step without enlarging the sheet architecture. | Medium: constrained sheet height. | Many highlights/notes/links; Arabic; scroll; keyboard in note entry; Dark/OLED. |
| 11 | PDF annotation type distinction. Current Frozen rows select different icons for page note, draw/highlight, and Study link, but highlight rows do not use their saved colour cue consistently. | Existing semantic data is present but the row mapping is too visually neutral. | Within the approved row language: marker/highlighter plus saved colour for Highlight, comment/note icon for Note, link/reference icon for Study Link. No new surface. | Low/Medium: contrast for pale colours and legacy annotations. | Every type; every supported colour; legacy/null values; Light/Dark/OLED; TalkBack type label. |
| 12 | Explorer typography. `VaultMobileWebShell.kt` currently uses about 13 sp application rows, 12 sp tree rows, 10 sp counts, and 9.5 sp section labels. | The main hierarchy, not the profile header, is the likely undersized area. | Test application rows at 14 sp and tree rows at 13 sp first. Retain counts/section labels unless device evidence requires a smaller adjustment. Do not jump blindly to 17 sp. | Medium: long names, nesting, and vertical density. | 360/412/430 dp; deep trees; Arabic/mixed names; long titles; counts; collapsed/expanded states. |

### C. Frozen Design Amendment Result

| # | Requirement | Why implementation is blocked | Required amendment/test gate |
|---:|---|---|---|
| 13 | Global Search redesign | Implemented from the final Frozen Stage 9 amendment using real Notes, Folders, Files/PDFs, and Courses results. | **RESOLVED IN STAGE 9 C2** |
| 14 | Global directional navigation motion | Implemented as restrained 210 ms forward/reverse route slides with reduced-motion handling. | **RESOLVED IN STAGE 9 C4** |
| 15 | PDF primary Draw Highlight workflow and preset colour | Draw Highlight now supports colour-first repeated rectangle creation while genuine text selection remains intact. | **RESOLVED IN STAGE 9 C1** |
| 16 | Floating PDF annotation/highlight pill | Implemented with real highlight/note counts, current colour, activity access, system insets, and narration-player clearance. | **RESOLVED IN STAGE 9 C1** |
| 17 | Outgoing Study Share | No outgoing production handler or frozen sharing semantics exists. | Decide exported content, rich-text conversion, title/body format, and attachments before UI. |
| 18 | Outgoing Library Share | No outgoing production handler or frozen sharing semantics exists. | Decide underlying file/link/metadata, permissions, and failure states before UI. |
| 19 | Workspace Attachments final placement | Final Explorer destination implemented and temporary Study FAB entry removed. | **RESOLVED IN STAGE 9 C2** |
| 20 | Aggregate Favourites final placement | Final Explorer destination implemented and temporary Study FAB entry removed. | **RESOLVED IN STAGE 9 C2** |
| 21 | Dashboard final redesign | Frozen compact Continue, Recent, and Pinned presentation now uses truthful production state only. | **RESOLVED IN STAGE 9 C2** |
| 22 | Global narration mini-player final design | One measured collapsed/expanded player now preserves the existing engine and dynamically clears Note, PDF, FAB, and system-inset surfaces. | **RESOLVED IN STAGE 9 C3** |

### D. Stage 10 / Compatibility

| # | Requirement | Current evidence/preservation | Stage 10 or compatibility gate |
|---:|---|---|---|
| 23 | Google Drive debug OAuth | Local debug certificate SHA-1 is `E7:1E:6D:03:41:65:44:79:8B:A7:F9:0A:D1:26:F4:A0:23:CB:BD:85`. The checked-in `google-services.json` identifies `com.myvault.app` but contains no Android OAuth client entry. Cloud credential state was not changed or independently queried. | Configure/verify the matching debug Android OAuth client separately. This is not a Stage 9 UI fix and does not by itself prove a release regression. Stage 10 must use a correctly signed/configured build. |
| 24 | Full destructive Android/Web backup and restore | Explicitly deferred throughout Stages 3-8; no destructive run is appropriate in this audit. | Stage 10 account-isolation, Android backup/restore, Web parse/restore, and cross-version round trip. |
| 25 | Library legacy view-mode preferences | Stored values are retained while the selector is absent. | Revisit only if a demonstrated runtime conflict appears; no destructive migration. |
| 26 | Study batch pin semantics | Production batch handler must be proven to target `isPinned` or `isFolderPinned`. | STOP-AND-ASK before wiring or changing batch pin behavior. |
| 27 | Memorise dormant repeat modes | 3x, 5x, 10x, and Until Stopped remain engine-only and intentionally unreachable. | Preserve compatibility; future visible placement requires an amendment. |
| 28 | Memorise active-session backup regression | Active recording/concealment/analysis remains device-local; records/attempts remain backed up. | Verify the approved boundary in the Stage 10 destructive round trip; never fabricate active recording restoration. |
| 29 | Pinned-expanded preference backup gap | Persisted preference and backup mapper remain intentionally unaligned. | Compatibility decision and old/new client testing before any backup-field change. |

## 4. Resolved Confirmation

`Qur'an -> More -> Memorise from here` remains **RESOLVED IN STAGE 8**.
Stage 8 documentation and runtime evidence record exact-ayah routing, immediate
recording when permission exists, and permission-then-auto-record otherwise.
It is not reopened by this audit.

## 5. Recommended Stage 9 Order

### Batch A1 - Isolated Functional Regressions

1. Reading-mode attachment visibility.
2. Edit-mode attachment layout/focus stability.
3. PDF route drawer-gesture suppression.

Build and run focused Note/PDF tests before touching Qur'an playback state.

### Batch A2 - Qur'an Functional Regressions

1. Tafsir sheet/dismissal correction.
2. Reciter single-source synchronization and stale-request protection.

Build, run Qur'an tests, and perform real audio/runtime verification.

### Batch B - Compact UI and Typography

1. Editor paragraph token and toolbar spacing.
2. Surah picker typography.
3. PDF Activity and current-page annotation typography.
4. PDF annotation-type distinction.
5. Explorer typography, last because it affects every route.

Each change should use before/after 412 x 892 captures and smaller/larger-width
checks. These items do not authorize new surfaces.

### Batch C - Motion Amendment Gate

Do not implement global directional route motion until a Frozen motion amendment
is approved. After approval, implement it as its own batch and regression-test
predictive Back, sheets, dialogs, root navigation, and reduced motion.

### Batch D - Frozen Amendment Items

Return to the Frozen prototype before production work on:

- Global Search;
- primary Draw Highlight workflow/preset colour;
- floating PDF annotation/highlight pill;
- final Attachments/Favourites destinations;
- Dashboard;
- global narration mini-player;
- future outgoing Share semantics.

Stage 10/compatibility items remain outside Stage 9.

## 6. Newly Discovered Regressions

The audit found four concrete Stage 4/7 implementation regressions:

1. Tafsir is inline even though the approved amendment specifies a sheet.
2. Reciter UI state is shared, but playback switching can remain asynchronous
   enough for the old reciter/request to continue.
3. Reading mode owns attachment state but omits attachment rows from the
   document flow.
4. Edit-mode attachment visibility and large previews are focus-dependent and
   can destabilize the note-body layout.

These are classified as direct fixes because the approved Frozen contracts
already answer their presentation and no data/engine redesign is required.

## 7. Final Amendment Implementation

The final Frozen checkpoint
`myvault-ui-prototype-frozen-stage-9-refinement-20260828-181132-AEST`
authorized and resolved Batches C1-C4. Production now includes the compact PDF
annotation pill and repeated Draw Highlight flow, final Search and Dashboard,
Explorer Attachments/Favourites destinations, the collapsed/expanded global
Note/PDF narration player, and directional route motion.

Runtime verification at the 412 x 892 logical reference viewport confirmed:

- repeated PDF rectangles increased the real count from one to three while Draw
  mode remained active;
- Explorer final destination order and real Attachments/Favourites routes;
- truthful Search and Dashboard states;
- Device TTS playback with collapsed/expanded player states;
- dynamic Note Edit, PDF pill, and FAB clearance around the measured player;
- PDF pill and expanded narration player visible together without overlap.

Canonical Qur'an data, audio engines, PDF rendering/geometry, Room schema,
backup format, note serialization, and repository contracts remain unchanged.
Outgoing Study/Library Share and all Stage 10 compatibility items remain open.
