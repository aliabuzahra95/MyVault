# MyVault Current-State Remediation - Phases 1-3

Date: 2026-08-29 AEST

Repository: `/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`

Branch: `frozen-design-master-port`

Starting commit: `24c078cb45cc312217c47323e6526368859008c7`

Recovery tag: `current-state-remediation-start-20260829` (pushed)

## Scope

This remediation changed only:

1. Dashboard-rooted navigation;
2. active Study/Course folder entry routes;
3. device-local Google Drive comparison metadata isolation;
4. the explicitly allowed trivial Global Search ancestry cleanup.

It did not change Room, backup payloads, Drive manifest/layout, canonical Qur'an data, production repositories, local vault contents, signing, or release artifacts.

## Phase 1 - Root navigation

### Cause

Dashboard was already the graph start, but callbacks still called `popBackStack("home", false)`. A normal Dashboard stack did not contain Home, so the pop returned false and the requested destination never opened.

### Fix

- The presentation host formerly named `Home` is now named `Knowledge`; its compatibility route string remains `home` so restored old navigation state is not broken.
- `navigateToVaultRoot` gives Dashboard, Knowledge, Search, and Settings one Dashboard-rooted, single-top strategy.
- No hidden Home route, startup redirect, delay, fake splash, or animation suppression was introduced.
- Exact Qur'an and Reflection targets set the existing pending verse and enter the Knowledge/Qur'an root explicitly.
- Search Course results select the Course before entering Knowledge/Courses.
- Note deletion removes a stacked Reader when necessary, otherwise returns to the surviving parent, with Dashboard as the final safe fallback.

### Verification

- Real `TestNavHostController` instrumentation covers root replacement, duplicate prevention, Reader/Editor delete completion, and surviving-parent behavior.
- Installed emulator runtime passed cold Dashboard launch and Dashboard -> Study, Library, Courses, Qur'an, Memorise, and Dashboard return.
- Dashboard Qur'an Continue opened the exact saved `Al-Faatiha 1:1` target rather than the picker.
- Dashboard Reflection, Reflections Hub, and Search Course fixtures were unavailable in the controlled emulator dataset; their callbacks now use the same verified helper and exact-target state.
- The connected Samsung production installation was not replaced because its signing certificate differs from the debug certificate. Uninstalling it would have risked user data.

Runtime captures: `/tmp/myvault-remediation/phase1/`.

## Phase 2 - Study and Course folder routing

### Cause

Explorer and Search folder callbacks navigated to the standalone generic `FolderView` route. That exposed the old third folder-browser presentation instead of the approved embedded Study or Course hierarchy.

### Fix

- Study/Personal direct folder entry now finds the complete real ancestor path, expands it in one preference update, selects the correct workspace/root, and opens the compact Corpus Browser.
- Course direct folder entry derives its owning Course from the Explorer tree, expands the Course folder path, selects that Course, and opens the approved Course detail.
- Course Note entry selects its Course context before opening the Stage 4 Note Reader/Editor.
- Active Study/Personal/Course callbacks no longer navigate to standalone `FolderView`.
- The generic composable remains isolated only for compatibility with restored old navigation state and its own internal legacy sub-navigation; no active current caller reaches it.

### Runtime verification

- Controlled Study fixture: `Parent -> Child/Cild`. Direct Explorer selection opened Study with the parent and selected child expanded. No generic breadcrumb, duplicate Menu/Back header, timestamps, or blue legacy folder screen appeared.
- Controlled Course fixture: `CourseA -> CourseFolder -> Inner`. Direct Explorer selection opened CourseA's approved detail with both ancestors expanded.
- A Course Note created in `Inner` opened the Stage 4 editor and Back returned to exact CourseA detail with `CourseFolder -> Inner` intact.
- Explorer retained the selected-node ancestor expansion.

Runtime captures: `/tmp/myvault-remediation/phase2/`, notably:

- `09-study-deep-direct.png`
- `10-study-explorer-ancestors.png`
- `14-course-deep-direct.png`
- `16-course-note-after-back.png`

## Phase 3 - Google Drive account isolation

### Cause

Drive API folders were rediscovered for the signed-in account, but `lastGoogleDriveSyncAt` and `lastGoogleDriveManifestAt` were one global pair. Account B could therefore inherit account A's comparison state.

### Device-local metadata strategy

- A new device-local DataStore set maps normalized Google account email to that account's last successful sync and manifest timestamps.
- Public UI fields continue to expose only the currently active account's values.
- On A -> B, B receives only B's known metadata; an unknown B receives zero/unknown state and must revalidate Drive.
- On B -> A, A's known scoped metadata is restored.
- Existing global timestamps are deliberately not assigned to the account active during upgrade because ownership cannot be proved.
- The legacy scalar keys are retained as an active-account mirror for local downgrade tolerance, but current comparison logic never uses them.
- Push, pull, and update checks obtain the actual current Google account before comparison and pass that account identity when recording success.
- Starting Change account clears the active identity/display while preserving each account's scoped history.
- No Drive file/folder IDs or backup listing are cached across accounts; every operation continues to rediscover the current account's Drive hierarchy.

### Data and conflict semantics

- Local Notes/PDFs are untouched by Google account changes. Google account identity is not treated as ownership of the local Room vault.
- A remote version blocks a normal push only when it is newer than the current account's own known manifest.
- Account A's version has zero influence on B's conflict or up-to-date decision.
- Pull records the restored manifest only for the account from which it was fetched.
- Backup format, Drive manifest, restore rules, and Room schema are unchanged.

### Verification

- Unit tests cover A/B encoding, A -> B isolation, B -> A restoration, case-normalized identity, malformed entries, unknown accounts, conservative legacy-global migration, and account-specific remote-newer comparison.
- Device-side DataStore instrumentation covers A -> B -> A, independent values, unknown-account zero state, and intentional sign-out.
- Six instrumentation tests passed in total, including the five root/back-stack tests.
- No disposable authenticated A/B Google account pair was available, so live cloud discovery/upload/restore switching was not performed.
- No destructive restore or user account change was performed on the connected Samsung phone.

## Optional P3 cleanup

Global Search now derives a real note location from all active folders:

- nested example: `Study / Quranic Lessons / Reflections`;
- unfiled fallback: `Study / Unfiled`;
- Personal, Library, and Course folder modes use their real root label.

The Reflections Hub already had one Back control in current source, so it was not modified.

## Incoming share

The incoming `ACTION_SEND` path still imports asynchronously and sets `pendingSharedNoteId` after the normal host mounts. Its first-frame issue remains open and was not changed by this remediation.

## Final automated gate

Environment: JBR 21.0.11.

- Debug unit tests: 214 tests, 0 failures.
- Release unit tests: 214 tests, 0 failures.
- Android instrumentation: 6 tests, 0 failures on `Medium_Phone_API_36.1`, API 36.
- `lintDebug`: PASS, 0 errors; 69 warnings and 2 hints remain maintenance debt.
- `assembleDebug`: PASS.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Debug APK SHA-256: `9acf8833dbb142a5f465b293ea39cc4713bf470aa1caf5b36c4d0933af048221`.
- `git diff --check`: PASS.

## Remaining gates

- Live disposable Google account A/B/A discovery, conflict, push target, and restore listing.
- Incoming-share first-frame remediation.
- A traceable signed build for physical-device source parity.
- The broader product/release regression remains outside this focused task.
