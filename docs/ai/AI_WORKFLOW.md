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
| gate-runner | Haiku | not run yet | |
| test-designer | Sonnet, high | not run yet | |
| edge-case-hunter | Opus, high | not run yet | |
| code-reviewer | Sonnet, high | Phase 1 docs vs PRD (run as a Sonnet general-purpose agent with the code-reviewer brief, because project agents load only in a new session): 10 findings, 0 high, 4 medium, 6 low. Verdict: changes needed. | All 10 fixed in `docs/API.md`, ADR 0003, ADR 0005 and `docs/QA.md`; finding 2 logged as AI failure 4. |
| security-reviewer | Sonnet, medium | not run yet | |
| ui-reviewer | Sonnet, medium | not run yet | |

## Lifecycle

Filled in at STOP 3: for each stage (define, design, build, test, review, document), what the AI did, what the
owner did, and the files that prove it.
