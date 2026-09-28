# Local baseline, binary fingerprints and pending capture

This stage is Android-local groundwork only. Production manual Backup still selects the existing full-array writer; production Restore still selects its existing reader/application path. Incremental publication remains hard-disabled. No real Drive requests, Web changes, sync workers, automatic uploads or user-content migrations are introduced.

## Storage and account boundaries

Room 33 -> 34 adds two tables, leaving all version-33 entity definitions unchanged:

- `backup_binary_fingerprints`: attachment ID, durable path, byte count, SHA-256 or null, VERIFIED/UNKNOWN/DELETED state, journal generation.
- `backup_binary_references`: account + attachment ID, committed checkpoint/head, verified remote file ID, byte count and SHA-256.

The local Vault and its immutable-byte fingerprints are shared local data, not duplicated per login. Permission to reuse a remote binary is per account and requires that account's trusted checkpoint plus matching verified size/hash. Existing normalized Drive-email scoping is retained. Another account cannot acknowledge pending work, trust a baseline, or reuse these remote references. New accounts start untrusted; conservative `local-unassigned` retention remains unchanged.

## Precise baseline proof

`BackupBaselinePreparer.prepare(account)` is an explicit, currently unused one-time full staging API. It holds the shared binary/settings gates, recovers interrupted settings writes, exports the existing metadata in a Room transaction and captures the exact journal generation/origin epoch. Only this baseline operation enumerates all groups and stages all durable bytes. Missing/unowned files or inconsistent metadata sizes fail safely; historical rows are not rewritten to force eligibility.

`VerifiedBackupBaseline.verify(...)` requires the complete authoritative metadata inventory, exact attachment-to-file claims, actual hash/size verification of every remote object under the same authenticated account, and byte-identical candidate/committed manifest readback. A timestamp, login, or old backup preference cannot satisfy this proof. The future authenticated transport must provide the remote bytes and verified account; this stage supplies no Drive integration.

Historical full manifests have no checkpoint UUID. Their equivalent identity is `full-<SHA-256 of exact committed manifest bytes>` for checkpoint and head, together with the actual manifest file ID/hash. `establishVerifiedBaseline` persists that identity and verified account-scoped binary references while acknowledging only generations included in the staged snapshot. Edits made afterward remain pending. Proof from another preparation, account, changed baseline, Restore epoch or incomplete settings write is rejected. Failure before acknowledgement leaves pending state intact.

Accounts remain untrusted after upgrade and normal legacy Backup. A future coordinated full Backup integration must prepare the matching snapshot, verify publication/readback and explicitly establish trust. No equality between current local data and an existing Drive backup is assumed.

Full Restore invalidates trust before any durable file writes; database/preference Restore suppression remains in place. Interrupted settings writes also invalidate trust. Explicit imported/uncertain state can use `invalidateBaseline(reason)` in future integration. Normal subsequent user edits retain the committed base and become pending, rather than invalidating it. Account switching does not transfer trust.

## Binary coverage and lifecycle

The existing backup binary inventory is attachment rows: imported library PDFs/files, note document attachments and generated/clipped PDF images. Temporary annotation previews, narration and other caches are not backed-up binaries.

`AttachmentRepository` hashes bytes once while writing document imports, library imports, generated images and PDF replacements. Metadata and fingerprint publish in one Room transaction. Replacement retains the same attachment ID, writes a fresh file first, and removes the old file only after DB commit. Restore copies also hash while writing, without creating user-origin journal edits; fingerprint uncertainty is persisted before overwriting bytes so a failed later Restore cannot retain a stale VERIFIED claim.

Attachment triggers create UNKNOWN fingerprints for legacy/unverified objects, invalidate path/size changes, and mark exact permanent deletion as DELETED. Trash/deletedAt updates retain existing semantics. No absent-ID deletion inference or Drive garbage collection exists.

Unknown fingerprints remain explicitly unknown until `verifyExisting(id)` or full baseline preparation verifies bytes. Cryptographic identity is SHA-256 plus measured byte count, never path/name/mtime equality. Normal batch capture does not hash, read, stat or walk files.

Metadata dependencies use stable protocol identities. Attachment UPSERT includes its own binary; PDF annotation/progress references its attachment; annotation geometry follows its annotation to that attachment; backlinks and knowledge links resolve their direct binary source where applicable. DELETE needs only its structural stable key, not deleted bytes. Full binary upload/remote reuse/deletion publication is not implemented here.

## Pending-only consistent snapshot

`PendingBackupCapture.capture(account)` acquires binary/settings gates and a Room transaction. It captures baseline, origin epoch, generation and pending operations, queries only exact pending UPSERT keys using typed Room adapters and existing backup serializers, and freezes JSON payload strings. DELETE never fetches a removed row. Composite keys remain separate fields. `settings.json` reads only the existing exported singleton when pending, with interrupted-write recovery before capture.

Direct binary dependencies are key-queried and deduplicated. Known fingerprints and account-scoped reuse references accompany the frozen payload. Missing UPSERT/dependency rows fail and require reconciliation/full baseline; absence never becomes DELETE.

Database writes cannot interleave within the snapshot. Later edits get newer generations and survive acknowledgement of the earlier batch. Binary bytes are not cloned while holding a database snapshot: `stageCaptured` copies only a required file into new private staging and verifies the captured size/hash. Changed/deleted bytes abort staging and require recapture. UNKNOWN fingerprints cannot be staged as verified. The future writer must enforce this before publication and commit acknowledgement.

## Evidence and remaining work

Kotlin tests cover proof validation, authoritative protocol adapters, dependencies, fingerprints, account guards and disabled production gates. Disposable native SQLite checks execute the actual generated migration/trigger/key-query SQL with Arabic/rich text, restart, rollback, deletion and generation races. Query counters report zero record reads for empty capture, one notes-only read for one pending note, and three reads for three independent pending objects; no capture hashing occurs. Room instrumentation uses uniquely named disposable databases on a fresh empty emulator, not the user's Samsung.

Before production writer integration: supply authenticated transport proof; wire safe full-baseline completion; stage/verify changed binaries and immutable deltas; generation-safely acknowledge only after committed-manifest readback; implement targeted Restore state separately; validate Android/Web round trips before enabling publication. Unmanaged external file modifications require explicit repair verification. No real Drive, Samsung timing or physical backup/restore acceptance is claimed here. The production 76-second preparation problem remains unresolved until that next integration stage.
