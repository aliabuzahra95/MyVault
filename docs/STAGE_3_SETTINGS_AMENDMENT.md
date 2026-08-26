# Stage 3 Settings Placement Amendment

Status: **PROPOSED FOR APPROVAL - AUDIT ONLY**

This document resolves the pre-Stage-3 inventory and placement audit. It does
not authorize Settings or Theme implementation. The Frozen Design Master is
read-only and remains authoritative for presentation.

## 1. Recovery And Audit Boundary

- Approved Stage 2 commit: `84a9884d282a0f46954d9c7fd5e77fb639e084dc`
- Working branch: `frozen-design-master-port`
- Recoverable tag: `stage-2-approved`
- Remote: `git@github.com:aliabuzahra95/MyVault.git`
- Known Stage 2 build state: `testDebugUnitTest`, `lintDebug`, and
  `assembleDebug` passed at the approved checkpoint.
- This audit changes production-side documentation only. It does not modify
  Study, Library, Explorer, Settings, Theme, repositories, preferences, backup
  models, or navigation.

The existing dirty working-tree files and generated artifacts are not part of
this audit and must not be staged with it.

## 2. Authoritative Sources Reviewed

Frozen Design Master:

- `DESIGN_SPEC.md`
- `COMPONENT_INVENTORY.md`
- `ANDROID_COMPONENT_MAP.md`
- `MOTION_SPEC.md`
- `DESIGN_MASTER_README.md`
- `10-settings.png`
- `11-theme-light.png`
- `12-theme-dark.png`
- `13-theme-oled.png`
- contextual Qur'an reference `30-quran-settings.png`

Production Android sources include:

- `ui/screens/SettingsScreen.kt`
- `ui/viewmodel/SettingsViewModel.kt`
- `data/preferences/VaultPreferences.kt`
- backup preference mapping and restore logic
- Google Drive incremental-sync repository and settings callbacks
- Supabase formatting-account session handlers
- `StorageRepository`
- `MainActivity` security-lock integration
- `ui/quran/QuranReaderSettingsSheet.kt`
- `ui/viewmodel/QuranReaderViewModel.kt`
- current routes to Recently Deleted, workspace switching, and contextual
  reader controls

## 3. Status And Compatibility Legend

- **ALREADY FROZEN**: the Frozen Master specifies the visible destination.
- **PROPOSED FOR APPROVAL**: a production capability needs the documented
  placement amendment.
- **DEFERRED**: preserve the current engine/state, but do not give it a new
  permanent Settings placement yet.
- **STOP-AND-ASK**: implementation must not proceed for that item until the
  listed ambiguity is resolved.
- Backup `Yes`: represented by the current Android backup preference mapper.
- Backup `No`: deliberately local/secret/runtime state, or currently omitted
  by the backup mapper. This audit does not change that behavior.

## 4. Complete Production Settings Inventory

The audit identifies **57 user-facing, operational, contextual, or dormant
settings/actions** plus **4 compatibility-only persisted presentation states**.
Operational actions are listed individually because a single legacy row such as
Backup & restore currently exposes several materially different handlers.

### 4.1 Main Settings, Appearance, Reading, Narration, And Vault

