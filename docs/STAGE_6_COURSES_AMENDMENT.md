# Stage 6 Courses Delta Audit

Status: **AUDIT COMPLETE - IMPLEMENTATION BLOCKED PENDING DESIGN DECISIONS**

Scope: Production Courses compared with Frozen Design Master screenshots 23-26 and the approved shared hierarchy/action-sheet language. No Stage 6 application code was changed.

## A. Authoritative References

- Frozen screenshots: `23-courses-root.png`, `24-course-workspace.png`, `25-course-nested.png`, `26-course-create.png`
- Frozen documents: `DESIGN_SPEC.md`, `COMPONENT_INVENTORY.md`, `ANDROID_COMPONENT_MAP.md`, `DESIGN_MASTER_README.md`
- Production checkpoint: `c52ce41fe3cb22f318ec0a499446ed4a2bf675fd`
- Recoverable tag: `stage-5-approved`
- Production sources audited: Course entities/DAO/repository/ViewModel/screen, shared folder/note/sticky repositories and entities, Explorer wiring, navigation, and backup/restore validation.

## B. Production Capability Inventory

Thirty-eight production capabilities or compatibility behaviours were identified.

| # | Capability | Existing production entry/state | Persistence impact | Frozen placement | Status |
|---|---|---|---|---|---|
| 1 | List/open courses | `CoursesScreen` / `CoursesViewModel.selectCourse` | Room `CourseEntity` | Compact root course cards | ALREADY FROZEN |
| 2 | Create course | Root FAB / `createCourse` | Course plus root `FolderEntity` | Root creation action | ALREADY FROZEN |
| 3 | Rename course | Course overflow / `renameCourse` | Course title | Context action sheet | APPROVED SHARED COMPONENT |
| 4 | Delete course | Course overflow / `deleteCourse` | Course and owned data | Context action, semantic error | APPROVED SHARED COMPONENT |
| 5 | Course metadata | `CourseEntity` title and timestamps only | Backed up | Root card title | ENGINE-ONLY |
| 6 | Last-opened course note | `CourseEntity.lastOpenedNoteId` | Backed up | Continue Lesson source | ENGINE-ONLY |
| 7 | Continue Lesson | `continueNoteId` / `openNote` | Reads last-opened pointer | Compact Continue Lesson row | NEEDS DESIGN DECISION |
| 8 | Count course notes | Tree-derived note count | No new persistence | Could supply truthful total | ENGINE-ONLY |
| 9 | Create root folder | Course creation sheet / shared folder handler | `FolderEntity` | New folder | ALREADY FROZEN |
| 10 | Create nested folder | Folder-specific creation / shared handler | `FolderEntity.parentId` | Folder creation sheet | APPROVED SHARED COMPONENT |
| 11 | Expand/collapse folders | Shared hierarchy expansion state | UI state | Compact hierarchy | ALREADY FROZEN |
| 12 | Rename folder | Shared folder context handler | Folder title | Context sheet | APPROVED SHARED COMPONENT |
| 13 | Edit folder description | Shared folder handler | Folder description | Secondary context action | APPROVED SHARED COMPONENT |
| 14 | Move folder | Shared move flow | Parent/mode/order | Context sheet | APPROVED SHARED COMPONENT |
| 15 | Delete folder | Shared delete flow | Recently Deleted semantics | Context sheet | APPROVED SHARED COMPONENT |
| 16 | Reorder folders/notes | Shared organise/reorder handlers | `orderIndex` | Compact organise flow | APPROVED SHARED COMPONENT |
| 17 | Create root course note | Course creation sheet / `createNote` | `NoteEntity` | New note | ALREADY FROZEN |
| 18 | Create nested course note | Folder-specific creation | Note folder relationship | Folder creation sheet | APPROVED SHARED COMPONENT |
| 19 | Open course note | `openNote`, shared note route | Updates last-opened pointer | Stage 4 Reader/Editor | APPROVED SHARED COMPONENT |
| 20 | Rename course note | Shared note context handler | Note title | Context sheet | APPROVED SHARED COMPONENT |
| 21 | Move course note | Shared move handler | Folder/workspace relationship | Context sheet | APPROVED SHARED COMPONENT |
| 22 | Cross-workspace move | Existing shared move engine | Workspace/mode relationship | Existing move destination flow | APPROVED SHARED COMPONENT |
| 23 | Create sub-note | Shared note handler | Parent-note relationship | Note context action | APPROVED SHARED COMPONENT |
| 24 | Workspace pin | Shared note handler, `isPinned` | Backed up | Pin/Unpin | APPROVED SHARED COMPONENT |
| 25 | Folder-local pin | Shared note handler, `isFolderPinned` | Backed up | Secondary Pin within folder | APPROVED SHARED COMPONENT |
| 26 | Favourite note | Shared note handler | Backed-up note state | Secondary action | APPROVED SHARED COMPONENT |
| 27 | Delete course note | Shared note delete handler | Recently Deleted semantics | Context sheet | APPROVED SHARED COMPONENT |
| 28 | Full-title/preview preferences | Shared preferences | Existing preferences | Shared compact leaf rows | APPROVED SHARED COMPONENT |
| 29 | Create sticky note | Course detail / `createSticky` | `FolderStickyNoteEntity` at course root | New sticky note | ALREADY FROZEN |
| 30 | Edit sticky note | Sticky preview / `updateSticky` | Sticky content | Supporting-content surface | ALREADY FROZEN |
| 31 | Delete sticky note | Sticky context / `deleteSticky` | Deletes sticky | Context action | APPROVED SHARED COMPONENT |
| 32 | Create concept card | Course detail / `createConceptCard` | `CourseConceptCardEntity` | New concept card | ALREADY FROZEN |
| 33 | Edit concept card | Concept card / `updateConceptCard` | Concept fields | Concept-card surface | ALREADY FROZEN |
| 34 | Delete concept card | Concept context / `deleteConceptCard` | Deletes concept | Context action | APPROVED SHARED COMPONENT |
| 35 | Explorer course hierarchy | Global Explorer from `courses` and `treesByCourse` | Same repository state | Expandable Courses hierarchy | ALREADY FROZEN |
| 36 | Course note Stage 4 integration | Shared note navigation | Same note engine | Frozen Note Reader/Editor | ALREADY FROZEN |
| 37 | Backup/restore | `courses.json`, shared folders/notes/stickies, concept cards | Existing compatible format | No new visible state | ENGINE-ONLY |
| 38 | Legacy course migration | Legacy course folder/note/sticky metadata | Compatibility-only | No new visible state | ENGINE-ONLY |

