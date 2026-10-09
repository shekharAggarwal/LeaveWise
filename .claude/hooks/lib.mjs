// Shared helpers for LeaveWise hooks. Plain Node (no dependencies) so hooks work on any OS with Node 22.
import {readFileSync} from 'node:fs';
import path from 'node:path';

export const projectDir = process.env.CLAUDE_PROJECT_DIR ?? process.cwd();

export function readHookInput() {
  try {
    return JSON.parse(readFileSync(0, 'utf8') || '{}');
  } catch {
    return {};
  }
}

/** Path relative to the project root, with forward slashes, or null when outside the project. */
export function projectRelative(filePath) {
  if (!filePath) return null;
  const rel = path.relative(projectDir, path.resolve(projectDir, filePath)).split(path.sep).join('/');
  return rel.startsWith('..') ? null : rel;
}

export function emit(output) {
  process.stdout.write(JSON.stringify(output));
}
