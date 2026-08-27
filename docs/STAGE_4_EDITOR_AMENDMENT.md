# Stage 4 Note Editor Functionality Audit

Status: **APPROVED FOR STAGE 4 IMPLEMENTATION**

The final Frozen Editor amendment and its 22 screenshots resolve the placement
questions recorded by this audit. The later production compatibility decision
also approves one restrained deviation in the Listen sheet: the existing
production `OpenAI TTS` provider is shown as a third provider row alongside
Device TTS and Azure Speech TTS. This is presentation and wiring only; the
existing `NarrationProvider.OpenAi` state and narration engine remain
authoritative.

This production-side amendment audits the current Android Note reading and
editing system against Frozen Design Master references 14-17. The Frozen
prototype remains read-only. This document proposes placement only; every item
marked **NEEDS DESIGN DECISION** or **STOP-AND-ASK** requires explicit approval
before Stage 4 implementation.

## 1. Authoritative References

Frozen references inspected:

- `design-master/screenshots/14-note-reading.png`
- `design-master/screenshots/15-note-editing.png`
- `design-master/screenshots/16-note-keyboard.png`
- `design-master/screenshots/17-note-oled.png`
- `DESIGN_SPEC.md`
- `COMPONENT_INVENTORY.md`
- `ANDROID_COMPONENT_MAP.md`
- `MOTION_SPEC.md`
- `DESIGN_MASTER_README.md`

Production sources inspected:

- `ui/screens/ReadingScreen.kt`
- `ui/screens/EditorScreen.kt`
- `ui/components/EditorToolbar.kt`
- `ui/screens/VaultRichText.kt`
- `ui/viewmodel/NoteViewModel.kt`
- note, attachment, knowledge, table, version and backlink repositories/entities
- narration providers, controller and player
- formatting models, repository, prompt builder and output engine
- `BackupRepository.kt`, `SyncRepository.kt`, and sync DTOs
- note routes and callbacks in `VaultNavHost.kt`

## 2. Frozen Editor Contract

The following architecture is already frozen:

- one document-first canvas with no outer editor card;
- compact header with Explorer access, note/path context and restrained overflow;
- reading mode with clean document presentation and a compact Edit affordance;
- editing mode with an editable title/body and formatting controls;
- formatting bar docked immediately above the Android IME;
- normal body, heading, quote, list and mixed Arabic/English rendering;
- Light, Dark and OLED presentation using shared semantic theme tokens;
- no additional permanent editor chrome.

The production rich-text, relationship, persistence, backup, narration and
formatting engines remain authoritative beneath that presentation.

## 3. Complete Production Capability Inventory

The audit tracks **63 distinct capabilities**. `R` means Reading mode, `E`
means Editing mode. A check in Backup means the capability changes or depends
on persisted/backup-compatible note data; `Local` means device/session state.

### A. Document lifecycle and metadata

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N01 | Render complete note body | Reading document canvas | `NoteViewModel`, `VaultRichText` | R | Document canvas | Backup | **ALREADY FROZEN** |
| N02 | Edit note body | Main rich-text field | `saveRichText` | E | Document canvas | Backup | **ALREADY FROZEN** |
| N03 | Edit title | Editable title field | `updateTitle` | E | Editable document title | Backup | **ALREADY FROZEN** |
| N04 | Separate reading/editing routes | Read route, Edit route | `VaultNavHost`, default-note-view preference | R/E | Frozen reading/editing states | Local | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N05 | Saving/Saved state | Header/status text | editor save state | E | Compact header metadata | Local | **ALREADY FROZEN** |
| N06 | Debounced autosave | Automatic after edits | editor coroutine, `saveRichText` | E | No added control | Backup | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N07 | Flush pending save on exit | Lifecycle/dispose | editor save flush | E | No added control | Backup | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N08 | Last-updated timestamp | Reading metadata | note `updatedAt` | R | Frozen does not show final placement | Backup | **NEEDS DESIGN DECISION** |
| N09 | Word count | Reading/editor metadata | derived body count | R/E | Frozen does not show final placement | Local | **NEEDS DESIGN DECISION** |
| N10 | Character count | Reading/editor metadata | derived body count | R/E | Frozen does not show final placement | Local | **NEEDS DESIGN DECISION** |
| N11 | Double-tap document to edit | Reading body gesture | navigation callback | R | Frozen shows Edit affordance, not this gesture | Local | **PROPOSE PRESERVE AS INVISIBLE GESTURE** |

