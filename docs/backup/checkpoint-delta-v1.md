# Checkpoint + Delta Backup Compatibility

## Release Status

This is a reader and transport-protocol compatibility stage, NOT the completed
change-only backup performance feature. Incremental publication is hard-disabled
in Android and Web. The existing manual full-array writer continues to handle
legacy backups normally. Neither legacy writer may republish an extended backup.

Android remains Room version 32: no schema changes, downgrade, migration,
database replacement, or user-data initialization was performed in this stage.

Starting Android: `5dea3b5be53ad0a9cf427488047f1b48da5f4e70`.
Recovery: `recovery-before-incremental-backup-20260928-125935`.
Starting Web: `e5e7d4768786d6a58e684f28b2d200adba6a5a8e` (same source tree as
the published pre-sync baseline `a1ee348`).
Recovery: `recovery-before-backup-compatibility-20260928-web`.

## Structure

The current schema-1 Drive manifest and its immutable `entries` identify the
complete checkpoint. Its existing full-array JSON files and binary entries are
not rewritten. New extended manifests add:

```json
{
  "incrementalBackup": {
    "version": 1,
    "requiredReader": "checkpoint-delta-v1",
    "checkpointId": "checkpoint-stable-id",
    "headId": "latest-delta-stable-id",
    "deltas": [{
      "deltaId": "latest-delta-stable-id",
      "parentId": "checkpoint-stable-id",
      "cloudFileId": "immutable-drive-object-id",
      "size": 123,
      "sha256": "64-lowercase-hex-characters"
    }]
  }
}
```

Each separately stored immutable delta contains:

```json
{
  "format": "myvault-backup-delta",
  "version": 1,
  "checkpointId": "checkpoint-stable-id",
  "parentId": "checkpoint-stable-id",
  "deltaId": "latest-delta-stable-id",
  "changes": [{
    "file": "notes.json",
    "key": ["existing-note-id"],
    "operation": "upsert",
    "value": { "id": "existing-note-id", "otherFields": "complete backed-up row" }
  }, {
    "file": "notes.json",
    "key": ["explicitly-permanently-deleted-note-id"],
    "operation": "delete"
  }]
}
```

The example is structural, not a valid note fixture: an upsert carries every
field required by the existing note decoder. Composite identities are arrays,
not delimiter-concatenated strings. The shared whitelist covers all 20 current
backed-up record groups. Settings are an upsert-only singleton with key
`["settings"]`. Unknown record groups, duplicate keys, mismatched payload IDs,
unsupported operations, bad ancestry, missing objects, and bad hashes fail closed.

## Reconstruction and Restore

1. Pin a committed manifest and validate checkpoint/delta ancestry.
2. Verify checkpoint metadata and every needed delta by exact size and SHA-256.
3. Start with the checkpoint arrays. Apply ordered, explicit upserts/deletes by
   each group's stable key. Preserve unmodified rows and unknown row properties.
4. The complete logical state and the remaining explicit tombstones are staged
   before database application. A later explicit upsert for the same ID supersedes
   its earlier tombstone. Missing rows never produce tombstones.
5. Android passes reconstructed full arrays through the existing restore decoder
   and applies whitelisted, parameter-bound exact-ID deletion statements in the
   same Room transaction. Its internal deletion sidecar is tied to the verified
   checkpoint/head marker. Historical unmarked optional files cannot delete data.
6. Web validates the reconstructed bundle using its existing validator and applies
   it in IndexedDB. Explicit deletes also remove only matching overlays/pending
   operations, scoped to the active account. Unrelated local drafts survive.
7. Extended Web backups are applied only through explicit Restore, not existing
   background/focus refresh. Historical Web behavior is otherwise unchanged.

The current Android cold-restore adapter reconstructs the entire checkpoint and
reads its binaries, as appropriate for a first restore. `deltasAfter` identifies
only the suffix after a known head (zero entries when already current), but
production applied-head persistence and incremental Room apply are not yet wired.
Unknown ancestry must trigger a full compatible restore, never a database clear.

## Writer Protocol and Safety Gate

The tested transport writers accept explicit coalesced changes, allocate one
immutable object per settled delta, read back and verify its bytes, preserve the
previous committed manifest, check the selected manifest has not changed, publish
the new pointer last, and confirm publication. Null/zero changes allocate no
objects. Retrying a confirmed delta after losing the local acknowledgement
recognizes its ID/hash and does not allocate another object. Failed staging may
leave harmless unreferenced objects; no automatic Drive deletion is performed.

Both public incremental publication entry points reject while their constant
gate is false. The lower-level transport routines are exercised only by isolated
fixtures and are not connected to Backup buttons. Merely flipping the constants
is NOT sufficient to finish or enable the original performance feature.

Still required before activation:

- Additive, reviewed durable local change tracking for all backed-up records,
  exact permanent deletion capture, generation-safe acknowledgement, and trusted
  per-account baseline initialization. Do not reuse record sync.
- Wire journal-driven manual Backup to the new transport and implement binary
  staging/fingerprints, dependency-aware changes, and preference capture.
- Persist applied backup identity and implement targeted delta restore with safe
  handling of local edits, unchanged binaries, and restore-origin suppression.
- Verify the actual Android Room restore and full file workflow in a disposable
  runtime. Host SQL and protocol tests are not a Samsung restore test.
- Validate authenticated Drive publication/read-back with disposable data, not
  the user's known-good backup. Deploy the Web reader before shipping/enabling
  the coordinated Android writer; update every participating old client.
- Re-run bidirectional reader/writer and retry tests after live integration.

Old applications do not understand the extension. Schema-1 alone does not make
old readers delta-aware: they may see the stale checkpoint and old writers may
drop the extension. All participating clients must be upgraded before a later
explicitly accepted capability flag enables publication. No extended manifest
has been published during development.

## Validation Evidence

`verify-incremental-backup.ts` generates disposable Web fixtures; Android
`IncrementalBackupCompatibilityTest` reads them and generates Android fixtures;
the Web script then reads the Android output through its production validator.
The tests forbid actual network access. They cover old arrays, one-note edits,
coalescing, zero changes, three deltas after one checkpoint, suffix catch-up,
permanent deletion, bilingual/rich-text preservation, broken/missing/corrupt
objects, failed commit, lost response, and retry without duplicate publication.

`verify-incremental-backup-browser.mjs` uses a new isolated browser context to
exercise the real Web reader/IndexedDB apply, exact-ID deletion, retained unrelated
drafts, another account's same-ID record, and idempotent re-application.
`verify-incremental-backup-sqlite.mjs` executes Android-generated bound deletion
SQL against an in-memory schema-32 database and checks all 20 unrelated user
tables plus database integrity.

These are host/disposable compatibility results, not physical-device or actual
Google Drive round trips. The original 76-second preparation problem is not yet
eliminated by this disabled compatibility stage. No user Vault or Drive backup
was used destructively.
