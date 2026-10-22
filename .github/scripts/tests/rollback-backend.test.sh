#!/bin/bash
# Exercise the real rollback script using stub executables, never Docker or a server.
set -euo pipefail

test_script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
rollback_script="$test_script_dir/../rollback-backend.sh"
test_directory=$(mktemp -d "${TMPDIR:-/tmp}/studyfactory-rollback-test.XXXXXX")
cleanup() {
  rm -f "$test_directory/docker" "$test_directory/curl" "$test_directory/sleep" \
    "$test_directory/commands.log" "$test_directory/output.log"
  rmdir "$test_directory"
}
trap cleanup EXIT
for stub_command in docker curl sleep; do
  install -m 755 "$test_script_dir/fixtures/rollback-command-stub.sh" "$test_directory/$stub_command"
done

export PATH="$test_directory:$PATH"
export ROLLBACK_TEST_LOG="$test_directory/commands.log"

run_case() {
  export ROLLBACK_TEST_SCENARIO=$1
  : > "$ROLLBACK_TEST_LOG"
  case_status=0
  sh "$rollback_script" > "$test_directory/output.log" 2>&1 || case_status=$?
}

assert_status() {
  if [ "$case_status" -ne "$1" ]; then
    echo "FAIL $ROLLBACK_TEST_SCENARIO: expected status $1, got $case_status" >&2
    cat "$test_directory/output.log" >&2
    exit 1
  fi
}

assert_no_mutation() {
  if grep -Eq '^docker (tag|compose)|^curl ' "$ROLLBACK_TEST_LOG"; then
    echo "FAIL $ROLLBACK_TEST_SCENARIO: unsafe image reached a mutation or readiness check" >&2
    cat "$ROLLBACK_TEST_LOG" >&2
    exit 1
  fi
}

for scenario in missing unlabelled wrong-label inspect-error; do
  run_case "$scenario"
  assert_status 1
  assert_no_mutation
done

run_case safe
assert_status 0
grep -Fxq 'docker tag studyfactory-backend:rollback studyfactory-backend:latest' "$ROLLBACK_TEST_LOG"
grep -Fxq 'docker compose --env-file .env -f compose.prod.yml up -d --no-build --force-recreate backend' "$ROLLBACK_TEST_LOG"
[ "$(grep -c '^curl ' "$ROLLBACK_TEST_LOG")" -eq 2 ]
grep -Fxq 'sleep 5' "$ROLLBACK_TEST_LOG"

run_case tag-error
assert_status 1
if grep -Eq '^docker compose|^curl ' "$ROLLBACK_TEST_LOG"; then
  echo 'FAIL tag-error: rollback continued after retag failed' >&2
  exit 1
fi

run_case recreate-error
assert_status 1
if grep -q '^curl ' "$ROLLBACK_TEST_LOG"; then
  echo 'FAIL recreate-error: rollback continued after recreation failed' >&2
  exit 1
fi

run_case unhealthy
assert_status 1
[ "$(grep -c '^curl ' "$ROLLBACK_TEST_LOG")" -eq 30 ]
grep -Fxq 'docker compose --env-file .env -f compose.prod.yml logs --tail=100 backend' "$ROLLBACK_TEST_LOG"

echo 'Rollback safety: 8 scenarios passed (stubbed commands only).'
