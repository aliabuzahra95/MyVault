# Continuous Quran Audio Contract

## Source and identity

The existing MyVault Quran proxy calls Quran Foundation Content API v4. The chapter endpoint returns one recording and its own millisecond ayah ranges. Chapter-reciter IDs and ayah-recitation IDs are different namespaces. The explicit registry in `QuranTimedRecitations` maps each enabled app reciter to its own full recording and timing provider. MP3Quran's public timing API supplies additional recordings; no backend or canonical-data changes are involved. The widget uses the app's existing selected-reciter preference.

| App choice | App ID | Full recording/timing identity |
| --- | --- | --- |
| Abdul Basit Mujawwad | 1 | Foundation chapter 1 |
| Abdul Basit Murattal | 2 | MP3Quran read 53, server7/basit |
| Abu Bakr al-Shatri | 4 | Foundation chapter 4 |
| Husary | 6 | Foundation chapter 6 |
| Mishary al-Afasy | 7 | Foundation chapter 7 |
| Minshawi Murattal | 9 | Foundation chapter 9 |
| Sa'ud ash-Shuraym | 10 | MP3Quran read 31, server7/shur |
| Muhammad al-Tablawi | 11 | MP3Quran read 106, server12/tblawi |
| Yasser al-Dossari | 1000092 | MP3Quran read 92, server11/yasser |
| Saad al-Ghamdi | 1000030 | MP3Quran read 30, server7/s_gmd |
| Fares Abbad | 1000081 | MP3Quran read 81, server8/frs_a |

The three new app IDs occupy a reserved namespace, not Quran Foundation's recitation IDs. Requested choices remain visible during catalogue outages. Existing choices are retained. Unsupported continuous recordings fail explicitly, never substitute another reciter.

Sources checked 2026-09-05:
- https://api-docs.quran.com/docs/content_apis_versioned/4.0.0/chapter-reciter-audio-file/
- https://api-docs.quran.com/docs/content_apis_versioned/4.0.0/chapter-reciters/
- https://quranicaudio.com/about

QuranicAudio permits free personal use of downloads, not commercial use. This does not establish redistribution/commercial rights for MyVault. No audio is bundled, mirrored or bulk downloaded; only the requested recording is cached. Quran Foundation application/source-specific terms continue to apply to the existing proxy access. Commercial release requires confirming those rights separately.

## Coverage evidence

| Recitation | Surah | Full resource | Timings | Verification |
| --- | --- | --- | --- | --- |
| Afasy Murattal chapter 7 | 1 | API audio ID 911 | 7 ordered ranges | MP3 fetched/decoded: 46.447 seconds; last boundary 46.490 seconds (43ms rounding tolerance). Runtime/audible checks tracked separately. |
| Afasy Murattal chapter 7 | 2 | API response | 286 ordered ranges | Canonical coverage unit test; audible long-track check pending. 2:255 starts 6,153,050ms. |
| Afasy Murattal chapter 7 | 4 | API response | 176 ordered ranges | Canonical coverage unit test; audible check pending. |
| Afasy Murattal chapter 7 | 9 | API response | 129 ordered ranges | Canonical coverage unit test; opening/alignment listening check pending. |
| Afasy Murattal chapter 7 | Other Surahs | 114 resources listed by API | Checked on request | Not advertised as individually audible-verified. Missing/invalid timings reject continuous mode. |
| All eight requested choices (including both Basit styles) | 1 | Distinct registry recordings | Seven ordered canonical intervals each | API 36.1 real MediaPlayer test passed for each: requested 1:2, correct reciter ID, unique recording identity, advancing media position. Not human listening acceptance. |
| Remaining unmapped reciters | Any | Existing ayah path | No verified full mapping | Continuous mode unavailable; no silent reciter switch. |

## Validation and storage

- Require exact chapter and ordered canonical Surah:ayah coverage, positive millisecond intervals, and no overlaps. Do not infer ayah boundaries from words or the API's legacy `duration` field.
- Preamble before the first timestamp has no playing ayah; natural gaps retain the preceding ayah. No Bismillah insertion/removal.
- Cache identity hashes chapter-reciter ID, source audio ID, exact URL and complete timing ranges. Full-Surah cache is separate from existing ayah downloads.
- Validate HTTP response and full Content-Length, decode duration, allow at most 250ms endpoint rounding, reject missing duration or unaccounted trailing audio over 30 seconds. API `file_size` is not used as a checksum: observed Al-Fatihah metadata differs from the actual HTTP object size.
- Per-recording limit 256MiB; bounded 384MiB evictable cache, free-space reserve 32MiB, cancellable reads and temporary-file cleanup. No vault backup changes.

