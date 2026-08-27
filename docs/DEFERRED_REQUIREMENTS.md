# Deferred Requirements Register

This is the production-side register for approved temporary placements,
unresolved Frozen Design Master gaps, and later-stage design decisions. The
Frozen prototype directory remains read-only.

## Active Deferred Requirements

| Requirement | Current preservation / temporary access | Resolution gate | Status |
|---|---|---|---|
| Outgoing Study Share | No action shown; inbound Send-to-MyVault/import remains unchanged | Separate sharing semantics/design decision | **DEFERRED - NEW FUNCTIONALITY** |
| Outgoing Library Share | No action shown; inbound Send-to-MyVault/import remains unchanged | Separate sharing semantics/design decision | **DEFERRED - NEW FUNCTIONALITY** |
| Workspace Attachments final placement | Study root FAB -> Tools -> Attachments | Final Frozen destination amendment | **TEMPORARY PLACEMENT** |
| Aggregate Favourites final placement | Study root FAB -> Tools -> Favourites | Final Frozen destination amendment | **TEMPORARY PLACEMENT** |
| Qur'an Reflections Hub final placement | Study root FAB -> Tools -> Qur'an Reflections | Before Stage 7 | **TEMPORARY PLACEMENT** |
| Dashboard final redesign | Current production presentation preserved | Dedicated Frozen reference/amendment | **DEFERRED** |
| Global Search final redesign | Current production presentation preserved | Dedicated Frozen reference/amendment | **DEFERRED** |
| Global narration mini-player design/placement | Current production presentation and behavior preserved | Stop if it collides with Frozen surfaces; dedicated design amendment | **DEFERRED** |
| Narration Settings placement | Reading & Listening, including Azure subpage | Implemented and verified in Stage 3; mini-player remains separate | **RESOLVED IN STAGE 3; MINI-PLAYER NOT RESOLVED** |
| Note metadata placement | Timestamp and word/character counts remain in production | Stage 4 Frozen placement decision | **STOP-AND-ASK** |
| Quote formatting command | Engine can render/serialize Quote; current main toolbar has no visible command | Approve adapter command for Frozen Quote control | **STOP-AND-ASK** |
| Clear-formatting command | Production clears selected colour but has no proven all-formatting command | Define exact semantics for Frozen Clear control | **STOP-AND-ASK** |
| URL link vs structured note-link controls | Both engines remain preserved and distinct | Approve chooser/secondary formatting presentation | **STOP-AND-ASK** |
| Note tables and advanced block controls | Table engine and dormant Checklist/Divider compatibility preserved | Stage 4 block/context presentation decision | **DEFERRED** |
| Note attachments management | Existing file/image import, previews and storage preserved | Stage 4 Attachments sheet/block anatomy decision | **DEFERRED** |
| Note knowledge surfaces | Tags, backlinks and PDF source references remain distinct | Stage 4 Knowledge & references placement decision | **DEFERRED** |
| Note version history / restore | Snapshot and restore engines preserved | Stage 4 dedicated history presentation decision | **DEFERRED** |
| Note export | Existing TXT/PDF callbacks preserved | Stage 4 Export placement decision | **DEFERRED** |
| Note narration entry and reading state | Listen, provider choice, selection narration and follow state preserved | Stage 4 entry/state amendment; player remains separately deferred | **DEFERRED** |
| Note formatting-provider actions | Structure Only, Intelligent Structure and result actions preserved; hidden retained actions not exposed | Stage 4 secondary sheet placement decision | **DEFERRED** |
| Reading-to-edit double-tap | Existing gesture preserved pending approval | Approve as invisible compatibility gesture alongside Frozen Edit control | **PROPOSED FOR APPROVAL** |
| Advanced PDF functions | Existing production functionality preserved | Pre-Stage-5 placement decisions | **DEFERRED** |
| Qur'an audio-download management | Existing contextual route preserved | Before Stage 7 | **DEFERRED** |
| Library legacy view-mode preferences | Stored values retained; no Frozen selector | Only revisit on a demonstrated runtime conflict | **DEFERRED COMPATIBILITY** |
| Study batch pin semantics | Must prove whether production batch handler changes `isPinned` or `isFolderPinned` | Before wiring that batch action | **STOP-AND-ASK** |

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
| Qur'an translation text size | Existing reader control is absent from Frozen screenshot | Preserve in contextual sheet or approve omission | **STOP-AND-ASK BEFORE STAGE 7** |
| Qur'an playback speed | Existing audio control is absent from Frozen Settings sheet | Keep in audio controls or place in reader Settings | **STOP-AND-ASK BEFORE STAGE 7** |
| Pinned-expanded preference backup gap | Persisted model and backup mapper are not aligned | Do not repair opportunistically; address in compatibility work if authorized | **DEFERRED COMPATIBILITY** |

## Separation Of Narration Concerns

- **Narration Settings**: provider, Azure credentials/region, and voice choices.
  These have a proposed Stage 3 Settings hierarchy.
- **Global Narration Mini-player**: playback presentation and global placement.
  This remains deferred and is not resolved by placing narration Settings.

## Register Rule

A temporary destination is not a final design decision. Entries remain active
until an explicit approved amendment resolves them. Do not silently close an
entry because a route is currently reachable.
