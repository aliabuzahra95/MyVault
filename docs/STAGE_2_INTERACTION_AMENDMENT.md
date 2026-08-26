# Stage 2 Production Interaction Amendment

Status: final approved Stage 2 interaction contract. Stage 2 implementation is
authorised only after this document is committed and pushed as its own recovery
checkpoint.

This document records the approved production-only secondary interactions for
the Study and Library Corpus Browser. The Frozen Design Master remains
authoritative for the primary screen, row, search, pinned-strip, FAB, and sheet
presentation. Nothing in this amendment authorises a permanent control or a
change to the approved primary Study/Library appearance.

## Legend

- **Approved**: placement and existing production handler are identified.
- **Deferred**: preserve the existing production route/state without redesigning
  or adding a new Frozen placement during Stage 2.
- **Stop and ask**: do not wire the action if the documented production semantics
  or handler cannot be verified exactly.
- **Direct**: visible in the first contextual/creation sheet.
- **More**: visible only after opening the restrained secondary actions sheet.

## Pin State Audit

### State 1: `NoteEntity.isPinned`

- **Meaning**: workspace-wide note pin. It feeds the compact, workspace-level
  Pinned strip/card collection.
- **Entities**: Study/Personal note entities only. Library files use the separate
  `AttachmentEntity.isPinned` field.
- **Current Android UI**: Home pinned cards, Home and folder note context actions,
  Note Editor, Reading screen, and course note pin actions.
- **Sorting/display**: `NoteDao.observePinned()` selects top-level notes with
  `isPinned = 1` for the workspace-level pinned collection. The tree mapper also
  promotes it to the beginning of its immediate folder/root ordering.
- **Persistence**: the full Android backup serialises and restores `isPinned`.
  The secondary API snapshot DTO also exports this state as `is_pinned`.
- **Frozen mapping**: the Frozen Design Master's `Pin/Unpin` explicitly controls
  the compact workspace-level Pinned strip. It therefore maps clearly to
  `isPinned`.

### State 2: `NoteEntity.isFolderPinned`

- **Meaning**: folder-local note pin/promotion. It keeps a note prominent within
  the folder currently being browsed without making it a workspace-level pinned
  card.
- **Entities**: Study/Personal note entities only.
- **Current Android UI**: Folder View's local pinned section and its distinct
  `Pin Note` / `Unpin Note` action.
- **Sorting/display**: the tree mapper promotes a note when either `isPinned` or
  `isFolderPinned` is true. Folder View additionally uses `isFolderPinned` for
  its folder-local pinned collection.
- **Persistence**: the full Android backup serialises and restores
  `isFolderPinned`. The secondary API snapshot DTO does not currently export
  this field.
- **Approved Frozen amendment**: expose this as the distinctly labelled
  `Pin within folder / Unpin within folder` action under Study note More.

### Relationship Between The States

- They are independent Boolean columns with separate DAO and repository setters.
- There is no database constraint or setter logic enforcing exclusivity.
- Both can therefore be active on the same note at the same time.
- They must not be merged, renamed, or made to overwrite one another.
- **Approved**: direct `Pin / Unpin` invokes `isPinned` only.
- **Approved**: More -> `Pin within folder / Unpin within folder` invokes
  `isFolderPinned` only.
- Library file pinning is a third, separate state
  (`AttachmentEntity.isPinned`) and is approved for the Library Pinned strip.

### Approved Pin Mapping

1. Direct `Pin / Unpin` maps only to `NoteRepository.setPinned` and therefore
   `NoteEntity.isPinned`.
2. More -> `Pin within folder / Unpin within folder` maps only to
   `NoteRepository.setFolderPinned` and therefore
   `NoteEntity.isFolderPinned`. It is shown only where the note belongs to a
   folder.
3. Library `Pin / Unpin` maps only to `LibraryViewModel.setFilePinned` and
   `AttachmentEntity.isPinned`.
4. Both Study note pin states remain independently serialised by the full
   Android backup and may be active simultaneously.
5. If an existing batch Pin handler cannot be proven to target exactly one of
   these two Study note fields, that batch action is a mandatory stop-and-ask.

Primary evidence:

- `data/local/entity/NoteEntity.kt`
- `data/local/dao/NoteDao.kt`
- `data/repository/NoteRepository.kt`
- `data/repository/UiMappers.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/FolderViewScreen.kt`
- `data/repository/BackupRepository.kt`
- `data/sync/SyncDtos.kt`

## Exact Action Hierarchy

### A. Study Root FAB Sheet

