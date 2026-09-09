# Library ordering and edit-mode attachments

## Scope and recovery

- Repository: MyVault Complete Before Tutor; branch: frozen-design-master-port.
- Starting commit: ef3a8c02fba1f4a7212db15f9384e210ed4a3ed5.
- Pushed recovery tag: pre-library-file-ordering-20260909-135119.
- Separate approval covers one attachment ordering column and its optional JSON field.
- No Web edits, Drive operations, manifest changes, PDF representation changes,
  rich-text storage changes, or widget/Qur'an changes were made.

## Ordering contract

Previously, Library rendered folders followed by alphabetically sorted files;
files had no persisted manual position. Room 29 -> 30 adds nullable INTEGER
`attachments.orderIndex`, using ALTER TABLE rather than rebuilding the table.
Existing files receive deterministic alphabetical positions (case-insensitive
name, then stable ID) after existing folder positions in their parent.

Folders and files share one sibling order domain. A reorder transaction changes
only order values in that parent, normalizing to consecutive integers. It does
not reparent items or update content/activity timestamps. File import and Move
append to the destination's mixed sibling order. Folder schema is unchanged.

Library offers Manual, Alphabetical, Recently created and Recently opened.
There is deliberately no Recently modified: Library has no truthful file
modification timestamp. Created uses import/creation time; Opened uses real
recorded opens and existing PDF reading activity. Study retains its own sort
preference and its genuine note modification dates. Automatic sorts change
presentation, not stored manual order.

## Backup compatibility

Android attachment JSON writing adds optional integer `orderIndex`; reading
accepts nonnegative integral JSON numbers up to 1,000,000,000, reserving append
headroom. Missing, null, string, negative, fractional and out-of-range input
falls back. Missing file order is seeded per parent after explicitly ordered
files/folders, with alphabetical name and ID tie-breaking. It is not required
by verification and does not change manifest or file identity/path contracts.

Read-only inspection and a host execution of the current Web merge functions
confirmed preservation of this unknown additive field during attachment rename,
including a concurrent Android order change. Web-created attachments without
the field remain compatible. No Web code was changed. This is a writer-contract
test, not a claim that a live Drive round trip was performed.

## Interaction and presentation

Study and Library reuse the existing reorderable list, with the dependency's
press-and-drag handle instead of its long-press handle. Ordinary touch slop
remains. On the Samsung, quick swipes at the old far-right handle position were
intercepted before reaching the app. A 26dp inset on organiser rows and a
handle-only system gesture exclusion make quick root/nested dragging work.
No phone settings or global long-press timing were changed. Header and pinned
strip padding are unchanged. Drag state stays local until the sibling order is
saved and the database-backed UI catches up.

Sort / Organize remains inside the long-press action sheet, not on the normal
main screen. Library folders expose rename/description, colour, import/create,
Move and organisation directly. Files have appropriate file actions. Delete is
last and separated. Normal Open and More actions entries are removed. Existing
PDF highlight/note/page metadata and normal pinned card designs remain.

The editor previously used the reading-mode inline attachment presentation and
gave the text its own weighted scroll area. Editing now has one scroll owner
for text followed by compact attachment rows, without image bitmap loading.
Reading mode still displays real inline images. Opening an attachment uses its
existing ID/viewer route; Back returns to the editor with saved text intact.

## Evidence and limitations

- Samsung SM-F966B: real Room 29 fixture -> Room 30 migration passed, comparing
  all prior column values, attachment IDs, folder links, annotations/progress.
- Samsung: production BackupRepository export/restore passed with custom mixed
  root/nested order, identical fixture bytes, IDs, folder relationships,
  creation times, PDF annotations and reading progress. Old/malformed optional
  metadata restored deterministically on repeated restores.
- Samsung: Library quick root and nested drag, sort switching/manual recovery,
  long-distance scroll, action sheets and database reopen passed.
- Samsung: edit-mode long English/Arabic fixture with multiple attachment types
  passed compact-card placement, exact image opening/Back, saved text and
  restoration of reading-mode images. Screenshots were inspected.
- Tests use disposable fixtures, not reordering/deleting the user's live vault.
- JBR 21: all 339 unit tests passed in each of debug and release; debug APK,
  instrumentation APK, release/R8 and lint completed successfully. Lint reports
  zero errors and 283 warnings; this is not a warning-free baseline.
- Samsung: Study quick root/nested drag and continuous edge scrolling to both
  ends passed. The edge test allows 12 seconds for a whole-list traversal;
  initial movement starts after a 16ms touch, not a long press.
- Final repeat of both Study/Library interaction tests passed (98.785 seconds).
  The automation refreshes accessibility bounds after animated moves and waits
  for the visible save indicator before sending the next interaction.
- No live Google Drive backup/restore was executed. No signed production APK
  publication is part of this task. No claim of every screen size or device
  configuration is made from the single Samsung test configuration.

## Checkpoints

- bd9eb89: additive migration, exported schema and preservation tests.
- 276456d: mixed Library persistence and optional Android backup metadata.
- e0d23c6: edit-mode compact attachment placement and device test.
- d4eb4b9: Library organisation parity, immediate shared handles and final
  physical interaction tests.
- Completion tag: study-library-organisation-complete-20260909 (created after
  the final device checks and pushed with the validation report).

## Remaining acceptance boundaries

The production backup code was exercised with real archives on Samsung, but
only with disposable data. The user's entire live vault was not destructively
restored for testing. Web field preservation was checked locally against its
current merge code, not through a new live Drive publication. Those distinctions
matter: host/build checks alone are not cross-device cloud acceptance.

No protected-boundary issue remains open for the approved optional field.
