# Pre-Stage 7 Qur'an Production Audit

Status: **AUDIT COMPLETE - STAGE 7 NOT AUTHORIZED**

Scope: Production Qur'an functionality compared with Frozen Design Master references 27-34 and 49. This document records presentation coverage and unresolved placement decisions only. No application code, canonical corpus, Room schema, repository contract, audio engine, or backup representation was changed.

## A. Authoritative References And Checkpoint

- Production branch: `frozen-design-master-port`
- Approved Stage 6 commit: `a1889db64dcbfbef8805b54f505121417f913737`
- Recoverable pushed tag: `stage-6-approved`
- Frozen screenshots: `27-quran-reader.png` through `34-quran-oled.png`, plus `49-quran-inline-reflection.png`
- Frozen documents: `DESIGN_SPEC.md`, `COMPONENT_INVENTORY.md`, `ANDROID_COMPONENT_MAP.md`, `MOTION_SPEC.md`, `DESIGN_MASTER_README.md`
- Production sources: `QuranShellScreen`, all `ui/quran` components, Qur'an ViewModels, repositories, preferences, audio/download services, reflection and memorisation systems, navigation, and backup/restore mapping.

The Frozen references are presentation authority. Production repositories and bundled assets remain content and behaviour authority.

## B. Complete Production Capability Inventory

Eighty-four distinct production capabilities or compatibility behaviours were identified.

