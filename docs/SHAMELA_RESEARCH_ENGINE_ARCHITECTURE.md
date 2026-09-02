# Shamela Research Engine Architecture

## Purpose

MyVault owns retrieval and evidence verification. OpenAI, Gemini, and Kimi are
answer writers over the same bounded evidence packet; they do not independently
search Shamela or create source cards.

The production flow is:

`understand -> plan -> retrieve -> expand -> classify -> cross-check -> counter-search -> verify -> packet -> synthesize -> audit -> render`

## Source hierarchy

Evidence is ranked, but lower-ranked material is not discarded when it can
corroborate, qualify, contradict, or lead to a stronger source.

1. Direct primary, explicit statement by the target scholar.
2. Direct primary, contextual position established by the complete passage.
3. Direct quotation of the target scholar in another author's work.
4. Explicit secondary attribution or position report.
5. Later secondary discussion.
6. Editorial, footnote, comment, or index material.

MyVault distinguishes these classes from passage roles such as an adopted view,
a madhhab report, an objection, a rejected view, a hadith quotation, and an
ambiguous passage. A madhhab report, objection, isolated hadith, or editorial
note cannot establish the target scholar's own preference by itself.

## Intent and bounded plan

The planner returns structured JSON containing the topic, domain, answer type,
named scholars, direct queries, alternate terminology, attribution queries,
counter-queries, and whether the user actually asked about consensus.

For a named scholar, MyVault performs bounded passes for:

- direct author-scoped evidence;
- alternate terminology in the same authored corpus;
- direct quotations and explicit attributions in the broader corpus;
- the opposite ruling, negation, recommendation-versus-obligation, or other
  material contradiction;
- disagreement discovery for fiqh questions.

Scholar comparison builds a separate verified group for every named scholar.
Passages never move between scholar groups.

## MCP research primitives

The direct Android MCP client permits only the read-only calls needed by this
research architecture:

- `shamela_resolve`: resolve a named scholar to a stable Shamela author ID;
- `shamela_search_pages`: scoped direct and broader body-text retrieval;
- `shamela_search_phrase`: phrase and near/proximity retrieval;
- `shamela_get_page`: full current-page and adjacent-page context;
- `shamela_verify_quote`: exact/orthographic quote verification at a stable
  book and page;
- `shamela_scan_consensus`: bounded disagreement discovery only.

The consensus scan is never treated as a verdict. A final consensus, majority,
or no-disagreement claim requires an exact verified passage that states it.

Every call is validated against a local argument allow-list. Debug builds log
the actual tool name and bounded research arguments, including author scope,
but never authorization headers, OAuth tokens, refresh tokens, or provider keys.

## Context, provenance, and exact quotations

Search snippets are leads, not evidence. Candidate pages are fetched in full,
with previous and next page context where available. On long pages the evidence
extractor receives a bounded window centered on the actual search match so a
relevant conclusion near the end of a page is not silently truncated.

The extractor must copy one contiguous Arabic quotation already present on the
retrieved author-body page. MyVault rejects invented or stitched wording, then
checks the quote using `shamela_verify_quote`. Footnote and comment matches are
not accepted as author-body proof.

When another work quotes a target scholar, MyVault searches the target scholar's
resolved corpus for the same wording. A located primary match replaces the
secondary quotation as the strongest source. If no primary location is found,
the secondary report may still be used with reporting language that names its
actual author.

## Evidence graph and ranking

The in-memory packet records, for each finding:

- stable source ID and exact `bookId/pageId`;
- book, author, supplied part/page/citation metadata;
- exact Arabic and bounded surrounding context;
- provenance, evidence class, passage role, and confidence;
- whether it supports, contradicts, contextualizes, or remains ambiguous;
- the attribution wording required in the answer.

Candidate selection reserves available space for primary, alternate-primary,
counter, secondary-attribution, disagreement, and general passes before filling
remaining slots by score. Direct explicit primary material outranks contextual
primary and secondary evidence, while counter-evidence remains visible to the
extractor and verifier.

## Same research, different AI

Verified question packets and verified comparison packets are cached in memory
by normalized question, with a strict eight-packet bound. Changing between
OpenAI, Gemini, and Kimi reuses the same packet and reruns only synthesis and the
final claim audit. This cache is transient and contains no backup data.

