# Pre-Stage 8 Memorise Production Audit

Status: **FROZEN AMENDMENT IMPLEMENTED AND VERIFIED - AWAITING STAGE 8 APPROVAL**

Scope: Production Memorise functionality compared with Frozen Design Master references 35-42 and the approved Stage 7 Qur'an-to-Memorise handoff. This is a documentation-only audit. No Stage 1-7 application code, canonical Qur'an content, speech engine, persistence model, or backup representation was changed.

## A. Checkpoint And References

- Production branch: `frozen-design-master-port`
- Approved Stage 7 commit: `5bb1460f4cd6f6d82cd001c0b8e4d08653e7a892`
- Recoverable pushed tag: `stage-7-approved`
- Frozen screenshots inspected: `35-memorise-overview.png` through `42-memorise-oled.png`
- Frozen documents inspected: `DESIGN_SPEC.md`, `COMPONENT_INVENTORY.md`, `ANDROID_COMPONENT_MAP.md`, `MOTION_SPEC.md`, `DESIGN_MASTER_README.md`, and the final Qur'an amendment/handoff material
- Production sources inspected: `MemoriseShellScreen`, `MemoriseViewModel`, Memorise models, conceal/repeat state, recorder, speech providers, analysis/scoring/alignment engines, attempt logs, whole-Surah test engine/sheets, navigation, preferences, and backup mapping

The Frozen references control presentation. Production Qur'an, recording, transcription, analysis, scoring, persistence, and backup code controls behaviour and truth.

## B. Complete Production Capability Inventory

Eighty production capabilities or compatibility behaviours were identified.

