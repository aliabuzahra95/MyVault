# Note UX modernisation: audit and verification

## Baseline

- Repository: MyVault Complete Before Tutor; branch frozen-design-master-port.
- Starting HEAD: fb0e8bdf01e1cdd357478533646e35497f0cfee8.
- Recovery tag (pushed): pre-note-ux-modernisation-20260909-fb0e8bd.
- No tracked changes at entry. Historical untracked artifacts are preserved.
- Six supplied Samsung screenshots are the visual baseline.

## Pipeline inspected before implementation

EditorScreen sends its current plain body and title to NoteViewModel.runFormattingTool.
NoteFormattingSessionStore retains per-note progress/results in memory. The repository
calls NativeNoteFormattingGenerator, which builds prompts, optionally chunks long notes,
and dispatches through DefaultNoteFormattingProviderGateway. The output engine cleans
HTML and uses approximate content-preservation checks. EditorScreen previews through
the existing RichImportParser, then replaces or appends the imported document. Normal
autosave calls NoteRepository.saveRichText, whose version capture is time-throttled.

Findings:

- The ViewModel unconditionally downgrades Smart to Fast, ignoring the quality control.
- Preservation uses ratios, word bags and selected substrings, not exact textual equality.
  It silently substitutes local formatting on failure. Added headings are allowed even
  though the strict contract forbids added wording.
- Results do not retain their source revision. Applying an older preview can overwrite
  subsequent typing. Import clears note links. Version throttling can skip the exact
  pre-formatting document.
- Both retained modes already promise lossless wording in their system prompts; the
  UI misleadingly promises freer rewriting. This pass follows the brief's stronger
  fixture requirement: preserve all wording in both modes, vary hierarchy, not facts.
- CleanFormat/FormatNote remain historical gateway actions; do not remove compatibility
  merely because the editor only exposes StructureOnly/IntelligentStructure.
- Reading/editor actions duplicate attachment and knowledge access and use generously
  spaced generic modal sections. Images are filename rows despite available local files.
- Reflections and non-PDF attachment detail routes are absent from the shell's own-header
  list. They receive a redundant hamburger/header reservation before their own Scaffold.
  PDF has its own early-return workspace and must remain unchanged.
- AttachmentEntity.fileName is the available display metadata; localPath is storage.
  Never substitute localPath or invent an original filename when none was retained.

## Provider audit

- Gemini: Firebase AI, gemini-2.5-flash / gemini-2.5-pro (with existing flash fallback).
- ChatGPT: existing authenticated myvault-ai function, fast/smart contract. Checked-in
  function defaults: gpt-5-mini / gpt-5.5, overridable by server environment. These are
  not proof of current deployed environment values.
- Kimi: existing direct Moonshot gateway, build-config fast/smart identifiers.
- GPT-6 Astra is documented at https://developers.openai.com/api/docs/models/gpt-6-astra
  (checked 2026-09-09), but account access and deployed function compatibility are not
  established. No speculative model switch or backend deployment in this UI task.

## Scoped implementation plan

1. Compact note-only actions, keeping underlying knowledge/attachment features.
2. Validate new formatting output with a structured parser and exact normalized text;
   preserve Unicode/punctuation, expose rejection, preserve selection and source revision.
3. Guard apply and capture the original through existing version history.
4. Bounded inline image rendering, exact attachment routing, detail-only shell correction.
5. Targeted fixtures, full host checks, Samsung screenshots and disposable-note checks.

## Acceptance evidence

### Implemented boundaries

- Compact note actions retain Listen, Pin/Favourite, Info, History, Export, Format and
  Delete. Only the two requested menu links are removed; underlying features remain.
- Reading images use existing attachment IDs/files, sampled to at most 1200px;
  editing thumbnails use 320px. Missing images and other file types retain honest rows.
- Reflections and attachment detail routes own their existing headers. Image content
  aligns to the top of the existing zoom surface. PDF workspace code is unchanged.
- New generated HTML is checked with a restricted structured parser, then exact
  whitespace/list-bullet-normalized text equality. Arabic combining marks, punctuation,
  URLs, numerical values, repeated text and word order are not normalized away.
- A second check guards editor import. Stale source previews cannot replace later
  edits. Existing note-link offsets are remapped, not discarded. The exact original is
  force-captured through existing Version history before Apply, without a new backup.
- Both exposed modes preserve wording. Intelligent mode may promote existing phrases
  and group adjacent material, not invent or rewrite. Historical formatter actions and
  rich-text compatibility readers remain; new structural output bypasses heuristic repair.
- Quality selection no longer silently becomes Fast. Provider and quality are separate;
  Kimi hides redundant quality choices when both configured model IDs are equal.
- Validation and HTML import run off the UI thread. Normal note viewing makes no AI
  request. Image decoding is bounded and keyed to attachment identity/path/size.

### Samsung evidence, first verification

Samsung SM-F966B, connected over ADB; existing app data retained. Screenshots use
synthetic fixtures with real composables on the physical device. They are not claims
that every production navigation path was exercised.

- Two instrumentation tests passed (22.304 seconds): compact menus at 360/390/412/430dp,
  exact attachment callback, image viewer/back/delete-cancel, provider quality callback,
  populated light/dark reflections and exact 1:1 callback; separate in-memory Room test
  verified the immediate original snapshot, restoration, note links and numbered import.
- Visual inspection caught and corrected centered image dead space, filename wrapping,
  and a wrapped Cancel control. Revised screenshots show the image below its header,
  a single-line filename, intact preview controls and compact reflection headers.
