# Attachment Binary Descriptor Capability

This is compatibility groundwork only. Room remains version 34. Incremental
publication remains hard-disabled on Android and Web. No journal-driven writer,
targeted restore, Drive garbage collection or live sync is enabled. Historical
checkpoint-only backups and their existing active backup/restore routes remain
unchanged. Existing Drive objects and checkpoint entries are never rewritten.

## Wire Contract

The existing manifest's `incrementalBackup` extension uses `version: 2` and
`requiredReader: "checkpoint-delta-binaries-v1"` if the chain contains binary
descriptors. This capability never downgrades when later deltas are metadata-only.
Metadata-only v1 deltas remain valid in a v2 chain.

A binary-capable delta retains `format: "myvault-backup-delta"`, uses `version: 2`,
and adds a mandatory `binaries` array (which may be empty):

```json
{
  "attachmentId": "existing-stable-attachment-id",
  "cloudFileId": "immutable-drive-object-id",
  "sha256": "64-lowercase-hex-characters",
  "size": 8192
}
```

Each descriptor must bind to exactly one attachment upsert in that same delta,
with the same stable ID, canonical `fileEntry: "files/<id>"`, and exact
`sizeBytes`. Byte counts are nonnegative safe integers shared by Kotlin and JS.
Duplicate/unbound descriptors, path-like IDs, malformed hashes and conflicting
byte identities for the same immutable Drive object are rejected.

## Resolution

Readers seed an attachment-ID map from verified checkpoint file descriptors and
fold the verified, ordered delta chain over it. A replacement overrides only the
logical map. A metadata-only upsert retains the previous descriptor; changed
size without a replacement, or a new binary-backed attachment without a
descriptor, fails. An explicit attachment deletion removes only that exact ID's
mapping. Absence is never used to infer deletion.

The final resolved objects must pass BOTH exact byte-count and SHA-256 checks
before restore is applied. Missing/corrupt replacement objects fail the restore;
there is no checkpoint fallback. Superseded or explicitly deleted objects are
not downloaded as the resulting attachment. Android stages canonical ZIP entries
from the resolved map using its existing file verification and restore decoder.
Web stores resolved file entries only after verifying their bytes, rejects stale
cached binaries, and re-verifies later downloads before display. Its old manifest
listing fallback cannot overwrite a binary-capable resolved map.

## Older Readers

The preceding checkpoint/delta-v1 Android and Web readers reject extension
version 2 / the new required capability explicitly. A binary delta concealed in
a v1 extension is also rejected by the new readers. Historical manifests without
the extension and v1 metadata deltas remain supported.

This cannot retrofit guards into already-installed applications from before
checkpoint/delta support. Such versions must not be used for an extended backup.
Publication remains disabled; coordinated reader upgrades and a separately
approved production writer stage are required before any real extended backup.

## Disposable Verification

`verify-backup-binaries.ts` creates Web checkpoint/delta fixtures with base64
object bytes. `BackupBinaryCompatibilityTest` reads them and creates Android
fixtures. Re-running the Web script tests the actual Web restore reader against
Android-created deltas. Both directions prove a 4096-byte checkpoint PDF payload
resolves to an 8192-byte replacement through four ordered deltas, including a
metadata-only update, a new attachment and an exact deletion. English/Arabic
note and rich-text data are unchanged. These are opaque binary fixtures, not
physical PDF rendering/print tests.

`verify-backup-binaries-browser.mjs` uses a fresh isolated browser/IndexedDB,
blocks external requests, checks stale-cache rejection, persists/reopens the
8192-byte replacement, rejects corrupt/missing mappings and confirms legacy
cache behavior. No real account, Vault, Drive backup or Samsung is accessed.

Fixture environment: `MYVAULT_BINARY_COMPAT_DIR` points to a disposable directory.
Existing `MYVAULT_BACKUP_COMPAT_DIR` fixtures continue to test legacy and v1
bidirectional backup compatibility. Physical/provider verification remains a
separate future gate; the 76-second production preparation problem is not fixed.