| # | Capability | Current entry/UI | Production owner | Persistence / backup | Frozen placement / coverage | Status |
|---|---|---|---|---|---|---|
| 1 | Canonical Surah catalog | Reader and picker | `QuranCatalog` | Bundled asset; no backup | Reader/picker | ENGINE-ONLY |
| 2 | Canonical Arabic ayah text | Reader | `QuranTextRepository`, `qpc_hafs.json` | Bundled asset; no backup | Document canvas | ENGINE-ONLY |
| 3 | Verse numbering | Ayah row | Catalog/repository | Derived | Frozen ayah presentation | ALREADY FROZEN |
| 4 | Bismillah handling | Before eligible Surahs | Reader surface | Derived; Surahs 1 and 9 special-cased | Document canvas | ENGINE-ONLY |
| 5 | Uthmani Hafs font | Arabic reader text | `R.font.uthmani_hafs` | App resource | Frozen Arabic typography | ENGINE-ONLY |
| 6 | Word segmentation | Ayah enrichment | `QuranTextRepository.withIndexedWords` | Bundled metadata | No ordinary visible word surface | ENGINE-ONLY |
| 7 | Word IDs and positions | Debug word chips | Word metadata repository | Bundled metadata | Debug-only, no Frozen row needed | ENGINE-ONLY |
| 8 | Word root/lemma/meaning metadata | Debug detail sheet | `QuranWordRendering` | Bundled metadata | Debug-only | ENGINE-ONLY |
| 9 | Tajweed character ranges/classes | Arabic text spans | `Tajweed.json`, renderer | Bundled metadata | Frozen Tajweed rendering/toggle | ALREADY FROZEN |
| 10 | Tajweed on/off | Reader settings | `QuranReaderViewModel.setTajweedEnabled` | Preferences; backed up | Reader Settings toggle | ALREADY FROZEN |
| 11 | Arabic text size | Reader settings slider | ViewModel/preferences | 70-140%; backed up | Reader Settings | ALREADY FROZEN |
| 12 | Initial reader entry | Qur'an route | `QuranReaderViewModel` | Uses last position | Opens directly to reading canvas | ALREADY FROZEN |
| 13 | Last Surah restore | Reader startup | Preferences/ViewModel | Backed up | Resume-first reader | ALREADY FROZEN |
| 14 | Last ayah restore/scroll | Reader startup/scroll | ViewModel/LazyList state | Backed up | Resume-first reader | ALREADY FROZEN |
| 15 | Update reading position | Scroll/open/double tap | ViewModel/preferences | Backed up | Automatic resume state | ENGINE-ONLY |
| 16 | Double-tap save position | Ayah gesture | Reader surface/ViewModel | Updates backed-up position | No Frozen gesture decision | NEEDS DESIGN DECISION |
| 17 | Recent locations | Chips below header | ViewModel/preferences | Latest 5; backed up | No Frozen management surface | STOP-AND-ASK |
| 18 | Pending exact-ayah navigation | Bookmark/reflection/Memorise routes | Navigation/ViewModel | Transient destination | Reader scroll target | ENGINE-ONLY |
| 19 | Surah picker | Tap Surah title | `QuranSelectorSheets` | UI state only | Frozen Surah picker | ALREADY FROZEN |
| 20 | Surah-name search | Picker search | Picker filtering | Transient | Frozen optional search | ALREADY FROZEN |
| 21 | Exact numeric ayah lookup | Picker query like `2:255` | Picker + bundled corpus | Transient | Could remain in picker search | APPROVED SHARED COMPONENT |
| 22 | Revelation-type filters | All/Meccan/Medinan chips | Picker | Transient | Not shown in Frozen picker | NEEDS DESIGN DECISION |
| 23 | Juz grouping in picker | Picker list headings | Catalog | Derived | Frozen picker does not specify grouping | NEEDS DESIGN DECISION |
| 24 | Ayah counts/current Surah marker | Picker rows | Catalog/UI state | Derived | Frozen picker rows | ALREADY FROZEN |
| 25 | Scroll-to-current Surah | No explicit automatic scroll handler found | Picker | None | Frozen does not require it | ENGINE-ONLY |
| 26 | Ayah contextual selection | Long press/action sheet today | Reader surface | Transient | Faint selected ayah + action bar | ALREADY FROZEN |
| 27 | Listen selected ayah | Permanent row control/action state | Audio ViewModel/repository | Uses selected reciter | Frozen `Listen` | ALREADY FROZEN |
| 28 | Tafsir selected ayah | Inline expansion today | ViewModel/repository | Source preference backed up | Frozen `Tafsir` sheet | ALREADY FROZEN |
| 29 | Reflect selected ayah | Action/reflection editor | Reflection repository | Ordinary Study note; backed up | Frozen `Reflect` | ALREADY FROZEN |
| 30 | Copy selected ayah | Ayah action | Clipboard handler | None | Frozen `Copy` | ALREADY FROZEN |
| 31 | Save/bookmark selected ayah | Bookmark action | ViewModel/preferences | Backed up | Frozen `Save` maps to bookmark | NEEDS LABEL CONFIRMATION |
| 32 | More selected ayah | Action sheet | Reader surface | Transient | Frozen `More` | ALREADY FROZEN |
| 33 | Copy payload | Clipboard | Reader surface | Arabic plus Surah name/reference; no translation | Preserve payload | ENGINE-ONLY |
| 34 | Create bookmark | Ayah action | `toggleBookmark` | Set of verse keys; backed up | `Save` | NEEDS LABEL CONFIRMATION |
| 35 | Remove bookmark | Ayah/bookmark action | `toggleBookmark` | Backed up | Selected saved state/More | NEEDS DESIGN DECISION |
| 36 | Bookmark list | Permanent top-bar bookmark icon | `QuranBookmarksSheet` | Reads backed-up keys | No Frozen destination | STOP-AND-ASK |
| 37 | Jump from bookmark | Bookmark sheet row | ViewModel/navigation | Updates reading location | Reader exact-ayah navigation | ENGINE-ONLY |
| 38 | Bookmark metadata | None beyond verse key | Preferences | Backed up | No extra UI required | ENGINE-ONLY |
| 39 | Create reflection | Ayah Reflect sheet | ViewModel/reflection repository | Creates Study note | Frozen Reflection sheet | ALREADY FROZEN |
| 40 | Edit reflection | Inline preview/full sheet | ViewModel/repository | Updates Study note | Frozen full Reflection surface | ALREADY FROZEN |
| 41 | Delete reflection | Reflection editor | Note repository | Deletes Study note | Frozen full Reflection surface must retain delete | APPROVED SHARED COMPONENT |
| 42 | Multiple reflections per ayah | Repository supports list | Reflection repository | Multiple notes; backed up | Compact preview/count | ALREADY FROZEN |
| 43 | Reflection timestamps | Hub/list metadata | Note timestamps | Backed up with note | Restrained metadata where frozen | ALREADY FROZEN |
| 44 | Inline reflection preview | Separate cards today | Reader/reflection state | Derived | Frozen 0/1/multiple rule | ALREADY FROZEN |
| 45 | Reflection source/Arabic/translation body | Structured Study note body | ViewModel | Backed up through notes | Engine-owned | ENGINE-ONLY |
| 46 | Aggregate Reflections Hub | Temporary Study FAB Tools route | `QuranReflectionsHubScreen` | Reads reflection notes | Final Frozen destination missing | STOP-AND-ASK |
| 47 | Hub newest-first sorting | Reflections Hub | Reflection repository | Derived from timestamps | Preserve in final hub | ENGINE-ONLY |
| 48 | Hub open exact ayah | Hub row | Navigation pending verse | Transient | Reader exact-ayah navigation | ENGINE-ONLY |
| 49 | Translation on/off | Reader settings | ViewModel/preferences | Backed up | Frozen Reader Settings | ALREADY FROZEN |
| 50 | Sahih International translation | Reader | Bundled translation source | Offline; source backed up | Translation source selector | ALREADY FROZEN |
| 51 | Maududi translation | Reader | Bundled Tanzil text | Offline; source backed up | Translation source selector | ALREADY FROZEN |
| 52 | Maududi explanatory footnotes | Expandable inline footnotes | Quran Foundation + cache | Cached up to 7 days; not backup content | No Frozen footnote treatment | STOP-AND-ASK |
| 53 | One active translation source | Reader settings | ViewModel | Backed up | Frozen selector | ALREADY FROZEN |
| 54 | Translation text size | Reader settings slider | ViewModel/preferences | 80-130%; backed up | Missing Frozen row | STOP-AND-ASK |
| 55 | Bundled Mukhtasar Tafsir | Tafsir surface | Text repository | Offline asset | Frozen Tafsir sheet | ALREADY FROZEN |
| 56 | Remote Tafsir sources | Tafsir source chips | Quran Foundation repository | Source ID backed up | Frozen needs source-selection treatment | STOP-AND-ASK |
| 57 | Tafsir loading/error/empty states | Inline Tafsir today | ViewModel/repository | Transient | Frozen sheet states required | APPROVED SHARED COMPONENT |
| 58 | Tafsir cache | Repository memory cache | Text repository | Device/runtime cache only | No new UI | ENGINE-ONLY |
| 59 | Arabic/English Tafsir rendering | Tafsir surface | Source content | None beyond selected source | Frozen sheet mixed-direction content | ALREADY FROZEN |
| 60 | Dynamic reciter catalog | Reciter picker | Audio repository/Quran Foundation | Catalog cache | Frozen reciter selector | ALREADY FROZEN |
| 61 | Selected/default reciter | First play/picker/settings | ViewModel/preferences | Reciter ID backed up | Frozen Reader Settings | ALREADY FROZEN |
| 62 | Reciter unavailable/error handling | Picker/play status | Audio repository/ViewModel | Transient | Frozen sheet/error state | APPROVED SHARED COMPONENT |
| 63 | Play selected ayah | Row/Listen | Audio repository/player | Local cache metadata | Frozen Listen/audio bar | ALREADY FROZEN |
| 64 | Pause/resume/stop | Audio mini-player | `QuranAudioPlayer` | Runtime only | Frozen compact audio bar | ALREADY FROZEN |
| 65 | Seek/progress/time | Audio mini-player | Audio player/ViewModel | Runtime only | Frozen compact audio bar | ALREADY FROZEN |
| 66 | Previous/next ayah | Audio mini-player | `playAdjacentAudio` | Runtime only | Frozen audio controls must retain | APPROVED SHARED COMPONENT |
| 67 | Skip backward/forward 10 seconds | Audio mini-player | ViewModel/player | Runtime only | Not explicit in Frozen bar | NEEDS DESIGN DECISION |
| 68 | Playback completion | Player callback | Audio player/ViewModel | Stops after current media; no autoplay | No extra UI | ENGINE-ONLY |
| 69 | Playback speed | Audio mini-player chips | ViewModel/preferences | 0.5, 1, 1.5, 2; global and backed up | Missing Frozen placement | STOP-AND-ASK |
| 70 | Audio status/error message | Reader/audio state | ViewModel | Transient | Frozen compact status/error | APPROVED SHARED COMPONENT |
| 71 | Background/process playback | MediaPlayer singleton only | `QuranAudioPlayer` | Runtime; no MediaSession/foreground playback service | Frozen does not promise persistent background playback | ENGINE-ONLY |
| 72 | Audio focus | No explicit AudioManager focus implementation found | Audio player | None | Existing engine limitation, not Stage 7 UI | ENGINE-ONLY |
| 73 | On-demand playback download/cache | First playback | Audio repository | Device-local | Invisible preparation state | ENGINE-ONLY |
| 74 | Download one Surah | Audio Downloads sheet | Audio repository | Device-local | Final placement missing | STOP-AND-ASK |
| 75 | Batch download missing Surahs | Audio Downloads sheet | Foreground download service | Device-local | Dedicated management needed | STOP-AND-ASK |
| 76 | Download queue/progress | Audio Downloads sheet/notification | Service/repository | Device-local markers | Dedicated management needed | STOP-AND-ASK |
| 77 | Downloaded/failed/retry states | Audio Downloads sheet | Repository/service | Device-local | Dedicated management needed | STOP-AND-ASK |
| 78 | Remove downloaded audio | No production handler found | None | Not supported | Must not be invented | ENGINE-ONLY |
| 79 | Wi-Fi-only/metered policy | No production setting found | None | Not supported | Must not be invented | ENGINE-ONLY |
| 80 | Qur'an audio storage accounting | No category calculator found | None | Not supported | Must not be fabricated | ENGINE-ONLY |
| 81 | Normal Qur'an text search | No Arabic/translation/full-text engine found | None | Not supported | Must not be invented | ENGINE-ONLY |
| 82 | Reader-to-Memorise handoff | Permanent per-ayah/top-bar controls today | Memorise ViewModel/navigation | Memorisation state backed up | Stage 8 boundary not frozen | DEFERRED TO STAGE 8 |
| 83 | Memorise status/actions in reader | Many per-ayah controls | Memorise engine | Backed up | Must be removed from normal Stage 7 chrome only after handoff is frozen | STOP-AND-ASK / DEFERRED TO STAGE 8 |
| 84 | AI Listen/repeat/test flows | Reader-hosted Memorise surfaces | Memorise engines | Attempts/state backed up | Stage 8 only | DEFERRED TO STAGE 8 |

