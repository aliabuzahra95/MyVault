# Stage 5 PDF Functionality Audit

Status: **PRE-STAGE-5 AUDIT ONLY - STAGE 5 IMPLEMENTATION IS NOT AUTHORIZED**

Stage 4 approved checkpoint:

- commit: `f5a969728a327ebb36d0928216b36c298ea87630`
- tag: `stage-4-approved`
- branch: `frozen-design-master-port`

This document compares the real production PDF implementation with Frozen PDF
references 18-22. It records presentation proposals and blockers; it does not
authorize changes to the renderer, annotation model, persistence, backup, or UI.

## 1. Authoritative References

Frozen sources inspected:

- `design-master/screenshots/18-pdf-reader.png`
- `design-master/screenshots/19-pdf-selection.png`
- `design-master/screenshots/20-pdf-annotation.png`
- `design-master/screenshots/21-pdf-immersive.png`
- `design-master/screenshots/22-pdf-oled.png`
- `DESIGN_SPEC.md`
- `COMPONENT_INVENTORY.md`
- `ANDROID_COMPONENT_MAP.md`
- `MOTION_SPEC.md`
- `DESIGN_MASTER_README.md`

Production sources inspected include:

- `AttachmentViewerScreen.kt`
- `AttachmentViewerViewModel.kt`
- `PdfActivityFeedScreen.kt`
- `PdfActivityFeedViewModel.kt`
- `PdfAnnotationRepository.kt`, `PdfAnnotationDao.kt`, `PdfAnnotationEntity.kt`
- `PdfReadingProgressRepository.kt`
- `AttachmentRepository.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`
- `BackupRepository.kt`, Web sync DTOs, and `VaultNavHost.kt`

## 2. Frozen PDF Contract

The Frozen reader owns the viewport and uses a compact header, continuous
vertical white PDF pages, restrained canvas, and chrome that can hide without
reflowing the document. Text selection produces a nearby contextual toolbar
with Highlight, Note, Copy, and More. Annotation entry uses the master bottom
sheet with selected text context. Page jump and other secondary actions use the
same sheet language. In OLED, the surrounding canvas is black while the PDF
paper remains white.

The future port must retain the production renderer, page/zoom state,
coordinate transforms, annotation persistence, file identity, and backup
contracts beneath that presentation.

## 3. Complete Production Capability Inventory

The audit identified **55 distinct production PDF capabilities or contracts**.

Status abbreviations:

- **FROZEN**: the Frozen PDF master supplies a clear presentation.
- **ENGINE**: preserve below presentation; no new permanent UI is needed.
- **DECISION**: placement or semantics require an approved amendment.
- **BLOCKED**: production and Frozen behavior conflict; stop before wiring it.
- **DEFERRED**: preserve current functionality until its final design is frozen.

