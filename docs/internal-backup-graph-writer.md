# Internal Immutable Backup Graph Writer

This stage is internal/disposable only. `InternalBackupGraphWriter` has no DI registration, worker, UI, Drive transport, normal Backup call site or Restore call site. Both publication constants remain false. The production full-array writer and existing Restore remain active. The production preparation-scan performance issue is not fixed by this stage.

## Persistence and migration

Room 34 -> 35 executes only three CREATE TABLE statements. Existing entity definitions, identities, user rows, journal triggers and mutation semantics are unchanged.

- `backup_graph_bindings`: primary key `(accountScope, lineageId)`; Drive account ID; graph commit UUID and immutable file/hash/size; checkpoint identity and immutable descriptor; independent delta head ID; captured Restore-origin epoch. Trust additionally requires the existing journal account's verified checkpoint/delta state and origin epoch to agree.
- `backup_graph_publications`: primary key `(accountScope, operationId)`; lineage and Drive identity; captured generation/epoch; exact original journal account and graph binding; frozen payload/binary/dependency batch; canonical commit bytes; status.
- `backup_graph_publication_objects`: primary key `(accountScope, operationId, objectId)`; dependency order, role, optional attachment ID, durable private staged path, expected SHA-256/size, and verified readback SHA-256/size.

Only one unfinished operation per local account is admitted, checked inside a Room transaction. Object IDs cannot be assigned to another operation in that account. Completed operations/receipts remain retained; no pruning or staging garbage collection is implemented.

## Root and pending-only publication

An explicit internal root operation consumes the existing consistent full baseline preparation. It freezes verified staged copies into private `filesDir` storage, creates a self-contained immutable full checkpoint using the existing full-manifest format, then publishes its root commit. It does not adopt an unknown remote tip or overwrite a historical checkpoint. An existing/ambiguous graph blocks new-root enrollment.

For incremental work, the journal's trusted state and local graph binding must match, and discovery must show that binding as the sole valid tip. Known forks, unsupported/corrupt/missing ancestry and newer descendants block ordinary publication. A 4096-delta epoch requires a future verified new checkpoint before another delta can be published.

The existing pending-only capture freezes exact row payloads, structural composite keys, settings and direct dependencies. The writer never re-exports all groups for an incremental operation. DELETE contains only the explicitly captured key; absence never creates deletion. Metadata-only attachment edits retain a verified binary reference. Unknown fingerprints require verification/reconciliation; they are not guessed. Changed bytes are staged and checked against the captured fingerprint, and require their explicit attachment UPSERT.

## Immutable object order and retry

1. Reserve intended physical IDs through the disposable transport contract.
2. Freeze exact binary/delta/commit bytes and hashes in durable private files.
3. Persist the entire intent and intended descriptors transactionally before any create.
4. Create/readback-verify binaries first (full roots also include full metadata).
5. Create/readback-verify the delta, or the full checkpoint for a root.
6. Create/readback-verify the graph commit LAST.
7. Complete locally in one Room transaction.

Status progresses PREPARED -> PUBLISHING -> COMMIT_VERIFIED -> COMPLETE. Crash recovery may re-verify already created objects; it does not recapture newer user rows or regenerate IDs/bytes. The store has no update/delete API. Existing intended IDs are read and verified; identical bytes are reused, conflicting bytes fail closed. A lost create response therefore recovers the same commit. Missing/corrupt staging refuses completion and preserves pending changes. Before-intent staging loss has no published commit or acknowledgement; orphan staging may remain.

After a verified commit, one outer Room transaction rechecks account/lineage, original baseline and graph binding, generation/epoch, all expected receipts and frozen payload integrity. It invokes the existing generation-safe journal acknowledgement/baseline proof mechanism, saves binary references, removes only explicitly deleted logical binary references, advances the independent graph binding and marks COMPLETE. Failure inside that transaction rolls back all of these effects. A COMPLETE retry does not advance the binding again and verifies that its immutable commit remains valid. Commit and delta-head IDs are never conflated in the existing `headId` field.

An already published operation can finish recovery even if a sibling appeared after its original precheck. Two clients that both checked P can safely publish P -> A and P -> B; both remain, discovery reports FORK, and no third ordinary branch is admitted. No mutable authoritative head/CAS/If-Match is used.

## Disposable validation

Runtime tests use uniquely named SQLite/Room databases and fixture directories, not the normal Vault database. They verify migration preservation (notes, folders, Arabic rich blocks, attachment metadata, highlights/geometry, reading progress, existing journal/fingerprints), fresh installation, SQL query counts, coalescing, exact deletions/Trash, binary replacement/reuse/unknown states, readback failures, staging loss, account isolation, sibling branches and failures at publication boundaries.

A separate two-instrumentation probe stops the disposable app process between preparation and recovery, asserts a different PID, and recovers the same verified commit while preserving a new N+1 mutation. Database-reopen tests cover the other publication boundaries, including rollback after acknowledgement but before transaction completion.

Expected/asserted incremental metrics:

| Work | Pending rows | Payload rows | Binaries staged/created | Deltas | Commits |
| --- | ---: | ---: | ---: | ---: | ---: |
| Zero changes | 0 | 0 | 0/0 | 0 | 0 |
| One note | 1 | 1 | 0/0 | 1 | 1 |
| One changed attachment | 1 | 1 | 1/1 | 1 | 1 |
| Attachment metadata only | 1 | 1 | 0/0 | 1 | 1 |

SQL callbacks additionally prove zero note queries on zero-change and exactly one keyed note payload query, with no attachment/block/folder payload queries, for the one-note fixture. Discovery reads small commit metadata, not binary contents. These are disposable instrumentation results, not Samsung timings.

The Android runtime exports actual writer-created objects for Web host and fresh-Chrome reader checks: a six-commit linear/deletion history, a three-commit 4096 -> 8192 binary replacement history, and a three-commit fork. No Web graph writer or production source behavior is changed.

## Remaining gates

Authenticated transport is not implemented. A later separately approved disposable stage must establish verified Drive account/lineage/namespace ownership, complete fail-closed discovery, shared-client visibility, and pre-generated ID exclusive-create/retry/readback behavior. `commits()` must throw on missing/ambiguous/incomplete inventory, not return an empty successful namespace. Authentication must supply a verified permission ID; internal fixture values are not proof of authentication.

No real Drive objects were created, updated or deleted. No authenticated requests, Samsung installation, production data changes, push or deployment occurred. Ordinary publication, production writer integration, production graph Restore, enrollment/fork UI, later checkpoint rollovers and compaction remain separately gated.
