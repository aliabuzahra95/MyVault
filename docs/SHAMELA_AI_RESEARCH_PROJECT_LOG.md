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
- Implementation commit: `21e56fe913805c04509de6a0c240d4b006311f8b`
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
- Implementation commit: `d39e4cf8d285b8e73479a6675024b3d2a98dcaa4`
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 10 - Verify Quote

- Status: COMPLETE
- Implementation: a compact composer mode selector adds `Verify quote` inside
  the existing AI conversation. The entered Arabic quotation is checked first
  with the read-only `shamela_search_phrase` consecutive-phrase tool. Only when
  that returns no evidence does MyVault run one bounded regular Shamela search
  to distinguish similar wording from not located.
- Verification boundary: ChatGPT, Gemini, and Kimi are not invoked and cannot
  declare a quotation verified. Exact/similar/not-located classifications are
  derived exclusively from live Shamela retrieval.
- Live result: `الاستواء معلوم` returned exact evidence;
  `الاستواء معلوم مجهول والكيف` returned similar evidence without an exact
  consecutive match; `زطغث ضظقث` returned not located with zero sources.
- UI: exact and similar results render their matched Arabic evidence as normal
  source cards with `Open source`; not-located remains a restrained inline
  response. The mode selector and Arabic placeholder fit the installed 412dp
  equivalent screen without clipping.
- Tests: source identity, exact/similar/not-located live instrumentation, JBR
  21 unit tests, debug assembly, and installed mode UI passed.
- Visual evidence: `artifacts/shamela-ai/stage-10-quote-mode-selected.png`
  (not tracked in Git).
- Implementation commit: `1025f9f76dd6655f733a27e171ea33e4ef1b5a96`
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 11 - Compare Scholars

- Status: COMPLETE
- Flow: the selected AI produces only a bounded JSON research plan containing
  the topic and two-to-four named scholars. MyVault resolves each scholar with
  `shamela_resolve`, then executes a separate `shamela_search_pages` call scoped
  to that resolved author ID. Only those isolated evidence groups are sent back
  for the streamed comparison.
- Identity safety: passages never move between scholar groups. Missing or
  unresolved evidence is represented explicitly and the model is instructed
  not to fill it from memory. A zero-evidence live attempt correctly returned
  no comparison.
- Planner hardening: live testing caught ChatGPT copying a schema placeholder
  as the search topic. The planner now uses a concrete example and the parser
  rejects placeholder text instead of issuing an empty/misleading search.
- Live result: the final Arabic topic `الله` resolved Ibn Taymiyyah to author ID
  54 and al-Nawawi to author ID 44. Each independent scope returned three
  sources; ChatGPT streamed a 2,298-character comparison tied to `[S1]`-`[S6]`
  and stated the limitations of the retrieved excerpts.
- UI: `Compare` is a compact third composer mode. The installed app rendered
  normal flowing comparison text with scholar headings and retained separate
  source-card identity below the answer.
- Tests: bounded plan parsing, explicit missing-evidence prompts, direct scoped
  retrieval, no-evidence refusal, live end-to-end comparison, targeted unit
  tests, debug assembly, and installed UI passed.
- Visual evidence: `artifacts/shamela-ai/stage-11/comparison-light.png` (not
  tracked in Git).
- Implementation commit: `34d13f1f10b6ef27f31a0954b7a3786a619d2033`
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 12 - Save Source to MyVault

- Status: COMPLETE
- Source actions: evidence cards expose `Save to Note`; the fully expanded
  source view exposes `Copy Arabic`, `Copy citation`, and `Save to Note`.
- New Note: MyVault creates a normal root Study Note through `NoteRepository`
  and saves the Arabic passage, book, author, volume/page location,
  provenance, and verified/fallback citation through the existing rich-text
  transaction.
- Existing Note: `Add passage to Note` searches the real production Note list
  and appends through the existing Note repository. Rich-text marks and Note
  links retain their ranges, legacy HTML remains HTML, plain block Notes remain
  block-based, tables are retained, and the normal version snapshot path runs
  before mutation. Invalid legacy rich text fails safely instead of flattening
  the Note.
- Live result: a real Shamela footnote passage was saved as a new root Study
  Note and opened in the Stage 4 Note Reader with all source metadata visible.
  A second real author-body passage was then appended to that same Note; the
  original passage and metadata remained intact above the appended source.
- Tests: source payload/citation tests, focused AI unit suite, debug assembly,
  installed production create/open/append/reopen flow, and `git diff --check`
  passed.
- Visual evidence: `artifacts/shamela-ai/stage-12/` (not tracked in Git).
- Implementation commit: `a419af1b6159b86eda347178676e753e43c2ff3a`
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Stage 13 - Full UI Refinement

- Status: COMPLETE ON EMULATOR; PHYSICAL SAMSUNG ACCEPTANCE REMAINS STAGE 16.
- Structure: the installed production destination retains a compact MyVault
  header, provider selector and Shamela state; outlined user questions; plain
  flowing AI responses; verified evidence cards; and one persistent multiline
  composer. No AI-answer card, full-screen progress modal, or stacked provider
  cards were introduced.
