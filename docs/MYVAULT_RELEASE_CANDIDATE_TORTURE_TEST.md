# MyVault release-candidate physical torture test

## Status

WAITING FOR HUMAN AUTHENTICATION: Samsung reconnected and acceptance resumed.
Enabling the existing app lock opened the real fingerprint/PIN prompt; user must
authenticate on device. No RC PASS or final RC tag has been issued.
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
| Editor | Long/short, rich text, Arabic/English, autosave, background | PARTIAL: short-note process restart and 150-paragraph mixed-text background autosave pass |
| Library | Nested folders, PDFs, pins, metadata, expansion | PENDING |
| PDF | Scroll/progress, highlight/note, Activity, previews, jumps, bounce | PARTIAL: four reopens pass; disposable one-shot highlight/note, exact jump and expanded-sheet flings pass; large-file/destructive coverage pending |
| Courses | List, Continue, folders/notes, sticky notes, concepts | PARTIAL: fixture note, full sticky, concept, Continue and exact Back route pass |
| Qur'an | Surahs, exact ayah, translation, Tafsir, audio | PARTIAL: exact 91:1, Tafsir source switching and sheet resume pass; audio pending |
| Memorise | Full Surah, resume, statuses, long Surah | PARTIAL: Al-A'laa whole Surah to 87:19 and scroll resume pass; recording/status mutation pending |
| Search | Notes, Courses, Library, Qur'an, exact routes | PENDING |
| Dashboard | Four independent Continue slots, global Recents, reflections | PARTIAL: deleted target fix retested; course/Personal exact routes pass |
| Settings | Appearance, reading, security, storage, account | PARTIAL: Light/OLED/Dark Dashboard checks pass; restored original Dark; security pending |
| Widgets | Qur'an, Note Viewer, Quick Note, resize, per-instance state | PARTIAL: all three added on actual launcher; Study/Course exact routes and refresh pass; resize/multiple-instance/lock pending |
| Incoming Share | Cold/warm, text/HTML/file where supported, duplicate intent | PARTIAL: plain/HTML import routes pass; adjacent HTML list formatting fails |
| App lock | Real unlock and pending destinations/actions | BLOCKED: existing security lock enabled; fingerprint/PIN prompt awaiting user |
| Recently Deleted | Disposable note/folder/file restore and safe purge | PARTIAL: nested note restore preserves body/location/pin/favourite; folder/file/purge pending |
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
- Three disposable launcher widgets remain: Quick Note; MyVault Note currently
  showing `RC-20260909-Course-note` with dark appearance/text size 3; Qur'an Reader
  showing Ash-Shams with translation/Tajweed on, dark appearance and size 4.
  Qur'an widget was placed by Samsung on another launcher page. Existing launcher
  icons/widgets were not intentionally moved or deleted. No fixture cleanup was
  attempted after device disconnection.
- Quick-note body additionally contains WIDGET-REFRESH1909 from the exact-editor
  refresh test. Existing non-fixture note bodies were not edited.
- App Qur'an reading position is now 91:1 after the exact-widget-open test;
  original app position was 2:163. On resumed Dashboard, Continue still displays
  2:163: the exact widget destination did not overwrite that saved Continue value.
- `RC-20260909-PDF-fixture.pdf`: 2-page disposable existing test document copied
  to device Downloads, then imported through Library Upload file. Created one
  drawn rectangle plus one selected-text highlight note with body
  `RC-PDF-NOTE1909 disposable annotation.` Counts: 2 highlights / 1 note.
  Fixture file and imported attachment remain for subsequent deletion/restore.

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

### RC-05: widget appearance labels are not toggle targets (P3)

In Note widget settings, tapping the word Dark twice leaves Light selected.
Tapping the adjacent radio circle selects Dark; widget surface changes and the
selection survives reopen. `WidgetAppearanceControl` attaches its click action
only to RadioButton, leaving the neighbouring Text inert. No state loss observed.
Small usability issue, not a release blocker; no change made during this stage.
Fixed on resumed stage: one accessible selectable radio row handles both label
and circle. Actual Samsung Light/Dark label taps change appearance and persist.