| # | Capability | Current entry / surface | Production owner | Persistence / backup | Frozen coverage / proposed placement | Status |
|---|---|---|---|---|---|---|
| 1 | Memorise destination | Explorer / Islamic workspace route | `VaultNavHost` | Navigation state | Frozen overview | ALREADY FROZEN |
| 2 | Workspace-aware shell | Current Islamic workspace | Navigation/workspace state | Existing workspace state | Shared Explorer/header | APPROVED SHARED COMPONENT |
| 3 | Memorised ayah count | Overview header/cards | `MemorizationOverview` | Derived from backed-up records | Compact overview metadata/stat | ALREADY FROZEN |
| 4 | In-progress Surah count | Overview cards | `MemorizationOverview` | Derived | Compact overview metadata/stat | ALREADY FROZEN |
| 5 | Revision count | Overview cards | `MemorizationOverview` | Derived | Five-column stat strip | ALREADY FROZEN |
| 6 | Incorrect count | Overview cards | Status aggregation | Derived from records/attempts | Five-column stat strip | ALREADY FROZEN |
| 7 | Difficult count | Overview cards | Manual weak/difficult state | Backed up | Five-column stat strip | ALREADY FROZEN |
| 8 | Needs-review count | Legacy filters/cards | Status aggregation | Derived | Frozen combines revision-oriented presentation | NEEDS DESIGN DECISION |
| 9 | Memorised Surah count | Legacy overview | Overview model | Derived | Not separately shown Frozen | ENGINE-ONLY |
| 10 | Dashboard filters | Legacy filter chips | `MemorizationDashboardGroup` | Transient | Frozen has no filter controls | NEEDS DESIGN DECISION |
| 11 | Continue target | Legacy focus card | `continueItem` derivation | Derived | Frozen Continue card | ALREADY FROZEN |
| 12 | Continue percentage | Legacy counts | Surah dashboard data | Truthfully derivable | Frozen Continue percentage | APPROVED SHARED COMPONENT |
| 13 | Continue exact ayah | Legacy focus card | Dashboard derivation | Derived | Frozen ayah range/target | ALREADY FROZEN |
| 14 | Surah progress rows | Legacy dashboard rows | Surah dashboard model | Derived | Frozen Progress section | ALREADY FROZEN |
| 15 | Revision rows | Legacy status rows | Attempt/record aggregation | Derived | Frozen Revision section | ALREADY FROZEN |
| 16 | Revision scheduling/due date | No production scheduler | None | None | Frozen `Today` / next-review language | STOP-AND-ASK |
| 17 | Empty overview | No records | `MemoriseShellScreen` | Derived | Frozen empty state not shown | NEEDS DESIGN DECISION |
| 18 | Start Memorising entry | Legacy overview action | Shell/ViewModel | UI only | Frozen overview has no start/new-target action | STOP-AND-ASK |
| 19 | Surah selection | Start sheet | ViewModel/catalog | Transient | Missing Frozen placement | STOP-AND-ASK |
| 20 | Ayah selection | Start sheet | ViewModel/catalog | Transient | Missing Frozen placement | STOP-AND-ASK |
| 21 | Start selected ayah | Start sheet | `startSelectedAyah` | Creates backed-up record | Should enter Frozen session | NEEDS DESIGN DECISION |
| 22 | Mark whole Surah memorised | Start sheet | `markEntireSelectedSurahMemorized` | Backed-up records | No Frozen placement | STOP-AND-ASK |
| 23 | Qur'an selected-ayah handoff | Reader More -> Memorise | `pendingMemoriseVerseKey` | Saveable/transient | Approved exact-verse handoff | ALREADY FROZEN |
| 24 | Handoff destination behaviour | Currently opens overview and preselects hidden target | `VaultNavHost`/ViewModel | Transient | Frozen likely requires direct session | STOP-AND-ASK |
| 25 | Explorer Memorise entry | Explorer row | Shared navigation | None | Frozen overview | ALREADY FROZEN |
| 26 | Open overview item | Currently opens normal Qur'an reader | Shell callback/navigation | Reading position may persist | Frozen expects Memorise session | STOP-AND-ASK |
| 27 | Canonical target ayah | Session data | Qur'an repositories | Bundled canonical corpus | Frozen session canvas | ENGINE-ONLY |
| 28 | Canonical Arabic word sequence | Session rendering | Word-enriched ayah model | Bundled word IDs/metadata | Frozen Arabic session text | ENGINE-ONLY |
| 29 | Translation | Session reference | Qur'an translation repository | Existing reader preference | Frozen translation line | ALREADY FROZEN |
| 30 | Hide Off | Legacy ayah control | Reader UI state | Transient/not backed up | Frozen Hide Off | ALREADY FROZEN |
| 31 | Hide Half | Legacy ayah control | `MemorizationConcealAmount.Half` | Transient | Frozen Hide 1/2 | ALREADY FROZEN |
| 32 | Hide All | Legacy ayah control | `MemorizationConcealAmount.Full` | Transient | Frozen Hide All | ALREADY FROZEN |
| 33 | Quarter conceal engine | No current visible control | `MemorizationConcealAmount.Quarter` | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 34 | Three-quarter conceal engine | No current visible control | `MemorizationConcealAmount.ThreeQuarters` | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 35 | Concealment algorithm | Legacy ayah rendering | `buildMemorizationDisplayText` | None | Frozen positional masks | APPROVED SHARED COMPONENT |
| 36 | Canonical repeat 3x | No current reachable Stage 7 control | Reader audio/repeat state | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 37 | Canonical repeat 5x | No current reachable Stage 7 control | Reader audio/repeat state | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 38 | Canonical repeat 10x | No current reachable Stage 7 control | Reader audio/repeat state | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 39 | Repeat until stopped | No current reachable Stage 7 control | Reader audio/repeat state | Transient | Not represented Frozen | ENGINE-ONLY / DORMANT |
| 40 | Ready-to-listen state | Legacy AI Listen sheet | Sheet/recorder state | Transient | Frozen Ready + Start | ALREADY FROZEN |
| 41 | Microphone permission request | AI Listen entry | Android permission launcher | Device permission | Frozen permission state absent | STOP-AND-ASK |
| 42 | Permission denial/permanent denial | Legacy status message only | Android permission result | Device permission | Missing Frozen state/action | STOP-AND-ASK |
| 43 | Start recording | Legacy auto-start after permission | `QuranMemorizationRecorder` | Temporary WAV | Frozen explicit Start | NEEDS DESIGN DECISION |
| 44 | 16 kHz mono PCM WAV capture | Recording engine | Recorder | Cache file only, not backup | Engine beneath Frozen recording state | ENGINE-ONLY |
| 45 | Recording timer | AI Listen sheet | Recorder/session state | Transient | Frozen timer | ALREADY FROZEN |
| 46 | Pause recording | Recording controls | Recorder | Transient | Frozen Pause | ALREADY FROZEN |
| 47 | Resume recording | Paused controls | Recorder | Transient | Frozen Resume | ALREADY FROZEN |
| 48 | Stop recording | Recording controls | Recorder | Produces cache WAV | Frozen Stop | ALREADY FROZEN |
| 49 | Release on target/sheet disposal | Lifecycle effect | Recorder | Runtime cleanup | No visible UI | ENGINE-ONLY |
| 50 | Recorded-recitation playback | Finished legacy state | Recorder/audio player | Cache file | Missing Frozen placement | STOP-AND-ASK |
| 51 | Re-record captured attempt | Finished legacy state | Recorder state | Replaces cache recording | Missing Frozen placement | STOP-AND-ASK |
| 52 | Captured/finished state | Legacy immediately analyzes after Stop | Sheet state machine | Transient | Frozen has separate Finished/Analyze | STOP-AND-ASK |
| 53 | Google Chirp provider | AI Listen provider selector | Speech provider | Config, not attempt state | Frozen provider choice absent | STOP-AND-ASK |
| 54 | OpenAI Transcribe provider | AI Listen provider selector | Speech provider | Config, not attempt state | Frozen provider choice absent | STOP-AND-ASK |
| 55 | Provider selection | Legacy AI sheet | Sheet-local state | Not backed up | Missing Frozen placement | STOP-AND-ASK |
| 56 | Provider model/locale request | Transcription engine | Provider implementation | Build/runtime configuration | No visible UI required | ENGINE-ONLY |
| 57 | Network transcription | After recording | Speech repository/provider | Attempt records provider/model | Frozen Analyzing | ENGINE-ONLY |
| 58 | Transcription timeout/auth/quota errors | Legacy status/result | Provider error mapping | Saved failure metadata where attempted | Frozen error/retry state absent | STOP-AND-ASK |
| 59 | Empty transcript handling | Analysis pipeline | Attempt factory/provider | Failure attempt may persist | Frozen error state absent | STOP-AND-ASK |
| 60 | Retry analysis | Legacy failed/finished state | Sheet action | Reuses recording | Frozen placement absent | STOP-AND-ASK |
| 61 | Re-analyze with another provider | Legacy provider change + Analyze | Sheet action/providers | New attempt metadata | Frozen placement absent | STOP-AND-ASK |
| 62 | Arabic transcript normalization | Analysis engine | Comparison normalizer | Derived | No visible UI needed | ENGINE-ONLY |
| 63 | Known spoken/imlaei variants | Analysis engine | Comparison normalizer | Derived | No visible UI needed | ENGINE-ONLY |
| 64 | Ordered word alignment | Analysis engine | Dynamic-programming aligner | Derived | Feeds Frozen result marks | ENGINE-ONLY |
| 65 | Correct word state | Results | Analysis engine | Saved matched IDs/counts | Frozen correct underline | ALREADY FROZEN |
| 66 | Missing word state | Results | Analysis engine | Saved missing IDs/counts | Frozen missed dash | ALREADY FROZEN |
| 67 | Extra word state | Results details | Analysis engine | Saved extra words/counts | Frozen mapping unclear | STOP-AND-ASK |
| 68 | Repeated word state | Results details | Analysis engine | Saved repeated words/counts | Frozen mapping unclear | STOP-AND-ASK |
| 69 | Unknown/low-confidence state | Results details | Analysis engine | Saved unknown count | Frozen mapping unclear | STOP-AND-ASK |
| 70 | Transcript/confidence/timing details | Legacy result | Provider/analysis | Partial saved metadata | Frozen omits detail surface | STOP-AND-ASK |
| 71 | Deterministic score | Result | Score engine | Saved 0-100 | Frozen compact score/result | ALREADY FROZEN |
| 72 | Grade | Result | Excellent/Good/Needs Review/Repeat | Saved | Frozen summary can bind real grade | ALREADY FROZEN |
| 73 | Status mapping | Dashboard/result | Passed/Needs Review/Incorrect/Unknown | Saved | Frozen result + overview status | APPROVED SHARED COMPONENT |
| 74 | Retry session | Results action | Session state | New attempt | Frozen Retry | ALREADY FROZEN |
| 75 | Next ayah | Results action not currently wired as a session | Qur'an catalog/navigation | Transient | Frozen Next ayah | STOP-AND-ASK |
| 76 | Attempt history, latest result, restore-on-open | Legacy sheet/dashboard | Preferences/history | Last 50 ayah attempts; backed up | Frozen history/drilldown absent | STOP-AND-ASK |
| 77 | Whole-Surah test modes and limits | Dormant `QuranSurahTestSheet` | Surah test engine | Last 50 attempts; backed up | Frozen single-ayah session only | STOP-AND-ASK |
| 78 | Whole-Surah per-ayah results | Dormant test result | Surah test engine | Backed up summary/results | No Frozen placement | STOP-AND-ASK |
| 79 | Manual memorised/revision/incorrect/difficult controls | Legacy overview/ayah controls | ViewModels/preferences | Backed up records | Frozen overview has no action placement | STOP-AND-ASK |
| 80 | Session return/process restoration | No dedicated session route today | Navigation/ViewModel | Target/session mainly transient | Frozen Back/end behaviour not specified | STOP-AND-ASK |

