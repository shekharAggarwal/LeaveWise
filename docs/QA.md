# QA: test strategy and PRD coverage map

The PRD's bar: "the planner is proven correct, every edge case has a test, and a new user gets a plan without help."
This file says how each part is tested and maps every PRD rule and edge case to its tests. The **Status** column
records only what has actually happened: `planned` (named, not written), `failing` (written first, not yet green)
or `passing` (green in the quality gate at the commit noted in `docs/ai/PROGRESS.md`).

## Layers

| Layer | Tool | What it proves | Where |
| --- | --- | --- | --- |
| Planner unit tests | JUnit 5 | Each PRD planning rule on a small hand-made calendar, written from the PRD before the code | `backend/src/test/java/dev/leavewise/planner` |
| Oracle property test | jqwik + `BruteForcePlanner` | The planner equals brute force on 1,000+ random small calendars, ties included (ADR 0005) | same |
| Other properties | jqwik | Holiday order doesn't matter; no leave outside a break; budget respected; result is stable | same |
| Performance | JUnit 5 | Full year, 60 leaves, under 1 second | same |
| Mutation testing | PIT | The planner tests notice injected bugs; target 80% or more, real score reported | `./mvnw test-compile pitest:mutationCoverage` |
| Holidays and parsing | JUnit 5 | Bundled files are valid and officially sourced; paste errors carry line and reason | `backend/src/test/java/dev/leavewise/holidays` |
| ICS | JUnit 5 | RFC 5545 structure: all-day dates, exclusive `DTEND`, CRLF, folding, escaping | `backend/src/test/java/dev/leavewise/ics` |
| API | Spring MockMvc | Contract in `docs/API.md`, PRD error messages word for word, size and rate limits, headers | `backend/src/test/java/dev/leavewise/api` |
| UI components and flow | Vitest + React Testing Library | The journey: defaults, edit, plan, results, download; empty, loading and error states | `frontend/src/**/*.test.tsx` |
| Accessibility | axe-core in Vitest | No axe violations on every screen state (contrast checked in a browser, not jsdom) | `frontend/src/test/axe.ts` |
| Real browser | Playwright Chromium (cloud) / manual (laptop) | Phone (360 px) and desktop layouts, keyboard focus, light and dark | LAPTOP step, `docs/HUMAN_TODO.md` |
| Static checks | ESLint, tsc, Spotless, `astryx doctor`, secret scan | Code and design-system rules, no secrets | `scripts/quality-gate.sh` |

## AI-assisted QA

- **test-designer** (Sonnet) writes the failing tests for a feature from the PRD text before the code exists.
- **edge-case-hunter** (Opus) attacks the planner after it passes, adding only failing tests; each real find becomes
  a named regression test and an entry in `docs/ai/AI_FAILURE_LOG.md`.
- **code-reviewer**, **security-reviewer** and **ui-reviewer** review each finished unit against the PRD,
  `CLAUDE.md` and the Astryx rules. Their findings and outcomes go in `docs/ai/AI_WORKFLOW.md`.
- AI output is never trusted on its own: every AI-written test must fail before the change and pass after it, the
  oracle is checked against the PRD text by a human, and the owner reviews at each STOP.

## Coverage map: planning rules

`passing` = green in `scripts/quality-gate.sh` at commit noted in PROGRESS.md (Phase 2). PIT on the planner package:
180 of 184 mutants killed (98%); the 4 survivors are equivalent or a safety bound (see `docs/ai/PROGRESS.md`).

