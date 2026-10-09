// SessionStart (startup|resume|clear): installs frontend dependencies when missing, then reminds Claude how to
// resume. SessionStart (compact): re-injects docs/ai/PROGRESS.md so the next step survives compaction.
import {existsSync, readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {emit, projectDir, readHookInput} from './lib.mjs';

const MAX_CONTEXT = 9000; // hook context is capped at 10,000 characters
const input = readHookInput();
const notes = [];

if (input.source !== 'compact') {
  const frontend = path.join(projectDir, 'frontend');
  if (existsSync(path.join(frontend, 'package-lock.json')) && !existsSync(path.join(frontend, 'node_modules'))) {
    const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    const result = spawnSync(npm, ['ci', '--no-fund', '--no-audit'], {
      cwd: frontend,
      encoding: 'utf8',
      shell: process.platform === 'win32',
    });
    notes.push(
      result.status === 0
        ? 'Installed frontend dependencies (npm ci).'
        : `npm ci failed in frontend/ (exit ${result.status}). Run it manually:\n${(result.stderr ?? '').slice(-1500)}`,
    );
  }
}

const progressPath = path.join(projectDir, 'docs/ai/PROGRESS.md');
if (existsSync(progressPath)) {
  const progress = readFileSync(progressPath, 'utf8');
  const next = progress.split('\n').find((line) => /^\s*- \[ \]/.test(line));
  notes.push(`Next unticked step in docs/ai/PROGRESS.md: ${next ? next.replace(/^\s*- \[ \]\s*/, '') : 'none'}`);
  if (input.source === 'compact') {
    notes.push(
      'Context was compacted. Re-read CLAUDE.md and docs/ai/kickoff-prompt.md before continuing. Current PROGRESS.md:',
    );
    notes.push(progress.length > MAX_CONTEXT ? `${progress.slice(0, MAX_CONTEXT)}\n[truncated; read the file]` : progress);
  }
}
if (process.env.CLAUDE_CODE_REMOTE === 'true') {
  notes.push('Cloud session (CLAUDE_CODE_REMOTE=true): skip LAPTOP steps and list them in docs/HUMAN_TODO.md.');
}

emit({hookSpecificOutput: {hookEventName: 'SessionStart', additionalContext: notes.join('\n').slice(0, 9800)}});
