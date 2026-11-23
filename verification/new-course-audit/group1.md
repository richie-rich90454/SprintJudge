# Group 1 audit — 36,000 questions

9 banks, 30+/dir read. Clean: IDs, units, options, misconceptions, answer shape, expl ≥60c. No "is the outcome"/"as stated"; "the key"/"the item" elsewhere is prose.

Near-dup stems (digit-masked): c 3,885, cpp 414, js 3,436, math 2,331, aas 16, comprinc 3,865, ja 30; it/latin 0. Exact dups 0. c: 1,941 "the key"/"fails because", 177 "undefined". Fixes: rewrite c expl; de-template c/js/comprinc/math; merge ja/aas.

| File/id | Quote | Fail | Fix |
|---|---|---|---|
| cseed-batch-001 `cseed-1.2-0009` | "Option A is the key... fails because undefined" | meta ×1941 | rewrite |
| cseed-batch-001 `cseed-1.2-0031` | "For data set 31 ... 2 + 3 * 4 - 1" | clone | vary |
| comprinc-converted-001 `comprinc-1.1-0001` | "Which statement about bit holds for 100 values?" | filler | drop |
| japanese-32 `japanese-5.1-0001` | "the particle は (wa) in this sentence" | clone ×50 | split |
| japanese-32 `japanese-5.1-0041` | "including the item in a set" | clone | merge |
| mathseed-batch-001 `mathseed-1.1-0044` | "In a skills check, 8, 7 - With 8 Boolean" | scratch | reword |
| jsseed-generated-001 `jsseed-4.2-0231` | "A routine processes 231: const arr=[231,2,3]" | clone ×3436 | vary |
| aas-batch-11 `aas-3.2-0009` | "The other choices describe motives..." | boilerplate | diversify |
| cppseed-authored-001 `cppseed-8.2-0001` | "The example centers on log entries. In std::map" | clone ×414 | vary |
| aas-batch-29 `aas-6.4-0056` | "chiefly unfolded in this period: 1964" | leak | rephrase |
