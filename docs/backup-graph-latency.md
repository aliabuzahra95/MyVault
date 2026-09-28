# Internal Graph Writer Latency

Production graph publication remains disabled. Room remains 35. Normal Backup and Restore still use the legacy paths.

## Measurement Contract

`BackupGraphTiming` is opt-in and uses `System.nanoTime`. Nested phase spans are inclusive: do not add parent and child spans together. The disposable transport separately records monotonic HTTP durations and request stages. No credentials or note payloads appear in timing logs.

`GraphWriterLatencyDriveTest` runs three repetitions of five identical scenarios against synthetic, named Room databases and an explicitly provisioned loopback broker. The writer interval excludes test setup, independent reader cross-checks and cleanup; those are reported separately. Historical timing cannot retroactively supply missing fine-grained spans.

## Safe Reductions

- Reserve all intended object IDs together before persisting the immutable publication intent. Retries use the persisted IDs, not new IDs.
- Verify the trusted parent descriptor against the bytes already present in the same fresh complete inventory. Do not redownload that parent immediately.
- An existing object already verified in the publication loop needs one exact verification, not two back-to-back identical reads. New objects still require exact post-create verification before receipts and acknowledgement.
- The disposable transport creates exclusively and leaves normal publication verification to the writer. It never overwrites an object. Standalone duplicate-create probes retain their independent verification.
- Fresh complete paginated discovery remains mandatory. A bounded account/root/lineage-scoped byte cache is acceleration only. Reuse requires freshly fetched provider SHA-256 and size matching independently downloaded/verified bytes and the expected descriptor. Missing or changed checksums fall back to byte download. New uploads are never trusted from local bytes alone.

Google documents the output-only [SHA-256 checksum](https://developers.google.com/workspace/drive/api/reference/rest/v3/files) and batched [generated IDs](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/generateIds). Availability and equality are additionally tested against disposable objects.

## Deliberately Retained

Fresh graph checks before capture, before publication and after completion preserve stale-parent/fork behavior. Commit remains last. Full SHA-256/size verification, staging checks, atomic generation-safe acknowledgement, account isolation and durable recovery remain intact.

Drive Changes is deferred: a trustworthy account-scoped complete index/cursor, initial-list race closure and invalidation fallback are prerequisites. The current stage needs no new Room schema. No mutable head or cached inventory is authoritative. No background sync exists.

The broker uses one process-scoped HTTP client pool and a private warm access token. There is no successful-request polling/backoff sleep. Authentication refresh, broker setup, independent reconstruction and verified cleanup are harness work, not per-note preparation.

## Reproduction

Run the Node cache/transport tests without authentication. For authenticated tests, explicitly set `MYVAULT_GRAPH_EVIDENCE_DIR` and `MYVAULT_GRAPH_AUTH_HELPER` to protected local paths. Never store OAuth material in the repository. Provision only the disposable emulator with broker connection configuration, not OAuth credentials. The broker admits only generated IDs belonging to its uniquely named disposable root. Resume requires its exact persisted allowlist and the same verified Drive account.

Before/after measurements must use matching history depths and disclose network variation. These are emulator/provider observations, not Samsung timings or a production latency guarantee.
