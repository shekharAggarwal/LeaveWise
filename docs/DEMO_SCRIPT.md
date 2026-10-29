# Demo script (5 minutes)

For the recorded demo. The submission form asks for at most 5 minutes with both a product walkthrough and a code
walkthrough. Times are cumulative; the spoken lines are a guide, not a script to read word for word. Numbers on
screen come from the app on the day; don't quote a number here that the screen doesn't show.

## The recorded video

The submitted video (4:43, 1920x1080) was scripted and recorded by Claude Code rather than read live, and it says
so at the start. The narration is in `scripts/demo-video/narration.mjs`, spoken by macOS text-to-speech (voice
Aman). Playwright drove the app (`docker compose up`) and the GitHub pages of commit `1920e04`, and ffmpeg put it
together. Every number it says comes from `/api/plan` for the inputs on screen. It follows the order below, with
shorter wording. To make it again (macOS, ffmpeg, the app on port 8080), run `node tts.mjs`, `node record.mjs` and
`node encode.mjs` in `scripts/demo-video/`.

**Before recording**

- Start the app: `docker compose up`, then open http://localhost:8080 (or the live URL once deployed), at a width
  where the form and the calendar both fit. Have the browser's device mode ready at 360 px, or a phone on the same
  URL.
- 2027 is the default year from October on and has no bundled list yet, so the demo pastes one. Copy the block
  below: FiftyFive's 11 fixed 2026 holidays moved to their 2027 dates, plus one deliberate bad line (30 February).
  The full list with the 13 floaters, and where each date comes from, is under "2027 holiday dates" at the end.

  ```
  2027-01-01 New Year's Day
  2027-01-26 Republic Day
  2027-03-10 Eid ul Fitr
  2027-03-23 Holi
  2027-03-27 Rangpanchmi
  2027-08-15 Independence Day
  2027-10-02 Mahatama Gandhi Jayanti
  2027-10-09 Dusshera
  2027-10-29 Diwali
  2027-11-04 Chhath Pooja
  2027-12-25 Christmas Day
  2027-02-30 Bad date
  ```
- Clear the browser's downloads. Have a calendar app open to import the `.ics` file.
- Open the repo in your editor (or on GitHub) with these tabs ready, in this order:
  1. `README.md`, the "Architecture" section
  2. `backend/src/main/java/dev/leavewise/planner/BreakOptimizer.java`, scrolled to `solve`
  3. `backend/src/test/java/dev/leavewise/planner/PlannerOracleProperties.java`, at `matchesBruteForce`
  4. `backend/src/main/java/dev/leavewise/api/PlanController.java`
  5. `CLAUDE.md`, `docs/ai/AI_WORKFLOW.md` and `docs/ai/AI_FAILURE_LOG.md` (entry 15)
  6. The latest green CI run on GitHub Actions
- Do one dry run with a timer. The code part is the easiest to overrun.

## 0:00 to 0:30 The problem

- "Every year the holiday list comes out, and people sit with a calendar working out where to put their leave."
- "Holidays move each year, and with a limited balance the order matters: spend leaves on a weak break and you
  can't afford a strong one later. Calendar apps show holidays, but none of them tells you where your leaves
  should go. LeaveWise does, and the plan is provably the best one for your inputs."

## 0:30 to 2:00 Product walkthrough

1. **Defaults** (0:30): "One page, no login. Every input has a default, so I can plan by typing just my leave
   count." Point at the year, leave days, weekly offs and break style.
2. **Holidays** (0:40): switch the year to 2026 for a moment: "This year comes prefilled with our company list,
   floating holidays unticked so you pick your own." Switch back to 2027: no list is bundled yet, so open
   **Paste a list**, paste the prepared lines and tap **Load holidays**. The 11 good lines load; line 12 shows its
   line number and the reason. "Every input is checked in the browser and again on the server."
3. **Plan** (1:00): tap **Plan my leaves**. Read the summary out loud: leaves used, days off, number of breaks.
4. **Breaks and calendar** (1:10): scroll the break list: dates, leave days, holidays inside, days off per leave.
   Then the calendar: "Colour and a letter on every day, so it works without colour too." Tap a day to show what
   it is.
5. **Change an input** (1:30): switch the break style to 9 or more days. Point at the "Inputs changed" note, then
   plan again: fewer, longer breaks.
6. **Calendar file** (1:40): tap **Add to calendar (.ics)** and import it: one all-day event per break, with the
   leave dates in the notes.
7. **Phone** (1:50): show the same page at 360 px.

## 2:00 to 3:30 Code walkthrough

1. **Structure** (2:00, `README.md` "Architecture"): "One Spring Boot app serves the React page and the API, in
   one Docker image. The backend has five packages. The one that matters is `planner`: plain Java, no Spring, no
   I/O, so it can be tested on its own."
