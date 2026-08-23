# Ask AI Decommission — Stage 7 Report

Date: 13 July 2026  
Project: `/Users/aliah/Desktop/MyVault Complete Before Tutor`  
Scope: Retire obsolete conversational storage through a compatibility-aware Room migration

## Outcome

Stage 7 is complete.

The database and backup system no longer carry runtime storage for the retired Ask AI ecosystem. Database version 27 removes exactly five obsolete tables through an explicit Room migration. No surviving knowledge-management table or column changed.

## Database migration

`MIGRATION_26_27` drops:

- `ai_messages`;
- `ai_conversations`;
- `home_chat_history`;
- `library_ai_file_cache`;
- `library_pdf_text_cache`.

Each statement uses `DROP TABLE IF EXISTS`, allowing the migration to complete safely even if a previous installation does not contain one of the optional historical tables.

The migration remains registered in `VaultDatabase.ALL_MIGRATIONS`, so existing installations upgrade sequentially from any supported database version to version 27 without falling back to destructive recreation.

## Why these tables were removable

### Note conversations and messages

`ai_conversations` and `ai_messages` stored the note Ask AI conversation history retired in Stages 2, 4 and 6. No production runtime reads or writes them after Stage 6.

### Shared Ask AI history

`home_chat_history` stored the shared Home, Study, Library and Courses conversation history. The shared chat presentation and runtime were already removed.

### AI file caches

`library_ai_file_cache` stored provider-upload metadata used only by the retired conversational PDF pipeline.

`library_pdf_text_cache` stored extracted text for that same retired Ask AI pipeline. It was not used by the current PDF viewer, highlighting, annotations, reading progress, document viewer, search, formatting or Qur'an memorisation systems.

## Removed source files

- `AiConversationDao.kt`;
- `AiConversationEntity.kt`;
- `AiMessageEntity.kt`;
- `HomeChatHistoryDao.kt`;
- `HomeChatHistoryEntity.kt`;
- `HomeAiStorageModels.kt`;
- `LibraryAiFileCacheEntity.kt`;
- `LibraryPdfTextCacheEntity.kt`.

Dependency-injection providers and database accessor methods for these DAOs were also removed.

Note and folder deletion no longer perform redundant cleanup against tables that do not exist.

## Backup behaviour

New backups no longer export:

- `ai_conversations.json`;
- `ai_messages.json`;
- `home_chat_history.json`.

Restore no longer constructs or writes the retired entities.

Older MyVault backups remain structurally restorable because backup import accepts additional metadata entries. If an older backup contains these three obsolete JSON files, current MyVault ignores those entries while restoring the supported notes, folders, attachments, PDFs, annotations, courses, settings and Qur'an data.

The retired chat records themselves are intentionally not restored because their corresponding product feature and database tables no longer exist.

## Schema comparison

The generated version 26 and version 27 Room schemas were compared directly.

- Removed tables: exactly the five retired Ask AI tables.
- Added tables: none.
- Changed surviving tables: none.

Protected tables confirmed unchanged include:

- notes and blocks;
- folders and folder sticky notes;
- attachments;
- PDF annotations;
- PDF reading progress;
- source backlinks;
- knowledge tags and links;
- courses, course notes and concept cards;
- note versions and tables;
- full-text search.

## Protected AI capabilities

Stage 7 does not change:

- Structure Only;
- Intelligent Structure;
- Clean Format and Format Note contracts;
- Gemini, ChatGPT or Kimi formatting providers;
- formatting provider/model selection;
- Structure Only lossless and Arabic-preservation protection.

## Qur'an AI Listen protection

Stage 7 does not change:

- Qur'an AI Listen;
- recording or playback;
- Google or OpenAI speech recognition;
- comparison, scoring or analysis;
- saved memorisation attempts;
- Surah memorisation testing.

## Verification

- Focused migration, decommission and formatting tests: passed.
- Complete debug unit suite: **136 passed**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Debug Kotlin compilation: passed.
- Debug APK assembly: passed.
- Generated Room schema 27: passed inspection.
- Patch whitespace validation: passed.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

No live paid-provider request was sent.

## Data-impact statement

When an existing installation first opens database version 27, locally stored retired Ask AI conversations and the obsolete AI file caches are permanently removed. Notes, documents, PDFs, highlights, annotations, reading progress, folders, courses, settings, Qur'an content and memorisation records are not removed or modified by this migration.

## Stage boundary

The local conversational Ask AI implementation is now decommissioned across presentation, runtime, orchestration, Room storage and backup persistence.

Private-key and secret removal remains postponed as requested. Any later work should focus on residual configuration, dependency and naming cleanup only after proving that each item is unnecessary for retained formatting or Qur'an AI Listen.
