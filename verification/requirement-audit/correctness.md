# Deep correctness audit (read-only)

Coverage: ≥100 items/dir in all 20 dirs. Full recomputation: 4,000 thermodynamics, 2,800 analytical-chemistry, 820 linear-algebra items (from stems, not explanations); others sampled for key/explanation/option consistency. No bank files changed.

## Ranked defects

1. **CRITICAL — inverted gravimetric factor, 49/50.** `analytical-chemistry/analychem-u6-t3-batch.json`, `analychem-6.3-2402`: precipitate 1.0817 g, factor 0.606, sample 4.409 g. Correct = 1.0817×0.606/4.409×100 = **14.88** (option D, labelled distractor); key A = 40.456 divides. `analychem-6.3-2434` uses identical wording but multiplies — contradictory.

2. **CRITICAL — resolution halved, 50/50 items.** `analytical-chemistry/analychem-u9-t2-batch.json`, `analychem-9.2-3401`: Rs = 2(5.46−3.65)/(0.81+0.81) = **4.469**; key D = 2.246, while the explanation itself states the factor-of-two formula (option C 4.49 called a distractor).

3. **HIGH — plate height off by 100×, 50/50 items.** `analytical-chemistry/analychem-u9-t3-batch.json`, `analychem-9.3-3501`: log10 H(mm) = log10(142/10^4.33) = **−2.178**; key C = −0.178. Correct value is not among the options.

4. **HIGH — ion-selective concentration keys wrong, 50/50 numeric items.** `analytical-chemistry/analychem-u8-t3-batch.json`, `analychem-8.3-3201`: c = 4.83e−3×10^(−37.3/59.16) = **1.13e−3 M**; key D = 2.947 (mM), also contradicting its own explanation. Later items diverge further (8.3-3205: derived 0.0279 mM vs key 4.553).

5. **MEDIUM — wrong numeric key.** `thermodynamics/thermo-batch-005.json`, `thermo-4.1-0436`: Q = nRT ln(Vf/Vi) = 3.4·8.314·341·ln(3.2/1.5) = **7.304 kJ**; key/explanation say 7.15 kJ.

6. **MEDIUM — impossible premise.** `calculus-1/calculus-1-u3-t4-quotient-rule-forms.json`, `calc1-3.4-0056`: R′(2)=1/25=0.04 is correct, yet the stem calls 1/25 an error to diagnose; key equals the alleged error.

7. **LOW — stale answer letters.** 9 items in `algebra-1/algebra-1-quadratics.json` and `algebra-1-linear.json` name a different letter than `correctId` (text matches key), e.g. `alg1-3.1-0006`: key A, explanation "The key is B".

8. **LOW — template text.** 11 explanations quote "the opposing verdict" where the keyed text is "The claim is correct" (e.g. `calc1-5.4-0026`); 17 `analychem-u10-t1` sample-size keys differ ~1–2% from (zs/E)²; discrete-math stems still contain "undefined" placeholders.

Skipped: full ~80k line-by-line review; residual risk in untemplated stems.
