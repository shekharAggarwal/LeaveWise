// Synthesizes every narration step with macOS `say` and records each clip's duration (afinfo).
import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {segments, VOICE, RATE} from './narration.mjs';

const DIR = new URL('./tts/', import.meta.url).pathname;
mkdirSync(DIR, {recursive: true});
const durations = {};
let total = 0;
let words = 0;
for (const seg of segments) {
  let segTotal = 0;
  for (const step of seg.steps) {
    const file = `${DIR}${seg.id}-${step.id}.aiff`;
    execFileSync('say', ['-v', VOICE, '-r', String(RATE), '-o', file, step.say]);
    const info = execFileSync('afinfo', [file], {encoding: 'utf8'});
    const seconds = Number(/estimated duration: ([\d.]+)/.exec(info)[1]);
    durations[`${seg.id}-${step.id}`] = seconds;
    segTotal += seconds;
    words += step.say.split(/\s+/).length;
    const captionLen = [...step.caption].length;
    if (captionLen > 150) console.log(`  caption long (${captionLen}): ${seg.id}-${step.id}`);
  }
  total += segTotal;
  console.log(`${seg.id.padEnd(8)} ${segTotal.toFixed(1)} s`);
}
writeFileSync(`${DIR}durations.json`, JSON.stringify(durations, null, 1));
console.log(`speech total ${total.toFixed(1)} s, ${words} words, ${(words / (total / 60)).toFixed(0)} wpm`);