## C. Frozen Coverage

The Frozen references map cleanly to:

- compact overview title/metadata;
- truthful Continue card based on an actual target;
- truthful memorised, in-progress, revision, incorrect, and difficult counts;
- hierarchy-free Revision and Progress rows;
- canonical single-ayah session canvas;
- Hide Off, 1/2, and All;
- Ready, Recording, Paused, Analyzing, and Results presentation;
- Pause, Resume, Stop, Retry, and Next visual language;
- correct/missed result markings where production states map directly;
- Light, Dark, and OLED theme tokens;
- Stage 7 exact-ayah entry intent.

The prototype values are representative. Production counts, Surah names, ayah ranges, percentages, translations, timers, scores, and result words must always come from real production state.

## D. Real Engines And Ownership

- `MemoriseViewModel` owns overview selection and manual memorisation records.
- `QuranReaderViewModel` currently owns conceal/repeat state and records AI/whole-Surah attempts.
- `QuranMemorizationRecorder` owns microphone capture, pause/resume, WAV finalization, and recorded-audio playback.
- speech providers own Google Chirp and OpenAI transcription requests and their error mapping.
- `QuranMemorizationComparisonNormalizer`, `QuranMemorizationAnalysisEngine`, and `QuranMemorizationScoreEngine` own deterministic comparison, alignment, word states, scoring, and grade mapping.
- `QuranSurahMemorizationTestEngine` owns whole-Surah segmentation and scoring.
- `VaultPreferences` owns manual records and the latest 50 ayah and whole-Surah attempts; backup/restore already carries these settings.
- temporary WAV files, concealment, repeat mode, selected target, recording state, and live session state are device-local/transient.