### B. Rich text, selection and formatting

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N12 | Bold | Editor toolbar | `VaultInlineStyle.Bold` | E | Formatting bar | Backup | **ALREADY FROZEN** |
| N13 | Italic | Editor toolbar | `VaultInlineStyle.Italic` | E | Formatting bar | Backup | **ALREADY FROZEN** |
| N14 | Underline | Editor toolbar | `VaultInlineStyle.Underline` | E | Formatting bar | Backup | **ALREADY FROZEN** |
| N15 | Heading 1 | Editor toolbar | `VaultInlineStyle.Heading` | E | Paragraph/heading selector | Backup | **ALREADY FROZEN** |
| N16 | Heading 2 | Editor toolbar | `VaultInlineStyle.Heading2` | E | Paragraph/heading selector | Backup | **ALREADY FROZEN** |
| N17 | Heading 3 | Editor toolbar | `VaultInlineStyle.Heading3` | E | Paragraph/heading selector | Backup | **ALREADY FROZEN** |
| N18 | Heading 4 | Editor toolbar | `VaultInlineStyle.Heading4` | E | Paragraph/heading selector | Backup | **ALREADY FROZEN** |
| N19 | Quote mark/style | Rich-text renderer supports it; main toolbar currently omits it | `VaultInlineStyle.Quote` | R/E | Frozen formatting bar shows Quote | Backup | **STOP-AND-ASK: FROZEN CONTROL HAS NO CURRENT VISIBLE COMMAND** |
| N20 | Text colour palette | Colour toolbar/picker | seven `VaultInlineStyle.Color*` marks | E | Formatting bar colour action + secondary palette | Backup | **ALREADY FROZEN** |
| N21 | Clear selected colour | Colour picker | mark removal | E | Secondary colour palette action | Backup | **PROPOSED FOR APPROVAL** |
| N22 | Bullet list | Editor toolbar | text-prefix transformation | E | Formatting bar | Backup | **ALREADY FROZEN** |
| N23 | Numbered list | Editor toolbar | text-prefix transformation | E | Formatting bar | Backup | **ALREADY FROZEN** |
| N24 | Undo | Editor top bar | local 48-state history | E | Formatting bar | Local | **ALREADY FROZEN** |
| N25 | Redo | Editor top bar | local 48-state history | E | Formatting bar | Local | **ALREADY FROZEN** |
| N26 | Selection-aware and pending styles | Rich-text field | selection/pending-mark state | E | Native selection + formatting bar active states | Local | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N27 | Mixed Arabic/English and RTL-safe display | Document renderer/editor | `VaultRichText` and Compose text layout | R/E | Document canvas | Backup | **ALREADY FROZEN** |
| N28 | Note editor body font size | Applied to renderer/editor | Settings preference | R/E | No editor control; Settings owns it | Local/Backup | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N29 | URL link insertion | Add-link dialog; inserts visible URL text | editor link dialog | E | Proposed More formatting | Backup text | **NEEDS DESIGN DECISION: URL SEMANTICS ARE NOT STRUCTURED LINKS** |
| N30 | Structured note links via `@` mention | Mention suggestions in editor | note search + `noteLinks` payload | E | Proposed More formatting/contextual mention | Backup | **NEEDS DESIGN DECISION** |

