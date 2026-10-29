// Records the LeaveWise demo, one segment at a time, with a CDP screencast per segment. The narration
// clips (tts.mjs) are the clock: each step waits for its clip, and the clip's start time is kept so
// encode.mjs can place the audio exactly. Usage: node record.mjs [segment ids...] (default: all).
import {chromium} from '../../frontend/node_modules/playwright/index.mjs';
import {mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {segments} from './narration.mjs';

const ROOT = new URL('./', import.meta.url).pathname;
const DUR = JSON.parse(readFileSync(`${ROOT}tts/durations.json`, 'utf8'));
const APP = 'http://localhost:8080';
const SHA = '1920e04674a539753850b787a7445f7e73cd7c6b';
const GH = 'https://github.com/shekharAggarwal/LeaveWise';
const CI_RUN = `${GH}/actions/runs/40841695005`;
const blob = (path, hash = '') => `${GH}/blob/${SHA}/${path}${hash}`;
const GAP = 450;
const Z = 1.5; // desktop: a 1920x1080 viewport with the page zoomed 1.5x looks like 1280x720, at full resolution
const PASTE = [
  "2027-01-01 New Year's Day",
  '2027-01-26 Republic Day',
  '2027-03-10 Eid ul Fitr',
  '2027-03-23 Holi',
  '2027-03-27 Rangpanchmi',
  '2027-08-15 Independence Day',
  '2027-10-02 Mahatama Gandhi Jayanti',
  '2027-10-09 Dusshera',
  '2027-10-29 Diwali',
  '2027-11-04 Chhath Pooja',
  '2027-12-25 Christmas Day',
  '2027-02-30 Bad date',
].join('\n');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const now = () => Date.now() / 1000;
const text = (segId, stepId) => segments.find((s) => s.id === segId).steps.find((s) => s.id === stepId);

// ---------- overlay: caption, section chip, cursor, click ripple, file preview ----------
function overlayInit({S, Z}) {
  const zoom = () => {
    if (!document.body) return false;
    document.body.style.zoom = String(Z);
    return true;
  };
  const watch = new MutationObserver(() => zoom() && watch.disconnect());
  watch.observe(document, {childList: true, subtree: true});
  document.addEventListener('DOMContentLoaded', zoom);
  const install = () => {
    if (window.__demo || !document.documentElement) return;
    const host = document.createElement('demo-overlay');
    host.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:2147483647;';
    const root = host.attachShadow({mode: 'open'});
    const narrow = window.innerWidth / Z < 600;
    const px = (n) => `${Math.round(n * S)}px`;
    root.innerHTML = `<style>
      .cap{position:fixed;left:50%;bottom:${px(narrow ? 18 : 26)};transform:translateX(-50%);width:max-content;
        max-width:${narrow ? '92vw' : `min(${px(1060)},86vw)`};box-sizing:border-box;padding:${narrow ? `${px(8)} ${px(12)}` : `${px(10)} ${px(20)}`};
        border-radius:12px;background:rgba(15,17,28,.88);color:#fff;text-align:center;
        font:500 ${px(narrow ? 14 : 21)}/1.4 -apple-system,"SF Pro Text",system-ui,sans-serif;
        box-shadow:0 8px 28px rgba(0,0,0,.28);opacity:0;transition:opacity .25s}
      .cap.on{opacity:1}
      .chip{position:fixed;top:${px(12)};left:50%;transform:translateX(-50%);padding:${px(6)} ${px(16)};border-radius:999px;
        background:rgba(48,64,180,.95);color:#fff;font:600 ${px(narrow ? 12 : 15)}/1.3 -apple-system,system-ui,sans-serif;
        letter-spacing:.02em;box-shadow:0 6px 18px rgba(0,0,0,.2);opacity:0;transition:opacity .35s}
      .chip.on{opacity:1}
      .cur{position:fixed;left:0;top:0;width:${px(24)};height:${px(24)};margin:${px(-12)} 0 0 ${px(-12)};border-radius:50%;
        background:rgba(48,64,180,.28);border:2px solid rgba(48,64,180,.95);box-shadow:0 0 0 2px rgba(255,255,255,.95);
        transition:transform .6s cubic-bezier(.4,0,.2,1),opacity .25s;opacity:0}
      .cur.on{opacity:1}
      .rip{position:fixed;left:0;top:0;width:${px(24)};height:${px(24)};margin:${px(-12)} 0 0 ${px(-12)};border-radius:50%;
        border:3px solid rgba(48,64,180,.9);animation:rip .55s ease-out forwards}
      @keyframes rip{from{transform:var(--p) scale(1);opacity:1}to{transform:var(--p) scale(2.8);opacity:0}}
      .panel{position:fixed;left:50%;top:50%;transform:translate(-50%,-50%) scale(.97);width:min(${px(820)},90vw);
        background:#fff;border-radius:14px;box-shadow:0 24px 70px rgba(0,0,0,.35);overflow:hidden;opacity:0;
        transition:opacity .3s,transform .3s}
      .panel.on{opacity:1;transform:translate(-50%,-50%) scale(1)}
      .ph{padding:${px(12)} ${px(18)};background:#1d2033;color:#fff;font:600 ${px(15)}/1.3 -apple-system,system-ui,sans-serif}
      .ph small{font-weight:400;opacity:.75;margin-left:8px}
      .pb{margin:0;padding:${px(14)} ${px(18)};font:${px(14)}/1.5 "SF Mono",Menlo,monospace;color:#1d2033;white-space:pre-wrap;
        max-height:${px(440)};overflow:hidden}
    </style><div class="chip"></div><div class="cap"></div><div class="panel"><div class="ph"></div><pre class="pb"></pre></div><div class="cur"></div>`;
    document.documentElement.appendChild(host);
    const $ = (s) => root.querySelector(s);
    let pos = null;
    window.__demo = {
      caption(t) {
        const c = $('.cap');
        if (t) c.textContent = t;
        c.classList.toggle('on', Boolean(t));
      },
      chip(t) {
        const c = $('.chip');
        if (t) c.textContent = t;
        c.classList.toggle('on', Boolean(t));
      },
      cursor(x, y, instant) {
        const c = $('.cur');
        if (instant || !pos) c.style.transition = 'opacity .25s';
        c.style.transform = `translate(${x}px,${y}px)`;
        c.getBoundingClientRect();
        c.style.transition = '';
        c.classList.add('on');
        pos = [x, y];
      },
      hideCursor() {
        $('.cur').classList.remove('on');
      },
      ripple(x, y) {
        const r = document.createElement('div');
        r.className = 'rip';
        r.style.setProperty('--p', `translate(${x}px,${y}px)`);
        r.style.transform = `translate(${x}px,${y}px)`;
        root.appendChild(r);
        setTimeout(() => r.remove(), 700);
      },
      panel(title, sub, body) {
        $('.ph').innerHTML = '';
        $('.ph').append(title);
        const s = document.createElement('small');
        s.textContent = sub;
        $('.ph').append(s);
        $('.pb').textContent = body;
        $('.panel').classList.add('on');
      },
      hidePanel() {
        $('.panel').classList.remove('on');
      },
    };
  };
  install();
  document.addEventListener('readystatechange', install);
  document.addEventListener('DOMContentLoaded', install);
}

// ---------- screencast recorder ----------
class Recorder {
  constructor(page, segId) {
    this.page = page;
    this.dir = `${ROOT}rec/${segId}/`;
    rmSync(this.dir, {recursive: true, force: true});
    mkdirSync(this.dir, {recursive: true});
    this.frames = [];
    this.marks = [];
    this.caption = '';
    this.cursorPos = null;
  }
  async start() {
    this.cdp = await this.page.context().newCDPSession(this.page);
    this.cdp.on('Page.screencastFrame', ({data, metadata, sessionId}) => {
      const file = `${this.dir}f${String(this.frames.length).padStart(6, '0')}.jpg`;
      writeFileSync(file, Buffer.from(data, 'base64'));
      this.frames.push({file, ts: metadata.timestamp, w: metadata.deviceWidth, h: metadata.deviceHeight});
      this.cdp.send('Page.screencastFrameAck', {sessionId}).catch(() => {});
    });
    await this.cdp.send('Page.startScreencast', {
      format: 'jpeg',
      quality: 92,
      maxWidth: 1920,
      maxHeight: 1080,
      everyNthFrame: 1,
    });
    // Nudge a repaint so the first frame arrives even on a still page.
    await this.page.evaluate(() => window.__demo?.chip('')).catch(() => {});
    for (let i = 0; i < 200 && this.frames.length === 0; i++) await sleep(25);
    if (this.frames.length === 0) throw new Error('no screencast frame');
    this.t0 = this.frames[0].ts;
  }
  async stop(tail = 700) {
    await sleep(tail);
    this.tEnd = now();
    await this.cdp.send('Page.stopScreencast');
    await sleep(150);
    writeFileSync(
      `${this.dir}timeline.json`,
      JSON.stringify({t0: this.t0, tEnd: this.tEnd, frames: this.frames, marks: this.marks}),
    );
    const sizes = new Set(this.frames.map((f) => `${f.w}x${f.h}`));
    console.log(
      `  ${this.dir.split('/').at(-2)}: ${this.frames.length} frames, ${(this.tEnd - this.t0).toFixed(1)} s, css sizes ${[...sizes].join(',')}`,
    );
  }
}

// ---------- page helpers ----------
async function overlay(page, fn, ...args) {
  return page.evaluate(([f, a]) => window.__demo && window.__demo[f](...a), [fn, args]).catch(() => {});
}
async function reapply(rec) {
  await overlay(rec.page, 'caption', rec.caption);
  if (rec.cursorPos) await overlay(rec.page, 'cursor', rec.cursorPos[0], rec.cursorPos[1], true);
}
async function say(rec, segId, stepId, {before, during} = {}) {
  if (before) {
    rec.caption = '';
    await overlay(rec.page, 'caption', '');
    await before();
  }
  const key = `${segId}-${stepId}`;
  rec.caption = text(segId, stepId).caption;
  rec.marks.push({clip: `${ROOT}tts/${key}.aiff`, t: now(), key});
  await overlay(rec.page, 'caption', rec.caption);
  await Promise.all([during ? during() : null, sleep(DUR[key] * 1000)]);
  await sleep(GAP);
}
async function chip(rec, label, ms = 3800) {
  await overlay(rec.page, 'chip', label);
  setTimeout(() => overlay(rec.page, 'chip', ''), ms);
}
async function moveTo(rec, x, y) {
  rec.cursorPos = [x, y];
  await overlay(rec.page, 'cursor', x, y);
  await rec.page.mouse.move(x, y, {steps: 6}).catch(() => {});
  await sleep(650);
}
async function center(locator) {
  const b = await locator.boundingBox();
  if (!b) throw new Error(`no box for ${locator}`);
  return [b.x + b.width / 2, b.y + b.height / 2];
}
async function smoothIntoView(rec, locator, block = 'center', wait = 900) {
  await locator.evaluate((el, blk) => el.scrollIntoView({behavior: 'smooth', block: blk}), block);
  await sleep(wait);
}
async function scrollPage(rec, top, wait = 900) {
  await rec.page.evaluate((y) => window.scrollTo({top: y, behavior: 'smooth'}), top);
  await sleep(wait);
}
async function point(rec, locator, {click = false, tap = false, scroll = true, block = 'center'} = {}) {
  if (scroll) {
    const inView = await locator.evaluate((el) => {
      const r = el.getBoundingClientRect();
      return r.top >= 90 && r.bottom <= window.innerHeight - 165;
    });
    if (!inView) await smoothIntoView(rec, locator, block);
  }
  const [x, y] = await center(locator);
  await moveTo(rec, x, y);
  if (click || tap) {
    await overlay(rec.page, 'ripple', x, y);
    if (tap) await locator.tap();
    else await locator.click();
    await sleep(250);
  }
}
async function go(rec, url, ready) {
  await rec.page.goto(url, {waitUntil: 'load'});
  if (ready) await ready();
  await toHighlight(rec.page);
  await sleep(500);
  await reapply(rec);
}
async function toHighlight(page) {
  // GitHub scrolls to a line anchor itself once its code view is ready, without allowing for the zoom.
  // Let that settle, then put the anchor's first line near the top, and check it stays there.
  const start = Number((/#L(\d+)/.exec(page.url()) ?? [])[1]);
  if (!start) return;
  const lineTop = () =>
    page.evaluate((n) => {
      const el = document.getElementById(`LC${n}`) ?? document.querySelector(`[data-line-number="${n}"]`);
      return el ? el.getBoundingClientRect().top : null;
    }, start);
  let last = -1;
  for (let i = 0; i < 16; i++) {
    const y = await page.evaluate(() => window.scrollY);
    if (y === last && i > 3) break;
    last = y;
    await sleep(250);
  }
  for (let ok = 0, i = 0; i < 8 && ok < 2; i++) {
    const top = await lineTop();
    if (top === null) break;
    if (top > 230 && top < 380) {
      ok++;
    } else {
      ok = 0;
      await page.evaluate((d) => window.scrollBy(0, d), top - 300);
    }
    await sleep(450);
  }
}

// ---------- cards (title, end) ----------
const cardHtml = (inner) => `<!doctype html><html><head><meta charset="utf-8"><style>
  html{height:100%}
  body{margin:0;min-height:100%;zoom:1.5}
  body{display:flex;align-items:center;justify-content:center;height:calc(100vh / 1.5);background:radial-gradient(1200px 600px at 30% 20%,#3a4bc8 0%,#26308f 45%,#151a4d 100%);
    color:#fff;font-family:-apple-system,"SF Pro Display",system-ui,sans-serif}
  .w{width:1040px}
  .badge{display:inline-block;padding:7px 16px;border-radius:999px;background:rgba(255,255,255,.14);font-size:17px;font-weight:600;letter-spacing:.03em}
  h1{font-size:104px;line-height:1;margin:28px 0 18px;letter-spacing:-.02em}
  .sub{font-size:30px;line-height:1.35;opacity:.92;max-width:900px}
  .by{margin-top:34px;font-size:22px;opacity:.85}
  .note{margin-top:54px;font-size:17px;line-height:1.5;opacity:.75;max-width:900px}
  .url{margin-top:26px;font:600 26px/1.3 "SF Mono",Menlo,monospace;opacity:.95}
</style></head><body><div class="w">${inner}</div></body></html>`;

const TITLE = cardHtml(`
  <span class="badge">Hack at FiftyFive · AI Hackathon 2026</span>
  <h1>LeaveWise</h1>
  <div class="sub">Turn your leaves into the longest breaks: the provably best plan for your year, in under a minute.</div>
  <div class="by">Shekhar Aggarwal</div>
  <div class="note">The narration is a synthetic voice (macOS text-to-speech). Claude Code wrote the script and recorded
  this video with Playwright, against the app running locally from the repository.</div>`);
const END = cardHtml(`
  <span class="badge">LeaveWise</span>
  <h1>Thanks for watching</h1>
  <div class="sub">The best plan for your leave, in under a minute, and proven.</div>
  <div class="url">github.com/shekharAggarwal/LeaveWise</div>
  <div class="note">Narration: synthetic voice. Script and recording: Claude Code with Playwright.</div>`);

async function card(page, segId, stepId, html) {
  await page.setContent(html, {waitUntil: 'load'});
  const rec = new Recorder(page, segId);
  await rec.start();
  const key = `${segId}-${stepId}`;
  await sleep(600);
  rec.marks.push({clip: `${ROOT}tts/${key}.aiff`, t: now(), key});
  await sleep(DUR[key] * 1000 + 900);
  await rec.stop(400);
}

// ---------- segments ----------
const appReady = (page) => async () => {
  await page.getByRole('button', {name: 'Plan my leaves'}).waitFor();
  await page.waitForFunction(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent === 'Plan my leaves');
    return b && !b.disabled && b.getAttribute('aria-disabled') !== 'true';
  });
};

async function problem(page) {
  await page.goto(APP);
  await appReady(page)();
  await page.evaluate(() => window.scrollTo(0, 0));
  await sleep(400);
  const rec = new Recorder(page, 'problem');
  await rec.start();
  rec.cursorPos = [700, 420];
  await overlay(page, 'cursor', 700, 420, true);
  await say(rec, 'problem', 'p1', {
    during: () => point(rec, page.getByRole('heading', {name: /Turn your leaves/})),
  });
  await say(rec, 'problem', 'p2', {
    during: () => point(rec, page.getByRole('heading', {name: 'Your plan shows here'})),
  });
  await say(rec, 'problem', 'p3');
  await rec.stop();
  return rec;
}

async function product(page) {
  const rec = new Recorder(page, 'product');
  await rec.start();
  await reapply(rec);
  await chip(rec, 'Product walkthrough');
  await say(rec, 'product', 'defaults', {
    during: async () => {
      await point(rec, page.getByRole('radio', {name: '2027'}));
      await point(rec, page.getByRole('textbox', {name: 'Leave days'}));
      await point(rec, page.getByRole('button', {name: 'Sat'}));
      await point(rec, page.getByRole('radio', {name: 'Long weekends'}));
    },
  });
  await say(rec, 'product', 'list2026', {
    before: async () => {
      await scrollPage(rec, 0, 600);
      await point(rec, page.getByRole('radio', {name: '2026'}), {click: true});
      await page.getByText('Makar Sankranti/Pongal').waitFor();
    },
    during: async () => {
      await smoothIntoView(rec, page.getByText('Makar Sankranti/Pongal'), 'center', 1100);
      await point(rec, page.getByText('Floater').first(), {scroll: false});
      await scrollPage(rec, (await page.evaluate(() => window.scrollY)) + 380 * Z, 1400);
    },
  });
  await say(rec, 'product', 'paste', {
    before: async () => {
      await scrollPage(rec, 0, 900);
      await point(rec, page.getByRole('radio', {name: '2027'}), {click: true});
      await page.getByRole('button', {name: 'Paste a list'}).waitFor();
    },
    during: async () => {
      await point(rec, page.getByRole('button', {name: 'Paste a list'}), {click: true});
      const box = page.getByRole('textbox', {name: /One holiday per line/});
      await box.waitFor();
      await point(rec, box);
      await box.fill(PASTE);
    },
  });
  await say(rec, 'product', 'loaded', {
    before: async () => {
      await point(rec, page.getByRole('button', {name: 'Load holidays'}), {click: true});
      await page.getByText(/1 line didn't load/).waitFor();
      await sleep(300);
    },
    during: async () => {
      await smoothIntoView(rec, page.getByText(/is not a real date/), 'center', 1000);
      await point(rec, page.getByText(/is not a real date/), {scroll: false});
    },
  });
  await say(rec, 'product', 'plan', {
    during: async () => {
      await point(rec, page.getByRole('button', {name: 'Plan my leaves'}), {click: true});
      await page.getByRole('region', {name: /^Your \d{4} plan$/}).waitFor();
      await sleep(250);
      await scrollPage(rec, 0, 1000);
      await point(rec, page.getByRole('heading', {name: /leaves → \d+ days off/}), {scroll: false});
    },
  });
  await say(rec, 'product', 'breaks', {
    during: async () => {
      await smoothIntoView(rec, page.getByRole('heading', {name: 'Your breaks'}), 'start', 1100);
      const list = page.getByRole('list', {name: 'Your breaks'});
      await point(rec, list.getByText(/leave Mon, 4 Jan/), {scroll: false});
      await point(rec, list.getByText('per leave').first(), {scroll: false});
      await scrollPage(rec, (await page.evaluate(() => window.scrollY)) + 260 * Z, 1200);
    },
  });
  await say(rec, 'product', 'calendar', {
    during: async () => {
      await smoothIntoView(rec, page.getByRole('heading', {name: /at a glance/}), 'start', 1100);
      await point(rec, page.getByRole('list', {name: 'Legend'}), {scroll: false});
      await sleep(1200);
      await point(rec, page.getByRole('button', {name: /: leave day/}).first(), {click: true, scroll: false});
    },
  });
  await say(rec, 'product', 'ties', {
    during: async () => {
      await smoothIntoView(rec, page.getByRole('heading', {name: 'Your breaks'}), 'start', 1100);
      await point(rec, page.getByText(/leave Fri, 8 Jan/), {scroll: false});
      await point(rec, page.getByText(/leave Fri, 26 Feb/), {scroll: false});
    },
  });
  await say(rec, 'product', 'bigtrips', {
    before: async () => {
      await scrollPage(rec, 230 * Z, 1000);
      await point(rec, page.getByRole('radio', {name: 'Big trips'}), {click: true, scroll: false});
      await sleep(300);
      await scrollPage(rec, 0, 900);
    },
    during: async () => {
      await point(rec, page.getByText(/Inputs changed/), {scroll: false});
      await sleep(700);
      await point(rec, page.getByRole('button', {name: 'Plan again'}), {click: true, scroll: false});
      await page.getByText(/across 3 breaks/).waitFor();
      await sleep(500);
      await smoothIntoView(rec, page.getByRole('heading', {name: 'Your breaks'}), 'start', 1200);
      await point(rec, page.getByRole('list', {name: 'Your breaks'}).getByText(/Diwali/), {scroll: false});
    },
  });
  await say(rec, 'product', 'ics', {
    before: async () => {
      await scrollPage(rec, 0, 900);
    },
    during: async () => {
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        point(rec, page.getByRole('button', {name: 'Add to calendar (.ics)'}), {click: true, scroll: false}),
      ]);
      const ics = readFileSync(await download.path(), 'utf8').replace(/\r\n/g, '\n');
      const events = (ics.match(/BEGIN:VEVENT/g) ?? []).length;
      const end = ics.indexOf('END:VEVENT') + 'END:VEVENT'.length;
      const shown = `${ics.slice(0, end).replace(/\n /g, '')}\n…`;
      writeFileSync(`${ROOT}rec/downloaded.ics`, ics);
      await overlay(page, 'panel', download.suggestedFilename(), `${events} all-day events, the first one shown`, shown);
      await overlay(page, 'hideCursor');
    },
  });
  await sleep(900);
  await overlay(page, 'hidePanel');
  await rec.stop(500);
  return rec;
}

