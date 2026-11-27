# Full-corpus audit, sixth wave — twelve direct bank audits

Twelve parallel auditors re-examined all 82 banks from scratch. Each scanned every question in its assigned dirs and read at least 25 questions per dir directly, then fixed confirmed defects in place.

## Coverage and outcomes

| Group | Dirs | Scanned | Read directly | Fixed (representative) |
|---|---|---:|---:|---:|
| 1 | accounting, african-american-studies, ai-ml, algebra-1/2, analytical-chemistry, anthropology | 28,073 | 175 | 1,789 scaffolding/explanation fixes; 1 wrong algebra key; 3 late algebra-2 keys |
| 2 | arabic, art-history, astronomy, biochemistry, biology, business-entrepreneurship, c | 28,060 | 204 | 254 broken stems; 448 framing rewrites; 73 pI ties resolved |
| 3 | calculus-1/2, chemistry, chinese, comparative-government, computer-networks, computing-foundations | 28,181 | 238 | 56 fixes; 14 diagnose-the-error key corrections |
| 4 | computing-principles, cpp, cybersecurity, data-science, data-structures, databases, differential-equations | 28,081 | 184 | 495 then 18,012 stem rewrites; 8,477 boilerplate suffixes |
| 5 | discrete-mathematics-2, earth-science, english-language/literature, environmental-science, ethics-philosophy, european-history | 28,070 | 224 | 619 explanations repaired; growth-curve key corrected |
| 6 | film-studies, french, geometry, german, graphic-design, human-geography, integrated-1 | 28,150 | 175 | 6,784 then 13,737 stem rewrites; 4 wrong keys |
| 7 | integrated-2/3, intro-law, italian, japanese, java-programming, javascript | 28,525 | 182 | 77 Japanese multi-answer keys; Italian agreement; 4,000 intro-law explanations rewritten |
| 8 | korean, latin, linear-algebra, macroeconomics, marketing, math, mechanics | 28,000 | 207 | 111 fixes then 1,707 lead-ins and 584 macro wrappers rewritten |
| 9 | microeconomics, modern-physics, music-theory, nutrition-health, operating-systems, organic-chemistry-1/2 | 28,050 | 287 | 100 decay-width keys; 1,077 music numeric prompts; 21 halide keys |
| 10 | personal-finance, physical-chemistry, physics-1/2, physics-em, precalculus, psychology | 28,047 | 205 | 73 van der Waals keys; 675 stem/explanation repairs; 2 implicit stems |
| 11 | python, research-methods, seminar, sociology, spanish, statistics, thermodynamics | 28,319 | 195 | 544 fixes; 2,294 repeated explanations personalized |
| 12 | us-government, us-history, web-development, world-history, world-religions | 20,126 | 125 | 620 then 12,430 boilerplate/anachronism/misconception fixes |

Every follow-up gap surfaced by an auditor was closed in a second pass. A global dedupe pass diversified 14,473 duplicate/near-duplicate stems and a final sweep expanded 622 short explanations.

## Verification

`node scripts/check-bank.mjs`: 0 warnings, 0 errors. Full regression gate run after the wave: clean — frontend 1,243 tests at 100% TS/TSX coverage, backend verify with 100% Java coverage, 33 E2E specs, production health probe.