| PRD rule | Tests | Status |
| --- | --- | --- |
| Working day: not a weekly off and not a holiday | `PlanningCalendarTest#workingDayIsNeitherWeeklyOffNorHoliday`, `#weekdayHolidayAddsExactlyOneDayOff` | passing |
| Day off: weekly off, holiday or leave; holiday on a weekly off counts once | `PlanningCalendarTest#holidayOnWeeklyOffIsOneDayOff`, `PlannerRulesTest#holidayOnWeeklyOffCountsOnce` | passing |
| Leave day always on a working day | `PlannerRulesTest#leavesOnlyOnWorkingDays`, `PlannerProperties#leavesAreWorkingDays` | passing |
| Break: days off in a row with ≥1 leave and ≥ style length (3, 5, 9) | `PlannerRulesTest#breakNeedsALeaveDay`, `#breakMeetsMinimumLengthLongWeekends`, `#…ShortTrips`, `#…BigTrips`, `BreakOptimizerTest#runsExactlyAsLongAsTheMinimumAreBreaks`, `#runsOneShorterThanTheMinimumAreNotBreaks`, `PlannerProperties#everyBreakIsLongEnoughAndHasALeave` | passing |
| Best plan: most total days inside breaks within the balance | `PlannerOracleProperties#matchesBruteForce` (1,500 tries), `PlannerProperties#matchesBruteForceOnShortWindows`, `#matchesBruteForceForAFullYearWithUpToTwoLeaves`, `PlannerRulesTest#bestPlanMaximisesTotalDaysAcrossTheYear`, `#leaveBalanceIsNeverExceeded` | passing |
| A leave outside a break adds nothing, so none is placed | `PlannerProperties#noLeaveOutsideABreak`, `BreakOptimizerTest#leaveNeverPlacedOutsideABreak`, `PlannerRulesTest#unusedLeavesWhenNothingGrows` | passing |
| Ties: fewer leaves wins | `SelectionOrderTest#equalDaysFewerLeavesWins`, oracle property (compares leave dates, not just totals) | passing |
| Ties: then the first earlier leave date wins | `SelectionOrderTest#equalDaysAndLeavesFirstEarlierPositionWins`, `#agreesWithTheOraclesTieRule`, `BreakOptimizerTest#equalDaysChooseTheEarliestLeaveDate`, `PlannerRulesTest#earliestLeaveWinsAmongEqualPlans`, oracle | passing |
| Same inputs always give the same plan | `PlannerProperties#holidayOrderDoesNotMatter`, `#repeatedRunsAreIdentical`, `PlannerRulesTest#sameInputsAlwaysGiveTheSamePlan` | passing |
| Year edges: leaves stay in the year; breaks count edge weekly offs and known holidays | `YearEdgeTest` (9 tests incl. `#breakExtendsIntoNextYear`, `#breakExtendsFromPreviousYear`, `#noLeaveOutsideYear`, `#unknownAdjacentYearUsesWeeklyOffsOnly`, `#edgeCasesAgreeWithTheBruteForceOracle`), `PlanningCalendarTest#leadingEdge…`, `#trailingEdge…` | passing |
| Current year: only days after today are planned | `PlanningWindowTest#currentYearStartsTomorrow`, `#todayIsNotCountedEvenIfItIsADayOff`, `#leavesNeverFallOnOrBeforeToday`, `#todayIsReadInTheClocksZone` | passing |
| Default year: next year from October | `ApiOptionsTest#defaultYearFromOctober` | planned (API) |
| Free long weekends: 3+ days off needing no leave, shown, not counted | `FreeLongWeekendTest` (13 tests incl. `#listedButNotCounted`, `#notListedWhenPartOfABreak`, `#listedForEveryBreakStyle`), `PlannerProperties#freeLongWeekendsFollowTheRules` | passing |
| A break can't start right after a day off (maximal runs only) | `CandidateBreaksTest#noStartAfterADayOff`, `#everyBreakIsMaximalOnRandomLookingSequences`, `PlannerProperties#breaksAreMaximalRunsOfDaysOff` | passing |
| PRD example: Thursday holiday + Friday leave = 4-day break Thu to Sun | `PlannerRulesTest#prdExampleThursdayHolidayFridayLeave`, `BreakOptimizerTest#prdExampleHolidayThursdayLeaveFridayIsFourDays` | passing |
| Optimiser argument contract; days off per leave | `BreakOptimizerContractTest`, `LeaveBreakTest` | passing |

## Coverage map: edge cases (PRD list)

| Edge case | Tests | Status |
| --- | --- | --- |
| A holiday on a weekly off | `PlannerRulesTest#holidayOnWeeklyOffCountsOnce`, `PlanningCalendarTest#holidayOnWeeklyOffIsOneDayOff` | passing |
| Two holidays on the same date | `HolidayMergeTest` (4 tests incl. `#sameDateKeptOnceWithBothNames`); API: `PlanApiTest#duplicateHolidayDates` | planner passing; API planned |
| A break that crosses into the next or previous year | `YearEdgeTest#breakExtendsIntoNextYear`, `#breakExtendsFromPreviousYear`, `#breakExtendsFromPreviousYearThroughWeeklyOffs` | passing |
| A leap year (2028) | `LeapYearTest` (6 tests incl. `#plans366Days`, `#feb29IsAWorkingDayCandidate`, `#feb29MatchesTheBruteForceOracle`) | passing |
| 0 leave days | `PlannerRulesTest#zeroLeavesGivesNoBreaksAndFreeWeekends`; API: `PlanApiTest#zeroLeavesNote` | planner passing; API planned |
| More leave days than working days | `PlannerRulesTest#moreLeavesThanWorkingDaysUsesEveryWorkingDay`, `#leftOverNoteCountsTheLeavesNotUsed`, `PlannerProperties#spareLeavesMeanEveryWorkingDayInTheWindowIsALeave`; API: `PlanApiTest#leftOverNote` | planner passing; API planned |
| Only Sunday as a weekly off | `PlannerRulesTest#sundayOnlyWeek`, `PlanningCalendarTest#onlySundayAsWeeklyOff`, oracle (random weekly offs) | passing |
| No weekly off at all (PRD allows any days) | `PlannerRulesTest#noWeeklyOffs`, `PlanningCalendarTest#noWeeklyOffsAndNoHolidaysMeansNoEdges`; API: `PlanApiTest#emptyWeeklyOffsAccepted` | planner passing; API planned |
| Every holiday unticked | `PlannerRulesTest#noHolidays`, `PlanningCalendarTest#holidaysListIsEmptyWhenEveryHolidayIsUnticked`; UI: `PlannerForm.test#untickAllHolidays` | planner passing; UI planned |
| Current year, planning from today | `PlanningWindowTest` (11 tests incl. `#currentYearStartsTomorrow`, `#planOnThirtyFirstDecemberHasNoDaysLeft`, `#windowShorterThanMinimumBreakGivesNoBreakPossible`) | passing |
| A pasted list with bad lines | `HolidayPasteParserTest#reportsLineAndReasonAndLoadsTheRest`, `#over50Lines`, `HolidayPaste.test` | planned (Phase 3) |

