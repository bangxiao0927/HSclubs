#!/usr/bin/env bash
# Focused tests for scripts/health-check.sh. No test framework: each check is
# a small function that reports pass/fail, run against fixtures under a
# throwaway temp directory. Nothing here touches the host's real curl,
# systemctl, or network -- every PATH used below is a fixture created by this
# script.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HEALTH_CHECK="$SCRIPT_DIR/health-check.sh"

TESTS_RUN=0
TESTS_FAILED=0

FIXTURE_ROOT="$(mktemp -d)"
cleanup() {
  rm -rf "$FIXTURE_ROOT"
}
trap cleanup EXIT

fail() {
  TESTS_FAILED=$((TESTS_FAILED + 1))
  printf 'FAIL: %s\n' "$*" >&2
}

pass() {
  printf 'ok - %s\n' "$*"
}

run_test() {
  local name="$1"
  shift
  TESTS_RUN=$((TESTS_RUN + 1))
  if "$@"; then
    pass "$name"
  else
    fail "$name"
  fi
}

assert_eq() {
  local expected="$1"
  local actual="$2"
  local message="${3:-}"
  if [[ "$expected" == "$actual" ]]; then
    return 0
  fi
  printf '  expected: %s\n  actual:   %s\n  %s\n' "$expected" "$actual" "$message" >&2
  return 1
}

mkdir -p "$FIXTURE_ROOT/bin" "$FIXTURE_ROOT/home"

# Fake curl: logs every invocation, and treats a -d/--data invocation as a
# webhook delivery (captures the JSON payload) versus a plain check request
# (controlled by FAKE_CHECK_EXIT). Never touches the network.
cat > "$FIXTURE_ROOT/bin/curl" <<'SH'
#!/usr/bin/env bash
echo "curl $*" >> "$CURL_LOG"
has_data=0
payload=""
prev=""
for arg in "$@"; do
  if [[ "$prev" == "-d" || "$prev" == "--data" ]]; then
    payload="$arg"
  fi
  if [[ "$arg" == "-d" || "$arg" == "--data" ]]; then
    has_data=1
  fi
  prev="$arg"
done
if [[ "$has_data" == "1" ]]; then
  printf '%s' "$payload" > "$WEBHOOK_PAYLOAD_FILE"
  exit "${FAKE_WEBHOOK_EXIT:-0}"
fi
exit "${FAKE_CHECK_EXIT:-0}"
SH
chmod +x "$FIXTURE_ROOT/bin/curl"

# Fake systemctl: only understands `is-active --quiet NAME` (and the --user
# variant), controlled per unit by FAKE_ACTIVE_UNITS (space-separated).
cat > "$FIXTURE_ROOT/bin/systemctl" <<'SH'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SYSTEMCTL_LOG"
args=("$@")
if [[ "${args[0]}" == "--user" ]]; then
  args=("${args[@]:1}")
fi
unit="${args[2]}"
for active in $FAKE_ACTIVE_UNITS; do
  if [[ "$active" == "$unit" ]]; then
    exit 0
  fi
done
exit 1
SH
chmod +x "$FIXTURE_ROOT/bin/systemctl"

run_isolated() {
  local env_assignments="$1"
  local snippet="$2"
  env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    CURL_LOG="$FIXTURE_ROOT/curl.log" \
    SYSTEMCTL_LOG="$FIXTURE_ROOT/systemctl.log" \
    WEBHOOK_PAYLOAD_FILE="$FIXTURE_ROOT/webhook-payload.json" \
    bash --noprofile --norc -c "
      $env_assignments
      # shellcheck source=/dev/null
      source '$HEALTH_CHECK'
      $snippet
    " 2>&1
}

reset_logs() {
  : > "$FIXTURE_ROOT/curl.log"
  : > "$FIXTURE_ROOT/systemctl.log"
  rm -f "$FIXTURE_ROOT/webhook-payload.json"
}

### check_http_endpoint: pass/fail and state recording ######################

test_check_http_endpoint_passes_and_records_ok() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-http-ok"
  local status=0
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' FAKE_CHECK_EXIT=0" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null || status=$?
  [[ "$status" -eq 0 ]] || { printf '  expected exit 0, got %s\n' "$status" >&2; return 1; }
  assert_eq "ok" "$(cat "$state_dir/frontend.status")" "must record an ok status file"
}

test_check_http_endpoint_fails_and_records_fail() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-http-fail"
  local status=0
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' FAKE_CHECK_EXIT=7" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit\n' >&2; return 1; }
  assert_eq "fail" "$(cat "$state_dir/frontend.status")" "must record a fail status file"
}

### check_systemd_service: pass/fail and state recording #####################

test_check_systemd_service_active_passes() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-svc-active"
  local status=0
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' FAKE_ACTIVE_UNITS='hsclubs.service'" '
    check_systemd_service hsclubs.service hsclubs.service
  ' >/dev/null || status=$?
  [[ "$status" -eq 0 ]] || { printf '  expected exit 0, got %s\n' "$status" >&2; return 1; }
  assert_eq "ok" "$(cat "$state_dir/hsclubs.service.status")" "must record an ok status file"
}

test_check_systemd_service_inactive_fails() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-svc-inactive"
  local status=0
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' FAKE_ACTIVE_UNITS=''" '
    check_systemd_service mysql.service mysql.service
  ' >/dev/null || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit\n' >&2; return 1; }
  assert_eq "fail" "$(cat "$state_dir/mysql.service.status")" "must record a fail status file"
}

### Alerts fire only on state transitions ####################################

