# Stage 10 Final Regression And Acceptance

Status: **CONDITIONAL / NOT RELEASE-READY**

Starting checkpoint:

- Branch: `frozen-design-master-port`
- Commit: `e2e006c7b94aa985ad31f0d02f1b8acbbd184495`
- Tag: `stage-9-approved`
- JBR 21 unit tests, lint, and debug assembly: PASS

## Phase A - Compatibility Audit

### Study batch pin

Repository history proves the production batch `Pin` action invokes
`onSetNotePinnedClick`, which maps to workspace-wide `NoteEntity.isPinned`.
It does not invoke folder-local `isFolderPinned`. The Stage 2 Frozen port
accidentally removed the mounted selection action bar while leaving its
selection state and component behind. Stage 10 restores that existing bar and
the original workspace-wide handler without changing either persisted field.

### Pinned-expanded preference

`pinnedExpandedByMode` is stored locally in DataStore and affects only whether
the legacy pinned presentation is expanded. The current backup preferences
model declares the field, but `settings.json` neither writes nor restores it.
Restore therefore returns to the local/default collapsed presentation. No note,
pin, hierarchy, or user content is lost. Adding it would extend the backup
payload, so Stage 10 leaves this as documented low-impact compatibility debt.

### Library legacy view mode

Legacy `list`, `grid`, and `icons` values remain accepted, backed up, restored,
and safely parsed. The Frozen Corpus Browser does not expose a selector and its
primary presentation does not branch on this state. Old values are therefore
harmless and no preference migration is required.

### Memorise compatibility boundaries

The dormant `3x`, `5x`, `10x`, and `Until stopped` enum values remain available
to old engine state but are not exposed by the Frozen Memorise screens. Repeat,
concealment, active recording, WAV files, STT work, and analysis state are
transient and absent from backup. Memorisation records and ayah/whole-Surah
attempts remain in the backed-up preference payload.

### PDF additive compatibility

Historic `text_box` annotations remain accepted and preserved as read-only
entities. Multi-rectangle selection geometry remains an optional
`pdf_annotation_geometry.json` extension keyed to a normal parent annotation;
the parent retains its representative rectangle and selected text, and invalid
or absent segment data falls back to that rectangle. Runtime fixture and
destructive round-trip evidence remain Phase B acceptance work.

### Signing and OAuth baseline

- Package: `com.myvault.app`
- Release certificate SHA-1: `77:D0:EE:6A:B8:DF:03:59:6D:50:B7:13:68:58:03:D7:76:F9:18:16`
- Release certificate SHA-256: `5D:33:F9:07:DB:32:D4:04:35:2F:C1:60:FB:D0:E7:B2:7D:64:8A:50:F9:7C:64:85:36:21:1D:68:CF:5E:80:A3`
- Debug certificate SHA-1: `E7:1E:6D:03:41:65:44:79:8B:A7:F9:0A:D1:26:F4:A0:23:CB:BD:85`

Historical production evidence identifies a Google OAuth Android client for
this release package/certificate. Debug is a separate certificate and is not
evidence of release failure. The current local Gradle signing properties point
at a different certificate and must not be used for distribution. The Stage 10
candidate was explicitly signed with the established production certificate.
Independent Drive sign-in/upload/restore remains an open release gate because
the available emulator has no Google account and no physical device is
connected.

## Phase B - Controlled Android Backup And Restore

- Captured an external safety copy of the isolated emulator application data before destructive testing.
- Exported an untouched archive through the production Backup / Restore screen, cleared only the isolated emulator app, and restored through the production workflow.
- Folders, notes, rich-text blocks, PDF annotations and ordered geometry, reading progress, note versions, Courses, Sticky Notes, Concept Cards, knowledge tags, and available embedded files survived the round trip.
- Ten unavailable attachment records were skipped because their source files were already absent before backup. The two app-owned PDFs embedded in the archive restored successfully.
- A proven restore regression was found and corrected: Study source backlinks use PDF document coordinates, but restore previously clamped them to `0..1`. Restore now preserves the stored coordinates exactly without changing the schema or backup format.

### Backup evidence

- Untouched production export: `artifacts/stage-10/evidence/android-production-backup.vaultbackup`
- Export SHA-256: `89891fa4af5ca9c9e6207ead1647c92102d143ee7ea738cd60bb4441fb12e826`
- Format/version: `myvault-backup` / `1`
- Safety copy SHA-256: `d5536b96907ad3182bd11a95393cda7a594ecfcb86d6145009e9d2f3da7eb652`
- Restored counts: 14 folders, 13 notes, 7 PDF annotations, 56 ordered geometry segments, 2 note versions, 1 Course, 1 Sticky Note, 1 Concept Card, 1 knowledge tag, and 1 PDF-to-Study source backlink.
- The corrected restore preserved the source backlink rectangle exactly as `118,43,160,53`.

### PDF fixtures

- A safe historic `text_box` fixture retained its annotation, tag, Study-note
  relationship, and `90,120,260,180` document rectangle after open, close, and
  reopen. Add-text-box remains unavailable.
