# Production Graph Readiness Check

This build adds a manual, read-only readiness check. It does not enable graph
Backup or targeted Restore. Existing production Backup/Restore remains active.
Room remains version 36. No schema or backup format change is made.

## Safety Boundary

The validated Web implementation currently has graph readers, not a production
graph writer, durable application/recovery path, or coordinated routing. Reader
compatibility alone cannot establish safe Web participation. Production graph
enablement remains blocked by that boundary and by unverified real-account
visibility/reconciliation. A successful diagnostic must not enable publication.

## Read-Only Inspection

Settings > Backup / Restore > Check backup readiness uses the app's existing
Google sign-in and drive.file scope. It verifies Drive permissionId and signed-in
email correspondence, then reads visible legacy manifest metadata and graph
commit objects. Graph commit SHA-256 and exact byte counts are checked. No
test-harness credentials are used. No Drive object is created, updated or deleted.

The local Room transaction reads account-scoped journal/proof state and performs
bounded existence checks. It never exports metadata, hashes files, registers an
account, acknowledges a generation, establishes trust or applies Restore.

The decision model distinguishes first/legacy baseline requirements, adoption,
current/pending state, linear remote updates, concurrent local/remote changes,
forks, divergence, corrupt/unsupported ancestry, account mismatch, missing or
ambiguous namespaces and unfinished recovery. Namespace invisibility is not
proof that no backup exists. Legacy visibility is not proof of content equality.

## Phone Acceptance For This Build

Install the signed APK over the existing app without uninstalling or clearing
data. Confirm existing notes/files open. Open Settings > Backup / Restore >
Check backup readiness and retain its message. This check is not a full baseline
transition and is not an incremental performance test.

The production 76-second preparation issue is not claimed fixed by this build.
Before production enablement, Web participation must be coordinated, production
OAuth visibility confirmed, the actual existing-Vault decision reviewed, and a
verified immutable baseline established while preserving the legacy backup.
