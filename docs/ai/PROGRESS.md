# LeaveWise progress

Source of the steps: [kickoff-prompt.md](kickoff-prompt.md). Tick a step only when it is done and green.
Every session starts by reading `CLAUDE.md` and this file, then continues from the first unticked step.

## Notes

- 2026-10-09: the repository started empty, with no `docs/PRD.md`. The owner then shared the PRD as a Claude Docs page,
  which was copied word for word into `docs/PRD.md` (revision 13). The page's embedded build-plan diagram was
  transcribed as a table, because the Markdown export drops embedded diagrams.
- Astryx packages pinned at 0.6.6 (the npm `latest` tag on 2026-10-09). github.com is blocked from the cloud VM,
  but raw.githubusercontent.com works, so the official `apps/example-vite` files were read from there.

## First actions
- [x] Save the brief word for word to docs/ai/kickoff-prompt.md
- [x] Create docs/ai/PROGRESS.md
- [x] Commit and push
- [x] Read docs/PRD.md in full (copied from the owner's Claude Docs page)

## Phase 1: Context engineering and scaffolding (ends at STOP 1)
- [ ] Empty backend project (Java 21, Spring Boot, Maven) that builds
- [ ] Empty frontend project (React 19, TypeScript, Vite, Node 22) that builds
- [ ] Install Astryx with exact pinned versions, add the `astryx` npm script
- [ ] `npm run astryx -- theme add stone --import`
- [ ] Short frontend/CLAUDE.md, then `npx @astryxdesign/cli init --features agents --agent claude`
- [ ] Root CLAUDE.md
- [ ] docs/ARCHITECTURE.md with a Mermaid diagram
- [ ] docs/API.md (endpoints, request and response JSON, error format)
- [ ] ADR: single deployable
- [ ] ADR: no database
- [ ] ADR: optimisation algorithm
- [ ] ADR: deterministic tie-breaks
- [ ] ADR: brute-force oracle testing
- [ ] ADR: year-edge handling
- [ ] ADR: holiday data source
- [ ] ADR: Astryx (why, beta risk, version pinning, licence)
- [ ] docs/QA.md: test strategy and PRD rule/edge case to test map
- [ ] Subagents: Explore, gate-runner, test-designer, edge-case-hunter, code-reviewer, security-reviewer, ui-reviewer
- [ ] Skill: quality-gate
- [ ] Skill: log-ai-failure
- [ ] Skill: astryx-ui
- [ ] .claude/settings.json: format hook, PRD/.env edit block, frontend install at session start, PROGRESS re-inject after compaction, subagent limits, permissions allowlist
- [ ] scripts/quality-gate.sh
- [ ] docs/ai/AI_WORKFLOW.md, docs/ai/AI_FAILURE_LOG.md, docs/ai/REVIEW_LOG.md (empty), docs/HUMAN_TODO.md
- [ ] STOP 1: context files, subagents, ADRs, recommended answers to PRD open questions, unclear PRD points

## Phase 2: Planner core (ends at STOP 2)
- [ ] test-designer writes failing tests from the PRD planning rules
- [ ] Day classification
- [ ] Breaks
- [ ] Best plan (exact method, provably optimal)
- [ ] Ties (deterministic)
- [ ] Year edges
- [ ] Current-year planning from tomorrow (injected clock)
- [ ] Free long weekends
- [ ] Brute-force oracle and jqwik property test on at least 1,000 random calendars
- [ ] Determinism test: holidays in any order give the same plan
- [ ] Performance test: full year, 60 leave days, under 1 second
- [ ] edge-case-hunter attack, then fixes
- [ ] PIT on the planner package (target at least 80%, real number reported)
- [ ] log-ai-failure run for every caught AI mistake
- [ ] quality-gate passes, output shown; branch pushed
- [ ] STOP 2: algorithm in plain words, worked example, oracle results, mutation score

## Phase 3: API and UI
- [ ] Plan endpoint
- [ ] Holiday list endpoint
- [ ] .ics download (RFC 5545 all-day events, one per break)
- [ ] Validation and error messages exactly as in the PRD table
- [ ] Rate limit per IP, request size limit, security headers, inputs never logged
- [ ] Health endpoint
- [ ] code-reviewer on the API diff, fixes
- [ ] UI journey screens (Astryx workflow, ui-reviewer after each)
- [ ] Results: summary, break list, 12-month calendar, .ics download
- [ ] Main flow test and axe check
- [ ] code-reviewer on the UI diff, fixes
- [ ] LAPTOP: check screens in a real browser

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
