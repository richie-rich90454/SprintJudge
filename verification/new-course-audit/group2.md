# Group 2 audit: organic-chemistry-1/2, physical, analytical, biochemistry

20,000 questions scanned in memory; 30/dir read (10 flagged + 20 unflagged).

Clean: no duplicate IDs, unit-label mismatches, short explanations, or scratch/meta; empty misconceptions occur only on correct options (by design); keys ~25% A–D. orgchem2 non-MCQ formats legitimately use empty options + `answer.value` (false-positive). Real defects: 18 orgchem1 items (5–6 options, 4 keyed E/F), 8 biochem items (3 options), 233 orgchem1 misconception leaks, heavy near-duplicate templating (no exact dups).

## 10 worst items

| # | file / id | quote | failure | fix |
|---|---|---|---|---|
|1|orgchem1-batch-02 / orgchem1-7.1-0004|"ketone carbonyl: near 1715, the target of this absorption"|misconception describes another question's answer; stem asks 3400|rewrite citing 1715 only|
|2|orgchem1-batch-03 / orgchem1-7.1-0008|"not this strong carbonyl region"|wrong region for 3400 cm-1 stem|drop "carbonyl region"|
|3|orgchem1-batch-02 / orgchem1-2.2-0004|6 options, correctId=F|>4 options; key outside A–D|cut to 4, rekey|
|4|orgchem1-batch-02 / orgchem1-3.3-0003|5 options, correctId=E|same shape violation|cut to 4|
|5|biochem-008 / biochem-8.3-0713|"Which pathway or process is Adenine most closely tied to?" (3 options)|option-count≠4|add 4th distractor|
|6|biochem-013 / biochem-2.1-1285|"which amino acid has the highest isoelectric point?"|~200 near-identical stems, identical explanation|dedupe/diversify|
|7|orgchem2-batch-08 / orgchem2-4.2-0001|"Analyzing the data, where does a nucleophile attack an unsymmetrical epoxide…"|15× lead-in-only variants|vary content|
|8|analychem-u3-t5-batch / analychem-3.5-1301|"Sample 1301: … equivalence point near pH 8.9"|100× "Sample N" template|diversify|
|9|physchem-batch-01 / physchem-1.1-0002|"At 422 K and 2.33 atm … ideal gas?"|73× numeric template|diversify|
|10|orgchem1-batch-01 / orgchem1-7.1-0001|"near 3300–3500, not this value"|3300–3500 includes the 3400 answer|narrow distractor|