No Room/schema or backup change is required merely to port the approved overview and one-ayah session. A requirement to persist an active session across process death would be a new compatibility decision.

## E. Entry, Session, And Navigation Findings

Current production does not have a dedicated Memorise session destination. The overview's Continue/items open the normal Qur'an reader. Starting an ayah records it and closes the start sheet but remains on the overview. The Stage 7 handoff selects a target internally, then opens the overview; it does not visibly open that exact target in the Frozen session.

These routes cannot be wired faithfully without approval of:

1. whether Stage 7 `Memorise from here` opens the exact ayah session directly;
2. how users start a new target from an empty/non-empty overview;
3. whether Continue and Progress rows enter the exact Frozen session;
4. Back behaviour from a session entered from Explorer, overview, or Qur'an;
5. Next behaviour at the end of a Surah;
6. whether an active target/session must survive process death.

## F. Overview Truth And Progress

Production can truthfully provide ayah counts, Surah counts, status counts, and a Surah completion percentage derived from memorised ayahs divided by canonical Surah ayah count. Production does not contain a spaced-repetition schedule or due-date engine.

Therefore Frozen labels such as `Today`, `Nothing due today`, or a next-review date must not be fabricated. The Frozen amendment must define a truthful Revision empty/non-empty state based on existing status and review timestamps, or explicitly authorize a new scheduling feature in a future stage.

## G. Recording And Permission States

Frozen references cover the happy path. Production additionally requires visible treatment for:

- microphone permission request;
- permission denied and permanently denied;
- recorder initialization/capture failure;
- missing/empty recording;
- network unavailable/timeout;
- provider authentication/configuration/quota/rate errors;
- empty transcript;
- retry analysis;
- optional playback and re-record of the captured recitation.

The current sheet auto-starts recording after permission, while Frozen shows Ready with an explicit Start. Whether Stage 8 changes only presentation timing or retains auto-start must be frozen before implementation.

## H. Provider And Result Mapping

Google Chirp and OpenAI Transcribe are both active production transcription engines. The old provider choice is sheet-local and not backed up. Frozen references do not place provider selection.

Frozen result markings directly cover `CORRECT` and `MISSING`. Production also distinguishes `EXTRA`, `REPEATED`, and `UNKNOWN`/low-confidence words and can expose transcript, confidence, timing, diagnostic, and score details. These states cannot be collapsed or hidden without an approved mapping.

## I. Whole-Surah Test

