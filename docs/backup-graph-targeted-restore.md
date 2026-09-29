# Internal Targeted Graph Restore

Production publication and targeted Restore remain disabled. The normal Backup
and Restore routes still select the legacy implementation. No UI switch is added.

## Additive Room 36

Migration 35 to 36 creates only three new tables:

- `backup_graph_applied_states`: account/lineage/verified Drive identity, last
  applied commit descriptor, checkpoint descriptor, separate delta-head ID and
  origin epoch.
- `backup_graph_restores`: immutable per-commit Restore intent, original applied
  proof, captured generation/epoch, exact commit descriptor, hashed frozen
  operations and binary descriptors, settings before-image and recovery phase.
- `backup_graph_restore_objects`: exact attachment ID and immutable remote
  descriptor, private staging/destination paths and durable verification phase.

Existing user tables and publication bindings are not transformed. Applied
Restore position does not establish or advance a trusted publication parent.

## Planning and Local Protection

`InternalBackupGraphRestore` accepts the existing abstract object store but uses
only discovery and read methods. It verifies the complete immutable commit
inventory with the existing graph reader. Forks, unsupported capability, missing
ancestry and divergence block ordinary Restore. Timestamps are not authority.

Already-current Restore reads no user payload rows, applies nothing and downloads
no binaries. A device with an applied ancestor loads only missing delta objects
in ancestry order. A newer checkpoint requires explicit reconciliation rather
than silently replaying a full snapshot.

Cold Restore is allowed only for an empty supported local Vault with no pending
mutations. It reconstructs the selected verified checkpoint and later deltas.
An existing nonempty Vault with no applied graph proof requires reconciliation;
its relationship to Drive is never assumed. Historical production Restore still
uses its existing path.

Before preparation and each commit transaction, the account, Drive identity,
lineage, original applied proof, origin epoch and exact journal generation are
checked. Pending local edits or unfinished publication block mutation. Restore
does not acknowledge journal entries. An N+1 edit during staging leaves both the
pending edit and the previous applied position intact. An already-current check
may still return current with local edits because it performs no writes.

## Per-Commit Recovery

1. Verify the delta/checkpoint and freeze exact operations in Room.
2. Stage required binary bytes privately; verify SHA-256 and exact byte count.
3. Rename verified bytes to a new unique private destination and record readiness.
4. Apply only represented backup settings using an explicit recovery token and
   durable before-image/target state. Confirm the actual preference state.
5. Recheck all guards. In one Room transaction, suppress Restore-origin journal
   mutations, apply exact typed rows, persist binary receipts/fingerprints, advance
   the applied commit, clear the owned settings token and complete the intent.

An interruption inside step 5 rolls back operations and applied position together.
After commit B succeeds, failure at C leaves B applied. Retry discovers completed
work and does not repeat a completed commit. No intent owns another account's work.

Settings and files are not claimed to be atomic with Room. An interrupted settings
phase can leave the verified target preferences present while Room remains at the
previous commit. The durable intent resumes idempotently; unexpected preference
changes block reconciliation and the applied commit never advances prematurely.

## Binary and Exact-Row Semantics

Replacement uses a new path, never overwrites the old working file. Metadata
references that new path only after exact file verification in the commit
transaction. Missing/corrupt stages fail closed; no stale-checkpoint fallback.
Old files and unreferenced stages are retained; garbage collection is not included.

Metadata-only attachment updates reuse an account-scoped verified binary reference
and local fingerprint. Only the referenced local dependency is verified; no full
file inventory is hashed. New/unknown attachments require a valid descriptor.
One physical immutable object ID cannot acquire contradictory digest/size claims.

The existing 20-group whitelist and typed legacy codecs are reused. UPSERT reads
only its exact key, skips identical normalized rows and uses parameter-bound
INSERT/UPDATE, not REPLACE, preserving unrelated annotation child geometry.
DELETE uses only the exact explicit protocol key and existing permanent-deletion
rules. Absence never deletes. Trash/deletedAt is a normal preserved UPSERT state.
Only backup settings fields are written; account and unrelated preferences are not.

## Verification Entry Points

- `BackupGraphRestoreRoomTest`: disposable migration, apply, restart, binary,
  settings, local-edit, account, exact deletion and graph-refusal tests.
- `AuthenticatedGraphRestoreDriveTest`: explicitly requested `restorePhase=prepare`
  and `restorePhase=recover`, dedicated synthetic source/target databases, existing
  loopback broker confined to a unique disposable Drive root. OAuth stays on host.
- `BackupGraphRestoreGateTest`: additive schema and disabled production routing.

Web's existing graph reader is independently tested against the same actual Drive
objects and Android applied-position measurements on host and fresh Chrome. Web
production persistence/application is not enabled or changed by this stage.

## Remaining Production Gates

Explicit approval, existing-Vault graph reconciliation/enrolment, coordinated
client rollout, user-facing blocked/recovery states, safe old-file retention policy
and production routing acceptance are still required. The legacy 76-second
preparation behavior is not claimed fixed by this internal gated implementation.