- A real multiline selected-text highlight retained its logical parent,
  selected text, representative legacy rectangle, and all 54 ordered geometry
  segments after the production round trip.
- No Room migration, backup-schema change, geometry rewrite, or annotation data
  conversion was introduced.

## Phase C - Android/Web And Signing Compatibility

### Web compatibility

The current MyVault Web checkout passed:

- safe-sync contract verification
- workspace contract verification
- TypeScript checking
- configured production build

The Web contract preserves unknown optional files and fields. A test-only
older/Web-style archive with no `themeModeV2` and no PDF geometry extension was
restored through the Android production UI. Android fell back to `Follow system
+ Dark`, retained the seven annotation parents and representative rectangles,
and restored 14 folders, 13 notes, and one Course. The additive fields therefore
remain optional and fail safely in both directions.

### Release signing

- Package: `com.myvault.app`
- Version name/code: `0.1.0` / `1`
- Candidate: `release/MyVault-Stage10-RC-signed.apk`
- APK SHA-256: `c62d16c4b9767218fbdab7d1669db9226418209252844592826c0b8efd4fc972`
- Signature verification: PASS, APK Signature Scheme v3
- Signing certificate SHA-1: `77:D0:EE:6A:B8:DF:03:59:6D:50:B7:13:68:58:03:D7:76:F9:18:16`
- Signing certificate SHA-256: `5D:33:F9:07:DB:32:D4:04:35:2F:C1:60:FB:D0:E7:B2:7D:64:8A:50:F9:7C:64:85:36:21:1D:68:CF:5E:80:A3`
- Signed release installation and cold launch on API 36.1 emulator: PASS
- Release process remained alive with no application fatal exception after launch.

The release certificate matches the established shipped/OAuth identity. Live
Google Drive sign-in, upload, discovery, and restore could not be exercised:
the emulator has no Google account and no physical device is connected. This
remains a release blocker rather than being inferred from certificate matching.

## Phase D - Product Regression

### Automated gates

- JBR 21 complete debug unit suite: PASS
- Android debug lint: PASS
- Debug APK assembly: PASS
- Release APK assembly, shrinking, signing and signature verification: PASS
- Stage 10 source-backlink coordinate regression test: PASS
- Stage 10 batch-pin and dormant Memorise mode contract tests: PASS
- Web safe-sync, workspace, typecheck, and production build: PASS

### Runtime evidence

- App shell, Explorer and principal Stage 1-9 destinations were exercised in
  their approved stage acceptance runs; Stage 10 re-ran the installed app,
  destructive restore, Note/PDF persistence paths, legacy restore path, and
  historic/multi-rectangle PDF fixtures.
- Study batch organisation again exposes its existing action bar. Batch Pin is
  wired only to workspace-wide `isPinned`; folder-local `isFolderPinned`
  remains a distinct per-note action.
- Library legacy `list`, `grid`, and `icons` preferences restore harmlessly and
  do not alter the Frozen Corpus Browser.
- Note rich text, blocks, versions, attachments that had real embedded files,
  hierarchy, pins, folder pins, favourites, and PDF source references survived
  the controlled round trip.
- Courses, Sticky Notes, Concept Cards, Qur'an preference payloads, PDF reading
  progress, and backed-up relationship data restored without schema changes.
- Dormant Memorise repeat modes remain engine-only. Active recording, WAV,
  concealment, STT, and analysis state remain intentionally device-local. The
  controlled archive contained no persisted Memorise attempts, so a populated
  attempt-history restore was not independently demonstrated in this run.
- The release build uses `FLAG_SECURE`; automated release screenshots and UI
  hierarchy capture are therefore black/unavailable by design. Process and
  activity evidence confirms the signed candidate launched successfully.

### Not independently repeatable in the available environment

- Real Google Drive account sign-in/upload/discovery/restore
- Physical multi-touch PDF pinch, pan-after-pinch, and highlight alignment
- Physical-device microphone, speaker, Qur'an audio and Memorise recording smoke
- External Google Chirp/OpenAI/Azure provider calls requiring configured accounts
- Full TalkBack traversal on a physical device

These are not recorded as passes.

## Phase E - Release Decision

The code, unit/lint gates, release signing, isolated destructive restore, PDF
compatibility fixtures, and Android/Web additive compatibility pass. The signed
candidate is suitable for the remaining device acceptance checks, but it is
**not declared release-ready**.

### Release blockers

1. Live release-signed Google Drive sign-in, backup upload, restore discovery,
   and restore execution have not been completed with a real Google account.
2. A physical-device smoke test, including genuine PDF pinch/zoom/pan and
   annotation alignment, has not been completed because no device is connected.

### Intentional limitations

- Outgoing Study Share and outgoing Library Share remain deferred new
  functionality and do not block this candidate.
- `pinnedExpandedByMode` remains local-only low-impact compatibility debt; no
  user content or pin state is lost.
- Missing pre-backup attachment source files are skipped truthfully; the restore
  does not fabricate file content.
- Material You and active/transient Memorise session data remain device-local by
  approved design.

## Final Acceptance Rule

Do not promote this candidate to final release until both blockers above pass
on the intended release-signed build. No additional product functionality is
required to perform those checks.
