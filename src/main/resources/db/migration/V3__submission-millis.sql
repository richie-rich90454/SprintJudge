-- Older builds stored epoch seconds. Convert them once so ordering by
-- submitted_at is monotonic at millisecond precision.
UPDATE submissions
SET submitted_at = submitted_at * 1000
WHERE submitted_at > 0 AND submitted_at < 100000000000;