Production has a separate whole-Surah test engine with:

- Continue Revision and Full Surah Test modes;
- a supported ceiling of 30 ayahs and 260 canonical words;
- one continuous recording/transcription;
- per-ayah analysis, score, status, and result detail;
- latest attempt restoration;
- backed-up attempt history.

The implementation sheet is currently dormant/unreachable after the Stage 7 reader port, but its engine and user data remain production capabilities. Frozen references 35-42 specify only a single-ayah session and provide no placement for the mode chooser, unsupported-size state, or whole-Surah result review. This requires an explicit Frozen amendment or an explicit decision to keep the engine dormant during Stage 8.

## J. Manual Status And Attempt History

Production permits marking/toggling Memorising, Memorised, Revision, Needs revision, Incorrect, Difficult, and review completion. Those states affect overview sorting/counts and are backed up. Frozen rows do not show context actions for these controls.

Production also preserves the latest 50 ayah attempts and 50 whole-Surah attempts. Frozen overview/results do not specify attempt-history access or historic-result drilldown. Stage 8 cannot remove the data, but its visible destination requires approval.

## K. Proposed Action Hierarchy For Approval

This is a proposal only; it is not authorized implementation.

**MEMORISE OVERVIEW**

- Header and five truthful statistics: Frozen direct mapping.
- Continue: opens the real current target's one-ayah session.
- Revision and Progress rows: open the selected one-ayah session.
- New target / empty-state entry: requires an approved compact action/sheet placement.
- Manual record actions and attempt history: require approved secondary placement.
- Whole-Surah Test: requires an approved secondary placement or explicit dormant decision.

**SESSION**

- Back: requires route-aware approved semantics.
- Hide Off / 1/2 / All: Frozen direct mapping.
- Ready / Start, Recording / Pause / Stop, Paused / Resume / Stop: Frozen direct mapping.
- Finished, recorded playback, re-record, provider choice, and errors: amendment required.
- Analyzing: Frozen direct mapping over the existing transcription/analysis engine.
- Results: bind real score/grade and canonical word states; extra/repeated/unknown mapping requires amendment.
- Retry: Frozen direct mapping.
- Next ayah: amendment required for end-of-Surah behaviour.

**SECONDARY SURFACES REQUIRING A DECISION**

- target picker;
- microphone permission/error state;
- provider chooser;
- recording review/playback/re-record;
- transcription failure/retry;
- result details and attempt history;
- manual memorisation status actions;
- whole-Surah test mode/results.

## L. Repeat And Playback Findings

- Canonical ayah repeat modes `3x`, `5x`, `10x`, and `Until Stopped` still exist in the production reader engine but have no reachable Stage 7 or Frozen Stage 8 control. They remain preserved, dormant engine functionality.
- Captured-recitation playback is active production behavior in the legacy AI Listen sheet. It is distinct from canonical reciter audio and repeat mode.
- Frozen Stage 8 does not place captured-recitation playback, re-record, canonical repeat, or stop-repeat controls. Their treatment requires an explicit decision; they must not be conflated.

## M. Themes And Motion

- The overview uses ordinary shared Light, Dark, and OLED theme tokens.
- The session can reuse the approved Qur'an-derived reading surface, including its warm Light canvas and shared Dark/OLED treatment, without changing the Stage 7 reader.
- Frozen motion specifies restrained 150-210 ms state transitions, a recording pulse, no pulse while paused, an analyzing spinner, result crossfade, and short concealment transitions.
- Production currently has no memorisation-specific haptic contract, celebration, streak, XP, badge, level, points, or confetti behavior. None should be invented.
- Motion must remain presentation-only and must not delay recorder, transcription, analysis, or persistence callbacks.

## N. Canonical Data Dependencies

- Session Arabic, translation, Surah/ayah identity, word order, word IDs, and Tajweed metadata must come from the existing production Qur'an repositories and bundled canonical assets.
- Concealment must preserve canonical word/character ordering; scoring must continue to consume the existing normalized canonical word sequence.
- Stage 8 must not copy prototype Arabic, translation, result words, counts, scores, or percentages into production logic.
- No canonical Qur'an change, word-ID remapping, Room migration, speech-provider replacement, recorder replacement, or analysis-engine rewrite is indicated by this audit.

## O. Data And Backup Compatibility