No production handler/state was found for Course search, course descriptions, lesson completion, completed-lesson counts, or completion percentages.

## C. Frozen Coverage

The following map directly without new design:

- compact Courses root cards and root FAB;
- Course detail hierarchy using the approved shared folder/note language;
- Continue Lesson as a compact destination row, once its label semantics are resolved;
- supporting Sticky Notes and Concept Cards;
- the four-action creation sheet shown in screenshot 26;
- shared context sheets for existing CRUD and organisation handlers;
- Stage 4 Note Reader/Editor for every Course Note;
- the Stage 1 Explorer as the single Courses navigation hierarchy;
- Light, Dark and OLED shared theme language.

Sticky Notes and Concept Cards remain separate production models. They must not be converted to ordinary notes. Production currently creates Course Sticky Notes at the course root; no folder-specific Course sticky creation handler exists.

## D. Creation Hierarchy

### Courses root

- New course -> existing `CoursesViewModel.createCourse`

### Course detail

- New folder -> existing shared root-folder handler
- New note -> existing shared course-note handler
- New sticky note -> existing `CoursesViewModel.createSticky` at the course root
- New concept card -> existing `CoursesViewModel.createConceptCard`

### Course folder

- New note -> existing shared handler in that folder
- New subfolder -> existing shared handler in that folder

Folder-specific Sticky Note creation is not an existing Course capability and must not be invented.

