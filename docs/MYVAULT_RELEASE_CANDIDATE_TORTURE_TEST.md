# MyVault release-candidate physical torture test

## Status

IN PROGRESS. No RC PASS or final RC tag has been issued.
Portrait only; rotation is excluded throughout.

## Starting checkpoint

- Source: `87cae01d09001667487c69de1caaef267dfcd847`.
- Branch: `frozen-design-master-port`.
- Origin: `git@github.com:aliabuzahra95/MyVault.git`.
- Recovery: `pre-rc-torture-20260909-170747` (pushed before testing).
- Tracked tree clean at start. Pre-existing untracked screenshots/artifacts
  and APK signature sidecars left untouched.
- Device: Samsung SM-F966B, serial RFCY70CMWZR, Android 16 / API 36.
- Reported One UI property: 80500 (raw value, not inferred marketing version).
- Data partition: 457 GB total, 351 GB available at start.
- Initial installed APK: signed diagnostic build; SHA-256
  `85a18bbb5c9aa5414b1a3c1158045b42eac1a587b996666cf641adf6fa66600b`.
- Signer SHA-256:
  `5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3`.
- Updated in place, without uninstall/clear-data, to the delivered release
  APK `MyVault-0.1.0-PRE-RELEASE-87cae01-Library-Organisation-signed.apk`.
  Package `com.myvault.app`, version 0.1.0 / code 1. Candidate SHA-256:
  `ca9e935eb4812ec61958e02656be534cddc4b0494aabb06f8a885462521476ba`.
- JBR 21 baseline `:app:test :app:lint :app:assembleDebug :app:assembleRelease`
  passed. 339 unit cases in each variant; lint has 0 errors / 283 warnings.
  Existing cached checks are baseline evidence, not new physical acceptance.

## Safety

Existing production content is read-only wherever practical. Any created test
content must use the `RC-20260909` prefix and be recorded here. Destructive
operations are allowed only against those recorded disposable fixtures.
No real Drive file may be deleted or moved; no primary-vault restore, data
clear, production uninstall, schema/manifest/format change, or rotation.

## Live acceptance matrix

| Area | Required physical coverage | State |
|---|---|---|
| Startup | Cold, warm, resume, process restart, lock | PARTIAL: three cold/warm cycles pass; lock pending |
| Navigation | Dashboard, drawer, all roots, rapid switching, Back | PARTIAL: 24 switches and three root Back cycles pass |
| Study | Nested folders, creation/move, pin/favourite, delete/restore | PARTIAL: A/B/C hierarchy, pin/favourite and note delete/restore pass |
| Editor | Long/short, rich text, Arabic/English, autosave, background | PARTIAL: short-note autosave/background/process restart pass |
| Library | Nested folders, PDFs, pins, metadata, expansion | PENDING |
| PDF | Scroll/progress, highlight/note, Activity, previews, jumps, bounce | PENDING |
| Courses | List, Continue, folders/notes, sticky notes, concepts | PENDING |
| Qur'an | Surahs, exact ayah, translation, Tafsir, audio | PENDING |
| Memorise | Full Surah, resume, statuses, long Surah | PENDING |
| Search | Notes, Courses, Library, Qur'an, exact routes | PENDING |
| Dashboard | Four independent Continue slots, global Recents, reflections | PARTIAL: deleted target fix retested; course/Personal exact routes pass |
| Settings | Appearance, reading, security, storage, account | PENDING |
| Widgets | Qur'an, Note Viewer, Quick Note, resize, per-instance state | PENDING |
| Incoming Share | Cold/warm, text/HTML/file where supported, duplicate intent | PARTIAL: plain/HTML import routes pass; adjacent HTML list formatting fails |
| App lock | Real unlock and pending destinations/actions | PENDING |
| Recently Deleted | Disposable note/folder/file restore and safe purge | PENDING |
| Audio | Focus, playback, pause/resume, background, source switching | PENDING |
| Offline | Local usability, truthful failures, recovery | PENDING |
| Performance | Startup, large PDF/note/trees, memory, widget updates | PENDING |
| Long session | Repeated cross-feature usage and cumulative state | PENDING |
| Adversarial state | Deleted/moved targets, competing pending routes | PENDING |
| Final consistency | Fixture counts, annotations, progress, widget targets | PENDING |

