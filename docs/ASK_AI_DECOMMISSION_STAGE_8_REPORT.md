# Ask AI Decommission — Stage 8 Report

Date: 13 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Residual cleanup, compatibility verification and final local release gate

## Outcome

Stage 8 is complete.

The repository no longer contains reachable conversational Ask AI presentation, runtime, storage, backup persistence, dead screen callbacks, chat rendering, or conversational backend modes. The remaining AI architecture is limited to note formatting plus the separately protected Qur'an AI Listen and narration/speech utilities.

No UI redesign was performed.

## Residual source cleanup

Removed:

- the unreachable `askAiOnSelected` PDF activity method;
- unused `onShareAiAnswerClick` parameters and navigation callbacks threaded through Home, Courses and Library;
- the unreferenced `RichMarkdownText` chat renderer;
- the accidental `_patch_probe.tmp` development artifact;
- obsolete Home AI OpenAI/Gemini model BuildConfig fields;
- the `HOME_AI_` naming from retained Kimi formatting model fields;
- unused output-sanitizer branches and helpers inherited from the former mixed chat repository;
- old Ask AI wording from document extraction and shared-text note naming.

The formatting output engine was reduced from 821 to 544 lines while retaining its lossless, Arabic, HTML-safety, list-normalisation and chunking tests.

## Retained account login

Supabase authentication was not removed because ChatGPT note formatting still requires an authenticated session.

The Settings presentation and view-model naming now describe this accurately as:

- ChatGPT formatting login;
- formatting account login/logout;
- ChatGPT formatting connected/signed out.

This is retained formatting infrastructure, not a conversational Ask AI account screen.

## Formatting-only Supabase function

The local source for `supabase/functions/myvault-ai/index.ts` now accepts only:

- `organise`;
- `format_note`.

Removed from that function:

- quick summary;
- deep summary;
- Study Tutor;
- deep analysis;
- explain note;
- note questions;
- general questions;
- response streaming;
- conversational fallback prompts.

The existing function directory and endpoint name remain for compatibility with the Android formatting client. Its request guard now identifies the feature as `NoteFormattingSupabaseFunction`.

The TypeScript source passed a Node type-stripping parse/load check with a local Deno stub.

### Deployment boundary

The Supabase function source was changed locally but was not deployed to the live Supabase project. Deployment is an external production change and should be performed only with explicit approval. The existing deployed function continues to support the Android formatting calls, so this does not block the local application build.

## Backup compatibility

`BackupCompatibilityPolicy` now explicitly removes these retired metadata entries when reading an older backup:

- `ai_conversations.json`;
- `ai_messages.json`;
- `home_chat_history.json`.

Supported backup entries remain untouched. A unit test verifies that legacy chat metadata is ignored while the manifest, notes and attachments remain available to restore.

New backups still do not export any retired chat entry.

No real `.vaultbackup` archive was available locally and no Android device was connected, so a physical-device restore was not performed in this stage.

## Real SQLite migration verification

Added `tools/verify_room_26_27_migration.py`.

The verifier:

1. creates a real SQLite version 26 database from the exported Room schema;
2. inserts surviving marker data;
3. executes the production five-table removal sequence;
4. creates a separate expected version 27 database;
5. compares every surviving table's columns and indexes;
6. verifies all five retired tables are absent;
7. verifies the marker data survived.

Result: passed.

## Protected functionality

Confirmed present after cleanup:

- Structure Only;
- Intelligent Structure;
- Clean Format and Format Note contracts;
- Gemini, ChatGPT and Kimi formatting providers;
- formatting provider/model selection;
- Structure Only lossless fallback;
- Arabic text preservation;
- long-note planning and chunking;
- PDF reading, highlights, annotations and reading progress;
- notes, folders, courses, search and knowledge links;
- Qur'an AI Listen;
- Qur'an recording, speech recognition, comparison, scoring and saved attempts;
- narration and Azure/OpenAI speech functionality.

## Verification

- Complete debug unit suite: **139 passed**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Android lint task: passed.
- Lint errors: **0**.
- Existing lint warnings: **52**.
- Debug APK assembly: passed.
- Room 26 → 27 real SQLite verification: passed.
- Supabase TypeScript parse/load check: passed.
- Patch whitespace validation: passed.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

No live paid-provider request was sent.

## Secrets and credentials

No private key, API key, service-account value, Supabase credential, speech credential or signing credential was removed or changed. That discussion remains postponed as requested.

## Remaining external verification

Local implementation is complete. Before treating this as the final installed release, the remaining steps are:

1. approve and deploy the narrowed Supabase formatting function if desired;
2. install the APK on a device containing a version 26 database and confirm the upgrade opens normally;
3. create and restore one fresh backup on-device;
4. optionally restore one real older backup containing retired Ask AI history;
5. smoke-test Structure Only, Intelligent Structure and Qur'an AI Listen with configured live providers;
6. build/sign the final release APK.

These are external/device checks rather than unfinished local architecture work.
