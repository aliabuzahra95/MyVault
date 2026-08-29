# Post-Stage-10 Physical-Device Corrections

Starting checkpoint: `35d144b99a88a4adda18929f7d719de94438d57f`

Safety tag: `stage-10-pre-physical-corrections`

## Implemented

### P1 - Explorer, Study and Library

- Increased Explorer destination and hierarchy text one restrained step.
- Reduced nested indentation and row spacing so long names retain more width.
- Persisted workspace-scoped Explorer expansion state in device-local DataStore state.
- Added short vertical Study/Library hierarchy expansion and collapse motion.
- Added restrained main-folder/subfolder icon and indentation distinction.
- Increased Workspace Attachments and Favourites supporting typography.

Explorer expansion state is intentionally device-local and is not added to backup.

### P2 - PDF

- Confirmed Draw Highlight commits through the existing overlay on finger release.
- Preserved repeated rectangle creation while Draw Highlight remains active.
- Renamed the single draw-mode completion action to `Exit`.
- Corrected highlight-colour chooser z-order; colour changes update current draw state immediately.
- Routed annotation pill counts to a local PDF bottom sheet with All, Highlights, Notes and Study links filters.
- Retained full PDF Activity through `View all activity`.
- Added explicit PDF Back-state priority before route exit.
- Increased PDF reader header, local activity and full activity typography.
- Added compact real highlight, note and reading-progress metadata to Library PDF rows.

No PDF geometry, annotation repository or backup representation changed.

### P3 - Courses, Dashboard and Qur'an

- Widened Course Continue to the normal content margins.
- Tightened Course-only hierarchy rows and added nested-folder icon distinction.
- Increased Dashboard section, row and pinned metadata typography.
- Increased Surah picker English, Arabic, metadata, count and section typography one step.
- Added short selected-ayah tonal-surface and contextual-action motion.

Global directional navigation motion is unchanged.

## P4 - Folder Colours

**DEFERRED - COMPATIBILITY DESIGN REQUIRED**

`FolderEntity` has no colour field. Correct persistence would require a Room schema migration and explicit backup/Web compatibility changes. A device-local workaround would fail the requested backup/restore behavior. The optional feature was therefore not implemented in this correction pass.

## Final Physical-Device Verification

Verified with the production-signed release candidate on a Samsung SM-S948B:

- Explorer typography, long-name handling and persisted expansion state passed after force-stop/relaunch.
- Draw Highlight auto-committed on finger release and remained active for repeated rectangles.
- The colour chooser rendered above the PDF pill and remained inside the viewport.
- Annotation counts opened the local half-height sheet; system Back closed PDF overlays before leaving the reader.
- Physical pinch zoom, pan, annotation alignment and PDF edge-swipe suppression passed.
- Course Continue proportions, compact hierarchy, Dashboard typography, Surah picker typography and selected-ayah actions passed with real production data.

The signed Google Drive gate passed with the connected production account:

- a fresh 229-file Drive backup uploaded and finalised successfully;
- the latest-backup timestamp and restore discovery updated correctly;
- the fresh backup downloaded, verified, rebuilt files, restored the database and finalised successfully;
- MyVault relaunched with the Study hierarchy and pinned content present.

JBR 21 compilation, unit tests, Android lint, debug APK assembly and release APK assembly passed. The release candidate uses package `com.myvault.app`, version `0.1.0` (`1`), SHA-256 `e3fdf20fe39ba0b3c27864620f9bece381741fabaf97a5cd03d8143ce2467674`, and production certificate SHA-1 `77:D0:EE:6A:B8:DF:03:59:6D:50:B7:13:68:58:03:D7:76:F9:18:16`.
