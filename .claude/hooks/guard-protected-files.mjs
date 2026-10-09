// PreToolUse: blocks edits to docs/PRD.md and any .env file (the owner's rules), for file tools and Bash.
import {emit, projectRelative, readHookInput} from './lib.mjs';

const input = readHookInput();
const toolInput = input.tool_input ?? {};

const isEnvFile = (p) => /(^|\/)\.env(\.[^/]*)?$/.test(p) && !p.endsWith('.env.example');
const isPrd = (p) => p === 'docs/PRD.md';

function deny(reason) {
  emit({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'deny',
      permissionDecisionReason: reason,
    },
  });
  process.exit(0);
}

const PRD_REASON =
  'docs/PRD.md is read-only: the owner maintains it. Note the requirement issue for the next STOP instead.';
const ENV_REASON = '.env files are off limits: no secrets in this project. Use .env.example for documentation.';

/** True when a shell command writes to, moves or deletes a path matching `target` (a regex source). */
export function shellWrites(cmd, target) {
  const t = `['"]?(?:\\./)?${target}`;
  const patterns = [
    `>>?\\s*${t}`, // redirection onto the file
    `\\btee\\b[^|;&]*${t}`,
    `\\b(?:sed|perl)\\s+-[a-zA-Z]*i[^|;&]*${t}`, // in-place edit
    `\\b(?:rm|mv|cp|truncate|dd|touch)\\b[^|;&]*${t}`,
    `\\bgit\\s+(?:checkout|restore|rm|mv)\\b[^|;&]*${t}`,
  ];
  return patterns.some((p) => new RegExp(p).test(cmd));
}

if (input.tool_name === 'Bash') {
  const cmd = String(toolInput.command ?? '');
  if (shellWrites(cmd, 'docs/PRD\\.md')) deny(PRD_REASON);
  if (shellWrites(cmd, '(?:[\\w./-]*/)?\\.env(?!\\.example)(?:\\.[\\w-]+)?(?![\\w.])')) deny(ENV_REASON);
  process.exit(0);
}

const rel = projectRelative(toolInput.file_path ?? toolInput.notebook_path);
if (rel && isPrd(rel)) deny(PRD_REASON);
const target = rel ?? String(toolInput.file_path ?? '');
if (isEnvFile(target)) deny(ENV_REASON);
process.exit(0);
