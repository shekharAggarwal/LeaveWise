---
name: quality-gate
description: Run every LeaveWise check (backend tests, PIT, frontend lint/tests/build, astryx doctor, secret scan) via the gate-runner subagent and report the real results. Use before every commit and at every STOP.
argument-hint: "[--only backend|frontend|scan]"
context: fork
agent: gate-runner
background: false
---

Run the LeaveWise quality gate from the repository root and report what really happened.

Arguments from the caller (may be empty): $ARGUMENTS

1. Run `scripts/quality-gate.sh $ARGUMENTS 2>&1 | tee /tmp/quality-gate.log`. Allow it up to 15 minutes.
2. Record the exit code exactly.
3. Copy the final `== Quality gate summary ==` block verbatim.
4. For each failed check, copy the lines from its `== <check> ==` section that show why it failed (first error,
   failing test names and assertion messages, scan findings), at most 40 lines each, without paraphrasing.

Reply in exactly this shape and nothing else:

```
Exit code: <n>
<summary block, verbatim>
Failures:
- <check>: <verbatim lines>
```

Write `Failures: none` when everything passed. Never describe a check as passing unless the summary says so.