| # | Capability | Current production entry/UI | Owner/state | Persistence impact | Frozen placement | Status |
|---:|---|---|---|---|---|---|
| 1 | Open a PDF | Library file opens `AttachmentViewerScreen` | Nav + attachment repository | File ID/path | Reader route | **FROZEN** |
| 2 | AndroidX renderer lifecycle | `VaultPdfViewerFragment` in retained fragment host | AndroidX PDF fragment | None | Beneath reader canvas | **ENGINE** |
| 3 | Renderer platform gate | API 31 + extension 13 support check | Viewer screen | None | Neutral unsupported/error state | **ENGINE** |
| 4 | Fallback/first-page rendering | `PdfRenderer` preview/fallback | Viewer screen | Cached bitmap only | Loading canvas | **ENGINE** |
| 5 | Loading and load errors | PDF callback registry and loading/error state | Viewer screen/ViewModel | None | Reader canvas state | **FROZEN** |
| 6 | Continuous vertical pages | AndroidX PDF viewer | Renderer | None | Frozen continuous reader | **FROZEN** |
| 7 | Current page and page count | Native viewer page callbacks | Viewer state | Progress record | Compact header count | **FROZEN** |
| 8 | Page/deep-link navigation | Attachment/page route and viewer jump | Nav + viewer | Progress record | Header count/page-jump sheet | **FROZEN**, exact action wiring pending |
| 9 | Zoom | Native AndroidX gestures | Renderer | None | Direct canvas gesture | **FROZEN/ENGINE** |
| 10 | Pan | Native AndroidX gestures | Renderer | None | Direct canvas gesture | **FROZEN/ENGINE** |
| 11 | PDF links | Native renderer behavior | Renderer | None | In-document behavior | **ENGINE** |
| 12 | Cached first-page preview | Preview retained until PDF-ready then faded | Viewer screen | Session cache only | Loading transition | **ENGINE** |
| 13 | Theme-specific canvas/paper | Current theme around native PDF | Theme + viewer | Theme preference only | White paper in Light/Dark/OLED | **FROZEN** |
| 14 | Immersive chrome hide/show | No equivalent Frozen-complete production presentation proven | Viewer screen | None | Frozen immersive state | **DECISION** for trigger/wiring |
| 15 | Reading progress | Debounced page/page-count/percent update | Reading progress repository | Room + backup | No permanent control | **ENGINE** |
| 16 | Resume position | Stored page restored on open | ViewModel/repository | Room + backup | Automatic reader resume | **ENGINE** |
| 17 | Document text extraction | `DocumentTextExtractor`/PDFBox | ViewModel/extractor | No PDF mutation | Selection/copy/narration dependency | **ENGINE**, entry depends on decisions |
| 18 | Native PDF text selection | Selection is deliberately cleared/suppressed | `AttachmentViewerScreen` | None | Frozen selected-text state | **BLOCKED** |
| 19 | Copy selected PDF text | No usable PDF-selection action while selection is suppressed | Renderer/viewer | Clipboard only | Frozen `Copy` action | **BLOCKED** |
| 20 | Selection contextual toolbar | AndroidX toolbox hidden; no Frozen-equivalent runtime state | Viewer screen | None | Near-selection toolbar | **BLOCKED** |
| 21 | Create highlight | Manual drag rectangle in Draw mode | Overlay + annotation repository | Room + backup | Frozen selection `Highlight` | **BLOCKED** semantics differ |
| 22 | Highlight colours | Yellow, blue, green, red, black | Overlay/repository | Annotation color | Secondary color treatment | **DECISION** |
| 23 | Highlight geometry | Normalized page-local rectangle and transforms | Overlay/repository | Coordinates in Room/backup | Render overlay on PDF page | **ENGINE** |
| 24 | Select existing highlight | Existing-annotation pick mode | Overlay/viewer | Selected ID only | Contextual annotation actions | **DECISION** |
| 25 | Recolour existing highlight | Current color controls | ViewModel/repository | Annotation color | Secondary annotation surface | **DECISION** |
| 26 | Add/edit highlight note | Quick-note dialog/action | ViewModel/repository | `noteText` | Frozen annotation sheet | **FROZEN**, selected-text quote unavailable |
| 27 | Delete highlight | Existing annotation action | ViewModel/repository | Deletes annotation | Restrained destructive action | **FROZEN** |
| 28 | Page note | Add note to current page | ViewModel/repository | `page_note` row | Annotation/page-note sheet | **DECISION** |
| 29 | Page activity/count indicator | Current-page/whole-PDF activity counts | Viewer/activity state | Derived | Frozen page note/count chip | **FROZEN**, mapping needs approval |
| 30 | Create text box | Repository and UI intent exist | Overlay/ViewModel/repository | `text_box` row | Not placed by Frozen master | **BLOCKED** |
| 31 | Edit text-box content/style | Text, color, size 10-36, background choices | Overlay/ViewModel/repository | Annotation fields | No Frozen placement | **BLOCKED** |
| 32 | Move/resize text box | Bounds update path exists; touch handler is unreachable | Overlay/repository | Geometry fields | No Frozen placement | **BLOCKED** |
| 33 | Current-page PDF activity | Existing Activity panel/popup | Viewer state | Derived annotations | Secondary PDF Activity relationship | **DEFERRED** |
| 34 | Whole-PDF Activity feed | Dedicated `PdfActivityFeedScreen` | Activity ViewModel | Derived annotations | Stage 2-approved secondary destination | **DEFERRED** internal design |
| 35 | Activity search | Search PDF activity | Activity ViewModel | None | PDF Activity subpage | **DEFERRED** |
| 36 | Group activity by PDF | Expand/collapse document groups | Activity screen/ViewModel | None | PDF Activity subpage | **DEFERRED** |
| 37 | Activity batch selection/delete | Selection mode and batch delete | Activity ViewModel/repository | Deletes annotations | PDF Activity subpage | **DEFERRED** |
| 38 | Edit activity title/description | Activity action dialog | Activity ViewModel/repository | Display title/note | PDF Activity subpage | **DEFERRED** |
| 39 | Create merged Study note from selected activity | Selection action | Activity ViewModel/note repository | New note + links | PDF Activity action | **DEFERRED** |
| 40 | Create source backlinks | Added during activity-to-note creation | Knowledge/source repositories | Relationship data + backup | Relationship workflow | **ENGINE** |
| 41 | Link annotation to existing Study note | Library annotation context flow | Library ViewModel/repositories | Relationship data | PDF Activity/context action | **DEFERRED** |
| 42 | Create Study note from one annotation | Library annotation context flow | Library ViewModel/repositories | New note + relationship | PDF Activity/context action | **DEFERRED** |
| 43 | Referenced Study notes | Annotation/PDF relationship surfaces | Library/knowledge repositories | Relationship data | PDF Activity detail | **DEFERRED** |
| 44 | Attachment tags | Library file context | Attachment/tag repositories | Tags + backup | Library More actions, not reader chrome | **ENGINE/STAGE 2** |
| 45 | PDF annotation tags | Annotation context; distinct tag system | Annotation/tag repositories | Tags + backup | PDF Activity secondary management | **DEFERRED** |
| 46 | Save/export to device | SAF destination then `exportAttachmentToUri` | Viewer ViewModel/repository | External copy only | Reader overflow or Library action | **DECISION** |
| 47 | Delete PDF | Current viewer and Library actions | Attachment repository | Cascades file/progress/annotations | Library context or reader overflow | **DECISION** |
| 48 | Open externally | Existing non-PDF attachment behavior only | Android intent | None | Not a PDF capability in current runtime | **ENGINE/NOT APPLICABLE** |
| 49 | Duplicate-PDF detection | Import flow detects existing hashes/identity | Library ViewModel/repository | Existing/new file identity | Transient Frozen-style decision sheet | **STAGE 2/ENGINE** |
| 50 | Duplicate replacement | Replace/skip while retaining production identity/metadata | Attachment repository | File bytes/hash/metadata | Import decision sheet | **STAGE 2/ENGINE** |
| 51 | PDF text narration engine path | Extractor and attachment narration methods exist | ViewModel/narration controller | Narration cache/state | No Frozen PDF entry | **DECISION** |
| 52 | PDF narration provider/progress | Azure attachment methods/progress exist; PDF UI does not invoke them | ViewModel/controller | Provider preference/cache | No Frozen PDF entry | **DECISION** |
| 53 | Annotation backup/restore | `pdf_annotations.json` with validated fields | Backup repository | Backup contract | No visible UI | **ENGINE** |
| 54 | Reading-progress backup/restore | `pdf_reading_progress.json` | Backup repository | Backup contract | No visible UI | **ENGINE** |
| 55 | Web sync/cascade cleanup | DTOs include progress/annotations; deletion cascades cleanup | Sync/attachment repositories | Web compatibility/data custody | No visible UI | **ENGINE** |