## C. Frozen-Covered Functions

The production engine maps cleanly to the Frozen reader for:

- resume-first entry into a continuous Surah/ayah reader;
- Surah title opening the Surah picker;
- canonical Arabic text, verse numbers and Bismillah;
- Uthmani font, RTL rendering and Tajweed spans;
- contextual Arabic size, translation, Tajweed, reciter and default Tafsir settings;
- restrained selected-ayah state with Listen, Tafsir, Reflect, Copy, Save and More;
- one active translation source;
- per-ayah Tafsir content;
- 0/1/multiple inline Reflection presentation and full Reflection editing;
- compact Qur'an audio playback bar;
- warm Light reader canvas and shared Dark/OLED systems.

No corpus, word metadata, Tajweed, Room, reflection, bookmark, audio or backup change is required for these mappings.

## D. Reader Entry And Resume Semantics

The production route already satisfies the Frozen resume-first contract:

1. `QuranReaderViewModel` reads `quranLastReadSurah` and `quranLastReadAyah`.
2. It loads that Surah from the canonical repository.
3. The reader scrolls to the restored ayah.
4. Meaningful location changes persist Surah/ayah and update a latest-five recent list.
5. Bookmark, Reflections Hub and Memorise routes can set an exact pending verse and reuse the same reader.

