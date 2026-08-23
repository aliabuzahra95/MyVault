# Ask AI Decommission — Stage 4 Report

Date: 12 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Remove unreachable conversational runtime and residual dead chat presentation while preserving database and backup compatibility

## Source-of-truth reset

The rejected UI/UX implementation was not carried forward.

The verified pre-UI/UX backup at:

`/Users/aliah/Desktop/MyVault-Backups/MyVault Complete Before Tutor - PRE UI UX IMPLEMENTATION - 2026-07-12 165722`

was copied back into the normal project location and became the sole implementation source. The rejected folder was moved to:

`/Users/aliah/Desktop/MyVault Complete Before Tutor - REJECTED UI UX - 2026-07-12`

The original backup remains unchanged.

Before Stage 4 changes, the restored source passed the complete debug unit suite and assembled a debug APK successfully.

## Outcome

Stage 4 removes the shared conversational runtime that became unreachable in Stages 2 and 3. It also removes residual, unmounted note-chat composables that remained inside the editor source.

This is still not the database deletion stage. Existing conversational records, tables, DAOs, Room schema history and backup import/export compatibility remain intact.

## Removed shared conversational runtime

- `HomeInlineAiClient.kt`
- `HomeInlineAiPromptBuilder.kt`
- `HomeInlineAiRepository.kt`
- `HomeInlineAiState.kt`
- `HomeInlineAiViewModel.kt`
- `HomeInlineAiClientTest.kt`

These files supplied general Home, Study, Library and Courses chat. Their visible launchers and panels were already removed, and no production screen referenced this runtime before deletion.

## Removed residual editor chat presentation

The following unreachable code was removed from `EditorScreen.kt`:

- the general `AskAiSheet` conversation screen;
- the selected-text Ask AI sheet;
- chat bubbles;
- conversational suggestion grids and action chips;
- selected-text AI insertion helpers;
- the `Send to chat` path;
- related dead presentation models and imports.

The editor continues to expose only the retained Structure & Format workflow.

## Compatibility models retained

`HomeAiStorageModels.kt` now contains only the small Room query projections still required while legacy tables remain in the schema.

The legacy `GEMINI` cache discriminator is represented as its stored string value rather than depending on the removed conversational provider enum.

## Explicitly protected

### Note formatting

- Structure & Format editor entry point.
- Run Structure Only.
- Run Intelligent Structure.
- Clean Format and Format Note application contracts.
- Gemini, ChatGPT and Kimi formatting providers.
- Fast and Smart formatting model selection.
- Formatting preview, copy, insert and replace.
- Lossless Structure Only validation and fallback.
- Arabic and long-note preservation tests.

### Qur'an memorisation

- AI Listen āyah action.
- `QuranAiListenSheet`.
- `QuranMemorizationRecorder`.
- OpenAI and Google speech-recognition paths.
- Memorisation comparison, scoring, analysis and attempt persistence.
- Surah testing and memorisation dashboard behaviour.

### Knowledge-management infrastructure

- PDF reading, highlights and annotations.
- Document text extraction used by the document viewer.
- Notes, folders, Library, Courses, search and narration.

## Deliberately retained for later stages

The following are not safe to delete in Stage 4:

- `AiConversationEntity` and `AiMessageEntity`;
- `HomeChatHistoryEntity`;
- `AiConversationDao` and the storage portions of `HomeChatHistoryDao`;
- existing Room tables and migrations;
- conversational fields in the current backup format;
- `NoteAiRepository` and `AiPromptBuilder`, because the temporary formatting adapter still uses their proven provider transport;
- provider credentials required by Structure Only and Intelligent Structure.

Deleting the storage items now would make existing databases or backups unreadable. Deleting the legacy note transport now would break retained formatting.

## Scale of removal

- Shared runtime and obsolete runtime test: 1,858 lines removed.
- Dead editor conversational presentation: 684 lines removed.
- Total removed in Stage 4: 2,542 lines.

## Regression protection

`AskAiSurfaceDecommissionContractTest` now also verifies:

- shared conversational runtime files remain absent;
- the editor cannot restore either dead chat sheet;
- selected-text Ask AI wording remains absent;
- legacy entities and DAOs remain present during the compatibility stage;
- Structure Only and Intelligent Structure remain visible;
- Qur'an AI Listen remains reachable with its recorder and speech-provider integration.

## Stage boundary

Stage 5 should extract the retained provider transport into a native formatting-only gateway. Only after that extraction passes the full formatting contract can `NoteAiRepository`, conversational prompt actions and note conversational view-model code be removed.

Database-table deletion, backup-format retirement and destructive migrations remain a later and separately reviewed stage. Private keys and secrets are outside this stage.
