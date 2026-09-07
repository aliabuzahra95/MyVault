# PDF hardening - Android, 2026-09-07

## Status and recovery

Implementation and host checks passed. Final acceptance remains OPEN for Samsung touch behavior and an authenticated disposable Web backup -> Android restore. No physical phone was connected; no live production backup or Room row was inspected. No Drive file was written, deleted or moved.

- Repository: `aliabuzahra95/MyVault`, branch `frozen-design-master-port`.
- Starting commit: `3ba4153b6629f47b49ac288cb97aa733df6e0b4b`.
- Pushed recovery tag: `pre-pdf-hardening-20260907-android-3ba4153`.
- Web starting commit: `d2f1f1959a7c5f0a9c90aa874227f64cff0c7f12`.
- Web recovery tag: `pre-pdf-hardening-20260907-web-d2f1f19`.
- Web implementation: `5e70b99e54615aa175853308965745dd3dd592d4`; separate repository and commit.
- Existing untracked artifacts and signed-APK sidecars were preserved and excluded from this commit.

## Cause and narrow correction

The production annotations sheet conditionally re-enabled Material sheet gestures when the list stopped scrolling at its top. This still allowed sheet/list gesture ownership to change. It did not implement the requested tap-only behavior. Separately, raster rows grew from a short loading label to a rendered image, which changed lazy-list measurements while previews arrived. These are identified sources of sheet motion and list jumps; they are not a claim that the disconnected Samsung's exact oscillation was captured.

`FrozenLocalPdfActivitySheet` now permanently disables sheet gestures. A 40dp handle target explicitly toggles expanded/partially-expanded state on tap, ignoring taps while a transition is in progress. Initial partial height and the existing sheet content height are preserved. The list retains its stable annotation-ID keys and disabled elastic overscroll; it alone owns its scroll gestures.

Raster loading, unavailable and successful states reserve the same 170dp preview area. Images fit within it rather than changing the row height. Saved-text previews, annotation geometry, edit/delete and exact-page actions are unchanged. Two presentation enums and the sheet composable are internal instead of private solely to exercise the real sheet from instrumentation.

## Restored annotation consistency

The active `AttachmentViewerScreen` uses `FrozenPdfReaderScreen`. Its shared supported-annotation projection feeds the PDF renderer, toolbar count, sheet and Activity view. Existing geometry/support tests cover absolute Web rectangles, geometry-only parent plus segments, missing selected text, multi-page segments, supported highlight/note membership and unknown-type rejection. Stored zero-based page 11 is displayed as page 12.

No new production mapping defect was established, so no annotation repository or schema change was made. Dormant legacy viewer code was not edited. Tests/source inspection do not prove that an actual user's restored annotation appears in every surface. A disposable authenticated round-trip with record IDs, stored page/rectangle and screen comparisons remains required.

## Verification

- JBR 21.0.11 used for all Gradle checks.
- Baseline relevant PDF unit checks and debug build passed.
- Full unit suite, lint, debug and release/R8 builds passed. Final separated host command: `./gradlew test lint assembleDebug assembleRelease assembleDebugAndroidTest --console=plain` (20 seconds, 159 tasks).
- Unit contract test updated for permanently disabled gestures, tap expansion/collapse, transition guard and fixed preview area.
- Instrumentation uses the production sheet with 45 disposable in-memory annotations and a generated cache PDF, not user database rows. It injects actual touch events: partial -> tap expanded -> header swipe -> 18 fast bottom flings -> 12 stable-bound samples -> each filter -> tap partial. It checks sheet, footer and last-row bounds, plus footer reachability.
- Earlier emulator runs passed. A later combined Gradle instrumentation run failed to attach (zero tests); another installer attempt failed. These are not counted as passing runs. Host checks were rerun separately and both APKs installed successfully before the final direct instrumentation run.
- Final direct emulator instrumentation passed: `OK (1 test)`, 29.971 seconds, API 36.1. It exercised all tap/scroll/filter/last-row stability assertions described above.
- No backup/restore, Room, IDs, geometry representation, source backlinks, OAuth, Drive architecture, Quran/widget or unrelated UI changes.

## Visual evidence and limitations

Emulator screenshots: `/data/local/tmp/pdf-half.png`, `pdf-expanded.png`, `pdf-bottom.png`. Host bottom screenshot: `/tmp/android-pdf-bottom.png`. The bottom screenshot was visually inspected: final annotation and View all activity footer are visible without overlap. The generated fixture intentionally contains mostly blank pixels in its crop region.

Samsung fast-fling acceptance, user-data parity, real Drive round-trip and physical frame/latency measurements remain unverified. No signed APK or Drive upload was performed in this PDF-hardening task. See the Web repository's same-named report for measured PDF reopen timings, raster source-pixel comparison, browser memory/window limits and responsive screenshots.
