# Course Coverage

The bank ships at least 4,000 questions per course. This document tracks the final
catalog. Names are self-standing; no external program or brand name is used.

## Shipping catalog (82 courses)

The catalog is complete: 42 original courses, 4 fill courses, and 36 new
courses, each at or above the 4,000-question floor (329,652 questions total).
Question content was generator-authored with audited fixes applied, not
bespoke hand-authoring. Six platform feature tracks are implemented:
analytics, assignments/LMS, player profiles, localization, accessibility,
and authoring/rich-stem tooling. All bank content gates are green; the full
gate matrix and its recorded outcomes are in
`verification/final-verification/integration.md`. Final verification ledgers
live in `verification/final-verification/` (final group sweeps and integration
summary), alongside `verification/merged-bank-verification/` and
`verification/new-course-audit/`.

### Original (42)

Java Programming, Computing Foundations, Chemistry, Calculus I, Calculus II,
Physics I, Physics II, Physics: Mechanics, Physics: Electricity and Magnetism,
European History, United States History, World History, Macroeconomics,
Microeconomics, Statistics, United States Government, Comparative Government,
Psychology, Environmental Science, Human Geography, English Language and
Composition, English Literature and Composition, Biology, Spanish Language and
Culture, French Language and Culture, German Language and Culture, Chinese
Language and Culture, Art History, Music Theory, Precalculus, Research Methods,
Interdisciplinary Seminar, Algebra I, Algebra II, Geometry, Integrated Math I,
Integrated Math II, Integrated Math III, Python Programming, Web Development,
Cybersecurity, Data Science.

### Fill (4)

C Programming, C++ Programming, JavaScript Programming, Discrete Mathematics.
Converted records carry `converted-seed` tags and distinct id prefixes
(`cseed`, `cppseed`, `jsseed`, `mathseed`); each course is now filled to 4,000.

### New (36)

African American Studies, Computing Principles, Italian Language and Culture,
Japanese Language and Culture, Latin, Organic Chemistry I, Organic Chemistry
II, Physical Chemistry, Analytical Chemistry, Biochemistry, Linear Algebra,
Differential Equations, Discrete Mathematics II, Data Structures and
Algorithms, Operating Systems, Computer Networks, Databases, Artificial
Intelligence and Machine Learning, Modern Physics, Thermodynamics, Personal
Finance, Business and Entrepreneurship, Accounting, Marketing, Introduction to
Law, Ethics and Philosophy, World Religions, Sociology, Anthropology,
Astronomy, Earth Science, Nutrition and Health, Film Studies, Graphic Design,
Arabic Language and Culture, Korean Language and Culture.

## Onboarding a course

1. Add the self-standing name to `docs/no-ap-branding.md`,
   `docs/subject-rename-map.md`, `docs/volume-targets.md`, the schema subject
   enum, and both lists in `frontend/src/screens/BankBrowser.tsx`.
2. Author questions from first principles in `src/main/resources/bank/<slug>/`.
   Framework documents may guide scope but their text is never copied.
3. Run `node scripts/check-bank.mjs`, `node scripts/check-volume.mjs`,
   `node scripts/check-unit-volume.mjs`, and the verification subagent sweep
   for the new subject before adding a `.shipped` marker.
