# Catalog consistency audit

Source of truth: `contracts/question-schema.json` subject enum (82 codes). Read-only check.

## Pass

- **Bank directories / volume:** 82/82 dirs under `src/main/resources/bank/`, each holding >=4,000 records by its own `subject` field (range 4,000–4,050). Total 329,652.
- `docs/subject-rename-map.md`: 82/82 entries.
- `docs/no-ap-branding.md`: 82/82 entries.
- `docs/course-coverage.md`: 82/82 entries.
- `check-volume` vs `check-bank`: agree exactly — both report 329,652 questions across 82 subjects, 0 per-subject count differences; `check-bank` exits 0 / clean.

## Mismatches

1. `docs/volume-targets.md` — 36 "New" courses have no entry: AFRICAN_AMERICAN_STUDIES, COMPUTING_PRINCIPLES, ITALIAN, JAPANESE, LATIN, ORGANIC_CHEMISTRY_I, ORGANIC_CHEMISTRY_II, PHYSICAL_CHEMISTRY, ANALYTICAL_CHEMISTRY, BIOCHEMISTRY, LINEAR_ALGEBRA, DIFFERENTIAL_EQUATIONS, DISCRETE_MATHEMATICS_II, DATA_STRUCTURES, OPERATING_SYSTEMS, COMPUTER_NETWORKS, DATABASES, AI_ML, MODERN_PHYSICS, THERMODYNAMICS, PERSONAL_FINANCE, BUSINESS_ENTREPRENEURSHIP, ACCOUNTING, MARKETING, INTRO_LAW, ETHICS_PHILOSOPHY, WORLD_RELIGIONS, SOCIOLOGY, ANTHROPOLOGY, ASTRONOMY, EARTH_SCIENCE, NUTRITION_HEALTH, FILM_STUDIES, GRAPHIC_DESIGN, ARABIC, KOREAN. Also 5 covered only by grouped phrasing, not a standalone name: SPANISH/FRENCH/GERMAN ("Spanish, French, German, Chinese Language and Culture 4,000 each") and INTEGRATED_2/INTEGRATED_3 ("Integrated Math I, II, III 4,000 each").
2. `frontend/src/screens/BankBrowser.tsx` — both `SUBJECT_CODES` and `SUBJECTS` list only the first 46 subjects (through DISCRETE_MATHEMATICS); the same 36 "New" courses above are absent from both lists.
