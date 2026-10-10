#!/usr/bin/env bash
# Builds the hackathon submission ZIP: the whole project with its full git history, without node_modules or
# build output, after a secret scan of every tracked file and of the full history (docs/SUBMISSION.md).
#
# Usage: scripts/build-submission.sh            (from a clean working tree, on the branch to submit)
# Output: dist/leavewise-submission.zip, plus its size, SHA-256 and commit count.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT_DIR="$ROOT/dist"
ZIP="$OUT_DIR/leavewise-submission.zip"
SECRETLINT="$ROOT/frontend/node_modules/.bin/secretlint"

fail() {
  echo "build-submission: $*" >&2
  exit 1
}

# The ZIP must be exactly what is committed, so nothing local slips in or gets left out.
[[ -z "$(git status --porcelain)" ]] || fail "the working tree has uncommitted changes; commit or stash them first."
[[ -x "$SECRETLINT" ]] || fail "secretlint is missing; run 'cd frontend && npm ci' first."

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "== 1. Secret scan of tracked files =="
git ls-files -z | xargs -0 "$SECRETLINT" --secretlintrc .secretlintrc.json --maskSecrets
echo "No secrets in tracked files."

echo "== 2. Secret scan of the full git history =="
# Every version of every file ever committed, as one patch, scanned the same way.
git log -p --all --no-color --format='commit %H' >"$WORK/history.patch"
"$SECRETLINT" --secretlintrc .secretlintrc.json --maskSecrets "$WORK/history.patch"
forbidden="$(git log --all --name-only --format= | sort -u |
  grep -E '(^|/)\.env($|\.)|\.pem$|\.key$|\.p12$|\.jks$|id_rsa' | grep -v '\.env\.example$' || true)"
[[ -z "$forbidden" ]] || fail "these files appear in the history and must not be submitted: $forbidden"
echo "No secrets or key files anywhere in the history."

echo "== 3. ZIP from a fresh clone (full history; no node_modules, no build output) =="
# A clone holds only committed files, so ignored folders (node_modules, dist, target) can't get in.
git clone --quiet --no-local "$ROOT" "$WORK/LeaveWise"
git -C "$WORK/LeaveWise" remote remove origin # the local path of this machine is not part of the project
mkdir -p "$OUT_DIR"
rm -f "$ZIP"
(cd "$WORK" && zip -q -r -X "$ZIP" LeaveWise)

for dir in node_modules target dist; do
  if unzip -Z1 "$ZIP" | grep -qE "(^|/)$dir/"; then fail "the ZIP contains a $dir/ folder."; fi
done

echo
echo "Built $ZIP"
echo "  branch:  $(git rev-parse --abbrev-ref HEAD) at $(git rev-parse --short HEAD)"
echo "  commits: $(git rev-list --count HEAD)"
echo "  size:    $(du -h "$ZIP" | cut -f1)"
echo "  sha256:  $(sha256sum "$ZIP" | cut -d' ' -f1)"
