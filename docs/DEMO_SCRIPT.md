# Demo script (5 minutes)

For the recorded demo. Times are cumulative; the spoken lines are a guide, not a script to read word for word.
Numbers on screen come from the live app on the day; don't quote a number here that the screen doesn't show.

**Before recording**

- Open the live URL on a laptop (or `docker run --rm -p 8080:8080 leavewise` locally), at a width where the form
  and the calendar both fit. Have a phone with the same URL ready, or the browser's device mode at 360 px.
- Clear the browser's downloads. Have a calendar app open to import the `.ics` file.
- Prepare a short paste list with one deliberate bad line, for example two company holidays and `2027-02-30 Bad
  date` (use the plan year on the day).
- In a second tab: the repository on GitHub, at `README.md` (the "For judges" table) and the latest CI run.

## 0:00 to 0:40 The problem

- "Every year the holiday list comes out, and people sit with a calendar working out where to put their leave."
- "Holidays move each year, and with a limited balance the order matters: spend leaves on a weak break and you
  can't afford a strong one later."
- "Calendar apps show holidays. None of them tells you where your leaves should go. LeaveWise does, and the plan it
  gives is provably the best one for your inputs."

## 0:40 to 2:30 Live demo

1. **Defaults** (0:40): "One page, no login. Every input has a default, so I can plan by typing just my leave
   count." Point at the year, leave days, weekly offs and break style.
2. **Holidays** (1:00): untick one holiday. Add a company holiday by date and name. Open **Paste a list**, paste the
   prepared lines and tap **Add pasted lines**: the good lines load, and the bad one shows its line number and the
   reason. "Every input is checked in the browser and again on the server."
3. **Plan** (1:25): tap **Plan my leaves**. Read the summary line out loud: leaves used, days off, number of
   breaks.
4. **Breaks and calendar** (1:40): scroll the break list: dates, leave days, holidays inside, days off per leave.
   Then the calendar: "Colour and a letter on every day, so it works without colour too." Tap a day to show what it
   is. Press the arrow keys to move between days: "fully usable by keyboard."
5. **Change an input** (2:00): switch the break style to **Big trips**. Point at the "Inputs changed" note, then
   plan again: fewer, longer breaks.
6. **Calendar file** (2:15): tap **Download calendar file (.ics)** and import it: one all-day event per break,
   with the leave dates in the notes.
7. **Phone** (2:25): show the same page on the phone or at 360 px.

## 2:30 to 3:30 How AI built it

- "Claude Code built this under a written brief with four stops where I reviewed the work. The rules it worked
  under are in `CLAUDE.md`."
- Show `docs/ai/AI_WORKFLOW.md`: "Specialised subagents. A test designer wrote tests from the PRD before the code.
  An edge-case hunter attacked the app. Code, UI and security reviewers checked each part."
- Show `docs/ai/AI_FAILURE_LOG.md`: "Every mistake the AI made that a check caught is logged, with how it was
  caught and the rule added so it doesn't happen again." Pick one, for example entry 15: a plan request at New Year
  midnight returned an error, because the clock was read twice. The edge-case hunter found it, and the fix and
  the rule are in the entry.
- "Every commit links back to the AI session that made it, through a `Claude-Session` trailer."

## 3:30 to 4:15 Key technical decisions

- **Exact, not greedy:** "Greedy breaks fail on exactly the case the problem is about. The planner uses dynamic
  programming over candidate breaks, so the answer is optimal." (`docs/adr/0003-optimisation-algorithm.md`)
- **Same answer every time:** "Ties go to fewer leaves, then earlier dates."
- **One deployable, nothing stored:** "One Spring Boot jar serves the page and the API, in one Docker image. No
  database, no cookies, inputs never logged."
- **Security:** "A strict Content Security Policy, checked at build time and in a real browser, plus a rate limit
  and request size limit."

## 4:15 to 5:00 How we know it works

- "A brute-force solver tries every combination on small random calendars; the planner matches it on all 1,500.
  Mutation testing injects bugs into the planner: the tests catch 98% of them."
- "Every PRD rule and edge case maps to a test in `docs/QA.md`."
- Show the CI run: "One quality gate runs every check, the same locally and in CI. That includes a real browser
  that checks phone layout, the security policy, touch-target sizes and accessibility, and the dependency scan.
  The scan already caught vulnerable library versions, which are now patched."
- Close: "LeaveWise: the best plan for your leave, in under a minute, proven."
