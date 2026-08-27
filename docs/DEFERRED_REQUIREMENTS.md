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
| Note metadata placement | Note info sheet: Updated, Words, Characters | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Quote formatting command | Existing serializable Quote mark receives the frozen direct toolbar adapter | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Clear-formatting command | Only proven `Clear selected colour` is exposed; no generic clear-all semantics | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| URL link vs structured note-link controls | Kept distinct as Add web link and Link to note under More Formatting | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note tables and advanced block controls | Table uses frozen More Formatting/context surfaces; dormant Checklist/Divider stay engine-only | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note attachments management | Frozen Attachments sheet plus document-flow attachment presentation | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note knowledge surfaces | Tags, backlinks and PDF source references remain distinct in Knowledge & references | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note version history / restore | Frozen Version history and restore-confirmation surfaces | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note export | Frozen Export surface invokes existing TXT/PDF callbacks | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Note narration entry and reading state | Listen sheet includes Device, Azure and approved production OpenAI row; selection/follow state preserved | Frozen Editor amendment; player separately deferred | **RESOLVED FOR STAGE 4; MINI-PLAYER NOT RESOLVED** |
| Note formatting-provider actions | Structure Only, Intelligent Structure and result actions use frozen secondary sheet; hidden actions remain unexposed | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
| Reading-to-edit double-tap | Preserved as invisible compatibility gesture alongside frozen Edit affordance | Frozen Editor amendment | **RESOLVED FOR STAGE 4** |
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
