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

## 7. Security filters keyed on the raw URI, so they could be bypassed (2026-10-10)
- What the AI got wrong: the rate-limit, size-limit and security-header filters (commit `260f86d`) decided "is this
  the API?" with `request.getRequestURI().startsWith("/api/")`. The raw URI is not decoded and keeps `;parameters`,
  while Spring still routes `/%61pi/plan` and `/api;x=1/plan` to the controllers.
- How it was caught: code-reviewer (Sonnet) on the API diff, confirmed against a running copy: 80 requests to
  `/%61pi/holidays/parse` all got 200 (the same 80 to `/api/...` got 61 x 429), and a 40 KB body to `/api;x/plan` got
  200 instead of 413.
- Fix: commit `07c1a2f` registers the limits with container URL patterns (`/api/*`), which match the decoded path,
  and the header filter uses `UrlPathHelper`. Regression test `PathBypassTest` (real server, random port); 3 of its
  4 tests fail on the old code.
- Root cause: matched on a string form of the path instead of the path the container routes; MockMvc tests can't
  see the difference.
- Rule added: CLAUDE.md, Working rules: "Decide routing in the container (URL patterns, servlet path), never by
  string-matching `getRequestURI()`; test path-sensitive security with a real server."

## 8. A lone CR in a holiday name could inject lines into the .ics file (2026-10-10)
- What the AI got wrong: `IcsWriter.escape` (commit `f4f7ac6`) escaped `\r\n` and `\n` but not a lone `\r`, and the
  plan endpoint accepted any characters in holiday names.
- How it was caught: code-reviewer, confirmed with `od` on a real `/api/plan/ics` response for the name
  `Evil\rATTACH:http://x`. test-designer's 21 `IcsWriterTest` cases had covered `\n` only.
- Fix: commit `07c1a2f` escapes every line-break form (regex `\R`) and drops control characters; the API and the
  paste parser reject names with control characters. Regression tests `IcsInjectionTest` (4, all fail on the old
  code) and `ReviewFindingsApiTest#holidayNameWithAControlCharacterIsRejected`.
- Root cause: escaping written from the common case (`\n`) instead of from the format's definition of a line break.
- Rule added: none beyond the test; RFC 5545 text now goes through one escaping function with its own tests.

## 9. Smaller API gaps from the same review (2026-10-10)
- What the AI got wrong (commit `260f86d`): an unusual `Accept` header gave a 500 and an ERROR log; the rate limiter
  read the clock twice per call and could drop tokens on a backwards step; IPv6 clients got one bucket per address;
  `/api/holidays/parse` coerced `"2027"` and `2027.9` to 2027; blank names and missing dates got misleading messages;
  name length counted UTF-16 units; `/actuator/health` returned probe groups not in docs/API.md.
- How it was caught: code-reviewer findings 3 to 8 and 10.
- Fix: commit `07c1a2f`, each with a regression test (`ReviewFindingsApiTest`, `RateLimitFilterTest`,
  `ReviewFindingsHolidaysTest`); docs/API.md updated with the new messages and the 406 row.
- Root cause: validation was strict on one endpoint (`/api/plan`) and not reused on the other; error paths outside
  the happy path weren't listed when writing the handler.
- Rule added: none: covered by the tests and the shared `PlanInputs.requireYear`.

## 10. Two test-side gaps caught by running the tests (2026-10-10)
- What the AI got wrong: (a) test-designer registered the test-only 500 endpoint with a class-level
  `@RequestMapping`, which Spring 6+ no longer maps, so `ErrorFormatTest` got a 404; (b) my first API implementation
  let Spring MVC log request and response bodies at DEBUG.
- How it was caught: the first full run of the API tests: (a) `ErrorFormatTest#unexpectedFailureIs500...` failed with
  404; (b) test-designer's `PrivacyTest` turns on DEBUG and found its marker string in the log.
- Fix: commit `260f86d`: (a) `@RestController` on the test endpoint; (b) Spring's web loggers pinned to INFO in
  `application.properties`, so turning on DEBUG elsewhere never logs input.
- Root cause: (a) an outdated Spring idiom; (b) privacy relied on the default log level instead of being enforced.
- Rule added: none: `PrivacyTest` guards (b).

## 11. UI state races and a lost keyboard Tab stop (2026-10-10)
- What the AI got wrong (commit `b02204f`): the paste handler built new rows from the rows captured at click time,
  so a paste finishing after a year switch wrote the old year's rows over the new year; the calendar remembered a
  focused date across plans, so after re-planning for another year no day had `tabindex="0"`; Enter could submit
  twice and planning was allowed before the holiday list had loaded; non-API errors showed raw text such as
  "TypeError: Failed to fetch"; the `.ics` object URL was revoked synchronously.
- How it was caught: code-reviewer (Sonnet) on the UI diff, 3 high and 5 medium findings.
- Fix: commit `3ca0132`: functional state updates, the holiday editor keyed by year with late responses dropped,
  a Tab stop that falls back to the first planned day, a re-entrancy guard and a request sequence number, one
  connection message for every non-API failure, a delayed revoke. Regression tests in
  `src/ReviewFindings.test.tsx`; 11 failed on the old code.
