# MyVault Note Widgets

## Scope

MyVault provides two launcher widgets:

- **MyVault Note** displays one selected Study or Course note.
- **Quick Note** creates one normal Study-root note and opens it in the existing Note Editor.

The widgets reuse production note IDs, repositories, folders, courses, editor routes, and lock handling. They do not introduce a widget-only note model, database migration, backup payload, or in-widget editor.

## Note Viewer

The configuration activity presents a combined, searchable Study and Courses note picker. Results show each note's location, and can be filtered to All, Study, or Courses. The widget's settings control can reopen this activity to change the selected note, adjust body text size, and show or hide the title and location.

Each widget instance stores its own note ID and display preferences in device-local `SharedPreferences`. Deleting one widget removes only that widget's preferences. Moving a note keeps the widget linked by stable note ID; deleting the note produces a safe `Note unavailable` state.

The widget uses size-specific `RemoteViews` layouts. Compact widgets prioritise the title and opening lines. Medium and larger widgets provide a scrollable collection of note paragraphs. Stored note text is not modified. The launcher rendering intentionally favours reliable plain text with paragraphs, line breaks, list markers, Arabic, and RTL direction over an incomplete rich-text editor renderer.

Tapping the title, location, or body sends an explicit note ID to `MainActivity`. Course notes also carry their Course ID so the existing Course location is revealed before the exact Note Editor route opens. When app lock is active, the request remains pending until successful unlock.

## Updates

`NoteWidgetUpdateCoordinator` observes the existing note, folder, and course flows while the app process is alive. It compares fingerprints and updates only widget instances whose selected note changed. Widget creation, resizing, package refresh, and settings changes also trigger normal Android widget updates. There is no timer or database polling.

## Quick Note

Quick Note sends a dedicated explicit action to `MainActivity`. After unlock, `MainActivity` calls the existing `NoteRepository.createNote(folderId = null)` path and opens the returned Note ID in the existing editor with quick-focus enabled.

A device-local elapsed-time guard rejects repeated launcher taps within 1.5 seconds, and the activity also keeps a creation in-flight guard. A note is never created before authentication succeeds.

## Privacy

The Note Viewer intentionally displays the selected note's content on the Android home screen. Only the note selected for that widget is queried and rendered. Widget preferences remain device-local and are not added to MyVault Backup/Restore.

## Deliberate Limits

- PDFs are not supported.
- Notes cannot be edited inside the widget.
- Full rich-text styling is not reproduced in `RemoteViews`; readable structure is preserved as plain text.
- Launcher widget dimensions and available resize stops remain launcher-dependent.
