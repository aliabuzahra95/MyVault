# Quran Widget and Audio Project

## Baseline - 2026-09-05

- Repository: MyVault Complete Before Tutor; branch `frozen-design-master-port`.
- HEAD: `31f3c80f96db81395bd9ac69bc11025bee8c8262`.
- Remote: `git@github.com:aliabuzahra95/MyVault.git`.
- Tracked tree was clean. Existing untracked screenshots and signing sidecars left alone.
- Recovery tag `recovery-quran-widget-audio-20260905-131411` pushed; remote peeled tag matches HEAD.
- JBR 21 baseline full unit suite and debug assembly passed.
- Samsung unavailable. API 36.1 emulator available; no phone data cleared or app uninstalled.

## Checkpoint A - Navigation and RTL

- Reader preferences could complete after an explicit widget request and start a competing saved-position load. The startup gate retains the latest explicit target until preferences initialize. Cancelled loads cannot publish stale results.
- Existing bookmark/exact-ayah route reused. Activity launch extras are consumed after capturing the pending target, which survives unlock. Normal launch stays Dashboard.
- Reader collection template has an explicit action and per-widget identity; row payload uses stable Surah/ayah, not position.
- Arabic direction remains RTL with absolute right alignment; translation is explicitly LTR/left. These are layout attributes, not unsupported RemoteViews reflection calls.
- Executed three startup-gate unit tests and the full unit suite; debug and Android test APKs assembled.
- Executed native RemoteViews rendering test on emulator across four sizes and both manual appearances. One test passed. The test caught and eliminated an unsupported setTextDirection RemoteViews call before checkpoint.
- Real launcher clicks, lock continuation, screenshots and Samsung acceptance still pending; this is not a physical acceptance claim.

## Next

Verify recording/timing contract, extend existing MediaPlayer with a shared background owner, then wire reader and widget controls. Backup/Drive/canonical content remain protected.

## Checkpoint B/C - Full-track contract and shared playback

- Existing proxy returned Afasy Murattal full recordings; verified separate chapter-reciter mapping. Ordered timing fixtures for 1/2/4/9 are covered by executed unit tests. Personal-use source terms and unverified wider/audible coverage are recorded in the contract.
- Added a bounded, recording-identity-specific full-track cache; existing single-ayah download implementation retained.
- Existing MediaPlayer retained; preparation cancellation, full-track boundary policy, actual-position sampling, audio focus, non-sticky foreground service and platform MediaSession added. Reader lifetime no longer owns/release-stops the player.
- Added compact listening-mode and opt-in follow controls; selected ayah and saved reading state remain independent.
- Widget collection now separates OPEN and PLAY through one explicit per-widget broadcast template. Header play/continue/stop and independent row Play controls wired; meaningful partial updates contain no scroll commands.
- Full unit suite and debug/test APK assembly passed. Nine new timing/policy tests passed. Updated one older source-location assertion to follow cancellation ownership from ViewModel into the shared controller.
- Two executed emulator real-MediaPlayer tests passed in 33.792s: same player/recording through Continue and boundaries, manual pause/mode changes, seek/speed, single-ayah stopping, background continuation, final completion, unsupported-reciter error and latest-request cancellation.
- Captured actual emulator PCM output through the emulator's local gRPC audio API. This capture includes explicit test pauses/seeks and is not a gap-free listening acceptance recording. No human audible judgment claimed.
- Launcher pin test passed but initial screenshot showed an empty page. Investigating actual rendering before accepting widget controls. Full lint/R8 validation running.

## Requested reciter expansion and emulator verification

- User explicitly requested Shatri, both Abdul Basit styles, Tablawi, Shuraym, Dossari, Ghamdi and confirmed Fares Abbad (not Fouad). Existing choices retained.
- Added an explicit provider registry and MP3Quran adapter. Reserved app IDs for the three new names preserve the existing integer preference; no backup/schema changes.
- Widget playback reads the same existing selected-reciter preference as the app. The real widget Play test confirmed Fares Abbad starts without opening the Activity.
- Foundation chapter ID 11 is al-Qasim, not app ayah-reciter 11 Tablawi. Tablawi uses its own MP3Quran read 106. No namespace guessing.
- Found overlapping Foundation timestamps for Shuraym Al-Fatihah and Basit Murattal Al-Baqara. Switched each to the matching MP3Quran recording/timing pair (31/53), retaining strict rejection rather than rewriting timestamps.
- Eight requested full recordings passed actual MediaPlayer preparation, unique identity, exact-ayah start and advancing position. Long Al-Baqara timing data checked separately. Final-source rerun is tracked in acceptance.
- Added MediaPlayer partial wake lock and documented the platform media-session notification permission exemption. No microphone permission added.
- Real widget OPEN dispatch passed after explicitly binding reading surfaces independently from the Play child. Earlier launcher-page visibility and persistence-based test assertions were corrected; screenshots show An-Nisaa ayah 5, not saved An-Nahl 16:1.
- Two actual launcher tests passed (65.221s); screenshots inspected. Samsung remains unavailable. Backup/Drive and protected data remain untouched.
- Final verification: 297 unit tests passed; four playback tests passed with final reciter mappings (91.511s); native widget rendering passed across four sizes and manual Light/Dark (10.013s). Screen-off full-Surah state progression completed without a player replacement or boundary pause.
- Extra 2:255 launcher case did not reach its tap assertion because the existing smooth-scroll setup missed the visibility deadline. Retained as an explicit open acceptance item, not a claimed navigation pass.
- JBR 21 full unit/lint/debug/release-R8 command passed in 5m 23s. Lint has zero errors, 305 warnings and one hint. No signed APK upload or Drive mutation was requested/performed in this pass. Physical/audible acceptance remains separate.
