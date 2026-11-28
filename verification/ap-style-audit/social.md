# the course framework-Style Quality Audit — Social Sciences / History

Scope: `us-history`, `world-history`, `european-history`, `us-government`,
`comparative-government`, `psychology`, `human-geography`. Read-only; no bank files edited.

Method: exhaustive in-memory scan of every question in
`src/main/resources/bank/<subject>/*.json` (28,149 questions across 495 files).
Read 20+ flagged and 8–10 unflagged items per subject; the 15 worst were re-opened
in full (stem, all options, misconceptions, answer, explanation, hint).

Definitions: **terse** = stem <80 chars. **no scenario** = null/empty `stimulus`
*and* stem with no source/scenario cue (table, excerpt, quote, "according to",
dated event, etc.). **MCQ <4 opt** = MCQ-format item (not NUMERIC) with 2–3 options.
**generic misconception** = vague/formulaic label (`wrong X`, `assumes …`, boilerplate
shells). Thresholds chosen to match `remaining.md`.

## Counts

| Subject | n | terse <80 | no scenario | MCQ <4 opt | REMEMBER | expl <100 | generic mis |
|---|---:|---:|---:|---:|---:|---:|---:|
| us-history | 4050 | 716 | 1086 | 0 | 284 | 296 | 295 |
| world-history | 4009 | 876 | 1428 | 2 | 302 | 189 | 944 |
| european-history | 4000 | 1711 | 1804 | 10 | 380 | 760 | 770 |
| us-government | 4020 | 846 | 1539 | 2 | 414 | 313 | 682 |
| comparative-government | 4030 | 559 | 1610 | 2 | 194 | 228 | 1377 |
| psychology | 4030 | 1237 | 2155 | 2 | 472 | 705 | 287 |
| human-geography | 4010 | 1335 | 2078 | 4 | 295 | 819 | 461 |
| **Total** | **28149** | **7280** | **11700** | **22** | **2341** | **3310** | **4816** |

Also: 83% (23,320) have no `stimulus` object at all; psychology and
human-geography contain **zero** SOURCE_BASED items. 2,917 source-based items overall.

## Cross-cutting findings

1. **Two-tier bank.** Topic-narrative files (`us-history-founding.json`,
   `world-history-networks.json`, `compgov-elections.json`) are strong course-framework-style:
   real passages/data, 4 parallel options, misconception-specific distractors,
   150–250-char teaching explanations. The `uN-tM-*` drill files are the problem:
   terse, REMEMBER-heavy, no stimulus, ~80-char "answer + wrong-option" explanations.
   Every one of the 15 worst items below is from a `uN-tM-*` file.
2. **Decorative pseudo-context.** Thousands of drills bolt on a token persona
   ("A hive diary asks…", "An astronomer logs…", "A legal file asks…", "Nurse 56…")
   that carries no information the item uses. This is a scenario in name only and
   is worse than a plain question because it mimics the course framework framing without doing the work.
3. **Mass-produced templates.** The explanation "The correct option captures the
   main historical effect, while the others rest on common misconceptions about
   speed, cost, or authority." appears **180×**. Distractors explicitly labelled
   `"nonsense distractor"` appear 35×; `"Assumes … the documents instead record"`
   shells 31×; `"the evidence runs the other way"` 32×. In the `world-14.6/14.7`
   files the *same four options* serve unrelated stems.
4. **Trivially eliminable distractors.** "faster running", "improved vision",
   "weather control", "the gap is always zero", "no reason; random placement" test
   nothing about the discipline; they are not misconceptions a student actually holds.
5. **Mislabelled cognitive demand.** Definition drills tagged REMEMBER/dok 1 with
   decorative scenarios; conversely one 2-option item is tagged CREATE/dok 4.

## 15 worst items

1. `world-14.6-0025` — `world-history/world-history-u14-t6-how-coaling-stations-set-a.json`
   Quote: stem "Question 0025: What did a trade grant convert a market into?"; options
   "It changed who bore the cost…" / "It shortened the time…" / "It made the outcome
   depend on local conditions" / "It shifted authority to a central office."
   Why: options are unrelated to the stem and reused verbatim across `u14.6/u14.7`
   questions; explanation is the 180× boilerplate shell; no stimulus; REMEMBER.
   Change: write stem-specific options about trade grants and markets; delete the
   template explanation; rebuild as a source-based item with a grant charter.

2. `world-14.7-0055` — `world-history/world-history-u14-t7-a-find-that-set-a-larger-c.json`
   Quote: "Question 0055: What did troops in a capital that declared a republic do?"
   with the identical four options and identical explanation as item 1.
   Why: recycled boilerplate; answer D ("shifted authority to a central office")
   does not answer the question asked; no scenario.
   Change: author unique, content-specific options and a real 1848-style source excerpt.

3. `euro-5.6-0015` — `european-history/european-history-u5-t6-overseas-markets-for-manuf.json`
   Quote: "Cash taxes in colonies often forced people to?" — only A/B/C.
   Distractors carry auto-generated shells: "Assumes avoid all work entirely; the
   documents instead record: Work for wages to pay the tax…".
   Why: 3 options, 43-char stem, REMEMBER, misconception labels that echo the option
   text instead of diagnosing an error.
   Change: add a fourth plausible option; give a tax-record excerpt; write real misconceptions.