| # | Current label/capability | Current location | State / handler | Frozen or proposed destination | UI type | Backup / scope | Status |
|---|---|---|---|---|---|---|---|
| 1 | Theme: Light / Dark / Auto | Appearance cards | `VaultUserPreferences.theme`; `setTheme` | Settings -> Appearance -> Theme subpage | Selector | Yes; global | **ALREADY FROZEN**, but model migration is **STOP-AND-ASK** |
| 2 | Accent colour | Appearance card swatches | `accentColor`; `setAccentColor` | Settings -> Appearance -> Accent | Swatch selector | Yes; global | **ALREADY FROZEN** |
| 3 | Dashboard font size | Reading & Listening | `dashboardFontSize`; `setDashboardFontSize` | Settings -> Reading & Display | Selector | Yes; global | **ALREADY FROZEN** |
| 4 | Note editor font size | Reading & Listening | `noteFontSize`; `setNoteFontSize` | Settings -> Reading & Display | Selector | Yes; global | **ALREADY FROZEN** |
| 5 | Show full note titles | Reading & Listening | `showFullNoteTitles`; setter | Settings -> Reading & Display | Switch | No in current backup mapper; global | **ALREADY FROZEN** |
| 6 | Show full file titles | Reading & Listening | `showFullFileTitles`; setter | Settings -> Reading & Display | Switch | No in current backup mapper; global | **ALREADY FROZEN** |
| 7 | Default note view | Reading & Listening | `defaultNoteView`; setter | Settings -> Reading & Display | Selector | Yes; global | **ALREADY FROZEN** |
| 8 | Note preview | Dialog code exists, but no visible row currently opens it | `notePreview`; `setNotePreview` | Settings -> Reading & Display -> Note preview | Selector | Yes; global | **PROPOSED FOR APPROVAL** |
| 9 | Legacy general font size | Persisted but no current visible row/use beyond compatibility fallback | `fontSize`; preference setter | No new visible row; retain compatibility | Hidden persisted preference | Yes; global | **DEFERRED** |
| 10 | Automatic tag suggestions | Persisted/backed up; no current row, setter, or consuming UI found | `autoTagSuggestions` | No placement until product behavior is identified | Hidden persisted preference | Yes; global | **STOP-AND-ASK** |
| 11 | Default Listen provider | Reading & Listening | `narrationProvider`; setter | Settings -> Reading & Listening | Selector | No in current backup mapper; global | **ALREADY FROZEN** |
| 12 | Azure Speech | Reading & Listening | Opens Azure configuration dialog | Settings -> Reading & Listening -> Azure Speech subpage | Subpage | Global service configuration | **ALREADY FROZEN** entry; subpage **PROPOSED FOR APPROVAL** |
| 13 | Azure Speech API key | Azure dialog | secure/local preference handler | Azure Speech subpage | Secret text field | No; global secret | **PROPOSED FOR APPROVAL** |
| 14 | Azure region | Azure dialog | Azure settings handler | Azure Speech subpage | Text/select field | No; global | **PROPOSED FOR APPROVAL** |
| 15 | Azure English voice | Azure dialog | Azure settings handler | Azure Speech subpage | Text/select field | No; global | **PROPOSED FOR APPROVAL** |
| 16 | Azure Arabic voice | Azure dialog | Azure settings handler | Azure Speech subpage | Text/select field | No; global | **PROPOSED FOR APPROVAL** |
| 17 | Backup & restore | Vault & Account | Opens legacy backup dialog | Settings -> Vault & Account -> Backup & Restore subpage | Subpage | Data-critical; global/account-bound | **ALREADY FROZEN** entry; subpage **PROPOSED FOR APPROVAL** |
| 18 | ChatGPT formatting login | Vault & Account | Supabase formatting session | Settings -> Vault & Account -> Formatting account | Subpage/row | No; global remote account | **PROPOSED FOR APPROVAL** |
| 19 | Recently Deleted | Vault & Account | Opens deleted note/folder manager | Settings -> Storage & Data -> Recently Deleted | Subpage | Room data; workspace-aware records | **PROPOSED FOR APPROVAL** |
| 20 | Security lock | Vault & Account | `securityLockEnabled`; setter; `MainActivity` auth gate | Settings -> Security & Privacy | Switch | Yes; global | **PROPOSED FOR APPROVAL** |
| 21 | Auto-lock timer | Vault & Account | `securityLockTimeoutMs`; setter | Settings -> Security & Privacy | Selector | Yes; global | **PROPOSED FOR APPROVAL** |
| 22 | Release readiness | Vault & Account | Opens hard-coded release checklist | Unresolved diagnostics/developer placement | Subpage/action | No; global | **STOP-AND-ASK** |
| 23 | Storage | Vault & Account | `StorageRepository.vaultStorageLabel`; refresh | Settings -> Storage & Data -> Storage usage | Read-only row | Derived local state; global app storage | **PROPOSED FOR APPROVAL** |

### 4.2 Backup, Restore, And Google Drive Operations