## Fixtures

- Study folder chain: `RC-20260909-A / RC-20260909-B / RC-20260909-C`.
- Nested note: `RC-20260909-Nested-note`, text markers A1909 and B1909.
  Pinned/favourited, deleted, then restored individually. Text, nested location,
  pin and favourite survived. Deleted again for the RC-01 retest; currently in
  Recently Deleted, not permanently removed.
- Shared notes: `RC-20260909-Shared-plain` and `RC-20260909-Shared-HTML`.
  Imported through real ACTION_SEND into the existing Personal Inbox. No existing
  Inbox content changed. Arabic/English text and basic emphasis visually checked.
- Course: `RC-20260909-Course`; note `RC-20260909-Course-note` (C1909),
  sticky text ending END-STICKY1909, concept `RC-20260909-Concept` (CONCEPT1909).
- Baseline Study counts: 20 folders / 75 notes. After nested fixtures:
  23 folders / 76 notes. Shared Personal notes do not increase Study count.
  Baseline Courses: 5; disposable course makes 6.
- Actual launcher Quick Note created `RC-20260909-Quick-note`, marker QUICK1909,
  at Study root. Current Study count is 23 folders / 76 notes with the nested
  fixture deleted. One disposable Quick Note widget remains on the launcher.
- Personal Inbox long-note fixture: `RC-20260909-Long-mixed`, 150 mixed
  Arabic/English paragraphs, 22,583 original characters, original UTF-8 SHA-256
  `32ab55844dc0bc3e027c19ca6aeebeea2ce54f223bcb7ea061fb3a7eff680497`.
  Added text LONG-EDIT1909 survived immediate background/reopen. Removing only
  that added text from the observed editor value exactly reproduces the original.

## Findings

### RC-01: deleted Dashboard target opens stale body (P1)

Reproduction on candidate ca9e935: open the disposable nested note, delete it
through Note actions, return to Dashboard, tap its retained Continue entry.
Actual: an `Untitled note` screen displays the deleted note's old body. Search
correctly drops the deleted note after its live query updates.

Cause: Dashboard history is persisted display metadata, not reconciled with
current active rows, and its open callback unconditionally routes the stored ID.
Completed fix: resolve history against active entities and validate a selected
target again before routing. No persisted history, schema, restore or backup
format changes. Five focused tests cover deletion/restoration, missing parent,
renaming/Personal context, course moves and deleted PDFs/exact page retention.
Physical retest PASS on signed release bb54637: deleted fixture disappears from
both Continue and Recents; active Personal and course targets remain correct.
The fixture is currently in Recently Deleted after this second deletion.
Commit recorded in the stage history below.

### RC-02: Personal note labelled Study on Dashboard (P2)

The real shared fixtures show `Personal / Inbox` in global Search but `Study /
Inbox` on Dashboard. Same metadata resolution fix as RC-01; physical retest PASS:
Dashboard now displays `Personal / Inbox` without changing the note location.

### RC-03: adjacent HTML lists concatenate (P2, protected boundary)

Normal warm ACTION_SEND with `<ul><li>Bullet one</li><li>Bullet two</li></ul>`
immediately followed by `<ol><li>Number one</li><li>Number two</li></ol>` displays
`Bullet two1. Number one` on one line. Screenshot `warm-html-share.png` confirms
the issue. Bold, italic, Arabic and link styling render; list item text is present.
`RichImportParser.normalizeListsForVaultImport` removes list wrapper boundaries
without adding a separator between adjacent lists. No parser change made because
incoming Share/rich-text compatibility is protected. Workaround: separate the
lists with a paragraph before sharing. Not evidence of broad data loss.

### RC-04: PDF saved-page regression on repeated reopen (P2)

