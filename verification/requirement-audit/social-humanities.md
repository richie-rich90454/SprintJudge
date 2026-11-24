# Requirement Audit — Social Sciences & Humanities (40 banks)

Read-only. Exhaustive in-memory scan: 2,188 files / 160,521 questions (133,819 MCQs); 15 read/dir; 15 worst re-opened.

## Counts
- Schema/playability: 0 errors; all MCQs have 4 options A–D, named distractor misconceptions; 0 exact duplicate stems; 0 within-item dup options.
- Key ~25%: 21/40 fail. First 20 A-low/C-D-high (us-history 10.8%, psychology 11.5%, macro 11.4%); korean A=33.6%. Last 20 ≈25%.
- Meta 253 (world-history 55, graphic-design 40); generic expl 1,271 (arabic 700, databases 122, marketing 90).
- Reused option sets 47,367 items (film-studies/graphic-design/computer-networks 4,000/4,000). Brands 11 (film-studies 10).
- Script: CJK/Arabic/Korean 0 gaps; Japanese 1,246 romaji-only (no kana/kanji).

## 15 worst defects
| File / id | Quote | Why / Fix |
|---|---|---|
| world-history-u13-t7 `world-13.7-0001` | "Question 0001: How did the commitment mechanism…" | Meta numbering; strip prefix. |
| world-history-u13-t7 `world-13.7-0004` | "Using that evidence, question 0004: What did a trial…" | Meta; use content cue. |
| world-history-u12 `world-12.20-0018` | "…to be written…" | Scratch; finish sentence. |
| spanish-u1-t8 `span-1.8-0003` | "The other options describe unrelated patterns." | Generic; cite distractors. |
| german-u10-t6 `germ-10.6-0005` | "The other options describe errors…" | Boilerplate; name error. |
| englang-rhetoric `englang-1.1-0002` | "the other choices are background facts…" | Generic; tie to text. |
| arabic-001 `arabic-1.1-0001` | "the glosses of nearby forms" | 700× boilerplate. |
| graphic-design `graphic-1.1-0001` | options reused 40× | Vary distractors. |
| business `business-1.1-0001` | options reused 50× | Rewrite distractors. |
| comprinc `comprinc-1.1-0043` | options reused 21× | Vary values. |
| sociology-01 `sociology-1.1-0001` | "2001 Midwest job loss" ×3 | Change scenario. |
| us-history-coldwar `ushist-7.1-0001` | A=10.8% dir-wide | Rebalance keys. |
| korean-01 `korean-1.1-0001` | A=33.6% dir-wide | Rebalance keys. |
| film-u10-t1 `film-10.1-0091` | "Netflix's release of House of Cards…" | Brand; genericize. |
| japanese-17 `japanese-2.1-0003` | "'to eat'?" → taberu (romaji) | Add kana/kanji. |

Skipped: full reread of 160k items; excluded brand false positives (`windows`, `daruber`).
