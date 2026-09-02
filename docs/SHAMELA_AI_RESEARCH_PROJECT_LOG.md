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

- Status: COMPLETE
- OAuth client: public native client using authorization code, PKCE S256, and
  refresh tokens through AppAuth for Android.
- Redirect: `com.myvault.app:/oauth2redirect/shamela`.
- Token security: AppAuth state encrypted with Android Keystore AES-GCM in
  app-private preferences; excluded from platform and manual MyVault backups.
- Account state: the live Shamela service accepted account activation and the
  installed Android app completed browser authorization successfully. MyVault
  returned directly to the AI workspace and restored the OAuth state after an
  app restart.
- Live contract: `docs/SHAMELA_MCP_LIVE_CONTRACT.md`.
- Tests: OAuth contract test, full unit suite, lint, and debug assembly passed.
- Live result: access-token acquisition and refresh-capable AppAuth state were
  proven without logging or backing up credentials.
- Implementation commit: `4ee15e0322e5d7e656020528cb154fea4c1a32d4`
- Emulator note: the initial blank custom tab was an emulator Chrome native
  library startup failure. After a clean browser restart, the unchanged OAuth
  request rendered the real Shamela account flow.

## Stage 3 - Real MCP Initialization

- Status: COMPLETE
- Implementation: minimal JSON-RPC client over the existing HTTPS stack with
  bearer authentication, JSON/SSE parsing, protocol negotiation, initialized
  notification, bounded pagination, timeout, cancellation, and structured
  errors.
- Live result: authenticated `initialize`, `notifications/initialized`, and
  `tools/list` succeeded from the installed app. The negotiated protocol is
  `2025-11-25`; the server is `shamela` version `1.3.0`; responses use SSE; the
  endpoint behaved statelessly without an MCP session header.
- Catalogue: 34 read-only, non-destructive tools with complete live schemas are
  captured in `docs/SHAMELA_MCP_TOOL_CATALOG.json`.
- Harmless invocation: `shamela_health` returned status `ok` and a readable
  searchable corpus through the same authenticated Android client.
- Runtime: the AI header reaches `Shamela · Connected` only after MCP discovery
  succeeds; initialization errors expose an inline retry action.
- Tests: JSON, SSE, and structured-error parser tests; full unit suite, lint,
  debug assembly, live instrumentation discovery, and runtime restart check.
- Implementation commit: `57fbf80d79bf3ca71766c67271458dd448de5a5f`
- Physical Samsung: NOT TESTED; only the Android emulator is currently
  connected.

## Stage 4 - Raw Shamela Search

- Status: COMPLETE
- Implementation: the full-screen AI composer now performs a bounded direct
  Shamela search and renders real Arabic source cards in the conversation.
- Provenance: author body, editor footnote, and comment snippets are normalized
  separately. Missing provenance remains visibly unavailable rather than being
  guessed.
- Live result: keyword, exact phrase, book-constrained search, surrounding-page
  context, and server-formatted citation calls all succeeded. The five calls
  completed in approximately 316-365 ms each in the final acceptance run.
- Runtime: installed production screen rendered six real sources for
  `الاستواء معلوم`, including author text and separately labeled footnotes.
- Rate limit: no 429 response observed during bounded sequential testing; no
  load test was attempted.
- Tests: structured MCP parsing, real result-shape normalization, HTML marker
  cleanup, and live authenticated instrumentation acceptance passed.
- Implementation commit: pending combined Stage 4/5 checkpoint commit.
- Visual evidence: `artifacts/shamela-ai/stage-4/` (not tracked in Git).

## Stage 5 - Research Provider Abstraction

- Status: COMPLETE
- Implementation: small `ResearchProvider` boundary, concrete
  `ShamelaResearchProvider`, and transient `ResearchSource` model. Raw MCP logic
  remains outside Compose and the ViewModel.
- Persistence: retrieval is transient; no Room, backup, Drive, or Web changes.
- Source fields: only live-supported book/page identity, title, author, passage,
  provenance, part/printed page, citation, and retrieval time are normalized.
- Tests: body and footnote separation and structured-result fallback passed.
- Implementation commit: pending combined Stage 4/5 checkpoint commit.