2. **The algorithm** (2:15, `BreakOptimizer.solve`): scroll slowly and point at three things.
   - The `best[p][b]` table: "Dynamic programming. For each day and each number of leaves left, the best plan
     from that day on."
   - `canStart` and `canEnd`: "A break in the best plan always starts and ends next to a working day you don't
     take, so only those runs are candidates. That's what keeps it fast and exact."
   - The `better` line and its comment: "Ties go to fewer leaves, then earlier dates, so the same inputs always
     give the same plan."
   - "Greedy picks the cheapest-looking break first and can waste leaves. This can't: it compares every
     combination of candidate breaks."
3. **The proof** (2:50, `PlannerOracleProperties.matchesBruteForce`): "This test generates random calendars,
   solves each one with a brute-force solver that tries every combination of leave days, and checks the planner
   gets the same answer. 1,500 calendars, all equal. The coverage checks stop it passing on easy cases only."
4. **The API edge** (3:10, `PlanController`): "The controller validates every input again on the server, with the
   PRD's error messages word for word, then hands a clean calendar to the planner. Filters in `web` add the rate
   limit, the request size limit and a strict Content Security Policy."

## 3:30 to 4:30 How AI built it

- "Claude Code built this under a written brief with STOP points where I reviewed the work. The rules it worked
  under are in `CLAUDE.md`." Show it briefly.
- Show `docs/ai/AI_WORKFLOW.md`: "Specialised subagents, each on the cheapest model that could do the job. A test
  designer wrote tests from the PRD before the code. An edge-case hunter attacked the app. Code, UI and security
  reviewers checked each part."
- Show `docs/ai/AI_FAILURE_LOG.md`, entry 15: "Every mistake the AI made that a check caught is logged, with how it
  was caught and the rule added so it doesn't happen again. Here, a plan request at New Year midnight returned an
  error because the clock was read twice. The edge-case hunter found it."

## 4:30 to 5:00 How we know it works

- Show the CI run: "One quality gate runs every check, the same locally and in CI: backend and frontend tests,
  mutation testing that catches 98% of injected bugs, a real-browser check for phone layout and accessibility,
  and a dependency scan."
- Close: "LeaveWise: the best plan for your leave, in under a minute, proven."

## 2027 holiday dates

FiftyFive's 2026 list (`backend/src/main/resources/holidays/india-2026.json`) with each holiday moved to its 2027
date. This is a stand-in for the demo, not FiftyFive's 2027 list: the company hasn't published one yet, so it isn't
bundled in the app (ADR 0007). Weekdays were checked with a script, and Good Friday against Easter 2027 (28 March).

Sources: the central government's 2027 list (DoPT O.M. of 16 July 2026, read through
[banaka.in/holidays/2027](https://banaka.in/holidays/2027), since the O.M. itself wasn't reachable) for every date
except Rang Panchami, which is from [bhaktibharat.com](https://www.bhaktibharat.com/amp/festival/rang-panchami).
Eid ul Fitr and Eid Al Adha depend on the moon and may move by a day. Janmashtami is 24 or 25 August depending on
the calendar; DoPT gives 25 August.

| 2027 date | Day | Holiday | 2026 type |
| --- | --- | --- | --- |
| 2027-01-01 | Fri | New Year's Day | fixed |
| 2027-01-14 | Thu | Makar Sankranti/Pongal | floater |
| 2027-01-26 | Tue | Republic Day | fixed |
| 2027-03-10 | Wed | Eid ul Fitr | fixed |
| 2027-03-23 | Tue | Holi | fixed |
| 2027-03-26 | Fri | Good Friday | floater |
| 2027-03-27 | Sat | Rangpanchmi | fixed |
| 2027-04-14 | Wed | Ambedkar Jayanti | floater |
| 2027-04-15 | Thu | Ram Navami | floater |
| 2027-04-19 | Mon | Mahaveer Jayanti | floater |
| 2027-05-17 | Mon | Eid Al Adha | floater |
| 2027-08-15 | Sun | Independence Day | fixed |
| 2027-08-17 | Tue | Raksha Bandhan | floater |
| 2027-08-25 | Wed | Janmashtami | floater |
| 2027-09-04 | Sat | Ganesh Chaturthi | floater |
| 2027-09-12 | Sun | Onam | floater |
| 2027-10-02 | Sat | Mahatama Gandhi Jayanti | fixed |
| 2027-10-08 | Fri | Mahav Navmi/ Durga Pooja | floater |
| 2027-10-09 | Sat | Dusshera | fixed |
| 2027-10-29 | Fri | Diwali | fixed |
| 2027-10-31 | Sun | Bhai Dooj | floater |
| 2027-11-04 | Thu | Chhath Pooja | fixed |
| 2027-11-14 | Sun | Guru Nanak Jayanti | floater |
| 2027-12-25 | Sat | Christmas Day | fixed |
