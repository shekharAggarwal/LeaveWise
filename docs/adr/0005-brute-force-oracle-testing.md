# 0005. A brute-force oracle and property tests prove optimality

Status: Accepted (2026-10-09)

## Context

The PRD's first acceptance item: "On 1,000+ random small calendars, the planner's result matches a brute-force
solver that tries every combination." Example-based tests alone can't show that a DP is optimal.

## Decision

- `BruteForcePlanner` (test sources only) tries every subset of working days of size up to the budget, scores each
  with a direct, literal implementation of the PRD rules (find runs of days off, keep runs with a leave day and the
  minimum length, sum their lengths), and keeps the best by the ADR 0004 order. It shares the day-kind model with
  the planner but none of its algorithm.
- A jqwik property generates at least 1,000 random small calendars (up to about 21 days, random weekly offs and
  holidays, budgets 0 to 8 including budgets above the number of working days, all three break styles, random
year-edge padding, and horizons with no leading edge as in current-year planning) and asserts the planner returns
  exactly the oracle's plan: same leave dates, same breaks, same total.
- Further properties: holiday order doesn't change the plan; no leave is placed outside a break; leaves used never
  exceed the budget; a full year with 60 leaves plans in under 1 second.
- Mutation testing (PIT) on the planner package checks that the tests would notice a broken planner; target 80% or
  more, and the real score is reported either way.

## Consequences

- The oracle must be obviously correct, so it's written for clarity, not speed, and reviewed against the PRD text.
- jqwik shrinks failing calendars to a minimal counter-example, which goes into a named regression test and, when
  it shows an AI mistake, into `docs/ai/AI_FAILURE_LOG.md`.
- The property runs in the normal test suite, so CI enforces it on every push.
