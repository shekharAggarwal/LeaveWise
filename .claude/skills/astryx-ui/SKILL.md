---
name: astryx-ui
description: The LeaveWise UI quality bar and the Astryx workflow (build, template, component --dense). Load before creating or changing any screen or component in frontend/, and for UI reviews.
paths:
  - "frontend/src/**"
---

# LeaveWise UI: quality bar and Astryx workflow

The UI must look designed, not generated. Astryx (`@astryxdesign/core` 0.6.6, LeaveWise theme on Stone) is the only UI library.
The managed block in `frontend/CLAUDE.md` holds Astryx's own rules; this skill adds the project's bar on top.

## Workflow for every screen (run in `frontend/`)

1. `npm run astryx -- build "<screen idea>"` names the page template to start from, plus blocks and components.
2. `npm run astryx -- template <name> <path>` scaffolds it. Keep its frame, gap and padding; replace data, copy and
   sections; delete sections you don't need.
3. `npm run astryx -- template <Block>` for a missing part.
4. `npm run astryx -- component <Name> --dense` for **every** component before you use or change it. Never guess a
   prop; if unsure it exists, `npm run astryx -- search "<thing>"`.
5. `npm run astryx -- docs tokens` (spacing, colour, radius) and `docs layout` before changing a frame.
6. After the screen: `npm run lint && npm test && npm run astryx -- doctor`, then the ui-reviewer subagent.

## Astryx rules (enforced by lint where possible)

- Start every page from a template. No raw `<div>` (lint error), no inline `style={{}}` (lint error), no
  `className`, no imported `.css` for components, no hex colours or px values.
- Tokens instead of magic numbers. Custom styling only through StyleX `xstyle`, and only when no component prop does
  the job. Never override `--color-*` in `:root`.
- Dense data as rows (Table, List/Item), not cards. Status with StatusDot/Token; Badge for counts only.

## Quality bar (from the owner's brief)

- One theme: the LeaveWise theme (`src/theme/leavewise.ts`), Stone with an indigo accent (owner's choice, ADR 0008
  amendment). Change colours only there, then rebuild it with `astryx theme build`.
- Colour carries meaning (leave days, holidays, weekly offs, breaks) and is **always paired with a text label**.
  The PRD repeats it: "never colour alone".
- No gradients, glows, emoji, decorative illustrations, stock photos, fake stats or testimonials, or lorem ipsum.
- Copy is short and specific, numbers first ("12 leaves, 41 days off"). Never "unlock", "seamless", "supercharge",
  "effortless", "elevate", "magic" or similar filler.
- Dates in Indian format: `Fri, 2 Oct` (with the year only when it differs from the plan year, `Fri, 1 Jan 2027`).
  Use the shared formatter in `frontend/src/lib/`, never ad-hoc `toLocaleDateString`.
- One primary action per screen. On the planner it is **Plan my leaves** (PRD wording).
- Real empty, loading and error states; error text from the PRD word for word, saying what to do next.
- Visible keyboard focus; everything usable by keyboard; respect `prefers-reduced-motion`.
- Light mode only (owner, 2026-10-10: "do only light theme work"; overrides the brief's "works in light and dark").
  Works from 360 px wide (PRD: phone first) up to desktop.
- The Claude Design canvas "LeaveWise UI redesign" is the visual spec: colours, sizes, copy, order of inputs, the
  phone screens and the holidays sheet. Deviate only where the PRD or WCAG requires it, and say so.

## Product specifics (PRD)

- Journey on one page: year and leave days → holiday list (untick, add, paste) → weekly offs and break style →
  **Plan my leaves** → summary, break list, 12-month calendar → `.ics` download.
- Results answer one question first: how many days off the leaves buy. Summary example:
  "12 leaves → 41 days off across 9 breaks", plus unused leaves.
- Year calendar: all 12 months, weekly offs, holidays, leave days and breaks marked by colour and a label; tapping a
  day says what it is.
- The holiday list shows its source and reminds users to check their company's official list.

## Accessibility checks in tests

Every screen state has a Vitest test that renders it and calls `expectNoAxeViolations(container)` from
`src/test/axe.ts`. Query by role and visible text, which also proves the labels exist.
