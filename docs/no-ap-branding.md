# Subject Naming Policy

This project uses only self-standing subject names in every user-facing
surface, question record, document, and commit message.

## Rule

Name each course for what it teaches, never for an external program.
Do not use any test-owner brand, program name, or branded prefix as an
adjective or noun. Do not claim alignment, endorsement, affiliation, or
equivalence with any external program.

## Subject catalog

Java Programming, Computing Foundations, Chemistry, Calculus I,
Calculus II, Physics I, Physics II, Physics: Mechanics,
Physics: Electricity and Magnetism, European History,
United States History, World History, Macroeconomics, Microeconomics,
Statistics, United States Government, Comparative Government,
Psychology, Environmental Science, Human Geography,
English Language and Composition, English Literature and Composition,
Spanish Language and Culture, French Language and Culture,
German Language and Culture, Chinese Language and Culture, Art History,
Music Theory, Precalculus, Research Methods, Interdisciplinary Seminar,
Biology, Algebra I, Algebra II, Geometry, Integrated Math I,
Integrated Math II, Integrated Math III, Python Programming,
Web Development, Cybersecurity, Data Science.

## Frameworks as references

Official course frameworks may serve as reference documents for scope
and accuracy. Scope (the list of topics) guides coverage; the text of
any framework document is never copied. Citations name the subject and
unit only, for example "the official course framework for
Java Programming, Unit 1".

## Trademark notice

All trademarks belong to their respective owners. Brand names of
external testing programs are not used anywhere in this project, and
this project is not affiliated with, endorsed by, or sponsored by any
external testing program.

## Enforcement

`make check-trademarks` fails the build on any branded program name
outside this policy file and the subject catalog file, where names
appear solely to describe what the project does not use.
