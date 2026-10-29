// Turns each recorded segment (frames + narration marks) into a 1080p H.264/AAC clip, then joins
// them into leavewise-demo.mp4. Usage: node encode.mjs
import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {segments} from './narration.mjs';

const ROOT = new URL('./', import.meta.url).pathname;
const REC = `${ROOT}rec/`;
const OUT = `${ROOT}leavewise-demo.mp4`;
const ff = (args) => execFileSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', ...args], {stdio: 'inherit'});

const parts = [];
for (const {id} of segments) {
  const tl = `${REC}${id}/timeline.json`;
  if (!existsSync(tl)) throw new Error(`segment ${id} not recorded`);
  const {t0, tEnd, frames, marks} = JSON.parse(readFileSync(tl, 'utf8'));
  const dur = tEnd - t0;
  let list = 'ffconcat version 1.0\n';
  frames.forEach((f, i) => {
    const next = i + 1 < frames.length ? frames[i + 1].ts : tEnd;
    list += `file '${f.file}'\nduration ${Math.max(next - f.ts, 0.001).toFixed(4)}\n`;
  });
  list += `file '${frames.at(-1).file}'\n`;
  writeFileSync(`${REC}${id}.frames.txt`, list);

  const inputs = ['-f', 'concat', '-safe', '0', '-i', `${REC}${id}.frames.txt`];
  let audio = '';
  marks.forEach((m, i) => {
    inputs.push('-i', m.clip);
    const ms = Math.max(0, Math.round((m.t - t0) * 1000));
    audio += `[${i + 1}:a]aresample=48000,aformat=channel_layouts=stereo,adelay=${ms}:all=1[a${i}];`;
  });
  audio += `${marks.map((_, i) => `[a${i}]`).join('')}amix=inputs=${marks.length}:normalize=0:dropout_transition=0,apad=whole_dur=${dur.toFixed(3)}[a]`;
  const video =
    '[0:v]scale=1920:1080:force_original_aspect_ratio=decrease:flags=lanczos,' +
    'pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x1B1E2E,setsar=1,fps=30,format=yuv420p[v]';
  const part = `${REC}${id}.mp4`;
  ff([
    ...inputs,
    '-filter_complex', `${video};${audio}`,
    '-map', '[v]', '-map', '[a]',
    '-t', dur.toFixed(3),
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-profile:v', 'high', '-r', '30',
    '-c:a', 'aac', '-b:a', '160k', '-ar', '48000', '-ac', '2',
    part,
  ]);
  console.log(`${id.padEnd(8)} ${dur.toFixed(1)} s, ${frames.length} frames, ${marks.length} clips`);
  parts.push(part);
}
writeFileSync(`${REC}parts.txt`, parts.map((p) => `file '${p}'`).join('\n') + '\n');
ff(['-f', 'concat', '-safe', '0', '-i', `${REC}parts.txt`, '-c', 'copy', `${REC}joined.mp4`]);
// Speech loudness to the usual -16 LUFS; the video is copied untouched.
ff(['-i', `${REC}joined.mp4`, '-c:v', 'copy', '-af', 'loudnorm=I=-16:TP=-1.5:LRA=11', '-c:a', 'aac', '-b:a', '160k', '-ar', '48000', '-movflags', '+faststart', OUT]);
const probe = execFileSync(
  'ffprobe',
  ['-v', 'error', '-show_entries', 'format=duration,size:stream=codec_type,codec_name,width,height', '-of', 'json', OUT],
  {encoding: 'utf8'},
);
const info = JSON.parse(probe);
console.log(
  `${OUT}\n  duration ${Number(info.format.duration).toFixed(1)} s, ${(Number(info.format.size) / 1e6).toFixed(1)} MB,`,
  info.streams.map((s) => `${s.codec_type}:${s.codec_name}${s.width ? ` ${s.width}x${s.height}` : ''}`).join(', '),
);
