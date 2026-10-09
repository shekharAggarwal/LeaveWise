// Tests for guard-protected-files.mjs. Run: node --test .claude/hooks/ (part of scripts/quality-gate.sh).
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {test} from 'node:test';
import assert from 'node:assert/strict';

const here = path.dirname(fileURLToPath(import.meta.url));
const project = path.resolve(here, '../..');

function decide(toolName, toolInput) {
  const result = spawnSync(process.execPath, [path.join(here, 'guard-protected-files.mjs')], {
    input: JSON.stringify({tool_name: toolName, tool_input: toolInput}),
    env: {...process.env, CLAUDE_PROJECT_DIR: project},
    encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout ? JSON.parse(result.stdout).hookSpecificOutput.permissionDecision : 'allow';
}

const file = (rel) => ({file_path: path.join(project, rel)});
const bash = (command) => ({command});
const PRD = ['docs', 'PRD.md'].join('/'); // built at runtime so this file's own text doesn't trip the hook

const cases = [
  ['Edit', file(PRD), 'deny'],
  ['Write', file(PRD), 'deny'],
  ['Write', file('backend/.env'), 'deny'],
  ['Edit', file('.env.local'), 'deny'],
  ['Write', file('.env.example'), 'allow'],
  ['Write', file('docs/API.md'), 'allow'],
  ['Bash', bash(`echo hi > ${PRD}`), 'deny'],
  ['Bash', bash(`sed -i s/a/b/ ${PRD}`), 'deny'],
  ['Bash', bash(`git checkout -- ${PRD}`), 'deny'],
  ['Bash', bash(`rm ${PRD}`), 'deny'],
  ['Bash', bash(`grep -n Ties ${PRD} 2>/dev/null | head`), 'allow'],
  ['Bash', bash(`cat ${PRD} > /tmp/prd-copy.md`), 'allow'],
  ['Bash', bash('echo KEY=1 >> .env'), 'deny'],
  ['Bash', bash('cp .env.example .env.local'), 'deny'],
  ['Bash', bash('cat .env.example'), 'allow'],
  ['Bash', bash('echo x > frontend/.envrc-notes.md'), 'allow'],
];

for (const [tool, input, expected] of cases) {
  test(`${tool} ${JSON.stringify(input).replaceAll(project, '<repo>')} -> ${expected}`, () => {
    assert.equal(decide(tool, input), expected);
  });
}
