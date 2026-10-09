# Frontend (React 19 + TypeScript + Vite + Astryx)

Loaded only when working on files in `frontend/`. Root rules in `../CLAUDE.md` still apply.

- UI is built **only** with Astryx (`@astryxdesign/core`, Stone theme, StyleX). No Tailwind, no other UI library.
- Before any UI work, load the `astryx-ui` skill (`.claude/skills/astryx-ui/SKILL.md`): it holds the UI quality bar
  and the Astryx workflow (`npm run astryx -- build`, `template`, `component <Name> --dense`). Never guess props.
- Lint enforces two Astryx rules: no raw `<div>`, no inline `style={{}}`. Don't disable them; fix the code.
- Astryx is beta: versions are pinned exactly in `package.json`. Upgrades go through an ADR update
  (`../docs/adr/0008-astryx-design-system.md`).
- Commands (run here): `npm run dev`, `npm test`, `npm run lint`, `npm run build`, `npm run astryx -- doctor`.
- Tests: Vitest + React Testing Library; every screen gets an axe check via `src/test/axe.ts`.
- Dates shown to users use Indian format (`Fri, 2 Oct`), via one shared formatter.
- The block below is managed by `astryx init`. Never edit inside it.

<!-- ASTRYX:START -->
Astryx v0.6.6 · 168 components
CLI: run every command as `npx astryx <cmd>` (shown below as `astryx ...`).

SETUP (once, in your app entry e.g. main.tsx) — without these, components render unstyled:
  import "@astryxdesign/core/reset.css";
  import "@astryxdesign/core/astryx.css";

WORKFLOW — start every page from a template. Never lay out a page from scratch:
1. `astryx build "<idea>"` — START HERE: names the [page] template to start from (always one: the closest match, or the app shell), two other templates, and the [block]s + [component]s for parts it lacks. No args = full playbook.
2. `astryx template <name> <path>` — scaffold that template into your project. Keep its frame, gap and padding; replace its data, copy and sections; delete sections you do not need.
3. `astryx template <Block>` for a part the template lacks; `astryx component <Name>` for props + examples before you use or change a component.
Changing a page you already have? Keep it: skip step 2 and add blocks and components inside its sections.

RULES:
- No <div> — components do all layout/spacing, page frame included.
- Frame first: the template you scaffold sets the page frame. Read `astryx docs layout` before you change it — region widths, breakpoint behavior.
- Dense data = rows (Table, List/Item), never Card-wrapped list items; Card is for standalone widgets. Status = StatusDot/Token; Badge = counts only.
- Custom styling: component props first; else the xstyle prop / StyleX tokens (@astryxdesign/core/theme/tokens.stylex). No raw hex/px.
- Tokens for every value (`astryx docs tokens`). Brand/accent belongs in the theme (`astryx theme list` / `theme add <slug>`, or `astryx theme template` for a custom one) — never override --color-* in :root.
- SELF-CHECK before you finish: re-read the file and replace any className=, style={{…}}, raw <div>/<span> layout, imported .css/@apply, or hardcoded #hex/px with the component or the xstyle prop + a token. Confirm the page kept its template's frame, gap and padding. If unsure a component/prop exists, run `astryx component <Name>` / `astryx search "<thing>"`; don't hand-roll CSS.

MORE CLI:
  search "<query>"   find any component / hook / doc / template / block / theme
  discover <words>   integrations you could add, and the ones you have
  component --list   168 components by category
  template --list    page + block recipes
  docs <topic>       authoring, browser-support, color, elevation, getting-started, icons, illustrations, internationalization, layout, migration, motion, principles, shape, spacing, styling-libraries, styling, theme, tokens, typography, working-with-ai
  docs cli           commands, API reference, integration authoring (one level at a time)
  swizzle <Name>     eject component source for deep customization
  upgrade --from <old version> --apply   run after any Astryx or integration dependency bump
<!-- ASTRYX:END -->
