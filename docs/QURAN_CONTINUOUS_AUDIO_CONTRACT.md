# Continuous Quran Audio Contract

## Source and identity

The existing MyVault Quran proxy calls Quran Foundation Content API v4. The chapter endpoint returns one recording and its own millisecond ayah ranges. Chapter-reciter IDs and ayah-recitation IDs are different namespaces. Only the verified mapping ayah ID 7 -> chapter ID 7 (Mishary al-Afasy, Murattal, Hafs) is enabled for continuous synchronization in this pass. Other existing reciters retain single-ayah playback, without automatic substitution.

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
| Other current reciters | Any | Existing ayah path | No verified mapping in this pass | Continuous mode unavailable; no silent reciter switch. |

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
