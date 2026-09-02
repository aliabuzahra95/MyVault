# Shamela AI Research Project Log

## Stage 0 - Freeze Current MyVault

- Status: COMPLETE
- Branch: `frozen-design-master-port`
- Starting production commit: `9b15931c23c9d55013e97b9c41f1737345a4d544`
- Pre-project stabilization commits: `dc309a3`, `308ea1f`
- Rollback commit: `308ea1fe3302ea9a3f64b3b7983d1b350b9db8d4`
- Recovery tag: `pre-shamela-ai-research-integration`
- Remote: `origin` (`git@github.com:aliabuzahra95/MyVault.git`)
- Push: COMPLETE
- Verification: JBR 21 unit suite, Android lint, debug APK, and diff check passed before the tag.
- Excluded from Git: existing untracked runtime evidence under `artifacts/` and detached APK signature sidecars.

## Stage 1 - Isolated AI Destination

- Status: COMPLETE
- Implementation: dedicated `AI` drawer destination, root route, full-screen conversation surface, persistent bottom composer, outlined user messages, plain-flowing assistant messages, compact provider selector, and subtle Shamela status.
- Provider preference: device-local and excluded from the manual MyVault backup payload.
- Networking: intentionally not enabled in this stage.
- Tests: JBR 21 unit suite and debug assembly passed.
- Runtime: installed `com.myvault.app` on the Medium Phone API 36.1 emulator; drawer, route, Back, keyboard-safe composer, send flow, and provider persistence after process restart passed.
- Implementation commit: `782d2ee477374734f22dd031849395d40a8a7c18`
- Visual evidence: `artifacts/shamela-ai/stage-1/` (not tracked in Git).
- Physical Samsung: NOT TESTED; no physical device was connected.

## Stage 2 - Shamela OAuth

- Status: IMPLEMENTED; LIVE AUTHORIZATION AWAITING EMAIL ACTIVATION
- OAuth client: public native client using authorization code, PKCE S256, and
  refresh tokens through AppAuth for Android.
- Redirect: `com.myvault.app:/oauth2redirect/shamela`.
- Token security: AppAuth state encrypted with Android Keystore AES-GCM in
  app-private preferences; excluded from platform and manual MyVault backups.
- Account state: the live Shamela service accepted account creation and sent its
  required activation link. No access token is available until the user opens
  that link.
- Live contract: `docs/SHAMELA_MCP_LIVE_CONTRACT.md`.
- Tests: OAuth contract test and debug assembly passed before the activation
  gate. Full unit/lint verification is recorded with the Stage 2 commit.
- Emulator note: the initial blank custom tab was an emulator Chrome native
  library startup failure. After a clean browser restart, the unchanged OAuth
  request rendered the real Shamela account flow.
