#!/usr/bin/env bash
# LeaveWise quality gate: runs every check, prints a short summary, exits non-zero if any check fails.
# CI runs this same script (plus the Docker build).
#
# Usage: scripts/quality-gate.sh [--only backend|frontend|scan]
#   --only narrows the run while iterating. Run the full gate before every commit.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ONLY="${2:-}"
if [[ "${1:-}" == "--only" && -z "$ONLY" ]]; then
  echo "usage: $0 [--only backend|frontend|scan]" >&2
  exit 2
fi
if [[ -n "${1:-}" && "${1:-}" != "--only" ]]; then
  echo "usage: $0 [--only backend|frontend|scan]" >&2
  exit 2
fi

declare -a NAMES=() RESULTS=() TIMES=()
FAILED=0

# run <name> <command...>: runs a check in its own section and records PASS or FAIL.
run() {
  local name="$1"
  shift
  echo
  echo "== ${name} =="
  local start=$SECONDS
  if "$@"; then
    RESULTS+=("PASS")
  else
    RESULTS+=("FAIL")
    FAILED=1
  fi
  NAMES+=("$name")
  TIMES+=("$((SECONDS - start))s")
}

# skip <name> <reason>: records a check that could not apply yet, with the reason. Never counts as a pass.
skip() {
  echo
  echo "== $1 =="
  echo "SKIPPED: $2"
  NAMES+=("$1")
  RESULTS+=("SKIP ($2)")
  TIMES+=("0s")
}

wants() { [[ -z "$ONLY" || "$ONLY" == "$1" ]]; }

backend_tests() { (cd "$ROOT/backend" && ./mvnw -B -ntp verify); }
backend_format() { (cd "$ROOT/backend" && ./mvnw -B -ntp spotless:check); }
backend_mutation() { (cd "$ROOT/backend" && ./mvnw -B -ntp test-compile pitest:mutationCoverage); }

planner_has_classes() {
  find "$ROOT/backend/src/main/java/dev/leavewise/planner" -name '*.java' ! -name 'package-info.java' | grep -q .
}

frontend_install() {
  if [[ ! -d "$ROOT/frontend/node_modules" ]]; then
    (cd "$ROOT/frontend" && npm ci --no-fund --no-audit)
  fi
}
frontend_lint() { (cd "$ROOT/frontend" && npm run --silent lint); }
frontend_format() { (cd "$ROOT/frontend" && npm run --silent format:check); }
frontend_tests() { (cd "$ROOT/frontend" && npm test --silent); }
frontend_build() { (cd "$ROOT/frontend" && npm run --silent build); }

# Fails on doctor failures (exit 1) and on warnings, so the Astryx setup stays clean.
astryx_doctor() {
  local out
  out="$(cd "$ROOT/frontend" && npm run --silent astryx -- doctor 2>&1)"
  local status=$?
  echo "$out" | tail -8
  if [[ $status -ne 0 ]]; then return 1; fi
  if echo "$out" | grep -Eq '^warn: [1-9]'; then
    echo "astryx doctor reported warnings (see above)."
    return 1
  fi
}

# Starts the jar from "Backend tests" serving the page from "Frontend build" (fixture holidays, a
# free port), then runs the real-browser check: overflow, CSP, touch targets, axe, plan time.
# The check loads the page 8 times (about 24 API calls in a few seconds), more than the production
# burst of 20, so this jar gets a larger burst; the rate limit itself is tested in RateLimitFilterTest.
browser_check() {
  local port log pid status
  port="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  log="$(mktemp)"
  java -jar "$ROOT/backend/target/leavewise.jar" --server.port="$port" \
    --leavewise.holidays.location="file:$ROOT/backend/src/test/resources/fixtures/holidays/india-*.json" \
    --leavewise.holidays.allow-test-data=true \
    --leavewise.rate-limit.burst=200 \
    --spring.web.resources.static-locations="file:$ROOT/frontend/dist/" >"$log" 2>&1 &
  pid=$!
  for _ in $(seq 1 60); do
    curl -fs "http://127.0.0.1:$port/actuator/health" >/dev/null && break
    sleep 0.5
  done
  (cd "$ROOT/frontend" && node scripts/browser-check.mjs "http://127.0.0.1:$port")
  status=$?
  kill "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
  if [[ $status -ne 0 ]]; then tail -20 "$log"; fi
  rm -f "$log"
  return $status
}

hook_tests() { (cd "$ROOT" && node --test .claude/hooks/*.test.mjs); }

# Scans every tracked and new (not ignored) file, and refuses any committed .env file.
secret_scan() {
  local envs
  envs="$(cd "$ROOT" && git ls-files --cached --others --exclude-standard | grep -E '(^|/)\.env($|\.)' | grep -v '\.env\.example$' || true)"
  if [[ -n "$envs" ]]; then
    echo ".env files must not be in the repository:"
    echo "$envs"
    return 1
  fi
  (cd "$ROOT" && git ls-files --cached --others --exclude-standard -z |
    xargs -0 frontend/node_modules/.bin/secretlint --secretlintrc .secretlintrc.json --maskSecrets)
}

if wants backend; then
  run "Backend tests (mvn verify)" backend_tests
  run "Backend format (Spotless)" backend_format
  if planner_has_classes; then
    run "Planner mutation testing (PIT, threshold in pom.xml)" backend_mutation
  else
    skip "Planner mutation testing (PIT, threshold in pom.xml)" "no planner classes yet"
  fi
fi

if wants frontend || wants scan; then
  run "Frontend dependencies (npm ci if missing)" frontend_install
fi

if wants frontend; then
  run "Frontend lint (ESLint)" frontend_lint
  run "Frontend format (Prettier)" frontend_format
  run "Frontend tests (Vitest + axe)" frontend_tests
  run "Frontend typecheck and build (tsc + Vite)" frontend_build
  run "Astryx doctor" astryx_doctor
fi

if [[ -z "$ONLY" ]]; then
  run "Browser check (Chromium: overflow, CSP, touch targets, axe)" browser_check
fi

if wants scan; then
  run "Claude Code hook tests (node --test)" hook_tests
  run "Secret scan (secretlint + no .env files)" secret_scan
fi

echo
echo "== Quality gate summary =="
for i in "${!NAMES[@]}"; do
  printf '%-58s %-6s %s\n' "${NAMES[$i]}" "${TIMES[$i]}" "${RESULTS[$i]}"
done
if [[ $FAILED -ne 0 ]]; then
  echo "RESULT: FAIL"
  exit 1
fi
echo "RESULT: PASS"