- Root cause: async handlers written as if the inputs couldn't change while a request was in flight.
- Rule added: frontend/CLAUDE.md: "Async UI work updates state functionally and ignores responses for inputs that
  have changed; test each async path with a deferred response."

## 12. Focus moved to an aria-hidden element, and unreadable past days (2026-10-10)
- What the AI got wrong (commit `b02204f`): after planning, focus went to an empty `aria-hidden` span, so screen
  readers announced nothing; past days used the disabled text colour while staying focusable; the free long weekend
  style was missing from the legend; "Holidays" was styled text, not a heading.
- How it was caught: ui-reviewer (Sonnet, with the `astryx-ui` skill preloaded), reading the code and the
  Playwright screenshots.
- Fix: commit `3ca0132`: the results region takes focus (`tabIndex={-1}`, labelled by its heading), past days use
  the secondary text colour, the legend lists all five marks, `Heading level={2}` for Holidays. Regression test
  `ReviewFindings.test.tsx#moves focus to the results region after planning`.
- Root cause: focus management written for the visual effect, not for what assistive technology announces.
- Rule added: none beyond the test.

## 13. The page overflowed on a 360 px phone (2026-10-10)
- What the AI got wrong (commit `b02204f`, before review): Astryx list rows don't wrap, so a break description with
  holiday names (about 495 px) and the seven weekly-off buttons (358 px) forced the whole page wider than a 360 px
  screen, cutting off both edges.
- How it was caught: my own check in the VM's Chromium (Playwright, 360 px, `scrollWidth > innerWidth`), then a
  script that measured each element's min-content width to find the two culprits. jsdom tests can't see layout.
- Fix: in `b02204f` itself: long list text goes through `Text textWrap="wrap"`, and the weekday group wraps via one
  StyleX `xstyle`. Re-checked at 360 and 1280 px, light and dark: no overflow, no console errors.
- Root cause: assumed Astryx rows wrap like plain text; layout was only tested in jsdom.
- Rule added: frontend/CLAUDE.md: "Before a UI review, load every screen at 360 px in Chromium and assert no
  horizontal overflow."


## 14. The production page broke its own Content Security Policy (2026-10-10)
- What the AI got wrong: the API sends `style-src 'self'` (commit `260f86d`), but the built page it served had an
  inline `<style>` with the Astryx cascade-layer order (added by the Astryx Vite plugin), and Astryx's popover layer
  injected another inline `<style>` with `@position-try` rules at runtime. The browser blocked both, so the layer
  order and the popover placement rules were lost. Every jsdom test passed, since jsdom enforces no CSP.
- How it was caught: my own Playwright check of the jar serving `frontend/dist`, which logged two
  `securitypolicyviolation` events (`style-src-elem inline`). The security-reviewer subagent flagged the inline
  layer `<style>` separately from reading `dist/index.html`.
- Fix: a Vite plugin in `frontend/vite.config.ts` moves the layer order to `layers.css` (first stylesheet) and emits
  Astryx's own slide rules as `astryx-layer-slide-rules.css`, linked with the id Astryx checks before injecting.
  The CSP stays strict. Re-checked in Chromium: no `<style>` elements, no CSP violations, the date picker popover
  places correctly. Regression check `frontend/scripts/check-dist-csp.mjs` runs inside `npm run build` and failed
  on a copy of the old `index.html` with five findings.
- Root cause: the CSP was written and tested on the API alone; the built page was never loaded with the real
  headers until Phase 4.
- Rule added: frontend/CLAUDE.md: "The built page must load under the production CSP with no violations: keep
  `scripts/check-dist-csp.mjs` in the build, and load the jar in Chromium after adding a component that injects styles."

## 15. A plan request at New Year midnight returned a 500 (2026-10-10)
- What the AI got wrong (commit `260f86d`): `PlanController` checked the year against one reading of the clock
  (`PlanYears.now`) and the planner read the clock again, so a valid 2026 request arriving at 23:59:59.999 IST on
  31 Dec reached the planner on 1 Jan, which threw "cannot plan a past year": a 500 instead of a plan or a 400.
- How it was caught: edge-case-hunter (Phase 4 whole-app attack), `NewYearMidnightApiTest` with a clock that steps
  over midnight between readings.
- Fix: `Planner.plan(request, today)`; the controller reads the clock once per request and passes that day to both
  the year check and the planner. The test now passes.
- Root cause: "time comes from an injected Clock" was followed, but nothing said to read it only once per request.
- Rule added: CLAUDE.md coding standards: the clock is "read once per request and passed down".

## 16. Browser and server checks of the same input drifted apart (2026-10-10)
- What the AI got wrong:
  - the browser added a holiday name with a tab, which the server refuses (commit `b02204f`, `HolidayEditor.addOne`);
  - the paste parser didn't treat a no-break space after the date as whitespace (commit `260f86d`);
  - the 8,000-character paste limit counted UTF-16 units, while names counted characters (commit `260f86d`);
  - the bad-date example year came from the system clock, not the app clock (commit `07c1a2f`).
  Also changed after the same report, as an interpretation rather than a bug: `leaveDays` written `60.0` or `6e1`
  is now the whole number 60 on the server (docs/API.md). The browser's text field still asks for digits only.
