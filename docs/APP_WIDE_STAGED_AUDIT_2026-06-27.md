# MyVault App-Wide Staged Quality Audit

Date: 2026-06-27

Baseline path: `/Users/aliah/Desktop/MyVault - Complete BACKUP BEFORE AI`

Safety backup for this staged pass:

- `/Users/aliah/Desktop/MyVault - Complete BACKUP BEFORE APP WIDE STAGES 2026-06-27`
- Source-level backup created with `.git/` excluded.

## Stage Summary

### Stage 1: Shared Design Language

Completed.

App-wide shared UI primitives were refined instead of redesigning individual screens one by one.

Changed shared pieces:

- `IconBtn`
- `SettingsRow`
- `SearchBar`
- `WorkspaceHeader`
- `VaultModal`

Result:

- More consistent button shape, elevation, and active states.
- Settings rows now have clearer icon hierarchy and boundaries.
- Search and workspace controls feel more deliberate and premium.
- Common bottom sheets have a clearer handle and close affordance.

Risk level: Low. These are visual component changes only.

### Stage 2: Backup and Restore Reliability

Completed.

Root finding:

- Backup export verification already validated settings.
- Restore converted `settings.json` directly into preferences without first applying the same validation.

Fix:

- Restore now validates backed-up settings before applying preferences.
- Added unit tests for valid settings and corrupt settings cases.

New coverage:

- Valid settings conversion.
- Invalid Qur'an bookmark rejection.
- Mismatched Qur'an memorisation record rejection.
- Unsafe library display scope rejection.
- Duplicate expanded folder state rejection.

Risk level: Low to medium. The behaviour is stricter only for invalid or unsafe backup settings.

### Stage 3: AI Surface Unification

Completed.

Root finding:

- Home AI and standalone Ask AI already use the shared rich Markdown renderer from the Kimi formatting pass.
- Editor note AI chat bubbles and selected-text AI results were still rendered as plain text.

Fix:

- Editor assistant messages now use the shared rich Markdown renderer.
- Selected-text AI results now use the shared rich Markdown renderer.
- User messages and errors remain plain and direct.

Result:

- OpenAI, Gemini, and Kimi answers now have a more consistent reading experience across the main AI surfaces.

Risk level: Low. Rendering changed, not AI request logic.

### Stage 4: Audit and Safe Optimisation

Completed for this pass.

Evidence collected:

- Main Kotlin source line count: 52,058 lines.
- Largest files:
  - `QuranShellScreen.kt`: 3,898 lines.
  - `AttachmentViewerScreen.kt`: 3,402 lines.
  - `EditorScreen.kt`: 3,033 lines.
  - `LibraryScreen.kt`: 2,176 lines.
  - `HomeScreen.kt`: 1,898 lines.
  - `NoteAiRepository.kt`: 1,852 lines.
  - `BackupRepository.kt`: 1,772 lines.
  - `VaultNavHost.kt`: 1,539 lines.

Safe optimisations applied:

- Reused a PDF drag-preview rectangle instead of allocating a new `RectF` during every draw frame.
- Switched audio slider state and root navigation state to primitive Compose state:
  - `mutableFloatStateOf`
  - `mutableIntStateOf`
  - `mutableLongStateOf`
- Updated test-only JSON dependency to the current version reported by lint.

## Verification

Passed:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`

Notes:

- Lint now passes.
- Remaining lint output is warning-level or hint-level, mostly dependency updates, modifier ordering, obsolete SDK checks, monochrome launcher icon metadata, and KTX suggestions.

## Current Risk Map

### Highest Product Risks

1. Large UI files make visual and behavioural changes harder to verify.
2. Backup/restore remains critical and should gain more end-to-end round-trip tests before any refactor.
3. AI logic is split between Home AI and note-level AI repositories.
4. `VaultNavHost.kt` owns too much route wiring and screen state collection.
5. Library and PDF workflows combine file operations, metadata, annotations, and UI state in large surfaces.

### Highest Performance Risks

1. Large Compose screens increase recomposition blast radius.
2. Whole-table reads are common in repository-level workflows.
3. Backup, Drive sync, and PDF operations process broad datasets.
4. PDF annotation rendering still performs coordinate conversions during draw.
5. Quran reader and audio surfaces have many stateful UI regions inside one screen file.

### Highest Test Gaps

1. Full backup export and restore round trip with real Room database data.
2. Library PDF import, open, annotate, backup, restore, and reopen.
3. AI provider rendering snapshot or parser tests.
4. Navigation smoke tests for core destinations.
5. Quran reading, bookmarking, memorisation, and audio smoke tests.

## Recommended Next Stages

### Next Stage A: Backup Round-Trip Tests

Add instrumentation or Robolectric-backed tests for:

- Folders.
- Notes.
- Rich text blocks.
- Tables.
- Tags.
- Attachments with real files.
- PDF reading progress.
- PDF page notes and highlights.
- Preferences.

Do this before splitting `BackupRepository.kt`.

### Next Stage B: Split Large Screens Safely

Suggested order:

1. Extract pure UI components from `SettingsScreen.kt`.
2. Extract PDF toolbar/activity sheet components from `AttachmentViewerScreen.kt`.
3. Extract AI sheet components from `EditorScreen.kt`.
4. Extract root shell/navigation UI from `VaultNavHost.kt`.
5. Split Quran sheets only after smoke tests exist.

### Next Stage C: AI Cleanup

Recommended:

- Keep the shared `RichMarkdownText` renderer as the one response renderer.
- Add parser tests for headings, lists, tables, quotes, and code blocks.
- Consider shared provider/model selector UI for Home AI and note AI.
- Avoid merging note-level AI and Home AI request logic until backup and editor tests are stronger.

### Next Stage D: Performance Instrumentation

Add lightweight timing logs around:

- Backup export.
- Backup restore.
- Drive push and pull.
- PDF open.
- Library folder hydration.
- Quran search and audio download list loading.

Keep logs debug-only.

## Bottom Line

This pass improved the app's shared visual language, tightened backup restore validation, unified remaining AI response rendering, and removed several small performance warnings. The app now passes unit tests, lint, and debug assembly after these changes.
