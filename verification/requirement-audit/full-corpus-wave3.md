# Full-corpus audit, third wave — execution and independent recomputation

Five parallel agents verified answer keys by running code or recomputing values from stems rather than trusting explanations.

| Cluster | Scope | Confirmed errors fixed |
|---|---|---|
| Java + Python | 3,604 code-bearing items compiled/executed | 8 wrong keys (integer identity, recursion, print tracing) |
| C + C++ + JavaScript | 12,000 items executed or rule-verified | 1 wrong key (dot operator does not dereference) |
| Web, data structures, OS, computing foundations, cybersecurity | 3,399 code-bearing items | 3 wrong keys, 34 stale explanation letters |
| Statistics, data science, algebra 2, geometry, precalculus | 20,064 items screened; 12,404 numeric | 9 wrong keys, 5 contradictory explanations |
| Calculus 2, differential equations, linear algebra, discrete math 2, math | ~6,500 items independently recomputed | 213 wrong keys (inverse Laplace, Laplacian, reduction of order, prime counts) |

All fixes rekeyed from independently derived values and bumped `version` and `provenance.reviewedAt`. Question ids, option ids, subjects, units, topics, and volumes were preserved. Gates after the wave: bank 0 warnings/0 errors, full regression clean, E2E 30/30.

Skipped: 483 Java/Python items with no runnable code shown relied on explanation consistency; CSS/DOM semantics were spot-checked rather than executed; exhaustive hand-recomputation of all ~18.5k numeric stems was not performed. Risk: a wrong key whose value coincidentally matches a simple expression could remain in the screened numeric pools.