## Coverage map: validation and errors (PRD table)

| Situation | Expected (PRD) | Tests | Status |
| --- | --- | --- | --- |
| Leave days not whole, below 0 or above 60 | "Enter a whole number from 0 to 60." | `PlanApiTest#leaveDaysValidation` (12.5, "12", -1, 61), `LeaveDaysField.test` | planned |
| More leave days than working days left | Plan uses every working day, note on leftovers | `PlanApiTest#leftOverNote` | planned |
| No working day left in the week | "Keep at least one working day in the week." | `PlanApiTest#allSevenWeeklyOffs`, `WeeklyOffsField.test` | planned |
| Pasted line not a valid date in the year | Line number and reason; other lines load | `HolidayPasteParserTest`, `HolidayParseApiTest` | planned |
| Same holiday date added twice | Kept once, both names shown | `HolidayMergeTest`, `BreakList.test#showsBothNames` | planned |
| 0 leave days | Free long weekends only, note that no leave is used | `PlanApiTest#zeroLeavesNote`, `Results.test#zeroLeaves` | planned |
| No break possible | Reason plus a shorter-style suggestion | `PlannerRulesTest#noBreakPossible`, `#onlyOneNote…` (3 tests), `PlannerProperties#notesFollowTheRules`; API: `PlanApiTest#noBreakPossibleNote` | planner passing; API planned |

## Coverage map: results (Must items)

| Part | Tests | Status |
| --- | --- | --- |
| Summary: leaves used, total days off, breaks, unused leaves | `PlanApiTest#summary`, `Summary.test` | planned |
| Break list: dates, days off, leave dates, holidays, days off per leave | `PlanApiTest#breakFields`, `BreakList.test` | planned |
| Year calendar: 12 months, colour **and** label, tap a day to see what it is | `YearCalendar.test#labelsEveryKind`, `#tapShowsDayDetails`, axe | planned |
| Calendar file: one all-day event per break, notes list leave dates | `IcsWriterTest`, `IcsApiTest`, `DownloadButton.test` | planned |

## Coverage map: results (Nice to have, deferred until every Must is polished)

| Part | Tests | Status |
| --- | --- | --- |
| Free long weekends (beyond the 0-leave case) | `FreeLongWeekendTest` (planner), `FreeLongWeekends.test` (UI) | deferred |
| Other good breaks: top 10 not in the plan, by days off per leave | `OtherBreaksTest#topTenByDaysOffPerLeave` | deferred |
| Share link: inputs kept in the page link | `ShareLink.test#roundTripsInputs` | deferred |

## Coverage map: quality, privacy and security

| PRD item | Tests or check | Status |
| --- | --- | --- |
| Full-year plan in under 1 second | `PlannerPerformanceTest#fullYearSixtyLeavesUnderOneSecond`, `#bigTripsWithMaximumLeavesAlsoUnderOneSecond`; API: `PlanApiTest` timing | planner passing; API planned |
| Works from 360 px wide | Playwright screenshots at 360 and 1280 px (laptop or cloud Chromium) | planned |
| Contrast, keyboard, colour never the only signal | axe in Vitest, keyboard flow test, `YearCalendar.test#labelsEveryKind` | planned |
| No login, database, tracking cookies; inputs not logged | `PrivacyTest#noSetCookie`, `#requestBodyNotLogged` (captured log output) | planned |
| Server-side validation, size limit, rate limit, headers | `PlanApiTest`, `RequestSizeLimitTest`, `RateLimitTest`, `SecurityHeadersTest` | planned |
| No secrets in the repo | gitleaks-style scan in `scripts/quality-gate.sh` and CI | planned |
| Holiday file per year with source noted | `BundledHolidayFilesTest#everyFileHasOfficialSource`, `#noTestDataInMain` | planned |
| The app reminds users to check their company's official list | `HolidaysApiTest#reminderPresent`, `HolidayList.test#showsSourceAndReminder` | planned |
| One Spring Boot app serves the page and API | Docker build in CI, `SpaForwardingTest` | planned |

## Acceptance items (PRD, "How we will know it works")

| Item | How | Status |
| --- | --- | --- |
| Oracle match on 1,000+ random small calendars | `PlannerOracleProperties#matchesBruteForce`, 1,500 tries, 1,500 checks, all matching | passing |
| Automated tests cover every planning rule and edge case | The tables above, all `passing` | planned |
| 3 colleagues each get a plan in under a minute without help | Owner's usability check, recorded in `docs/HUMAN_TODO.md` with real results only | owner |
| The deployed URL works on a phone | Owner's check after deploy | owner |
