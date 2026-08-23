# Ask AI Decommission — Stage 6 Report

Date: 12 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Complete the formatting extraction and remove the legacy note-conversation runtime

## Outcome

Stage 6 is complete.

The note editor now depends only on the dedicated note-formatting architecture. The mixed conversational `NoteAiRepository`, its conversational prompt builder and the note conversation-session repository have been removed from production source.

The visible Structure & Format experience has not been redesigned. Structure Only, Intelligent Structure, provider selection, model selection, preview, copy, insert and replace remain available.

## Native formatting architecture

### `NoteFormattingPromptBuilder`

The retained prompt rules now live inside the formatting package and support only:

- Structure Only;
- Intelligent Structure;
- Clean Format;
- Format Note.

The builder preserves the existing editor-safe HTML rules, lossless Structure Only instructions, 32,000-character middle-aware scope for non-lossless actions, provider/model token multipliers and long-note planning prompt.

It contains no chat, Study Tutor, summary, explanation, selected-text or conversation-history prompt mode.

### `NoteFormattingOutputEngine`

The output engine now owns:

- editor-HTML cleanup;
- markdown-fence removal;
- safe heading and list normalisation;
- Arabic segment preservation checks;
- source-segment preservation checks;
- unsafe shortening and expansion rejection;
- conservative local Structure Only fallback;
- bounded 25,000-character chunk splitting.

The lossless safety behaviour was moved without weakening its thresholds or fallback policy.

### `NoteFormattingSessionStore`

The editor now retains only formatting state:

- selected formatting action;
- Gemini, ChatGPT or Kimi provider;
- Fast or Smart model;
- loading/progress state;
- formatted result;
- formatting error.

It no longer retains chat messages, streaming text, questions, continuation state, conversation IDs, conversation summaries or tutoring state.

## Removed production runtime

- `NoteAiRepository.kt` — 1,852 lines.
- `AiPromptBuilder.kt` — 721 lines.
- `AiConversationRepository.kt` — 162 lines.
- Legacy note chat and selected-text state, routing and persistence logic from `NoteViewModel`.
- The unused `NoteAiSupabaseFunction` request-guard label.

The three deleted repository files remove 2,735 lines of obsolete mixed conversational runtime.

## Editor integration

`NoteViewModel`, `VaultNavHost` and `EditorScreen` now exchange native formatting types directly:

- `NoteFormattingAction`;
- `NoteFormattingProvider`;
- `NoteFormattingModel`;
- `NoteFormattingUiState`;
- `NoteFormattingRequest`.

There is no mapping back to a legacy chat enum and no runtime reference to the deleted conversation repositories.

The existing lightweight-model routing remains unchanged: the retained editor actions continue to use the Fast model when the old implementation would have downgraded Smart for those actions. Changing that product behaviour is outside this extraction stage.

## Provider behaviour preserved

### Gemini

- Gemini 2.5 Flash for Fast.
- Gemini 2.5 Pro with Flash fallback for Smart.
- Existing temperature, top-p and output-token behaviour.
- Existing partial-output handling.

### ChatGPT

- Existing authenticated Supabase function.
- `format_note` and `organise` formatting action mapping.
- Existing Fast/Smart mapping, session refresh and timeouts.

### Kimi

- Existing configured Fast/Smart model IDs.
- `kimi-k2.6` temperature `0.6`.
- `thinking.type = disabled`.
- Non-streaming formatting response.
- Existing API-key handling and friendly failures.

No provider credential, secret or private key was changed or removed.

## Storage compatibility deliberately retained

Stage 6 does not delete or migrate stored conversational data.

The following remain because they are still part of the current Room schema and backup format:

- `AiConversationEntity`;
- `AiMessageEntity`;
- `HomeChatHistoryEntity`;
- `AiConversationDao`;
- the storage-only portions of `HomeChatHistoryDao`;
- current database schema versions and migrations;
- conversation fields in backup import/export;
- note/folder deletion cleanup for obsolete conversation rows.

This prevents existing installations and old backups from becoming unreadable. A later, separately reviewed database migration stage can retire these fields safely.

## Qur'an AI Listen protection

Qur'an AI Listen remains present and reachable. Stage 6 did not change:

- `QuranAiListenSheet`;
- `QuranMemorizationRecorder`;
- Google or OpenAI speech-recognition providers;
- recording and playback;
- comparison, analysis and scoring;
- attempt persistence;
- Surah memorisation testing.

## Verification

- Formatting and decommission contract tests: passed.
- Complete debug unit suite: **135 passed**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Debug Kotlin compilation: passed.
- Debug APK assembly: passed.
- Patch whitespace validation: passed.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

No paid live-provider request was sent during verification.

## Stage boundary

The production formatting runtime is now independent of the retired Ask AI ecosystem.

The next safe stage should review storage retirement separately: database entities, DAOs, deletion cleanup and backup fields must be handled through a compatibility-aware migration rather than ordinary code deletion. Private-key and secret removal remains postponed as requested.