#### Primary creation

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| New note | Outlined note/add | Create note, then open editor | `HomeViewModel.createNote(folderId = null, mode = FOLDER_MODE_STUDY)` | Direct | Study | Approved |
| New folder | Outlined folder/add | New root-folder form | `HomeViewModel.createFolder(parentId = null, mode = FOLDER_MODE_STUDY)` | Direct | Study | Approved |
| Import file | Outlined upload/document | Android document picker, then import | `ActivityResultContracts.OpenDocument` -> `HomeViewModel.importDocument(..., mode = FOLDER_MODE_STUDY)` | Direct | Study | Approved |

#### Organise utility

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Select / organise existing items | Outlined checklist/reorder | Study Organise sheet | Enters existing `manageSelectionMode` or `organizeMode` presentation state | Direct under `ORGANISE` section | Study | Approved |

The nested **Study Organise** sheet exposes only existing engines:

| Label | Icon concept | Destination | Existing handler/state | Level | Status |
| --- | --- | --- | --- | --- | --- |
| Select items | Outlined checklist | Existing batch-selection mode | `manageSelectionMode = true` | Direct | Approved |
| Reorder folders | Outlined vertical reorder | Existing folder organise mode | `organizeMode = true`; movement invokes `HomeViewModel.moveFolderInOrder` | Direct | Approved |

Existing batch Move, Favourite/Unfavourite, Delete, sorting, and reordering
operations remain part of the production selection/organise flow. Batch Pin may
be wired only after its existing setter semantics are verified; ambiguity is a
mandatory stop-and-ask.

### B. Study Folder `+` Sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| New note | Outlined note/add | Create in selected folder, then open editor | `FolderViewModel.createNoteInFolder`; root-tree adapter may call `HomeViewModel.createNote(folderId = targetId)` | Direct | Study folder | Approved |
| New subfolder | Outlined folder/add | New subfolder form | `FolderViewModel.createSubfolderInFolder`; root-tree adapter may call `HomeViewModel.createFolder(parentId = targetId)` | Direct | Study folder | Approved |
| New sticky note | Outlined sticky note | Sticky-note form | `FolderViewModel.createStickyNote` | Direct | Study folder | Approved, with wiring note below |

Implementation wiring note: `FolderViewModel.createStickyNote` is scoped to the
currently opened folder. The main tree's inline folder `+` will require a small
adapter to invoke the same repository operation for an arbitrary target folder.
This is a wiring gap, not permission to add a new UI or data model.

### C. Study Folder Long-Press Sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| New note | Outlined note/add | Folder-specific creation | Same handlers as section B | Direct | Study folder | Approved |
| New subfolder | Outlined folder/add | Folder-specific creation | Same handlers as section B | Direct | Study folder | Approved |
| Rename | Outlined rename/edit | Rename form | Existing folder rename handler through `HomeViewModel` / `FolderViewModel` | Direct | Study folder | Approved |
| Move | Outlined move/folder | Existing destination chooser, including Personal and Islamic Corpus when supported | `HomeViewModel.moveFolder`; workspace destination uses existing `moveFolderToMode` internally | Direct | Study folder | Approved |
| More actions | Outlined ellipsis | Study folder More sheet | Existing secondary utilities only | Direct | Study folder | Approved |
| Delete | Outlined delete | Confirmation sheet/dialog | `HomeViewModel.deleteFolder` / current folder delete handler | Direct, semantic error only | Study folder | Approved |

No folder Pin action is added: production has no folder pin field and the Frozen
note Pin decision cannot be extended to folders.

### D. Study Note Long-Press Sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Open | Outlined open/document | Existing note route | Current navigation callback | Direct | Study note | Approved |
| Rename | Outlined rename/edit | Rename form | Existing note title update handler | Direct | Study note | Approved |
| Move | Outlined move/folder | Existing destination chooser, including workspace destinations | Existing note move handler/repository state | Direct | Study note | Approved |
| Create sub-note | Outlined nested note/add | Create child note, then open editor | Existing child-note creation using `parentNoteId` | Direct | Study note | Approved |
| Pin / Unpin | Outlined pin | Compact Pinned strip | `NoteRepository.setPinned` (`isPinned`) | Direct | Study note | Approved |
| More actions | Outlined ellipsis | Study note More sheet | Existing secondary note actions | Direct | Study note | Approved |
| Delete | Outlined delete | Confirmation sheet/dialog | Existing note delete handler | Direct, semantic error only | Study note | Approved |

### E. Study `More Actions`

