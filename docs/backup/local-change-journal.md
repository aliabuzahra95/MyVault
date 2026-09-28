# Local manual-backup change journal

This is tracking groundwork only. The full-array production Backup writer and existing Restore reader remain active. `IncrementalBackupPublicationEnabled` remains false. No uploads, workers, sync protocol, or UI changes are introduced.

## Storage and mutation capture

Room 32 -> 33 adds only `backup_journal_state`, `backup_tracking_accounts`, and `backup_pending_changes`. All existing tables, identities, rich text, indices and inert compatibility-only record-sync tables remain unchanged.

`BackupRecordKeys` in `IncrementalBackupFormat.kt` is the authoritative group/key registry. The database callback inspects column metadata, not user rows, and installs backup-only INSERT/UPDATE/DELETE triggers for:

- folders, notes, blocks, tags, note_tags, note_tables, note_versions
- attachments, folder_sticky_notes
- courses, course_concept_cards, course_folders, course_notes, course_sticky_notes
- pdf_reading_progress, pdf_annotations, pdf_annotation_geometry
- source_backlinks, knowledge_tags, knowledge_tag_links

`pdf_annotation_geometry.json` maps to `pdf_annotation_segments`; other group filenames map to their same-name tables. Every DAO/repository/bulk write to these tables is captured in the entity transaction. Actual deletes, including exact cascade rows, capture OLD keys. No inventory comparison or missing-ID inference exists. Updating `deletedAt` for Trash remains UPSERT. Identical UPDATEs do not advance the journal; INSERT/Room REPLACE conservatively dirties the row even if its content is identical.

The compound primary key is account + group + separate key0/key1/key2 columns. Key arity comes from the protocol registry; no delimiter concatenation is used. Repeated mutations replace one pending row and advance a global monotonic generation. Identifiers are never rewritten by tracking. When future schema migrations change tracked columns, the trigger definition/version must also be updated.

## Accounts and baseline trust

The journal reuses the existing backup account scope: verified Google Drive email, trimmed and lowercased. It does not introduce the abandoned sync identity protocol or claim to use Drive permissionId. Account rename/missing identity requires a new untrusted baseline, not reuse of another account's acknowledgement.

Before account enrolment, mutations are retained under `local-unassigned`. New accounts receive those pending changes conservatively and then have their own pending rows. Enrolled accounts each track subsequent shared local-Vault mutations. Acknowledging one account never clears another or the unassigned scope. Unassigned retention is intentionally conservative; cleanup is not implemented here.

Migration does not scan existing rows or declare them backed up. Every account starts untrusted. Existing backup timestamps/metadata alone are not proof of a full matching checkpoint. Future integration must establish a verified full checkpoint against the complete local snapshot, with committed-manifest readback, before accepting incremental publication. Production full Backup currently does not acknowledge the journal.

## Capture and acknowledgement

Capture runs in a Room transaction and returns the pending rows, global generation, restore-origin epoch and account baseline. The future writer must stage a consistent payload with this capture, verify transport bytes and read back the committed manifest before supplying `ConfirmedBackupCommit`.

Acknowledgement validates account, captured generation, manifest checksum/identity, unchanged account baseline, restore epoch, and absence of a settings intent. Delta acceptance also requires a trusted matching checkpoint. It then clears only rows for that account with generation <= the captured generation in the same transaction as baseline advancement. Newer mutations survive. Failed or unacknowledged work remains pending; acknowledgement after a Restore or another baseline change is rejected.

## Preferences and Restore origin

The existing `settings.json` singleton is UPSERT-only. Only the 30 fields actually serialized by the historical settings exporter participate. The preference mapping is tested against that serializer. Credentials, backup timestamps, account metadata and preferences not currently exported are excluded.

Backed-up preference setters share a mutex. They persist a dirty settings entry and write-intent token before DataStore commits, then advance the settings generation and clear the token afterward. Capture/acknowledgement is blocked while a token is present. Process interruption leaves the intent durable. Recovery first reads durable DataStore, conservatively dirties settings, invalidates baseline trust and clears the intent. Future writer integration must invoke this recovery before capturing after restart, even if no later preference setter has run.

The existing Restore database transaction is wrapped in transaction-local journal suppression, with baseline invalidation and an origin epoch change. The same existing parsing, upsert and deletion paths still run. Restored preferences use restore-origin handling too. A failed database transaction rolls back both content and journal state. No targeted delta Restore is connected. Future integration must gate the entire Restore lifecycle (including file staging) against any active backup capture/publication.

## Binary dependencies and remaining integration

Attachment/PDF rows are tracked logically. Future staging must resolve their durable binary paths/fingerprints; annotation geometry, backlinks and attachment links depend on those objects. This stage neither hashes nor uploads binaries. Direct external modification of bytes without a repository/metadata mutation is not detected; future integrity repair must handle that case.

Next stage: consistent per-key payload capture, verified baseline establishment, settings-intent recovery at backup entry, binary dependency/fingerprint handling, incremental writer acknowledgement after commit, and targeted restore-state tracking. None is enabled here. The previous 76-second preparation scan is not fixed by this groundwork.

## Validation

Targeted Kotlin tests cover the actual protocol/settings registry, acknowledgement guards, unchanged old schema, additive migration and disabled production gates. `tools/verify_backup_journal.mjs` runs the Kotlin-generated migration/triggers on a disposable schema-32 SQLite file with all 20 record groups, Arabic/rich-text fixtures, exact/composite/cascade deletes, Trash, no-op UPDATE, coalescing, account isolation, generation races, reopen persistence, rollback, restore suppression and interrupted settings intent.

`BackupJournalRoomTest` additionally exercises Room migration/open/reopen on a uniquely named disposable database. It is intended only for a disposable Android installation. Host SQLite validation is not a claim of execution on a physical Samsung or Android runtime.
