# Drawer Cleanup Verification

Status: implemented, committed, pushed, built, signed and uploaded; physical Samsung acceptance remains pending.

## A. Starting Point

- Repository: `/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`
- Branch: `frozen-design-master-port`
- Starting SHA: `9b1ea0624dda83528b9ea3b67ae7dc6c096f2b8b`
- Pushed recovery tag: `pre-drawer-cleanup-20260911-114637`, resolving to the starting SHA.
- Baseline debug build and unit suite passed. Existing unrelated untracked artifacts were preserved.

## B-C. Drawer Structure

Dashboard, Favourites, Qur'an, Reflections, Memorise, Study, Library, Courses.

Removed the top Search and Settings rows, APPLICATION/KNOWLEDGE labels, their extra spacing, and the header X. The user's follow-up also removed Workspace Attachments from the drawer and moved Favourites immediately below Dashboard. The internal attachments route and underlying data are preserved. Courses remains after Library; the user explicitly requested only Workspace Attachments be removed.

Top-level rows share the same icon/text columns as expandable roots. The final horizontal refinement moves icon centers from 48dp to 36dp, approximately beneath the unchanged avatar center at 35dp. Titles move from 65dp to 57dp, with a 12dp icon/title gap. The 27dp chevron touch gutter is retained separately; non-expandable rows reserve noninteractive space. Nested indentation uses a 16dp base plus 8dp per level, capped at three levels. Right-side count/add controls and row heights remain unchanged. Native assertions confirm equal title left edges, including real production roots.

## D-E. Search And Closing

The header Search icon closes the drawer and invokes the existing Global Search callback/route. Search implementation and result routing are unchanged. Real emulator navigation tested from Dashboard, Qur'an, Reflections, Memorise, Study, Library and Courses. PDF context is covered by a shell callback fixture and the unchanged shared route, not a real PDF device test.

Testing exposed a pre-existing Back behavior that left the activity instead of closing the drawer. Added a drawer-open-only Back handler. Back, outside tap, swipe and navigation-selection closing pass on the final build at all four widths. The automated swipe waits for its input process to complete before evaluating the resulting screen.

## F-G. Photo

Reads `GoogleSignIn.getLastSignedInAccount` display name and photo URL from the existing local sign-in cache. Metadata must match the current Drive account email before it can appear. No OAuth scopes, sign-in flows, Google Cloud, Drive queries or reconnect behavior changed.

Tiny HTTPS-only, bounded avatar loader using the app's existing Android bitmap/networking patterns. Memory cache is bounded to 1 MB; disk cache is account/URL-keyed with at most 16 files of up to 512 KB each. Decode is sampled to at most 128 pixels. Read/network/decode failures retain initials. Cache bytes remain device-local, outside vault backups. Account changes recreate bitmap state so the previous account photo cannot be retained under the new account.

Synthetic offline-cache, malformed-image and account-isolation tests passed. A real signed-in Google profile photo remains unverified without the Samsung account session.

## H-I. Names And Isolation

Dedicated local DataStore, separate from `VaultUserPreferences` and backup serialization. Account keys are hashes of normalized emails; disconnected identity uses an independent local key. Custom names win over matching Google display name, then email local-part, then MyVault. Full email is not displayed in the header.

Tap name/avatar to edit; subtitle retains workspace switching. Unicode/Arabic, spaces and punctuation supported. Names are trimmed, limited to 60 Unicode code points, and ellipsized in the header. Blank custom name clears the override. Save checks the current account before writing. Failure retains the dialog and shows feedback.

Tests cover A/B/A isolation, disconnected fallback, trimming/length, per-account persistence, reset to fallback, and a write -> force-stop -> fresh-process read using a disposable test account key. Test keys were removed after verification; real account settings were not switched.

## J-K. Preserved Areas

Footer Settings/status dot/Backup/Theme remain unchanged. Folder rendering changes only the requested horizontal spacing; interaction callbacks, counts, plus controls and hierarchy remain unchanged. Existing backup callback and status source remain unchanged. Workspace switching remains accessible through the subtitle.

Reflections is the existing real destination between Qur'an and Memorise. No Reflections screen, data, search/filter/sort, canonical data, note semantics, Room, manifest, backup/restore, Google Drive architecture or Web changes.

