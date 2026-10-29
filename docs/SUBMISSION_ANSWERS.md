# Submission form answers

Drafts for the hackathon submission form (hackathon-55.lovable.app/submit), one heading per field, each within the
form's character limit. Facts come from the repo docs named in each answer.

## A. Project / Product Name

LeaveWise

## A. Problem Statement

Every year when the new holiday list comes out, most of us sit down with a calendar and try to work out which leave days to take so they join up with weekends and holidays into proper breaks. It takes longer than it should, and people still miss the good combinations.

A few things make it harder than it looks:

- Holidays land on different weekdays every year, so last year's plan is useless.
- Your balance is limited, so order matters. If you spend three leaves on a so-so break in March, you may not have enough left for a much better one in October.
- Calendar apps show you the holidays, but none of them tells you where to put your leaves.

LeaveWise is for salaried employees in India planning their year's leave, usually right after HR shares the holiday list.

## A. Solution & Key Features

LeaveWise is a one-page web app. You enter your leave balance, check the holiday list, pick your weekly offs and the kind of break you want, and it tells you exactly which days to take off for the most days off in the year. It also shows you that plan on a 12-month calendar and gives you a calendar file to import. There's no login and nothing is stored.

What it does:

- Every input has a sensible default (12 leaves, Saturday and Sunday off, long weekends), so you can get a plan by typing one number.
- The holiday list comes prefilled. For 2026 it's FiftyFive's own list, with the floating holidays unticked so you pick yours. You can untick holidays, add company ones, or paste a list in `YYYY-MM-DD Name` format. Bad lines get a line number and a reason, and the good lines still load.
- Three break styles: long weekends (3+ days), short trips (5+) or big trips (9+).
- The results start with one line that answers the real question: how many days off your leaves buy, across how many breaks. Below it is each break with its dates, leave days, holidays and days off per leave, then a full-year calendar where every day has a colour and a letter (so it doesn't rely on colour alone). Tap a day to see what it is.
- A download gives you an `.ics` file with one all-day event per break, which you can import into Google Calendar or Outlook.

The part I care most about is that the plan is actually the best one, not just a good guess. The planner is an exact dynamic program, not a greedy heuristic. On small random calendars a brute-force solver tries every possible combination of leave days, and the planner has matched it on all 1,500 random calendars tested. When two plans tie, it picks the one with fewer leaves, then the one with earlier dates, so the same inputs always give the same answer.

It works from a 360 px phone up to desktop, can be used by keyboard, and was checked with axe against WCAG 2.2 AA. A full-year plan comes back in a few milliseconds.

## A. Working Product / Demo URL

(Leave empty until the app is deployed.)

## A. Source Code Repositories

- Full stack (backend in `backend/`, frontend in `frontend/`): https://github.com/shekharAggarwal/LeaveWise

## B. Demo Video URL

(The owner records it and pastes the link; it must be at most 5 minutes and open without signing in.)

## C. Architecture (Markdown)

# LeaveWise architecture

LeaveWise is one Spring Boot process that serves both the React page and a small JSON API. Nothing is stored: every request carries all of its inputs, and the only data on disk are the bundled holiday lists.

## Components

```
Browser (React 19 + TypeScript, Astryx design system)
   │  fetch /api/*
   ▼
Spring Boot 4.1 on Java 21  (one jar, one Docker image)
   ├─ web       security headers + CSP, per-IP rate limit, 32 KB body limit, SPA forwarding
   ├─ api       REST controllers, validation, RFC 9457 problem+json errors
   ├─ planner   plain Java, no Spring, no I/O: day types, exact DP, breaks, notes
   ├─ holidays  bundled JSON per year (with source), paste parser
   └─ ics       RFC 5545 calendar writer
```

| Package | What it does |
| --- | --- |
| `dev.leavewise.planner` | Builds the planning calendar (window, day kinds, year edges), finds the best plan, derives breaks, free long weekends and notes. Depends only on `java.time`. |
| `dev.leavewise.holidays` | Loads and checks `holidays/india-YYYY.json` at startup, parses pasted `YYYY-MM-DD Name` lines with per-line errors, merges duplicate dates. |
| `dev.leavewise.ics` | Writes RFC 5545 calendars: CRLF line endings, line folding, text escaping, all-day `VALUE=DATE` events. |
| `dev.leavewise.api` | The HTTP contract in `docs/API.md`: request and response records, validation, error mapping. |
| `dev.leavewise.web` | Servlet filters for headers, rate limiting and body size; forwards non-API paths to `index.html`. |

The planner never sees HTTP types, and the API layer never makes planning decisions. Keeping that boundary is what let me test the planner exhaustively (unit, property and brute-force oracle tests, plus mutation testing) without starting Spring.

## Data flow: "Plan my leaves"

1. The browser checks the inputs for quick feedback and sends `POST /api/plan` with everything in the body (year, leave days, weekly offs, break style, holidays).
2. The web filters apply the size limit, the per-IP rate limit and the security headers.
3. The controller validates again (the server is the authority; error messages are word for word from the PRD) and looks up the holidays of the years before and after, for breaks that cross 1 January or 31 December.
4. The planner gets a `PlanningCalendar`, the leave budget and the minimum break length, and returns breaks, leave dates, free long weekends and notes.
5. The response has the summary, the breaks and every calendar day already classified, so the browser doesn't re-derive anything.
6. `POST /api/plan/ics` with the same body returns `text/calendar` with one all-day event per break.

Other endpoints: `GET /api/options` (today in Asia/Kolkata, default year), `GET /api/holidays?year=`, `POST /api/holidays/parse`, and `/actuator/health`.

## The planner

The inputs become a sequence of days, each either a working day (a leave can go there) or a day off (weekly off or holiday). It covers the planning window (1 January, or tomorrow for the current year, to 31 December) plus the days off just outside the year.

In an optimal plan every break is a maximal run of days off with an untaken working day on each side. So the planner lists those candidate runs with their cost in leaves, and a dynamic program over (position, leaves left) picks the non-overlapping set with the most days off. Comparison order: most days in breaks, then fewer leaves, then the earliest leave dates. That gives one deterministic answer. A full year with 60 leaves plans in 3 to 8 ms.

## Cross-cutting decisions

| Concern | Decision |
| --- | --- |
| Time | One `Clock` in Asia/Kolkata, read once per request and passed down, so every rule sees the same "today". Tests inject fixed clocks. |
| Determinism | Holidays are sorted and merged, weekly offs treated as a set, and ties have a total order. |
| Privacy | No database, no cookies, no analytics, no request-body logging. |
| Security | Server-side validation, 32 KB body limit, per-IP token bucket, strict CSP with no inline scripts or styles (checked at build time and in a real browser). |
| Deployment | Multi-stage Dockerfile (Node builds the page, Maven builds the jar with it inside, a JRE image runs it as non-root). `render.yaml` for Render, `compose.yaml` for a local one-command run. CI on GitHub Actions. |

## Stack

- Backend: Java 21, Spring Boot 4.1.1 (MVC, Validation, Actuator), Maven. Tests: JUnit 6, jqwik (property tests), PIT (mutation testing).
- Frontend: React 19, TypeScript 6 (strict), Vite 8, Astryx 0.6.6 with a custom LeaveWise theme built on Stone, StyleX. Tests: Vitest, Testing Library, axe-core, Playwright for the real-browser check.
- Delivery: Docker, GitHub Actions (quality gate, osv-scanner dependency scan, image build and smoke test), Render.

## Key technical decisions

There are eight ADRs in `docs/adr/`: a single deployable, no database, an exact optimisation algorithm, deterministic tie-breaks, brute-force oracle testing, year-edge handling, the holiday data source and the Astryx design system.

## D. How was AI used during development?

Nearly all of the code, tests and docs were written by Claude Code, working from a brief and a PRD I wrote. I treated it like a lead engineer on a team where I'm the product owner and reviewer, not like an autocomplete.

The flow was:

1. Define. I worked out the PRD with Claude in a normal claude.ai chat, going back and forth on the rules until they were exact enough to test (what counts as a break, how ties are broken, what happens at year edges). Then I wrote a build brief with five phases, STOP points where I review, and rules it couldn't break.
2. Design before code. In phase 1 it wrote the architecture doc, the API contract, eight ADRs and a QA map linking every PRD rule to a test. A code-reviewer subagent checked those docs against the PRD and found 10 issues, all fixed before any code existed.
3. Build test-first. A test-designer subagent wrote the tests from the PRD before the code: 135 planner tests and the brute-force oracle, then 135 API, holiday and `.ics` tests. The main session then wrote code until they passed.
4. Attack and review. An edge-case-hunter subagent tried to break the planner and later the whole app. code-reviewer, ui-reviewer and security-reviewer subagents reviewed each finished part. Every finding was either fixed or written down with a reason.
5. Deliver. It also wrote the Dockerfile, CI, deployment config, README and demo script.

For the UI redesign I sketched the screens with Claude Design, picked the look I wanted, and had Claude Code match the canvas while keeping the PRD rules where they disagreed.

My part was the idea, the requirements, the reviews at each STOP, the decisions it couldn't make on its own (like which holiday list to preload), and the real-world steps like checking on a phone and deploying.

## D. Which AI tools and models were used?

- Claude Code was the main tool, run partly in the cloud (Claude Code on the web) and partly on my laptop.
- Main session: Claude Opus at high effort. It acted as the lead engineer and handled architecture, code, integration and decisions.
- Subagents, each on the cheapest model that could do the job well:
  - Haiku: gate-runner (runs the full quality gate and returns only the summary) and Explore (read-only code search).
  - Sonnet: test-designer, code-reviewer, ui-reviewer and security-reviewer.
  - Opus: edge-case-hunter, since finding real edge cases in an optimiser needs the strongest reasoning.
- Claude (claude.ai chat) to work out the PRD with me.
- Claude Docs to hold the PRD, which was copied word for word into the repo.
- Claude Design for the redesign canvas I approved before the UI was rebuilt.
- Claude in Chrome and Playwright for checking the running app in a real browser.

No other AI tools were used.

## D. How did you manage context?

I kept the context in files in the repo, not in my head or in a long chat. Each layer has one job:

- `CLAUDE.md` is the standing brief: commands, the architecture map, coding standards, a definition of done and working rules. It imports the PRD and the progress file, so every session starts with them loaded.
- `docs/PRD.md` holds the exact rules and error messages. It's read-only, and a hook blocks any edit to it, so the AI can't quietly "fix" a requirement to match its code.
- `docs/ai/kickoff-prompt.md` is my original brief, word for word. `docs/ai/PROGRESS.md` is a checklist of every step, plus the decisions and interpretations it made along the way. Every session starts by reading both and continues from the first unticked step. A hook re-injects the next step after context compaction, so a long session doesn't lose its place.
- `docs/ARCHITECTURE.md`, `docs/API.md` and the ADRs were written before the code, so the code follows the design and not the other way round.
- `frontend/CLAUDE.md` only loads when working in the frontend, which keeps UI rules out of backend work.
- Subagents get tight briefs and a 10-finding cap, so noisy work like running the full gate or reading lots of code stays out of the main context.
- `docs/ai/AI_FAILURE_LOG.md` records every mistake the AI made. When a rule would prevent a repeat, it goes into `CLAUDE.md`. That's how the context got better over the project.

## D. Approach to planning, prompting and iteration

The plan was the brief: five phases (scaffolding and context, planner core, API and UI, hardening, delivery) with a STOP at the end of the important ones. At each STOP it summarised what it had done, the numbers (test counts, mutation score, oracle results) and the questions it needed answered, and waited for me.

I didn't prompt it line by line. Instead the brief set the rules: test first, never invent data, commit after every green step, and don't ask me questions between STOPs but make the most reasonable choice and record it. Then it worked through a phase on its own. When something in the PRD was ambiguous, it picked the most reasonable reading, wrote it down in PROGRESS.md under "Decisions and interpretations", and raised it at the next STOP. That kept it moving without guessing silently.

Iteration happened in small loops: write a failing test, write the code, run the tests, run the quality gate, commit and push. Every green step was its own Conventional Commit, so the git history (42 commits) shows the real order of work.

The bigger iterations came from reviews. The phone layout, the redesign and the holiday list all came from me looking at the app and asking for changes. Each time the same loop ran: plan the change, update tests, review with a subagent, fix, commit.

## E. Agents, skills, reusable instructions or workflows

Seven subagents in `.claude/agents/`, each with a written brief, a model and a limited set of tools:

- Explore (Haiku): read-only search across the repo.
- gate-runner (Haiku): runs `scripts/quality-gate.sh` and returns only the summary table and any failures, word for word.
- test-designer (Sonnet): writes failing tests from the PRD for a feature before its code exists. It never writes production code.
- edge-case-hunter (Opus): attacks the planner or the app with nasty inputs and adds only failing tests. It never fixes anything.
- code-reviewer (Sonnet): reviews one finished feature's diff against the PRD, the ADRs and CLAUDE.md. Read-only.
- security-reviewer (Sonnet): checks validation, headers, rate and size limits, dependencies and secrets. Read-only.
- ui-reviewer (Sonnet): checks a screen against the UI quality bar and the Astryx rules. Read-only.

Three skills in `.claude/skills/`:

- `quality-gate` runs every check through gate-runner and reports the real result.
- `log-ai-failure` records an AI mistake and, where it helps, adds a rule to CLAUDE.md.
- `astryx-ui` is the UI rulebook, preloaded into ui-reviewer.

Reusable instructions: `CLAUDE.md` and `frontend/CLAUDE.md`, the definition of done, and `scripts/quality-gate.sh`, which runs 12 checks (backend tests, mutation testing, frontend lint, tests and build, Astryx doctor, a real-browser check, secret scan) the same way locally and in CI.

## E. Responsibilities the agents handled

- Main session: read the brief and PRD, wrote the architecture, API contract and ADRs, wrote the production code, integrated everything, kept PROGRESS.md and the failure log honest, and committed each green step.
- test-designer wrote the tests before the code: 135 planner tests plus the brute-force oracle in phase 2, and 57 holiday and `.ics` tests plus 78 API and web tests in phase 3 (two runs in parallel).
- edge-case-hunter ran twice. On the planner it ran a 3,000-try fuzz at the year edges and killed two mutation-testing survivors. On the whole app it found six real bugs, including a 500 error at New Year midnight and the browser and server counting a paste limit differently.
- code-reviewer ran on the docs, the API, the UI and both redesigns. On the API it found two high-severity issues (a filter bypass via encoded URLs and line injection into the `.ics` file) and confirmed them against a running copy with curl.
- security-reviewer started its own copy of the app and probed it. It found no critical or high issues, but caught the built page breaking its own Content Security Policy.
- ui-reviewer read the code and Playwright screenshots at 360 and 1280 px. It caught focus moving to a hidden element, small touch targets and contrast problems.
- gate-runner ran the full gate before every commit and returned just the summary, so the main session's context stayed clean.

## E. Guardrails implemented

- The PRD and any `.env` file can't be edited, by file tools or shell commands. A hook (`guard-protected-files.mjs`) blocks it, and it has its own tests.
- Every edited file is formatted automatically (google-java-format for Java, Prettier for the frontend).
- A narrow permissions allowlist in `.claude/settings.json`. Force-push, pushing to `main` and deleting branches are denied.
- At most 3 subagents at once, and subagents can't start their own subagents.
- Reviewer agents are read-only. The edge-case hunter can only add tests, not change code.
- Lint enforces the design system: raw `<div>` and inline styles fail the build.
- One quality gate for everything, run before every commit and in CI. A step isn't ticked until the gate passes.
- Rules in CLAUDE.md that came from real mistakes, for example: run the command that proves a fact before writing it in a doc, never weaken or delete a failing test to get green, and never invent data (holiday dates, test results, review notes).
- A secret scan on every commit, and on the full git history before the submission ZIP is built.
- App security: server-side validation, a request size limit, a per-IP rate limit, a strict CSP and no logging of user input.

## E. How was human review incorporated?

I reviewed at fixed STOP points in the brief, not whenever I happened to look.

- STOP 1, after scaffolding: I read the context files, ADRs and its list of PRD interpretations, answered the open questions and approved.
- STOP 2, after the planner: it explained the algorithm in plain words with a worked example, the oracle results and the mutation score. I approved before any UI was built.
- STOP 3, before delivery: a summary of the cloud work and the steps only I can do.

Between STOPs I used the app and pushed back. I asked for the UI redesign, approved it on a Claude Design canvas, then asked it to match the canvas more closely. I reported the broken phone layout and gave it FiftyFive's 2026 holiday list from Keka to prefill. It checked every date against the screenshot before committing the list, and when its question to me got the fixed and floater counts wrong, that was corrected and noted too.

My own words are kept separately, word for word, in `docs/ai/REVIEW_LOG.md`, so anyone can see what I actually said, not the AI's summary of it. Whenever the AI had to interpret something, it logged it in PROGRESS.md for me to check rather than deciding quietly.

## F. AI-assisted testing strategy

The main risk in this app is the planner giving a plan that looks fine but isn't the best one. So most of the testing effort went there, in layers:

1. Tests from the requirements, written first. test-designer turned each PRD planning rule and edge case into tests before the planner existed, so the tests describe what the PRD says, not what the code does.
2. A brute-force oracle. A separate, deliberately simple solver tries every combination of leave days on small calendars. A jqwik property test generates random calendars (holidays, weekly offs, budgets, break styles) and checks that the planner's answer equals the oracle's.
3. Mutation testing with PIT on the planner package, to show the tests actually catch changes in the code and not just run it.
4. Adversarial testing. The edge-case-hunter's job was to break things, and it could only add failing tests, not fixes.
5. API and web tests against the contract in `docs/API.md`, including the exact PRD error messages and security tests run against a real server.
6. Frontend tests with Testing Library, axe for accessibility, and a real-browser check in Chromium at 360 and 1280 px (no horizontal overflow, no CSP violations, touch targets of at least 24 px, WCAG 2.2 AA).

`docs/QA.md` maps every PRD rule and edge case to the tests that cover it, so you can check nothing was skipped.

## F. Tests created or executed

- Over 300 backend tests (JUnit 6 and jqwik): planner rules, ties, year edges, current-year planning with an injected clock, leap year 2028, free long weekends, holiday parsing, `.ics` output, API validation and errors, security headers, rate and size limits.
- The oracle property test: 1,500 random calendars, all 1,500 equal to brute force (539 of them with breaks, 702 with more leave than working days).
- A determinism test: holidays in any order give the same plan.
- A performance test: a full year with 60 leave days plans in 3 to 8 ms, against the PRD's limit of 1 second.
- Mutation testing: 183 of 187 planner mutants killed (98%). The four survivors were looked at one by one: two are equivalent mutants, and two are a safety bound the API can't reach.
- More than 110 frontend tests (Vitest and Testing Library), including the main flow from defaults to results, error states and axe checks.
- The real-browser check (Playwright) in the quality gate, in four app states at two widths.
- CI on GitHub Actions runs the same gate, an osv-scanner dependency scan of the backend and frontend, and a Docker image build with a smoke test.

## F. How AI helped find bugs, edge cases or regressions

The AI's own checks caught its own mistakes, and I logged 21 of them in `docs/ai/AI_FAILURE_LOG.md` with how each was found. Some examples:

- test-designer's property test caught the first planner adding a year-edge rule that no document asked for. It also showed that one of the notes in the API could never actually happen, so it was removed.
- code-reviewer found that the security filters could be bypassed with an encoded URL like `/%61pi`, and that a carriage return in a holiday name could inject lines into the `.ics` file. Both were confirmed against a running copy and fixed with regression tests.
- edge-case-hunter found a 500 error when a request landed exactly at New Year midnight, because the clock was read twice in one request. It also found the browser and server disagreeing on control characters, no-break spaces and how to count characters in a pasted list.
- security-reviewer and a Playwright check found that the production page broke its own Content Security Policy.
- The first CI run's dependency scan found critical Tomcat and high Jackson advisories, which were patched. The AI had marked that step done before the backend was scanned, so a rule was added: a check that couldn't run doesn't count as passed.
- The quality gate caught a flaky test that matched error text which the design system also announces in a hidden live region.

Thirteen of these led to a new rule in CLAUDE.md, so the same kind of mistake didn't come back.

## F. How were test results validated?

I didn't take "tests pass" on trust:

- The numbers in the docs come from actual runs, and the rules say to run the command that proves a fact before writing it down. If a check couldn't run (for example the backend CVE scan couldn't reach its server from the cloud VM), the step stayed unticked until CI ran it.
- The oracle is independent of the planner. It's a simple brute-force search with no shared logic, so agreement on 1,500 random calendars means something.
- Mutation testing checks the tests themselves. The surviving mutants weren't hidden; each one is explained in PROGRESS.md.
- Tests were written to fail first. A fix wasn't accepted until a test failed without it and passed with it, and reviewers flagged tests that couldn't fail as written.
- The gate runs the same way on my laptop and in GitHub Actions, so a green result isn't tied to one machine.
- The running app was also checked in Chrome at phone and desktop widths against the canvas I approved, and I tried it myself and reported what was off.