### C. Structures, attachments and note relationships

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N31 | Create table | Editor toolbar/table-size chooser | table repository/ViewModel | E | Proposed More formatting | Backup | **NEEDS DESIGN DECISION** |
| N32 | Edit table cells | Separate table editor below body | table repository/ViewModel | R/E | Proposed contextual table surface | Backup | **NEEDS DESIGN DECISION** |
| N33 | Delete table | Table action/dialog | table repository/ViewModel | E | Proposed contextual table surface | Backup | **NEEDS DESIGN DECISION** |
| N34 | Attach document/file | Editor toolbar or overflow | attachment importer | E | Proposed More formatting or Attachments sheet | Backup/file | **NEEDS DESIGN DECISION** |
| N35 | Attach image | Editor toolbar | image importer | E | Proposed More formatting or Attachments sheet | Backup/file | **NEEDS DESIGN DECISION** |
| N36 | Inline attachment/image preview and open | Reading/editor attachment section | attachment state and open callback | R/E | Frozen document canvas omits final anatomy | Backup/file | **NEEDS DESIGN DECISION** |
| N37 | Open linked note | Inline note-link spans | note navigation callback | R | Contextual document interaction | Backup | **PROPOSED FOR APPROVAL** |
| N38 | Display/open PDF source references | Reading source section | `KnowledgeRepository` | R | Proposed Knowledge & references sheet/section | Backup | **NEEDS DESIGN DECISION** |
| N39 | Remove source reference | Reading source action/dialog | `removeSourceReference` | R | Proposed Knowledge & references sheet | Backup | **NEEDS DESIGN DECISION** |
| N40 | Display/open backlinks | Reading backlinks section | `NoteRepository.observeBacklinks` | R | Proposed Backlinks/Knowledge sheet | Backup relationship | **NEEDS DESIGN DECISION** |
| N41 | Add knowledge tag | Reading overflow/dialog | knowledge repository | R | Proposed Knowledge & references sheet | Backup | **NEEDS DESIGN DECISION** |
| N42 | Display/remove knowledge tags | Reading chips/menu | knowledge repository | R | Proposed Knowledge & references sheet | Backup | **NEEDS DESIGN DECISION** |
| N43 | Workspace-wide Pin/Unpin | Reading/editor top action | `setPinned` / `isPinned` | R/E | Proposed Note overflow | Backup | **NEEDS DESIGN DECISION** |
| N44 | Folder-local Pin/Unpin | Stage 2 note context More actions | `setFolderPinned` / `isFolderPinned` | Outside editor | Keep Stage 2 placement; do not duplicate | Backup | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N45 | Favourite/Unfavourite | Reading/editor top action | `setFavourite` | R/E | Proposed Note overflow | Backup | **NEEDS DESIGN DECISION** |
| N46 | Move note/workspace | Stage 2 context and move flow | note/folder repositories | Outside editor | Keep Stage 2 placement unless editor overflow is approved | Backup | **DEFERRED FROM EDITOR** |
| N47 | Create child note/sub-note | Stage 2 note context | `createNote(... parentNoteId)` | Outside editor | Keep Stage 2 placement | Backup | **ENGINE-ONLY / NO NEW UI REQUIRED** |
| N48 | Soft delete to Recently Deleted | Reading/editor overflow/dialog | `deleteNote` | R/E | Proposed Note overflow, semantic-error action | Backup | **NEEDS DESIGN DECISION** |