### RC-06: widget configuration completion returns to prior app screen (P2)

With a MyVault activity already in its task, Note widget settings -> Done returns
to that prior note rather than the launcher. Qur'an widget Search -> Search
similarly returns to the existing Course screen; Home shows correctly filtered
widget results. Settings and search state are retained. Workaround: press Home.
Observed repeatedly on the real launcher. Task/Activity return behaviour needs a
narrow investigation and physical retest; no navigation changes made yet.
Fixed on resumed stage: only the three widget utility activities have empty task
affinity and are excluded from Recents; widget Surah search explicitly starts a
new task. MainActivity, Share and exact-content intents are unchanged. Samsung
Note settings Done/system Back and Surah Search now return to the launcher;
`Shams` produces exactly the expected 91 result in the existing widget picker.

### RC-07: Note widget settings Back under status bar (P2)

Actual screenshot shows the title and Back arrow overlapping Samsung status
content. Tapping the visible Back target did not close settings; system Back
worked. Added status-bar padding to this settings header and safe drawing padding
to the compact Quick Note appearance screen. Final signed Samsung build retest:
header is clear of status content and tapping Back returns to the launcher.

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
- `b09e5a0`: PDF position stage, committed and pushed to the correct branch.
  Installed signed release SHA-256
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
- 18:04-18:14 AEST: actual launcher Note Viewer selected the disposable Study
  Quick-note, showed its body, opened that exact editor, and refreshed after
  WIDGET-REFRESH1909. Text size 3 survived settings reopen. Dark appearance worked
  using its radio control; independent Quick Note remained light. Changed Note
  Viewer selection via searchable picker to the Course fixture: exact editor
  opened and Back returned to `RC-20260909-Course`. Screenshots inspected in light
  and dark. No clipped short body; long-body widget scrolling still pending.
- Qur'an widget added from actual picker. Search `91` yielded exactly Ash-Shams;
  selection returned reader mode. Translation on, Tajweed on, size 4 and dark
  surface visibly render. Tapping ayah 91:1 opens app Ash-Shams, Ayah 1 of 15.
  Contextual Listen/Tafsir/Reflect toolbar observed. Tafsir acceptance not reached:
  the toolbar was absent by the next delayed harness tap, then the device
  disconnected before a combined action retry. Do not count that as a Tafsir pass
  or a demonstrated app defect. Screenshots remain local under
  `artifacts/rc-torture-20260909/` and are not committed.
- 18:14 AEST: ADB reports serial RFCY70CMWZR unavailable; explicit device listing
  is empty. Mandatory physical acceptance cannot continue. App lock is still
  Off; no biometric/security settings have been changed. No Drive operation was
  performed, no production data cleared, and no final RC tag created.

## Resumed widget/PDF stage, 18:20-18:45 AEST

- Reconnected Samsung installed bytes matched previous 9737e1d candidate; tracked
  tree clean. RC-05/06 narrow fixes built and installed as signed release 266c484a.
  RC-07 inset correction then built and installed in place as
  `/tmp/myvault-rc-widget-insets-signed.apk`, installed SHA-256
  `f0a4b9e07a9cbc2fbc8ecec4b2ba3cc9a24f485d574ec2a44153f05579b31e12`.
  Signing certificate remains CN=Ali, SHA-256 5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3.
- Final full gates: 349 debug + 349 release unit tests, no failures/skips; lint
  0 errors / 283 warnings; debug and release/R8 builds PASS; diff check required
  before checkpoint. Logs `/tmp/rc-widget-task-gates.log` and
  `/tmp/rc-widget-inset-gates.log`.
- Added two instrumentation checks for merged widget activity task isolation and
  unchanged main activity affinity. Debug runner cannot attach to R8 release:
  it crashed before tests with missing un-obfuscated Kotlin Intrinsics. This is
  test-harness incompatibility, not counted as an app workflow crash or a pass.
  Both tests subsequently PASS on the existing API 36.1 emulator with matched
  debug app/test APKs. Samsung stayed on signed release throughout physical tests.
  Emulator was shut down after these checks; no Samsung app data clear/uninstall.
