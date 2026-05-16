# Sync Setup Notes

Sync is scaffolded but not activated by default.

The safe architecture is:

```text
Android app -> Cloudflare Worker -> Supabase
```

The Android app does not contain the Supabase service role key.

## Android setting

The proxy URL and shared Worker token currently live in:

```text
gradle.properties
```

Look for:

```properties
MYVAULT_SYNC_PROXY_URL=
MYVAULT_SYNC_PROXY_TOKEN=
```

After deploying the Worker, fill in both values:

```properties
MYVAULT_SYNC_PROXY_URL=https://your-worker.your-name.workers.dev
MYVAULT_SYNC_PROXY_TOKEN=the-same-secret-you-added-to-cloudflare
```

Then sync Gradle in Android Studio and rebuild.

## Current sync behavior

- Push is scaffolded through `SyncRepository.pushLocalSnapshot()`.
- Pull currently returns raw JSON through `SyncRepository.pullRemoteSnapshotRaw()`.
- The Worker requires `Authorization: Bearer <shared-secret>` for sync push/pull.
- Conflict resolution is not implemented yet. The spec listed this as an open question, so the code does not pretend to solve it.
