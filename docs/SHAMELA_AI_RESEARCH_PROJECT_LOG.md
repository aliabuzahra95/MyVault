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
- Implementation commit: `6d749ceaf4013df34add6b5f45612943997f6b30`
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
- Implementation commit: `6d749ceaf4013df34add6b5f45612943997f6b30`

## Stage 6 - Multi-provider AI

- Status: COMPLETE
- Provider boundary: normalized `AiProviderClient`, bounded request/response
  models, common gateway, provider-specific errors, cancellation-aware HTTPS,
  and a delta callback ready for the streaming stage.
- Providers: OpenAI Responses API (`gpt-5-mini`), Gemini Generate Content
  (`gemini-2.5-flash`), and Kimi Chat Completions (`kimi-k2.6`).
- Credentials: optional provider overrides are encrypted with Android Keystore
  AES-GCM in app-private device-local preferences. Platform backup is disabled
  for all preferences and the manual MyVault backup does not include this
  store. Credentials are never sent to Shamela or written to logs/tests.
- Existing condition: provider keys already configured through generated
  `BuildConfig` fields remain the fallback used by older MyVault AI features.
  This project did not broaden that unrelated security refactor.
- Live Android result: the common contract returned `MyVault provider
  connected.` from ChatGPT in 2,792 ms, Gemini in 1,280 ms, and Kimi in 905 ms
  on the API 36.1 emulator. Kimi's documented production constraint of
  temperature `0.6` for `kimi-k2.6` is enforced.
- Tests: request construction, response parsing, empty/malformed boundaries,
  live calls for all three providers, JBR 21 unit tests, lint, and debug build.
- Implementation commit: `a600f4e70da48a0cd1fba645c4e6ad212bb4b093`
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 7 - Shamela-grounded AI Answers

- Status: COMPLETE
- Flow: the selected provider converts a natural-language question into one
  bounded Shamela search phrase; MyVault validates and executes the fixed
  read-only search; only the question and at most six retrieved passages are
  then supplied to the selected provider for explanation.
- Tool safety: the model cannot call MCP directly. The only Stage 7 operation is
  the hard-coded `shamela_search_pages` path through `ShamelaResearchProvider`,
  with its existing query, result-count, response-size, timeout, and
  cancellation bounds.
- Grounding: source text is explicitly marked untrusted data. The provider is
  instructed not to follow passage instructions, fill evidence gaps from
  memory, rewrite Arabic as a direct quotation, or invent metadata/citations.
- Raw search: an explicit `Search Shamela...` composer command still returns
  source cards directly without generating an AI explanation.
- Live result: an Arabic question completed query planning, retrieved six real
  Shamela passages, and generated an Arabic ChatGPT answer. The installed
  production UI showed the outlined question, plain answer text, and verified
  sources as separate cards. When retrieved passages did not establish the
  requested phrase precisely, the answer stated that limitation.
- Tests: grounded prompt boundaries, six-source cap, missing-metadata handling,
  verbose query-plan normalization, live authenticated retrieval/generation,
  and installed production rendering passed.
- Implementation commit: `d0c52f46c8e3584ef490e2a3746ac11817c19bda`
- Visual evidence: `artifacts/shamela-ai/stage-7/` (not tracked in Git).
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 8 - Streaming Answers

- Status: COMPLETE
- Transport: real SSE streaming is implemented independently for OpenAI
  Responses, Gemini `streamGenerateContent`, and Kimi Chat Completions. The
  existing non-streaming path remains available for the compact Shamela query
  planning request.
- Safety: stream reads retain provider authentication, coroutine cancellation,
  connection/read timeouts, a 4 MiB response limit, and a 100,000-character
  generated-answer limit. Provider stream errors are normalized without
  exposing credentials or response bodies.
- UI: streamed deltas are coalesced into updates at most every 50 ms. The
  conversation follows generation only while the reader remains at the bottom;
  deliberately scrolling upward disables automatic following.
- Live provider result: ChatGPT (`gpt-5-mini`) delivered 291 chunks / 1,711
  characters, Gemini (`gemini-2.5-flash`) 7 / 1,841, and Kimi (`kimi-k2.6`)
  301 / 1,878. For every provider, the concatenated chunks exactly equalled the
  returned final answer.
- Installed-app result: the production AI destination visibly rendered a
  partial grounded ChatGPT answer before generation completed, then replaced it
  with the exact final answer and attached the six retrieved Shamela source
  cards.
- Tests: provider request flags and delta parsers, full JBR 21 unit suite,
  Android lint, debug assembly, live provider instrumentation, and installed
  production rendering passed.
- Visual evidence: `artifacts/shamela-ai/stage-8/` (not tracked in Git).
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 9 - Open Source

- Status: COMPLETE
- Implementation: every evidence card exposes `Open source`, which opens a
  fully expanded, dismissible source presentation with the selected book,
  author, provenance, citation, and readable RTL Arabic page content.
- Context retrieval: MyVault invokes only the fixed read-only
  `shamela_get_page` tool. Retrieval is bounded to previous/current/next pages;
  the current page may read at most three server-split body parts. No book-wide
  download or automatic pagination is performed.
- Live result: a real grounded source from `شعب الإيمان - ط الرشد` opened with
  the server-provided author, footnote provenance, citation, preceding context,
  selected page, and following context. The first visual run exposed a
  partially expanded sheet; it was corrected to open fully expanded and the
  installed app was recaptured.
- Tests: source-page parsing and metadata absence, JBR 21 unit tests, debug
  assembly, live authenticated context retrieval, and installed UI passed.
- Visual evidence: `artifacts/shamela-ai/stage-9/source-context-light.png`
  (not tracked in Git).
- Physical Samsung: NOT TESTED; only the Android emulator is connected.
