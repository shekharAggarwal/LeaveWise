<!-- Copied word for word from the owner's Claude Docs page "LeaveWise PRD" (revision 13, exported 2026-10-09). The build-plan diagram embedded in that page is transcribed as the table under "Milestones and open questions". Do not edit: changes go through the owner. -->

# LeaveWise PRD

Oct 9, 2026 · @Shekhar Aggarwal

## Summary

LeaveWise tells you which days to take off so your leave days become the longest possible breaks around weekends and holidays. Enter your leave balance and holiday list, and get a plan for the whole year in one click.

It is a solo entry for the FiftyFive Build With AI Hackathon 2026, due Oct 30, 2026. The MVP is one page, with no login and nothing stored.

## Problem

Picking leave days by hand is slow, and people miss the best combinations. Each year, employees sit with a calendar and the holiday list and try to work out which leave days join holidays and weekends into long breaks.

- Holidays fall on different days every year, so last year's plan doesn't carry over.
- With a limited leave balance, order matters: leaves spent on a weak break can leave too few for a strong one later.
- Calendar apps show holidays but never say where to put your leaves.

## Users and goals

LeaveWise is for salaried employees in India planning their year's leave, usually when the new holiday list comes out. HR teams sharing a company holiday list are a possible later audience, not part of the MVP.

Goals for the MVP:

- A good plan in under a minute, with no sign-up.
- The plan is the best possible for the inputs, proven by tests against a brute-force solver.
- Works well on a phone.

## User journey

The whole journey happens on one page and takes under a minute.

1. Open LeaveWise. No login.
2. Pick the year and enter your leave days.
3. Check the holiday list: untick holidays that don't apply and add your company's extra ones.
4. Set your weekly offs and break style.
5. Tap **Plan my leaves**.
6. Read the summary, the list of breaks and the year calendar.
7. Download the plan as a calendar file.

Changing any input and tapping **Plan my leaves** again gives a fresh plan.

## Inputs

Every input has a default, so a user can plan by entering just their leave count.

| Input | Default | Rules |
| --- | --- | --- |
| Year | Current year; next year from October onward | Current year or next year. For the current year, only days after today are planned. |
| Leave days | 12 | Whole number from 0 to 60. |
| Weekly offs | Saturday, Sunday | Any days of the week; at least one working day must remain. |
| Break style | Long weekends | Long weekends (3+ days), Short trips (5+ days) or Big trips (9+ days). Sets the shortest break the planner will build. |
| Holidays | India public holiday list for that year | Untick any; add a date with a name; or paste a list, one `YYYY-MM-DD Name` per line, up to 50 lines. |

## Planning rules

The planner picks the leave days that give the most total days off inside breaks, without going over the leave balance. These rules are exact, so the code and the tests can follow them word for word.

- **Working day:** any day that is not a weekly off and not a holiday.
- **Day off:** a weekly off, a holiday or a leave day. A holiday on a weekly off counts once.
- **Leave day:** always placed on a working day.
- **Break:** days off in a row that include at least one leave day and are at least as long as the break style (3, 5 or 9 days).
- **Best plan:** the set of leave days, within the balance, with the most total days inside breaks. A leave day outside a break adds nothing, so the planner never places one.
- **Ties:** the plan using fewer leave days wins. If still tied, compare both plans' leave dates in order; the plan with the first earlier date wins, so the same inputs always give the same plan.
- **Year edges:** leave days stay inside the chosen year, but a break also counts the weekly offs and known holidays just before 1 January and just after 31 December.
- **Free long weekends:** 3 or more days off in a row that need no leave. They are shown, but don't count toward the plan.

Example: a holiday on Thursday plus 1 leave day on Friday makes a 4-day break, Thursday to Sunday.

## Results

The results page answers one question first: how many days off your leaves buy.

