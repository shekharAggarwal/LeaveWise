# LeaveWise progress

Source of the steps: [kickoff-prompt.md](kickoff-prompt.md). Tick a step only when it is done and green.
Every session starts by reading `CLAUDE.md` and this file, then continues from the first unticked step.

## Notes

- 2026-10-09: the repository started empty, with no `docs/PRD.md`. The owner then shared the PRD as a Claude Docs page,
  which was copied word for word into `docs/PRD.md` (revision 13). The page's embedded build-plan diagram was
  transcribed as a table, because the Markdown export drops embedded diagrams.
- Astryx packages pinned at 0.6.6 (the npm `latest` tag on 2026-10-09). github.com is blocked from the cloud VM,
  but raw.githubusercontent.com works, so the official `apps/example-vite` files were read from there.

## Decisions and interpretations (for review at STOP 1)

Made without asking, as the brief says; each is also in the ADR or doc named.

- `theme add stone --import`: CLI 0.6.6 has no `--import` flag ("unknown option"). Ran `theme add stone`; its copy
  was identical to the package source, so the app imports the pinned `@astryxdesign/theme-stone` package instead
  and `package.json` declares `"astryx": {"theme": "stone"}` for `astryx doctor` (ADR 0008).
- Vitest uses StyleX's Babel plugin instead of the Astryx Vite plugin, which kept test runs open 10 s (ADR 0008).
- TypeScript 6.0.3 and plugin-react 5.2.0 (the example's majors) instead of the newest majors: typescript-eslint
  8.71 supports TypeScript below 6.1 only.
- Current year: days up to and including today don't count toward a break, even if they're days off (ADR 0006).
- Year edges use only bundled holiday lists for the adjacent years; without one, only weekly offs count (ADR 0006).
- Free long weekends are computed by the planner (a PRD planning rule, and required for the 0-leave case) even though
  the Results table lists them as Nice to have; a run that a leave extends is a break, not a free long weekend.
- `summary.totalDaysOff` = days inside breaks (the objective), not counting free long weekends (docs/API.md).
- Error and note copy the PRD doesn't give (year, break style, paste reasons, 413, 429, notes) is this project's
  wording, listed in docs/API.md.
- Weekly offs may be empty (0 to 6 days); the PRD only forbids all seven.
- Only one note per plan, in a fixed order (docs/API.md). PRD tension: "more leave days than working days left" asks
  for a plan using every working day, but if the window is shorter than the minimum break even that is no break
  (PRD Break rule), so the note is `NO_BREAK_POSSIBLE`. Today 31 Dec in the current year gives `NO_DAYS_LEFT`.
- At most 100 holidays per plan request (bundled list plus up to 50 pasted lines plus manual adds); invented limit.
- Nice-to-have results (other good breaks, share link, free long weekends beyond the 0-leave case) are deferred
  until every Must is polished; their planned API shape is in docs/API.md.
- `.ics` UIDs are stable per break; `DTSTAMP` is the download time, as RFC 5545 requires.
- The "no break possible" note omits the shorter-style suggestion when the style is already Long weekends.
- Pasting more than 50 lines: lines 51+ are reported as not loaded; lines 1 to 50 still load.
- "Today" is in Asia/Kolkata on the server; the browser takes it from `GET /api/options`.
- Docker: the CLI is installed but no daemon is running in the cloud VM (`/var/run/docker.sock` missing). To try in
  Phase 5 (start `dockerd`); if it can't run, the image build becomes a LAPTOP/CI step.
- Checked in the VM's Chromium (Playwright, 360 px, light and dark): the Phase 1 shell renders with Stone tokens and no
  console errors, without importing `astryx.css`. The theme names Montserrat/Figtree but ships no font files, so
  users without them installed get system fonts. Phase 3: decide whether to self-host the theme fonts.
- Skills and subagents created in this session load only in the next session, so in Phase 1 the `log-ai-failure`
  steps were followed by hand (AI failures 1 to 3).

## Phase 2 results (2026-10-09)

- 141 planner tests pass (135 from test-designer, 6 from edge-case-hunter). Oracle property: 1,500 tries, 1,500
  checks, all equal to brute force (539 with breaks, 702 with a budget above the working days).
- PIT on `dev.leavewise.planner`: 180 of 184 mutants killed (98%), line coverage 98%. Survivors: BreakOptimizer
  line 71 (`>` to `>=` inside `days != current.days ? days > current.days : ...`, equivalent) and line 65
  (`cost + rest.leaves` to `-`, equivalent: with equal days the candidate never has more leaves; checked
  exhaustively up to 14 days and on 90k random sequences by edge-case-hunter); PlanningCalendar lines 76 and 85
  (the 366-day safety bound on edge runs, unreachable through the API and not PRD behaviour).
- Speed: a full year with 60 leaves plans in 3 to 8 ms (best of 20 after warm-up), against the PRD's 1 second.
- Open interpretation for the owner (edge-case-hunter): planning next year on 30 or 31 Dec counts today and earlier
  days in a break that crosses 1 January. The PRD's Year edges rule allows it; ADR 0006 limits "today isn't counted"
  to the current year.

## First actions
- [x] Save the brief word for word to docs/ai/kickoff-prompt.md
- [x] Create docs/ai/PROGRESS.md
- [x] Commit and push
- [x] Read docs/PRD.md in full (copied from the owner's Claude Docs page)

## Phase 1: Context engineering and scaffolding (ends at STOP 1)
- [x] Empty backend project (Java 21, Spring Boot, Maven) that builds
- [x] Empty frontend project (React 19, TypeScript, Vite, Node 22) that builds
- [x] Install Astryx with exact pinned versions, add the `astryx` npm script
- [x] `npm run astryx -- theme add stone --import` (flag does not exist in CLI 0.6.6; see Decisions)
- [x] Short frontend/CLAUDE.md, then `npx @astryxdesign/cli init --features agents --agent claude`
- [x] Root CLAUDE.md
- [x] docs/ARCHITECTURE.md with a Mermaid diagram
- [x] docs/API.md (endpoints, request and response JSON, error format)
- [x] ADR: single deployable
- [x] ADR: no database
- [x] ADR: optimisation algorithm
- [x] ADR: deterministic tie-breaks
- [x] ADR: brute-force oracle testing
- [x] ADR: year-edge handling
- [x] ADR: holiday data source
- [x] ADR: Astryx (why, beta risk, version pinning, licence)
- [x] docs/QA.md: test strategy and PRD rule/edge case to test map
- [x] Subagents: Explore, gate-runner, test-designer, edge-case-hunter, code-reviewer, security-reviewer, ui-reviewer
- [x] Skill: quality-gate
- [x] Skill: log-ai-failure
- [x] Skill: astryx-ui
- [x] .claude/settings.json: format hook, PRD/.env edit block, frontend install at session start, PROGRESS re-inject after compaction, subagent limits, permissions allowlist
- [x] scripts/quality-gate.sh
- [x] docs/ai/AI_WORKFLOW.md, docs/ai/AI_FAILURE_LOG.md, docs/ai/REVIEW_LOG.md (empty), docs/HUMAN_TODO.md
- [x] STOP 1: context files, subagents, ADRs, recommended answers to PRD open questions, unclear PRD points

## Phase 2: Planner core (ends at STOP 2)
- [x] test-designer writes failing tests from the PRD planning rules
- [x] Day classification
- [x] Breaks
- [x] Best plan (exact method, provably optimal)
- [x] Ties (deterministic)
- [x] Year edges
- [x] Current-year planning from tomorrow (injected clock)
- [x] Free long weekends
- [x] Brute-force oracle and jqwik property test on at least 1,000 random calendars
- [x] Determinism test: holidays in any order give the same plan
- [x] Performance test: full year, 60 leave days, under 1 second
- [x] edge-case-hunter attack, then fixes
- [x] PIT on the planner package (target at least 80%, real number reported)
- [x] log-ai-failure run for every caught AI mistake
- [x] quality-gate passes, output shown; branch pushed
- [x] STOP 2: algorithm in plain words, worked example, oracle results, mutation score

## Phase 3: API and UI
- [x] Plan endpoint
- [x] Holiday list endpoint
- [x] .ics download (RFC 5545 all-day events, one per break)
- [x] Validation and error messages exactly as in the PRD table
- [x] Rate limit per IP, request size limit, security headers, inputs never logged
- [x] Health endpoint
- [x] code-reviewer on the API diff, fixes
- [x] UI journey screens (Astryx workflow, ui-reviewer after each)
- [x] Results: summary, break list, 12-month calendar, .ics download
- [x] Main flow test and axe check
- [x] code-reviewer on the UI diff, fixes
- [ ] LAPTOP: check screens in a real browser (cloud Chromium checks done at 360 and 1280 px, light and dark; a real phone and contrast check remain)

## Phase 4: Hardening and QA
- [ ] Full security-reviewer pass, fixes
- [ ] Full ui-reviewer pass across every screen, fixes
- [ ] No high-severity dependency issues (or each explained)
- [ ] edge-case-hunter across the whole app
- [ ] Every PRD acceptance item and edge case has a passing test; docs/QA.md map updated
- [ ] Performance and accessibility checks, fixes
- [ ] quality-gate passes

## Phase 5: Delivery prep (ends at STOP 3)
- [ ] Multi-stage Dockerfile
- [ ] GitHub Actions CI (quality-gate plus Docker build)
- [ ] Docker image built in the cloud VM, output shown
- [ ] Host config (default Render, current pricing checked) and docs/DEPLOYMENT.md
- [ ] README.md (problem, solution, demo link placeholder, quick start, architecture, limitations, dependencies, "For judges" table)
- [ ] LAPTOP: README screenshots
- [ ] docs/ai/AI_WORKFLOW.md finished (stages, subagent table, Claude-Session trailer note)
- [ ] docs/DEMO_SCRIPT.md
- [ ] docs/SUBMISSION.md and the secret-scan plus ZIP script
- [ ] quality-gate passes and Docker image builds, output shown; branch pushed
- [ ] STOP 3: cloud work summary, LAPTOP steps and human steps in order

## Laptop phase (ends at STOP 4)
- [ ] LAPTOP: fetch official holiday lists, build holiday JSON files with source URLs, list unverified dates in HUMAN_TODO
- [ ] LAPTOP: run the app locally, check every screen at phone and desktop widths, fix, save screenshots
- [ ] LAPTOP: quality-gate, commit and push
- [ ] STOP 4: remaining human steps in order
