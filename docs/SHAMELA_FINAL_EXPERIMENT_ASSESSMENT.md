# Shamela Final Experiment Assessment

## Decision

**NO-GO as a general scholarly-answer feature.**

The bounded correction made MyVault materially better at one difficult fiqh
question and at exact source opening. It did not generalize reliably enough to
tafsir, hadith reconciliation, and secondary-attribution questions. Keeping the
feature as an explicitly experimental source-discovery aid is technically
possible, but it should not be represented as dependable scholarly synthesis.

## Checkpoints and rollback

- Branch: `frozen-design-master-port`
- Bounded-attempt starting commit: `cc6a5b88ffc69cbbfe81f01e282a5f1cab2a9599`
- Pre-final-attempt tag: `pre-final-shamela-research-attempt`
- Pre-Shamela/pre-AI tag: `pre-shamela-ai-research-integration`
- Pre-Shamela/pre-AI commit: `308ea1fe3302ea9a3f64b3b7983d1b350b9db8d4`

No rollback was performed. Returning to the pre-AI state remains a deliberate
future choice and must use the existing tag rather than deleting history.

## What changed in the bounded attempt

The correction stayed inside the existing Android orchestration:

- a second structured planner repairs weak modern-language queries with bounded
  classical terminology;
- fiqh searches involving a known act such as wudu always include a neutral
  recommendation-versus-obligation axis;
- pages containing explicit authorial preference language receive priority;
- if the broad extractor skips such a page, one focused extraction checks it;
- explicit preference is classified above unqualified contextual exposition;
- when that preference is verified, contextual passages remain in the developer
  dossier but cannot become supporting claims in the user-facing answer;
- debug traces record planner, MCP, extractor, synthesis, and audit outputs
  without credentials.

No backend, corpus mirror, vector database, Room migration, backup change, or
new paid infrastructure was introduced.

## Benchmark 1

Question: "What did Ibn Taymiyyah say about touching the private parts while in
a state of wudu?"

The engine independently retrieved the decisive direct passage from Ibn
Taymiyyah's *Jami al-Masa'il*:

> والأظهر أن الوضوء من مسّ الذكر مستحبٌّ ليس بواجب، فإن توضأ فهو أفضل، وإن لم يتوضأ جازت صلاته

The research pass searched primary author material, alternate terminology,
opposing formulations, and secondary attributions. The internal dossier retained
related contextual passages but classified them as context rather than allowing
them to redefine the explicit preferred ruling.

The final user-facing packet contained one direct primary statement and two
supporting attributions. OpenAI, Gemini, and Kimi all reused the same cached
packet and concluded that renewed wudu is recommended rather than obligatory,
and that prayer remains valid without it. No provider generated source metadata.

Tapping the primary citation opened the exact Shamela record:

- Book: *Jami al-Masa'il - Ibn Taymiyyah - Ata'at al-Ilm edition*
- Volume: 9
- Printed page: 312
- Stable remote location: book `145376`, page `3481`

## Additional live benchmarks

| Area | Question/result | Outcome |
|---|---|---|
| Aqidah | Ibn Taymiyyah on faith increasing and decreasing; six direct source pages and a materially relevant answer | PASS, although verbose |
| Tafsir | Al-Tabari on Taha 20:5; five candidates but no exact evidence survived classification | FAIL SAFE |
| Hadith commentary | Al-Nawawi reconciling the touching/wudu hadiths; four pages located, draft rejected by claim audit | FAIL SAFE |
| Quote verification | Ibn Taymiyyah on faith as speech and action that increases and decreases; three source pages | PASS |
| Secondary attribution | What Ibn Muflih reports Ibn Taymiyyah chose on touching/wudu | FAIL; response described Ibn Muflih's two-view discussion without proving the requested attribution |

## Strengths

- B1 now retrieves and opens the exact decisive primary passage.
- OpenAI, Gemini, and Kimi can synthesize one shared verified dossier.
- Source metadata and navigation remain application-generated and stable.
- Contextual material cannot silently overrule explicit authorial preference.
- Unsafe tafsir and hadith answers were blocked rather than fabricated.
- The feature remains read-only toward Shamela and does not alter MyVault backup
  or persisted production data.

## Weaknesses and failure modes

- Retrieval remains sensitive to planner wording and remote search ranking.
- A broad extractor may omit a decisive page unless focused review is triggered.
- General tafsir retrieval did not reliably anchor the requested verse and
  commentator.
- Hadith commentary retrieval found related pages but did not establish the
  requested reconciliation.
- Secondary-attribution logic can answer a nearby discussion instead of the
  precise reported attribution, which is a material scholarly error.
- Research often takes roughly one to three minutes on the emulator.
- Physical Samsung behavior and latency were not tested because no phone was
  connected.

## Comparison with the initial version

The initial engine commonly treated a verified snippet as a verified conclusion,
allowed contextual propositions to outweigh explicit preference, and could cite
irrelevant pages confidently. The final bounded version is substantially safer:
it retrieves B1 correctly, separates supporting evidence from context, reuses a
single dossier across providers, and blocks several unsupported answers.

That improvement is not enough for a general Islamic research answer engine.
The remaining failures indicate that dependable quality would require materially
more dedicated retrieval infrastructure, corpus-aware indexing/reranking, and
domain-specific evaluation than the approved remote-MCP-plus-Android boundary.

## Honest recommendation

Do not present this feature as authoritative or generally reliable. Either:

1. retain it only as an experimental source-discovery assistant with explicit
   limitations; or
2. revert to `pre-shamela-ai-research-integration` if the experiment is not
   worth retaining.

The second option removes the entire Shamela AI experiment cleanly while
preserving all Git history. It was not performed automatically.
