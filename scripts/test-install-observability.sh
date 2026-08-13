#!/usr/bin/env bash
# Focused tests for scripts/install-observability.sh. No test framework: each
# check is a small function that reports pass/fail, run against fixtures
# under a throwaway temp directory. Nothing here touches the host's real
# systemd --user instance or crontab -- every PATH and HOME used below is a
# fixture created by this script.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALLER="$SCRIPT_DIR/install-observability.sh"

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

REPO_DIR="$FIXTURE_ROOT/repo"
mkdir -p "$REPO_DIR/scripts"
cat > "$REPO_DIR/scripts/health-check.sh" <<'SH'
#!/usr/bin/env bash
exit 0
SH
cat > "$REPO_DIR/scripts/backup-mysql.sh" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$REPO_DIR/scripts/health-check.sh" "$REPO_DIR/scripts/backup-mysql.sh"

# Fake systemctl --user: logs every invocation and always succeeds, standing
# in for a real user systemd manager.
cat > "$FIXTURE_ROOT/bin/systemctl" <<'SH'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SYSTEMCTL_LOG"
exit 0
SH
chmod +x "$FIXTURE_ROOT/bin/systemctl"

# Fake systemctl --user that always fails, standing in for a host with no
# reachable user systemd manager (for example a bare SSH command context).
cat > "$FIXTURE_ROOT/bin/systemctl-unavailable" <<'SH'
#!/usr/bin/env bash
exit 1
SH
chmod +x "$FIXTURE_ROOT/bin/systemctl-unavailable"

# Auto mode should select systemd-user only when its manager survives logout.
cat > "$FIXTURE_ROOT/bin/loginctl" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "${FAKE_LINGER:-yes}"
SH
chmod +x "$FIXTURE_ROOT/bin/loginctl"

# Fake crontab: `-l` prints the fixture's stored crontab (or fails if none
# exists yet, like a real empty crontab), and `-` reads stdin into it.
# Mirrors real (Vixie) cron: a non-empty payload not ending in a newline is
# rejected with "missing newline before EOF, can't install" rather than
# silently accepted or truncated.
cat > "$FIXTURE_ROOT/bin/crontab" <<'SH'
#!/usr/bin/env bash
if [[ "$1" == "-l" ]]; then
  [[ -f "$CRONTAB_FILE" ]] || exit 1
  cat "$CRONTAB_FILE"
  exit 0
fi
if [[ "$1" == "-" ]]; then
  tmp="$(mktemp)"
  cat > "$tmp"
  if [[ -s "$tmp" && "$(tail -c1 "$tmp")" != "" ]]; then
    echo "crontab: new crontab file is missing newline before EOF, can't install." >&2
    rm -f "$tmp"
    exit 1
  fi
  mv "$tmp" "$CRONTAB_FILE"
  exit 0
fi
exit 1
SH
chmod +x "$FIXTURE_ROOT/bin/crontab"

run_isolated() {
  local env_assignments="$1"
  local snippet="$2"
  env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    SYSTEMCTL_LOG="$FIXTURE_ROOT/systemctl.log" \
    CRONTAB_FILE="$FIXTURE_ROOT/crontab.txt" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR'
      $env_assignments
      # shellcheck source=/dev/null
      source '$INSTALLER'
      $snippet
    " 2>&1
}

reset_state() {
  : > "$FIXTURE_ROOT/systemctl.log"
  rm -f "$FIXTURE_ROOT/crontab.txt"
}

### Unit content generation ####################################################

test_health_check_service_references_the_script_and_env_file() {
  local output
  output="$(run_isolated "export OBSERVABILITY_ENV_FILE='$FIXTURE_ROOT/home/.config/hsclubs/observability.env'" '
    health_check_service_unit_content
  ')"
  [[ "$output" == *"ExecStart=$REPO_DIR/scripts/health-check.sh"* ]] || {
    printf '  expected ExecStart to reference health-check.sh, got: %s\n' "$output" >&2
    return 1
  }
  [[ "$output" == *"EnvironmentFile=-$FIXTURE_ROOT/home/.config/hsclubs/observability.env"* ]] || {
    printf '  expected an optional EnvironmentFile entry, got: %s\n' "$output" >&2
    return 1
  }
}

