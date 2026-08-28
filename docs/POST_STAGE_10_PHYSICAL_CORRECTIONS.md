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

## Verification Boundary

JBR 21 compilation, unit tests, Android lint and debug APK assembly passed after each implemented batch. Emulator checks covered installation, launch, Explorer restart persistence, and the updated Surah picker.

The following remain release gates until a physical device is connected:

- repeated Draw Highlight touch workflow and colour chooser;
- PDF pinch, pan and annotation alignment;
- Course Continue/hierarchy proportions with real Course data;
- selected-ayah motion on a physical display;
- release-signed Google Drive sign-in, upload, discovery and controlled restore.

