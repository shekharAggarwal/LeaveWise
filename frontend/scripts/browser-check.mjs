// Real-browser checks that jsdom can't do, run against a running LeaveWise server (the jar serving
// the built page): no horizontal overflow at 360 px, no CSP violations or console errors, touch
// targets of at least 24 px (WCAG 2.2 2.5.8), axe WCAG 2.2 AA including colour contrast, and the
// plan request's time. Light mode (the only one), phone and desktop widths.
// Usage: node scripts/browser-check.mjs [base URL, default http://localhost:8080]
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {chromium} from 'playwright';

const BASE = process.argv[2] ?? 'http://localhost:8080';
const AXE = readFileSync(
  join(import.meta.dirname, '..', 'node_modules/axe-core/axe.min.js'),
  'utf8',
);
const SIZES = [
  [360, 800],
  [1280, 900],
];
// Light only: the owner dropped dark mode on 2026-10-10 (docs/ai/REVIEW_LOG.md). Each width is checked in two
// states: the results, and the holiday paste panel (on a phone: the results screen, then the inputs with the
// holidays sheet open).
const STAGES = ['results', 'holidays'];
const PHONE_WIDTH = 799;
const MIN_TARGET = 24;

// WCAG 2.5.8 "equivalent" exception: the date field itself (a full-size target) opens the same
// picker as Astryx DateInput's 16 px calendar icon.
const TARGET_EXCEPTIONS = [{label: /calendar/i, reason: 'the date field opens the same picker'}];

const failures = [];
const fail = (where, what) => failures.push(`${where}: ${what}`);

/** Plans with the defaults, then for the 'holidays' stage opens the paste panel. */
async function exercise(page, stage, width) {
  await page.goto(BASE);
  const plan = page.getByRole('button', {name: 'Plan my leaves'});
  await plan.waitFor();
  await page.waitForFunction(() => {
    const b = [...document.querySelectorAll('button')].find(
      (x) => x.textContent === 'Plan my leaves',
    );
    return b && !b.disabled && b.getAttribute('aria-disabled') !== 'true';
  });
  const started = Date.now();
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/plan')),
    plan.click(),
  ]);
  await page.getByRole('region', {name: /^Your \d{4} plan$/}).waitFor();
  const planMs = Date.now() - started;
  if (stage === 'holidays') {
    if (width <= PHONE_WIDTH) {
      await page.getByRole('button', {name: 'Edit inputs'}).click();
      await page.getByRole('button', {name: /^Holidays: /}).click();
    } else {
      await page.getByRole('button', {name: 'Edit list'}).click();
    }
    await page.getByRole('radio', {name: 'Paste a list'}).click();
    await page.getByRole('textbox', {name: /One holiday per line/}).waitFor();
  }
  return {planMs, status: response.status()};
}

const browser = await chromium.launch();
const timings = [];
try {
  for (const [width, height] of SIZES) {
    for (const stage of STAGES) {
      const scheme = 'light';
      const where = `${width}px ${stage}`;

      // Pass 1: the real CSP, so violations surface.
      const context = await browser.newContext({viewport: {width, height}, colorScheme: scheme});
      const page = await context.newPage();
      page.on('console', (m) => m.type() === 'error' && fail(where, `console error: ${m.text()}`));
      page.on('pageerror', (e) => fail(where, `page error: ${e.message}`));
      await page.addInitScript(() =>
        document.addEventListener('securitypolicyviolation', (e) =>
          console.error(`CSP violation: ${e.violatedDirective} ${e.blockedURI}`),
        ),
      );
      const {planMs, status} = await exercise(page, stage, width);
      if (status !== 200) fail(where, `POST /api/plan returned ${status}`);
      timings.push(planMs);

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
      if (overflow > 0) fail(where, `page is ${overflow}px wider than the viewport`);

      const small = await page.evaluate((min) => {
        const selector =
          'button, a[href], input:not([type=hidden]), textarea, select, [role=checkbox], [role=radio]';
        // A text field's tap target is its visible field box (the input's parent), not the bare
        // <input> inside it.
        const isTextField = (el) =>
          el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' && !/checkbox|radio/.test(el.type));
        const boxOf = (el) => (isTextField(el) ? el.parentElement : el).getBoundingClientRect();
        return [...document.querySelectorAll(selector)]
          .filter((el) => {
            const box = boxOf(el);
            const style = getComputedStyle(el);
            if (box.width <= 1 || box.height <= 1 || style.visibility === 'hidden') return false;
            if (style.opacity === '0') return false; // a visually hidden native input behind a custom control
            // WCAG 2.5.8 inline exception: a link inside a sentence.
            if (el.tagName === 'A' && el.parentElement.textContent.trim() !== el.textContent.trim())
              return false;
            return box.width < min || box.height < min;
          })
          .map((el) => {
            const box = boxOf(el);
            const label = el.getAttribute('aria-label') || el.textContent.trim().slice(0, 40);
            return {label, size: `${Math.round(box.width)}x${Math.round(box.height)}`};
          });
      }, MIN_TARGET);
      for (const target of small) {
        if (!TARGET_EXCEPTIONS.some((e) => e.label.test(target.label))) {
          fail(where, `touch target "${target.label}" is ${target.size} (minimum ${MIN_TARGET}px)`);
        }
      }
      await context.close();

      // Pass 2: axe needs its script injected, which the page's CSP rightly blocks.
      const axeContext = await browser.newContext({
        viewport: {width, height},
        colorScheme: scheme,
        bypassCSP: true,
      });
      const axePage = await axeContext.newPage();
      await exercise(axePage, stage, width);
      await axePage.addScriptTag({content: AXE});
      const violations = await axePage.evaluate(async () => {
        const result = await window.axe.run(document, {
          runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']},
        });
        return result.violations.map(
          (v) =>
            `${v.id} (${v.nodes.length}): ${v.nodes[0]?.failureSummary?.split('\n')[1] ?? v.help}`,
        );
      });
      violations.forEach((v) => fail(where, `axe ${v}`));
      await axeContext.close();
    }
  }
} finally {
  await browser.close();
}

const worst = Math.max(...timings);
console.log(
  `Plan with the defaults, click to results shown: ${timings.join(', ')} ms (worst ${worst} ms).`,
);
if (worst >= 1000) fail('timing', `a plan took ${worst} ms; the PRD allows under 1 second`);
if (failures.length > 0) {
  console.error(`Browser check failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log(
  `Browser check passed at ${SIZES.map(([w]) => `${w}px`).join(' and ')}, light mode, results and holiday panel: no overflow, ` +
    'no CSP violations or console errors, touch targets of 24px or more, no axe WCAG 2.2 AA violations.',
);
