# Note Editor Cursor / Keyboard Scroll Fix

## A. Checkpoint
- Repository: MyVault Complete Before Tutor; branch: frozen-design-master-port.
- Starting commit: 6cd1a666bf35069ffa3c6e35ed9532e7d0a5c477.
- Pushed recovery tag: pre-note-cursor-scroll-fix-20260910-121506.
- Unrelated untracked artifacts were preserved. No backup/restore, Drive, schema, stored rich-text, or note-ID changes.

## B. Proven Root Cause
Two independent defects were reproduced on Samsung. Reading used a lazy list while Edit created a separate scroll state at zero, without transferring the reading position. Separately, Compose's native first-focus handler could request visibility for its cached selection before the actual pointer selection arrived. In the disposable long note the stale selection was at the document END, not offset zero. The recording showed a jump from around paragraph 78 toward paragraph 137, then back.

## C. Competing Effects
- New editor viewport initialized at zero.
- Native BasicTextField/CoreTextField focus relocation used the previous selection.
- The existing custom caret/IME bring-into-view effect then relocated to the current selection.
- Notes with tables additionally constrained the body to a small internally scrolling field above the outer table scroller.

## D. State Ordering
Capture the visible reading text position; enter the existing Editor route; construct the existing document; apply a validated reading anchor after measurement but before placement; wait for a user tap; establish its selection before native focus; apply only necessary caret avoidance as the viewport/IME changes.

## E. Viewport Anchor
The navigation-only anchor carries note ID, text length/hash, character offset, and fractional line position. It is validated against the current document and remapped using the editor's line layout. It does not store note content or enter backups. Reading keeps layouts only for composed chunks. There is no additional full-document parse on tap.

## F. Cursor / Focus
An unconsumed initial pointer event seeds the exact character selection before focus. Native scrolling gestures, selection, and RTL handling remain responsible for the actual interaction. Ordinary Edit entry does not open the keyboard automatically. Existing explicit Quick Note autofocus remains unchanged.

## G. IME / Scroll Ownership
The focused note body has one caret-scroll owner. Native stale focus relocation is suppressed for that body; an already-visible caret produces zero movement. An obscured caret produces an immediate, bounded correction with the existing comfort margin. Body text above tables now uses that same outer viewport instead of the 220dp nested body scroller; table-cell editing itself remains unchanged.

## H. Arabic / RTL
Mixed Arabic/English paragraphs and isolated Arabic lines were exercised on Samsung. Screenshots show the Arabic caret visible above the keyboard without a wrong-position jump. Synthetic rich-text tests also preserve heading/bold ranges and exact Arabic wording after editing.

## I. Long Notes / Repetition
The 22,617-character, 150-paragraph disposable note passed ten repeated Reading -> Edit -> tap transitions, spanning top, approximately quarter/middle/three-quarter positions, and near the end. Every focused trace retained one selection. Five taps required no movement; the other five moved 580-627 physical pixels monotonically for keyboard clearance, with no overshoot/return.

## J. Study / Course / Saving
Explicitly opted-in tests expanded only the existing RC-20260909-Quick-note and RC-20260909-Course-note fixtures with 100 mixed-language paragraphs. Full editor text was compared after normal repository autosave and reopen. Both passed. Focus/selection-only changes caused no content-save callback in the isolated tests. Immediate exit after an edit preserved exact text and formatting ranges.

## K. Physical Evidence
- Device: Samsung SM-F966B, Android 16/API 36, outer portrait display, 1080x2520, 420dpi.
- Before: rc-unfocused-before.mp4 and rc-cursor-before.mp4.
- After, focused motion comparison: rc-cursor-single-owner.mp4.
- Ten repetitions: cursor-repeat-results.json, cursor-repeat-*.log and screenshots.
- Study/Course: cursor-study-live-keyboard.png and cursor-course-live-keyboard.png.
- Signed release: cursor-launcher-keyboard.png and cursor-launcher-resume.png show the same paragraph/caret after Home -> normal My Vault launcher icon. Reading background/resume was also checked, followed by Edit.
- Note Viewer widget opened the exact Course editor on the release build: cursor-widget-exact-course-editor.png.
- Dashboard Continue and Global Search were used as real entry routes. A fresh text Share opened the new disposable note with Arabic text; a subsequent edit survived exit and reopening from Dashboard with every original character preserved.
- Sample diagnostic timing: pointer-selection update to focus about 9ms; first IME inset about 141ms after focus; keyboard settled in about half a second. These are individual traces, not a statistical latency benchmark.

## L. Tests / Builds
JBR 21 used throughout.
- Debug unit suite: 355 passed, zero failures/errors/skips.
- Release unit suite: 355 passed, zero failures/errors/skips.
- Samsung instrumentation: 5 passed, including both live fixtures, rich text, table-adjacent text, and the existing attachment-return regression.
- Lint passed with warnings, no errors. Debug and release/R8 builds passed.
- git diff --check passed.
- Test-harness corrections were required for styled accessibility labels, order-independent style-mark comparison, and drawer animation timing. ActivityScenario lifecycle cleanup was not used as proof of launcher resume; that was verified separately through the actual launcher on the signed release.

## M. Delivery / Version Control
- Commit: 6aaf610e9aebd031a8243c02220fcb3087ff4975.
- Pushed branch: frozen-design-master-port.
- Pushed completion tag: note-cursor-scroll-fix-complete-20260910.
- Signed release installed on Samsung without uninstalling or clearing app data.
- Local test delivery: /tmp/myvault-cursor-release-signed.apk.
- APK SHA-256: b421bb2afdeea050f63d968a4f37f2e489f187f9abbd714cd9f1814a7725fa2f.
- Signature verified against the existing CN=Ali certificate.
- No Google Drive files were moved, deleted, or modified. No Drive upload was part of this focused task.

## N. Remaining Limits
This is not a new exhaustive release-candidate torture test. Verification used one Samsung outer-display configuration; other keyboards, folded/inner-display transitions and process-death timing were not exhaustively retested. Table-cell navigation was not redesigned. All user data outside explicitly named disposable fixtures was left untouched. The new Share fixture was retained rather than deleted.