async function phone(browser) {
  const ctx = await browser.newContext({
    viewport: {width: 390, height: 844},
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    bypassCSP: true,
  });
  // No zoom here: the phone's bottom sheets don't follow a zoomed body.
  await ctx.addInitScript(overlayInit, {S: 1, Z: 1});
  const page = await ctx.newPage();
  await page.goto(APP);
  await appReady(page)();
  // Same inputs as the desktop plan, set up before the recording starts.
  await page.getByText(/tap to review or add/).click();
  await page.getByRole('radio', {name: 'Paste a list'}).click();
  await page.getByRole('textbox', {name: /One holiday per line/}).fill(PASTE);
  await page.getByRole('button', {name: 'Load holidays'}).click();
  await page.getByText(/1 line didn't load/).waitFor();
  await page.getByRole('button', {name: 'Done'}).click();
  await sleep(500);
  await page.getByRole('radio', {name: 'Big trips'}).click();
  await page.getByRole('button', {name: 'Plan my leaves'}).click();
  await page.getByText(/across 3 breaks/).waitFor();
  await sleep(800);
  const rec = new Recorder(page, 'phone');
  await rec.start();
  await say(rec, 'phone', 'phone1', {
    during: async () => {
      await sleep(600);
      await scrollPage(rec, 320, 1500);
    },
  });
  await say(rec, 'phone', 'phone2', {
    during: async () => {
      await scrollPage(rec, 0, 700);
      await point(rec, page.getByRole('radio', {name: 'Calendar'}), {tap: true, scroll: false});
      await sleep(700);
      await point(rec, page.getByRole('button', {name: /: leave day/}).first(), {tap: true});
      await sleep(400);
      await overlay(page, 'hideCursor');
    },
  });
  await sleep(600);
  await rec.stop(400);
  await ctx.close();
  return rec;
}

const ghReady = (page, selector) => async () => {
  await page.locator(selector).first().waitFor({timeout: 20000});
  await sleep(600);
};

async function code(page) {
  await page.goto(blob('README.md', '#architecture'), {waitUntil: 'load'});
  await ghReady(page, 'article')();
  const rec = new Recorder(page, 'code');
  await rec.start();
  await reapply(rec);
  await chip(rec, 'Code walkthrough');
  await say(rec, 'code', 'arch', {
    during: async () => {
      const pre = page.locator('article pre').filter({hasText: 'planner'}).first();
      await smoothIntoView(rec, pre, 'center', 1100);
      await point(rec, pre.getByText(/planner/).first(), {scroll: false}).catch(() => point(rec, pre, {scroll: false}));
    },
  });
  // On code pages the highlighted lines carry the attention; no cursor.
  rec.cursorPos = null;
  await overlay(page, 'hideCursor');
  const optimizer = 'backend/src/main/java/dev/leavewise/planner/BreakOptimizer.java';
  await say(rec, 'code', 'dp', {
    before: () => go(rec, blob(optimizer, '#L41-L46'), ghReady(page, '.react-code-lines')),
  });
  await say(rec, 'code', 'candidates', {
    before: () => go(rec, blob(optimizer, '#L44-L60'), ghReady(page, '.react-code-lines')),
  });
  await say(rec, 'code', 'tiebreak', {
    before: () => go(rec, blob(optimizer, '#L66-L71'), ghReady(page, '.react-code-lines')),
  });
  await say(rec, 'code', 'oracle', {
    before: () =>
      go(rec, blob('backend/src/test/java/dev/leavewise/planner/PlannerOracleProperties.java', '#L110-L131'), ghReady(page, '.react-code-lines')),
  });
  await say(rec, 'code', 'controller', {
    before: () => go(rec, blob('backend/src/main/java/dev/leavewise/api/PlanController.java', '#L60-L73'), ghReady(page, '.react-code-lines')),
  });
  await rec.stop();
  return rec;
}

async function ai(page) {
  await page.goto(blob('CLAUDE.md'), {waitUntil: 'load'});
  await ghReady(page, 'article')();
  const rec = new Recorder(page, 'ai');
  await rec.start();
  await reapply(rec);
  await chip(rec, 'How AI built it');
  await say(rec, 'ai', 'brief', {
    during: async () => {
      await sleep(1500);
      await smoothIntoView(rec, page.locator('article h2', {hasText: 'Working rules'}), 'start', 1500);
    },
  });
  await say(rec, 'ai', 'agents', {
    before: () => go(rec, blob('docs/ai/AI_WORKFLOW.md', '#subagents'), ghReady(page, 'article table')),
    during: async () => {
      await sleep(2500);
      await scrollPage(rec, (await page.evaluate(() => window.scrollY)) + 300 * Z, 2000);
    },
  });
  await say(rec, 'ai', 'failures', {
    before: () =>
      go(rec, blob('docs/ai/AI_FAILURE_LOG.md', '#15-a-plan-request-at-new-year-midnight-returned-a-500-2026-10-10'), ghReady(page, 'article')),
  });
  await rec.stop();
  return rec;
}

async function proof(page) {
  await page.goto(CI_RUN, {waitUntil: 'load'});
  await ghReady(page, 'main')();
  const rec = new Recorder(page, 'proof');
  await rec.start();
  await reapply(rec);
  await chip(rec, 'How we know it works');
  await say(rec, 'proof', 'ci', {
    during: async () => {
      for (const name of ['quality-gate', 'dependency-scan', 'docker-image']) {
        const job = page.getByRole('link', {name: new RegExp(name)}).first();
        if (await job.count()) await point(rec, job).catch(() => {});
        await sleep(900);
      }
    },
  });
  await rec.stop();
  return rec;
}

// ---------- main ----------
const only = process.argv.slice(2);
const want = (id) => only.length === 0 || only.includes(id);
const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: {width: 1920, height: 1080},
  deviceScaleFactor: 1,
  bypassCSP: true,
  acceptDownloads: true,
  locale: 'en-IN',
});
await ctx.addInitScript(overlayInit, {S: Z, Z});
const page = await ctx.newPage();
try {
  if (want('title')) await card(page, 'title', 'intro', TITLE);
  if (want('problem') || want('product')) await problem(page);
  if (want('product')) await product(page);
  if (want('phone')) await phone(browser);
  if (want('code')) await code(page);
  if (want('ai')) await ai(page);
  if (want('proof')) await proof(page);
  if (want('end')) await card(page, 'end', 'close', END);
} finally {
  await browser.close();
}
