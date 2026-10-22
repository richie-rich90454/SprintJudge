# Volume Targets

Every course ships at least 4,000 questions.
Macroeconomics and Microeconomics may share one combined 4,000+ pool.

## Per-course floor

Java Programming 4,000. Computing Foundations 4,000. Chemistry 4,000.
Calculus I 4,000. Calculus II 4,000. Physics I 4,000. Physics II 4,000.
Physics: Mechanics 4,000. Physics: Electricity and Magnetism 4,000.
European History 4,000. United States History 4,000. World History 4,000.
Macroeconomics plus Microeconomics 4,000 combined. Statistics 4,000.
United States Government 4,000. Comparative Government 4,000.
Psychology 4,000. Environmental Science 4,000. Human Geography 4,000.
English Language and Composition 4,000.
English Literature and Composition 4,000. Biology 4,000.
Spanish, French, German, Chinese Language and Culture 4,000 each.
Art History 4,000. Music Theory 4,000. Precalculus 4,000.
Research Methods 4,000. Interdisciplinary Seminar 4,000.
Algebra I, Algebra II, Geometry 4,000 each.
Integrated Math I, II, III 4,000 each.
Python Programming, Web Development, Cybersecurity, Data Science
4,000 each.

## Per-unit floor

Every unit ships with 50+ questions, full format coverage, full scope
coverage, 20/40/30/10 cognitive-demand balance, p-values spanning
0.2-0.9, and every named misconception covered by a distractor.

## Enforcement

`make check-volume` and `make check-unit-volume` run in CI on every PR.
A course below the floor is a build failure, never a warning.

A subject directory that has reached its floor carries a `.shipped` marker
(`scripts/mark-shipped.mjs` writes it). The marker makes that subject's floor
binding: `check-volume` then fails the build if the subject later falls below
it, instead of only warning.
