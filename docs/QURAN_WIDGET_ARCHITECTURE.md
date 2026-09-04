# Qur'an Reader Widget Architecture

## Platform decision

The widget uses traditional `RemoteViews` with a `ListView` backed by
`RemoteViewsService`.

This was selected over Jetpack Glance because the core requirement is a reliable,
launcher-hosted collection that can scroll hundreds of Arabic ayah rows on Samsung
One UI. Glance ultimately renders through `RemoteViews`, remains constrained by the
same widget host capabilities, and would add a dependency without improving
collection control for this use case. The platform collection APIs provide stable
row IDs, lazy row population, per-row fill-in intents, and established launcher
compatibility.

## Data and state

- `QuranCanonicalSource` is the single process-wide loader for `qpc_hafs.json`.
  The in-app reader and widget therefore read the same immutable Arabic source.
- Existing `quranCatalog` metadata supplies Surah names and ayah counts.
- Each widget stores only `surah`, `mode`, and best-known `anchor ayah` under its
  Android widget ID in device-local `SharedPreferences`.
- Passive launcher scroll position is not observable through the widget API. The
  widget honestly retains the last ayah opened and uses stable IDs so the launcher
  can retain ordinary list position where supported.
- No widget state is added to Room or MyVault backup/restore.

## Interaction

- Reader mode is a lazy, vertically scrolling ayah collection.
- The Surah heading switches the same widget to a scrolling 114-Surah picker.
- Previous and next controls update only the tapped widget instance.
- An ayah or the open icon launches `MainActivity` with an exact validated
  `surah:ayah`. `VaultNavHost` then uses the existing Qur'an reader location route.

## Responsive layouts

The provider uses the launcher's reported minimum width and height to choose one of
four layouts: compact, medium, large, or extra large. These variants change header
density, metadata visibility, padding, and Arabic type size rather than simply
stretching one fixed layout. Resize updates target only the affected widget.

## Update behavior

The corpus is local and static. There is no polling or background worker. A widget
updates only when it is added, resized, changes mode/Surah, or receives an explicit
provider update.
