# MyVault Web Context

This file is a high-level architecture and product brief for a future separate project: **MyVault-Web**. It exists so a future workspace can understand MyVault's philosophy, systems, and non-negotiables without needing this Android project conversation history.

It is not an Android implementation dump. It is the durable context needed to design the web version responsibly.

## 1. What MyVault Is

MyVault is a personal knowledge vault for serious study, research, reading, and long-term note keeping.

It is designed around:

- Notes, folders, and nested intellectual structure
- PDFs, documents, and library files
- PDF reading, highlights, and annotation notes
- Tags, references, backlinks, and source relationships
- Safe manual and cloud backup workflows
- A premium, calm, scholarly study experience

MyVault is not:

- A generic productivity app
- A social or collaborative app
- A team workspace
- A chat app
- A Notion, Obsidian, or Google Docs clone
- A dashboard-heavy recommendation product

The product should feel like a private intellectual archive: personal, focused, structured, and trustworthy.

## 2. Core App Structure

MyVault is organized into three major modes:

### Study

Study is the primary intellectual workspace.

Purpose:

- Active study notes
- Theological/research notes
- Long-term intellectual folders
- Nested folders/subfolders
- Pinned study material
- Search and note reading/editing

UX philosophy:

- Hierarchy-first
- Dense but readable
- Spatially stable
- Built for scanning intellectual structure
- The folder tree is a core strength, not a temporary list

Study should feel like a structured scholarly workspace.

### Library

Library is the reading and archive workspace.

Purpose:

- PDFs
- Books/documents
- Images and imported files
- Reading progress
- PDF highlights
- Annotation notes
- Source material that can later feed Study notes

UX philosophy:

- A personal scholarly archive
- Reading-focused
- Immersive PDF experience
- Folder/subfolder organization remains important
- Files and annotations should remain visibly connected to their source

Library should feel like entering a private reading shelf or archive, not an Android file manager.

### Personal

Personal is the private everyday notes and utility space.

Purpose:

- Personal notes
- Business/operational notes
- Temporary notes
- Non-study folders
- Attachments and everyday vault material

UX philosophy:

- Practical
- Calm
- Lightweight
- Similar to the original MyVault home experience

Personal should preserve the existing note/folder strengths without competing with Study.

## 3. Design Philosophy

MyVault's design direction is premium minimalism.

Core design principles:

- Calm surfaces
- Subtle blue accent
- Rounded cards/buttons without feeling childish
- Soft light/dark themes
- Smooth motion and swipe transitions
- No visual clutter
- No heavy dashboards
- No corporate productivity look
- No excessive gradients, glow, glassmorphism, or decorative UI

The UI should prioritize:

1. Reading
2. Study flow
3. Spatial awareness of knowledge
4. Safe organization
5. Contextual intelligence only where useful

The app should feel fluid and polished, but never flashy.

## 4. Android Architecture Summary

The Android app is built with:

- Kotlin
- Jetpack Compose
- Room database
- DataStore preferences
- Hilt dependency injection
- Internal PDF viewer
- PDF reading progress
- Area-based PDF highlights
- PDF annotation notes
- Tags and cross-topic relationships
- Source backlinks and references
- Google Drive incremental sync
- `.vaultbackup` snapshot backup/restore

Important Android concepts:

- Notes and folders are local-first.
- Study, Library, and Personal are presentation/context modes, not separate databases.
- Library files use the attachment/file infrastructure.
- PDF highlights do not mutate PDF files. Highlight/annotation data is stored separately.
- Manual `.vaultbackup` remains the disaster recovery mechanism.
- Google Drive sync is incremental and semi-manual.

The web app should not copy Android screen layouts directly. It should preserve the architecture and philosophy while using desktop-native interaction patterns.

## 5. Google Drive Architecture

Google Drive is used as structured cloud storage for incremental sync.

Drive layout:

```text
MyVault/
  metadata/
  files/
  manifests/
  backups/
```

### Metadata

Metadata is stored separately from large files.

Examples:

- Notes
- Folders
- Attachments metadata
- PDF reading progress
- PDF annotations/highlights
- Tags
- Source backlinks
- AI conversations/messages if included
- Reference relationships

### Files

Large files are uploaded individually.

Examples:

- PDFs
- Images
- Documents
- Other imported Library files

Files should be tracked by stable IDs and hashes, not just names.

### Manifest

The Drive sync manifest tracks:

- Cloud version
- Entry paths
- File names
- Backup entry names
- SHA-256 hashes
- File sizes
- Google Drive file IDs

The manifest drives push/pull decisions and restore reconstruction.

### Sync Model

The sync model is semi-manual:

- Device A: Push to Drive
- Device B: Pull latest from Drive

It is intentionally not realtime sync.

Reasons:

- Safer for personal knowledge vaults
- Lower corruption risk
- Easier recovery
- Lower bandwidth usage
- Better for large PDFs/files
- Avoids dangerous merge complexity early

Push should upload only changed metadata and new/modified files. Unchanged PDFs/files should be skipped.

Pull should download only missing/changed files where possible and apply metadata safely through the existing restore path.

Google Drive should be treated as the cloud source of truth for sync, while local data remains usable offline.

## 6. Data Model Concepts

The important conceptual relationships are:

### Folders and Subfolders

- Folders form a nested hierarchy.
- Folders can belong to Study, Library, or Personal context.
- Moving a parent folder moves its visible subtree.
- Folder hierarchy is central to MyVault and should not be replaced by tags or graphs.

### Notes

