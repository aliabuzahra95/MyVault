# Ask AI Decommissioning — Stage 1 Formatting Architecture Report

Date: 12 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Extract the retained note-formatting application boundary without changing visible behavior

## Stage result

Stage 1 is complete. Structure Only, Intelligent Structure, Clean Format and Format Note now enter a dedicated formatting-only application service before reaching the proven provider transport.

- Full unit suite: 127 passed, 0 failed, 0 errors, 0 skipped.
- Debug Kotlin compilation: passed.
- Debug APK assembly: passed.
- Patch whitespace validation: passed.
- Visible editor behavior changed: no.
- Ask AI features removed: none.
- Database or backup format changed: no.
- Provider credentials or models changed: no.
- Stage 2 work: not started.

## New formatting-only architecture

### Independent models

The retained workflow now has its own types:

- `NoteFormattingAction`
- `NoteFormattingProvider`
- `NoteFormattingModel`
- `NoteFormattingRequest`
- `NoteFormattingResult`
- `NoteFormattingException`

These types contain no chat messages, conversation IDs, tutoring state, selected-text state, history, continuation or response-narration behavior.

### Provider-neutral repository

`NoteFormattingRepository` is now the application boundary used by the editor.

It owns:

- empty-note validation;
- provider-neutral request dispatch;
- progress forwarding;
- whitespace normalization at the result boundary;
- empty-output rejection;
- formatting-specific failure wrapping.

It depends only on `NoteFormattingGenerator`, not on the conversational models or repositories.

### Temporary legacy adapter

`LegacyNoteFormattingGenerator` is an explicit transitional adapter around the existing, proven provider transport in `NoteAiRepository`.

Only this adapter imports:

- `NoteAiRepository`;
- legacy note AI actions;
- legacy provider and model enums.

The adapter maps the four retained actions, three providers and Fast/Smart models exactly, with no fallback or substitution. This preserves current Gemini, ChatGPT and Kimi behavior while allowing later stages to replace the adapter without changing the editor or formatting repository.

This is deliberate strangler architecture: the new system is already the editor-facing contract, while the old transport remains behind one replaceable seam until provider extraction is safe.

## Editor routing

`NoteViewModel.runAiTool` still accepts the current UI contract so the interface remains unchanged.

Its behavior is now split:

- conversational actions continue through `NoteAiRepository.generateStreaming` and conversation persistence;
- editor-output actions map into `NoteFormattingRequest` and run through `NoteFormattingRepository`;
- editor-output results still update the existing UI state and are applied through the existing editor workflow;
- editor-output actions still do not create conversation records.

No chat history, selected-text context or conversation turn is passed into the formatting repository.

## Dependency injection

`NoteFormattingModule` binds the provider-neutral `NoteFormattingGenerator` interface to the temporary `LegacyNoteFormattingGenerator` implementation.

The binding can later be replaced by a native formatting provider gateway without changing `NoteViewModel`, editor screens or formatting tests.

## Verification added

### Repository behavior

`NoteFormattingRepositoryTest` verifies:

1. Requests cross the boundary unchanged.
2. Progress labels are forwarded in order.
3. Results are trimmed and returned as editor HTML.
4. Every retained action works with every retained provider at the boundary.
5. Empty notes are rejected before provider invocation.
6. Empty provider responses become formatting-specific failures.
7. Provider errors retain their useful message and cause.

### Legacy transport mapping

`LegacyNoteFormattingMappingTest` verifies:

1. All four actions map exactly to the existing transport actions.
2. Gemini maps to Gemini.
3. ChatGPT maps to ChatGPT.
4. Kimi maps to Kimi.
5. Fast and Smart models map without silent provider-model substitution.

### Retained Stage 0 protection

The earlier formatting contract tests continue to protect:

- lossless Structure Only fallback;
- Arabic preservation;
- safe HTML;
- markdown-fence cleanup;
- long-note chunking;
- provider prompt contracts;
- conversational-action rejection.

## Files introduced

- `app/src/main/java/com/myvault/app/data/formatting/NoteFormattingModels.kt`
- `app/src/main/java/com/myvault/app/data/formatting/NoteFormattingRepository.kt`
- `app/src/main/java/com/myvault/app/data/formatting/LegacyNoteFormattingGenerator.kt`
- `app/src/main/java/com/myvault/app/di/NoteFormattingModule.kt`
- `app/src/test/java/com/myvault/app/data/formatting/NoteFormattingRepositoryTest.kt`
- `app/src/test/java/com/myvault/app/data/formatting/LegacyNoteFormattingMappingTest.kt`
- `docs/ASK_AI_DECOMMISSION_STAGE_1_REPORT.md`

## Files extended

- `app/src/main/java/com/myvault/app/ui/viewmodel/NoteViewModel.kt`
- Stage 0 files and tests remain in place.

## Final verification

```text
./gradlew testDebugUnitTest compileDebugKotlin assembleDebug --no-daemon

BUILD SUCCESSFUL
127 tests, 0 failures, 0 errors, 0 skipped
```

## Architectural audit

The formatting package has a controlled dependency shape:

- models import no legacy AI types;
- `NoteFormattingRepository` imports no legacy AI types;
- only `LegacyNoteFormattingGenerator` imports the old repository and enums;
- the editor depends on `NoteFormattingRepository` for retained actions;
- the old conversational path remains independently callable until later removal.

## Known limitations carried forward

1. Provider HTTP/Firebase/Supabase transport still physically lives inside `NoteAiRepository`.
2. Formatting sanitizers and long-note orchestration still physically live in the legacy repository file.
3. `NoteViewModel` and `NoteAiUiState` still contain both chat and formatting presentation state.
4. The visible editor still presents Ask AI and provider/model controls.
5. Smart formatting is still subject to the existing `fastForLightweight` selection rule; Stage 1 intentionally did not change provider behavior.
6. No paid live-provider requests were sent during this architectural extraction.

The first two limitations are isolated behind the adapter and can be addressed after the visible Ask AI retirement without destabilizing the editor.

## Scope protection

- No Ask AI launcher or route was removed.
- No chat history was deleted or changed.
- No provider request body was changed.
- No Supabase function was changed.
- No database table, migration or backup entry was changed.
- No narration, PDF, search, Qur'an speech or knowledge-management behavior was changed.
- No automatic project backup was created.

## Stage decision

The editor now has a tested formatting-only application boundary and no longer invokes the conversational repository directly for retained actions. Provider behavior remains stable through one explicit temporary adapter.

It is safe to begin Stage 2: retire Ask AI from the visible interface while keeping the dormant runtime and data stores intact for rollback.

Implementation stops here. Stage 2 has not begun.
