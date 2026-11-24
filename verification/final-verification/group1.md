# Final verification — group 1 (24 dirs, 97,061 items)

`node scripts/check-bank.mjs`: clean (329,652 questions / 4,594 files). Zero duplicate IDs, stems, or within-item options. Language script N/A (no language dirs in scope; code fits c/cpp/js/py/java).

| Dir / id | Evidence | Defect → fix |
|---|---|---|
| c (1,982 MCQ) | A 80.4 / B 19.4 / C 0.2 / D 0.1% | key skew → rebalance to 10–40% |
| javascript (2,732) | A 98.8 / B 1.2 / C,D 0% | key skew → rebalance |
| math (2,512) | A 98.9 / B 0.8 / C 0.4 / D 0% | key skew → rebalance |
| algebra-1, physics-em | A 9.4%, 7.3% | marginal → nudge |
| `cseed-1.2-0001` | "For data set 1, a sensor lab repeats a value 3 times across 4 rows" | 3,885 cloned stems → rewrite |
| `mathseed-1.1-0033` | "For a homework assignment, taking a closer look, work through this problem:" | 600 stem preambles → strip |
| `mathseed-1.1-0033` | "Choice B is a distractor: it shifts to a subset..." | 2,480 meta explanations → vary |
| `geom-1.1-0001` | "The key is A, '53 degrees', because it is the only choice" | 2,705 template explanations (calc1 679, geom 603, stats 525, alg2 522, precalc 208, calc2 157) → rewrite |
| `cppseed-5.2-0024` | "Let the snippet be part of a system tracking cache lines." | 138 cloned openings → vary |
| `jsseed-1.5-0204` | "On the supplied values ... one step at a time" | 1,032 / 956 template stems → vary |

Also 6,426 "is the answer a classmate submitted" frames; 1,337 cross-item exact option-set collisions (numeric reuse), zero within-item. "case N" and "undefined" are subject matter; no "as stated", "Option A is the key", or "Scenario N" filler.

Skipped: full read of all items (20/dir sampled), cross-group duplicates. Risk: skew/clone counts are exact; unscanned dirs may share these templates.