test_backup_service_references_the_script() {
  local output
  output="$(run_isolated "" '
    backup_service_unit_content
  ')"
  [[ "$output" == *"ExecStart=$REPO_DIR/scripts/backup-mysql.sh"* ]] || {
    printf '  expected ExecStart to reference backup-mysql.sh, got: %s\n' "$output" >&2
    return 1
  }
}

test_health_check_timer_uses_configured_interval() {
  local output
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=10min" '
    health_check_timer_unit_content
  ')"
  [[ "$output" == *"OnUnitActiveSec=10min"* ]] || {
    printf '  expected OnUnitActiveSec=10min, got: %s\n' "$output" >&2
    return 1
  }
}

test_backup_timer_uses_configured_schedule() {
  local output
  output="$(run_isolated "export BACKUP_SCHEDULE='*-*-* 04:30:00'" '
    backup_timer_unit_content
  ')"
  [[ "$output" == *"OnCalendar=*-*-* 04:30:00"* ]] || {
    printf '  expected the configured OnCalendar schedule, got: %s\n' "$output" >&2
    return 1
  }
}

### systemd --user install/uninstall ###########################################

test_install_systemd_user_writes_units_and_enables_timers() {
  reset_state
  run_isolated "export XDG_CONFIG_HOME='$FIXTURE_ROOT/xdg-config-install'" '
    install_systemd_user_units
  ' >/dev/null

  local unit_dir="$FIXTURE_ROOT/xdg-config-install/systemd/user"
  [[ -f "$unit_dir/hsclubs-health-check.service" ]] || { printf '  missing health-check.service\n' >&2; return 1; }
  [[ -f "$unit_dir/hsclubs-health-check.timer" ]] || { printf '  missing health-check.timer\n' >&2; return 1; }
  [[ -f "$unit_dir/hsclubs-backup.service" ]] || { printf '  missing backup.service\n' >&2; return 1; }
  [[ -f "$unit_dir/hsclubs-backup.timer" ]] || { printf '  missing backup.timer\n' >&2; return 1; }

  local log
  log="$(cat "$FIXTURE_ROOT/systemctl.log")"
  [[ "$log" == *"daemon-reload"* ]] || { printf '  expected a daemon-reload, got: %s\n' "$log" >&2; return 1; }
  [[ "$log" == *"enable --now hsclubs-health-check.timer"* ]] || {
    printf '  expected the health-check timer to be enabled, got: %s\n' "$log" >&2
    return 1
  }
  [[ "$log" == *"enable --now hsclubs-backup.timer"* ]] || {
    printf '  expected the backup timer to be enabled, got: %s\n' "$log" >&2
    return 1
  }
}

test_uninstall_systemd_user_removes_units() {
  reset_state
  local xdg_config="$FIXTURE_ROOT/xdg-config-uninstall"
  run_isolated "export XDG_CONFIG_HOME='$xdg_config'" '
    install_systemd_user_units
  ' >/dev/null
  run_isolated "export XDG_CONFIG_HOME='$xdg_config'" '
    uninstall_systemd_user_units
  ' >/dev/null

  local unit_dir="$xdg_config/systemd/user"
  [[ ! -e "$unit_dir/hsclubs-health-check.service" ]] || { printf '  health-check.service still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-health-check.timer" ]] || { printf '  health-check.timer still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-backup.service" ]] || { printf '  backup.service still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-backup.timer" ]] || { printf '  backup.timer still present\n' >&2; return 1; }
}

### uninstall tears down both backends regardless of mode resolution #########

