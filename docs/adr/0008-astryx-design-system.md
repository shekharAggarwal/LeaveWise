# 0008. Astryx 0.6.6 with the Stone theme, pinned exactly

Status: Accepted (2026-10-09)

## Context

The owner chose the Astryx design system (Meta's `@astryxdesign/*` packages, source at github.com/facebook/astryx)
with the Stone theme, and a UI that must not look AI-generated. Astryx is in beta: 0.x versions, with several
canary builds a day on 2026-10-09.

## Decision

- Use `@astryxdesign/core`, `@astryxdesign/theme-stone`, `@astryxdesign/build` (Vite plugin) and
  `@astryxdesign/cli`, all pinned **exactly** at `0.6.6`, the npm `latest` tag on 2026-10-09, with
  `@stylexjs/stylex` and its plugins pinned at `0.19.1`. Every other frontend dependency is pinned exactly too, and
  `package-lock.json` is committed.
- Set up as the official `apps/example-vite`: `astryxStylex()` before `react()` in `vite.config.ts`, the reset and
  theme CSS imported in `main.tsx`, `<Theme theme={stoneTheme}>` at the root.
- Import the theme from the pinned package (`@astryxdesign/theme-stone/built` and `/theme.css`) instead of keeping a
  copy. `astryx theme add stone` was run and its copy was byte-identical to the package source apart from the
  licence header; importing the package keeps the palette unchanged by construction. The brief's
  `theme add <theme> --import` flag doesn't exist in CLI 0.6.6, so `package.json` declares `"astryx": {"theme": "stone"}`
  instead, which is what `astryx doctor` checks.
- Agent rules come from `astryx init --features agents --agent claude`, a managed block in `frontend/CLAUDE.md`
  that is never edited by hand. The workflow (`build` → `template` → `component --dense`) is in the `astryx-ui`
  skill.
- Lint enforces two Astryx rules (no raw `<div>`, no inline `style`); `astryx doctor` runs in the quality gate.
- Under Vitest, StyleX's Babel plugin compiles product styles instead of the Astryx Vite plugin, because the
  unplugin-based plugin kept file handles open and every test run took 10 extra seconds to exit. Astryx components
  come prebuilt from the package, so tests still render the real components.

## Licence

All Astryx packages and StyleX declare `"license": "MIT"` in their `package.json` (checked at 0.6.6 and 0.19.1).
`@astryxdesign/cli`, `/build` and `/theme-stone` also ship a `LICENSE` file ("MIT License, Copyright (c) 2026 Meta
Platforms, Inc."); `@astryxdesign/core` and `@stylexjs/stylex` ship none in the npm package, so the MIT text from the
other packages is the reference. MIT allows commercial use and bundling as long as the notice is kept; the README's
dependency section credits Astryx and StyleX under MIT.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Breaking changes between 0.x releases | Exact pins and a committed lockfile; upgrade only on purpose, then run `npm run astryx -- upgrade --from 0.6.6 --apply` and the full quality gate. |
| A component is missing or buggy | Check `astryx search` and `astryx component <Name>` first; report with `astryx gap-report`; compose existing components before any custom StyleX. |
| Docs out of step with the installed version | The CLI serves docs for the installed version (`--dense` reads), so agents never rely on web docs or memory. |
| The project may outlive the beta | The UI uses only public subpath exports (`@astryxdesign/core/Button`, ...), which keeps a future migration mechanical. |
