// README screenshots (docs/screenshots/) of the running app: the 2027 plan, Big trips, with FiftyFive's fixed
// holidays pasted in. Needs the app on http://localhost:8080. Usage: node screenshots.mjs
import {chromium} from '../../frontend/node_modules/playwright/index.mjs';

const OUT = new URL('../../docs/screenshots/', import.meta.url).pathname;
const APP = 'http://localhost:8080';
const PASTE = [
  "2027-01-01 New Year's Day", '2027-01-26 Republic Day', '2027-03-10 Eid ul Fitr', '2027-03-23 Holi',
  '2027-03-27 Rangpanchmi', '2027-08-15 Independence Day', '2027-10-02 Mahatama Gandhi Jayanti', '2027-10-09 Dusshera',
  '2027-10-29 Diwali', '2027-11-04 Chhath Pooja', '2027-12-25 Christmas Day',
].join('\n');
const ready = async (page) => {
  await page.getByRole('button', {name: 'Plan my leaves'}).waitFor();
  await page.waitForFunction(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent === 'Plan my leaves');
    return b && !b.disabled && b.getAttribute('aria-disabled') !== 'true';
  });
};

const browser = await chromium.launch();
const desk = await browser.newContext({viewport: {width: 1280, height: 800}, deviceScaleFactor: 1.5});
const page = await desk.newPage();
await page.goto(APP);
await ready(page);
await page.getByRole('button', {name: 'Paste a list'}).click();
await page.getByRole('textbox', {name: /One holiday per line/}).fill(PASTE);
await page.getByRole('button', {name: 'Load holidays'}).click();
await page.getByText('11 holidays added.').waitFor();
await page.getByRole('radio', {name: 'Big trips'}).click();
await page.getByRole('button', {name: 'Plan my leaves'}).click();
await page.getByText(/across 3 breaks/).waitFor();
await page.evaluate(() => window.scrollTo(0, 0));
await page.waitForTimeout(500);
await page.screenshot({path: `${OUT}desktop-plan.png`});
const cal = page.getByRole('heading', {name: /at a glance/});
await cal.evaluate((el) => el.scrollIntoView({block: 'start'}));
await page.evaluate(() => window.scrollBy(0, -24));
await page.getByRole('button', {name: /: leave day/}).first().click();
await page.waitForTimeout(500);
await page.screenshot({path: `${OUT}desktop-calendar.png`});

const phone = await browser.newContext({viewport: {width: 390, height: 844}, deviceScaleFactor: 2, isMobile: true, hasTouch: true});
const p = await phone.newPage();
await p.goto(APP);
await ready(p);
await p.getByText(/tap to review or add/).click();
await p.getByRole('radio', {name: 'Paste a list'}).click();
await p.getByRole('textbox', {name: /One holiday per line/}).fill(PASTE);
await p.getByRole('button', {name: 'Load holidays'}).click();
await p.getByText('11 holidays added.').waitFor();
await p.getByRole('button', {name: 'Done'}).click();
await p.getByRole('radio', {name: 'Big trips'}).click();
await p.getByRole('button', {name: 'Plan my leaves'}).click();
await p.getByText(/across 3 breaks/).waitFor();
await p.waitForTimeout(500);
await p.screenshot({path: `${OUT}phone-plan.png`});
await browser.close();
console.log('saved');