- Evidence readability: card titles and metadata were increased one restrained
  step, Arabic passages now render at 17sp/28sp with explicit RTL direction,
  and the source-detail actions were reduced to compact `Arabic`, `Citation`,
  and `Save` controls so all three remain reachable at 360dp.
- Composer: verified with a long multiline question, keyboard open/closed, and
  a long conversation. It expands to six lines, remains above the native IME,
  keeps the send control thumb-reachable, and does not cover source content.
- Responsive inspection: actual installed UI passed at 360dp, 390dp, 412dp,
  and 430dp equivalent widths. The header, provider selector, Arabic source
  cards, source actions, and composer did not clip or overflow.
- Theme inspection: actual Light and Dark states retained readable text,
  borders, evidence surfaces, muted metadata, provider status, and composer
  contrast.
- Visual evidence: `artifacts/shamela-ai/stage-13/` (not tracked in Git).
- Implementation commit: `c8af802e737573320bb7d50fe6fef4e7dd3b832e`

## Stage 14 - Failure and Edge-Case Hardening

- Status: COMPLETE ON EMULATOR; PHYSICAL SAMSUNG ACCEPTANCE REMAINS STAGE 16.
- Cancellation: the active research job is now owned explicitly by the
  ViewModel. The composer exposes a real Cancel request action while work is in
  progress, the underlying HTTP connection is disconnected, and cancellation
  cannot be converted into a generic provider error or leave a spinner active.
- Network/auth recovery: offline, timeout, 401, 429, and provider-unavailable
  failures map to concise recovery text. A Shamela 401 clears only the invalid
  local Shamela session and returns the header to Connect; OAuth cancellation
  is reported without creating an authenticated state.
- Protocol hardening: malformed JSON-RPC, unexpected SSE, unsupported tools,
  missing required tool arguments, and unexpected arguments fail closed.
  Only the four read-only operations used by MyVault research are callable.
- Content edges: no-result and quote-not-found states remain truthful; partial
  citations remain absent rather than being fabricated; passages, page context,
  questions, prompts, streamed answers, result counts, and conversation UI are
  bounded.
- Runtime evidence: installed debug app passed user cancellation, offline
  recovery, background/foreground, portrait/landscape recreation, and return to
  portrait. The completed and failed messages remained, no request duplicated,
  and no Cancel/spinner state remained after completion.
- Automated evidence: malformed MCP JSON, unexpected SSE, tool allow-list,
  argument mismatch, safe error mapping, very-long passage bounds, and partial
  citation behavior have targeted unit coverage. Full unit, lint, and debug
  assembly are run at the stage checkpoint.
- Visual evidence: `artifacts/shamela-ai/stage-14/` (not tracked in Git).
- Physical Samsung: NOT TESTED; only the Android emulator is connected.
- Implementation commit: `298b7b1a7c15e09023223064338c30b3c4c4b491`

## Stage 15 - Performance, Privacy, and Security

- Status: COMPLETE IN CODE/AUTOMATED REVIEW; PHYSICAL PERFORMANCE ACCEPTANCE
  REMAINS STAGE 16.
- Main-thread work: provider credential decryption and fallback lookup now run
  on `Dispatchers.IO`; Shamela response parsing, page assembly, source cleaning,
  and result shaping run on `Dispatchers.Default`; network I/O remains on
  `Dispatchers.IO`.
- Lifecycle/duplication: the ViewModel owns one active request, blocks duplicate
  submission, cancels its actual connection, survives configuration change, and
  caps the in-memory conversation at 200 messages. Streaming UI publication is
  coalesced to at most one update per 50 ms.
- OAuth: access/refresh state is AES-GCM encrypted with an Android Keystore key,
  excluded from Android backup, and refresh is serialized by a Mutex to prevent
  concurrent refresh-token races. No token or authorization header is logged.
- Provider credentials: device overrides are AES-GCM encrypted with a separate
  Android Keystore key and excluded from Android backup. The approved direct
  Android architecture may also use build-configured fallback provider keys;
  such embedded fallback keys are inherently extractable from a distributed
  APK and must be treated as restricted/rotatable provider credentials. A
  backend would be required to fully conceal them, but this project explicitly
  does not introduce one.
- MCP boundary: only `shamela_search_pages`, `shamela_get_page`,
  `shamela_search_phrase`, and `shamela_resolve` are allowed. Their argument
  names and required fields are validated locally. Tool discovery, responses,
  pages, passages, counts, context parts, and payload bytes are bounded.
- Prompt injection: Shamela text is preserved as quoted source data inside
  explicit source delimiters and paired with system instructions that prohibit
  treating retrieved text as system/tool instructions. The Android client does
  not let model output invoke MCP tools.
- Privacy/logging: Android cleartext traffic is disabled; AI requests use TLS;
  OpenAI requests set `store=false`; no AI Research source contains debug logs,
  token prints, credential prints, or stack-trace prints. Error details shown to
  users are bounded and authentication/rate-limit failures are sanitized.
