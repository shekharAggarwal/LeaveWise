# LeaveWise: autonomous build brief

You are the lead engineer building LeaveWise, my solo entry for the FiftyFive "Build With AI Hackathon 2026". This repository already contains the product requirements in docs/PRD.md. Read it fully before anything else.

The idea and the PRD were worked out with Claude in a claude.ai chat; I chose the idea, the scope and the name. You take it from here.

UI theme: Stone

## Where you're running
- This brief starts in a Claude Code cloud session. I'll continue the same work later in Claude Code on my laptop, possibly in a fresh session, so the repo must always be ready to resume.
- Commit and push your branch after every green step, so no work is ever stuck in the cloud VM.
- The environment variable CLAUDE_CODE_REMOTE is "true" only in the cloud. There, skip the steps marked LAPTOP: list them in docs/HUMAN_TODO.md under "Laptop" and carry on with everything else.
- At the start of every session, and after a context compaction, read CLAUDE.md and docs/ai/PROGRESS.md, then continue from the first unticked step.

Work on your own until a STOP point. At a STOP, finish the phase, push, tell me what you did and exactly what I should check, then wait for my review notes. Don't ask me questions between STOPs: make the most reasonable choice, record it in an ADR or in PROGRESS.md, and keep going.

## First actions
1. Save this brief word for word to docs/ai/kickoff-prompt.md.
2. Create docs/ai/PROGRESS.md with every phase and step below as a checklist, and update it after each step.
3. Commit and push.

## How we're judged (optimise for this, in this order)
- 35% AI development practice: AI used across the whole lifecycle; context engineering (requirements, architecture, standards, AI instructions, delivery info, context that evolves as we learn); AI-assisted QA; agents, skills and instructions; human validation; learning from AI failures.
- 30% engineering: architecture, maintainability, testing, security, reliability, deployment, documentation, explicit trade-offs.
- 25% product quality: the core journey works flawlessly, good UX, the chosen scope complete and polished.
- 10% idea and business potential.
The organisers' own rules: a small polished product beats a big unfinished one; more AI is not better AI; never treat the amount of AI-generated code as success; validate every AI output. Build exactly the PRD, and polish every Must before touching any Nice to have.

The submission needs: a product description and problem statement; a working demo link; a ZIP of the whole project with its full git history; architecture, stack, technical decisions, limitations and dependencies; how AI was used (workflow, context engineering, skills, agents, instructions, guardrails); the AI-assisted QA approach; a demo video of at most 5 minutes (I record it). No secrets in the repo or the ZIP. Deadline: 30 Oct 2026, end of day. I will submit on 29 Oct.

## Non-negotiable rules
- Never invent anything: holiday dates, test results, metrics, log entries or review notes. Record only what actually happened.
- Holiday dates come only from an official Government of India source, with the source URL stored in each data file. Government sites aren't reachable from the cloud, so building the real holiday files is a LAPTOP step. Until then, tests use small fixtures clearly marked as test data.
- Never skip, weaken or delete a failing test to get to green. Fix the code, or stop and explain.
- No secrets, personal data, or customer or employer material in the repo. None should be needed.
- Push only your own working branch: never to main, no force-pushes, no branch deletions. Never deploy, create accounts or run anything that costs money; those steps are mine, written in docs/HUMAN_TODO.md with exact instructions.
- Don't edit docs/PRD.md. If a requirement looks wrong, note it for the next STOP and build what the PRD says.
- docs/ai/REVIEW_LOG.md holds only my words. When I send review notes, add them word for word with the date, and write nothing else there.
- Use Conventional Commit messages. The full history is submitted and judged.

## Stack
- Repo layout: backend/ (Maven) and frontend/ (Vite), with the Dockerfile and scripts/ at the root.
- Backend: Java 21 (pre-installed in the cloud VM), the latest stable Spring Boot that supports it, Maven. The planner is a plain Java package with no Spring dependency.
- Frontend: React 19, TypeScript and Vite on Node 22, built only with the Astryx design system: @astryxdesign/core with @stylexjs/stylex, the theme package named at the top, and @astryxdesign/cli. Follow the official Vite example in github.com/facebook/astryx (apps/example-vite) for setup. Pin Astryx versions exactly, because it's in beta. No Tailwind, no other UI library.
- One deployable: a multi-stage Dockerfile builds the frontend and Spring Boot serves it. No database; holiday lists are JSON resources.
- Tests: JUnit 5, jqwik for property tests, PIT for mutation testing, Vitest with React Testing Library and an axe accessibility check.
- scripts/quality-gate.sh runs every check (backend tests, mutation testing on the planner, frontend lint, tests and build, npm run astryx -- doctor, a secret scan), prints a short summary and exits non-zero on any failure. CI on GitHub Actions runs the same script plus the Docker build.

