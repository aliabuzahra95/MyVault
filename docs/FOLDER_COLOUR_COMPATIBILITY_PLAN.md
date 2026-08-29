# Folder Colour Compatibility Plan

Status: **AUDIT ONLY - IMPLEMENTATION BLOCKED**

The folder-colour UI is not frozen yet. This document defines the minimum compatible data path only. No Room, backup, repository, ViewModel, Explorer, Study, or Library implementation is authorised by this plan.

## Recommended Model

- Add one optional semantic field to `FolderEntity`: `colorKey: String? = null`.
- Supported stored values: `red`, `blue`, `green`, `purple`, `yellow`.
- `null`, blank, missing, or an unknown value resolves to the approved neutral Default appearance.
- Do not store Compose `Color`, ARGB integers, theme tokens, or separate Study/Library/Explorer values.
- `FolderEntity.colorKey` is the single source of truth for Study and Library folders and subfolders.

## Room Migration

When separately authorised, increment `VaultDatabase` from version 28 to 29 and add only:

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

Add optional `colorKey` to each `folders.json` row only after implementation approval.

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

Before Android schema implementation is released, add explicit Web contract assertions that `colorKey` survives validation, no-op export, safe sync merge, rename, reorder, and Android-to-Web-to-Android round trip. Do not rely only on the existing representative fixture's unrelated `customColour` field.

## Shared Rendering Path

Future approved implementation must carry the same `FolderEntity.colorKey` through:

- Study/Personal: `FolderRepository` -> `UiMappers.kt` -> `VaultTreeItem` -> shared Corpus Browser folder row.
- Library/Personal Library: `LibraryViewModel.toLibraryFolderItem()` -> `LibraryFolderItem` -> shared Corpus Browser folder row.
- Explorer: `VaultTreeItem.toExplorerNode()` and `LibraryFolderItem.toExplorerNode()` -> `VaultMobileWebExplorerNode` -> the single Explorer row implementation.
- Startup caches: `HomeSnapshotRepository` and `LibrarySnapshotRepository` must encode/decode the optional semantic value to avoid a temporary neutral-colour flash before Room emits.

The renderer should map semantic values to approved theme-aware visual tokens only after the Folder Colour Frozen amendment is available. No screen may own an independent colour preference.

## Stop Conditions

Stop for approval if implementation would require a destructive migration, backup-version change, raw colour storage, separate Explorer state, mode-specific duplicate fields, Web parser changes beyond additive tolerance, or any visual choice not fixed by the pending mockup.