- Notes belong to folders or can exist at root.
- Study and Personal notes are normal notes surfaced by context.
- Notes can have rich text, formatting, tables, attachments, links, AI conversation history, tags, and references.

### Attachments / Library Files

- Attachments/files have stable IDs.
- Files can be attached to notes or live in Library folders.
- Library imports should be files directly, not fake notes with attachments.

### PDF Annotations

- PDF highlights and notes are stored separately from the PDF file.
- Highlights use page number/index and normalized rectangle coordinates.
- Annotation notes remain linked to the source PDF/highlight/page.
- Annotation data must survive rename, move, backup, restore, and app restart.

### Tags

- Tags are cross-topic relationships.
- Tags do not replace folders.
- Tags can attach to notes, files, and annotations.
- Tags must remain optional, compact, and non-cluttering.

### Source Backlinks and References

- Source relationships connect Study notes to Library sources.
- They should use stable IDs, not fragile text parsing.
- A Study note can reference a PDF, page, annotation, or highlight.
- A Library PDF can show where it is referenced in Study.
- Broken references must degrade gracefully rather than crash or delete note content.

## 7. Web App Goal

MyVault-Web should become a desktop knowledge workspace.

It should not be:

- A stretched phone UI
- A literal copy of Android screens
- A generic file manager
- A Notion-style document database
- A Google Docs collaborative editor

The web app should use desktop space properly:

- Better sidebars
- Larger reading surfaces
- Multi-pane layouts
- Contextual panels
- Keyboard/mouse ergonomics
- Better PDF and reference browsing

The goal is to make MyVault feel more powerful on desktop while preserving the calm, private, scholarly identity.

## 8. Web App Phase Plan

### Phase 1: Read-Only Web App

Initial web version should be conservative and safe.

Scope:

- Google login
- Connect to the user's MyVault Google Drive data
- Read Drive manifest
- Browse Study, Library, and Personal structure
- Read notes
- Search notes/files/annotations
- Open PDFs
- View PDF highlights
- View annotation notes
- View tags
- View source references/backlinks

No destructive edits in Phase 1.

Reason:

- Proves Drive compatibility
- Protects user data
- Avoids sync conflicts early
- Lets web UI mature before write support

### Phase 2: Editing and Push Support

Add controlled write capabilities:

- Edit notes
- Add/create folders
- Import files if safe
- Add/edit tags
- Add/edit references
- Push changes to Drive

This phase must include:

- Clear sync status
- Local change tracking
- Conflict warnings
- No silent overwrite of newer cloud data

### Phase 3: Advanced Sync Later

Only after the system is stable:

- Better conflict detection
- Possible per-entity sync
- Smarter merge tools
- Device comparison
- Recovery views
- Optional background update checks

Do not start with realtime collaboration.

## 9. Web UI Direction

The desktop web app should use a layout suited to serious reading and study.

Suggested desktop structure:

```text
Left Sidebar       Main Reading/Editor Area        Right Context Panel
------------       ------------------------        -------------------
Study              Note / PDF / Search Result      References
Library            Reading surface                 Tags
Personal           Annotation focus                Source links
Folders            Editor when enabled             PDF annotations
Search             Current document                Related context
```

### Sidebar

Use for:

- Study / Library / Personal switch
- Folder hierarchy
- Search entry
- Pinned items if useful

The sidebar should be calm and structured, not crowded.

### Main Area

Use for:

- Notes
- PDF reading
- Search result detail
- Folder content
- Annotation detail

The main area should prioritize reading comfort.

### Contextual Panel

Use for:

- Tags
- Source backlinks
- References
- Annotation list
- PDF metadata
- Reading progress

The contextual panel should be optional/collapsible and only show useful context.

### Responsive Layout

Desktop:

- Multi-pane layout

Tablet:

- Sidebar can collapse
- Context panel can become drawer/sheet

Mobile web:

- Should adapt carefully, but Android remains the primary mobile experience

## 10. Non-Negotiables

- Preserve data integrity above all else.
- Preserve backup safety.
- Do not silently overwrite newer cloud data.
- Do not implement destructive sync without confirmation.
- Do not create separate duplicate note systems.
- Do not replace folders with tags, graphs, or dashboards.
- Do not mutate original PDF files for annotations initially.
- Do not introduce realtime sync complexity early.
- Do not auto-merge conflicts dangerously.
- Do not clutter Study or Library with AI/recommendation surfaces.
- Do not make the UI corporate, loud, or productivity-dashboard-like.
- Keep `.vaultbackup` disaster recovery compatible.
- Keep Google Drive incremental sync understandable and recoverable.
- Broken references should degrade gracefully.
- Missing files should never crash the app.

## 11. What The Web App Should Not Do

The web app should not:

- Force realtime sync
- Add social features
- Add team collaboration
- Behave like a chat app
- Become a generic cloud drive UI
- Become a corporate dashboard
- Add aggressive AI clutter
- Hide the folder hierarchy behind feeds or recommendations
- Auto-merge conflicting edits silently
- Delete or overwrite data without confirmation
- Depend only on visible source text for backlinks
- Assume every device is always online
- Treat Google Drive as a black-box file dump without a manifest

## Final Direction

MyVault-Web should extend MyVault into a desktop-grade private study environment.

The correct direction is:

- Local-first philosophy
- Google Drive-backed sync
- Readable and recoverable metadata
- File-efficient cloud storage
- Calm desktop UI
- Rich reading and reference workflows
- Safe, staged editing later

Build the web project slowly and carefully. Phase 1 should prove that the web app can read and present the existing vault safely before it is allowed to write back to Google Drive.
