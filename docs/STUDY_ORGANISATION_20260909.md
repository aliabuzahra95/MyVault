# Study Organisation

## Checkpoint and scope

- Repository: MyVault Complete Before Tutor, branch `frozen-design-master-port`.
- Starting commit: `07caea4cfe976f2638d7e50c2c40bb4181490c05`.
- Pushed recovery tag: `pre-study-organisation-20260909-07caea4`.
- Tracked baseline was clean. Existing untracked screenshots and release artifacts were preserved.
- Baseline Study/folder/navigation tests and debug build passed using JBR 21.
- Study only. No backup format, restore, Drive, Room schema, PDF, Quran, widget, Course, or Personal workflow changes.

## Implementation

The previous Study Organize action only displayed Done. Its entire hierarchy was one lazy-list item, with no drag callbacks. Existing repository arrow operations also changed `updatedAt`.

Manual reordering now uses a virtualized list with stable IDs, 48dp long-press drag handles, active-item elevation, placement animation, and edge auto-scroll. The existing Apache-2.0 Reorderable library (`sh.calvin.reorderable:reorderable:3.1.0`, https://github.com/Calvin-LL/Reorderable) owns gesture and scroll mechanics. Study owns sibling validation and persistence. Physical testing found immediate-drag gestures competing with scrolling; deliberate long-press ownership resolved that failure without changing persistence.

- Folder `parentId` and note `folderId`/`parentNoteId` are unchanged.
- Existing mixed folder/note `orderIndex` is reused. One transaction normalizes only the affected sibling group.
- Dragging changes a local presentation draft; one drop saves once. Reactive updates do not replace an active draft.
- Cross-parent, missing, and duplicate submitted IDs are rejected. Hidden or concurrently added siblings keep their slots.
- `updatedAt` and `createdAt` are not changed by reorder.
- Existing create paths append at the next sibling index. No new creation semantics.
- Manual, Alphabetical, Recently modified, Recently created, and Recently opened apply recursively to siblings, never flattening the hierarchy.
- Automatic sorts are presentation-only and do not overwrite manual order. Alphabetical comparison uses a case-insensitive root-locale collator with stable ID fallback; dates use deterministic tiebreakers.
- Sort mode and actual opening timestamps reuse the device-local Dashboard activity preferences. Notes use existing editor-open events; Study folder expansion records a real folder-open event. No fake modified timestamps.
- Existing retained note-open history seeds the index; older unrecorded history cannot be reconstructed. Unknown items use manual order until opened.
- Manual indices already participate in existing backup serialization. Local sort preferences and open-history additions are not added to backups.
- Pinned preview cards remain separate and unchanged. Pinned flags remain unchanged; the normal Study hierarchy follows the chosen sort, including Manual indices.

## Menus

Per the user's follow-up, Sort / Organize is inside the item popup only, not on the main Study screen. It opens the five choices; Manual enters drag mode, Done exits it.

Folder: New note, New subfolder, Rename / Edit description, Change colour, Sort / Organize, Move, Move to Personal workspace, then separated Delete.

Note: Rename, Create sub-note, Pin/Unpin, Favourite, Pin within folder, Sort / Organize, Move, Move to Personal workspace, then separated Delete.

No Open or More actions submenu in Study. Other workspaces retain their existing menus. Colour returns directly through the existing callback; no new CRUD implementation.

## Verification

- Full debug and release unit suites: 325 tests each, zero failures. Final JBR 21 `test lint assembleDebug assembleRelease :app:assembleDebugAndroidTest` passed in 9m 11s, including release/R8. `git diff --check` passed.
- Samsung SM-F966B: tests use a separate disposable Room database and isolated preferences, never the user's vault records.
- Final Samsung test passed in 49.069 seconds: root note and folder dragging, nested mixed siblings, top-to-bottom and bottom-to-top held drags with auto-scroll, Manual restoration after automatic sorts, unchanged modification timestamps, rejection of cross-parent/duplicate/missing IDs, database reopen persistence, and persisted real note/folder-open timestamps.
- Visually inspected Samsung captures in `artifacts/study-organisation-20260909/`: folder-actions, note-actions, sort-options, nested-manual, manual-restored, and autoscroll-bottom. Main screen has no Sort / Organize control; it is only in the popup.
- No live Drive/backup operation performed.

## Acceptance status

Physical drag, menu, and build checks passed. The final optimized release build was signed with the existing Samsung-compatible certificate and installed in place, with no uninstall or data clear. A force-stop/cold launch opened Dashboard; the actual Study screen retained its pinned strip and expanded hierarchy, and had zero main-screen Sort / Organize controls.

Installed APK SHA-256: `a050fe5527107aa6d40fc97b4749917b4d74613a5cfb17511d33e641328a538e`.

Completion recovery tag: `study-organisation-complete-20260909` (created after verification).

Automated Samsung inspection is not a claim of user visual approval. The fixture has 3 folders and 29 notes; this is not a thousands-of-items stress benchmark. Existing CRUD callbacks are reused, not replaced; destructive actions were not exercised against the user's records. Full device reboot and a live backup/restore round trip were not performed. Existing backup compatibility unit tests run in the full suite.
