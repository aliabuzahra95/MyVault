# Supabase Setup for Vault Notes

This app currently uses Supabase for cloud backup/restore.

The app still works fully offline. Supabase is only used when you sign in from Settings and tap Cloud backup or Cloud restore.

## 1. Open Supabase

Go to https://supabase.com/dashboard and open your project.

You can use your existing Qur'anic Threads Supabase account, but it is cleaner to create a separate project for Vault Notes.

## 2. Copy Project URL and Anon Key

In Supabase:

1. Open Project Settings.
2. Open API.
3. Copy the Project URL.
4. Copy the anon public key.

Then open this project file:

```text
gradle.properties
```

Fill in:

```properties
MYVAULT_SUPABASE_URL=https://your-project-id.supabase.co
MYVAULT_SUPABASE_ANON_KEY=your-anon-key
```

After changing this file, sync Gradle in Android Studio.

## 3. Create Storage Bucket

In Supabase:

1. Open Storage.
2. Create a bucket named:

```text
vault-backups
```

3. Keep it private.

## 4. Add Storage Policies

In Supabase:

1. Open SQL Editor.
2. Create a new query.
3. Run this SQL:

```sql
create policy "Users can upload their own vault backups"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'vault-backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can update their own vault backups"
on storage.objects
for update
to authenticated
using (
  bucket_id = 'vault-backups'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'vault-backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can download their own vault backups"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'vault-backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);
```

## 5. Use It in the App

## 5. Turn Off Email Confirmation For This App

This app uses simple email/password sign in inside Android. For the current backup feature, the easiest setup is to turn off Supabase email confirmation.

In Supabase:

1. Open Authentication.
2. Open Providers.
3. Open Email.
4. Turn Confirm email off.
5. Save.

If you leave email confirmation on, Supabase may send you to `http://localhost:3000`, which is the default web-app redirect and not useful for this Android app yet.

## 6. Use It in the App

In the app:

1. Open Settings.
2. Tap Supabase account.
3. Create account or sign in.
4. Tap Cloud backup to upload your latest backup.
5. Tap Cloud restore to download and restore the latest backup.

## Current Limits

- This is cloud backup/restore, not live multi-device sync yet.
- It stores one latest backup per signed-in user.
- Restore merges the cloud backup into the current vault.
- Row-by-row Supabase sync can be added next after this is working.