- Bounds: 8 search results, 6 grounding sources, 12 comparison sources, 3 page
  parts, previous/current/next page context, 48,000-character prompts,
  100,000-character streamed answers, 4 MiB network responses, and finite MCP
  discovery pagination. No autonomous/repeated tool loop exists.
- Tests: targeted prompt-injection delimiting, MCP allow-list, result/passage
  bounds, provider payload, streaming parsing, and error-sanitization tests pass.
- Physical Samsung: NOT TESTED; only the Android emulator is connected.

## Comprehensive Evidence-Hierarchy Amendment

- Status: IMPLEMENTED IN WORKTREE; LIVE BENCHMARK AND PHYSICAL SAMSUNG
  ACCEPTANCE REMAIN OPEN.
- Starting production checkpoint: `abdb103f8bb4f499f8e59f6de62632d3cb550150`
  on `frozen-design-master-port`.
- The previous search-first grounded answer path has been replaced by a bounded
  plan, multi-pass retrieval, full-context expansion, provenance/role
  classification, exact-quote verification, evidence ranking, synthesis, and
  post-answer source audit.
- Named-scholar research now prioritizes resolved author-scoped primary books
  while retaining secondary quotations, explicit attributions, position
  reports, and disagreement material. Secondary wording is searched back into
  the target scholar's corpus before it can be promoted to primary evidence.
- Deliberate alternate-term and contradiction passes are performed before final
  selection. Fiqh questions also use `shamela_scan_consensus` as a disagreement
  discovery primitive, never as an automatic consensus verdict.
- The MCP allow-list now contains six read-only research operations:
  `shamela_resolve`, `shamela_search_pages`, `shamela_search_phrase`,
  `shamela_get_page`, `shamela_verify_quote`, and `shamela_scan_consensus`.
- Every selected quotation must already occur on the retrieved author-body page
  and pass exact/orthographic verification. Long-page extraction windows are
  centered on the real search match.
- Ordinary and scholar-comparison evidence packets are cached transiently by
  normalized question. Provider switches reuse the same verified packet and
  rerun only synthesis and final audit.
- Synthesis is structured and claim-linked. Unknown source IDs, declared/rendered
  ID mismatches, substantive uncited paragraphs, unsupported consensus language,
  and unsafe final audits are blocked.
- Source cards remain application-generated and open the stored Shamela book and
  page identity. Evidence class, role, and provenance are visible.
- Developer traces now record actual MCP tool calls and bounded arguments,
  candidates, classifications, selected/rejected evidence, packet reuse, model
  evidence IDs, and final verdict without credentials.
- Architecture and the eight-case live benchmark gate are documented in
  `docs/SHAMELA_RESEARCH_ENGINE_ARCHITECTURE.md`.
- Focused research/provider/UI parsing tests and debug Kotlin compilation pass
  with JBR 21 after this amendment. Full unit, lint, debug assembly, live
  provider benchmarks, and physical Samsung acceptance are still pending at
  this checkpoint.
- Backup/restore, Google Drive backup architecture, Room, Web, canonical Qur'an,
  Memorise, and PDF architecture were not changed.

## Final Bounded Research-Quality Attempt

- Status: COMPLETE ON AUTHENTICATED EMULATOR; FINAL RECOMMENDATION IS `NO-GO`
  FOR GENERAL SCHOLARLY SYNTHESIS.
- Starting commit: `cc6a5b88ffc69cbbfe81f01e282a5f1cab2a9599`.
- Recovery tags: `pre-final-shamela-research-attempt` and
  `pre-shamela-ai-research-integration`.
- Root cause confirmed: remote retrieval and AI extraction could locate useful
  material but did not reliably distinguish a decisive explicit preference from
  nearby contextual propositions, and extraction could omit a decisive page
  from a long candidate packet.
- Bounded correction: refined classical query planning, a generic fiqh
  recommendation/obligation search axis, explicit-preference ranking, one
  focused review when a decisive page is skipped, context downgrading, and a
  supporting-evidence-only synthesis boundary for scholar-position answers.
- Benchmark 1 passed through OpenAI, Gemini, and Kimi using the same cached
  dossier. All three returned the recommended-not-obligatory ruling and the
  primary citation opened exactly to *Jami al-Masa'il*, volume 9, page 312.
- Additional benchmark outcomes: aqidah PASS; quote verification PASS; tafsir
  FAIL SAFE; hadith commentary FAIL SAFE; secondary attribution FAIL.
- Decision: the feature is useful experimentally for source discovery and some
  tightly scoped questions, but the failed attribution case prevents a reliable
  scholarly-answer claim. Further improvement would exceed this bounded attempt
  and require dedicated retrieval infrastructure that was explicitly out of
  scope.
- Physical Samsung: NOT TESTED because no physical device was connected. The
  user's fallback instruction authorized completion and signed-APK delivery
  without the phone.
- Full technical results and rollback guidance are recorded in
  `docs/SHAMELA_FINAL_EXPERIMENT_ASSESSMENT.md`.