test_no_alert_when_status_is_unchanged_ok() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-no-transition-ok"
  mkdir -p "$state_dir"
  printf 'ok' > "$state_dir/frontend.status"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' HEALTH_CHECK_WEBHOOK_URL='http://example.invalid/webhook' FAKE_CHECK_EXIT=0" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ ! -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  must not send a webhook when status stays ok\n' >&2
    return 1
  }
}

test_no_alert_when_status_is_unchanged_fail() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-no-transition-fail"
  mkdir -p "$state_dir"
  printf 'fail' > "$state_dir/frontend.status"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' HEALTH_CHECK_WEBHOOK_URL='http://example.invalid/webhook' FAKE_CHECK_EXIT=7" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ ! -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  must not send a webhook on a repeated failure\n' >&2
    return 1
  }
}

test_alert_sent_on_ok_to_fail_transition() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-transition-fail"
  mkdir -p "$state_dir"
  printf 'ok' > "$state_dir/frontend.status"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' HEALTH_CHECK_WEBHOOK_URL='http://example.invalid/webhook' FAKE_CHECK_EXIT=7" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  expected a webhook to be sent on an ok -> fail transition\n' >&2
    return 1
  }
  local payload
  payload="$(cat "$FIXTURE_ROOT/webhook-payload.json")"
  [[ "$payload" == *'"check":"frontend"'* && "$payload" == *'"previous_status":"ok"'* && "$payload" == *'"status":"fail"'* ]] || {
    printf '  expected a payload naming the check and both statuses, got: %s\n' "$payload" >&2
    return 1
  }
}

test_alert_sent_on_fail_to_ok_recovery() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-transition-recovery"
  mkdir -p "$state_dir"
  printf 'fail' > "$state_dir/frontend.status"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' HEALTH_CHECK_WEBHOOK_URL='http://example.invalid/webhook' FAKE_CHECK_EXIT=0" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  expected a webhook to be sent on a fail -> ok recovery\n' >&2
    return 1
  }
  local payload
  payload="$(cat "$FIXTURE_ROOT/webhook-payload.json")"
  [[ "$payload" == *'"previous_status":"fail"'* && "$payload" == *'"status":"ok"'* ]] || {
    printf '  expected a recovery payload, got: %s\n' "$payload" >&2
    return 1
  }
}

test_no_webhook_configured_does_not_invoke_curl_with_data() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-no-webhook-configured"
  mkdir -p "$state_dir"
  printf 'ok' > "$state_dir/frontend.status"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' FAKE_CHECK_EXIT=7" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ ! -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  must not attempt delivery when no webhook URL is configured\n' >&2
    return 1
  }
}

test_first_run_from_unknown_does_not_alert() {
  reset_logs
  local state_dir="$FIXTURE_ROOT/state-first-run"
  run_isolated "export HEALTH_CHECK_STATE_DIR='$state_dir' HEALTH_CHECK_WEBHOOK_URL='http://example.invalid/webhook' FAKE_CHECK_EXIT=0" '
    check_http_endpoint frontend http://example.invalid/
  ' >/dev/null
  [[ -f "$FIXTURE_ROOT/webhook-payload.json" ]] || {
    printf '  expected an alert on the very first observed status (transition from unknown)\n' >&2
    return 1
  }
}

### No secrets: webhook URL is never hardcoded ################################

test_health_check_script_has_no_hardcoded_webhook_url() {
  ! grep -Eq 'https?://(hooks\.slack\.com|discord\.com/api/webhooks)' "$HEALTH_CHECK"
}

test_default_frontend_probe_targets_local_proxy() {
  local output
  output="$(run_isolated "" 'printf "%s" "$FRONTEND_HEALTH_URL"')"
  assert_eq "http://127.0.0.1/" "$output" "production does not run a Vite preview server"
}

test_json_escape_handles_control_characters() {
  local output
  output="$(run_isolated "" 'json_escape $'\''line 1\nline 2\tvalue\r'\''')"
  assert_eq 'line 1\nline 2\tvalue\r' "$output" "webhook payloads must remain valid JSON"
}

### Run everything ############################################################

run_test "check_http_endpoint: passes and records ok" test_check_http_endpoint_passes_and_records_ok
run_test "check_http_endpoint: fails and records fail" test_check_http_endpoint_fails_and_records_fail
run_test "check_systemd_service: active passes" test_check_systemd_service_active_passes
run_test "check_systemd_service: inactive fails" test_check_systemd_service_inactive_fails
run_test "alerts: no webhook when status is unchanged (ok)" test_no_alert_when_status_is_unchanged_ok
run_test "alerts: no webhook when status is unchanged (fail)" test_no_alert_when_status_is_unchanged_fail
run_test "alerts: webhook sent on ok -> fail transition" test_alert_sent_on_ok_to_fail_transition
run_test "alerts: webhook sent on fail -> ok recovery" test_alert_sent_on_fail_to_ok_recovery
run_test "alerts: no delivery attempt without a configured webhook" test_no_webhook_configured_does_not_invoke_curl_with_data
run_test "alerts: first observed status alerts (unknown -> status)" test_first_run_from_unknown_does_not_alert
run_test "no hardcoded webhook URL in the script" test_health_check_script_has_no_hardcoded_webhook_url
run_test "defaults: frontend probe targets the local production proxy" test_default_frontend_probe_targets_local_proxy
run_test "webhook JSON: control characters are escaped" test_json_escape_handles_control_characters

printf '\n%d run, %d failed\n' "$TESTS_RUN" "$TESTS_FAILED"
[[ "$TESTS_FAILED" -eq 0 ]]