4. `usgov-1.4-0020` — `us-government/us-government-u1-t4-declaration-of-independenc.json`
   Quote: "An astronomer asks what problem divided power can create; which is a real
   risk?" — 3 options: "It guarantees instant decisions", "It can produce gridlock
   between levels", "It eliminates all state laws".
   Why: only 3 options; astronomer frame is decorative; no stimulus; 97-char explanation.
   Change: add a fourth option (e.g., "It removes all judicial review") and a scenario
   of a federal–state clash; explain the trade-off between checking power and speed.

5. `psych-11.2-0032` — `psychology/psychology-u11-t2-personality-the-lexical-hy.json`
   Quote: "An engineer must name the two directions a lexicon study split into.";
   three distractors all labelled simply "wrong dimension".
   Why: REMEMBER recall, no scenario, non-diagnostic misconception labels, 91-char explanation.
   Change: show the actual factor table and ask which interpretation the split supports.

6. `world-13.6-0035` — `world-history/world-history-u13-t6-the-energy-base-of-the-fir.json`
   Quote: "What health problem did coal smoke mainly cause?" with distractors
   "It mainly caused faster running", "improved vision", "better hearing" — two
   self-labelled `"nonsense distractor"`.
   Why: absurd options, no source, REMEMBER; zero teaching value.
   Change: replace with plausible competing claims (e.g., "only affected crops")
   and attach an 1830s urban-health report.

7. `world-12.6-0032` — `world-history/world-history-u12-t6-the-permanent-seats-create.json`
   Quote: "What usually drives a contest for regional leadership?" — distractor
   "A desire for weather control usually drives the contest" (`"nonsense distractor"`).
   Why: trivially eliminable; bare recall; no scenario.
   Change: present a regional rivalry scenario with security, trade and cultural stakes.

8. `ushist-4.5-0006` — `us-history/us-history-u4-t5-crisis-and-convention.json`
   Quote: "A hive diary asks which amendment protects freedom of speech and press."
   Why: decorative beekeeping frame, pure amendment-number recall, 90-char explanation,
   distractors that simply swap amendment numbers.
   Change: quote a speech/press censorship case and ask which constitutional claim applies.

9. `ushist-5.7-0005` — `us-history/us-history-u5-t7-missouri-compromise.json`
   Quote: "A legal file asks who won the presidential election of 1860."; three
   distractors all labelled `"names a losing candidate"`.
   Why: "legal file" is decorative; non-diagnostic labels; no stimulus; REMEMBER.
   Change: give the 1860 electoral-vote map and ask what the sectional split reveals.

10. `compgov-5.5-0007` — `comparative-government/comparative-government-u5-t5-weak-and-strong-review.json`
    Quote: "A beekeeping rule exists on paper but apiaries rarely follow it."
    Why: the beekeeping analogy is off-task for a comparative-politics concept
    (compliance gap); open-ended EXPLANATION with no source; REMEMBER.
    Change: use an actual civil-service or environmental-enforcement case with data.

11. `compgov-3.5-0016` — `comparative-government/comparative-government-u3-t5-distributive-policy.json`
    Quote: "The projection asks what an implementation gap is; what is the answer?"
    Why: definition recall with a fake "projection" wrapper; 99-char explanation;
    REMEMBER.
    Change: present a policy target versus outcome table and ask for the gap's cause.

12. `humgeo-4.4-0027` — `human-geography/human-geography-u4-t4-a-geometric-boundary-besid.json`
    Quote: "An architect's brief: propose a fair self-determination process; key
    element?" — only two options ("Coercion of dissenters" / "Free, informed and
    inclusive participation").
    Why: 2-option MCQ tagged CREATE/dok 4 — mislabelled demand; no scenario; 75-char explanation.
    Change: add two plausible alternatives and a disputed-territory case with stakeholders.

13. `humgeo-7.7-0056` — `human-geography/human-geography-u7-t7-what-a-map-scale-must-be-a.json`
    Quote: "Nurse 56 first states the purpose of a control point, then uses it in a survey."
    Why: "Nurse 56" is an irrelevant persona; the promised two stages are not present;
    REMEMBER recall of a definition; no map or survey data.
    Change: show a survey diagram with a known-coordinate point and ask how new points are fixed.

14. `euro-12.2-0013` — `european-history/european-history-u12-t2-what-a-turning-point-is.json`
    Quote: "A historian measures the interval between two dated events."; distractors
    "The gap is the sum of the two dates", "The gap cannot be measured", "The gap is always zero."
    Why: an arithmetic exercise in a history bank; absurd distractors; no historical content.
    Change: use two dated turning points and ask what the interval's *significance* is.

15. `usgov-12.1-0001` — `us-government/us-government-u12-t1-commander-in-chief-of-the-.json`
    Quote: "Who is designated Commander in Chief of the Army and Navy?"; explanation
    "The President is Commander in Chief. The other options name officials who do not hold that role."
    Why: textbook fact recall, no scenario, REMEMBER, explanation restates the answer
    without teaching; distractors are obvious non-commanders.
    Change: give a crisis scenario requiring civilian control of the military and ask
    which constitutional authority applies.

## Systemic fix (shortest version)

Retire the `uN-tM-*` drill files that carry decorative personas, nonsense distractors
and template explanations, or regenerate only those items as SOURCE_BASED/STIMULUS_MCQ
with four content-plausible options, a real stimulus, and a 150+ char explanation that
teaches why each wrong option is a genuine misconception. Keep the topic-narrative
files as the quality model.
