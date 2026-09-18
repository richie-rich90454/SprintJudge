# Scoring

The SprintJudge Score (SJS) is multi-dimensional, explainable, and
accuracy-first.

## Weights

Accuracy 50, response time 20, consistency 10, improvement 15,
mastery 5.

## Difficulty weighting

Correct answers earn `DOK weight x difficulty multiplier x format
multiplier`. DOK weights: 1 to 1.0, 2 to 1.5, 3 to 2.5, 4 to 4.0.
Difficulty multiplier `1 + (0.5 - p)` clamped to 0.5-1.5. Format
multipliers: choice 1.0, multi-select and numeric 1.2, ordering 1.3,
code 1.5, free-response 2.0.

## Response-time normalization

Credit is linear between the calibrated 25th and 75th percentiles per
question. Careful students lose little; fluent students gain fairly.

## Speed-accuracy signal

Fast plus accurate reads "You know this cold." Fast plus inaccurate
reads "You are fast but guessing, try slowing down." Slow plus
accurate reads "You know it, fluency needs work." Slow plus
inaccurate reads "This topic needs reteaching, try practice mode."

## Leaderboard

Rank, delta, top three, distribution histogram, personal best, trend,
and tier (Gold, Silver, Bronze, Rising, Practicing). The lower half
sees its own rank privately and is never named publicly.

## Implementation

Pure functions in `com.sprintjudge.scoring`. See ADR 0003. Every
branch tested. Breakdown endpoints expose per-student transparency.