| Part | What it shows | Priority |
| --- | --- | --- |
| Summary | Leaves used, total days off and number of breaks, plus any unused leaves. Example: “12 leaves → 41 days off across 9 breaks”. | Must |
| Break list | Each break's dates, total days off, leave dates, holidays inside and days off per leave. | Must |
| Year calendar | All 12 months, with weekly offs, holidays, leave days and breaks marked by colour and a label, never colour alone. Tap a day to see what it is. | Must |
| Calendar file | An .ics download with one all-day event per break; each event's notes list its leave dates. | Must |
| Free long weekends | Runs of 3+ days off that need no leave. | Nice to have |
| Other good breaks | Top 10 breaks not in the plan, ranked by days off per leave. | Nice to have |
| Share link | The inputs kept in the page link, so a plan can be shared. | Nice to have |

## Validation and errors

Every input is checked on the server as well as in the browser, and every error says what to do next.

| Situation | What the user sees |
| --- | --- |
| Leave days not a whole number, below 0 or above 60 | “Enter a whole number from 0 to 60.” |
| More leave days than working days left | A plan using every working day, with a note on how many leaves were left over. |
| No working day left in the week | “Keep at least one working day in the week.” |
| A pasted holiday line that isn't a valid date in the chosen year | The line number and the reason; all other lines still load. |
| The same holiday date added twice | Kept once, showing both names. |
| 0 leave days | Free long weekends only, with a note that no leave is used. |
| No break possible with these inputs | The reason, plus a suggestion to try a shorter break style. |

## Quality, privacy and security

LeaveWise stores nothing, which keeps privacy simple. The rest of the quality bar:

- **Speed:** a full-year plan returns in under 1 second.
- **Phone first:** works from 360 px wide, and on desktop.
- **Accessibility:** readable contrast, usable by keyboard, colour never the only signal.
- **Privacy:** no login, no database, no tracking cookies, and inputs are not logged.
- **Security:** server-side validation, a request size limit, a rate limit per IP, security headers and no secrets in the repo.
- **Same answer every time:** the same inputs always give the same plan.
- **Holiday data:** one bundled JSON file per year with its source noted; the app reminds users to check their company's official list.
- **Deployment:** one Spring Boot app serves both the React page and the API at a public URL for the judges.

## Out of scope for the MVP

These are left out on purpose, to keep the build to three weeks.

- Login, accounts and saved plans
- Team views and leave clash checks
- HR system or calendar account integration (the .ics file covers adding to a calendar)
- Half days and leave types (casual, sick, earned)
- Alternate-Saturday patterns (workaround: add those Saturdays as holidays)
- State-wise holiday lists (workaround: add them as custom holidays)
- Countries other than India

## How we will know it works

The MVP is done when the planner is proven correct, every edge case has a test, and a new user gets a plan without help.

- [ ] On 1,000+ random small calendars, the planner's result matches a brute-force solver that tries every combination.
- [ ] Automated tests cover every rule in Planning rules and every edge case below.
- [ ] 3 colleagues each get a plan in under a minute without help.
- [ ] The deployed URL works on a phone.

Edge cases to test:

- A holiday on a weekly off
- Two holidays on the same date
- A break that crosses into the next or previous year
- A leap year (2028)
- 0 leave days
- More leave days than working days
- Only Sunday as a weekly off
- Every holiday unticked
- Current year, planning from today
- A pasted list with bad lines

## Milestones and open questions

*Build plan · 3 weeks, 3 checks: LeaveWise is submitted on 29 Oct, a day before the deadline.*

| Week | Dates | Work | Check at the end |
| --- | --- | --- | --- |
| Week 1 | 9–15 Oct | Planning logic + tests; checked against brute force | Logic proven: 15 Oct · matches brute force |
| Week 2 | 16–22 Oct | Page and calendar view; deployed to a public URL | Live URL: 22 Oct · works on phone |
| Week 3 | 23–30 Oct | Export, polish, AI review; docs and 5-min demo | Submitted: 29 Oct · ZIP + demo (deadline 30 Oct, end of day) |

Each week ends with a check: the logic matches brute force, then the live URL works on a phone, then the ZIP and demo go in a day early.

**Open questions**

- [ ] Which holiday list to preload: the central government list or a typical private-company list?
- [ ] Should the planner spread breaks across the year, or only maximise days off?
- [ ] Is Long weekends the right default break style?
