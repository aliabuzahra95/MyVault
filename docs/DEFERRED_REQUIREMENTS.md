# Deferred Requirements Register

This is the production-side register for approved temporary placements,
unresolved Frozen Design Master gaps, and later-stage design decisions. The
Frozen prototype directory remains read-only.

## Active Deferred Requirements

| Requirement | Current preservation / temporary access | Resolution gate | Status |
|---|---|---|---|
| Outgoing Study Share | No action shown; inbound Send-to-MyVault/import remains unchanged | Separate sharing semantics/design decision | **DEFERRED - NEW FUNCTIONALITY** |
| Outgoing Library Share | No action shown; inbound Send-to-MyVault/import remains unchanged | Separate sharing semantics/design decision | **DEFERRED - NEW FUNCTIONALITY** |
| Workspace Attachments final placement | Final Explorer destination invokes the existing production route; temporary Study FAB entry removed | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Aggregate Favourites final placement | Final Explorer destination invokes the existing aggregate note-favourites state; temporary Study FAB entry removed | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Qur'an Reflections Hub final placement | Reader overflow -> Reflections; temporary Study FAB entry removed | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Qur'an bookmark management and Save wording | Selected ayah uses Bookmark/Remove bookmark; reader overflow opens backed-up bookmark list | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Qur'an recent-location management | Reader overflow opens the real latest-five locations | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Qur'an translation footnotes | Maududi explanatory footnotes expand inline from real production content | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Qur'an Tafsir source management | Reader settings and the dismissible Tafsir sheet use the production source list and states | Frozen Qur'an amendment plus direct Stage 9 dismissal correction | **RESOLVED IN STAGE 7; DISMISSAL FIXED IN STAGE 9** |
| Qur'an Surah picker filters/Juz grouping | Full-screen picker retains All/Meccan/Medinan and Juz grouping | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Qur'an reader-to-Memorise handoff | Selected ayah More -> Memorise from here hands the exact verse to the existing Memorise destination | Implemented from Frozen Qur'an amendment; detailed Memorise UI remains Stage 8 | **RESOLVED IN STAGE 7** |
| Qur'an double-tap save-position gesture | Existing invisible gesture remains and updates the backed-up reading position | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Dashboard final redesign | Truthful Continue, Recent, and Pinned sections use the final compact Frozen presentation | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Global Search final redesign | Compact initial, empty, and grouped real-result states use existing production navigation | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Global narration mini-player design/placement | Final collapsed/expanded Note/PDF player preserves the engine and uses measured collision offsets | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Narration Settings placement | Reading & Listening, including Azure subpage | Implemented and verified in Stage 3; mini-player remains separate | **RESOLVED IN STAGE 3; MINI-PLAYER NOT RESOLVED** |
| Note metadata placement | Note info sheet: Updated, Words, Characters | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Quote formatting command | Existing serializable Quote mark receives the frozen direct toolbar adapter | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Clear-formatting command | Only proven `Clear selected colour` is exposed; no generic clear-all semantics | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| URL link vs structured note-link controls | Kept distinct as Add web link and Link to note under More Formatting | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note tables and advanced block controls | Table uses frozen More Formatting/context surfaces; dormant Checklist/Divider stay engine-only | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note attachments management | Frozen Attachments sheet plus compact document-flow rows in Reading and Editing | Frozen Editor amendment plus direct Stage 9 regression correction | **RESOLVED; READING/EDIT LAYOUT FIXED IN STAGE 9** |
| Note knowledge surfaces | Tags, backlinks and PDF source references remain distinct in Knowledge & references | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note version history / restore | Frozen Version history and restore-confirmation surfaces | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note export | Frozen Export surface invokes existing TXT/PDF callbacks | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note narration entry and reading state | Listen sheet includes Device, Azure and approved production OpenAI row; selection/follow state preserved | Frozen Editor amendment; player separately deferred | **RESOLVED FOR STAGE 4; MINI-PLAYER NOT RESOLVED** |
| Note formatting-provider actions | Structure Only, Intelligent Structure and result actions use frozen secondary sheet; hidden actions remain unexposed | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Reading-to-edit double-tap | Preserved as invisible compatibility gesture alongside frozen Edit affordance | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| OpenAI note narration provider row | Existing `NarrationProvider.OpenAi` handler shown as the approved third restrained Listen provider | Stage 4 production-specific decision | **RESOLVED FOR STAGE 4** |
| PDF text selection and Copy | Genuine AndroidX selected text and ordered page rectangles feed the Frozen selection actions | Frozen PDF amendment plus additive compatibility decision | **RESOLVED FOR STAGE 5** |
| Manual rectangle highlight vs selected-text highlight | Draw Highlight is the primary repeated colour-first rectangle workflow; selected-text highlight and geometry remain supported | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| PDF text-box annotation lifecycle | Historic valid text boxes are preserved and read-only; no creation/editing UI is exposed | Frozen PDF compatibility decision | **RESOLVED FOR STAGE 5 COMPATIBILITY** |
| PDF Activity final internal design | Contextual Activity route uses frozen search, filters, rows, navigation and annotation actions | Frozen PDF amendment | **RESOLVED FOR STAGE 5** |
| PDF page notes and annotation actions | Current-page sheet, page notes, colour/note/tag/link/create/delete actions use frozen secondary surfaces | Frozen PDF amendment | **RESOLVED FOR STAGE 5** |
| PDF annotation tags and linked Study-note actions | Distinct tag, link-existing-note, create-note and reference-navigation flows are retained | Frozen PDF amendment | **RESOLVED FOR STAGE 5** |
| PDF narration entry | Reader overflow and selected-text More expose existing Device/OpenAI/Azure narration engines | Frozen PDF amendment | **RESOLVED FOR STAGE 5; GLOBAL PLAYER STILL DEFERRED** |
| PDF Save to device and Delete placement | Kept Library-owned; no duplicate reader actions | Frozen PDF amendment | **RESOLVED FOR STAGE 5** |
| PDF page-jump and immersive triggers | Page counter/overflow open page jump; overflow enters immersive and an unclaimed canvas tap restores chrome | Frozen PDF amendment | **RESOLVED FOR STAGE 5** |
| PDF multi-rectangle backup compatibility | Optional ordered geometry extension plus legacy representative rectangle; malformed extension falls back safely | Approved additive Stage 5 compatibility extension | **RESOLVED FOR STAGE 5; DESTRUCTIVE ROUND TRIP IN STAGE 10** |
| Full destructive backup/restore regression | Controlled production export, clear and restore completed; source-backlink coordinate regression corrected and exact multi-rectangle geometry retained | Stage 10 Android/Web regression | **RESOLVED IN STAGE 10** |
| Qur'an audio-download management | Reader overflow opens the existing real download manager; expanded player exposes current-Surah download | Implemented from Frozen Qur'an amendment | **RESOLVED IN STAGE 7** |
| Library legacy view-mode preferences | Stored values remain safely parsed/backed up but do not alter the Frozen Corpus Browser; no selector or migration | Stage 10 compatibility audit | **RESOLVED AS HARMLESS LEGACY STATE** |
| Study batch pin semantics | Repository history proves the batch action invokes workspace-wide `isPinned`; Stage 10 restores the accidentally unmounted selection bar without changing pin fields | Stage 10 regression verification | **RESOLVED IN STAGE 10** |
| PDF annotation/count pill refinement | Real highlight/note counts, selected colour, Draw entry, Activity access, insets, and player collision are implemented | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| Preset PDF highlight-colour workflow refinement | Four existing production colours are selectable before repeated Draw Highlight rectangles | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| PDF Activity typography refinement | Restrained one-step typography increase applied without changing row architecture | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| Explorer font-size refinement | Primary application/tree labels increased one restrained step; metadata/counts preserved | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| PDF reader Explorer edge-swipe disable | Drawer gesture disabled on the PDF route only; hamburger and other routes retained | Authorized Stage 9 Batch A1 | **RESOLVED IN STAGE 9** |
| Memorise empty state and target entry | Existing targets route directly; new targets begin from the Qur'an handoff and whole-Surah entry without invented overview chrome | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Qur'an-to-Memorise exact-ayah session handoff | Exact ayah opens a dedicated session and auto-records immediately or after permission grant | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise Revision schedule language | No scheduler language is shown; only truthful production statuses/counts | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise manual status actions | Exact five production statuses use the compact Set status surface | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise attempt history | Real latest ayah and whole-Surah attempts have History and Detail destinations | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Whole-Surah memorisation test | Existing whole-Surah engine is reachable through the Frozen session language | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise microphone permission/error states | Request, denied/settings, capture failure, and empty recording use Frozen recovery states | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise speech-provider selection | Existing transient Google Chirp/OpenAI choice uses the secondary provider sheet | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise recording review and transcription failures | Playback, re-record, retry, provider/network errors, timeout, empty transcript and analysis failure preserve captured audio | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise result-state mapping | Correct, missing, extra, repeated, and unclear states use canonical overlays; extras remain separate | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise session exit/end-of-Surah/process restoration | Dedicated Back, Next/Surah complete, and process-safe non-recording restoration implemented | Final Frozen Memorise amendment | **RESOLVED IN STAGE 8** |
| Memorise canonical repeat modes | 3x, 5x, 10x, and Until Stopped remain compatible engine-only values and are not exposed by Frozen UI | Stage 10 compatibility audit | **RESOLVED AS DORMANT COMPATIBILITY** |
| Memorise active-session backup | Live recording/conceal/analysis state remains intentionally transient; persisted records and attempts remain in the backup contract | Stage 10 compatibility audit; populated-attempt device fixture still desirable | **RESOLVED DEVICE-LOCAL MAPPING** |