No active freehand/ink annotation engine was found in the audited production
path. It must not be invented during Stage 5.

## 4. Frozen Coverage

The Frozen references already cover:

- compact reader header and page count;
- continuous vertical PDF pages;
- white PDF paper across Light/Dark/OLED;
- nearby selected-text toolbar with Highlight, Note, Copy, and More;
- annotation bottom sheet with selected quote context;
- page note/count indicator treatment;
- immersive reader presentation;
- master sheet and restrained motion language.

Renderer lifecycle, zoom/pan, progress, resume, caching, coordinate transforms,
backup and file identity remain engine-owned and do not need new permanent UI.

## 5. Missing Placements and Explicit Conflicts

### 5.1 True text selection versus manual rectangle highlights - BLOCKER

Production currently suppresses AndroidX PDF text selection by clearing every
selection and intercepting long press. The Frozen master requires real selected
text for Highlight, Note, Copy, and the selected-quote preview. Current Draw
mode creates a geometric rectangle and captures no selected text.

These are not equivalent interactions. Stage 5 must not silently relabel Draw
mode as text selection. An approved technical/design decision is required on
whether to enable and integrate native text selection, retain manual rectangle
highlighting as a separate secondary tool, or amend the Frozen contract.

### 5.2 Text-box annotation lifecycle - BLOCKER

Production contains repository/UI methods for text boxes, but current data
validation treats `text_box` as non-current and the legacy cleanup query
explicitly selects it for deletion. The overlay's text-box touch handler also
returns before its interaction logic. Therefore creation/edit/move/resize cannot
be represented honestly as a healthy existing capability.

