# Folder Colour Compatibility Plan

Status: **IMPLEMENTED - AUTOMATED COMPATIBILITY VERIFIED; RELEASE DEVICE ACCEPTANCE PENDING**

The Physical Device Refinement amendment froze the folder-colour UI and authorised this additive compatibility model. Production implementation now uses the single semantic `FolderEntity.colorKey` value described below across Study, Library, and Explorer.

## Recommended Model

- Add one optional semantic field to `FolderEntity`: `colorKey: String? = null`.
- Supported stored values: `red`, `blue`, `green`, `purple`, `yellow`.
- `null`, blank, missing, or an unknown value resolves to the approved neutral Default appearance.
- Do not store Compose `Color`, ARGB integers, theme tokens, or separate Study/Library/Explorer values.
- `FolderEntity.colorKey` is the single source of truth for Study and Library folders and subfolders.

## Room Migration

Implemented by incrementing `VaultDatabase` from version 28 to 29 and adding only:

```sql
ALTER TABLE folders ADD COLUMN colorKey TEXT
```

Add `MIGRATION_28_29` to `ALL_MIGRATIONS` and retain every existing migration. Existing rows receive `NULL`, preserving IDs, parent relationships, modes, ordering, favourites, descriptions, deletion state, and timestamps. Add a focused DAO update for `colorKey` rather than replacing a folder row.

Migration tests must cover:

- version 28 database to version 29 without data loss;
- Study, Personal, Library, and Personal Library folder modes;
- nested hierarchy and deleted folders;
- `NULL` rendering as Default;
- each allowed semantic value;
- unknown stored values resolving to Default without destructive rewriting.

## Backup And Restore

`folders.json` now includes the optional `colorKey` field.

- New Android backup: writes `colorKey` as a semantic string or JSON null.
- New Android restore: accepts a missing field as Default and accepts only the supported values.
- Old backup to new Android: missing `colorKey` restores as `NULL`/Default.
- New backup to current older Android: the existing `JSONObject.toFolderEntity()` reads named known fields and ignores an additional field, so restore remains parse-safe.
- Downgrade caveat: an older Android client can read the backup but cannot retain or re-export a colour it does not understand. A later backup made by that older client may therefore lose `colorKey`. This must be documented and tested before release.
- Do not change backup version, manifest semantics, folder IDs, or any unrelated file.

Targeted backup tests:

- old backup without `colorKey` to new Android;
- new backup with all values to new Android;
- missing, null, blank, and unknown values;
- nested Study and Library folders;
- Android backup through Web no-op export and back to Android;
- Android backup through a Web rename/reorder and back to Android;
- downgrade restore followed by re-backup, with the expected colour-loss limitation recorded.

## Web Compatibility

The current Web backup/sync implementation lives under `MyVault-Web/artifacts/myvault-web`.

- `validateSyncCandidate.ts` requires known folder fields but does not reject unknown fields, so an optional `colorKey` does not invalidate Android metadata.
- `syncPreflight.ts` merges existing rows as `{ ...original, ...patch }`, preserving unknown Android fields when an existing folder is renamed or reordered.
- Local backup export packages the metadata bundle rather than rebuilding every folder through the generated API DTO, so a no-op export can preserve `colorKey`.
- Web-created folders currently omit `colorKey`; that correctly means Default.
- Generated Web Folder API/Zod types do not expose `colorKey`, so Web cannot display or intentionally edit folder colour until separately updated.

The current Web parser was verified to accept a representative Android folder row containing `colorKey: "red"`; `pnpm verify:sync` also passes unchanged. No Web code or Web UI change was required. A release-signed Google Drive round trip remains required on a connected physical device before release promotion.

## Shared Rendering Path

The implementation carries the same `FolderEntity.colorKey` through:

- Study/Personal: `FolderRepository` -> `UiMappers.kt` -> `VaultTreeItem` -> shared Corpus Browser folder row.
- Library/Personal Library: `LibraryViewModel.toLibraryFolderItem()` -> `LibraryFolderItem` -> shared Corpus Browser folder row.
- Explorer: `VaultTreeItem.toExplorerNode()` and `LibraryFolderItem.toExplorerNode()` -> `VaultMobileWebExplorerNode` -> the single Explorer row implementation.
- Startup caches: `HomeSnapshotRepository` and `LibrarySnapshotRepository` must encode/decode the optional semantic value to avoid a temporary neutral-colour flash before Room emits.

The renderer maps semantic values to the exact frozen colours and applies them only to the folder icon and title. No screen owns an independent colour preference.

## Verification Record

- Room migration chain includes `MIGRATION_28_29`; schema 29 is exported.
- Existing/missing/unknown values resolve safely to Default.
- New backup round-trip preserves a supported colour.
- Old backup without `colorKey` restores as Default.
- Study parent Red and nested child Purple remain independent.
- Study, Library, and Explorer render the same authoritative value.
- Default can be reselected, and Red/Purple survive an app relaunch.
- Web validation accepts the additive field without parser changes.
- Google Drive upload/discovery/controlled restore is pending because no release-authorised physical device/account is connected in this environment.

## Stop Conditions

Stop for approval if implementation would require a destructive migration, backup-version change, raw colour storage, separate Explorer state, mode-specific duplicate fields, Web parser changes beyond additive tolerance, or any visual choice not fixed by the pending mockup.