#### Note More sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Favourite / Unfavourite | Outlined star | Toggle in place and dismiss | `HomeViewModel.setNoteFavourite` / `FolderViewModel.setNoteFavourite` | More | Study note | Approved |
| Pin within folder / Unpin within folder | Outlined local pin | Toggle in place and dismiss | `NoteRepository.setFolderPinned` (`isFolderPinned`) through the existing ViewModel handler | More | Study note in a folder | Approved |
| Share | Outlined Android share | Existing native Android sharing flow | Existing outgoing Study share handler must be verified before wiring | More | Study note/folder where supported | Approved placement; stop and ask if handler is absent |

#### Folder More sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Organise contents | Outlined vertical reorder | Existing current-folder organise mode | `FolderViewScreen.organizeMode`; movement uses existing ordering handlers | More | Study folder | Approved |
| Edit description | Outlined description/edit | Existing description form | Current `FolderViewScreen` update-folder handler | More | Study folder | Approved |

No folder description control is added to the primary Corpus Browser.

### F. Study Organise Flow Entry

The only root entry is:

`Study root FAB` -> `ORGANISE` -> `Select / organise existing items`

The next sheet contains `Select items`, existing sorting controls, and
`Reorder folders`, as documented in section A. No organise toolbar, sort icon,
filter bar, or permanent top-level control is added.

### G. Library Root FAB Sheet

#### Primary creation

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Upload file | Outlined upload/document | Android multi-document picker | `ActivityResultContracts.OpenMultipleDocuments` -> `LibraryViewModel.importFiles` / `importFilesToFolder(null, ...)` | Direct | Library | Approved |
| New folder | Outlined folder/add | New root-folder form | `LibraryViewModel.createFolder(parentId = null)` | Direct | Library | Approved |

#### Organise utility

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Organise folders | Outlined vertical reorder | Library Organise sheet/mode | Existing ordering engine `LibraryViewModel.moveFolderInOrder` | Direct under `ORGANISE` section | Library | Approved |

The present Library implementation exposes ordering as move-up/move-down rather
than a root organise mode. Stage 2 may add only the approved transient entry
state around the existing `moveFolderInOrder` engine; it must not add permanent
reorder controls.

### H. Library Folder Long-Press Sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Upload file | Outlined upload/document | Multi-document picker targeting folder | `LibraryViewModel.importFilesToFolder(folder.id, ...)` | Direct | Library folder | Approved |
| New subfolder | Outlined folder/add | New subfolder form | `LibraryViewModel.createFolder(parentId = folder.id)` | Direct | Library folder | Approved |
| Rename | Outlined rename/edit | Rename form | `LibraryViewModel.renameFolder` | Direct | Library folder | Approved |
| Move | Outlined move/folder | Existing folder destination chooser | `LibraryViewModel.moveFolder` | Direct | Library folder | Approved |
| More actions | Outlined ellipsis | Library folder More sheet | Existing secondary ordering utility | Direct | Library folder | Approved |
| Delete | Outlined delete | Confirmation sheet/dialog | `LibraryViewModel.deleteFolder` | Direct, semantic error only | Library folder | Approved |

Library folder More contains `Reorder folders`, which invokes the approved
transient organise presentation backed by `LibraryViewModel.moveFolderInOrder`.

### I. Library File/PDF Long-Press Sheet

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Open | Outlined open/document | Existing file/PDF route | Current navigation callback | Direct | Library file/PDF | Approved |
| Rename | Outlined rename/edit | Rename form | `LibraryViewModel.renameFile` | Direct | Library file/PDF | Approved |
| Move | Outlined move/folder | Existing Library folder chooser | `LibraryViewModel.moveFile` | Direct | Library file/PDF | Approved |
| Pin / Unpin | Outlined pin | Library compact Pinned strip | `LibraryViewModel.setFilePinned` (`AttachmentEntity.isPinned`) | Direct | Library file/PDF | Approved |
| PDF activity | Outlined activity/annotation | Existing PDF Activity destination | `VaultDestination.PdfActivityFeed.route(libraryMode)` and `PdfActivityFeedViewModel` | Direct; PDF only | Library PDF | Approved |
| More actions | Outlined ellipsis | Library file More sheet | Existing export/tag actions | Direct | Library file/PDF | Approved |
| Delete | Outlined delete | Confirmation sheet/dialog | `LibraryViewModel.deleteFile` | Direct, semantic error only | Library file/PDF | Approved |