| # | Current label/capability | Current location | State / handler | Proposed destination | UI type | Compatibility / scope | Status |
|---|---|---|---|---|---|---|---|
| 24 | Backup reminder/status | Backup dialog | backup timestamps and health state | Backup & Restore subpage summary | Status row | Data-critical; global/account-bound | **PROPOSED FOR APPROVAL** |
| 25 | Backup vault | Backup dialog | `exportBackup` | Backup & Restore -> Local export | File creation action | Writes Android-compatible vault backup | **PROPOSED FOR APPROVAL** |
| 26 | Restore vault | Backup dialog | `restoreBackup` | Backup & Restore -> Local import | File picker/action | Destructive data import | **PROPOSED FOR APPROVAL** |
| 27 | Login / Connect Google Drive | Backup dialog | Drive sign-in/consent intent | Google Drive group in Backup & Restore | Account action | Google account-bound | **PROPOSED FOR APPROVAL** |
| 28 | Backup health | Backup dialog | repository health/status state | Backup & Restore summary/details | Status row | Data-critical | **PROPOSED FOR APPROVAL** |
| 29 | Push to Drive | Backup dialog | `pushGoogleDriveSync` | Backup & Restore -> Back up now | Action | Writes Drive backup | **PROPOSED FOR APPROVAL** |
| 30 | Restore from Drive | Backup dialog | `pullGoogleDriveSync` | Backup & Restore -> Restore from Drive | Action | Replaces/merges local vault per production engine | **PROPOSED FOR APPROVAL** |
| 31 | Restore/sync progress | Backup dialog | sync progress state | Backup & Restore transient progress | Progress/status | Runtime; account-bound | **PROPOSED FOR APPROVAL** |
| 32 | Last Drive update | Backup dialog | `lastGoogleDriveSyncAt` | Backup & Restore summary | Status row | Not in backup; local account metadata | **PROPOSED FOR APPROVAL** |
| 33 | Pull latest after conflict | Backup conflict UI | existing pull handler | Backup & Restore conflict sheet | Destructive/confirm action | Data-critical | **PROPOSED FOR APPROVAL** |
| 34 | Force push local vault | Backup conflict UI | `forcePushGoogleDriveSync` | Backup & Restore conflict sheet | Destructive/confirm action | Data-critical | **PROPOSED FOR APPROVAL** |
| 35 | Connected Drive account email | Backup dialog/status | `googleDriveAccountEmail` | Backup & Restore account summary | Status/value | Not in backup; account-bound | **PROPOSED FOR APPROVAL** |
| 36 | Last Drive manifest/check state | Internal backup status | `lastGoogleDriveManifestAt` | Backup & Restore details only if needed | Status/detail | Not in backup; account-bound | **PROPOSED FOR APPROVAL** |

No user-facing Disconnect handler is currently exposed by `SettingsViewModel`.
The sync repository clears/signs out as part of starting a different sign-in
flow, which is not equivalent to an approved Disconnect action. Disconnect is
therefore listed under unresolved decisions rather than invented here.

### 4.3 Formatting Account Operations

| # | Current label/capability | Current location | State / handler | Proposed destination | UI type | Compatibility / scope | Status |
|---|---|---|---|---|---|---|---|
| 37 | Formatting account email | Login dialog | Supabase auth input/session | Formatting account subpage | Text field/status | Not Android backup; global remote account | **PROPOSED FOR APPROVAL** |
| 38 | Formatting account password | Login dialog | Supabase auth input | Formatting account subpage | Secret field | Never in backup | **PROPOSED FOR APPROVAL** |
| 39 | Sign in formatting account | Login dialog | `signInFormattingAccount` | Formatting account subpage | Action | Remote session | **PROPOSED FOR APPROVAL** |
| 40 | Sign out formatting account | Login dialog | `signOutFormattingAccount` | Formatting account subpage | Action | Remote session | **PROPOSED FOR APPROVAL** |

The current product label says ChatGPT formatting while the implementation uses
a Supabase session. This audit does not rename the feature. Final visible naming
is an explicit approval decision.

### 4.4 Recently Deleted Operations

