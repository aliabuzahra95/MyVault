# Ask AI Decommissioning — Stage 0 Baseline Report

Date: 12 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Baseline stabilisation and protection of retained note-formatting workflows

## Stage result

Stage 0 is complete. The restored pre-tutor baseline is now internally consistent and has automated protection for the AI functionality that must survive Ask AI decommissioning.

- Full unit suite: 119 passed, 0 failed, 0 errors, 0 skipped.
- Debug Kotlin compilation: passed.
- Debug APK assembly: passed.
- Patch whitespace validation: passed.
- Database migration-chain expectation: corrected to version 26.
- Ask AI features removed: none.
- Database tables changed or deleted: none.
- Navigation changed: none.
- Provider configuration changed: none.
- Stage 1 work: not started.

## Baseline correction

The application already declared Room database version 26 and registered migration `25 -> 26`, which creates the existing `library_pdf_text_cache` table. The migration-chain test still expected version 25, causing the only pre-existing test failure.

The test expectation now matches the real database schema at version 26. No migration logic or stored user data was altered.

## Retained formatting contract

`NoteFormattingContract` now identifies the exact editor-output actions approved to survive conversational Ask AI retirement:

- Structure Only;
- Intelligent Structure;
- Clean Format;
- Format Note.

The production `NoteAiRepository.generate` path now passes generated editor output through this contract. The behavior is equivalent to the prior path: output is cleaned, and Structure Only receives the existing lossless-content validation and fallback.

Conversational actions are rejected by this boundary. This gives the later extraction into a dedicated `NoteFormattingRepository` a small, verified seam instead of requiring another broad rewrite of untested behavior.

## Automated protection added

The new `NoteFormattingContractTest` verifies:

1. The retained allowlist contains only the four approved formatting actions.
2. Ask and Study Tutor cannot enter the formatting-only contract.
3. Shortened Structure Only output is rejected.
4. Every important English source segment is recovered by the lossless fallback.
5. Arabic source text is preserved.
6. Complete, safe editor HTML remains usable.
7. Intelligent Structure removes markdown code fences and returns editor HTML.
8. Long notes are split into bounded chunks no larger than 25,000 characters.
9. The first and final content of long notes remain represented across chunking.
10. Gemini, ChatGPT and Kimi formatting prompts all require editor HTML and retain the note body.

## Logging portability fix

The lossless Structure Only fallback previously called Android logging directly when rejecting unsafe output. That call crashes in ordinary JVM tests because Android's `Log` implementation is unavailable there.

The diagnostic log is now best-effort. Android debug builds still receive it, while a missing logging runtime can no longer interrupt the content-preservation fallback. Formatting behavior and user-visible output are unchanged.

## Files changed

- `app/src/main/java/com/myvault/app/data/repository/NoteAiRepository.kt`
- `app/src/test/java/com/myvault/app/data/local/VaultMigrationChainTest.kt`
- `app/src/test/java/com/myvault/app/data/repository/NoteFormattingContractTest.kt`
- `docs/ASK_AI_DECOMMISSION_STAGE_0_REPORT.md`

## Verification commands

Focused contract gate:

```text
./gradlew testDebugUnitTest \
  --tests 'com.myvault.app.data.local.VaultMigrationChainTest' \
  --tests 'com.myvault.app.data.repository.NoteFormattingContractTest' \
  --no-daemon

BUILD SUCCESSFUL
8 tests passed
```

Complete project gate:

```text
./gradlew testDebugUnitTest compileDebugKotlin assembleDebug --no-daemon

BUILD SUCCESSFUL
119 tests passed
```

## Known limitations carried into Stage 1

1. `NoteAiRepository` still combines formatting and conversational provider behavior.
2. `AiPromptBuilder` still combines editor-output and chat prompts.
3. Note chat state and formatting state remain intertwined in `NoteViewModel`.
4. The editor still presents broad Ask AI interfaces.
5. No live paid-provider requests were sent during this baseline gate.
6. Editor undo/autosave behavior was compile-verified but not manually exercised on a physical device during Stage 0.

These are expected Stage 1 and later concerns. They do not weaken the new pure formatting-output tests.

## Scope protection

- No Ask AI launcher was hidden or removed.
- No conversation history was deleted.
- No Room entity or backup entry was changed.
- No Supabase function was changed.
- No provider key, model, credential or security configuration was changed.
- No narration, document extraction, PDF, Qur'an speech, search or knowledge-management code was changed.
- No automatic project backup was created.

## Stage decision

The restored baseline is green and the retained formatting behavior has a direct automated contract. It is now safe to begin Stage 1: extracting the formatting-only architecture without changing the visible editor experience.

Implementation stops here. Stage 1 has not begun.