### J. Library `More Actions`

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Save to device | Outlined download/save | Android create-document destination | `ActivityResultContracts.CreateDocument` -> `LibraryViewModel.exportFile` | More | Library file/PDF | Approved |
| Share | Outlined Android share | Existing native Android sharing flow | Existing outgoing Library share handler must be verified before wiring | More | Library file/PDF | Approved placement; stop and ask if handler is absent |
| Add file tag | Outlined tag/add | Existing tag chooser/editor | `LibraryViewModel.addAttachmentTag` | More | Library file/PDF | Approved |
| Remove file tag | Outlined tag/remove | Existing tag chooser/editor | `LibraryViewModel.removeAttachmentTag` | More | Library file/PDF | Approved |

Attachment/file tags remain distinct from PDF annotation tags.

### K. PDF Activity Sheet/View

`PDF activity` opens the existing secondary `PdfActivityFeed` destination. It
does not create a Library main-screen rail.

The existing activity feed and its contextual actions preserve:

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Open activity | Outlined open/page | Existing source page/activity | Existing `onActivityClick` navigation | Direct | PDF activity | Approved |
| Rename activity | Outlined rename/edit | Activity details form | `PdfActivityFeedViewModel.updateActivityDetails` / existing annotation rename handler | Direct | PDF activity | Approved |
| Move annotation | Outlined move/folder | Existing annotation folder chooser | `LibraryViewModel.moveAnnotation` | Direct | PDF annotation | Approved |
| Link to Study note | Outlined link/note | Existing Study note chooser | `LibraryViewModel.linkAnnotationToStudyNote` | Direct | PDF annotation | Approved |
| Create Study note | Outlined note/add | Existing Study destination flow | `LibraryViewModel.createStudyNoteFromAnnotation` | Direct | PDF annotation | Approved |
| More actions | Outlined ellipsis | Annotation More sheet | Annotation tag management and note-only deletion | Direct | PDF annotation | Approved |
| Delete annotation | Outlined delete | Confirmation | `LibraryViewModel.deleteAnnotation` | Direct, semantic error only | PDF annotation | Approved |

Annotation More contains:

- `Add annotation tag` -> `LibraryViewModel.addAnnotationTag`
- `Remove annotation tag` -> `LibraryViewModel.removeAnnotationTag`
- `Delete annotation note` -> `LibraryViewModel.deleteAnnotationNote`

The production feed's existing selection/batch actions remain engine-owned.
Any newly visible batch action not already covered by the approved feed must stop
for placement approval.

### L. Duplicate-PDF Handling

When `LibraryUiState.duplicatePdfImport` is non-null, show a transient Frozen-
language bottom sheet/dialog:

| Label | Icon concept | Destination | Existing handler/state | Level | Scope | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Replace | Outlined replace/refresh | Replace existing PDF, continue import | `LibraryViewModel.replaceDuplicatePdf` | Direct | Duplicate PDF prompt | Approved |
| Skip | Neutral close/forward | Keep existing PDF, continue queue | `LibraryViewModel.skipDuplicatePdf` | Direct | Duplicate PDF prompt | Approved |

The prompt identifies the duplicate filename. It is transient and adds no
permanent duplicate-management UI.

## Deferred And Stop-And-Ask Production Functions

1. **Aggregate Favourite management**: preserve its existing production route
   temporarily. Do not add a Favourites rail or new permanent entry. If the new
   architecture makes that route unreachable, stop and ask.
2. **Workspace Attachments destination**: preserve the existing route in its
   legacy form. Add no new Study control. If it becomes unreachable, stop and
   ask.
3. **Qur'an Reflections Hub**: deferred to the Stage 7 placement decision. Do not
   modify it in Stage 2.
4. **Study batch Pin**: wire only after proving whether its existing handler
   modifies `isPinned` or `isFolderPinned`. Ambiguity is a stop-and-ask.
5. **Outgoing Study Share**: approved under More where applicable. The exact
   existing native handler must be found before wiring; absence is a
   stop-and-ask rather than permission to create new sharing behaviour.
6. **Outgoing Library Share**: approved under More. The exact existing native
   handler must be found before wiring; absence is a stop-and-ask.
7. **Library view-mode preference**: retain its stored value and compatibility,
   but remove the visible selector from the Frozen Corpus Browser. Do not migrate
   or overwrite old values. A runtime/layout conflict caused by an old value is
   a stop-and-ask.

No Stage 2 implementation may proceed through a stop-and-ask item by silently
omitting, relocating, merging, or redesigning it.

## Primary-Screen Freeze

This amendment adds no permanent top-bar icons, row icons, annotation rails,
cards, buttons, secondary FABs, organise toolbars, tags, or export controls.
All additions are transient creation/context/action sheets and preserve the
Frozen Design Master's Study and Library primary presentation.