| # | Current label/capability | Current location | State / handler | Proposed destination | UI type | Compatibility / scope | Status |
|---|---|---|---|---|---|---|---|
| 41 | Deleted notes/folders list | Recently Deleted dialog | Room-backed deleted records | Storage & Data -> Recently Deleted | Subpage/list | Workspace-aware local data | **PROPOSED FOR APPROVAL** |
| 42 | Restore deleted note | Recently Deleted | `restoreNote` | Item contextual/action row | Action | Restores Room record | **PROPOSED FOR APPROVAL** |
| 43 | Restore deleted folder | Recently Deleted | `restoreFolder` | Item contextual/action row | Action | Restores hierarchy | **PROPOSED FOR APPROVAL** |
| 44 | Delete forever | Recently Deleted | permanent-delete handler | Item contextual confirmation | Destructive action | Creates existing safety backup | **PROPOSED FOR APPROVAL** |
| 45 | Delete all | Recently Deleted | clear-all handler | Recently Deleted overflow/footer confirmation | Destructive action | Creates existing safety backup | **PROPOSED FOR APPROVAL** |

### 4.5 Contextual Qur'an Reader Settings

These remain reader-specific. They must not be duplicated in global Settings
without a separate approval.

| # | Current label/capability | Current location | State / handler | Frozen/proposed destination | UI type | Backup / scope | Status |
|---|---|---|---|---|---|---|---|
| 46 | Arabic text size | Qur'an Options sheet | Qur'an Arabic font percentage | Contextual Qur'an Reader Settings | Slider | Yes; global reader preference | **ALREADY FROZEN** |
| 47 | Show translation | Qur'an Options sheet | translation enabled | Contextual Qur'an Reader Settings | Switch | Yes; global reader preference | **ALREADY FROZEN** |
| 48 | Translation source | Qur'an Options sheet | translation source ID | Contextual Qur'an Reader Settings | Selector | Yes; global reader preference | **ALREADY FROZEN** |
| 49 | Translation text size | Qur'an Options sheet | translation font percentage | Contextual Qur'an Reader Settings | Slider | Yes; global reader preference | **PROPOSED FOR APPROVAL**; omitted from frozen screenshot |
| 50 | Tajweed | Qur'an Options sheet | Tajweed enabled | Contextual Qur'an Reader Settings | Switch | Yes; global reader preference | **ALREADY FROZEN** |
| 51 | Word IDs | Debug builds only; local sheet state | debug-only toggle | No release Settings placement | Debug-only switch | No; ephemeral | **DEFERRED** |
| 52 | Audio downloads | Qur'an Options sheet | existing download manager route | Contextual Qur'an Reader Settings | Subpage/action | Downloaded local audio; not backup data | **DEFERRED** pending Stage 7 placement |
| 53 | Reciter | Current reciter picker/audio flow | selected reciter preference | Contextual Qur'an Reader Settings | Selector | Yes; global reader preference | **ALREADY FROZEN**, wiring deferred to Stage 7 |
| 54 | Default Tafsir source | Current per-ayah Tafsir source selection | selected Tafsir source | Contextual Qur'an Reader Settings | Selector | Yes; global reader preference | **ALREADY FROZEN**, wiring deferred to Stage 7 |
| 55 | Playback speed | Current Qur'an audio controls | audio playback speed | Audio controls or reader Settings, unresolved | Selector | Yes; global reader preference | **STOP-AND-ASK** before Stage 7 |

No production-wide PDF preference screen or user-visible PDF setting was found.
PDF progress, annotations, and reader state are operational content/state rather
than global Settings and remain a Stage 5 concern.

### 4.6 Frozen-Only Account And Theme Requirements

| # | Frozen capability | Current production state | Proposed handling | Status |
|---|---|---|---|---|
| 56 | Material You | No persisted Material You mode or dynamic scheme implementation found | Add only after persistence, system behavior, and backup compatibility are approved | **STOP-AND-ASK** |
| 57 | Account/profile row | No single MyVault account engine or profile destination exists; Workspace, Google Drive, and Supabase identities are distinct | Do not wire to an arbitrary account. Decide whether it is informational, workspace profile, or a future account destination | **STOP-AND-ASK** |

### 4.7 Compatibility-Only Persisted Presentation State

These are not proposed as Settings rows. They must remain backward-compatible
and must not be destructively migrated during Stage 3.

