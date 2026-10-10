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
| Real browser | Playwright Chromium against the jar, in the quality gate | At 360 and 1280 px, light mode (the only one since 2026-10-10), results and the holiday paste panel (phone: results screen, then the holidays sheet): no overflow, no CSP violations or console errors, 24 px touch targets, axe WCAG 2.2 AA with contrast, plan time | `frontend/scripts/browser-check.mjs` |
| Real devices | Manual (laptop, phone) | Real phone layout and touch, fonts, keyboard on a real browser | LAPTOP step, `docs/HUMAN_TODO.md` |
| Built page vs CSP | Node script in `npm run build` | No inline `<style>`, `style=` or script in `index.html` | `frontend/scripts/check-dist-csp.mjs` |
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
| Default year: next year from October | `OptionsApiTest#defaultYearFromOctober`, `#firstOfOctoberAlreadyDefaultsToNextYear` | passing |
| Free long weekends: 3+ days off needing no leave, shown, not counted | `FreeLongWeekendTest` (13 tests incl. `#listedButNotCounted`, `#notListedWhenPartOfABreak`, `#listedForEveryBreakStyle`), `PlannerProperties#freeLongWeekendsFollowTheRules` | passing |
| A break can't start right after a day off (maximal runs only) | `CandidateBreaksTest#noStartAfterADayOff`, `#everyBreakIsMaximalOnRandomLookingSequences`, `PlannerProperties#breaksAreMaximalRunsOfDaysOff` | passing |
| PRD example: Thursday holiday + Friday leave = 4-day break Thu to Sun | `PlannerRulesTest#prdExampleThursdayHolidayFridayLeave`, `BreakOptimizerTest#prdExampleHolidayThursdayLeaveFridayIsFourDays` | passing |
| Optimiser argument contract; days off per leave | `BreakOptimizerContractTest`, `LeaveBreakTest` | passing |

## Coverage map: edge cases (PRD list)

