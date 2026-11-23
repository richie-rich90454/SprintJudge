# the course framework-Style Quality Audit — Humanities / Language

Scope: `english-language`, `english-literature`, `art-history`, `music-theory`,
`spanish`, `french`, `german`, `chinese`. Read-only; no bank files edited.

Method: exhaustive in-memory scan of every question in
`src/main/resources/bank/<subject>/*.json` (32,270 questions, 547 files; `.shipped`
metadata files excluded). Read 20+ flagged and 10+ unflagged items per subject;
the 15 worst were re-opened in full (stem, all options, misconceptions, answer,
explanation, hint).

Definitions: **terse** = stem <80 chars. **no scenario** = no usable `stimulus`
(`text`/`body`/`content` empty or absent) *and* no quoted source/scenario in the
stem. **MCQ <4 opt** = MCQ-format item (CONCEPT/STIMULUS/SOURCE_BASED/MULTI_SELECT)
with 2–3 options. **generic misconception** = narrow check for vague labels
(`wrong X`, `not a court`, `anachronistic`); broad "thinks/confuses/assumes"
boilerplate is far more common. **short explanation** = <100 chars.

## Counts

| Subject | n | terse <80 | no scenario | MCQ <4 opt | REMEMBER | expl <100 | generic mis |
|---|---:|---:|---:|---:|---:|---:|---:|
| english-language | 4040 | 2119 | 2356 | 6 | 176 | 1238 | 0 |
| english-literature | 4020 | 366 | 3149 | 0 | 140 | 52 | 0 |
| art-history | 4020 | 306 | 3430 | 2 | 110 | 170 | 45 |
| music-theory | 4050 | 1578 | 3823 | 0 | 490 | 1297 | 32 |
| spanish | 4050 | 2041 | 2767 | 5 | 374 | 1226 | 20 |
| french | 4020 | 1940 | 3005 | 0 | 378 | 1643 | 182 |
| german | 4020 | 2364 | 2862 | 0 | 392 | 1324 | 43 |
| chinese | 4050 | 2021 | 3078 | 2 | 400 | 1479 | 38 |
| **Total** | **32270** | **12735** | **24470** | **15** | **2460** | **8429** | **360** |

Also: 3,325 stems carry a decorative persona prefix ("in an engineer's design
brief", "a legal clerk's file note", "Dans un compte rendu"); **1,285 stack two or
more** of them in one stem, a template-assembly defect. Format mix: CONCEPT_MCQ
10,283, EXPLANATION 6,444, STIMULUS_MCQ 5,659, TWO_STAGE 3,643, NUMERIC 1,767.

## Cross-cutting findings

1. **Two tiers.** Narrative/analysis files (e.g. `english-language-argument.json`,
   `art-history` unit-10, `chinese` unit-10.2, `spanish` 1.8) are strong course-framework-style:
   real passages, four parallel options, misconception-specific distractors,
   150–250-char teaching explanations that do close reading. Almost every failure
   lives in generated `*-uN-tN-*` drill files.
2. **Recall drills dominate the weak tier.** Bare questions ("Which law banned
   workers' combinations?") with REMEMBER bloom, one-sentence explanations, and
   distractors labelled only `wrong regime` / `wrong purpose`. No passage, image,
   audio or notated stimulus to analyse.
3. **Fake context and template leaks.** Persona prefixes are decorative, and
   doubling ("In a course handout, in an engineer's design brief, 姑妈 refers to
   which relative?") shows stitched templates, not a scenario.
4. **Answer leakage in metadata.** Some `misconception` fields quote the correct
   option verbatim, and one explanation says "Option A is correct."
5. **Target-language use is inconsistent.** Many French/German/Spanish/Chinese
   items ask in English with a single French phrase bolted on, so they test
   translation recall rather than reading in the target language.
6. **Teaching explanations are the biggest systemic gap**: 8,429 under 100 chars;
   the standard form is "X is Y. The distractors pick wrong forums."

## 15 worst (id / file / quote / failure → change)

1. germ-6.4-0035 / `german-u6-t4-how-a-false-claim-keeps-sp` / "Why this split?" —
   15-char, context-dependent stem; unanswerable standalone → supply the exchange.
2. germ-7.5-0055 / `german-u7-t5-the-ordinary-notice-period` / "Is this adequate?" —
   17-char stem referring to an absent email → quote the notice.
3. germ-7.4-0007 / `german-u7-t4-a-condition-attached-to-a-` / "Which court hears the
   appeal?" — recall of the Landesarbeitsgericht; misconception field quotes the
   key → give a case scenario; rewrite the leaked label.
4. germ-7.4-0004 / same file / "Which step must a dismissal procedure observe?" —
   REMEMBER, 71-char expl, `wrong channel` labels → case file + reasoned options.
5. fren-8.2-0007 / `french-u8-t2-royal-officers-in-the-cape` / "Which law banned
   workers' combinations?" — bare recall, `wrong legal text` → source excerpt.
6. fren-8.2-0019 / same / "What executive power was created in 1958?" — recall,
   `wrong regime` → constitutional excerpt to analyse.
7. fren-8.3-0015 / `french-u8-t3-what-a-fixed-exchange-rate` / "Why open a palace as
   a national museum?" — trivia with absurd distractors ("To demolish the
   building") → museum notice with plausible policy choices.
8. fren-8.2-0012 / `french-u8-t2-royal-officers-in-the-cape` / "The constitutional
   laws of 1875 founded the Third Republic." — stem states the answer without a
   task → turn into a question about the laws' effect.
9. fren-2.7-0048 / `french-u2-t7-the-aller-family-and-its-m` / "Dans un dossier, in
   an analyst's dashboard note, which meaning does assister have in French?" —
   doubled prefix, English-medium, "Option A is correct" → one French sentence.
10. chin-2.2-0032 / `chinese-u2-t2-word-families` / "In a course handout, in an
    engineer's design brief, 姑妈 refers to which relative?" — doubled prefix,
    kinship recall → short family dialogue in Chinese.
11. chin-2.2-0053 / same / "In a reference guide, in an engineer's design brief,
    爷爷 refers to which relative?" — same template defect → contextual dialogue.
12. arthist-7.7-0029 / `art-history-u7-t7-the-temple-plan-as-a-diagr` / "What carries
    the suspended deck in the note?" — 91-char expl; a misconception quotes the
    answer ("Cables to towers and anchorages") → show the bridge diagram; fix label.
13. musict-8.3-0014 / `music-theory-u8-t3-the-degrees-a-blues-progre` / "What is the
    section form of a standard rhythm change?" — recall of AABA with no audio or
    score → supply the notated/chord excerpt.
14. span-6.8-0015 / `spanish-u6-t8-a-headline-and-what-it-for` / "which element in
    that podcast is a convention of the form?" — only 3 options → add a fourth
    parallel distractor.
15. englang-1.4-0051 / `english-language-u1-t4-rhetorical-situation` / "Which
    sentence conveys urgency?" — 31-char stem, no passage, recall → supply the
    four sentences and ask for the revision.

## Fixes

Convert recall drills to stimulus-based analysis; delete decorative and doubled
persona prefixes; add fourth options and real teaching explanations; remove
answer leakage from misconceptions; ask target-language items in the target
language.
