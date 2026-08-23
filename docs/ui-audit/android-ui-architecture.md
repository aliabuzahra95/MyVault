# MyVault Android UI Architecture Audit

## Scope

This audit covers the native Jetpack Compose presentation layer before the mobile Web visual redesign. Existing repositories, Room data, backup/restore, Google Drive, PDF, note, course, Study, Library, Quran, and Memorisation behaviour remain authoritative and are outside the redesign boundary.

## Application Shell

- `VaultNavHost` owns the top-level navigation graph and the shared Study/Library/Courses/Quran/Memorise shell.
- The five primary work areas use a `HorizontalPager` with `VaultFixedBottomNavigation`.
- Detail destinations such as folders, notes, settings, attachments, and readers remain separate navigation routes.
- `Scaffold` ownership is currently screen-specific. `HomeScreen`, `LibraryScreen`, editor, readers, settings, search, and detail screens each provide their own page surface.

## Shared Presentation Components

- `VaultTopBar` and `ScreenTopBar` provide root and detail-screen chrome.
- `VaultFixedBottomNavigation` owns the persistent five-tab navigation.
- `FolderTreeRow` owns recursive Study hierarchy, expansion, selection, long press, and organise interactions.
- `VaultModal` provides the preferred shared modal and bottom-sheet language, although legacy `AlertDialog` calls remain in several screens.
- `SearchBar`, `SectionLabel`, `IconBtn`, `FloatingActionMenu`, note cards, and attachment components are reusable but still contain some local sizing decisions.

## Existing Design System

- `VaultColors` already provides semantic light/dark surfaces, text hierarchy, accent states, warning, success, and scrim colours.
- `VaultShapes` and `VaultSpacing` centralise most corner radii and spacing.
- `VaultTypography` provides one Sans Serif family, but body sizes are dense and some headings use negative letter spacing.
- Motion timing and minimum interactive dimensions are not yet centralised.

## Screen Ownership

- Study root: `HomeScreen`; Study folder details: `FolderViewScreen`.
- Library: `LibraryScreen`; PDF and document display: `ReadingScreen` and `AttachmentViewerScreen`.
- Courses: `CoursesScreen` plus course destinations routed by `VaultNavHost`.
- Notes: `EditorScreen`, shared editor components, and rich-text conversion utilities.
- Quran: `QuranShellScreen` and components under `ui/quran`.
- Memorisation: `MemoriseShellScreen` and Quran memorisation sheets.
- Settings: `SettingsScreen`.

## Study Reference Findings

1. The recursive tree is the correct interaction model and should be retained.
2. Folder and note rows use 30-36 dp minimum heights, below the intended accessible touch target.
3. The disclosure icon has a very small independent hit area.
4. Subfolders use a hard-coded red icon colour instead of the selected accent token.
5. Organise mode runs an infinite shake animation across movable rows.
6. Expanded top-level folders add a full border; the mobile Web reference relies more on spacing and a subtle surface.
7. Expansion motion changes height but lacks the restrained fade used by the mobile Web hierarchy.
8. Study content mixes many local dp values and several overlapping utility controls.
9. Search, pinned content, hierarchy, reflections, selection, long press, create, rename, move, and delete behaviour must remain intact during visual work.

## Mobile Web Design Mapping

| Mobile Web quality | Native Compose interpretation |
| --- | --- |
| Compact page hierarchy | Clear page title, muted metadata, then section label and content |
| Calm dark neutral surfaces | Use `VaultColors.bg`, `surface`, and `elevated`; avoid new hard-coded colours |
| Restrained accent | Accent icons, active controls, and selection only |
| Lightweight folder rows | Whitespace and subtle active surfaces instead of boxed rows and heavy borders |
| Nested knowledge tree | Keep recursive inline expansion with stable keys and saved expansion state |
| Native interaction | 48 dp targets, ripple, long press, haptic feedback, and short eased motion |
| Dense but readable type | Strong 24-28 sp page title, 15-16 sp row titles, 12-14 sp metadata |
| Quiet motion | 120-220 ms state transitions; no perpetual animation |

## Shared Changes Approved For Stage 1

- Add central layout, icon, touch-target, and motion tokens beside the existing theme tokens.
- Remove negative heading letter spacing.
- Refine `FolderTreeRow` once so Study root and Study folder details stay consistent.
- Preserve all callbacks and data contracts.

## Deferred Until Study Acceptance

- Broad visual propagation to Library, Courses, Quran, Memorisation, PDF, editor, and Settings.
- Replacing every legacy dialog.
- Changes to repositories, persistence, backup formats, Drive, or navigation destinations.
