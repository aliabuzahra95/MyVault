# Ask AI Decommission — Stage 5 Report

Date: 12 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Extract the retained formatting provider transport from the legacy note-conversation repository

## Outcome

Stage 5 is complete. Structure Only, Intelligent Structure, Clean Format and Format Note no longer invoke or inject `NoteAiRepository`.

The retained editor workflows now use a native formatting-only orchestrator and provider gateway. The legacy conversation repository still exists in source for the next removal stage, but it is no longer on the production formatting dependency path.

## New native formatting architecture

### `NativeNoteFormattingGenerator`

The new orchestrator owns only retained editor formatting:

- formatting requests;
- formatting provider and Fast/Smart model selection;
- long-note structure planning;
- bounded 25,000-character chunk processing;
- progress labels;
- coherent chunk merging;
- final lossless Structure Only validation;
- formatting debug traces.

It contains no:

- chat messages;
- conversation IDs;
- history;
- tutoring state;
- selected-text AI actions;
- continuation behaviour;
- conversational streaming state.

### `NoteFormattingProviderGateway`

The provider gateway now owns the three retained transports directly:

- Gemini through Firebase AI;
- ChatGPT through the existing authenticated Supabase function;
- Kimi through Moonshot's OpenAI-compatible endpoint.

The gateway accepts formatting-only request and prompt models. It does not depend on `NoteAiRepository`.

### Provider request preservation

ChatGPT retains:

- `format_note` for Structure Only and Format Note;
- `organise` for Intelligent Structure and Clean Format;
- Fast/Smart model selection;
- the existing Supabase authentication and session-refresh behaviour;
- the same timeouts and formatting metadata.

Kimi retains:

- the configured Kimi Fast/Smart model IDs;
- `kimi-k2.6` temperature `0.6` when that model is selected;
- `thinking.type = disabled`;
- non-streaming formatting responses;
- the exact system/user message structure;
- existing API-key handling and friendly failures.

Gemini retains:

- Gemini 2.5 Flash for Fast;
- Gemini 2.5 Pro with the existing Flash fallback for Smart;
- existing temperature, top-p and output-token values;
- partial-output handling when Gemini reaches its output limit.

## Removed

- `LegacyNoteFormattingGenerator.kt`.
- The dependency-injection binding from formatting to the legacy adapter.
- The obsolete legacy-adapter test filename and class.

## Dependency injection

`NoteFormattingModule` now binds:

- `NativeNoteFormattingGenerator` to `NoteFormattingGenerator`;
- `DefaultNoteFormattingProviderGateway` to `NoteFormattingProviderGateway`;
- `DefaultNoteFormattingTrace` to `NoteFormattingTrace`.

No production formatting binding references the conversation repository.

## New verification

`NativeNoteFormattingGeneratorTest` verifies:

1. Every retained action and provider crosses the native formatting gateway.
2. Formatting prompts remain editor-HTML prompts.
3. Structure Only still restores English and Arabic source content omitted by a provider.
4. Long notes use one structure plan followed by bounded chunks.
5. Long-note progress remains observable.
6. Kimi retains its proven message, model, thinking and temperature request shape.
7. ChatGPT payloads contain only retained formatting actions and Fast/Smart models.
8. Formatting payloads contain no conversation, history or Study Tutor state.

The decommission contract additionally verifies that:

- the native formatting source does not reference `NoteAiRepository`;
- dependency injection does not restore `LegacyNoteFormattingGenerator`;
- the deleted adapter file remains absent.

## Full verification

- Debug unit tests: **139 passed**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Debug Kotlin compilation: passed.
- Debug APK assembly: passed.
- Patch whitespace validation: passed.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

No paid live-provider request was sent during this stage.

## Explicitly protected

### Note formatting

- Structure & Format entry point.
- Run Structure Only.
- Run Intelligent Structure.
- Clean Format and Format Note contracts.
- Gemini, ChatGPT and Kimi.
- Fast and Smart selection.
- Preview, copy, insert and replace.
- Lossless fallback, Arabic preservation and long-note handling.

### Qur'an

- AI Listen.
- Recorder and playback.
- Google and OpenAI speech recognition.
- Analysis, comparison, scoring and persistence.
- Surah testing and memorisation dashboards.

### Storage

- No Room table or migration changed.
- No stored conversation was deleted.
- No backup field was removed.
- No provider credential or secret changed.

## Temporary compatibility seam

The new formatting orchestrator still calls the proven static formatting prompt and output contracts currently located beside the legacy note engine:

- `AiPromptBuilder` for formatting prompt construction;
- `NoteFormattingContract` for output cleaning, lossless validation and chunk splitting;
- small exact enum mappings from formatting models to the existing contract types.

This is not a runtime call to `NoteAiRepository`. It is the final compatibility seam needed to prove transport equivalence before moving the large sanitizer and prompt rules.

## Stage boundary

Stage 6 can now move the formatting prompt builder and output sanitizer into the formatting package, then remove:

- the legacy `NoteAiRepository`;
- conversational actions and prompt modes;
- note conversation and selected-text AI code from `NoteViewModel`;
- `AiConversationRepository` runtime usage.

Database entities, stored history and backup compatibility should remain until the later migration stage. Private keys and secret removal remain outside this work.