- Do not change canonical Qur'an text, word IDs, normalization, alignment, scoring, or status semantics.
- Do not change `MemorizationRecord`, saved ayah attempt, saved whole-Surah attempt, preference keys, or backup JSON for presentation convenience.
- Keep attempt history limits and existing restore validation unchanged.
- Keep temporary audio device-local and excluded from backup.
- Keep conceal/repeat state transient unless separately authorized.
- Stage 10 must destructively verify Android/Web backup compatibility for the existing memorisation settings.

## P. Missing Frozen Placements

The Frozen references do not currently place:

- empty-state/new-target selection;
- current dashboard filters and manual status actions;
- truthful no-scheduler Revision states;
- whole-Surah testing and its restored result history;
- microphone permission and error recovery;
- speech-provider choice;
- captured-recording review/playback/re-record;
- transcription failure/retry/provider re-analysis;
- extra/repeated/unknown result states and detailed metrics;
- attempt-history access;
- session entry/back/process-restoration and end-of-Surah navigation.

## Q. Explicit Blockers / Required Decisions

Stage 8 has new design blockers. Before implementation, freeze decisions for:

1. overview empty state and target creation/selection;
2. direct Stage 7 exact-ayah handoff and all session entry/back routes;
3. truthful Revision presentation without a scheduling engine;
4. dashboard filter removal or secondary placement;
5. manual status controls and attempt-history access;
6. whole-Surah test placement or explicit dormant treatment;
7. microphone permission, denied, permanently denied, and recorder failure states;
8. explicit Start versus current post-permission auto-start;
9. provider selection placement and persistence policy;
10. captured-recording playback, re-record, and Finished-to-Analyze flow;
11. network/transcription failure and retry states;
12. visual mapping for extra, repeated, and unknown words and optional detail metrics;
13. Next at end of Surah and active-session process restoration.

## R. Stage Decision

**STAGE 8 REQUIRES A FROZEN PROTOTYPE AMENDMENT.**

The production one-ayah engines can support the core Frozen happy path without data-model changes, but the unresolved production states and whole-Surah capability require explicit visual/interaction decisions. Stage 8 implementation must not begin until those decisions are approved and frozen.

## S. Approved Frozen Amendment Implementation

The final Memorise amendment at
`myvault-ui-prototype-frozen-memorise-amendment-final-20260828-150155-AEST`
resolved the audit blockers and is implemented through one production
Memorise overview/session system.

Implemented presentation and routing:

- truthful five-state overview, Continue, Surah progress, Set status, Attempts,
  Attempt Detail, and Test whole Surah;
- dedicated canonical ayah/whole-Surah session with Hide Off, 1/2, and All;
- exact Qur'an Reader `Memorise from here` handoff;
- automatic recording only for that Qur'an handoff, both for existing
  permission and immediately after a successful permission grant;
- Ready, Recording, Paused, Recording complete, captured-audio playback,
  Re-record, Analyzing, Results, Details, transcription/analysis/recording
  failures, empty speech, retry, end-of-Surah, Dark, and OLED states;
- secondary Google Chirp/OpenAI Transcribe provider selection using the
  existing production engines;
- process recreation returns to the persisted Memorise overview and never
  fabricates an active recorder.

Production engine boundaries remain unchanged: canonical Qur'an repositories,
word IDs, Arabic normalization, alignment, scoring, recorder, providers,
attempt factories, preference history limits, entities, and backup mappings.
No Room migration or backup/schema extension was introduced.

## T. Verification Evidence

- JBR 21 `testDebugUnitTest`, `lintDebug`, and `assembleDebug`: PASS.
- Targeted memorisation analysis, scoring, dashboard, attempt-persistence,
  whole-Surah, and OpenAI provider unit suites: PASS.
- Real installed `com.myvault.app` at 412 x 892 logical viewport:
  permission request/grant, auto-record handoff, ordinary non-auto session,
  record, timer, pause, resume, stop, review, playback state, re-record,
  provider selection, retry, Attempts, Attempt Detail, whole-Surah entry,
  process recreation, Back, Light, and OLED exercised.
- Google Chirp reached the configured production service and returned the
  expected recoverable empty-transcript state for emulator silence.
- OpenAI Transcribe completed a real transcription, deterministic analysis,
  persisted attempt, Results, and Details flow.
- Side-by-side and difference evidence is stored under
  `artifacts/stage-8/comparisons` and runtime captures under
  `artifacts/stage-8/runtime` (local verification artifacts, not production
  source).

Full destructive Android/Web backup and restore remains a mandatory Stage 10
regression. Temporary WAV recordings, active recording, concealment, and live
analysis remain device-local and outside backup exactly as before.