## UI quality bar (it must not look AI-generated)
- Use Astryx's workflow for every screen: npm run astryx -- build "<screen idea>" to pick the template, npm run astryx -- template <name> <path> to scaffold it, then npm run astryx -- component <Name> --dense for every component you use. Never guess props.
- Follow Astryx's rules: start every page from a template, no raw divs, no inline style={{}}, tokens instead of magic numbers. Custom styling only through StyleX xstyle, and only when no component prop does the job.
- One theme with its palette unchanged. Colour carries meaning (leave days, holidays, weekends) and is always paired with a text label.
- No gradients, glows, emoji, decorative illustrations, stock photos, fake stats or testimonials, or lorem ipsum.
- Copy is short and specific, with numbers first ("12 leaves, 41 days off"). Never "unlock", "seamless", "supercharge", "effortless", "elevate", "magic" or similar filler. Dates in Indian format (Fri, 2 Oct).
- One primary action per screen. Real empty, loading and error states. Visible keyboard focus. Respect reduced motion. Works in light and dark mode.

## Subagents: the right model for each job
Main session: Opus at high effort. Raise effort only for the Phase 2 algorithm, and only if the oracle keeps failing. Never use Fable.

Create these in .claude/agents/ with exactly these settings. Keep each description to one line and put the detail in the body.

| Agent | model | effort | tools | other | Use it for |
| --- | --- | --- | --- | --- | --- |
| Explore (replaces the built-in) | haiku | (none) | Read, Grep, Glob | omitClaudeMd: true, maxTurns: 15 | finding files, symbols and usages |
| gate-runner | haiku | (none) | Bash, Read, Grep | omitClaudeMd: true, maxTurns: 10 | running scripts/quality-gate.sh and returning only the summary and failures |
| test-designer | sonnet | high | Read, Grep, Glob, Write, Edit, Bash | maxTurns: 40 | writing failing tests from the PRD before each feature's code |
| edge-case-hunter | opus | high | Read, Grep, Glob, Write, Edit, Bash | memory: project, maxTurns: 40 | attacking the planner; it only adds failing tests, never production code |
| code-reviewer | sonnet | high | Read, Grep, Glob, Bash | memory: project, maxTurns: 25 | reviewing each finished feature's diff against the PRD and CLAUDE.md |
| security-reviewer | sonnet | medium | Read, Grep, Glob, Bash | maxTurns: 20 | input validation, headers, rate limits, dependencies, secrets |
| ui-reviewer | sonnet | medium | Read, Grep, Glob, Bash | skills: astryx-ui, maxTurns: 20 | checking each screen against the UI quality bar and Astryx's rules |

Use them efficiently:
- Delegate only when it keeps noisy output out of the main context, needs fresh eyes, or can run on a cheaper model. Small edits and reading a file you already know stay in the main session.
- Give each agent a tight brief: the files or diff range, the PRD sections that matter, and what to return. Never paste the conversation history.
- Review once per finished unit (a feature, a screen, a phase), never after every edit. Run independent reviewers in parallel.
- Reports have at most 10 findings, each with file:line, severity, why it matters and the fix. Fix gaps in correctness and requirements; log style suggestions as optional rather than chasing them.
- Settings enforce the limits: CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS=3, and CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1 so subagents can't spawn more subagents.
- Keep a short table in docs/ai/AI_WORKFLOW.md: agent, model, what it found, what changed.

## Phase 1: Context engineering and scaffolding (ends at STOP 1)
Create these before any product code:
- Empty backend and frontend projects that build, so the tooling exists from day one. In frontend/: install Astryx, add the npm script "astryx": "node node_modules/@astryxdesign/cli/clients/cli/bin/astryx.mjs", run npm run astryx -- theme add <theme> --import, create a short frontend/CLAUDE.md, then run npx @astryxdesign/cli init --features agents --agent claude from frontend/. Its managed Astryx block lands in frontend/CLAUDE.md, which loads only when working on frontend files. Never edit inside that block.
- Root CLAUDE.md, short and specific: what the product is; that every session starts by reading docs/ai/PROGRESS.md and docs/ai/kickoff-prompt.md; links to the PRD and docs (use @ imports where useful); build, test, run and quality-gate commands; an architecture map; coding standards; the definition of done; working rules (read before editing, test first, run tests after every change, commit and push after every green step, never invent data, update PROGRESS.md).
- docs/ARCHITECTURE.md with a Mermaid diagram, and docs/API.md (endpoints, request and response JSON, error format), written before any API code.
- docs/adr/: one short ADR per real decision: single deployable, no database, the optimisation algorithm, deterministic tie-breaks, brute-force oracle testing, year-edge handling, holiday data source, and Astryx (why, beta risk, version pinning, its licence).
- docs/QA.md: the test strategy, plus a table mapping every PRD rule and edge case to the tests that will cover it.
- The subagents above.
- Skills in .claude/skills/:
  - quality-gate: delegates the run to gate-runner and reports the real results
  - log-ai-failure: adds an entry to docs/ai/AI_FAILURE_LOG.md (what the AI got wrong, how it was caught, the fix) and, when it would prevent a repeat, a new rule in CLAUDE.md
  - astryx-ui: the UI quality bar and the Astryx workflow commands; loaded for UI work and preloaded into ui-reviewer