The reader is continuous Surah/ayah presentation, not Mushaf-page based. Juz exists as catalog metadata/picker grouping; production has no Mushaf page-number state.

The invisible double-tap-to-save-position gesture is not described by the Frozen contract and needs an explicit preserve/remove decision.

## E. Ayah Selection And Action Hierarchy

### Frozen direct selected-ayah actions

- Listen -> existing Qur'an recitation playback
- Tafsir -> existing source-aware Tafsir engine
- Reflect -> existing Reflection note flow
- Copy -> existing clipboard payload
- Save -> proposed mapping to existing bookmark state, pending explicit label confirmation
- More -> secondary production actions approved by a future amendment

### Existing secondary/extra actions requiring placement decisions

- Remove bookmark / saved state treatment
- Double-tap save current reading position
- Memorise/test/status actions (Stage 8 boundary)
- Skip backward/forward 10 seconds in audio controls
- Translation footnotes

Current Copy semantics are preserved: Surah name and `surah:ayah` reference followed by Arabic text. It does not copy the translation and does not invoke Android Share.

## F. Reflection Behaviour

Reflections use real Study notes rather than a separate Room schema:

- the root Study folder is named `Quran Reflections`;
- a structured `Source: <Surah name> <surah>:<ayah>` line links the note to an ayah;
- Arabic text, the active translation and the user's body are saved in the note body;
- create, edit and delete use existing folder/note repositories;
- multiple notes may reference the same ayah;
- timestamps and backup behaviour come from ordinary notes.

