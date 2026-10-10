# How AI is used in LeaveWise

Filled in as the work happens; only real events are recorded. Cloud commits carry a `Claude-Session:` trailer that
links each change to the Claude Code session that made it.

## Roles

| Who | Does |
| --- | --- |
| Owner (Shekhar) | Chose the idea, scope and name; worked out the PRD with Claude in a claude.ai chat; wrote the build brief; reviews at each STOP; does the human steps in `docs/HUMAN_TODO.md`. |
| Main Claude Code session (Opus, high effort) | Lead engineer: context files, architecture, code, integration, and every decision recorded in an ADR or `docs/ai/PROGRESS.md`. |
| Subagents (`.claude/agents/`) | Narrow jobs on the cheapest model that can do them, with tight briefs and at most 10 findings per report. |

## Context engineering

| Layer | Files | Purpose |
| --- | --- | --- |
| Requirements | `docs/PRD.md` (read-only, hook-enforced) | The exact rules, messages and edge cases. |
| Brief and progress | `docs/ai/kickoff-prompt.md`, `docs/ai/PROGRESS.md` | What to build, in which order, and where the work stands. |
| Standing instructions | `CLAUDE.md` (imports PROGRESS and the PRD), `frontend/CLAUDE.md` (with the managed Astryx block) | Commands, architecture map, standards, definition of done, working rules. |
| Architecture | `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/adr/` | Written before the code, so the code follows the design and not the reverse. |
| QA | `docs/QA.md` | Every PRD rule and edge case mapped to its tests, with real status. |
| Learning | `docs/ai/AI_FAILURE_LOG.md` | Mistakes, how they were caught, and the rules added to prevent repeats. |

## Guardrails

| Guardrail | Where |
| --- | --- |
| PRD and `.env` files can't be edited (file tools and shell writes) | `.claude/hooks/guard-protected-files.mjs`, `permissions.deny` |
| Every edited file is formatted (Prettier, google-java-format) | `.claude/hooks/format-on-edit.mjs` |
| Frontend dependencies installed at session start; PROGRESS re-injected after compaction | `.claude/hooks/session-start.mjs` |
| At most 3 concurrent subagents; subagents can't spawn subagents | `env` in `.claude/settings.json` |
| Narrow permissions allowlist; force-push, pushes to `main` and branch deletion denied | `.claude/settings.json` |
| Astryx rules (no raw `<div>`, no inline styles) fail lint | `frontend/eslint.config.js` |
| One gate for every check, locally and in CI | `scripts/quality-gate.sh` |

## Skills

| Skill | Use |
| --- | --- |
| `quality-gate` | Runs the gate inside the gate-runner subagent (Haiku) and returns only the summary and failures. |
| `log-ai-failure` | Records an AI mistake and, when useful, a new rule in `CLAUDE.md`. |
| `astryx-ui` | The UI quality bar and Astryx workflow; loaded for UI work and preloaded into ui-reviewer. |

## Subagents

| Agent | Model, effort | What it found | What changed |
| --- | --- | --- | --- |
| Explore | Haiku | not run yet | |
| gate-runner | Haiku | Phase 2: ran the full gate via the `quality-gate` skill; returned the summary only (11 checks PASS). | none needed |
| test-designer | Sonnet, high | Phase 3: two parallel runs wrote 57 holiday/.ics unit tests and 78 API/web MockMvc tests from docs/API.md before the code; one test-config bug (Spring 6 mapping, AI failure 10). | Fixed in the test config; tests otherwise unchanged. |
| test-designer | Sonnet, high | Phase 2: wrote 135 planner tests and the brute-force oracle before the code (130 failing on stubs, 5 oracle-only passing). Hit its 40-turn limit once and was resumed to report. Found that the `LEAVES_UNUSED` note was unreachable; its date-level property caught an extra year-edge rule in the first implementation. | Note removed from API and planner; edge rule removed (AI failures 5 and 6). |
| edge-case-hunter | Opus, high | Phase 2: no planner defect found (3,000-try year-edge fuzz against the date oracle, code reading). Killed 2 PIT survivors with 6 new tests; showed 2 survivors equivalent and 2 a safety bound. Raised one interpretation for the owner (year edge on 30/31 Dec). | `BreakOptimizerContractTest`, `LeaveBreakTest` added; PIT 178 → 180 of 184. |
| edge-case-hunter | Opus, high | Phase 4 whole app: 6 failing tests (a 500 at New Year midnight from two clock readings; browser accepting control characters; no-break space in pastes; paste limit in UTF-16 units; system-clock example year; `60.0` leave days) and 3 passing tests for PRD edge cases that had only backend coverage. | All 6 fixed (AI failures 15 and 16); its tests kept as written. |
| code-reviewer | Sonnet, high | Phase 1 docs vs PRD (run as a Sonnet general-purpose agent with the code-reviewer brief, because project agents load only in a new session): 10 findings, 0 high, 4 medium, 6 low. Verdict: changes needed. | All 10 fixed in `docs/API.md`, ADR 0003, ADR 0005 and `docs/QA.md`; finding 2 logged as AI failure 4. |
| code-reviewer | Sonnet, high | Phase 3 API diff: 10 findings, 2 high (filter bypass via `/%61pi` and `/api;x`, `.ics` line injection via lone CR), 4 medium, 4 low; ran its own copy of the app with curl to confirm 4 of them. Verdict: changes needed. | All fixed in `07c1a2f` with regression tests that fail on the old code (AI failures 7 to 9); `instance` field and `/api/%zz` container 400 documented as known. |
| code-reviewer | Sonnet, high | Phase 3 UI diff: 10 findings, 3 high (paste race across years, lost Tab stop, double submit and planning before holidays load), 5 medium, 2 low, including tests that couldn't fail as written. | All high and medium fixed in `3ca0132` with 11 regression tests and 13 unit tests (AI failure 11); date tests now run in America/Los_Angeles. |
| security-reviewer | Sonnet, medium | Phase 4 whole app, with its own running jar and curl probes: 0 critical or high, 2 medium (inline `<style>` against `style-src 'self'`; rate limit trusts `X-Forwarded-For` from private peers), 7 low. | CSP fixed in `05a4dde` (AI failure 14); duplicate JSON keys rejected and `npm ci --ignore-scripts`; proxy trust goes into `docs/DEPLOYMENT.md`; the rest accepted and listed for STOP 3 in PROGRESS. |
| ui-reviewer | Sonnet, medium | Phase 3 whole page, code plus Playwright screenshots: 10 findings, 1 high (focus to an aria-hidden span), 5 medium (raw error text, stale results, disabled-colour past days, legend gap, headings), 4 low. | All high and medium fixed in `3ca0132` (AI failure 12); tokens and grid offsets fixed; Banner for the weekly-off error kept (no `status` prop on ToggleButtonGroup). |
| ui-reviewer | Sonnet, medium | Phase 4 every screen state at 360 and 1280 px, light and dark, with faked 500 and 429 responses: 0 blockers, 3 should-fix (16 px calendar icon, 18 px paste trigger, Sun alone on a row), 4 optional. | Trigger and weekday grid fixed (AI failure 17); calendar icon kept under WCAG 2.5.8's equivalent-control exception; errors clear on edit and a stale hint by the button added; pasted-text re-submit kept (names dedupe); text-field focus ring is Astryx's own. |

