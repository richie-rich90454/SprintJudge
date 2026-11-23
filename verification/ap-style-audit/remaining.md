# the course framework-Style Quality Audit — Remaining Subjects

Scope: `macroeconomics`, `microeconomics`, `research-methods`, `seminar`,
`integrated-1`, `integrated-2`, `integrated-3` (the seven subjects in
`verification/bank-verification/remaining.jsonl`). Read-only; no bank files edited.

Method: in-memory scan of every question in `src/main/resources/bank/<subject>/*.json`
(28,172 questions, 0 duplicate IDs). Read 20 flagged + 10 unflagged items per subject.

Definitions: "no scenario" = null/empty `stimulus` and stem with no numeric data or
scenario/source cue; "MCQ" = item with options (16,097 items); "generic misconception" =
label template repeated >=20x, <=17 chars, or a vague boilerplate prefix.

## Counts

| Subject | n | terse <80 | no scenario | MCQ <4 opt | REMEMBER | expl <100 | generic mis |
|---|---:|---:|---:|---:|---:|---:|---:|
| macroeconomics | 4000 | 1607 | 1346 | 1 | 269 | 1168 | 530 |
| microeconomics | 4000 | 1714 | 1302 | 0 | 275 | 1219 | 547 |
| research-methods | 4050 | 858 | 1321 | 1 | 137 | 322 | 531 |
| seminar | 4040 | 1025 | 2678 | 0 | 119 | 379 | 650 |
| integrated-1 | 4050 | 2571 | 389 | 1 | 209 | 2228 | 750 |
| integrated-2 | 4010 | 1963 | 480 | 9 | 176 | 2162 | 686 |
| integrated-3 | 4022 | 2080 | 892 | 1 | 146 | 1849 | 629 |
| **Total** | **28172** | **11818** | **8408** | **13** | **1331** | **9327** | **4323** |

Also: 113 questions have a distractor with no misconception label.

Two-tier pattern: the topic narrative files (`*-growth.json`, `*-consumer.json`,
`integrated-*-u1-t10…`) have long stems, long teaching explanations and specific
misconceptions. The `uN-tM-*` drill files are terse, REMEMBER-heavy, and use templated
explanation shells (`"<answer>" is correct. The rule behind it: …`).

## 15 worst items

1. `int3-3.7-0024` — `src/main/resources/bank/integrated-3/integrated-3-u3-t7-a-binomial-probability-com.json`
   Quote: stem "A large residual suggests the model"; options only A/B ("Fails to capture that observation well" / "Is perfect").
   Why: 2-option MCQ, no scenario, 35-char stem, 83-char explanation, trivial distractor.
   Change: add two plausible residuals-based distractors and a data/stem context; expand explanation.

2. `resmeth-8.2-0040` — `research-methods/research-methods-u8-t2-what-a-review-protocol-com.json`
   Quote: "How does a fixed-effect model differ from a random-effects model?" — 3 options.
   Why: violates four-option rule; pure definition recall.
   Change: add a fourth misconception distractor; embed a heterogeneity scenario.

3. `resmeth-7.3-0016` — `research-methods/research-methods-u7-t3-nonresponse-a-telephone-su.json`
   Quote: correct D "Cluster sampling to reduce travel." carries `misconception: "harms validity"`; B carries the templated "Assumes remove quality checks when the scenario actually turns on cluster sampling…".
   Why: misconception labels misplaced/auto-generated — a correctness defect, not just style.
   Change: move "harms validity" to a wrong option; rewrite all four labels from the actual error.

4. `int2-1.19-0026` — `integrated-2/integrated-2-u1-t19-factoring-a-quadratic-with.json`
   Quote: "Law of sines states a/sin A = ?" / mis='wrong trig — treating "b/cos B" as the answer'.
   Why: REMEMBER recall, no scenario, 64-char boilerplate explanation, templated misconception strings that echo the option text.
   Change: give a triangle with side data; write distractor-specific misconceptions; teach why the ratio holds.

5. `int1-1.10-0013` — `integrated-1/integrated-1-u1-t10-systems-with-no-solution.json`
   Quote: "Which is a Pythagorean triple?" explanation `"3,4,5." is correct. The rule behind it: Check a^2 + b^2 = c^2.`; distractors all mis='not a triple'.
   Why: generic, non-diagnostic distractor labels; no scenario; minimal teaching.
   Change: use a coordinate/area context; label each distractor with the specific arithmetic slip.

