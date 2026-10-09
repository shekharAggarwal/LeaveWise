# edge-case-hunter memory

## 2026-10-09, Phase 2 planner attack (dev.leavewise.planner)

Dead ends (planner matched the oracle; don't repeat):
- Scratch jqwik fuzz, 3,000 tries (2,393 checked): year 2027, today in {2026-10-09, 2026-12-30, 2026-12-31,
  2027-12-25..30}, 0-6 weekly offs, 0-6 leaves, all styles, up to 40 holidays dense at both edges
  (offsets -14..14 and 350..380). Planner == DateOracle on leave dates and total days. Deleted (duplicate coverage).
- BreakOptimizer DP tie logic: exhaustive Python replica over every W/O sequence up to 14 days (budget 6,
  min 1/2/3/5) plus 90k random up to 40 days: the case "equal days, more leaves than current" never occurs at
  line 74, so the leave count in the comparison is dead. Mutants line 65 (+ -> -) and line 74 (> -> >=) are
  equivalent.
- PlanningCalendar MAX_EDGE_DAYS (366) mutants: unreachable via the API (adjacent-year days come only from bundled
  lists; custom holidays must be in the plan year). Not PRD behaviour; no test.

Added (kill mutants, pass): BreakOptimizerContractTest (minBreakDays 1, argument validation),
LeaveBreakTest (daysOffPerLeave division and rounding). PIT went 178/184 -> 180/184.

Open interpretation (not a test, PRD text allows it): planning next year on 30/31 Dec counts today and earlier days
in the leading edge, unlike ADR 0006's "days up to and including today are not counted" for the current year.

Ideas not yet tried: API layer (malformed JSON, huge bodies, unicode names, 100-holiday cap, paste parser line 51+),
.ics folding with long unicode holiday names.