Read-only reproduction with `Al_Tabari_Creed_English.PDF` (43 pages, 9 highlights,
5 notes): Go to page 20 -> Dashboard says page 20 -> reopen says 19 -> reopen
again says 18. Canonical PDF content and annotations were not edited. Initial
saved position was page 11 and was returned there after testing.
The AndroidX API explicitly includes partially visible pages in firstVisiblePage;
the current screen treats it as the reading page. Screenshots show the requested
page centred with a preceding-page fragment visible. Completed fix selects the
page occupying the greatest visible height, with centre-distance tie-breaking,
and prevents initial-load callbacks overwriting the pending saved destination.
Scoped to reading-position reporting, not annotation geometry or stored format.
Four consecutive physical reopen cycles remained on page 20; original page 11
restored afterwards. Existing annotation counts remained 9 highlights / 5 notes.

## Execution log

- 17:07-17:10 AEST: repository/device/checkpoint/baseline verified. Initial
  diagnostic build cold activity launch 372ms (not counted as release timing).
  Device was unlocked and in portrait. Release APK installed successfully.
- 17:15-17:18: three cold process launches, three Home/resumes, 24 drawer switches
  across all eight roots, three Settings-to-Dashboard Back cycles PASS. Actual
  release screenshots captured and Dashboard inspected. Footer labels are visible.
  See local `artifacts/rc-torture-20260909/startup-navigation.json` for timings.
  UI-observed timing includes approximately 3s accessibility dump overhead; it
  is not a precise first-frame/usable measurement. Android activity timings are
  separately recorded. A background `am kill` attempt did not terminate the
  process, so it is NOT counted as a system-recreation pass.
- 17:21-17:33: nested folder/short-note creation, immediate background after text
  entry, normal reopen and force-stop/manual reopen preserved A1909/B1909. Global
  Search opened the exact fixture. Note info reports 12 words/90 characters.
  Pin/favourite survived individual Recently Deleted restore. Existing deleted
  items were not restored or purged. RC-01/02/03 reproduced as above.
- Host scripts drive only the real installed app's visible UI. No replacement
  Compose screen, mock graph or fixture database is used for physical claims.
  Screenshot capture removes a Samsung multi-display warning prefix before PNG
  decoding; orientation stays portrait.
- 17:39-17:45: disposable Course note, sticky note and concept card created.
  Sticky text appears in full above Course Notes; concept remains distinct.
  Dashboard fix release build passed, signed with the unchanged production key,
  installed in place and installed bytes verified as
  `bb54637d31310a33a948365cbcfc55f721fa46fae4abb45a517a248bfab02f93`.
  Physical deletion/Personal-label retest PASS; Course Continue opens C1909 and
  Back returns to its exact Course. Full suite: 344 tests per variant PASS,
  lint 0 errors / 283 warnings, debug and release/R8 PASS, diff check PASS.
- 17:45 onward: read-only PDF jump/reopen exposed RC-04; annotation previews
  render and existing 9-highlight/5-note counts remain unchanged. Full PDF
  annotation/bounce/performance acceptance is still pending.

## Completed stages

- `5846da9`: Dashboard active-target resolution and five regression tests,
  committed and pushed to `frozen-design-master-port`.
- PDF position stage: installed signed release SHA-256
  `9737e1d1e5a447804689d2fa6ad07b91d4b3db7e0303b63b667bda573f686c1f`;
  installed bytes and unchanged CN=Ali signing certificate verified. Four real
  Samsung reopen tests PASS. Five new focused tests PASS; full 349 debug and
  349 release tests PASS; lint 0 errors / 283 warnings; debug and release/R8 PASS.
  Logs: `/tmp/rc-pdf-position-build.log`, `/tmp/rc-pdf-full-gates.log`.
- Actual Samsung widget picker: Quick Note registered with accurate preview;
  adding and tapping it opens a new Study-root note editor without a chooser.
  Only one note was created in the observed sequence. Rapid-tap, lock and resize
  acceptance remain pending, not inferred from this result.
- Long-note test initially expected the added text at the end. Samsung keyboard
  input left the cursor in the middle instead. Exact text comparison confirms
  all original content plus exactly one addition survived. This was a harness
  cursor assumption, not a demonstrated autosave defect.
- Host UI driver now refuses a dump unless Android confirms a fresh snapshot,
  avoiding accidental use of stale accessibility data after an idle timeout.

## Final gate

Not reached. All PENDING rows require evidence before final RC classification.