- Actual Samsung Note settings label, Done, system Back, on-screen Back, and
  Qur'an widget search-return fixes retested as detailed in RC-05/06/07. Widget
  note selection, manual dark mode and text-size level survived app updates.
- Tafsir 91:1 source switching loaded Ibn Kathir (9,425 text characters), Tabari
  (759), Qurtubi (1,224); Mukhtasar was selectable but its short body was not
  measured by the >150-character probe. Sheet survives Home/manual reopen.
  A naive error-keyword probe matched the word failed inside actual Tafsir prose;
  that is not an application failure. No content/translation assets changed.
- Light, OLED and Dark Dashboard screenshots inspected. Original Dark restored.
  Four Continue cards remain readable; no content clipping. Light status icons
  have weak contrast against the pale app surface (nonblocking existing polish).
- Memorise continuous Al-A'laa opens full 19-ayah view. Declined microphone prompt
  with Not now; no recording made. Scrolled to 87:19; Home/manual reopen preserves
  full-Surah view and bottom position. Counts remain 64 memorised / 21 in progress.
- Search opened the exact disposable Course note. Library search found the new
  PDF and opened it. Initial asynchronous No results settled into the correct
  result; not recorded as a search failure.
- Disposable PDF: one-shot rectangle creates exactly one highlight, next drag
  scrolls normally. Raster preview appears in Activity; source jump goes to page
  1; highlight survives Back/reopen. Native selected-text Note saves marker above.
  All filter shows its body; four upward flings leave sheet expanded. These checks
  do not replace large-document or real annotation deletion/restore acceptance.
- Widget fixes/log checkpoint `a9d6ffa` pushed to `frozen-design-master-port` after
  successful diff check. Current installed release is f0a4b9e0 as recorded above.
- App lock gate: original preference Off, timer 1 hour. Enabled the existing
  Security lock through its normal authentication flow, then temporarily selected
  the 30-second timer. After Home and 32 seconds, tapping the Course Note widget
  reached the real locked overlay. On the next live inspection after user
  authentication, the exact `RC-20260909-Course-note` editor and its expected
  body were visible, rather than Dashboard. Screenshot:
  `note-widget-unlocked-exact-course.png`. Locked Note widget continuation PASS.
- Returned Home, waited another 32 seconds, then tapped the actual Quick Note
  widget. The old Course editor remained behind the locked overlay; no new-note
  editor appeared before authentication. The overlay said Authentication cancelled.
  Tapped Unlock once to display the real Samsung fingerprint / Use PIN prompt.
  Following the user's physical unlock, the actual Untitled note editor appeared
  directly, with no location picker or Dashboard detour. Renamed this disposable
  note `RC-20260909-Locked-quick-note` and entered
  `Created after real Samsung authentication. LOCKED-QUICK1909.`
  Screenshot `quick-note-after-real-unlock.png` records the initial editor.
  Locked Quick Note editor continuation PASS; duplicate prevention and independent
  persisted-count verification remain PENDING. No credential was entered or
  authentication bypassed.
- User needed to leave for work. Restored Auto-lock timer to 1 hour and Security
  lock to Off through the normal Settings UI. Fresh accessibility inspection
  confirmed timer text `1 hour` and the actual switch checked=false. Screenshot
  `security-original-settings-restored.png`. Told user the Samsung can be
  unplugged. No further device interactions planned until it is available again.

## Resume checklist

Verify Samsung RFCY70CMWZR and installed bytes still match f0a4b9e0.
Security preferences are restored to their original Off / 1 hour. Verify the
saved locked-Quick-Note fixture, exactly one new Study-root note, and remaining
locked widget destinations/actions when the Samsung is available again. Never
request the actual PIN in conversation or simulate authentication success.
Continue actual widget resize/long scroll/
multiple-instance and locked pending destinations. Continue every outstanding
matrix row, especially annotation mutations/bounce, large PDF, Memorise, offline,
audio, Share re-delivery, and final fixture consistency. Builds passing does not
replace these gates. Retest any subsequent fix physically before RC acceptance.

## Final gate

Not reached. All PENDING rows require evidence before final RC classification.
