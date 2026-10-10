# 0008. Astryx 0.6.6 with the Stone theme, pinned exactly

Status: Accepted (2026-10-09); amended 2026-10-10 (LeaveWise theme, see Amendment)

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

## Amendment (2026-10-10): the LeaveWise theme

The owner approved a redesign drawn on a Claude Design canvas ("LeaveWise UI redesign": indigo leave days, marigold
holidays, warm-grey weekly offs) and, asked whether to keep Stone's palette unchanged as the brief says, chose a
custom theme (docs/ai/REVIEW_LOG.md, 2026-10-10). So:

- `frontend/src/theme/leavewise.source.ts` is a `defineTheme` with `extends: stoneTheme`: Stone's typography,
  scales, icons and every token are inherited; only the accent family (`--color-accent`, `-accent-muted`,
  `text-accent`, `icon-accent`, `on-accent`) is replaced by an indigo, in light and dark. One component override:
  a pressed `toggle-button` (weekly offs) is drawn in ink (`--color-background-inverted`) with tighter inline
  padding, so seven weekday buttons fit one row at 360 px.
- It is compiled with `astryx theme build` into `leavewise.css`, `.js` and `.d.ts` beside the source (no runtime
  style injection, so the production CSP holds; AI failure 14). The built files are committed, excluded from
  Prettier, and `astryx theme build src/theme/leavewise.source.ts --check` confirms they match the source.
- Contrast of the new accent is checked by the real-browser axe run in light and dark (`npm run check:browser`).
- Calendar day colours stay on Stone's categorical tokens (orange for holidays, muted for weekly offs) and the new
  accent for leave days, always with a letter code, so colour is never the only signal.

### Second amendment (2026-10-10): light only, the canvas as the spec

After the first build the owner wrote "nothing is matching we built in artifact so can you do only light theme work
and match the UI end to end" (docs/ai/REVIEW_LOG.md). So the app renders in light mode only (`<Theme mode="light">`;
this overrides the brief's "works in light and dark mode"), and the canvas's palette and component shapes go into
`leavewise.source.ts` as tokens and component overrides, so components stay free of raw colours. The theme's dark
values are used only by the summary card, which is a nested dark `<Theme>`, and are set to that card's canvas colours.
