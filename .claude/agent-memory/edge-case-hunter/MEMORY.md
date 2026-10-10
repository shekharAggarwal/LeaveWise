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

(API-layer ideas from Phase 2 were tried in Phase 3, below.)

## 2026-10-10, Phase 3 whole-app attack (api, web, holidays, ics, frontend)

Found (failing tests kept, 5 backend + 1 frontend):
- NewYearMidnightApiTest: PlanController reads the clock twice (PlanYears.now, then Planner) -> a request that
  straddles midnight IST on 31 Dec is a 500 ("cannot plan a past year"). Needs its own context with a stepping clock.
- EdgeCaseHuntApiTest: paste "YYYY-MM-DD<U+00A0>Name" gets "Start the line with a date" (Java \s is ASCII only;
  strip() keeps NBSP; NBSP-only line is also not blank). PlanInputs example year uses LocalDate.now() (system
  clock) when year is invalid. leaveDays 60.0 / 6e1 rejected (isIntegralNumber). Paste 8,000 limit counts UTF-16
  units, not characters.
- EdgeCaseHunt.test.tsx: HolidayEditor manual add doesn't check control characters (tab via paste), server then
  fails every plan with a page-level banner.

Dead ends (behaved per contract; don't repeat):
- ics: Accept text/calendar + invalid body -> 400 problem+json; break crossing 31 Dec (DTEND next-year, UID ok);
  NO_DAYS_LEFT ics = headers only. Lone surrogate in a name: JSON escapes it, ics writes "?" (no 500).
- Body "[]" / "null" -> 400 invalid-input "Send the plan inputs as a JSON object."; 5000-deep nesting and
  5000-digit number -> 400 unreadable. Plan next year on 31 Dec, 29 Feb 2028 holiday with clock in 2027: fine.
- Frontend: formatRange across years (tested already), YearCalendar Home/End/ArrowLeft 1 Jan/ArrowRight 31 Dec,
  leap-year grid, LEAVES_LEFT_OVER summary, NO_DAYS_LEFT results: all fine (kept as UI coverage tests).

Not tried yet: 100 holidays x 80-emoji names (>32 KB -> 413 for a field-valid request; doc says 413 for >32 KB, so
contract conflict, not a defect); \u0085/\u000C in pasted text splitting lines; X-Forwarded-For spoofing.