| # | Persisted state | Current use | Placement | Status |
|---|---|---|---|---|
| 58 | Library root view mode | Legacy Library presentation preference | No visible Frozen control | **DEFERRED** compatibility only |
| 59 | Library per-folder view modes | Legacy per-folder presentation | No visible Frozen control | **DEFERRED** compatibility only |
| 60 | Expanded folder state | Restores browser/Explorer expansion | No Settings row | **DEFERRED** compatibility only |
| 61 | Pinned expanded state | Persisted model exists; current backup mapping is incomplete | No Settings row | **STOP-AND-ASK** if Stage 3 touches preference serialization |

## 5. Frozen Coverage

Production capabilities that map cleanly to Frozen destinations are:

- Theme entry, subject to legacy model migration approval.
- Accent colour.
- Dashboard font size.
- Note editor font size.
- Show full note titles.
- Show full file titles.
- Default note view.
- Default Listen provider.
- Azure Speech entry.
- Google Drive entry.
- Backup / Restore entry.
- Arabic text size, translation display/source, Tajweed, reciter, and default
  Tafsir in contextual Qur'an Reader Settings.

The Frozen Master additionally specifies Material You, OLED, Follow system +
Dark, Follow system + OLED, and an Account/profile row, none of which currently
has a complete production-state mapping.

## 6. Proposed Complete Settings Hierarchy

This is the exact conceptual hierarchy proposed for approval. Compact rows,
grouped surfaces, semantic labels, and Frozen tokens apply to the main screen
and all subpages.

```text
Settings

[Profile / account row]
  STOP-AND-ASK: meaning and destination are unresolved

APPEARANCE
  Theme                         -> Theme subpage
  Accent colour                 -> swatch selector
  Material You                  -> switch, only after model decision

READING & DISPLAY
  Dashboard font size           -> selector
  Note editor font size         -> selector
  Note preview                  -> selector
  Show full note titles         -> switch
  Show full file titles         -> switch
  Default note view             -> selector

READING & LISTENING
  Default Listen provider       -> selector
  Azure Speech                  -> Azure Speech subpage

SECURITY & PRIVACY
  Security lock                 -> switch
  Auto-lock timer               -> selector

STORAGE & DATA
  Storage usage                 -> read-only detail row
  Recently Deleted              -> Recently Deleted subpage

VAULT & ACCOUNT
  Google Drive                  -> Backup & Restore subpage, account section
  Backup / Restore              -> same Backup & Restore subpage
  ChatGPT formatting login      -> Formatting account subpage

UNRESOLVED / NOT SHOWN
  Release readiness             -> STOP-AND-ASK
```

### 6.1 Theme Subpage

```text
Theme

STYLE
  Material You

THEME
  Light
  Dark
  OLED
  Follow system + Dark
  Follow system + OLED

ACCENT
  Manual accent swatches
  Disabled when Material You is enabled
```

Before implementation, approve the mapping from legacy `Auto` and the storage
and backup representation for the two new system-following modes, OLED, and
Material You.

### 6.2 Azure Speech Subpage

```text
Azure Speech

CONNECTION
  API key
  Region

VOICES
  English voice
  Arabic voice
```

Values continue to use existing production handlers. Secrets remain local and
must not be added to Android backup.

### 6.3 Backup & Restore Subpage

```text
Backup & Restore

GOOGLE DRIVE
  Connected account / Connect Google Drive
  Last Drive update
  Backup health
  Back up now
  Restore from Drive

LOCAL BACKUP
  Export backup file
  Restore backup file

CONFLICT RESOLUTION (only when required)
  Pull latest
  Force push local vault

DETAILS
  Sync/restore progress and latest status only when active/relevant
```

The main Settings screen remains restrained. It does not display every Drive
diagnostic, timestamp, or conflict action permanently. Conflict actions appear
only in the existing conflict condition and use the production engine.

### 6.4 Formatting Account Subpage

```text
Formatting account

Disconnected:
  Email
  Password
  Sign in

Connected:
  Connected email
  Sign out
```

The final visible product name is unresolved. No Supabase credentials or
session secrets enter Android backup.

### 6.5 Recently Deleted Subpage

```text
Recently Deleted

  Deleted notes and folders
  Restore item
  Delete forever
  Delete all (confirmation required)
```

Existing safety-backup and permanent-delete semantics remain unchanged.

