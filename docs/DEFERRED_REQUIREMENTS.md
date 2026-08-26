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
| Narration Settings placement | Proposed under Reading & Listening, including Azure subpage | Pre-Stage-3 Settings amendment approval | **PROPOSED; MINI-PLAYER NOT RESOLVED** |
| Advanced Note Editor functions | Existing production functionality preserved | Pre-Stage-4 placement decisions | **DEFERRED** |
| Advanced PDF functions | Existing production functionality preserved | Pre-Stage-5 placement decisions | **DEFERRED** |
| Qur'an audio-download management | Existing contextual route preserved | Before Stage 7 | **DEFERRED** |
| Library legacy view-mode preferences | Stored values retained; no Frozen selector | Only revisit on a demonstrated runtime conflict | **DEFERRED COMPATIBILITY** |
| Study batch pin semantics | Must prove whether production batch handler changes `isPinned` or `isFolderPinned` | Before wiring that batch action | **STOP-AND-ASK** |

## Pre-Stage-3 Settings Decisions

| Requirement | Why unresolved | Required decision | Status |
|---|---|---|---|
| Theme model migration | Production only has Light/Dark/Auto; Frozen adds Material You, OLED, and two system-following variants | Legacy mapping, defaults, persistence, and backup compatibility | **STOP-AND-ASK** |
| Frozen Account/profile row | No unified production MyVault account exists | Define whether row is informational, workspace profile, or future account destination | **STOP-AND-ASK** |
| Google Drive Disconnect | No user-facing production handler exists | Preserve Connect/change-account only or authorize new Disconnect behavior | **STOP-AND-ASK** |
| Release readiness checklist | Developer/release checklist is currently user-visible but absent from Frozen Settings | Keep, relocate to approved diagnostics, or remove from release UI | **STOP-AND-ASK** |
| Automatic tag suggestions | Persisted and backed up, but no visible row/setter/consumer was found | Confirm whether feature remains active and where it belongs | **STOP-AND-ASK** |
| Legacy general font size | Persisted/backed up but no active visible row/use was found | Preserve hidden compatibility or approve visible mapping | **DEFERRED / STOP-AND-ASK IF TOUCHED** |
| Formatting-account product name | Legacy label says ChatGPT; implementation is Supabase session | Approve final visible naming | **STOP-AND-ASK** |
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
