# Quran Widget and Audio Acceptance

## Identity and scope

- Date: 2026-09-05.
- Branch: `frozen-design-master-port`; baseline `31f3c80f96db81395bd9ac69bc11025bee8c8262`.
- Recovery: `recovery-quran-widget-audio-20260905-131411` (remote peeled tag verified against baseline).
- Checkpoints: `f835416` navigation/RTL; `95f02ad` shared full-track playback and controls.
- Protected backup/Drive, canonical assets, Room, Web, Memorise and Note/PDF narration were not changed.
- Samsung was not connected. No production phone installation, content reset, restore or Drive mutation was performed.

## Verified evidence so far

| Area | Executed evidence | Limit |
| --- | --- | --- |
| Startup target | Three pure startup-gate tests: explicit target wins hydration; latest queued target wins; ordinary saved fallback | Not equivalent to launcher or unlock acceptance |
| RTL | Native RemoteViews applied/measured on API 36.1, four layout buckets and two manual appearances; real Arabic paragraph direction -1, right gravity, font padding; English LTR | Samsung launcher and size extremes still require physical review |
| Actual widget rendering | API 36.1 launcher screenshot `widget-page2.png`: An-Nisaa Arabic, translation and header control rendered | Initial blank screenshot was the other launcher page; tap interaction investigated separately |
| Actual OPEN / PLAY | Two real launcher-touch tests passed in 65.221s: 4:5 opens An-Nisaa ayah 5 despite saved 16:1; widget Play uses selected Fares Abbad and stays on launcher | Earlier test incorrectly waited on persistence rather than visible navigation; final assertion checks reader title/ayah, not a direct intent. Samsung unlock remains untested |
| Requested reciters | Final live MediaPlayer rerun passed for all eight choices (seven reciters, both Basit styles), including replacement Basit Murattal source: unique recording per choice, requested 1:2 and advancing media position | Structural/real-player check, not human identification or listening to all 114 Surahs |
| Screen off | Full Al-Fatihah ended naturally with all seven ayah states observed, same MediaPlayer instance and no boundary pause while screen off; four playback tests passed in 91.511s | Does not establish Samsung OEM battery-policy behavior or human audible alignment |
| Long-Surah data | Al-Baqara: 286 ordered non-overlapping entries for enabled Basit styles, Shatri, Shuraym, Tablawi, Dossari, Ghamdi and Fares | Metadata check, not full long-track listening |
| Timing data | Nine executed unit tests, real API-derived fixtures for 1/2/4/9, 598 complete canonical ayah intervals | Structural validity is not audible alignment verification |
| Full-track engine | Real MediaPlayer emulator test retained exact player instance through verse progression, mode changes, seek, speed and backgrounding | No claim of Samsung timing accuracy |
| This ayah | Real test paused at 1:5 end 27,660ms; Continue advanced to 1:6 without player replacement | Controller sampling is every 50ms, not sample-accurate audio editing |
| Requests/errors | Unsupported reciter rejected explicitly; rapid 2:255 then 1:4 request left only latest target active | Long Al-Baqara playback is separate from cancelled-download test |
| Audio capture | Actual emulator PCM captured to WAV, 28.908s, during a pause/seek test | Not an uninterrupted gap-test recording and not a human listening assessment |

## Required remaining acceptance

- Additional 2:255 and warm/cold/unlock combinations beyond executed 4:5 launcher test. The 2:255 launcher test failed its setup visibility deadline: existing smooth collection scrolling had only reached earlier verses. It never reached its tap assertion; do not report a 2:255 launcher pass.
- Source-versus-output audible gap comparison; screen-off state/player continuity passed but is not a waveform/listening comparison.
- Samsung: exact widget taps, unlock continuation, two widget instances, header/row controls, screen-off/media controls, resizing and visual approval.
- Audible timing alignment at beginning/middle/end, especially long Surahs and At-Tawbah; unsupported reciters must remain explicit.

## Intentional limits

Enabled mappings include the seven requested reciters, both Basit styles, and the validated existing Afasy/Husary/Minshawi Murattal mappings. See the source matrix in `QURAN_CONTINUOUS_AUDIO_CONTRACT.md`. Each requested chapter must pass full timing/count/duration validation; 114 API resources does not mean 114 individually listened-to recordings. The widget follows the app-selected reciter; there is no independent per-widget reciter preference.

The requested full recording is downloaded before playback. Initial preparation can be substantial for long Surahs; cached playback is independent of network. Existing single-ayah downloads are not interchangeable with the new full-track cache. The cache is evictable and device-local, not an offline-library guarantee.

Recording identity includes the API identity, exact URL and complete timing map. The upstream API does not provide a cryptographic audio checksum; HTTP complete length and decoded duration are checked. A provider replacing bytes at the same URL with a different same-length/duration performance is not cryptographically detectable by this contract.

QuranicAudio's published permission covers free personal use, not unrestricted commercial distribution. This work does not establish commercial licensing rights. See `QURAN_CONTINUOUS_AUDIO_CONTRACT.md` for sources.

## Acceptance decision

RECITER EXPANSION VERIFIED ON EMULATOR / FULL PHYSICAL ACCEPTANCE INCOMPLETE. All requested choices passed real playback and the Fares widget trigger passed. Host tests and emulator observations must not be presented as final Samsung acceptance. The separate 2:255 launcher scroll setup and physical/audible gates above remain open.

## Screenshot evidence

Inspected local API 36.1 captures:

- `/tmp/myvault-final-widget-evidence/widget-before-4-5.png`
- `/tmp/myvault-final-widget-evidence/reader-after-4-5.png`
- `/tmp/myvault-final-widget-evidence/widget-fares-playing.png`

These show the real pinned launcher widget, exact reader destination and active launcher playback. They are not Samsung screenshots.

## Final executed checks

- Full unit suite: 297 tests, zero failures or skipped tests; includes six reciter registry/parser regressions.
- Final playback instrumentation: four tests passed, 91.511s (`/tmp/myvault-final-playback-runtime.log`). Covers all eight requested choices with final source mappings, continuous screen-off playback, same-player mode/seek/background transitions, unsupported source and cancellation behavior.
- Real launcher interaction: two tests passed, 65.221s (`/tmp/myvault-widget-real-click6.log`).
- Native RemoteViews rendering: one test passed, 10.013s (`/tmp/myvault-final-widget-rendering.log`), all four buckets and manual Light/Dark.
- Extra 2:255 launcher test: failed target-row visibility setup; no tap result claimed (`/tmp/myvault-widget-2255-runtime.log`).
- JBR 21 final full unit/lint/debug/release-R8 command passed in 5m 23s (`/tmp/myvault-reciter-final-build.log`). Instrumentation APK assembly also passed. Lint: zero errors, 305 warnings and one hint; not a warning-free claim.
- `git diff --check` passed. No Samsung was connected; no Drive objects were touched.