Before Stage 5, decide whether text boxes remain supported and the existing
defects are repaired, or whether the feature is formally deprecated through a
separate data-compatibility decision. No cleanup, migration, or silent removal
is authorized by this audit.

### 5.3 Secondary functions without final Frozen placement

The following need a frozen amendment or explicit approval:

- internal PDF Activity presentation and its batch/edit/link workflows;
- page-note creation independent of selected text;
- highlight color selection and existing-annotation actions;
- annotation tags and referenced Study notes;
- PDF narration entry/provider state;
- Save to device and Delete placement in the reader versus Library only;
- exact page-jump trigger and immersive-mode trigger;
- whether manual rectangle highlighting remains available alongside selection.

## 6. Proposed Stage 5 Action Hierarchy - Not Approved

This hierarchy is a placement proposal only. It must be frozen before code work.

### Reader Header

- Menu/Explorer
- document title and breadcrumb
- current page / page count, opening the Page Jump sheet
- restrained overflow

### Reader Overflow

- Immersive reading
- Page jump if the page count itself is not the trigger
- Save to device, only if approved in-reader rather than Library-only
- PDF Activity, using the Stage 2 secondary route
- Delete, only if approved in-reader rather than Library-only

### Selected-text Context Toolbar

- Highlight
- Note
- Copy
- More

This surface is **blocked** until real text selection semantics are approved and
proven against the AndroidX renderer and custom overlay.

### Annotation Sheet

- selected quote preview when true text exists
- note/comment input
- highlight color as a secondary choice if approved
- confirm/cancel

### Existing Annotation Context Sheet

- edit note
- change color
- open related activity/Study references
- delete

### Page Note Sheet

- page context
- note input
- save/cancel

### PDF Activity Subpage

- grouped activity and search
- open source page
- edit title/description
- selection and supported batch actions
- link to Study note
- create Study note/source backlinks
- annotation tag management

Its route is preserved by Stage 2, but its final Stage 5 visual design is not
frozen.

### Library-owned File Management

- rename, move, pin, attachment tags and duplicate handling remain Library
  responsibilities unless a later amendment explicitly duplicates an action in
  reader overflow.
- outgoing Share remains deferred because no production handler exists.

## 7. Engine Ownership and Rendering/Geometry Risks

Stage 5 must preserve these ownership boundaries:

- AndroidX `PdfViewerFragment` owns decode, paging, vertical scroll, zoom, pan,
  links and native page state.
- the existing overlay owns conversion between view coordinates and normalized
  page coordinates;
- repositories own annotation/progress persistence and cascade behavior;
- backup/sync own serialized field compatibility.

Primary risks:

1. Recreating or resizing the native fragment on chrome/drawer/sheet changes can
   cause white rerenders, position loss, or cache loss.
2. Selection UI layered over the custom annotation overlay can intercept touch,
   invalidate AndroidX selection, or drift after zoom/pan.
3. Changing normalized-coordinate assumptions can shift highlights across zoom,
   page sizes, rotation, or restored backups.
4. Writing progress during transient page/layout callbacks can produce position
   jumps or stale resume state.
5. Treating text boxes as valid without resolving cleanup/validation can create
   data that is immediately deleted; removing them can destroy backed-up data.
6. Re-keying or replacing attachments can orphan activity, progress, tags and
   Study backlinks.
7. Theme work must never tint the rendered PDF page or make source text faint.

## 8. Backup and Persistence Contract

No Stage 5 UI port may change:

- `PdfAnnotationEntity` IDs or type strings;
- normalized annotation coordinates;
- reading-progress schema;
- `pdf_annotations.json` or `pdf_reading_progress.json` semantics;
- file/attachment identity;
- Web sync DTO meanings;
- cascade cleanup behavior;
- duplicate replacement semantics.

The text-box contradiction must be resolved explicitly before any compatibility
change. A destructive backup/restore round trip remains mandatory in Stage 10,
not during this audit.

## 9. Required Decisions Before Stage 5 Authorization

1. Approve the production strategy for true PDF text selection and Copy.
2. Decide whether manual rectangle highlighting remains as a separate tool.
3. Resolve text-box annotation support versus formal deprecation, including
   existing backed-up rows.
4. Freeze the internal PDF Activity presentation.
5. Place page notes, annotation colors, annotation tags, and linked Study-note
   actions.
6. Decide whether PDF narration receives an entry and where.
7. Decide whether Save to device and Delete appear in reader overflow or remain
   Library-only.
8. Confirm page-jump and immersive-mode triggers.

Until these decisions are frozen, Stage 5 implementation must not begin.
