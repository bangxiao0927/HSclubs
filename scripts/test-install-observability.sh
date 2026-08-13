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
cat > "$FIXTURE_ROOT/bin/crontab" <<'SH'
#!/usr/bin/env bash
if [[ "$1" == "-l" ]]; then
  [[ -f "$CRONTAB_FILE" ]] || exit 1
  cat "$CRONTAB_FILE"
  exit 0
fi
if [[ "$1" == "-" ]]; then
  cat > "$CRONTAB_FILE"
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

test_cron_entries_load_observability_environment() {
  local output
  output="$(run_isolated "export OBSERVABILITY_ENV_FILE='$FIXTURE_ROOT/private-observability.env'" '
    render_cron_entries
  ')"

  local occurrences
  occurrences="$(grep -c "$FIXTURE_ROOT/private-observability.env" <<< "$output")"
  assert_eq "2" "$occurrences" "both cron jobs must load the optional observability environment"
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
run_test "auto mode: selects systemd-user when available" test_auto_mode_selects_systemd_user_when_available
run_test "auto mode: falls back to cron when systemd --user is unreachable" test_auto_mode_falls_back_to_cron_when_systemd_user_unavailable
run_test "auto mode: falls back to cron when lingering is disabled" test_auto_mode_falls_back_to_cron_without_lingering
run_test "cron: install adds marked lines" test_install_cron_entries_adds_marked_lines
run_test "cron: both jobs load the observability environment" test_cron_entries_load_observability_environment
run_test "cron: install preserves unrelated existing lines" test_install_cron_entries_preserves_unrelated_existing_lines
run_test "cron: install is idempotent" test_install_cron_entries_is_idempotent
run_test "cron: uninstall removes only managed lines" test_uninstall_cron_entries_removes_only_managed_lines
run_test "args: rejects an unsupported --mode" test_parse_args_rejects_unsupported_mode
run_test "args: --uninstall sets ACTION=uninstall" test_parse_args_sets_uninstall_action

printf '\n%d run, %d failed\n' "$TESTS_RUN" "$TESTS_FAILED"
[[ "$TESTS_FAILED" -eq 0 ]]