The selected answer provider receives structured verified findings rather than
raw MCP output. It must return `answer_markdown` and the exact `cited_evidence_ids`
it used. MyVault rejects malformed output, unknown IDs, mismatches between the
declared and rendered IDs, and substantive uncited paragraphs.

## Post-answer verification

A bounded structured control-provider sequence audits the draft. The audit may
pass it, revise unsupported wording, or mark the evidence insufficient. It must
preserve claim-level `[S#]` bindings and cannot add a source. MyVault also blocks
consensus/majority language unless the question requested it and an exact
verified finding actually supports it.

Source cards are built only from the selected `ResearchSource` objects. Tapping
a citation or Open uses the stored `bookId/pageId`; it does not rerun search or
open the first result from a book. The source sheet identifies direct,
contextual, reported, and secondary evidence instead of presenting all cards as
the target scholar's own words.

## Developer trace

Debug-only `MyVaultResearchTrace` events record:

- interpreted plan and query families;
- actual MCP tools and bounded arguments;
- candidate source IDs and retrieval passes;
- per-scholar comparison groups;
- selected classifications and rejected-source reasons;
- final evidence packet and model-used evidence IDs;
- packet-cache reuse and final audit verdict.

The trace excludes credentials and is absent from release builds.

## Live benchmark suite

All rows require a real Shamela session, exact source inspection, and a readable
answer. A benchmark is not passed by a unit test alone.

| ID | Architecture case | Question | Required evidence gate | Current status |
|---|---|---|---|---|
| B1 | Named-scholar fiqh preference | What did Ibn Taymiyyah say about touching the private parts while in a state of wudu? | Direct author search, counter-search, secondary search, exact Arabic, exact source; must not infer invalidation from `ظاهر المذهب` or invent consensus | Not yet rerun on physical Samsung after the comprehensive amendment |
| B2 | Quote verification | Verify whether Ibn Taymiyyah said: `الإيمان قول وعمل يزيد وينقص` | Dedicated quote verifier, exact/near/partial/not-found verdict, body provenance, exact source | Not yet rerun on physical Samsung |
| B3 | Secondary preservation | What position does Ibn Muflih report Ibn Taymiyyah chose regarding wudu after touching the private parts? | Secondary attribution labeled honestly, primary-match attempt, no false direct citation | Not yet rerun on physical Samsung |
| B4 | Madhhab report vs preference | When Ibn Taymiyyah reports the Hanbali narrations about touching and wudu, which position does he personally prefer? | Separate reported madhhab material from explicit/contextual personal choice | Not yet rerun on physical Samsung |
| B5 | Aqidah terminology | What did Ibn Taymiyyah say about whether faith increases and decreases? | Scoped primary terminology, rebuttal/context classification, exact Arabic | Not yet rerun on physical Samsung |
| B6 | Tafsir | How did al-Tabari explain `الرحمن على العرش استوى` in Taha 20:5? | Verse-aware terminology, direct tafsir passage, no unrelated lexical hit | Not yet rerun on physical Samsung |
| B7 | Hadith commentary | How did al-Nawawi reconcile the hadiths about touching the private parts and wudu? | Commentary/reconciliation rather than raw hadith quotation; exact source | Not yet rerun on physical Samsung |
| B8 | Scholar comparison | Compare Ibn Taymiyyah and al-Nawawi on whether touching the private parts nullifies wudu. | Separate packet per scholar, same combined packet across all AI providers, explicit evidence gaps | Not yet rerun on physical Samsung |

For B1 the known direct passage that the live run must independently retrieve is
from *Jami al-Masa'il* (Ibn Taymiyyah), supplied as volume 9, printed page 312:

> والأظهر أن الوضوء من مس الذكر مستحب ليس بواجب، فإن توضأ فهو أفضل، وإن لم يتوضأ جازت صلاته

This text is a benchmark oracle, not a hard-coded production answer.

## Known limitations and acceptance boundary

- Retrieval depends on the remote Shamela corpus, indexing, OAuth session, and
  provider availability.
- Secondary-source independence or copying relationships can be described only
  when the retrieved record supplies enough evidence.
- The in-memory packet cache is intentionally lost on process death; a later run
  may retrieve a changed remote corpus.
- The architecture can reject unsafe synthesis but cannot guarantee that every
  relevant passage exists in or is searchable through the remote corpus.
- Physical Samsung acceptance, all three-provider B1 comparison, and all eight
  live benchmark results remain mandatory before claiming the research-quality
  remediation complete.