- No original user note was reformatted. The repository persistence test uses an isolated
  in-memory database. Provider fixtures are disposable text, never inserted in the vault.

### Initial live provider observations

Three fixtures per mode: short research/URL/reference, mixed Arabic/English quotations
and bullets, long 120-point note. All accepted results also passed actual editor import.

| Provider | Accepted | Other outcomes |
| --- | ---: | --- |
| Gemini Fast | 4/6 | One malformed output, one wording mismatch; both rejected |
| ChatGPT Fast | 0 requests sent | Formatting account signed out on Samsung |
| Kimi | 4/6 | Long-note requests hit the account request-rate limit |

Manual inspection: the short research note gained useful existing-text headings; mixed
quotations and URLs survived. The long Gemini result retained all 120 points, but made
every existing point title a heading, so hierarchy quality remains source-dependent.
Kimi assigned red to a hadith quotation despite the red-for-Quran instruction. This is
a semantic-formatting error, not a word-preservation failure, and requires tightening.
Do not equate accepted text, HTTP success or a passing capture test with stylistic quality.

### Changes driven by the live review

- Every generated chunk is now validated before proceeding. A failed validation gets
  exactly one retry from the original text with minimal markup instructions; a second
  failure remains an explicit rejection. There is no unbounded retry or silent summary.
- Generated source-colour attribution is disabled in the two exposed formatting modes.
  The validator rejects data-color attributes, rather than trusting the model to identify
  Quran versus hadith. Bold, italics, lists, headings, quotations and RTL spans remain.
  This does not alter existing stored colour marks or legacy readers.
- Rate-limit errors no longer show raw provider account identifiers. They advise waiting
  one minute. The test harness spaces Kimi requests; production does not fake progress
  or silently queue an unlimited number of retries.
- A conflicting old instruction allowing invented neutral headings was removed. Numerical
  list handling preserves existing reference values instead of renumbering them.

The final Gemini batch accepted 7/8 fixtures after editor import. The failed long
Intelligent result inserted an extra "Research points" heading twice; it was rejected
after the bounded retry. The source note was never changed. This is a remaining
provider-quality limitation, not a parser or data-loss success claim.

The final Kimi batch accepted 8/8, including both long-note modes. The short outputs
used existing headings, lists and exact quotations; neither retained religious colours.
Both long outputs kept every numbered research point, but produced similar hierarchy
in both modes. This is useful formatting, not evidence of sophisticated editorial reasoning.
Short Kimi requests took about 3.7-4.7 seconds; each long request took about 101-103 seconds.
The test harness paused between Kimi runs to respect account limits; those pauses are
not reported as application generation time. Gemini accepted requests took 3.7-28.7 seconds.

The final JBR 21 build passed in 12m38s: full unit suite (336 tests per variant; 672 total,
zero skipped/failures/errors), lint, debug APK and release/R8. Lint reports 0 errors,
280 warnings and 1 hint; this is not a claim of a warning-free repository. Both staged
and working-tree whitespace checks passed. The final matching-certificate debug build
was installed on Samsung using an update install; app data was not cleared.
Final production navigation checks are pending.
Samsung subsequently locked itself. The additional UI Apply test could not start because
the notification/lock surface obscured the app; earlier two physical tests passed, but
this later run must not be reported as an all-green UI suite. Unlock was requested.
ChatGPT formatting-account sign-in was requested separately; no credentials were changed.
The final three-test Samsung batch therefore has two passes (repository/history and live
capture) and one externally obstructed UI failure, not three passes. The capture test's
JUnit pass means evidence was recorded, not that every provider output succeeded.
The actual Dashboard was opened and inspected; it is unchanged. The phone locked before
the attempted Dashboard-to-Reflections navigation could be verified.

## Preserved exclusions and remaining gates

No Room schema/migration, backup format/restore/Drive/OAuth, Web, canonical Quran,
PDF annotation architecture, widgets, IDs or Course relationships were changed.
No Google Drive deletion, move, upload or backup operation was performed.

The broad unit suite includes rich-text storage/deletion, note relationships, Quran
reflection parsing/routing, attachment resilience and existing navigation/widget contracts.
Build coverage is not a substitute for live backup/restore, export, narration or incoming
Share acceptance; those operations were not invoked on the user's original notes.

Recovery: pre-note-ux-modernisation-20260909-fb0e8bd (starting fb0e8bd).
No completion claim or completion tag until the remaining acceptance gates are verified.

## Local evidence and reproduction

Synthetic Samsung screenshots and the final provider capture are retained under
`artifacts/note-ux-modernisation-20260909/` locally. Historical artifacts are untouched.
The final JSON includes only synthetic fixture text, generated output, status and timing;
no credentials. Screenshot evidence is not mixed with the user's original-note images.

`NoteUxDeviceTest` contains the physical fixture and isolated history tests. Its live
capture accepts optional instrumentation arguments `provider` (Gemini/ChatGPT/Kimi),
`fixture` (0-3) and `auditRun` (output suffix). The Samsung must be unlocked for UI tests.
ChatGPT needs the existing Settings > Formatting account sign-in, independent of Drive.

Remaining acceptance: sign in and run ChatGPT; unlock Samsung and rerun the expanded
UI Apply test plus production Reflections/attachment navigation, scroll return and zoom.
Live export, narration and incoming Share were not exercised in this pass. Gemini's
occasional rejection and the long-note provider latency are explicitly retained limitations.