## E. Context-Action Hierarchy

### Course

- Open
- Rename
- Delete

### Course folder

- Open/Expand
- New note
- New subfolder
- Rename
- Move
- Organise/Reorder
- More -> Edit description
- Delete

### Course note

- Open
- Create sub-note
- Move
- Pin/Unpin (`isPinned`)
- More -> Favourite/Unfavourite
- More -> Pin within folder/Unpin within folder (`isFolderPinned`)
- Rename where exposed by the existing shared flow
- Delete

### Sticky Note

- Open/Edit
- Delete

### Concept Card

- Open/Edit
- Delete

No production move, reorder, pin, favourite, or nesting semantics exist for Sticky Notes or Concept Cards.

## F. Continue Lesson Semantics

Production `Continue Lesson` means **reopen the last Course Note the user opened**:

1. Opening a Course Note calls `CoursesViewModel.openNote(noteId)`.
2. The repository writes that ID to `CourseEntity.lastOpenedNoteId`.
3. `CoursesUiState.continueNoteId` reads that field.
4. Continue opens the same shared Stage 4 note route.

Production does not maintain a lesson sequence or completion state. A depth-first ordinal could be derived from the current hierarchy, but it would describe the last-opened note's position, not completed progress.

## G. Progress Semantics

Production can truthfully provide:

- total notes currently contained in a course;
- the last-opened note;
- a derived hierarchy position for that note, if explicitly approved.

Production cannot truthfully provide:

- completion percentage;
- completed lesson count;
- total planned lesson count independent of existing notes;
- per-lesson completed state;
- course description subtitle.

The Frozen references display `x of y lessons`, percentages, progress bars, and descriptive subtitles. Those values cannot be populated from the current production engine without either a semantic amendment or new persisted data.

## H. Note Editor and Explorer Integration

- Course Notes already route into the shared Stage 4 Reading/Editing architecture; no separate Course editor is required or permitted.
- Returning follows the existing navigation stack to the correct Course location.
- The global Explorer consumes the same `CoursesViewModel` course/tree state as the main screen.
- CRUD performed from Explorer or the Course screen therefore remains synchronized through the same repositories.
- Sticky Notes and Concept Cards are supporting Course content and are not represented as ordinary Explorer note leaves.

## I. Data and Backup Considerations

- `courses.json` preserves course ID, title, root folder ID, last-opened note ID, and timestamps.
- Modern Course hierarchy persists through shared `folders.json` and `notes.json` using mode `course:<courseId>`.
- Modern stickies persist in `folder_sticky_notes.json`.
- Concept Cards persist in `course_concept_cards.json`.
- Legacy Course metadata remains for restore compatibility.
- Restore validation checks the Course root folder mode and validates the last-opened note reference.

No entity, Room schema, backup field, ID, or migration change is authorised by this audit.

## J. Explicit Blockers

### Blocker 1: Course progress presentation

The Frozen cards and workspace header show completion progress that production does not calculate.

Decision required:

1. Approve a production-specific mapping to **current lesson position** derived from the last-opened note and current note total, with no completion percentage; or
2. Approve omission of progress/percentage UI; or
3. Authorise a future completion engine and compatible data/backup design as new functionality.

### Blocker 2: Course description subtitle

The Frozen root cards show a descriptive subtitle, but `CourseEntity` has no description field.

Decision required:

1. Approve omission/empty subtitle as a production-specific mapping; or
2. Authorise new Course metadata plus persistence and backup compatibility design.

### Blocker 3: Course search

The Frozen Courses root shows a Search action, but production currently supplies no Course search handler or state; the current callback is empty.

Decision required:

1. Approve hiding the Search action as a production-specific visual deviation; or
2. Authorise Course search as new production functionality and define its scope (course titles only or nested Course content).

## K. Gate Result

**STAGE 6 HAS NEW DESIGN/SEMANTIC BLOCKERS.**

A narrow Frozen Course amendment or explicit production-specific mapping is required for the three blockers above. Stage 6 implementation must not begin until those decisions are approved.