This maps cleanly to the Frozen inline rule and full Reflection sheet. Stage 7 must change presentation only; it must not alter reflection note structure or parsing.

## G. Reflections Hub

The existing aggregate Hub:

- lists all parsed Reflection notes newest first;
- displays Surah/ayah reference, title, timestamp, Arabic, translation and preview;
- opens the exact Qur'an ayah;
- has no production search, filters or grouping controls;
- does not expose aggregate edit/delete controls; editing occurs from the ayah Reflection surface.

Its current Study root FAB -> Tools entry is explicitly temporary. A final Frozen destination and visual treatment are required before Stage 7.

## H. Bookmark And Recent-Location Management

Bookmarks are a backed-up set of `surah:ayah` keys. Production supports add, remove, list and exact-ayah jump, but no labels, folders or bookmark notes. A permanent top-bar bookmark icon currently opens the list.

Recent locations are a backed-up latest-five list containing Surah, ayah and timestamp. They currently appear as reader chips and open the exact location.

The Frozen reader defines contextual Save but not the management destination for bookmarks or recent locations. Their final destination, access control and presentation require an amendment. They must not silently disappear.

## I. Tafsir

- Bundled offline source: Mukhtasar.
- Remote source catalog: Quran Foundation sources, filtered toward available Ibn Kathir (English), Al-Tabari (Arabic) and Al-Qurtubi (Arabic).
- The selected source ID is persisted and backed up.
- Remote content has loading/error/empty state and in-memory caching.
- Current presentation expands Tafsir inline per ayah and exposes source chips.

The Frozen Tafsir sheet covers content presentation, but it does not fully freeze the multiple-source selector and its loading/error states. A source-selector amendment is required; the engine and source list must remain production-owned.

## J. Translations And Translation Text Size

