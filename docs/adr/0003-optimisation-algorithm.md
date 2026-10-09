# 0003. Exact dynamic programming over candidate breaks

Status: Accepted (2026-10-09)

## Context

The PRD's best plan is "the set of leave days, within the balance, with the most total days inside breaks", and it
must be "the best possible for the inputs, proven by tests against a brute-force solver". A greedy choice (for
example, best days-off-per-leave first) is not optimal: leaves spent on a weak break can leave too few for a strong
one later, which is exactly the problem the PRD describes.

## Decision

Model the horizon as a sequence of days, each either a working day (a leave may go there) or a day off. The horizon
is the planning window plus the runs of days off just outside it at the year edges (ADR 0006).

In an optimal plan, every break is a maximal run of days off, so it starts right after an untaken working day (or at
the horizon start) and ends right before an untaken working day (or at the horizon end); every working day inside it
is a leave day. Leaves outside breaks are never placed (PRD). So a plan is a set of non-overlapping **candidate
intervals** `[i, j]` where

- day `i-1` is a working day or `i` is the horizon start, and day `j+1` is a working day or `j` is the horizon end;
- the interval contains `c >= 1` working days (its cost in leaves) and has length `j - i + 1 >= minBreakDays`;
- the next chosen interval starts at `j + 2` or later, so the working day `j + 1` stays untaken.

Solve with a suffix DP. `best(p, b)` is the best plan for the days from position `p` onwards with at most `b`
leaves:

```
best(p, b) = better of
  best(p + 1, b)                                        skip day p
  interval [p, j] + best(j + 2, b - cost(p, j))         for each candidate [p, j] with cost <= b
```

"Better" is the total order of ADR 0004 (days in breaks, then fewer leaves, then earliest leave dates). That order
is compatible with the DP: for a fixed first interval, the best completion is the best suffix plan, because sums
add and a shared prefix doesn't change a lexicographic comparison.

Size: a full year is about 380 positions, budgets 0 to 60, and at most about 90 candidate ends per start before the
cost exceeds 60, so about 2 million transitions. Plans are stored as shared immutable linked lists, so a transition
doesn't copy.

## Alternatives considered

- **Greedy by days off per leave**: simple, but provably wrong on small counter-examples; rejected.
- **Integer linear programming**: exact, but adds a solver dependency and is harder to explain and test.
- **Brute force over all leave subsets**: exact but exponential; kept as the test oracle (ADR 0005) only.

## Consequences

- Optimality follows from the interval argument above; the oracle checks it on 1,000+ random small calendars.
- Runtime is far below the PRD's 1 second; a performance test plans a full year with 60 leaves.
- Free long weekends, unused leaves and the notes are derived after planning from the final calendar.