### D. History, export and retained block compatibility

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N49 | Automatic version snapshots/history list | Reading overflow/history dialog | version repository; max 30, minimum 5-minute capture interval | R | Dedicated Version history sheet/subpage | Backup | **NEEDS DESIGN DECISION** |
| N50 | Restore older version | Version history dialog | `restoreVersion` | R | Dedicated Version history sheet with confirmation | Backup | **NEEDS DESIGN DECISION** |
| N51 | Export TXT | Reading/editor overflow | existing TXT export callback | R/E | Dedicated Export sheet/overflow | External | **NEEDS DESIGN DECISION** |
| N52 | Export PDF | Reading/editor overflow | existing PDF export callback | R/E | Dedicated Export sheet/overflow | External | **NEEDS DESIGN DECISION** |
| N53 | Dormant checklist block compatibility | `EditorTool.Checklist`/block model, omitted from current main tool list | retained block model | Engine | Do not surface without approval | Backup compatibility | **ENGINE-ONLY / DEFERRED** |
| N54 | Dormant divider/advanced block compatibility | retained block types, no active main control | retained block model | Engine | Do not surface without approval | Backup compatibility | **ENGINE-ONLY / DEFERRED** |

### E. Narration and listening

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N55 | Listen to note | Reading header action | narration controller/provider | R | Proposed Note overflow direct action | Local/cache | **NEEDS DESIGN DECISION** |
| N56 | Narration provider chooser | Listen dialog | Device/Azure/OpenAI provider state | R | Dedicated Listen sheet | Local | **NEEDS DESIGN DECISION** |
| N57 | Continue/configure Azure narration | Listen dialog/settings route | Azure state and Settings route | R | Listen sheet to existing Azure Settings | Local | **NEEDS DESIGN DECISION** |
| N58 | Active-sentence highlight and follow audio | Reading body plus follow toggle | narration playback state | R | Contextual reading state; no Frozen reference | Local | **NEEDS DESIGN DECISION** |
| N59 | Listen from selected text | Native text-selection action | narration selection callback | E | Text-selection actions | Local/cache | **NEEDS DESIGN DECISION** |
| N60 | Global narration mini-player | Existing global production surface | player manager/controller | Global | Final design/placement remains deferred | Local | **DEFERRED** |

### F. Formatting provider workflow

| ID | Capability | Current entry/surface | Existing handler/state | Mode | Frozen coverage / proposed placement | Persistence | Status |
|---|---|---|---|---|---|---|---|
| N61 | Structure Only and Intelligent Structure | Floating `Structure & Format` pill and sheet | formatting repository/session | E | Proposed Formatting provider sheet via Note overflow | Note output | **NEEDS DESIGN DECISION** |
| N62 | Review output: Copy, Insert Below, Replace, Clear | Formatting result sheet | formatting output state and editor callbacks | E | Dedicated Formatting provider sheet | Note output | **NEEDS DESIGN DECISION** |
| N63 | Provider/model/session and retained Clean Format/Format Note actions | Formatting sheet/settings; two actions retained by engine but not current main sheet | formatting models/repository/session | E/Engine | Provider/model belongs in dedicated sheet; hidden actions remain compatibility-only | Local/note output | **STOP-AND-ASK BEFORE EXPOSING RETAINED HIDDEN ACTIONS** |

## 4. Frozen Coverage Summary

The Frozen Master directly covers:

- reading and editing canvases;
- editable title and body;
- saving state;
- bold, italic, underline, H1-H4, colour, bullet and numbered lists;
- undo/redo;
- mixed Arabic/English rendering;
- a Quote and Clear-formatting visual position, subject to the production
  capability conflict below;
- keyboard-adjacent formatting-bar presentation;
- compact Note overflow and Explorer access;
- a compact Edit affordance in reading mode;
- Light, Dark and OLED presentation.

It does not assign final positions for metadata counts/timestamps, tables,
attachments, URL/note links, tags, backlinks, PDF source references, pin,
favourite, delete, history, export, narration, or formatting-provider actions.

## 5. Proposed Action Hierarchy - Not Yet Approved

### Reading Mode

- Frozen compact header: Explorer, Note/path context, overflow.
- Document title, saved/updated state only if metadata placement is approved.
- Document-first rich-text canvas.
- Inline links remain directly tappable.
- Compact Edit affordance.
- No formatting bar until editing begins.