## Lifecycle

What the AI did and what the owner did at each stage, with the files that show it. Counts are from the files named.

| Stage | AI (Claude Code and subagents) | Owner | Evidence |
| --- | --- | --- | --- |
| Define | Copied the owner's PRD page word for word (revision 13) and transcribed its build-plan diagram. Listed every interpretation it had to make and recommended answers to the PRD's open questions for STOP 1. | Chose the idea and scope; worked out the PRD with Claude in a claude.ai chat; wrote the build brief with its phases, STOP points and rules. | `docs/PRD.md`, `docs/ai/kickoff-prompt.md`, "Decisions and interpretations" in `docs/ai/PROGRESS.md` |
| Design | Before any code: architecture, API contract, 8 ADRs, and the PRD-rule-to-test map. code-reviewer checked the docs against the PRD (10 findings, all fixed). | Reviewed at STOP 1 and approved ("push the changes on git, you are allowed to do everything"). | `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/adr/`, `docs/QA.md`, `docs/ai/REVIEW_LOG.md` |
| Build | test-designer wrote tests from the PRD and API contract before the code (135 planner tests and the brute-force oracle; 135 holiday, `.ics` and API tests). The main session wrote the code to pass them, and the UI from Astryx templates (`astryx build`, `template`, `component`). Hooks formatted every edit and blocked edits to the PRD. | Gave the go-ahead at STOP 2 ("looks good to me continue"). | `backend/`, `frontend/`, `.claude/hooks/`, git history |
| Test | Brute-force oracle (1,500 random calendars, all equal), PIT (183 of 187 mutants killed), edge-case-hunter twice (Phase 2 planner, Phase 4 whole app: 6 failing tests, all fixed), a real-browser check in the gate, and CI on GitHub (gate, dependency scan, Docker build). | Still to do: the real phone check and the PRD's 3-colleague check (`docs/HUMAN_TODO.md`). | `docs/QA.md`, `backend/src/test/`, `frontend/src/**/*.test.tsx`, `frontend/scripts/browser-check.mjs`, `.github/workflows/ci.yml` |
| Review | code-reviewer 3 times (docs, API, UI), ui-reviewer twice, security-reviewer once; every finding fixed or answered in the table above. Each mistake a check caught went into the failure log; 13 of the 18 added a rule to `CLAUDE.md` or `frontend/CLAUDE.md`. | Reviews at each STOP; the words are kept as written. | Subagent table above, `docs/ai/AI_FAILURE_LOG.md` (18 entries), `CLAUDE.md` working rules |
| Document and deliver | README, deployment guide, Dockerfile (built and smoke-tested in the cloud VM), Render config, and this file. Owner-only steps written out with exact instructions. | Still to do: merge, deploy, live link, demo and submission (`docs/HUMAN_TODO.md`). | `README.md`, `docs/DEPLOYMENT.md`, `Dockerfile`, `render.yaml`, `docs/HUMAN_TODO.md` |

## Traceability

Every commit made in the cloud ends with a `Claude-Session:` trailer that links the change to the Claude Code
session that made it, next to the `Co-Authored-By` line. To see them:

```sh
git log --format='%h %s%n  %(trailers:key=Claude-Session,valueonly)'
```

On 2026-10-10 all 31 commits on the branch carried it. Alongside the trailers, `docs/ai/PROGRESS.md` records each
step as it was ticked, and `docs/ai/REVIEW_LOG.md` holds only the owner's own words.
