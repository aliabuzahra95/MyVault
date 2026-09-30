# Production Graph Readiness Check

## Latest Gated Routing Work

The September 30 continuation starts at Android
`8711d78d330a54f710751bb8e412369262b79779` and Web
`b60dd68349f90b01e45b0a1054f3dda37e659e05`. Both recovery tags are
`recovery-before-production-graph-routing-20260929-214738`.

Normal authenticated Android graph transport and paired manual Backup/Restore
branches are implemented, but both production flags remain false. Namespace
enrollment persists intended folder IDs before creating anything. Existing graph
discovery is read-only; a missing trusted root cannot be replaced silently.
The first graph baseline captures the current local Vault in a separate immutable
namespace and never rewrites the legacy backup. Later publications use pending
capture. Force Backup is rejected for graph-established accounts rather than
falling back to mutable legacy publication.

Publication-parent proof and actual applied/Restore position remain separate.
A verified own publication supports an already-current Restore without inventing
a Restore row. Following an actual Restore, the next Backup explicitly adopts
the verified single tip as its parent, without acknowledging pending user edits.
Publication intents are bound to their exact enrolled namespace on retry.

The existing persistent manual WorkManager operation invokes the repository,
so the gated graph route uses the same background protection/progress channel.
This is not automatic sync. Large graph objects use streaming resumable creates;
interrupted transfers retry the same intended immutable ID and full frozen bytes,
not an unverified overwrite. Real-provider testing of this normal large-object
adapter still requires the production-auth acceptance step.

Validation so far: 129 focused Android host tests, 38 disposable Room/runtime
tests, debug/test APK build and release/R8 build passed. Room remains 36.
The Web counterpart passed 32 Chromium/IndexedDB cases, typecheck, production
build, historical/delta/binary compatibility and Android writer fixture checks.
The authenticated disposable writer passed prepare and separate-process recovery;
both Android and Web detected the resulting two-tip fork. These broker tests do
not prove file visibility between the normal production OAuth clients.

A second fresh disposable Drive graph passed full Restore, already-current,
one-note, three-commit, 4096-to-8192-byte replacement, metadata-only update,
new attachment, exact deletion and process-restart recovery. The already-current
case used two Drive requests and applied zero rows/downloaded zero binaries.
The one-note case used five requests, applied one row and downloaded zero
binaries. Web independently reconstructed the same nine-commit synthetic graph.
These are emulator/test-account observations, not Samsung acceptance timings.

The normal Web OAuth visibility probe did not complete: Google sign-in closed
its popup before returning permission, and the user confirmed that this test
browser does not allow sign-in. No normal Web Drive file request was made. Do
not retry that browser or substitute the separate disposable broker credential
for the actual Web client. The normal Android-owned visibility probe remains
one-sided and its exact isolated test root must be cleaned up only through the
same authorized owner; it is not a graph backup or a legacy backup.

Release is still blocked on normal Android/Web OAuth cross-visibility,
coordinated Web activation, production-account read-only reconciliation and
verified initial baseline establishment. No new functional release APK has been
signed or uploaded, and no real Vault or legacy backup has been modified.

## Earlier Readiness-Only Build

This build adds a manual, read-only readiness check. It does not enable graph
Backup or targeted Restore. Existing production Backup/Restore remains active.
Room remains version 36. No schema or backup format change is made.

## Safety Boundary

Web now has gated graph reader/writer and durable Restore groundwork. It has not
been deployed/enabled for the production graph workflow. Production OAuth
visibility in both directions, existing-Vault reconciliation and coordinated
routing remain incomplete. Reader/disposable-harness compatibility alone is not
proof of production account visibility. A successful diagnostic must not enable
publication.

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

## Initial Backup Progress And Background Work

The internal graph baseline/writer now reports reading, metadata/file staging,
uploading, byte verification, full checkpoint verification and local completion.
Counts and percentages describe the current stage, not an estimated percentage
of the whole backup. Initial snapshot reading and local completion are
indeterminate. No time-remaining estimate is shown. Success is reported only
after verified commit publication and atomic local completion; forks do not
report ordinary success. These graph progress hooks remain behind disabled
production routing.

The existing manual Drive operation runs in persistent WorkManager work, not a
screen coroutine. It awaits dataSync foreground-service protection before Drive
work and no longer silently ignores failure to start that protection. Coroutine
cancellation propagates through the worker and legacy repository so Android can
stop/restart work correctly. Work progress retains detailed status on reopen.
Android 13+ notification permission is requested at a user-started Drive
operation; denying it does not cancel the operation. The ongoing notification
opens MyVault when tapped.

Leaving the app should not cancel an operation. Force-stop, loss of network,
Samsung battery restrictions and Android scheduler/foreground-service quotas
can still interrupt it; uninterrupted twenty-minute execution is not guaranteed.
The graph writer already persists exact publication intent and verified object
receipts for safe resumption, but production worker integration and physical
Samsung long-running acceptance remain unverified. No live/background sync is
introduced.