| Edge case | Tests | Status |
| --- | --- | --- |
| A holiday on a weekly off | `PlannerRulesTest#holidayOnWeeklyOffCountsOnce`, `PlanningCalendarTest#holidayOnWeeklyOffIsOneDayOff`, `PlanApiTest#aHolidayOnAWeeklyOffIsKindHoliday` | passing |
| Two holidays on the same date | `HolidayMergeTest` (4), `ReviewFindingsHolidaysTest#holidayDayMerge…`, `HolidaysApiTest#twoHolidaysOnTheSameDateAreReturnedOnceWithBothNames`, `PlanApiTest#duplicateHolidayDates`; UI rows merge by date (`holidayRows.ts`) | passing |
| A break that crosses into the next or previous year | `YearEdgeTest#breakExtendsIntoNextYear`, `#breakExtendsFromPreviousYear`, `#breakExtendsFromPreviousYearThroughWeeklyOffs`, `IcsWriterTest#dtendAfterABreakCrossingNewYearRollsIntoTheNextYear`, `dates.test#formats a range` | passing |
| A leap year (2028) | `LeapYearTest` (6), `PlanApiTest#leapYearHas366Days`, `HolidayPasteParserTest#the29thOfFebruaryIsRealInALeapYear…`, `EdgeCaseHunt.test` (366-day calendar with 29 Feb, keyboard at the year's edges) | passing |
| 0 leave days | `PlannerRulesTest#zeroLeavesGivesNoBreaksAndFreeWeekends`, `PlanApiTest#zeroLeavesNote`, `App.test#0 leave days: free long weekends only` | passing |
| More leave days than working days | `PlannerRulesTest#moreLeavesThanWorkingDaysUsesEveryWorkingDay`, `PlannerProperties#spareLeavesMeanEveryWorkingDayInTheWindowIsALeave`, `PlanApiTest#leftOverNote`, `EdgeCaseHunt.test` (summary, note and a break into the next year in the page) | passing |
| Only Sunday as a weekly off | `PlannerRulesTest#sundayOnlyWeek`, `PlanningCalendarTest#onlySundayAsWeeklyOff`, oracle (random weekly offs) | passing |
| No weekly off at all (PRD allows any days) | `PlannerRulesTest#noWeeklyOffs`, `PlanApiTest#emptyWeeklyOffsAccepted` | passing |
| Every holiday unticked | `PlannerRulesTest#noHolidays`, `PlanningCalendarTest#holidaysListIsEmptyWhenEveryHolidayIsUnticked`, `App.test#plans with the ticked holidays` (unticked one not sent) | passing |
| Current year, planning from today | `PlanningWindowTest` (11), `PlanApiTest#currentYearIsPlannedFromTomorrow`, `#noDaysLeftNote`, `NewYearMidnightApiTest` (a request across midnight on 31 Dec), `EdgeCaseHunt.test` (31 Dec in the page) | passing |
| A pasted list with bad lines | `HolidayPasteParserTest` (22), `HolidayParseApiTest` (8), `App.test#pasted lines load and bad lines show their line number and reason`, `EdgeCaseHuntApiTest` (no-break space, limit in characters) | passing |

## Coverage map: validation and errors (PRD table)

| Situation | Expected (PRD) | Tests | Status |
| --- | --- | --- | --- |
| Leave days not whole, below 0 or above 60 | "Enter a whole number from 0 to 60." | `PlanApiTest#leaveDaysValidation` (12.5, "12", -1, 61), `#leaveDaysBoundariesAreAccepted`, `App.test#leave days outside 0 to 60 or not whole` (61, -1, 12.5, abc; no request sent) | passing |
| More leave days than working days left | Plan uses every working day, note on leftovers | `PlanApiTest#leftOverNote`, planner rows above | passing |
| No working day left in the week | "Keep at least one working day in the week." | `PlanApiTest#allSevenWeeklyOffs`, `App.test#all seven weekly offs` | passing |
| Pasted line not a valid date in the year | Line number and reason; other lines load | `HolidayPasteParserTest`, `HolidayParseApiTest`, `App.test#pasted lines…` | passing |
| Same holiday date added twice | Kept once, both names shown | `HolidayMergeTest`, `HolidaysApiTest#twoHolidays…`, `PlanApiTest#duplicateHolidayDates` | passing |
| 0 leave days | Free long weekends only, note that no leave is used | `PlanApiTest#zeroLeavesNote`, `App.test#0 leave days…` | passing |
| No break possible | Reason plus a shorter-style suggestion | `PlannerRulesTest#noBreakPossible`, `#onlyOneNote…` (3), `PlanApiTest#noBreakPossibleNoteSuggests…`, `#noBreakPossibleNoteHasNoSuggestionForLongWeekends`; page: one "Try …" button per shorter style, one tap re-plans with it (`Redesign.test#no break possible`, 2) | passing |
| Server-side errors are problem+json and say what to do | docs/API.md errors table | `ErrorFormatTest` (4), `ReviewFindingsApiTest` (12), `App.test#a server error shows its message and keeps the inputs` | passing |

## Coverage map: results (Must items)

| Part | Tests | Status |
| --- | --- | --- |
| Summary: leaves used, total days off, breaks, unused leaves | `PlanApiTest#summary`, `App.test#plans with the ticked holidays…` ("2 leaves → 7 days off", "across 2 breaks"), `Redesign.test#shows the summary figures…` (leaves used, days off, breaks, days off per leave) | passing |
| Break list: dates, days off, leave dates, holidays, days off per leave | `PlanApiTest#breakFields`, `#daysOffPerLeaveIsRoundedToOneDecimal`, `LeaveBreakTest`, `App.test` (first break's five facts) | passing |
| Year calendar: 12 months, colour **and** label, tap a day to see what it is | `PlanApiTest#daysHasOneEntryPerDateOfTheYear`, `#daysCarryKindPlannedHolidayNamesAndBreakIndex`, `App.test` (month headings, day button's spoken label, tapped-day line in a live region, legend), axe | passing |
| Calendar file: one all-day event per break, notes list leave dates | `IcsWriterTest` (21), `IcsInjectionTest` (4), `IcsApiTest` (6), `App.test#downloads the .ics for the same inputs` | passing |

## Coverage map: redesign additions (2026-10-10, not PRD rules)

| Behaviour | Tests | Status |
| --- | --- | --- |
| Leave-day stepper: −/+ by one, 0 to 60, starts from 0 on text that isn't a whole number, clears the error; the field stays a text box named "Leave days" | `Redesign.test#leave-day stepper` (3) | passing |
| Phone screens (canvas): inputs with holidays row and plan bar, results with Edit inputs, figures, breaks, the one-month calendar with chips | `PhoneFlow.test` (4, matchMedia stubbed to a phone, axe on both screens) | passing |
| Results panel says what it will hold before the first plan | `Redesign.test#shows what the panel will hold…` (with axe) | passing |
| Stepper rule (whitespace, leading zeros, decimals, 20 digits, 0 and 60) | `validation.test#stepLeaveDays` (13) | passing |
| Days off per leave rounds half up like the server | `figures.test#daysOffPerLeave` (5, incl. 43 / 20 = 2.2) | passing |
| "Try …" waits for an in-flight plan; an invalid input keeps the break style | `Redesign.test#waits while a plan is on its way…` (deferred response) | passing |

## Coverage map: results (Nice to have, deferred until every Must is polished)

| Part | Tests | Status |
| --- | --- | --- |
| Free long weekends (beyond the 0-leave case) | `FreeLongWeekendTest` (planner); UI shows them only for 0 leaves | deferred |
| Other good breaks: top 10 not in the plan, by days off per leave | `OtherBreaksTest#topTenByDaysOffPerLeave` | deferred |
| Share link: inputs kept in the page link | `ShareLink.test#roundTripsInputs` | deferred |

## Coverage map: quality, privacy and security

| PRD item | Tests or check | Status |
| --- | --- | --- |
| Full-year plan in under 1 second | `PlannerPerformanceTest` (2), `PlanApiTest#fullYearPlanRespondsInUnderOneSecond`; measured 3 to 8 ms in the planner; `scripts/browser-check.mjs`: click to results shown in 101 to 146 ms in Chromium against the jar (two Phase 4 runs) | passing |
| Works from 360 px wide | Playwright Chromium screenshots at 360 and 1280 px (light and dark until 2026-10-10, light only since), with a horizontal-overflow check (cloud VM, Phase 3); `scripts/browser-check.mjs` in the quality gate (overflow, 24 px touch targets); real-device check is a LAPTOP/owner step | passing in Chromium (phone: LAPTOP) |
| Contrast, keyboard, colour never the only signal | axe in `App.test` (form and results, no contrast in jsdom); axe WCAG 2.2 AA with colour contrast in Chromium (light mode) via `scripts/browser-check.mjs` in the quality gate; `Phase4Review.test` (day text inside its spoken name); calendar days carry a text code and a spoken label; roving arrow-key focus | passing (a real phone: LAPTOP) |
| No login, database, tracking cookies; inputs not logged | `PrivacyTest#noResponseSetsACookie`, `#requestInputsNeverAppearInTheLog` (root DEBUG on) | passing |
| Server-side validation, size limit, rate limit, headers | `PlanApiTest`, `RequestSizeLimitTest`, `RateLimitTest`, `RateLimitFilterTest`, `PathBypassTest` (real server), `SecurityHeadersTest`, `SecurityReviewApiTest` (duplicate JSON keys), `EdgeCaseHunt.test` (browser refuses control characters, as the server does) | passing |
| The page itself obeys the CSP (`style-src 'self'`, `script-src 'self'`) | `frontend/scripts/check-dist-csp.mjs` inside `npm run build` (no inline `<style>`, `style=` or script; layer and slide-rule stylesheets linked); Playwright Chromium against the jar: 0 CSP violations (Phase 4) | passing |
| No secrets in the repo | secretlint in `scripts/quality-gate.sh` | passing |
| Holiday file per year with source noted | `BundledHolidayFilesTest`, `HolidayRepositoryTest` (13), `ReviewFindingsHolidaysTest#retrievedOnMustBeAnIsoDate` | passing (real files: LAPTOP) |
| The app reminds users to check their company's official list | `HolidaysApiTest#reminderPresent`, `App.test#shows the holiday source and the reminder` | passing |
| One Spring Boot app serves the page and API | `SpaForwardingTest` (2); Docker build in Phase 5 | partly (Docker pending) |

## Acceptance items (PRD, "How we will know it works")

| Item | How | Status |
| --- | --- | --- |
| Oracle match on 1,000+ random small calendars | `PlannerOracleProperties#matchesBruteForce`, 1,500 tries, 1,500 checks, all matching | passing |
| Automated tests cover every planning rule and edge case | The tables above | passing |
| 3 colleagues each get a plan in under a minute without help | Owner's usability check, recorded in `docs/HUMAN_TODO.md` with real results only | owner |
| The deployed URL works on a phone | Owner's check after deploy | owner |
