// Checks the built page against the production CSP (style-src 'self'; script-src 'self'):
// no inline <style>, style="" or inline <script> in index.html, and the two stylesheets that
// replace Astryx's inline styles (vite.config.ts) are present. Usage: node check-dist-csp.mjs [dist]
import {existsSync, readFileSync} from 'node:fs';
import {join, resolve} from 'node:path';

const dist = resolve(process.argv[2] ?? join(import.meta.dirname, '..', 'dist'));
const html = readFileSync(join(dist, 'index.html'), 'utf8');
const failures = [];

if (/<style[\s>]/i.test(html)) failures.push('index.html has an inline <style> element');
if (/\sstyle\s*=/i.test(html)) failures.push('index.html has a style="" attribute');
for (const [tag] of html.matchAll(/<script\b[^>]*>/gi)) {
  if (!/\ssrc\s*=/i.test(tag)) failures.push(`index.html has an inline script: ${tag}`);
}

const layers = join(dist, 'layers.css');
if (!existsSync(layers) || !readFileSync(layers, 'utf8').startsWith('@layer ')) {
  failures.push('layers.css with the cascade-layer order is missing');
}
const layersLink = html.indexOf('href="/layers.css"');
const firstStylesheet = html.search(/<link[^>]*rel="stylesheet"/);
if (layersLink === -1 || html.lastIndexOf('<link', layersLink) !== firstStylesheet) {
  failures.push(
    'layers.css must be the first stylesheet, so the layer order is set before any rule',
  );
}

const slideRules = join(dist, 'astryx-layer-slide-rules.css');
if (!existsSync(slideRules) || !readFileSync(slideRules, 'utf8').includes('@position-try')) {
  failures.push('astryx-layer-slide-rules.css is missing or has no @position-try rules');
}
if (!/<link[^>]*id="astryx-layer-slide-rules"/.test(html)) {
  failures.push(
    'the <link id="astryx-layer-slide-rules"> that stops runtime style injection is missing',
  );
}

if (failures.length > 0) {
  console.error(`CSP check of ${dist} failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log(
  `CSP check of ${dist}: no inline styles or scripts; layer and slide-rule stylesheets present.`,
);
