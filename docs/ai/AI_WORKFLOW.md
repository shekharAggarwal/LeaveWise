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
| code-reviewer | Sonnet, high | Phase 1 docs vs PRD (run as a Sonnet general-purpose agent with the code-reviewer brief, because project agents load only in a new session): 10 findings, 0 high, 4 medium, 6 low. Verdict: changes needed. | All 10 fixed in `docs/API.md`, ADR 0003, ADR 0005 and `docs/QA.md`; finding 2 logged as AI failure 4. |
| code-reviewer | Sonnet, high | Phase 3 API diff: 10 findings, 2 high (filter bypass via `/%61pi` and `/api;x`, `.ics` line injection via lone CR), 4 medium, 4 low; ran its own copy of the app with curl to confirm 4 of them. Verdict: changes needed. | All fixed in `07c1a2f` with regression tests that fail on the old code (AI failures 7 to 9); `instance` field and `/api/%zz` container 400 documented as known. |
| code-reviewer | Sonnet, high | Phase 3 UI diff: 10 findings, 3 high (paste race across years, lost Tab stop, double submit and planning before holidays load), 5 medium, 2 low, including tests that couldn't fail as written. | All high and medium fixed in `3ca0132` with 11 regression tests and 13 unit tests (AI failure 11); date tests now run in America/Los_Angeles. |
| security-reviewer | Sonnet, medium | not run yet | |
| ui-reviewer | Sonnet, medium | Phase 3 whole page, code plus Playwright screenshots: 10 findings, 1 high (focus to an aria-hidden span), 5 medium (raw error text, stale results, disabled-colour past days, legend gap, headings), 4 low. | All high and medium fixed in `3ca0132` (AI failure 12); tokens and grid offsets fixed; Banner for the weekly-off error kept (no `status` prop on ToggleButtonGroup). |

## Lifecycle

Filled in at STOP 3: for each stage (define, design, build, test, review, document), what the AI did, what the
owner did, and the files that prove it.
