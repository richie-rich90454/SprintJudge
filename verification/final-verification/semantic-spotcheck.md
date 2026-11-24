# Semantic spot-check (read-only)

Sampled 25/subject in all 82 dirs = 2,050 items; all 258,476 four-option MCQs structurally checked (1 exact duplicate option bank-wide). Deep recomputation on ~525 quantitative items; rest spot-read.

## High — wrong key / recompute fails
1. `calculus-1/u5-t3-natural-log-derivative` `calc1-5.3-0051`: keyed C `1/(x ln(x²))`. True derivative `2/(x ln(x²))` (x=2: 0.72 vs 0.36). Fix: key D; fix explanation.
2. `calculus-2/u4-t2-divergence-test...` `calc2-4.2-0015`: keyed C claims `1/n+1/n³ ≤ 2/n²`; equivalent to (n−1)²≤0, false for n>1; series diverges. Fix: drop item.
3. `analytical-chemistry/u6-t3-batch` `analychem-6.3-2434`: keyed D `115.30 %` impossible. 0.9280×0.727=0.6747 g ⇒ `61.00 %`. Fix: key A; invert explanation.
4. `analytical-chemistry/u1-t2-batch` `analychem-1.2-0168`: 8.32×6.52=54.2464 ⇒ `54.2`; keyed `54.3`. Fix option A.
5. `chemistry/u14-t3` `chem-14.3-0003`: Kc=0.4²/(0.1²·0.2)=`80`, not keyed `16`. Fix option B.

## Medium — contradiction / impossible premise
6. `algebra-2/u8` `alg2-8.20-0044`: explanation calls keyed D a distractor.
7. `calculus-1/u11` `calc1-11.20-0026`: "not its own inverse, since squaring it gives the identity".
8. `physics-1/u14` `phys1-14.23-0053`: "Option B adopts the old wording" — B is the modern view.
9. `mathseed-batch-002` `mathseed-1.2-0098`: stem asks #assignments with a fixed variable true (=8); keyed 1.
10. `analychem-8.3-3267`: key `3.82` is −log C, not a concentration; explanation yields ~1.5e−4 M.
11. `chem-1.6-0038`: stem gives no number ("How many significant figures?").
12. `mech-3.2-0011`: "either angle beats 45 degrees" — 45° is the maximum.
13. `mech-14.20-0019`: options B and C are mathematically identical; `calc1-4.5-0049`: classmate's answer is correct, A/C duplicate.

Low: ~5 analychem numeric tails off 0.1–0.3 %; `chem-11.1-0005` false temperature claim; `calc1-8.5-0048` incoherent stem; several topic-label mismatches.