### Editing Mode

- Frozen compact header and editable document title.
- Saving/Saved state.
- Document-first rich-text field.
- Formatting bar docked above IME.
- No legacy floating AI pill unless a placement amendment explicitly approves it.

### Formatting Toolbar

- Undo
- Redo
- Paragraph/Heading selector containing Paragraph and H1-H4
- Bold
- Italic
- Underline
- Colour
- Bullet list
- Numbered list
- Quote, pending resolution of N19
- Clear formatting, pending proof/approval of production semantics

### More Formatting

- Table
- URL link
- Note link / `@` mention help
- Attach file
- Attach image
- No Checklist or Divider controls unless separately approved.

### Note Overflow

Proposed direct/high-value rows:

- Edit in Reading mode
- Listen
- Pin / Unpin
- Favourite / Unfavourite

Proposed restrained secondary destinations:

- Knowledge & references
- Attachments
- Version history
- Export
- Structure & Format in Editing mode
- Delete, with restrained semantic-error treatment

This hierarchy is a proposal only. It must not be implemented until approved.

### Text-selection Actions

- Native Copy/Cut/Paste/Select actions remain Android-owned.
- `Listen from here` remains an existing production action but needs approved
  placement alongside Android's selection menu.
- Applying toolbar marks remains selection-aware.

### Dedicated Sheets / Subpages

- **Version history**: snapshots, timestamp/current marker, Restore confirmation.
- **Knowledge & references**: knowledge tags, backlinks, source references, open
  source, remove source reference.
- **Attachments**: imported files/images, preview/open, add/remove if existing
  production semantics support removal.
- **Export**: TXT and PDF using existing production callbacks.
- **Listen**: provider choice, Azure continuation/configuration, follow-audio
  behavior if approved.
- **Formatting provider**: Structure Only, Intelligent Structure, provider/model,
  result review and Copy/Insert Below/Replace/Clear.
- **Table context**: create size, edit cells and delete table using existing table
  repository behavior.

## 6. Explicit Conflicts Requiring Decisions

1. **Quote control**: Frozen shows Quote in the direct toolbar. The engine can
   render/serialize `VaultInlineStyle.Quote`, but the current production main
   toolbar exposes no Quote command. Approval is required before adding a new
   visible adapter action.
2. **Clear formatting control**: Frozen shows Clear formatting. Production has
   selected-colour clearing but no equivalent current all-formatting command.
   Exact semantics must be approved before this control is wired.
3. **URL links versus note links**: current URL insertion writes visible URL text,
   while `@` mentions create structured backed-up note links. They must not be
   represented as one ambiguous Link action without an approved chooser.
4. **Metadata**: timestamps and word/character counts have no final Frozen
   placement. Adding them permanently would alter the approved sparse canvas.
5. **Tables and attachments**: they are separate production entities rendered
   after the rich-text body, not inline rich-text nodes. The Frozen document
   canvas does not define their row/block anatomy.
6. **Knowledge surfaces**: tags, backlinks and PDF source references are distinct
   production relationships and must not be merged semantically merely to reduce
   UI.
7. **Header actions**: legacy Reading/Editing headers permanently show Listen,
   Pin and Favourite. Frozen shows restrained overflow; moving these requires the
   action hierarchy above to be approved.
8. **Narration playback state**: sentence highlighting, follow-audio controls and
   the global mini-player are absent from the Frozen Note references. The global
   mini-player remains separately deferred.
9. **Formatting provider**: the legacy floating pill is not in Frozen. A secondary
   sheet entry requires approval, and hidden retained `CleanFormat`/`FormatNote`
   engine actions must not be surfaced automatically.
10. **Version history and export**: both are active production capabilities but
    have no Frozen presentation.
11. **Reading-to-edit gesture**: Frozen visibly provides Edit; preserving the
    existing double-tap gesture is proposed as invisible compatibility behavior.
