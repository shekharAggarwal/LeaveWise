# LeaveWise

One-page web app for salaried employees in India: enter a leave balance, holiday list, weekly offs and break style,
and get the provably best plan of leave days for the year (the most days off inside breaks), a 12-month calendar
and an `.ics` file. No login, nothing stored. Hackathon entry, submitted 29 Oct 2026.

## Start of every session (and after compaction)

1. Read `docs/ai/PROGRESS.md` (imported below) and continue from the first unticked step.
2. Read `docs/ai/kickoff-prompt.md`: the owner's brief, with the phases, STOP points and non-negotiable rules.
3. `CLAUDE_CODE_REMOTE=true` means the cloud VM: skip LAPTOP steps and list them in `docs/HUMAN_TODO.md`.

@docs/ai/PROGRESS.md

## Requirements and docs

@docs/PRD.md

- Architecture and module map: `docs/ARCHITECTURE.md`. API contract: `docs/API.md`. Decisions: `docs/adr/`.
- Test strategy and the PRD-rule-to-test map: `docs/QA.md`. Owner-only steps: `docs/HUMAN_TODO.md`.
- AI practice: `docs/ai/AI_WORKFLOW.md`, `docs/ai/AI_FAILURE_LOG.md`. `docs/ai/REVIEW_LOG.md` holds only the owner's words.
- Frontend rules live in `frontend/CLAUDE.md` (loads when you work there).

## Commands

| What | Command |
| --- | --- |
| Every check (tests, PIT, lint, build, doctor, secret scan) | `scripts/quality-gate.sh` (or the `quality-gate` skill) |
| Backend tests | `cd backend && ./mvnw -B verify` |
| Planner mutation testing | `cd backend && ./mvnw -B test-compile pitest:mutationCoverage` |
| Backend format | `cd backend && ./mvnw -B spotless:apply` |
| Run the backend | `cd backend && ./mvnw spring-boot:run` (port 8080) |
| Frontend dev server | `cd frontend && npm run dev` (proxies `/api` to 8080) |
| Frontend tests, lint, build | `cd frontend && npm test && npm run lint && npm run build` |
| Astryx health | `cd frontend && npm run astryx -- doctor` |
| Real-browser check (overflow, CSP, touch targets, axe) | `cd frontend && npm run check:browser -- http://localhost:8080` (jar running with the built page) |

## Architecture map

```
backend/  Spring Boot 4.1, Java 21, Maven
  dev.leavewise.planner   pure Java, no Spring: day classification, exact DP planner, results
  dev.leavewise.holidays  bundled holiday JSON (one file per year), paste parser
  dev.leavewise.ics       RFC 5545 writer
  dev.leavewise.api       REST controllers, request/response records, validation, problem+json errors
  dev.leavewise.web       security headers, rate limit, request size limit, SPA forwarding
frontend/ React 19, TypeScript, Vite, Astryx (Stone theme), built into the Spring Boot jar
scripts/  quality-gate.sh and delivery scripts
```

## Coding standards

- Planner code has no Spring imports and no I/O. Time comes from an injected `java.time.Clock` (zone Asia/Kolkata),
  read once per request and passed down, so every rule in a request sees the same day. (AI failure 15)
- Java: records for data, immutable collections, no nulls across package boundaries, google-java-format (Spotless).
- TypeScript: `strict`, no `any`, no non-null assertions; API types in one module that mirrors `docs/API.md`.
- UI only through Astryx: the `astryx-ui` skill is the rulebook. Lint blocks raw `<div>` and inline `style`.
- User-facing copy comes from the PRD when the PRD gives it (error messages word for word). Dates: `Fri, 2 Oct`.
- Never log request bodies or any user input. Errors are RFC 9457 problem+json as in `docs/API.md`.
- Comments explain why, not what. No dead code, no TODOs without a PROGRESS.md entry.

## Definition of done (per step)

1. A test that fails without the change exists and now passes; the PRD-rule map in `docs/QA.md` is updated.
2. `scripts/quality-gate.sh` passes locally (show its summary).
3. A finished feature or screen has had its review (code-reviewer, ui-reviewer for UI) and the findings are fixed
   or logged as optional.
4. Docs touched by the change are updated (API.md, ARCHITECTURE.md, an ADR if a decision changed).
5. `docs/ai/PROGRESS.md` is ticked, then a Conventional Commit is pushed to the working branch.

## Working rules

- Read a file before editing it. Test first: write the failing test, then the code.
- Run the relevant tests after every change; run the quality gate before every commit.
- Commit and push after every green step. Only the working branch; never `main`, never force-push.
- Never invent data: holiday dates, test results, metrics, review notes, log entries. Record only what happened.
- Never skip, weaken or delete a failing test to get green. Fix the code, or stop and explain.
- `docs/PRD.md` is read-only (a hook enforces it). If a requirement looks wrong, note it for the next STOP.
- When a test, the oracle or a reviewer catches an AI mistake, run the `log-ai-failure` skill.
- Delegate to subagents (`.claude/agents/`) only for noisy, fresh-eyes or cheaper-model work; brief them tightly.
- Before pinning a dependency, check its `engines` and peer ranges against the toolchain (Node 22, Java 21, the
  pinned Vite/TypeScript). (AI failure 1)
- Run the command that proves a fact (versions, licences, flags, test results) before writing it in a doc. (AI failure 2)
- A check that couldn't run is not passed: leave its step unticked and say what blocked it. (AI failure 18)
- Check example dates in docs with a script (weekday, rule) before writing them. (AI failure 4)
- Code follows the written contract (PRD, ADR, javadoc). To change a rule, update the ADR or javadoc first, then the
  tests, then the code. (AI failure 5)
- Every result state written into a contract needs an example input that produces it; if none exists, it doesn't go
  in. (AI failure 6)
- A rule checked in both the browser and the server is one rule: same cases, same counting (characters, not UTF-16
  units), same whitespace; when one side changes, test the other with the same inputs. (AI failure 16)
- Decide routing in the container (URL patterns, servlet path), never by string-matching `getRequestURI()`; test
  path-sensitive security with a real server. (AI failure 7)
