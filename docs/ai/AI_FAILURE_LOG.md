# AI failure log

What the AI got wrong in this project, how it was caught, and what changed. Entries are added with the
`log-ai-failure` skill and record only what actually happened. When a rule would prevent a repeat, it goes into
`CLAUDE.md` and the entry names it.

Template:

```
## <n>. <short title> (YYYY-MM-DD)
- What the AI got wrong:
- How it was caught:
- Fix: (commit, regression test)
- Root cause:
- Rule added: (CLAUDE.md line, or "none: one-off")
```

## 1. Pinned a jsdom version the runtime can't run (2026-10-09)
- What the AI got wrong: while scaffolding the frontend it pinned `jsdom` at 30.1.2, the npm `latest`, without
  checking its `engines` field. jsdom 30 needs Node `^22.22.2`; the cloud VM has Node 22.22.0.
- How it was caught: `npm install` printed `EBADENGINE` warnings for jsdom 30 and two of its dependencies.
- Fix: pinned `jsdom` at 29.1.1 (`engines: ^22.13.0`) before the scaffold commit (`cc5023f`); the reinstall was clean.
- Root cause: chose versions by "latest" alone instead of checking each package's `engines` and peer ranges against
  the project's toolchain.
- Rule added: CLAUDE.md, Working rules: "Before pinning a dependency, check its `engines` and peer ranges against
  the toolchain (Node 22, Java 21, the pinned Vite/TypeScript)."

## 2. Wrote a licence claim before checking it (2026-10-09)
- What the AI got wrong: the first draft of ADR 0008 said the Astryx licences were "checked in each package's
  `package.json` and `LICENSE` file". `@astryxdesign/core` and `@stylexjs/stylex` ship no `LICENSE` file.
- How it was caught: the verification command run straight after writing the ADR (`head .../core/LICENSE`) failed
  with "No such file or directory".
- Fix: ADR 0008's Licence section now says which packages ship a `LICENSE` file and which only declare MIT in
  `package.json`. Corrected before the ADRs were committed.
- Root cause: wrote the factual claim first and verified second.
- Rule added: CLAUDE.md, Working rules: "Run the command that proves a fact (versions, licences, flags, test results)
  before writing it in a doc."

## 3. Edit-guard hook would have blocked harmless reads (2026-10-09)
- What the AI got wrong: the first Bash rule in `.claude/hooks/guard-protected-files.mjs` denied any command that
  mentioned the PRD path and contained `>`, so a read such as `grep ... <PRD path> 2>/dev/null` would have been blocked.
- How it was caught: self-review of the hook against example commands before enabling it in `.claude/settings.json`.
- Fix: the rule now matches only writes aimed at the protected path (redirection onto it, `tee`, `sed -i`, `rm`,
  `mv`, `cp`, `git checkout/restore`). The example commands became an automated test,
  `.claude/hooks/guard-protected-files.test.mjs` (16 cases), which runs in `scripts/quality-gate.sh`.
- Root cause: a broad pattern written for the deny case without the allow cases next to it.
- Rule added: none; the automated hook test now guards it. A side effect seen afterwards: the live hook also denies
  a shell command whose *text* contains such a write (for example a heredoc that writes test strings), so files that
  contain those strings are written with the file tools instead.

## 4. API example contradicted the PRD's free long weekend rule (2026-10-09)
- What the AI got wrong: the `freeLongWeekends` example in `docs/API.md` (commit `af1a9c6`) showed Thu 19 Aug to
  Sun 22 Aug 2027 as a free long weekend with a holiday only on the Thursday. Friday was a working day, so that run
  needs a leave and is not "3 or more days off in a row that need no leave" (PRD, Planning rules).
- How it was caught: the Phase 1 document review (code-reviewer brief, run on a Sonnet general-purpose agent).
- Fix: the example now uses a Friday holiday, Fri 27 to Sun 29 Aug (weekdays checked with a script).
- Root cause: example data typed by hand without checking weekdays against the rule it illustrates.
- Rule added: CLAUDE.md, Working rules: "Check example dates in docs with a script (weekday, rule) before writing them."

## 5. Planner added a year-edge rule that no document asked for (2026-10-09)
- What the AI got wrong: the first `PlanningCalendar.of` implementation stopped the leading year edge at today, so a
  next-year plan made on 30 Dec didn't count 30 Dec as part of a break crossing 1 January. ADR 0006 and the
  `PlanningCalendar` javadoc say the whole run of days off before 1 January counts; "not today" applies only to the
  current year. The wrong version was never committed.
- How it was caught: test-designer's `PlannerProperties#matchesBruteForceForAFullYearWithUpToTwoLeaves` (its own
  date-level brute force), counter-example: today 2027-12-30, plan 2028, 1 leave, no weekly offs, holidays
  2027-12-30, 2027-12-31, 2028-01-01. Expected 4 days in breaks, got 3.
- Fix: removed the extra condition before commit `d49127e`; the same property guards it.
- Root cause: the implementation "improved" the spec on the fly instead of following the written contract.
- Rule added: CLAUDE.md, Working rules: "Code follows the written contract (PRD, ADR, javadoc). To change a rule,
  update the ADR or javadoc first, then the tests, then the code."

## 6. Specified a note that can never occur (2026-10-09)
- What the AI got wrong: `docs/API.md` (commits `af1a9c6`, `fc342fa`) defined a `LEAVES_UNUSED` note for "some leaves
  would not make any break longer". With at least one break, a spare leave on the untaken working day next to a
  break always makes it longer, so an optimal plan has spare leaves only when every working day is a leave
  (`LEAVES_LEFT_OVER`); with no break the note is `NO_BREAK_POSSIBLE`.
- How it was caught: test-designer, while writing `PlannerProperties#notesFollowTheRules`, reported it as unreachable.
- Fix: removed from `Note.Code`, the planner and `docs/API.md` in `d49127e`; `notesFollowTheRules` checks that spare
  leaves with a break always mean every working day in the window is a leave.
- Root cause: output states were listed from intuition without checking each one is reachable under the optimiser.
- Rule added: CLAUDE.md, Working rules: "Every result state written into a contract needs an example input that
  produces it; if none exists, it doesn't go in."