test_uninstall_removes_cron_entries_regardless_of_resolved_mode() {
  reset_state
  # Install under cron explicitly, simulating a prior run made while
  # lingering was disabled.
  run_isolated "" '
    MODE=cron
    install_cron_entries
  ' >/dev/null

  # Uninstall through the auto-resolving entrypoint, with lingering enabled
  # this time so resolve_mode alone would pick systemd-user; --uninstall
  # must still remove what was actually installed under cron.
  run_isolated "" '
    main --uninstall
  ' >/dev/null

  local crontab_content
  crontab_content="$(cat "$FIXTURE_ROOT/crontab.txt" 2>/dev/null || true)"
  [[ "$crontab_content" != *"health-check.sh"* && "$crontab_content" != *"backup-mysql.sh"* ]] || {
    printf '  expected --uninstall to remove cron entries installed while lingering was off, got: %s\n' "$crontab_content" >&2
    return 1
  }
}

test_uninstall_removes_systemd_user_units_regardless_of_resolved_mode() {
  reset_state
  local xdg_config="$FIXTURE_ROOT/xdg-config-uninstall-regardless"
  run_isolated "export XDG_CONFIG_HOME='$xdg_config'" '
    MODE=systemd-user
    install_systemd_user_units
  ' >/dev/null

  # Uninstall with lingering disabled this time, so resolve_mode alone
  # would pick cron; --uninstall must still remove the systemd-user units
  # that were actually installed.
  run_isolated "export XDG_CONFIG_HOME='$xdg_config' FAKE_LINGER=no" '
    main --uninstall
  ' >/dev/null

  local unit_dir="$xdg_config/systemd/user"
  [[ ! -e "$unit_dir/hsclubs-health-check.service" ]] || { printf '  health-check.service still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-health-check.timer" ]] || { printf '  health-check.timer still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-backup.service" ]] || { printf '  backup.service still present\n' >&2; return 1; }
  [[ ! -e "$unit_dir/hsclubs-backup.timer" ]] || { printf '  backup.timer still present\n' >&2; return 1; }
}

test_uninstall_removes_cron_entries_even_if_systemd_user_teardown_fails() {
  reset_state
  # Install under cron, simulating a plain SSH session without lingering:
  # `systemctl` is present on such a host but there is no reachable user
  # bus, so `systemctl --user daemon-reload` fails.
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  run_isolated '
    systemctl() {
      if [[ "$1" == "--user" && "$2" == "daemon-reload" ]]; then
        return 1
      fi
      return 0
    }
  ' '
    main --uninstall
  ' >/dev/null

  local crontab_content
  crontab_content="$(cat "$FIXTURE_ROOT/crontab.txt" 2>/dev/null || true)"
  [[ "$crontab_content" != *"health-check.sh"* && "$crontab_content" != *"backup-mysql.sh"* ]] || {
    printf '  expected --uninstall to remove cron entries even though systemctl --user daemon-reload failed, got: %s\n' "$crontab_content" >&2
    return 1
  }
}

### auto mode detection #########################################################

test_auto_mode_selects_systemd_user_when_available() {
  local output
  output="$(run_isolated "" '
    resolve_mode
  ')"
  assert_eq "systemd-user" "$output" "must prefer systemd-user when the user manager is reachable"
}

test_auto_mode_falls_back_to_cron_when_systemd_user_unavailable() {
  local output
  output="$(env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR'
      systemctl() { systemctl-unavailable \"\$@\"; }
      source '$INSTALLER'
      resolve_mode
    " 2>&1)"
  assert_eq "cron" "$output" "must fall back to cron when systemd --user is unreachable"
}

test_auto_mode_falls_back_to_cron_without_lingering() {
  local output
  output="$(run_isolated "export FAKE_LINGER=no" '
    resolve_mode
  ')"
  assert_eq "cron" "$output" "timers would stop after logout when lingering is disabled"
}

### cron install/uninstall: idempotent, marker-scoped #########################

test_install_cron_entries_adds_marked_lines() {
  reset_state
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  local crontab_content
  crontab_content="$(cat "$FIXTURE_ROOT/crontab.txt")"
  [[ "$crontab_content" == *"$REPO_DIR/scripts/health-check.sh"* ]] || {
    printf '  expected a cron line for health-check.sh, got: %s\n' "$crontab_content" >&2
    return 1
  }
  [[ "$crontab_content" == *"$REPO_DIR/scripts/backup-mysql.sh"* ]] || {
    printf '  expected a cron line for backup-mysql.sh, got: %s\n' "$crontab_content" >&2
    return 1
  }
}

test_install_cron_entries_preserves_unrelated_existing_lines() {
  reset_state
  printf '0 0 * * * /usr/bin/some-other-job\n' > "$FIXTURE_ROOT/crontab.txt"
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  local crontab_content
  crontab_content="$(cat "$FIXTURE_ROOT/crontab.txt")"
  [[ "$crontab_content" == *"/usr/bin/some-other-job"* ]] || {
    printf '  must preserve unrelated existing crontab entries, got: %s\n' "$crontab_content" >&2
    return 1
  }
}

test_install_cron_entries_payload_ends_with_trailing_newline() {
  reset_state
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  [[ -s "$FIXTURE_ROOT/crontab.txt" ]] || {
    printf '  expected install_cron_entries to write a non-empty crontab.txt\n' >&2
    return 1
  }
  [[ "$(tail -c1 "$FIXTURE_ROOT/crontab.txt")" == "" ]] || {
    printf '  expected the payload handed to crontab - to end with a newline (Vixie cron rejects a missing final newline), got tail byte: %q\n' "$(tail -c1 "$FIXTURE_ROOT/crontab.txt")" >&2
    return 1
  }
}

test_install_cron_entries_preserves_previous_entries_on_failed_reinstall() {
  reset_state
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  local before
  before="$(cat "$FIXTURE_ROOT/crontab.txt")"

  local status=0
  run_isolated "export HEALTH_CHECK_INTERVAL=90sec" '
    install_cron_entries
  ' >/dev/null 2>&1 || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  expected install_cron_entries to fail for an untranslatable interval\n' >&2
    return 1
  }

  local after
  after="$(cat "$FIXTURE_ROOT/crontab.txt")"
  assert_eq "$before" "$after" "a failed re-install must not drop the previously installed managed entries"
}

test_cron_entries_load_observability_environment() {
  local output
  output="$(run_isolated "export OBSERVABILITY_ENV_FILE='$FIXTURE_ROOT/private-observability.env'" '
    render_cron_entries
  ')"

  local occurrences
  occurrences="$(grep -c "$FIXTURE_ROOT/private-observability.env" <<< "$output")"
  assert_eq "2" "$occurrences" "both cron jobs must load the optional observability environment"
}

### cron mode must honor --health-interval / --backup-schedule, not the ######
### fixed HEALTH_CHECK_CRON_SCHEDULE / BACKUP_CRON_SCHEDULE defaults #########

test_cron_entries_translate_health_interval_into_cron_schedule() {
  local output
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=10min" '
    render_cron_entries
  ')"
  [[ "$output" == "*/10 * * * *"* ]] || {
    printf '  expected a cron line starting with */10 * * * *, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_translate_backup_schedule_into_cron_schedule() {
  local output
  output="$(run_isolated "export BACKUP_SCHEDULE='*-*-* 04:30:00'" '
    render_cron_entries
  ')"
  [[ "$output" == *$'\n'"30 4 * * *"* ]] || {
    printf '  expected the backup line to start with 30 4 * * *, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_reject_ambiguous_health_interval_with_actionable_error() {
  local output
  local status=0
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=90sec" '
    render_cron_entries
  ')" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit for an untranslatable interval\n' >&2; return 1; }
  [[ "$output" == *"--health-cron-schedule"* ]] || {
    printf '  expected the error to point at --health-cron-schedule, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_reject_ambiguous_backup_schedule_with_actionable_error() {
  local output
  local status=0
  output="$(run_isolated "export BACKUP_SCHEDULE='Mon *-*-* 03:00:00'" '
    render_cron_entries
  ')" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit for an untranslatable OnCalendar schedule\n' >&2; return 1; }
  [[ "$output" == *"--backup-cron-schedule"* ]] || {
    printf '  expected the error to point at --backup-cron-schedule, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_reject_non_divisor_health_interval_with_actionable_error() {
  local output
  local status=0
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=7min" '
    render_cron_entries
  ')" || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  expected a non-zero exit for a 7min interval: */7 * * * * is not a 7-minute period\n' >&2
    return 1
  }
  [[ "$output" == *"--health-cron-schedule"* ]] || {
    printf '  expected the error to point at --health-cron-schedule, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_reject_non_divisor_backup_hour_interval_with_actionable_error() {
  local output
  local status=0
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=7h" '
    render_cron_entries
  ')" || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  expected a non-zero exit for a 7h interval: 0 */7 * * * has a 3-hour wrap gap\n' >&2
    return 1
  }
  [[ "$output" == *"--health-cron-schedule"* ]] || {
    printf '  expected the error to point at --health-cron-schedule, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_honor_explicit_health_cron_schedule_override() {
  local output
  output="$(run_isolated "export HEALTH_CHECK_INTERVAL=90sec HEALTH_CHECK_CRON_SCHEDULE='7 8 * * *'" '
    render_cron_entries
  ')"
  [[ "$output" == "7 8 * * *"* ]] || {
    printf '  expected the explicit --health-cron-schedule override to win, got: %s\n' "$output" >&2
    return 1
  }
}

test_cron_entries_honor_explicit_backup_cron_schedule_override() {
  local output
  output="$(run_isolated "export BACKUP_SCHEDULE='Mon *-*-* 03:00:00' BACKUP_CRON_SCHEDULE='15 2 * * 1'" '
    render_cron_entries
  ')"
  [[ "$output" == *$'\n'"15 2 * * 1"* ]] || {
    printf '  expected the explicit --backup-cron-schedule override to win, got: %s\n' "$output" >&2
    return 1
  }
}

test_parse_args_sets_health_cron_schedule_override() {
  local output
  output="$(run_isolated "" '
    parse_args --health-cron-schedule="7 8 * * *"
    echo "$HEALTH_CHECK_CRON_SCHEDULE"
  ')"
  assert_eq "7 8 * * *" "$output" "--health-cron-schedule must set HEALTH_CHECK_CRON_SCHEDULE"
}

test_parse_args_sets_backup_cron_schedule_override() {
  local output
  output="$(run_isolated "" '
    parse_args --backup-cron-schedule="15 2 * * 1"
    echo "$BACKUP_CRON_SCHEDULE"
  ')"
  assert_eq "15 2 * * 1" "$output" "--backup-cron-schedule must set BACKUP_CRON_SCHEDULE"
}

test_install_cron_entries_is_idempotent() {
  reset_state
  run_isolated "" '
    install_cron_entries
  ' >/dev/null
  run_isolated "" '
    install_cron_entries
  ' >/dev/null

  local occurrences
  occurrences="$(grep -c "health-check.sh" "$FIXTURE_ROOT/crontab.txt")"
  assert_eq "1" "$occurrences" "re-running install must not duplicate the managed cron lines"
}

test_uninstall_cron_entries_removes_only_managed_lines() {
  reset_state
  printf '0 0 * * * /usr/bin/some-other-job\n' > "$FIXTURE_ROOT/crontab.txt"
  run_isolated "" '
    install_cron_entries
  ' >/dev/null
  run_isolated "" '
    uninstall_cron_entries
  ' >/dev/null

  local crontab_content
  crontab_content="$(cat "$FIXTURE_ROOT/crontab.txt")"
  [[ "$crontab_content" != *"health-check.sh"* && "$crontab_content" != *"backup-mysql.sh"* ]] || {
    printf '  expected the managed lines to be removed, got: %s\n' "$crontab_content" >&2
    return 1
  }
  [[ "$crontab_content" == *"/usr/bin/some-other-job"* ]] || {
    printf '  must preserve unrelated existing crontab entries on uninstall, got: %s\n' "$crontab_content" >&2
    return 1
  }
}

### Argument parsing ############################################################

test_parse_args_rejects_unsupported_mode() {
  local output
  local status=0
  output="$(run_isolated "" '
    parse_args --mode=bogus
  ')" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit for an unsupported mode\n' >&2; return 1; }
  [[ "$output" == *"Unsupported --mode"* ]] || {
    printf '  expected an actionable error, got: %s\n' "$output" >&2
    return 1
  }
}

test_parse_args_sets_uninstall_action() {
  local output
  output="$(run_isolated "" '
    parse_args --uninstall
    echo "$ACTION"
  ')"
  assert_eq "uninstall" "$output" "--uninstall must set ACTION=uninstall"
}

### Run everything ###############################################################

run_test "unit content: health-check service references the script and env file" test_health_check_service_references_the_script_and_env_file
run_test "unit content: backup service references the script" test_backup_service_references_the_script
run_test "unit content: health-check timer uses the configured interval" test_health_check_timer_uses_configured_interval
run_test "unit content: backup timer uses the configured schedule" test_backup_timer_uses_configured_schedule
run_test "systemd --user: install writes units and enables timers" test_install_systemd_user_writes_units_and_enables_timers
run_test "systemd --user: uninstall removes units" test_uninstall_systemd_user_removes_units
run_test "uninstall: removes cron entries regardless of resolved mode" test_uninstall_removes_cron_entries_regardless_of_resolved_mode
run_test "uninstall: removes systemd-user units regardless of resolved mode" test_uninstall_removes_systemd_user_units_regardless_of_resolved_mode
run_test "uninstall: removes cron entries even if systemd --user teardown fails" test_uninstall_removes_cron_entries_even_if_systemd_user_teardown_fails
run_test "auto mode: selects systemd-user when available" test_auto_mode_selects_systemd_user_when_available
run_test "auto mode: falls back to cron when systemd --user is unreachable" test_auto_mode_falls_back_to_cron_when_systemd_user_unavailable
run_test "auto mode: falls back to cron when lingering is disabled" test_auto_mode_falls_back_to_cron_without_lingering
run_test "cron: install adds marked lines" test_install_cron_entries_adds_marked_lines
run_test "cron: install payload ends with a trailing newline" test_install_cron_entries_payload_ends_with_trailing_newline
run_test "cron: both jobs load the observability environment" test_cron_entries_load_observability_environment
run_test "cron: translates --health-interval into a cron schedule" test_cron_entries_translate_health_interval_into_cron_schedule
run_test "cron: translates --backup-schedule into a cron schedule" test_cron_entries_translate_backup_schedule_into_cron_schedule
run_test "cron: rejects an ambiguous --health-interval with an actionable error" test_cron_entries_reject_ambiguous_health_interval_with_actionable_error
run_test "cron: rejects an ambiguous --backup-schedule with an actionable error" test_cron_entries_reject_ambiguous_backup_schedule_with_actionable_error
run_test "cron: rejects a non-divisor minute interval with an actionable error" test_cron_entries_reject_non_divisor_health_interval_with_actionable_error
run_test "cron: rejects a non-divisor hour interval with an actionable error" test_cron_entries_reject_non_divisor_backup_hour_interval_with_actionable_error
run_test "cron: --health-cron-schedule override wins over translation" test_cron_entries_honor_explicit_health_cron_schedule_override
run_test "cron: --backup-cron-schedule override wins over translation" test_cron_entries_honor_explicit_backup_cron_schedule_override
run_test "args: --health-cron-schedule sets HEALTH_CHECK_CRON_SCHEDULE" test_parse_args_sets_health_cron_schedule_override
run_test "args: --backup-cron-schedule sets BACKUP_CRON_SCHEDULE" test_parse_args_sets_backup_cron_schedule_override
run_test "cron: install preserves unrelated existing lines" test_install_cron_entries_preserves_unrelated_existing_lines
run_test "cron: a failed re-install preserves the previously installed managed entries" test_install_cron_entries_preserves_previous_entries_on_failed_reinstall
run_test "cron: install is idempotent" test_install_cron_entries_is_idempotent
run_test "cron: uninstall removes only managed lines" test_uninstall_cron_entries_removes_only_managed_lines
run_test "args: rejects an unsupported --mode" test_parse_args_rejects_unsupported_mode
run_test "args: --uninstall sets ACTION=uninstall" test_parse_args_sets_uninstall_action

printf '\n%d run, %d failed\n' "$TESTS_RUN" "$TESTS_FAILED"
[[ "$TESTS_FAILED" -eq 0 ]]
