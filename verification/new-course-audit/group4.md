# Group 4 audit: modern-physics + thermodynamics

Scanned all 8,000 items. IDs, option counts, misconceptions, explanations and answer shapes clean; 50 hand-checked numeric items matched their explanations. Systemic defects: 4,000 thermo stems begin `Trial <9-digit>:`; 3,994/4,000 modphys and 4,000/4,000 thermo are number-swapped clones (101/386 distinct stems); 67/67 "Entropy of the Surroundings" keys are sign-flipped; 15,740 modphys options have trailing spaces.

## 10 worst items

| # | File / id | Quote | Failure | Fix |
|---|---|---|---|---|
|1|thermo-batch-001 / thermo-5.3-0023|"gain 1701 J ... positive 5.58 J per K"|Key D=−5.58 contradicts own explanation|Use A=+5.58|
|2|thermo-batch-012 / thermo-5.3-1163|"positive 6.29 J per K"|Key C=−6.29 mislabeled correct|Use B=+6.29|
|3|thermo-batch-007 / thermo-5.3-0623|"positive 2.06 J per K"|Key A=−2.06|Use D=+2.06|
|4|thermo-batch-034 / thermo-5.3-3323|"positive 2.28 J per K"|Key D=−2.28|Use B=+2.28|
|5|thermo-batch-001 / thermo-1.1-0001|"Trial 897938224: a laboratory sensor..."|Generation scratch in stem|Strip prefix globally|
|6|thermo-batch-001 / thermo-5.5-0025|"37485000.0 J per K" vs answer 294.1|Distractor 127,000× too large|Rescale distractor|
|7|thermo-batch-001 / thermo-11.5-0055|"7.71e+3 J" vs answer 1.28e-20 J|Impossible N_A-scale distractor|Use R-based value|
|8|modphys-001 / modphys-1.1-0001|"5.03 × 10^-1 us "|Trailing space on all options|Trim whitespace|
|9|modphys-016 / 2.3-0155 vs -0160|"927 volts" / "92 volts"|Near-duplicate template, shared explanation|Vary scenario|
|10|modphys-022 / 2.7-0080 vs -0119|"14 degrees" / "143 degrees"|Near-duplicate spin template|Vary scenario|
