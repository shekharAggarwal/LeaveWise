---
name: log-ai-failure
description: Record an AI mistake caught by a test, the oracle, a reviewer or the owner in docs/ai/AI_FAILURE_LOG.md, and add a CLAUDE.md rule when it would prevent a repeat. Use right after the mistake is confirmed and fixed.
argument-hint: "<one-line description of what the AI got wrong>"
---

Log one AI failure in LeaveWise. The caller's description: $ARGUMENTS

Only log what actually happened in this project. Don't reconstruct details you can't point to; if a detail is
unknown, write "not recorded".

1. Confirm the facts before writing, from the conversation, `git log -p` or the test output:
   - **What the AI got wrong**: the wrong code, test, doc or claim, with the file and the commit if it was committed.
   - **How it was caught**: which check (named test, the brute-force oracle with its shrunk counter-example,
     PIT, a subagent review with its name, the owner at a STOP, a tool error).
   - **The fix**: what changed, the commit hash if committed, and the regression test that now guards it.
   - **Root cause**: why it happened (for example, a guessed API, a misread PRD rule, a stale assumption about a
     tool version).
2. Append an entry to `docs/ai/AI_FAILURE_LOG.md` using the template at the top of that file. Number entries
   sequentially and date them (YYYY-MM-DD). Keep it short: facts, not narrative.
3. Decide whether a rule would prevent a repeat. Add one only if the mistake could plausibly recur and a short,
   checkable instruction would stop it (for example, "Run `astryx component <Name> --dense` before using a prop").
   Add it to the **Working rules** section of `CLAUDE.md` (or `frontend/CLAUDE.md` for frontend-only rules, outside
   the managed Astryx block), and note in the log entry which rule was added. Don't add a rule for a one-off typo.
4. If the mistake came from a subagent, add one line to the subagent table in `docs/ai/AI_WORKFLOW.md`.
5. Commit with `docs(ai): log AI failure <n>: <short summary>` together with the fix, or on its own if the fix is
   already committed.
