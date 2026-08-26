# Stage 1 Verification: Global Shell and Explorer

Date: 2026-08-26

Branch: `frozen-design-master-port`

## Scope

This checkpoint contains only the approved Stage 1 work:

- the shared production shell and Explorer drawer;
- Explorer navigation for Dashboard, Search, Settings, Courses, Study,
  Library, Qur'an, and Memorise;
- expandable production hierarchy rows;
- existing creation and contextual-action wiring;
- the approved profile/header workspace chooser amendment;
- removal of generic page transition animations.

Dashboard, global Search, narration, and all feature screens retain their
existing production presentation. Stage 2 has not started.

## Frozen References

- `01-explorer-study-expanded.png`
- `02-explorer-library-expanded.png`
- `03-explorer-courses-expanded.png`

Frozen reference viewport: 412 x 892.

Android validation device: `Medium_Phone_API_36.1`, 1080 x 2400 at 420 dpi,
approximately 411 x 914 logical dp including native system bars.

## Android Captures

- `android-explorer-study-final.png`
- `android-explorer-personal-final.png`
- `android-workspace-chooser.png`
- `android-explorer-add-sheet.png`
- `android-explorer-context-sheet.png`
- `stage1-explorer-side-by-side.png`

## Visual Comparison

Confirmed against the frozen Explorer reference:

- 366 dp maximum drawer width with the approved 46 dp revealed-page edge;
- compact profile/header composition and close action;
- `APPLICATION` and `KNOWLEDGE` section hierarchy;
- compact application, root, folder, and leaf row rhythm;
- subtle hierarchy indentation;
- neutral outlined hierarchy icons with restrained accent use;
- no filled selected-navigation cards;
- no row dividers or per-row overflow buttons;
- root and folder add actions;
- fixed Drive/theme/account footer without a strong divider;
- modal scrim and native edge-swipe drawer behavior.

The workspace chooser uses the approved production-side amendment and is not a
Frozen Design Master discrepancy.

## Functional Checks

Passed on the emulator:

- edge swipe opens the Explorer and close dismisses it;
- Dashboard opens the unchanged production Dashboard destination;
- global Search opens the unchanged production Search destination;
- Islamic Corpus and Personal selection invokes the existing workspace switch;
- the Explorer header updates to the selected production workspace;
- Study hierarchy expands and displays a newly created production folder;
- Study root `+` opens the shared `Add to Study` sheet;
- folder long press opens the existing Open/New note/New folder/Rename/Move/Delete actions;
- drawer dismissal and Back behavior work without a generic page animation;
- existing feature screens and production data engines were not rewritten.

## Build and Tests

Command:

```text
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
```

Result: `BUILD SUCCESSFUL`.

Unit tests: 169 passed, 0 failed, 0 skipped.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Approved or State-Driven Differences

- Native Android status and navigation bars reduce the app content height
  compared with the 412 x 892 prototype viewport.
- The clean emulator has a fallback account avatar and only the temporary
  `Stage1Test` folder, so it cannot reproduce the reference account name and
  restored hierarchy content. The shared row geometry was verified in the
  production hierarchy that is available.
- Dashboard and global Search remain intentionally legacy under the new shell.
- The screen behind the drawer remains intentionally legacy until its approved
  implementation stage.
- The production narration mini-player remains unchanged. No Stage 1 collision
  was encountered while narration was inactive.

## Stage Boundary

No Study, Library, Settings, editor, PDF, Courses, Qur'an, or Memorise screen
redesign has been implemented. Stage 2 requires explicit approval.
