# Group 3 quality audit — linear-algebra, differential-equations, discrete-mathematics-2, data-structures, operating-systems, computer-networks, databases, ai-ml

Scope: all 32,000 questions (8 × 4,000) parsed programmatically; 10 flagged + 20 unflagged items read per directory. No bank files modified.

## Checks that passed
- **id uniqueness:** 0 duplicates globally. **Unit agreement:** 0 mismatches between id unit number and `unit`.
- **Option count / answer shape:** every MCQ has exactly four A–D options and a valid `correctId`; discmath2's 1,000 `NUMERIC` items legitimately use `answer.value`. Total 1,000 option-less items (all `NUMERIC`).
- **Misconceptions:** no short (<12 char) misconceptions; the single empty misconception is always on the correct option (24,000 items) — by design, not a defect.
- **Explanations:** none under 60 chars (minimum ≈80).
- **JSON:** all 320 files parse cleanly.

## Systemic failures
- **Wrong math/logic in templated items:** 68/68 differential-equations "autonomous" answers inverted; 17/68 exactness verdicts wrong; 18 derangement values wrong (used n!(1−1/e) instead of !n); 4 OS SJF orders are actually priority orders.
- **Near-duplicate stems:** 708 templated groups cover 20,753 items; discmath2 has 121 groups with identical stem *and* answer; computer-networks has only 400 distinct option sets for 4,000 items (10 rewordings each).
- **Scratch/meta phrasing:** ai-ml 2,994 "Scenario N:" prefixes; discmath2 3,067 "(case N)" tags; differential-equations 4,000 copies of "Only the option consistent with this derivation is correct."; wrapper noise such as "This item tests your reasoning" (51), "Focus on the problem in this item" (42), "handed to a student" (333).
- **Code/data claims:** all stimulus fields are null. Networks labels 400 DIAGRAM, 400 SOURCE_BASED and 400 DATA_INTERPRETATION items but supplies no diagram, trace, table or frame (333 stems claim a "neighbour table is also available"). DSA's 503 CODE_TRACING items carry real snippets (84 are operation traces, not code); DB's 164 carry SQL but no table data; 42 OS items cite "matrices of a worked example" that are absent.

## Ten worst items

| # | File / id | Quote | Failure | Fix |
|---|---|---|---|---|
| 1 | `differential-equations/diffeq-batch-001.json` — `diffeq-1.4-0001` | "Is y' = 1y - 1y^2 autonomous?" → correct option "No, the right side has no explicit x" | Answer text contradicts its own explanation; inverted in all 68 items. | Swap to "Yes, because x does not appear explicitly". |
| 2 | `discrete-mathematics-2/discmath2-batch-03.json` — `discmath2-1.3-0006` | "A derangement has no fixed points; !8=25487" | !8 = 14833; 25487 counts permutations *with* a fixed point (18 wrong items). | Replace with round(n!/e); regenerate explanation. |
| 3 | `differential-equations/diffeq-batch-001.json` — `diffeq-2.3-0001` | "Is (1x + 2y)dx + (1x + 1y)dy = 0 exact?" → "Yes, because M_y equals N_x" | M_y=2, N_x=1; wrong verdict and false "here M_y = 1" (17 items). | Recompute cross-partials per item. |
| 4 | `operating-systems/os-batch-01.json` — `os-3.2-1002` | "Using nonpreemptive shortest job first" → "P1, P4, P2, P3" | Bursts 9,3,5,2 give SJF P4,P2,P3,P1; answer is priority order (4 items). | Recompute from bursts; ignore priorities. |
| 5 | `computer-networks/networks-batch-01.json` — `networks-1.1-0009` | "For scenario 9 at 192.0.2.10, a router's neighbour table is also available" | SOURCE_BASED with no source; stimulus null. | Attach a real table or drop the format label. |
| 6 | `computer-networks/networks-batch-01.json` — `networks-1.1-0006` | "A trace captured at 192.0.2.7 during exercise 6 is handed to a student" | DIAGRAM item with no diagram/trace; 400 such items. | Supply the trace or convert to CONCEPT_MCQ. |
| 7 | `data-structures/dsa-batch-001.json` — `dsa-2.4-0001` | "For the matrix [[[1, 6], [1, 4], [6, 0], [0, 1]]] … what is the row sum?" | No row index given; explanation silently assumes "row 2" (28 items). | State the row index in the stem. |
| 8 | `operating-systems/os-batch-01.json` — `os-5.2-1015` | "allocation and maximum matrices of a worked example, and the available vector is [2, 1, 1]" | Matrices never supplied; safe sequence P1→P2→P3→P4 unverifiable (42 items). | Include the matrices in stimulus. |
| 9 | `data-structures/dsa-batch-001.json` — `dsa-2.2-0001` | Distractor D: "8-1" with rationale "reports the first index" | Unevaluated arithmetic masquerading as an option (26 items). | Use a numeric distractor with a matching rationale. |
| 10 | `discrete-mathematics-2/discmath2-batch-03.json` — `discmath2-1.3-0003` | "What is the 9th Catalan number?" (also 0025, 0029, 0054) | Same stem and answer 4862 repeated under four ids; 121 duplicate groups bank-wide. | Keep one item; delete or rewrite the copies. |

Priority: fix the wrong-answer templates (items 1–4) before deduplication and formatting cleanup.