Production offers one active translation at a time:

- Sahih International, bundled/offline;
- Maududi/Tafheem-ul-Quran, bundled/offline, with optional Quran Foundation explanatory footnotes cached temporarily.

Translation enabled/source are persisted and backed up. Translation size is independently adjustable from 80% to 130%, affects translation text only, and is backed up. Arabic size remains a separate 70%-140% preference.

Required Frozen amendments:

- add the real translation-size control to contextual Reader Settings;
- define the presentation of expandable Maududi footnotes without permanent ayah cards.

## K. Tajweed And Word Data

Tajweed is a single global persisted on/off preference. Production has no per-rule toggles or user-facing legend. Character-range metadata is applied to canonical text and must not be remapped.

Word metadata includes stable Surah/ayah/position identity, root, lemma, transliteration, meaning and source. Normal reader rendering does not expose word taps; the word-detail sheet is debug-only. Stage 7 may retain the engine without adding permanent word UI. Any future normal word interaction would require a separate design and canonical-alignment verification.

## L. Reciters And Audio Playback

The reciter catalog is retrieved from the production audio source and filtered to supported real reciters, including Abdul Basit variants, Sudais, Shatri, Rifai, Husary variants, Mishary, Minshawi variants, Shuraym and Tablawi. Fallback entries exist when the network catalog is unavailable.

The selected reciter is global, persisted and backed up. First playback asks for a reciter when none is selected. Audio uses verse-by-verse media when available or timestamped full-Surah media otherwise.

Production playback supports play, pause, resume, stop, seek, progress/time, previous/next ayah and +/-10-second seek. Completion stops playback; it does not automatically continue to the next ayah. There is no normal-reader repeat mode. Repeat/test logic belongs to Memorise/Stage 8.

The player is an in-process `MediaPlayer`; no dedicated MediaSession, audio-focus manager or playback foreground service was found. The foreground service belongs to downloads, not playback. These are existing engine characteristics, not authority to redesign or replace audio during Stage 7.

## M. Playback Speed

The visible production speed choices are 0.5x, 1x, 1.5x and 2x. The preference is global across reciters, persisted and backed up. It currently lives in the audio mini-player.

The Frozen references do not place speed. Recommended placement: retain it as a compact secondary control in the expanded reciter/audio surface rather than permanently enlarging Reader Settings. This recommendation requires approval.

## N. Audio Downloads

Production supports:

- on-demand download/cache required for playback;
- explicit single-Surah download;
- batch download of all missing Surahs for a reciter;
- foreground queue/notification progress;
- preparing, queued, downloading, downloaded and failed/retry states;
- offline playback from app-local files.

Production does not support deleting downloaded audio, Wi-Fi-only rules or truthful Qur'an-audio storage totals. These must not be invented.

Downloaded media and completion markers are device-local and intentionally excluded from MyVault backup because they are re-downloadable.

Recommended final placement: **D**, a contextual single-Surah action plus a dedicated restrained Downloads management subpage/sheet reachable from Reader Settings or the reciter/audio surface. The final visual destination must be frozen before implementation.

## O. Search

Production supports:

- Surah-name search inside the picker;
- exact numeric ayah lookup when the query contains a valid Surah/ayah pair.

Production does not implement Arabic full-text search, translation search, general ayah-text search or normal word search. Stage 7 must not invent them. Global Search remains a separate deferred feature.

## P. Study Relationships

Production's Qur'an-to-Study relationship is the Reflection system: it creates and updates a structured Study note and supports returning from the Reflections Hub to the exact ayah.

No separate production handler was found for linking an arbitrary ayah to an existing Study note, creating a generic Study note excerpt, or producing note backlinks outside the Reflection source parser. Stage 7 must not infer PDF-style linking semantics.

## Q. Memorise Boundary

The current reader exposes substantial Memorise functionality: test Surah, mark Surah memorised, per-ayah memorising/memorised/revised/weak/incorrect states, conceal/reveal, review panels, attempt history and AI Listen.