6. `macro-2.8-0052` — `macroeconomics/macroeconomics-u2-t8-gross-domestic-product-def.json`
   Quote: "The GDP deflator measures prices of?" (36 chars, REMEMBER, no stimulus).
   Why: bare fact recall with no data or source; 80-char explanation.
   Change: give nominal/real GDP figures and ask for the deflator, then distinguish CPI/imports.

7. `micro-2.5-0006` — `microeconomics/microeconomics-u2-t5-why-ordinal-utility-is-eno.json`
   Quote: "optimal spending requires?" (26 chars, lowercase, REMEMBER, mis='ignores prices' etc.).
   Why: fragment, no scenario, generic misconceptions, 76-char explanation.
   Change: supply a two-good bundle with prices and MUs; ask for the optimal mix.

8. `seminar-4.7-0045` — `seminar/seminar-u4-t7-a-promise-the-course-canno.json`
   Quote: distractors "The instructor's height", "The essay font size", "The course room color".
   Why: absurd distractors with self-admitted "nonsensical and disconnected" labels — trivially eliminable, not misconception-based.
   Change: replace with plausible competing explanations (e.g., assignment design, grading policy).

9. `seminar-4.8-0039` — `seminar/seminar-u4-t8-analyzing-a-redirect.json`
   Quote: stem "What happened?" (14 chars) with passage stimulus; distractor "The font changed".
   Why: stem carries no task; low-information distractors.
   Change: stem "What shift in the participant's position does the note record?"

10. `int1-1.9-0036` — `integrated-1/integrated-1-u1-t9-percent-increase-and-decre.json`
    Quote: "Slope formula is?" (17 chars); mis='inverts — treating "(x2 − x1)/(y2 − y1)." as the answer'.
    Why: formula-recall, REMEMBER, templated misconception echoing option text.
    Change: give two points; ask for slope and classify a wrong calculation.

11. `int2-1.4-0013` — `integrated-2/integrated-2-u1-t4-a-recursive-sequence.json`
    Quote: "An architect needs cos(60°) for a bracing angle." — only 3 options.
    Why: three options, REMEMBER recall, cosmetic rather than genuine scenario (no dimensions used), boilerplate explanation.
    Change: add a fourth option and a triangle with a real side length to compute.

12. `int3-3.3-0003` — `integrated-3/integrated-3-u3-t3-an-exponential-distributio.json`
    Quote: "Type I error is?" (16 chars, REMEMBER, 69-char explanation).
    Why: rote definition; no hypothesis-testing scenario.
    Change: present a test decision with a true/false null and ask which error occurred.

13. `macro-5.6-0036` — `macroeconomics/macroeconomics-u5-t6-money-growth-in-hyperinfla.json`
    Quote: "A train dispatcher logs a central bank sale of bonds; money supply".
    Why: decorative pseudo-context — the dispatcher frame is irrelevant to the economics; REMEMBER, terse explanation.
    Change: state an open-market sale amount and reserve ratio; ask for the base change.

14. `micro-9.3-0044` — `microeconomics/microeconomics-u9-t3-non-credible-threats.json`
    Quote: mis='Appeals to preferences in completeness and transitivity where none are specified.' / 'Thinks existence and uniqueness only alone is enough when more is required.'
    Why: auto-generated boilerplate misconceptions that do not name the student error; REMEMBER recall.
    Change: write short item-specific labels (e.g., "lists preference axioms, not bargaining axioms").

15. `resmeth-1.10-0003` — `research-methods/research-methods-u1-t10-leading-questions.json`
    Quote: explanation "The item tests question order effects by presenting a memo scenario; the model answer captures the key reasoning and why the tempting shortcut fails in this context."
    Why: vacuous meta-explanation — teaches neither the answer nor the distractor logic (passes mechanical length checks, fails the teaching rubric).
    Change: state which conclusion is correct for the 24-resident memo and why the shortcut fails.

## Cross-cutting fixes

- Retire the `"<answer>" is correct. The rule behind it: …` explanation template; require a worked reason and a distractor-specific reason.
- Audit auto-generated misconception labels: the `Assumes X when the scenario actually turns on Y` and `treating "Z" as the answer` families are boilerplate and sometimes land on the correct option.
- Add real scenario/data stems to `uN-tM-*` drill files; reject cosmetic职业/persona frames.
- Restore a fourth option to the 13 short MCQs (9 in integrated-2).