## G. Important architectural or product decisions

- One deployable: the React page is built into the Spring Boot jar, and one Docker image runs it all. That's simpler to host and there's no CORS.
- No database and no login. Every request carries its inputs and nothing is kept, which keeps privacy simple.
- An exact dynamic program instead of a greedy heuristic, so the plan is provably the best, checked against brute force.
- Deterministic ties: fewer leaves first, then earlier dates, so the same inputs always give the same plan.
- For the current year only days after today are planned, and "today" comes from one clock in Asia/Kolkata.
- Breaks can count weekly offs and known holidays just across the year boundary, but leaves always stay inside the chosen year.
- The server classifies every calendar day, so the browser never re-derives the rules.
- Astryx design system with exact version pins, because it's in beta.
- The 2026 list is FiftyFive's own holidays, with floaters unticked.

## G. Known limitations or incomplete features

- Only 2026 has a bundled holiday list (FiftyFive's). For 2027 you add or paste your holidays until an official list is added.
- When plans tie, the earlier dates win, so breaks can bunch up early in the year.
- Not built yet: "other good breaks", a share link, and free long weekends outside the 0-leave case (all Nice to have in the PRD).
- Out of scope: half days, leave types, alternate-Saturday patterns, state-wise lists, team views, other countries.
- On a free hosting plan the app sleeps when idle, so the first load after a pause is slow.
- Still to do: a check on a real phone and testing with three colleagues.

## G. External dependencies

- Backend: Spring Boot 4.1.1 (MVC, Validation, Actuator) with Tomcat 11.0.26 and Jackson 3.1.7. Tests: JUnit 6, jqwik, PIT.
- Frontend: React 19, TypeScript 6, Vite 8, Astryx 0.6.6 (beta design system), StyleX, self-hosted Figtree and Montserrat fonts. Tests: Vitest, Testing Library, axe-core, Playwright.
- Delivery: Docker, GitHub Actions, osv-scanner, Render for hosting.

No external APIs or paid services are called at runtime.

## G. Current risks and future improvements

Risks:

- Holiday data. Lists change, and companies differ. The app reminds users to check their company's official list, and each bundled file records its source.
- Astryx is in beta. Versions are pinned exactly, and lint rules keep the UI inside the design system, so an upgrade is a deliberate step and not a surprise.
- Free hosting sleeps when idle and has limited memory. The app starts in about 2 seconds and stays around 150 MB after 50 full-year plans, so it fits, but the first load can be slow.
- New advisories keep appearing for dependencies. CI scans them on every push, and on 29 Oct it caught three new ones in dev-only tools (fast-uri, undici, zod). All three had patch releases, so they were updated the same day.
- The rate limit trusts the forwarded client IP only from private proxy addresses. That's safe behind the host's proxy, and the deployment guide says how to change it otherwise.

Future improvements:

- The remaining Nice-to-haves: a list of other good breaks ranked by days off per leave, a share link that keeps the inputs in the URL, and free long weekends shown for every plan.
- Official holiday lists for 2027 and later, and optional state-wise lists.
- An option to spread breaks across the year instead of only maximising days off. This was an open question in the PRD.
- Half days and alternate-Saturday patterns, which many companies use.
- For HR teams, a way to publish a company holiday list as a link that employees open straight into LeaveWise.

## H. Pre-existing Work Disclosure

(Owner answers. The repository started empty on 2026-10-09, per `docs/ai/PROGRESS.md`.)