These engines and backed-up records remain authoritative, but their normal-reader placement is not frozen. The Stage 7 reader must not carry permanent Memorise chrome merely because the current screen does. It also must not remove access before a Stage 8 handoff/entry treatment is approved. This is a hard pre-Stage-7 design blocker.

Qur'an recitation audio, Note/PDF narration and Memorise AI Listen remain three distinct systems.

## R. Theme, RTL And Font Constraints

- Light reader requires the approved warm/off-white canvas, not the cool global Light background.
- Dark and OLED use shared semantic systems; Tajweed colours must be verified independently in both.
- Arabic uses the production Uthmani Hafs font, RTL direction and right alignment.
- Translation, Tafsir and metadata retain their own mixed-direction treatment.
- Uthmani font metrics require generous line height; spacing must be tuned around the real font rather than substituting another face.
- Verse-number placement must be adapted visually without modifying canonical text.

Current Tajweed theme detection should be tested carefully for OLED because renderer colour decisions must not assume only one dark palette.

## S. Backup And Persistence

Existing backed-up Qur'an state includes:

- last Surah and ayah;
- Arabic and translation font percentages;
- translation enabled/source;
- Tajweed enabled;
- default Tafsir source ID;
- reciter ID;
- playback speed;
- bookmarks;
- recent locations;
- memorisation records and attempts.

Reflections are backed up through existing folders/notes. Downloaded audio is device-local and excluded. Active playback, selected ayah, open sheets and expanded Tafsir are transient.

The audit found no need for a Stage 7 backup field, schema change, Room migration, corpus rewrite or sync-format change. Full destructive Android/Web backup and restore remains mandatory in Stage 10.

## T. Performance Risks And State Ownership

- Surahs use a `LazyColumn`; Al-Baqarah reaches 286 ayahs.
- Canonical Surahs are cached in an LRU repository; remote text is cached separately.
- Tajweed `AnnotatedString` work is remembered by ayah, annotations and theme state.
- Reflections and Tafsir are keyed by verse; audio has global progress state updated about every 250 ms.
- Current per-ayah presentation receives many Memorise/audio/reflection flags and can cause broad recomposition.
- Surah switching and exact-ayah navigation depend on stable scroll restoration.
- Picker exact-ayah lookup reads the bundled corpus for numeric lookup.

Stage 7 should use stable ayah keys and isolate audio progress, selection, Tafsir and Reflection state so playback does not recompose the whole Surah or move the reader. This is an implementation constraint, not authority to replace the engines.

## U. Explicit Frozen Amendment Blockers

Stage 7 requires a Frozen Qur'an amendment before implementation. The unresolved visible states are:

1. Final Qur'an Reflections Hub destination and Hub presentation.
2. Bookmark list access/management and the exact `Save`/saved-state wording.
3. Recent-location management/access, including whether reader chips remain.
4. Translation text-size row in Reader Settings.
5. Maududi translation footnote expansion treatment.
6. Multiple Tafsir source selection and loading/error/empty treatment inside the Frozen Tafsir architecture.
7. Playback speed placement.
8. Audio download management: single-Surah action and dedicated manager destination.
9. Surah picker Meccan/Medinan filters and Juz grouping treatment.
10. +/-10-second seek treatment in the audio surface.
11. Double-tap save-reading-position compatibility gesture.
12. Normal-reader to Memorise entry/handoff and removal of permanent Memorise controls from Stage 7 chrome.

No proposed solution may modify canonical text, word IDs, Tajweed ranges, reflection/bookmark schemas, audio architecture, Memorise state, Room, backup or reading-position compatibility.

## V. Decision Gate

**A Frozen prototype amendment is required. Stage 7 cannot proceed directly.**

The production engines are sufficient for the approved core reader. The blocker is presentation placement for the twelve existing production states above. Stage 7 implementation must wait until those states are explicitly frozen.
