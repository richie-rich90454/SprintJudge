# Independent final audit

`node scripts/check-bank.mjs`: clean (329,652 questions, 82 dirs, 0 errors/warnings).

Passed: duplicate ids/stems/exact option text = 0; empty explanation or misconception = 0; scripts present (Arabic 210,674 chars, Hangul 164,494, Kana 88,499, Han 249,044); 4-option answer skew <15pp in every dir.

Remaining defects, ranked:

1. **Critical — wrong key.** `nutrition-batch-033.json` / `nutrition-10.1-0066`: "BMI of 90 kg/m2" keyed A "Overweight, 25 to 29.9"; correct is C "Obese, at 30 or above". Fix: flip `correctId` to C and rewrite the explanation.

2. **High — template labels in stems.** 8,940 items/23 dirs. `accounting-1.1-0001` "Case 0001:"; `analychem-1.1-0001` "Sample 0001:"; `networks-3.1-0001` "For scenario 801 at 192.0.2.52"; `dsa-6.4-0001` "Given the problem as stated, answer." Fix: regex-strip leading `Case/Sample/Trial/Item/Session/Ticket/Q N` and `scenario/lab exercise N` prefixes.

3. **High — `undefined` as generated parameter.** 935 stems: `discmath2-1.2-0032` "On the figures 4, undefined"; `aiml-3.2-0280` "The numbers 0.4 and undefined apply here". Fix: substitute the intended value.

4. **Medium — numeric distractors <5% apart.** 5,609 all-numeric items; `accounting-4.1-0005` offers "1.01" and "0.99" (2%). Fix: widen gaps to ≥5%.

5. **Medium — skew on short option lists.** 3-option 96.8% A (n=62), 2-option 100% A (n=12), 5-option 76.9% A (n=13), e.g. `calc1-4.7-0005`. Fix: rebalance keys.

6. **Low — non-independent options.** `englit-8.3-0037`: all four options begin "The difference is what each extract does with the fact…"; `int2-1.10-0001` option C says "identical to option B". Fix: rewrite distractors.

Skipped: full review of all 329,652 records (sampled 10/dir) and answer-correctness spot-checks beyond the sample — item 1 shows that residual risk.