- How it was caught: edge-case-hunter, `EdgeCaseHuntApiTest` (4 tests) and `EdgeCaseHunt.test.tsx` (1 test); all 5
  failed on the old code.
- Fix: a shared-rule `hasControlCharacter` in `frontend/src/lib/validation.ts` (unit tests list the server's cases),
  `[\s\p{Zs}]` separators and a loop-based strip in `HolidayPasteParser`, `codePointCount` for the paste limit,
  `PlanYears` passed into the holiday check.
- Root cause: each side's check was written from its own part of docs/API.md, not as one rule with shared cases.
- Rule added: CLAUDE.md working rules: "A rule checked in both the browser and the server is one rule: same cases,
  same counting, same whitespace."

## 17. Small touch targets and calendar names that didn't match their visible text (2026-10-10)
- What the AI got wrong (commit `b02204f`): the "Paste a list" trigger was 18 px tall; the weekday buttons wrapped at
  360 px with Sun alone on a second row; each calendar day showed "3 W" while its spoken name was "Sun, 3 Jan: weekly
  off", so the visible text wasn't part of the name (WCAG 2.5.3) on 119 day buttons.
- How it was caught: ui-reviewer (Phase 4 full pass, Playwright measurements) for the first two; the new
  `frontend/scripts/browser-check.mjs` (axe in Chromium, `label-content-name-mismatch`) for the third. The jsdom axe
  run in `App.test` did not report it.
- Fix: padded trigger (28 px), a 4-column weekday grid under 480 px (measured: 7 buttons at 75x32 px in 2 rows),
  the code letter drawn as generated content like an icon, so a day's text is its day number. Regression test
  `Phase4Review.test.tsx` (failed on the old code); the browser check now runs in the quality gate.
- Root cause: touch-target size and label-in-name were never measured in a real browser.
- Rule added: frontend/CLAUDE.md: run `npm run check:browser` before a UI review.

## 18. A dependency check was ticked before the backend had been scanned (2026-10-10)
- What the AI got wrong (commit `05a4dde`): PROGRESS ticked "No high-severity dependency issues (or each
  explained)" after `npm audit` passed, while noting that the backend scan couldn't run in the VM (api.osv.dev
  blocked). The backend in fact carried Tomcat 11.0.24 with 3 critical advisories and Jackson 3.1.5 with 7.
- How it was caught: the osv-scanner job in the first CI run (`.github/workflows/ci.yml`, run 1 on commit
  `f16f040`), which listed GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6 and GHSA-h3x4-894j-xpx5 for Tomcat and seven
  GHSAs for Jackson, each with its fixed version.
- Fix: `pom.xml` overrides `tomcat.version` (11.0.26) and `jackson-bom.version` (3.1.7) until Spring Boot manages
  them; the jar now holds those versions and all 312 backend tests pass. The CI scan guards it on every push.
- Root cause: "the scan couldn't run here" was treated as "nothing to report" instead of "not done".
- Rule added: CLAUDE.md working rules: "A check that couldn't run is not passed: leave its step unticked and say
  what blocked it."

## 19. The summary's days off per leave rounded differently from the server (2026-10-10)
- What the AI got wrong (uncommitted redesign of `frontend/src/features/results/Results.tsx`): the new "Days off per
  leave" figure used `(totalDaysOff / leavesUsed).toFixed(1)`, while the server rounds half up
  (`LeaveBreak.daysOffPerLeave`: `Math.round(days * 10.0 / leaves) / 10.0`). 43 days over 20 leaves showed 2.1 on
  the page against 2.2 from the server.
- How it was caught: code-reviewer subagent on the redesign diff (checked in node).
- Fix: `frontend/src/lib/figures.ts` `daysOffPerLeave` uses the server's formula; `figures.test.ts` (43 / 20 is 2.2)
  failed before the helper existed. Committed with the redesign.
- Root cause: a figure the server already computes per break was re-derived in the browser without reading the
  server's rounding.
- Rule added: none; CLAUDE.md already has it (AI failure 16: a rule in both the browser and the server is one rule,
  same counting). It was not applied to a new derived number.

## 20. The phone break list followed the canvas and dropped a PRD Must (2026-10-10)
- What the AI got wrong (commit `86b24d6`, `frontend/src/features/results/PhoneResults.tsx`): the phone break rows
  copied the canvas (dates, days off, leave dates, holidays) and left out "days off per leave", which the PRD's
  Results table lists as a Must for every break. docs/API.md even recorded the omission as copy.
- How it was caught: code-reviewer subagent on `86b24d6`.
- Fix: each phone row ends with "{ratio} days off per leave"; `PhoneFlow.test#each break row keeps its days off per
  leave` failed before the fix. Committed with the review fixes.
- Root cause: "match the canvas end to end" was applied field by field without checking each screen against the PRD's
  Must list; the canvas is a visual spec, the PRD is the contract.
- Rule added: frontend/CLAUDE.md: when a design and the PRD disagree, the PRD wins; check each results screen against
  the PRD's Must fields.