- Hooks and settings in .claude/settings.json: format after every edit; block edits to docs/PRD.md and any .env file; install frontend dependencies at session start when they're missing; re-inject PROGRESS.md after compaction; the two subagent limits above under env; a permissions allowlist limited to the commands this project needs.
- docs/ai/AI_WORKFLOW.md (filled in as you go), docs/ai/AI_FAILURE_LOG.md, docs/ai/REVIEW_LOG.md (empty, for my notes) and docs/HUMAN_TODO.md.

STOP 1. Show me the context files, the subagent definitions and the ADRs, recommend an answer to each open question in the PRD, and list anything in the PRD you think is unclear.

## Phase 2: Planner core (ends at STOP 2)
- Implement the PRD's planning rules exactly: day classification, breaks, best plan, ties, year edges, current-year planning from tomorrow (inject a clock), free long weekends.
- The best plan must be provably optimal, so no greedy shortcuts. Use an exact method (for example, dynamic programming over candidate breaks with the leave budget) and explain the choice in its ADR.
- Build a brute-force oracle that tries every combination on small calendars, and a jqwik property test comparing it with the planner on at least 1,000 random calendars. Also test that the same inputs, with holidays in any order, always give the same plan, and that a full year with 60 leave days plans in under 1 second.
- Order of work: test-designer writes the tests → implement → edge-case-hunter attacks → fix → PIT. Aim for at least 80% mutation score on the planner package and report the real number either way.
- Whenever the oracle, a test or a reviewer catches an AI mistake, run log-ai-failure.

Exit criteria: every Phase 2 step ticked; quality-gate passes with its output shown; oracle results and mutation score shown; the branch is pushed.

STOP 2. Explain the algorithm in plain words with one worked example, and show the oracle results and the mutation score. I have to be able to explain it to the judges.

## Phase 3: API and UI
- API as in docs/API.md: the plan, the holiday list, and the .ics download (RFC 5545 all-day events, one per break). Validation and error messages exactly as in the PRD's table. Rate limit per IP, a request size limit, security headers, inputs never logged, and a health endpoint for the host.
- UI: the PRD's journey and results (summary, break list, a 12-month calendar, the .ics download), built screen by screen with the Astryx workflow and the UI quality bar. Test the main flow and run the axe check. After each screen, run ui-reviewer and fix what it finds. Checking the screens in a real browser is a LAPTOP step.
- After each feature: code-reviewer on the diff, fix, commit, push.

## Phase 4: Hardening and QA
- A full security-reviewer pass and a full ui-reviewer pass across every screen; fix the findings. No high-severity dependency issues, or each one explained.
- Run edge-case-hunter across the whole app. Every PRD acceptance item and edge case has a passing test; update the map in docs/QA.md.
- Check performance and accessibility and fix what fails. Run quality-gate.

## Phase 5: Delivery prep (ends at STOP 3)
- Dockerfile and the CI workflow. Build the Docker image to prove it works (Docker is available in the cloud VM).
- A config file for one simple host (default: Render; check its current pricing), and docs/DEPLOYMENT.md covering environments, configuration and exact deploy steps. Steps only I can do also go in HUMAN_TODO.
- README.md: problem, solution, a placeholder for the live demo link, quick start, architecture summary, limitations, dependencies, and a "For judges" table mapping each judging criterion to the files that show it. Screenshots are a LAPTOP step.
- Finish docs/ai/AI_WORKFLOW.md: for each stage (define, design, build, test, review, document), what the AI did, what I did, and the files that prove it, plus the subagent table. Note that cloud commits carry a Claude-Session trailer linking each change to the session that made it.
- docs/DEMO_SCRIPT.md: a timed 5-minute script covering the problem, a live demo, how AI helped, the key technical decisions and the QA approach.
- docs/SUBMISSION.md: a checklist of everything the organisers require, plus a script that runs a secret scan and builds the ZIP with the full git history (without node_modules or build output).

Exit criteria: every step ticked except LAPTOP steps; quality-gate passes and the Docker image builds, with output shown; the branch is pushed.

STOP 3. Summarise the cloud work and list the LAPTOP steps and my human steps, in order.

## Laptop phase (only where CLAUDE_CODE_REMOTE is not "true"; ends at STOP 4)
- Fetch the official holiday lists, build the holiday JSON files with their source URLs, and list any date you couldn't verify in HUMAN_TODO.
- Run the app locally. If a browser tool is available, check every screen at phone and desktop widths against the UI quality bar, fix what looks wrong and save screenshots for the README; otherwise list exactly what I should check.
- Run quality-gate, commit and push.

Exit criteria: holiday files committed with source URLs; quality-gate passes, with output shown; the branch is pushed.

STOP 4. Give me the remaining human steps in order: verify the holiday data, merge the branch into main, deploy, add the live link to the README, test on my phone, record the demo, build the ZIP and submit by 29 Oct.
