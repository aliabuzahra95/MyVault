# Vault Notes Release Checklist

Use this before treating the app as your main notes vault.

## Data Safety

- Run Settings > Backup & restore > Check backup safety.
- Export a manual `.vaultbackup` file.
- Upload a Supabase cloud backup.
- Restore a backup on a test install before relying on it.
- Confirm rich text, folders, tables, and attachments restore correctly.

## Device Checks

- Confirm the launcher icon is the new icon.
- Confirm the app name is correct.
- Create, edit, close, and reopen a long note.
- Attach and open an image.
- Attach and open a PDF.
- Delete a note and restore it from Recently Deleted.

## Release Build

- Increase `versionCode` before sharing a new APK.
- Keep `android:allowBackup="false"` unless you intentionally want Android system backup.
- Confirm Supabase URL and anon key are configured for the intended project.
