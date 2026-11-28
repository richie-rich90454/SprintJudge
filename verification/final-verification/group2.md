## Group 2 — final verification

`node scripts/check-bank.mjs`: exit 0, **clean** (9,104 advisories, 0 errors). Read 20/dir × 21; scanned 88,591 questions.

**Clean:** duplicate IDs, stems, exact options — 0 in scope. Cross-dir stem duplicate: `chin-1.2-0001` = `mathseed-1.1-0018` (advisory).

| Defect | File / ID | Quote | Fix |
|---|---|---|---|
| Doubled opening clause, 6,017 explanations (fren 1244, chin 1102, germ 1015, englang 966, span 942, musict 598, arthist 107, englit 39) | `english-language/…u4-t4-icon-and-label.json` `englang-4.4-0032` | "Close reading of the note yields this rule: close reading of the note yields this rule:" | Delete first clause. |
| Trailing bare misconception label, 918 items (humgeo 192, euro 167, usgov 149, psych 136, others) | `psychology/…u2-t4-neuron-structure.json` `psych-2.4-0007` | "…supports about -70 millivolts. Confuses peak with resting." | Remove fragment; keep in `misconception`. |
| No target-language script: spanish 42, french 68, german 10, chinese 60 wholly English | `spanish/…u4-t3-structure-of-an-argument.json` `span-4.3-0007` | "Ticket 1007 asks a developer to label the main claim in a short argument about caching." | Rewrite in target language. |
| Missing evidence | `spanish/…u1-t2-ser-and-estar.json` `span-1.2-0001` | "…se lee: La nota ofrece cuatro afirmaciones posibles…" | Insert note. |
| Skew >15pp: art-history A=5.2% (143/2,736) | `art-history` | B 31.8%, C 30.8%, D 32.1% | Rebalance keys toward A. |
| Invalid key / contradictory stem | `integrated-3/…u1-t2-reading-a-limit-from-a-gra.json` `int3-1.2-0004`; `…u1-t13-…json` `int3-1.13-0022` | "value 0 signals nonexistence in this encoding" (answer.value=0); "f(3)=5 … but f is undefined at 3" | Free-response for DNE; drop "f(3)=5". |