12. **Dormant block types**: Checklist and Divider exist in retained models but
    are not in the current supported main toolbar. They must remain compatible
    without receiving invented UI.

## 7. Backup and Data Compatibility

Stage 4 presentation must preserve all current payloads and relationships:

- `rich_text` body blocks with text, style marks and note links;
- note title/body plain text and timestamps;
- note tables (`note_tables.json`);
- note versions (`note_versions.json`);
- attachments and their files;
- knowledge tags/links and source backlinks;
- `parentNoteId`, `isPinned`, `isFolderPinned`, favourite and ordering fields;
- Android/Web-compatible style mark and note-link validation.

No Room migration, ID change, serialization rewrite, backup schema change or
formatting-output rewrite is authorised by this audit.

## 8. Approved Stage 4 Resolution

The Frozen Editor amendment approves the following production mapping:

- Reading uses the compact Note header, document-first canvas, restrained
  overflow and Edit affordance.
- Editing uses the same canvas with saved/editing state and an IME-adjacent
  formatting toolbar.
- The primary toolbar contains Undo, Redo, Paragraph/H1-H4, Bold, Italic,
  Underline, Colour, Bullet, Numbered, Quote and More Formatting.
- Clear formatting is limited to the proven production command, `Clear selected
  colour`; no generic all-formatting command is invented.
- More Formatting contains Table, Add web link, Link to note, Attach file and
  Attach image using existing production handlers and persistence.
- Note overflow owns Listen, Pin/Unpin, Favourite/Unfavourite, Note info,
  Knowledge & references, Attachments, Version history, Export, Structure &
  Format while editing, and Delete.
- Note info contains Updated, Words and Characters rather than permanently
  displaying those values on the canvas.
- Tags, backlinks and PDF source references remain semantically distinct inside
  Knowledge & references.
- Tables and attachments remain production entities rendered in the document
  flow and managed through their approved contextual surfaces.
- Version history, restore confirmation, TXT/PDF export, Structure Only,
  Intelligent Structure and formatting-result actions use the frozen secondary
  sheets.
- Device TTS, Azure Speech TTS and OpenAI TTS appear as three restrained rows in
  the Listen sheet. OpenAI is an approved production-specific extension because
  it is an active persisted production provider.
- Active-sentence highlighting and selection-based `Listen from here` remain
  operational. The global narration mini-player presentation remains deferred.
- The existing double-tap-to-edit gesture remains as invisible compatibility
  behaviour alongside the visible Edit affordance.
- Checklist, Divider, hidden Clean Format/Format Note actions and conversational
  Ask AI receive no new UI.

Stage 4 implementation is authorised. No Room, repository, note-serialization,
backup-format or narration-provider data change is authorised by this amendment.

## 9. Stage 4 Implementation Record

Implemented on the `frozen-design-master-port` branch using the existing
production note, attachment, knowledge, version, export, narration and
formatting-provider handlers.

- Reading and Editing now share the compact Note workspace header and open
  document canvas.
- The shell-level menu row is suppressed on Note routes so only the approved
  Note header is rendered.
- The editing toolbar is attached to the editor viewport and remains above the
  native IME.
- More Formatting contains the approved Table, web link, note link, file and
  image attachment actions.
- Reading/Editing overflow and the approved secondary sheets use existing
  production callbacks.
- The Listen sheet contains Device TTS, Azure Speech TTS and the explicitly
  approved OpenAI TTS row. This is a production-specific extension to the
  frozen screenshot and does not alter narration-provider semantics.
- Runtime captures were made at approximately 412 x 892 logical dp in Light,
  Dark and OLED, with side-by-side frozen comparisons stored under
  `artifacts/stage-4/comparisons/`.
- Existing unit tests and the debug APK build pass. No Room migration, payload
  rewrite, backup-format change or repository-interface change was required.

The global narration mini-player remains deferred and was not redesigned in
Stage 4.
