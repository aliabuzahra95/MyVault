# Production Graph Readiness Check

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
