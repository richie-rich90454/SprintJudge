# Course Coverage

The bank ships 4,000 questions per course. This document tracks the catalog and
the onboarding queue. Names are self-standing; no external program or brand
name is used.

## Shipping catalog (42 courses)

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

## Onboarding from converted legacy sets

C Programming, C++ Programming, JavaScript Programming, Discrete Mathematics.
These carry converted records with `converted-seed` tags and distinct id
prefixes (`cseed`, `cppseed`, `jsseed`, `mathseed`). They are below the floor
until new questions are authored; CI reports them as short, not shipped.

## Expansion queue

### Tier 1 — framework gaps
African American Studies, Computing Principles, Italian Language and Culture,
Japanese Language and Culture, Latin.

### Tier 2 — advanced and higher-level
Organic Chemistry I, Organic Chemistry II, Physical Chemistry, Analytical
Chemistry, Biochemistry, Linear Algebra, Differential Equations, Discrete
Mathematics II, Data Structures and Algorithms, Operating Systems, Computer
Networks, Databases, Artificial Intelligence and Machine Learning, Modern
Physics, Thermodynamics.

### Tier 3 — practical and non-framework
Personal Finance, Business and Entrepreneurship, Accounting, Marketing,
Introduction to Law, Ethics and Philosophy, World Religions, Sociology,
Anthropology, Astronomy, Earth Science, Nutrition and Health, Film Studies,
Graphic Design, Arabic Language and Culture, Korean Language and Culture.

## Onboarding a course

1. Add the self-standing name to `docs/no-ap-branding.md`,
   `docs/subject-rename-map.md`, `docs/volume-targets.md`, the schema subject
   enum, and both lists in `frontend/src/screens/BankBrowser.tsx`.
2. Author questions from first principles in `src/main/resources/bank/<slug>/`.
   Framework documents may guide scope but their text is never copied.
3. Run `node scripts/check-bank.mjs`, `node scripts/check-volume.mjs`,
   `node scripts/check-unit-volume.mjs`, and the verification subagent sweep
   for the new subject before adding a `.shipped` marker.