## L-M. Device And Visual Evidence

Samsung is disconnected. Emulator API 36 used instead; this does not satisfy physical Samsung acceptance.

360/390/412/430dp: native Light/Dark/OLED fixtures, rename dialog, Arabic name, long-name truncation and expanded tree captured. Representative images inspected at every width, plus the real production drawer at 412dp. No overlap or clipping found; top-level titles align and footer controls remain visible.

Before screenshots: `verified-native/drawer-cleanup/`. Final horizontal alignment screenshots: `alignment-after/drawer-cleanup/`. No Samsung screenshots were possible.

Pixel comparison of the 412dp production screenshots confirms the Dashboard icon center moved from x=96px to x=72px (12dp left), approximately under avatar center x=70px. Qur'an also centers at x=72px. Header, footer and the right-side action/count regions compare pixel-identically before/after. Representative final light/dark/OLED screenshots were inspected at all four widths.

## N. Build And Tests

- JBR 21.0.11.
- Debug unit suite: 377 passed; no failures/errors/skips.
- Release unit suite: 377 passed; no failures/errors/skips.
- Debug and instrumentation APK builds passed.
- Previous layout native suite: 17/17 at 412dp, plus 3/3 at each other width and two separate cold-start persistence phases.
- Final alignment: 11 drawer/profile/Reflections tests passed at 412dp. An incorrectly qualified RootNavigationTest invocation produced a class-loading error; rerunning the correct ui.navigation class passed all six root tests. This was a test command error, not an app failure.
- Final alignment other-width checks: 3/3 at each of 360, 390 and 430dp. In total, all 26 selected native tests pass after correcting the test-class invocation.
- Committed source debug/release unit suites: 377 each, zero failures/errors/skips. Final debug/release R8 build and lint passed in 6m13s (`committed-build.log`). Lint reports 288 warnings and one hint, no errors.
- Historical `attestReleaseArtifact` / `verifyBuildIsolation` tasks are not present in this checkout. Exact source commit/tree, clean tracked worktree, package metadata, signature, alignment and APK checksum are recorded instead; no custom attestation task was added.
- Whitespace checks passed; protected paths unchanged.

## O-P. Version Control

Focused implementation commit: `04eced622808645083bb27d8627625e3deaa7b3c`.
Tree: `42bc554498cfc2b6e11addff1b04aebb0c06baa7`.
Pushed to `origin/frozen-design-master-port`; remote readback confirms the exact commit.

No completion tag: user explicitly requires Samsung verification before tagging completion.

## Q. Remaining Acceptance

Physical Samsung gestures, real Google profile photo and safe real-account A/B switching remain pending. No existing Drive files were deleted, moved or overwritten.

## Signed APK Delivery

- File: `release/MyVault-0.1.0-PRE-RELEASE-04eced6-Drawer-Refinement-signed.apk`
- Package: `com.myvault.app`, version 0.1.0 / code 1, minimum SDK 29, target SDK 36.
- APK signature v3 verifies; 16KB zip alignment verifies.
- Signer SHA-256: `5d33f907db32d404352fc160fbd0e7b27d648a50f97c648536211d68cf5e80a3`, identical to prior signed release `6aaf610`.
- The saved default signing configuration used a different certificate. Existing keystores were checked and the matching production certificate was selected; no key or global configuration was changed.
- APK SHA-256: `15f596f4e508198a47b67794ef0c6bca73e5b402ff81acbeb0b844256f2a7b40`.
- Size: 54,173,337 bytes. Google Drive metadata readback confirms identical size, exact filename, MIME type and destination folder. The connector does not return checksum fields, so no independent cloud checksum comparison is claimed.
- Upload: https://drive.google.com/file/d/1phJs4km5dLKy0XgEQyVPFFtnCSqeHAhe/view?usp=drivesdk
- Folder: MyVault APKs (`1RlSgXtDFX6F1BGAuS0zri56eal0yKO_1`). New-file upload only; no sharing changes.
- Tracked worktree is clean after delivery. Local screenshots, logs, signing helper and this report remain outside the commit. No completion tag was created while Samsung acceptance is open.
