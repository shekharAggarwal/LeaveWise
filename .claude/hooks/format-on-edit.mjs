// PostToolUse (Edit|Write|MultiEdit): formats the edited file. Prettier for frontend files, Spotless
// (google-java-format) for backend Java. Never blocks; a formatter failure is reported back as context.
import {existsSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {emit, projectDir, projectRelative, readHookInput} from './lib.mjs';

const input = readHookInput();
const rel = projectRelative(input.tool_input?.file_path);
if (!rel || !existsSync(path.join(projectDir, rel))) process.exit(0);

let result = null;
if (rel.startsWith('frontend/') && /\.(tsx?|jsx?|mjs|cjs|json|css|html)$/.test(rel) && !rel.endsWith('CLAUDE.md')) {
  const prettier = path.join(projectDir, 'frontend/node_modules/prettier/bin/prettier.cjs');
  if (!existsSync(prettier)) process.exit(0); // dependencies not installed yet
  result = spawnSync(process.execPath, [prettier, '--write', '--log-level', 'warn', rel.slice('frontend/'.length)], {
    cwd: path.join(projectDir, 'frontend'),
    encoding: 'utf8',
  });
} else if (rel.startsWith('backend/') && rel.endsWith('.java')) {
  const isWindows = process.platform === 'win32';
  const mvnw = path.join(projectDir, 'backend', isWindows ? 'mvnw.cmd' : 'mvnw');
  const fileRegex = '.*' + path.basename(rel).replace(/[.$]/g, '\\$&');
  result = spawnSync(mvnw, ['-q', '-B', '-o', 'spotless:apply', `-DspotlessFiles=${fileRegex}`], {
    cwd: path.join(projectDir, 'backend'),
    encoding: 'utf8',
    shell: isWindows,
  });
}

if (result && result.status !== 0) {
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`
    .split('\n')
    .filter((line) => !line.startsWith('Picked up JAVA_TOOL_OPTIONS'))
    .slice(-20)
    .join('\n');
  emit({
    hookSpecificOutput: {
      hookEventName: 'PostToolUse',
      additionalContext: `Formatter failed on ${rel} (the file may not parse yet):\n${output}`,
    },
  });
}
process.exit(0);