## Playback policy

Existing MediaPlayer retained. A singleton QuranPlaybackController owns preparation, cancellation generation, timeline and state. A mediaPlayback foreground service owns background lifetime, AudioFocusRequest, platform MediaSession and notification. Reader ViewModel observes state and delegates commands; closing the reader no longer releases playback.

This ayah pauses the loaded full recording at its actual ayah end. Continue Surah removes that boundary without replacing MediaPlayer. Manual paused state is retained when mode changes. Continue after a single-ayah boundary moves to the next known boundary; final ayah never wraps. Position samples drive highlighting; no wall-clock verse counter. Selected ayah and saved reading position are not rewritten by playback.

Follow recitation is opt-in, suspended by a drag or explicit navigation request. Widget updates occur on status/ayah/source changes, not position ticks; partial headers and stable collection refreshes send no scroll commands.

## Android contract

https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start permits user-triggered widget service starts. The service is non-exported and START_NOT_STICKY; boot/resize/update do not start audio. Widget rows use a broadcast collection template with separate OPEN and PLAY payloads. OPEN explicitly launches the existing Activity route; PLAY never launches an Activity. Real launcher/BAL acceptance is separately required.

Audio focus loss and noisy-route events pause recitation. Stop releases focus and media. Platform force-stop is not treated as ordinary backgrounding. No new recording permission, Media3 dependency or unrelated narration change.

## Additional reciter investigation requested by user

The user's priorities include Abu Bakr al-Shatri, Abdul Basit, Muhammad al-Tablawi, Sa'ud ash-Shuraym, Yasser al-Dossari, Saad al-Ghamdi and Fares Abbad. All are now wired into the shared app/widget playback path.

Live checks on 2026-09-05 found Quran Foundation chapter recordings with seven Al-Fatihah timing entries for Shatri (chapter ID 4), Abdul Basit Mujawwad (1), Abdul Basit Murattal (2), Shuraym (10), Husary (6) and Minshawi Murattal (9). They have distinct, named recording paths and must not reuse Afasy timings.

IMPORTANT: MyVault ayah-recitation ID 11 is Tablawi, but requesting chapter-reciter ID 11 returns an Abdul Muhsin al-Qasim recording. Do not equate these namespaces. Tablawi is not in the current Foundation chapter-reciter catalogue.

MP3Quran's documented public API provides a separate legitimate candidate for Tablawi:

- Documentation: https://www.mp3quran.net/eng/api
- Timing catalogue: https://www.mp3quran.net/api/v3/ayat_timing/reads
- Tablawi Murattal timing read ID 106, exact folder https://server12.mp3quran.net/tblawi/
- Catalogue reports 114 timed Surahs. Live Al-Fatihah response has preamble ayah 0 plus ayat 1-7; Al-Baqara response has preamble 0 plus ayat 1-286. Preamble must not become canonical ayah 1.
- Tablawi Al-Fatihah MP3 fetched and decoded: 52.435756 seconds, 847710 bytes; timing end 52050ms. This is a source/duration check, not an audible alignment pass.
- The timing catalogue also lists Shatri (4), Shuraym (31), Abdul Basit Mujawwad (51) and Murattal (53), each reporting 114 Surahs.
- Additional candidates absent from MyVault's current curated list: Ahmed al-Ajmy (5), Saad al-Ghamdi (30), Ali Jaber (76), Nasser al-Qatami (86), Yasser al-Dossari (92). Each timing catalogue entry reports Hafs and 114 Surahs.

Shuraym's Foundation Al-Fatihah timestamps overlap by 9-10ms at several boundaries. The strict parser rejects that fixture. The enabled Shuraym mapping instead uses MP3Quran read 31 and its own matching recording, with ordered non-overlapping timings. Abdul Basit Murattal's Foundation Al-Baqara data also overlaps at one boundary, so it uses MP3Quran read 53. Both replacement sources passed Al-Fatihah and Al-Baqara count/order/non-overlap checks. No boundary trimming, estimation or timing reuse across performances was introduced.

MP3Quran preamble ayah 0 is kept separate from canonical ayat. Continue from ayah 1 plays the actual opening from file position zero; the preamble has no fabricated playing ayah. Exact-ayah requests use that ayah's supplied start time.

Catalogue counts are not proof that every individual timing map audibly aligns with its recording. Each chapter is structurally and duration-validated on request. Physical listening and commercial-use terms remain separate acceptance gates; public API availability is not a blanket redistribution licence.
