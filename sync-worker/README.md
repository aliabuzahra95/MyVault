# My Vault Sync Worker

This folder is the sync proxy. The Android app must talk to this Worker, not directly to Supabase.

## What this does

- `GET /health` checks that the Worker is alive.
- `GET /sync/pull` reads vault rows from Supabase.
- `POST /sync/push` upserts vault rows into Supabase.

The Supabase service role key lives only in Cloudflare Worker secrets.

## Setup Steps

1. Create a Supabase project.
2. Open Supabase SQL Editor.
3. Run `sync-worker/sql/schema.sql`.
4. Install Worker dependencies:

```bash
cd sync-worker
npm install
```

5. Copy the example config:

```bash
cp wrangler.toml.example wrangler.toml
```

6. Add Cloudflare secrets:

```bash
npx wrangler secret put SUPABASE_URL
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
npx wrangler secret put SYNC_SHARED_SECRET
```

7. Deploy:

```bash
npx wrangler deploy
```

8. Put the deployed Worker URL and the same shared secret into the Android app's sync Gradle properties:

```properties
MYVAULT_SYNC_PROXY_URL=https://your-worker.your-account.workers.dev
MYVAULT_SYNC_PROXY_TOKEN=the-same-secret-you-added-to-cloudflare
```

For now, those fields are blank on purpose so the app builds and runs offline without trying to sync.
