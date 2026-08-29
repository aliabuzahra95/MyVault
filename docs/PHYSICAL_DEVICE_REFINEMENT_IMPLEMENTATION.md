# Physical Device Refinement Implementation

## Authority And Scope

- Production repository: `/Users/aliah/Desktop/Current Projects/MyVault Complete Before Tutor`
- Branch: `frozen-design-master-port`
- Starting commit: `9d80524638476f907a4dde3316ff00e5001e8677`
- Recovery tag: `pre-physical-device-refinement`
- Frozen amendment: `PHYSICAL_DEVICE_REFINEMENT_AMENDMENT.md`
- Frozen checkpoint: `myvault-ui-prototype-physical-device-refinement-frozen-20260829-113753-AEST`

The frozen prototype remained read-only. No unrelated feature, navigation, canonical Qur'an data, PDF geometry model, note engine, or outgoing sharing behavior was changed.

## Batch A - PDF

- Highlight arms exactly one rectangle.
- Pointer release validates and persists through the existing annotation handler, clears transient geometry, and returns to normal reader gestures.
- There is no Done, Exit, persistent draw mode, or second confirmation.
- The frozen pill shows Highlight, the current persisted colour, and real current-document H/N counts.
- The palette opens above the swatch and retains the selected production colour for the next operation.
- The H/N area opens the approximately half-height local annotations sheet with All, Highlights, Notes, and Study links filters.
- Compact rows retain real type, colour, excerpt/text/link, and page data.
- View all activity opens the existing full PDF Activity route.
- Back handling retains the required local-overlay priority.

Runtime fixture evidence: `08-pdf-pill.png` through `11-pdf-activity-from-local-sheet.png`. Earlier isolated runtime testing confirmed one-shot behavior: a second drag without rearming did not create an annotation; rearming created the next rectangle.

## Batch B - Folder Colours

- `FolderEntity.colorKey` is nullable and accepts only `red`, `blue`, `green`, `purple`, and `yellow`; null/missing/unknown means Default.
- Room version 29 adds only `folders.colorKey TEXT` through `MIGRATION_28_29`.
- Folder IDs, hierarchy, modes, ordering, and unrelated fields are unchanged.
- `folders.json` writes the optional semantic value; old/missing/unknown values restore safely to Default.
- Study, Library, startup snapshots, and Explorer use one authoritative value.
- Colour applies only to folder icon and title.

Runtime evidence:

- Red parent in Study and Explorer: `03-study-folder-red.png`, `04-explorer-folder-red.png`.
- Red parent and independent Purple child: `12-study-parent-red-child-purple.png`, `13-explorer-parent-red-child-purple.png`.
- Default reselection: `14-study-child-default.png`, `15-explorer-child-default.png`.
- Relaunch persistence: `16-nested-colours-after-relaunch.png`.
- Blue Library folder and Explorer parity: `17-library-folder-blue.png`, `18-explorer-library-folder-blue.png`.

Automated compatibility covers migration 28->29, supported-colour backup round-trip, legacy missing field, and unknown field fallback. MyVault Web `verify:sync` passes, and an exact representative `colorKey: "red"` Android folder row is accepted by Web validation without a Web source change.

## Batch C - Dashboard

- Existing Continue, Recent, and Pinned remain truthful.
- Qur'an reads the actual saved Surah/ayah and resolves the real catalog name.
- Reflections uses the newest one to three real repository items.
- Both routes reuse the existing exact-verse Stage 7 handoff.
- Sections with no production data are omitted.

Runtime evidence: `06-dashboard.png` shows the real `Al-Faatiha · 1:1` state, and `07-dashboard-quran-target.png` confirms the row opens Ayah 1 of 7.

## Batch D - Maududi Footnotes

- Stable identity remains Surah, ayah, translation source, and footnote ID.
- Same-marker selection closes the inline footnote.
- Selecting another marker replaces the open footnote.
- Existing restrained expansion animation remains in use.

Runtime evidence: `19-maududi-before.png` through `23-maududi-switch-footnote.png`; UI state confirmed open Footnote 1, closed Footnote 1, opened Footnote 2, then replaced it with Footnote 1.

## Verification And Release Gate

- Java: JBR 21 at `/Users/aliah/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home`.
- Full unit suite, debug APK, Android lint, and `git diff --check` are mandatory before checkpointing.
- Only `emulator-5554` is currently connected. No physical-device acceptance may be claimed.
- Emulator Explorer reports `Drive not connected`; release-signed upload/discovery/controlled restore cannot be claimed.
- A new signed RC must not be promoted until the physical device verifies PDF touch/pinch/pan and the production Drive account completes the folder-colour backup/restore smoke.

## Intentional Deferrals

- Outgoing Study Share: deferred new functionality.
- Outgoing Library Share: deferred new functionality.
- Global or unrelated redesigns: not part of this refinement.
