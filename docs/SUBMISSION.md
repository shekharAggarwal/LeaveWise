# Submission checklist

Everything the organisers ask for (from the brief, `docs/ai/kickoff-prompt.md`), where it lives, and its state.
The organisers' form (hackathon-55.lovable.app/submit) asks for repository links, a demo video link and written
answers, not a ZIP. The answers, each within the form's limit, are in [SUBMISSION_ANSWERS.md](SUBMISSION_ANSWERS.md).
Deadline: 30 Oct 2026, end of day. The owner submits on 29 Oct.

| Required | Where | State |
| --- | --- | --- |
| Product description and problem statement | `README.md` ("The problem", "The solution"), `docs/PRD.md` | done |
| A working demo link | `README.md`, "Live demo" | optional on the form; after deployment (owner, `docs/HUMAN_TODO.md`) |
| ZIP of the whole project with its full git history | `scripts/build-submission.sh` builds `dist/leavewise-submission.zip` | not asked for by the form (it takes the public repository link); build it if the organisers ask |
| Architecture | `docs/ARCHITECTURE.md`, `README.md` ("Architecture") | done |
| Stack | `README.md` ("Architecture", "Dependencies"), `docs/adr/0008-astryx-design-system.md` | done |
| Technical decisions | `docs/adr/` (8 ADRs), "Decisions and interpretations" in `docs/ai/PROGRESS.md` | done |
| Limitations | `README.md` ("Limitations") | done |
| Dependencies | `README.md` ("Dependencies", with licences) | done |
| How AI was used: workflow, context engineering, skills, agents, instructions, guardrails | `docs/ai/AI_WORKFLOW.md`, `CLAUDE.md`, `frontend/CLAUDE.md`, `.claude/agents/`, `.claude/skills/`, `.claude/settings.json`, `.claude/hooks/` | done |
| The AI-assisted QA approach | `docs/QA.md`, `docs/ai/AI_FAILURE_LOG.md`, the subagent table in `docs/ai/AI_WORKFLOW.md` | done |
| Demo video, at most 5 minutes | Script: `docs/DEMO_SCRIPT.md`; recorded by `scripts/demo-video/` (4:43) | [on Google Drive](https://drive.google.com/file/d/1B_lObLL0Qkde35j6B8jt_MahFo5-QZZn/view?usp=sharing), opens without signing in |
| No secrets in the repo or the ZIP | secretlint in `scripts/quality-gate.sh` (every commit) and in `scripts/build-submission.sh` (tracked files and the full history) | checked by the script at build time |

## Before building the ZIP

1. The laptop phase is done: real holiday files committed, screenshots in the README.
2. The branch is merged into `main`, CI is green on `main`, and the live link is in the README.
3. `scripts/quality-gate.sh` passes on `main`.

## Build the ZIP

```sh
git checkout main && git pull
cd frontend && npm ci && cd ..      # secretlint comes from the frontend's dev dependencies
scripts/build-submission.sh
```

The script refuses to run with uncommitted changes. It then:

1. scans every tracked file for secrets (secretlint, recommended rules);
2. scans every version of every file in the full history the same way, and fails if a `.env`, key or keystore file
   was ever committed;
3. clones the repository afresh (so `node_modules`, `dist` and `target` can't get in), removes the clone's remote
   (it would point at a local path), zips it with its `.git` folder, and checks that no build folder got in.

It prints the branch, the commit count, the size and the SHA-256 of `dist/leavewise-submission.zip`.

## Check the ZIP before uploading

```sh
cd "$(mktemp -d)" && unzip -q /path/to/dist/leavewise-submission.zip && cd LeaveWise
git log --oneline | wc -l          # the full history is there
git status                         # clean
scripts/quality-gate.sh            # passes from the ZIP alone
```

The gate needs Java 21, Node 22 and Playwright's Chromium (`cd frontend && npx playwright install chromium`);
it installs the frontend's dependencies itself.

## Submit

Upload the ZIP, the demo link and the video on the organisers' form, and paste the README's problem statement into
the description field if the form asks for one.
