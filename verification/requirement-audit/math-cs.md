# Math/CS bank audit

Read-only; all 88,952 questions scanned in memory (15/dir read closely incl. flagged). Source: `src/main/resources/bank`.

**Counts** (opt≠4 / shape-outliers / meta-items / undefined-stems): alg1 0/32/15/1; alg2 11/16/528/14; geom 8/18/604/0; precalc 0/0/209/2; calc1 16/2/679/24; calc2 0/0/157/3; stats 21/10/533/2; java 52/191/87/0; py 4/1/130/0; cf 45/110/36/0; ds 4/0/30/1; cyber 4/0/70/0; web 39/1/115/5; c 0/0/176/0; cpp 0/36/76/0; js 0/0/130/1; math 0/0/0/0; la 0/0/0/0; de 0/0/0/0; dm2 0/0/98/791; dsa 0/0/180/0; os 0/126/876/0.

Globals: 0 invalid JSON, 0 duplicate IDs, 0 unit mismatches, 0 branded names; 204 option-count breaks, 543 answer-shape outliers, 210 missing fields, 13,418 key-options with `misconception`, 22,000 template-duplicate stems, 3,813 intra-item duplicate options, 4,729 meta-phrase items, 844 undefined-stems, 2,059 stems <45 chars. ~1,600 recomputations: 49/49 maxterms keys wrong, 81/168 OS schedule averages wrong.

**15 worst defects** (file, id, quote → why; fix):

1. mathseed-batch-032.json, mathseed-9.1-0012: "false on exactly 28… maxterms" key 4; maxterms=28. Use k.
2. calculus-1-u4-t3b-optimization-2.json, calc1-4.3-0112: "local minimum at x=4"; g'=3(x−2)²≥0. Delete premise.
3. algebra-2-u5-t3-tangent-to-a-circle.json, alg2-5.3-0002: "0-4 equals -40"; B²−4AC=−4. Fix arithmetic.
4. computing-foundations-d4o-cloud.json, cf-4.25-0039: "40×2=80" in-flight; cap 40. Fix formula.
5. os-batch-01.json, os-3.1-1097: SJF "5.75" vs 4.25; recompute all four averages.
6. algebra-2-u10-t7-reading-a-first-difference.json, alg2-10.7-0030: 5 options, "Let's solve:"; use 4 clean options.
7. web-development-u1-t26-the-document-as-a-tree-rat.json, web-1.26-0002: A/C/D only; restore B.
8. calculus-1-u4-t7-related-rates-with-units.json, calc1-4.7-0005: 3 options; add fourth.
9. linalg-batch-001.json, linalg-1.1-0001: key misconception "Correct pivot count"; blank it.
10. cppseed-authored-001.json, cppseed-1.2-0001: key misconception "Correctly identifies…"; blank it.
11. cseed-batch-001.json, cseed-1.2-0009: key misconception "Correct result…"; blank it.
12. discmath2-batch-01.json, discmath2-1.1-0001: key misconception "Correct binomial coefficient."; blank it.
13. discmath2-batch-23.json, discmath2-6.3-0085: "inputs 3, undefined"; supply real data.
14. os-batch-01.json, os-3.1-1001: "Here is scenario 1001"; drop label.
15. precalculus-u12-t7-the-maclaurin-series-for-c.json, precalc-12.7-0024: "B misses factorial" contradicts key B; delete.
