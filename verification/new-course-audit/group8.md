# Group 8 quality audit — film-studies, graphic-design, arabic, korean

Scope: all 4,000 questions per directory (16,000 total) parsed programmatically; 30+ items per directory inspected in detail. No bank files were modified.

## Checks that passed in all four directories
id uniqueness (0 duplicates), option count = 4, option ids A–D, valid `correctId`, non-empty misconceptions on all distractors (min lengths: film 23, graphic 38, arabic 39, korean 32 chars), explanation >= 60 chars (min 139/203/171/159), no scratch/meta/TODO/AI phrasing, no malformed JSON, unit-number agreement between `id` and `unit`.

## Systemic failures
- **graphic-design:** 3,960/4,000 answers are `A` (all 40 `B` answers are in batch-01). 100 fixed option sets cover all 4,000 items (40 rewordings per set). A test-taker scores 99% by always choosing A.
- **arabic:** 1,420 items contain no Arabic script in stem/options/stimulus; 740 of those have no Arabic anywhere (Latin romanization only). 3,509 items fall into 834 identical-option-set groups. 420 explanations repeat "The other choices are the glosses of nearby forms…".
- **korean:** 3,101/4,000 explanations repeat "…while the other options are meanings of different Korean words that do not describe this term". 3,104 items are 8-way rewordings of the same vocabulary term; 4,000 stems contain no Hangul (script appears only in stimulus/explanation). 13 (unit,topic) id pairs map to two different topic names. Answer skew A=1,344 vs ~880 each for B/C/D.
- **film-studies:** 2,000 stems are 10-way rewordings of one scenario; all 4,000 items collapse into 1,600 identical-option-set groups. Answer distribution is balanced.

## Ten worst items

| # | File / id | Quote (abridged) | Failure | Fix |
|---|---|---|---|---|
| 1 | `graphic-design/graphic-design-batch-02.json` — `graphic-1.4-0121` | "On Continuity flow: a designer reviewing a museum exhibition poster…" | `correctId=A`; 99% of bank is A — answer-shape bias. | Distribute correct option uniformly across A–D. |
| 2 | `graphic-design/graphic-design-batch-27.json` — `graphic-7.66-2601` | "On Vector paths: …moving anchor points. What does moving the anchor points actually do?" | Same 4 options reused across 40 items in this topic only the venue noun changes. | Write distinct distractors per item. |
| 3 | `film-studies/film-u1-t1-mise-en-sc-ne.json` — `film-1.1-0051` | "In this described scene, which concept is shown: a director places a character by a window with harsh side light…" | Scenario repeated verbatim in 10 items (0051–0060), only the lead-in phrase varies; 2,000 such items bank-wide. | Keep one item per scenario. |
| 4 | `film-studies/film-u1-t1-mise-en-sc-ne.json` — `film-1.1-0001` | "Which statement best defines Mise-en-scène?" | Identical options in 0001/0005/0009; explanation opens by copying the correct option verbatim, then "This is precisely what…". | Dedupe; explain *why*, not restate. |
| 5 | `arabic/arabic-batch-001.json` — `arabic-1.1-0006` | "In alphabet, which teaching note belongs to ‘ha’?" options: "a voiceless pharyngeal fricative"… | Zero Arabic characters in stem, options or explanation (only romanized ‘ḥāʾ’) — target language absent. | Include ح and Arabic-script choices. |
| 6 | `arabic/arabic-batch-001.json` — `arabic-1.1-0001` | "In alphabet, what does ‘alif’ denote?" | Arabic appears only in explanation; stem/options are English. Boilerplate "The other choices are the glosses of nearby forms…" (420 items). | Put ا in the tested item; vary rationale. |
| 7 | `arabic/arabic-batch-033.json` — `arabic-9.2-0004` | "In levant, which Arabic form names the Maghreb?" | Near-duplicate reciprocal stem `arabic-9.4-0022`: "In maghreb, which Arabic form names the Levant?" | Merge or rewrite one direction. |
| 8 | `arabic/arabic-batch-021.json` — `arabic-6.1-0005` | "In narrative, which statement is paired with Layla and the guests?" | Option set shared with 18 other items (e.g. `6.2-0025`, `6.3-0017`); stem differs only by context noun. | Unique distractors per passage. |
| 9 | `korean/korean-batch-01.json` — `korean-1.1-0001` | "What does 'annyeonghaseyo' mean?" | One term reworded 7 ways (0001–0007) with identical options; 3,101 identical explanation boilerplates; stem has no Hangul. | One item per term; vary explanations; show 안녕하세요 in stem. |
| 10 | `korean/korean-batch-36.json` — `korean-11.1-3545` (cf. `korean-batch-35.json` — `korean-11.1-3441`) | "Which option correctly fills the blank marked eunneun?" vs "What does 'gang' mean?" | Unit/topic agreement: id `11.1` maps to both "Korean Grammar and Particles" and "Places Vocabulary" (232 mislabeled items). | Renumber grammar ids to their own unit/topic. |

Priority: fix graphic-design answer placement first (single highest-scoring exploit), then Arabic target-language coverage, then bank-wide deduplication.
