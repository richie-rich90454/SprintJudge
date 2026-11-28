# Final verification — group 3 (36 dirs, 144,000 items)

`node scripts/check-bank.mjs`: clean. In-memory scans: 0 duplicate ids, stems, or exact option texts. Scripts present where applicable (Arabic 4,000/4,000; Hangul 4,000/4,000; Kana 4,000/4,000). 15 items/dir read manually.

## Remaining defects

| Dir | File / id | Quote | Fix |
|---|---|---|---|
| differential-equations | `diffeq-batch-001.json#diffeq-1.1-0001` | "Only the option consistent with this derivation is correct." (×4,000); key A in 3,984/4,000 (99.6%) | Rotate correctId A–D; replace filler with item-specific reasoning |
| computer-networks | `networks-batch-01.json#networks-1.1-0001` | "a trace … is handed to a student"; "a router's neighbour table is also available" (333/666 items), `stimulus: null`; key only B/D (50/50) | Attach trace/table or relabel format; rotate keys to A–D |
| arabic | `arabic-batch-001.json#arabic-1.1-0001` | Option A ends "[الدرس ١.١ / السؤال ٠٠٠١]" (×4,000, all options) | Strip bracket tags from option texts |
| anthropology | `anthropology-u01-t01.json#anthro-1.1-0001` | "That is why option A is correct:" (×4,000, letter matches key) | Delete meta clause; let the rationale stand |
| accounting / analytical-chemistry / networks / OS / ai-ml / discmath2 | `accounting-batch-01.json#accounting-1.1-0001` | "Case 0001: …" — 9,266 numbered stem prefixes bank-wide | Strip numeric prefixes from stems |
| dsa / OS / analytical-chemistry | `dsa-batch-002.json#dsa-7.4-0003` | Options 0.81 / 0.80 (1.25% gap); 452 numeric-format items under 5% | Space numeric distractors ≥5% apart |
| linalg / ai-ml / marketing | `linalg-batch-009.json#linalg-8.1-0055` | "det([[-5, --9] …", "e^{--5.5}", "a established firm" | Fix double-minus typos and article errors |

Korean key A=33.6% is within the 15-point tolerance.