## Pre-Stage 9 Real-Device Audit Additions

The complete 29-item classification and evidence are frozen production-side in
`docs/STAGE_9_REFINEMENT_PLAN.md`. The following newly observed items were not
previously explicit rows in this register:

| Requirement | Preservation / evidence | Resolution gate | Status |
|---|---|---|---|
| Qur'an Tafsir dismissal | Approved modal sheet now has fixed header/X, independent body scroll, Back-first and swipe dismissal | Authorized Stage 9 Batch A2 | **RESOLVED IN STAGE 9** |
| Qur'an reciter synchronization | One selected state now drives Settings, player and playback; stale preparation is cancelled/ignored | Authorized Stage 9 Batch A2 | **RESOLVED IN STAGE 9** |
| Qur'an Surah picker typography | English, Arabic and metadata increased one restrained step with density retained | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| Note attachments in Reading mode | Real compact attachment rows render in document flow and use existing open handlers | Authorized Stage 9 Batch A1 | **RESOLVED IN STAGE 9** |
| Note attachment domination in Edit mode | Focus-dependent large previews replaced by stable compact document-flow rows | Authorized Stage 9 Batch A1 | **RESOLVED IN STAGE 9** |
| Editor paragraph token | Compact `P`/`H1`-`H4` tokens retain the existing style chooser and semantics | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| Editor toolbar spacing | Visible padding tightened while controls and horizontal access remain intact | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| Global directional route motion | Ordinary routes use restrained 210 ms forward/reverse slides; reduced motion disables translation | Final Frozen Stage 9 amendment | **RESOLVED IN STAGE 9** |
| PDF current-page annotation typography | Excerpts/metadata increased one restrained step without changing sheet architecture | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| PDF annotation type distinction | Highlight, Note and Study Link now use distinct semantic icons; highlights retain their saved colour cue | Authorized Stage 9 Batch B | **RESOLVED IN STAGE 9** |
| Google Drive debug OAuth | Debug SHA is separate and remains unconfigured; release candidate uses the established production certificate | Debug setup is optional; live release-account round trip remains required | **DEBUG DEFERRED; RELEASE DEVICE TEST BLOCKED** |

