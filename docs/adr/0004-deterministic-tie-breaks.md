# 0004. A total order on plans, so the same inputs give the same plan

Status: Accepted (2026-10-09)

## Context

Many plans can reach the same number of days off. The PRD fixes the order: "the plan using fewer leave days wins.
If still tied, compare both plans' leave dates in order; the plan with the first earlier date wins, so the same
inputs always give the same plan."

## Decision

Plans are compared by:

1. more total days inside breaks;
2. then fewer leave days;
3. then the leave dates sorted ascending, compared element by element: at the first position where they differ,
   the plan with the earlier date wins. (After step 2 both lists have the same length.)

This is a total order on distinct leave sets, so there is exactly one best plan. Inputs are normalised before
planning (weekly offs as a set; holidays sorted by date and merged by date), so the order in which the user lists
holidays can't change the result.

## Consequences

- Determinism is testable: the same inputs with holidays shuffled must give an identical plan (property test).
- The oracle (ADR 0005) applies the same comparator, so ties are checked too, not just the objective value.
- The comparator is one shared class used by the planner and the oracle; it gets its own unit tests so a bug in it
  can't hide in both.