## 7. Global Versus Workspace Scope

- Appearance, narration, security, Drive account metadata, Azure, and Qur'an
  reader defaults are currently global app preferences.
- The selected active workspace is global navigation state but switches between
  distinct Personal and Islamic Corpus data. Its approved entry remains the
  Stage 1 Explorer profile/header chooser, not a duplicate Settings control.
- Recently Deleted records retain their production workspace/entity semantics.
- Drive backup/restore may contain multi-workspace data and is account-bound.
  Stage 3 must not narrow the backup payload to the active workspace.
- Supabase formatting account state is a separate remote identity from Google
  Drive and from the workspace chooser.

## 8. Compatibility Findings

1. The current Android backup mapper includes theme, workspace, accent, legacy
   font size, dashboard/note sizes, note preview, default note view,
   auto-tagging, security, lock timeout, and Qur'an preferences.
2. The current mapper does not include full-title switches, narration provider,
   Azure secrets/settings, Drive account/timestamps, or formatting-account
   credentials/session.
3. The persisted `pinnedExpanded` model is not fully represented by the current
   backup mapper. Stage 3 must not opportunistically repair this unrelated
   serialization gap without approval.
4. Legacy `Auto` theme must not be silently reinterpreted until its exact mapping
   to Follow system + Dark/OLED is approved.
5. Material You and OLED require new production theme state. Adding them may
   affect Android backup compatibility and therefore requires a documented
   migration/default policy.
6. Library view-mode values remain stored even though Stage 2 removed the
   visible selector. Stage 3 must leave these values intact.
7. Security settings are backed up, but authentication itself remains native
   device biometric/device credential behavior.
8. Azure API keys, passwords, and session tokens must never be added to backup
   merely to make Settings uniform.

## 9. Conflicts And Explicit Decisions Required

Stage 3 implementation must not begin until these are resolved:

1. **Theme migration**: map legacy `Auto`, choose defaults for existing users,
   and approve persistence/backup values for Material You, OLED, Follow system +
   Dark, and Follow system + OLED.
2. **Account/profile row**: define what account it represents. Production has
   workspace identity, Google Drive identity, and Supabase formatting identity,
   but no unified MyVault account.
3. **Google Drive Disconnect**: no user-facing disconnect handler exists. Decide
   whether Stage 3 only supports Connect/change account through the existing
   flow or whether Disconnect becomes separately authorized functionality.
4. **Release readiness**: decide whether this developer-oriented checklist
   remains user-visible, moves to an approved diagnostics destination, or is
   removed from release UI. It cannot silently disappear.
5. **Dormant automatic tag suggestions**: identify whether the preference still
   drives a production feature. Do not expose or delete it until known.
6. **Dormant legacy general font size**: retain compatibility without adding a
   redundant visible row unless specifically approved.
7. **Formatting feature naming**: approve the visible name for the existing
   `ChatGPT formatting login` / Supabase account destination.
8. **Storage action scope**: production currently calculates display-only usage;
   there is no cache cleanup/management handler. Do not imply cleanup controls.
9. **Qur'an translation text size**: preserve it contextually or approve its
   omission from the final frozen reader settings.
10. **Qur'an playback speed**: approve whether it remains in audio controls or
    enters contextual Reader Settings before Stage 7.
11. **Qur'an audio downloads**: final placement remains deferred to Stage 7.

## 10. Deferred Requirements Affected

- Narration settings receive a proposed Settings home; the global narration
  mini-player remains separately deferred and unchanged.
- Qur'an audio-download management remains deferred to Stage 7.
- Dashboard and global Search remain legacy temporary exceptions.
- Advanced Note Editor and PDF capabilities remain their later-stage gates.
- Workspace Attachments, Aggregate Favourites, and Qur'an Reflections retain
  their Stage 2 temporary destinations and are not resolved by Settings.
- Outgoing Share remains deferred new functionality.

See `docs/DEFERRED_REQUIREMENTS.md` for the canonical current register.

## 11. Stage Gate

No Stage 3 UI implementation, theme migration, preference migration, backup
format change, or Settings navigation change is authorized by this document.
Resolve section 9 and explicitly authorize Stage 3 before code changes begin.