## Resolved Stage 6 Course Decisions

| Requirement | Approved production mapping | Status |
|---|---|---|
| Course progress | Completion UI is absent; show only truthful Course Note count and last-opened Continue | **RESOLVED IN STAGE 6** |
| Course description | Omit because production has no persisted Course description | **RESOLVED IN STAGE 6 - OMITTED** |
| Course search | Omit Course-specific Search; global Search remains separately deferred | **RESOLVED IN STAGE 6 - FUTURE NEW FUNCTIONALITY ONLY** |

## Resolved Stage 3 Settings Decisions

| Requirement | Why unresolved | Required decision | Status |
|---|---|---|---|
| Theme model migration | Legacy `theme` plus optional `themeModeV2`; invalid/missing V2 falls back safely | Backward-compatible additive mapping | **RESOLVED IN STAGE 3** |
| Material You portability | Dynamic colour is device-specific | Device-local preference; not backed up | **DEVICE-LOCAL / NOT BACKED UP** |
| Frozen Account/profile row | No unified production MyVault account exists | Informational real workspace context only | **RESOLVED IN STAGE 3** |
| Google Drive Disconnect | No user-facing production handler exists | No Disconnect action; preserve supported connect/check/change-account behavior | **RESOLVED - ACTION NOT AUTHORIZED** |
| Release readiness checklist | Developer/release checklist is absent from Frozen Settings | Hidden from normal Settings; diagnostic implementation preserved | **RESOLVED IN STAGE 3** |
| Automatic tag suggestions | Persisted/backed up compatibility state | Remains hidden and unchanged | **RESOLVED AS HIDDEN COMPATIBILITY** |
| Legacy general font size | Persisted/backed up compatibility state | Remains hidden and unchanged | **RESOLVED AS HIDDEN COMPATIBILITY** |
| Formatting-account product name | Supabase-backed formatting session | Visible neutral name `Formatting account` | **RESOLVED IN STAGE 3** |
| Storage usage categories | Production can calculate only total local MyVault size | Show truthful total only; no fabricated category bars | **APPROVED PRODUCTION-SPECIFIC MAPPING** |
| Recently Deleted item types | Production supports deleted notes/folders only | Show only real notes/folders; no PDF-trash mock or new engine | **APPROVED PRODUCTION-SPECIFIC MAPPING** |
| Qur'an translation text size | Existing independent 80-130% reader preference is backed up | Compact contextual Reader Settings slider | **RESOLVED IN STAGE 7** |
| Qur'an playback speed | Existing global 0.5x/1x/1.5x/2x backed-up audio preference | Compact expanded-player choices | **RESOLVED IN STAGE 7** |
| Pinned-expanded preference backup gap | Persisted model and backup mapper are not aligned | Do not repair opportunistically; address in compatibility work if authorized | **DEFERRED COMPATIBILITY** |

## Separation Of Narration Concerns

- **Narration Settings**: provider, Azure credentials/region, and voice choices.
  These have a proposed Stage 3 Settings hierarchy.
- **Global Narration Mini-player**: final Note/PDF playback presentation and
  measured global placement were resolved by the Frozen Stage 9 amendment.

## Register Rule

A temporary destination is not a final design decision. Entries remain active
until an explicit approved amendment resolves them. Do not silently close an
entry because a route is currently reachable.
