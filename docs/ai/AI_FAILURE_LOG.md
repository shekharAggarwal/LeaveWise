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
