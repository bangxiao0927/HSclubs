#!/usr/bin/env bash
# Checks the production HTTP frontend and backend API, plus the hsclubs,
# caddy, and mysql systemd services, and records the result of each check.
# Sends a webhook alert only when a check's status changes (ok -> fail or
# fail -> ok), never on every run, so a steady failure does not spam the
# configured webhook on every invocation from cron or a systemd timer.
#
# No secrets live in this repository: the webhook URL is read from the
# HEALTH_CHECK_WEBHOOK_URL environment variable, never hardcoded or
# committed. This script takes no arguments and reads no config file of its
# own; scripts/install-observability.sh is what loads the optional, private
# observability env file into the environment before invoking it, via a
# systemd `EnvironmentFile=` directive or an equivalent `set -a` wrapper in
# the cron entry.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# Production serves the built frontend through Caddy on ports 80/443; it does
# not run Vite's preview server on port 4173. A 3xx response from this local
# HTTP probe still proves that Caddy reached its HTTPS redirect handler.
FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1/}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/api/clubs}"
# Space-separated systemd unit names to check with `systemctl is-active`.
HEALTH_CHECK_SERVICES="${HEALTH_CHECK_SERVICES:-hsclubs.service caddy.service mysql.service}"
# "system" (default, reading status needs no privilege) or "user", matching
# deploy-main.sh's SYSTEMD_SCOPE naming for a user-scope backend deployment.
HEALTH_CHECK_SYSTEMD_SCOPE="${HEALTH_CHECK_SYSTEMD_SCOPE:-system}"
# Where each check's last observed status is recorded, one small file per
# check, so a later run can detect a transition. Defaults under the XDG
# state directory so no root is required to create or write it.
HEALTH_CHECK_STATE_DIR="${HEALTH_CHECK_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/hsclubs/health-check}"
# Generic webhook endpoint; unset means alerts are logged but not delivered.
HEALTH_CHECK_WEBHOOK_URL="${HEALTH_CHECK_WEBHOOK_URL:-}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\b'/\\b}"
  value="${value//$'\f'/\\f}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

read_previous_status() {
  local check_name="$1"
  local status_file="$HEALTH_CHECK_STATE_DIR/$check_name.status"

  if [[ -r "$status_file" ]]; then
    cat "$status_file"
  else
    printf 'unknown'
  fi
}

write_current_status() {
  local check_name="$1"
  local status="$2"

  mkdir -p "$HEALTH_CHECK_STATE_DIR"
  printf '%s' "$status" > "$HEALTH_CHECK_STATE_DIR/$check_name.status"
}

send_webhook_alert() {
  local check_name="$1"
  local previous_status="$2"
  local current_status="$3"
  local detail="$4"
  local payload

  payload="$(printf '{"check":"%s","previous_status":"%s","status":"%s","detail":"%s","host":"%s","timestamp":"%s"}' \
    "$(json_escape "$check_name")" \
    "$(json_escape "$previous_status")" \
    "$(json_escape "$current_status")" \
    "$(json_escape "$detail")" \
    "$(json_escape "$(hostname 2>/dev/null || printf 'unknown')")" \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')")"

  if [[ -z "$HEALTH_CHECK_WEBHOOK_URL" ]]; then
    log "State transition for $check_name: $previous_status -> $current_status (no webhook configured)"
    return
  fi

  log "State transition for $check_name: $previous_status -> $current_status; sending webhook alert"
  if ! curl -fsS --connect-timeout 5 --max-time 10 \
    -H 'Content-Type: application/json' \
    -d "$payload" \
    "$HEALTH_CHECK_WEBHOOK_URL" >/dev/null 2>&1; then
    log "Failed to deliver webhook alert for $check_name"
  fi
}

# Records the outcome of one check, comparing it against the last recorded
# status and alerting only when it differs. Always overwrites the recorded
# status with the current one, including the first-ever run (transitioning
# from "unknown").
evaluate_check() {
  local check_name="$1"
  local current_status="$2"
  local detail="$3"
  local previous_status

  previous_status="$(read_previous_status "$check_name")"
  if [[ "$previous_status" != "$current_status" ]]; then
    send_webhook_alert "$check_name" "$previous_status" "$current_status" "$detail"
  fi
  write_current_status "$check_name" "$current_status"
}

check_http_endpoint() {
  local check_name="$1"
  local url="$2"
  local status="ok"

  if ! curl -fsS --connect-timeout 5 --max-time 10 -o /dev/null "$url" >/dev/null 2>&1; then
    status="fail"
  fi
  evaluate_check "$check_name" "$status" "HTTP check for $url"
  [[ "$status" == "ok" ]]
}

check_systemd_service() {
  local check_name="$1"
  local service="$2"
  local status="ok"

  if [[ "$HEALTH_CHECK_SYSTEMD_SCOPE" == "user" ]]; then
    systemctl --user is-active --quiet "$service" 2>/dev/null || status="fail"
  else
    systemctl is-active --quiet "$service" 2>/dev/null || status="fail"
  fi
  evaluate_check "$check_name" "$status" "systemd service $service"
  [[ "$status" == "ok" ]]
}

main() {
  local overall_status=0
  local service

  # Without curl every HTTP probe below fails identically to a real outage,
  # which would alert once and then sit there looking like the site is down
  # forever. A missing dependency is an operator error, not an outage, so
  # fail loudly instead of reporting a false negative.
  command -v curl >/dev/null 2>&1 || die "Missing required command: curl"

  check_http_endpoint "frontend" "$FRONTEND_HEALTH_URL" || overall_status=1
  check_http_endpoint "backend_api" "$BACKEND_HEALTH_URL" || overall_status=1

  for service in $HEALTH_CHECK_SERVICES; do
    check_systemd_service "$service" "$service" || overall_status=1
  done

  exit "$overall_status"
}

# Guarded so tests can source this file to reach the individual functions
# above without running the full check.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
