#!/usr/bin/env bash
# Installs periodic health checks (scripts/health-check.sh) and nightly MySQL
# backups (scripts/backup-mysql.sh) as either systemd --user timers or cron
# entries for the current, non-root user. Never touches /etc or requires
# sudo: systemd-user units live under ~/.config/systemd/user, and the cron
# fallback edits only the invoking user's own crontab.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# Optional, private (not committed) environment file the installed units and
# cron entries source for secrets such as HEALTH_CHECK_WEBHOOK_URL. Its
# absence is not an error; both scripts work with defaults or plain
# environment variables instead.
OBSERVABILITY_ENV_FILE="${OBSERVABILITY_ENV_FILE:-${XDG_CONFIG_HOME:-$HOME/.config}/hsclubs/observability.env}"

HEALTH_CHECK_INTERVAL="${HEALTH_CHECK_INTERVAL:-5min}"
BACKUP_SCHEDULE="${BACKUP_SCHEDULE:-*-*-* 03:00:00}"
# Empty by default: cron mode derives its schedule from HEALTH_CHECK_INTERVAL
# / BACKUP_SCHEDULE (see translate_interval_to_cron and
# translate_oncalendar_to_cron below) so a user-supplied --health-interval or
# --backup-schedule is never silently ignored under cron. Set either of
# these explicitly (or pass --health-cron-schedule / --backup-cron-schedule)
# only for a schedule with no unambiguous cron equivalent.
HEALTH_CHECK_CRON_SCHEDULE="${HEALTH_CHECK_CRON_SCHEDULE:-}"
BACKUP_CRON_SCHEDULE="${BACKUP_CRON_SCHEDULE:-}"

MODE="${MODE:-auto}"
ACTION="install"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: install-observability.sh [--mode=auto|systemd-user|cron] [--uninstall]
                                 [--health-interval=DURATION] [--backup-schedule=ONCALENDAR]
                                 [--health-cron-schedule=CRON] [--backup-cron-schedule=CRON]

Installs (or removes, with --uninstall) a periodic health check and a
nightly MySQL backup for the current user. Requires no root privileges.

Under cron mode, --health-interval and --backup-schedule are translated to
an equivalent cron schedule automatically. For a schedule with no
unambiguous cron equivalent, pass --health-cron-schedule or
--backup-cron-schedule directly instead.
USAGE
}

parse_args() {
  local arg
  for arg in "$@"; do
    case "$arg" in
      --mode=*)
        MODE="${arg#--mode=}"
        ;;
      --uninstall)
        ACTION="uninstall"
        ;;
      --health-interval=*)
        HEALTH_CHECK_INTERVAL="${arg#--health-interval=}"
        ;;
      --backup-schedule=*)
        BACKUP_SCHEDULE="${arg#--backup-schedule=}"
        ;;
      --health-cron-schedule=*)
        HEALTH_CHECK_CRON_SCHEDULE="${arg#--health-cron-schedule=}"
        ;;
      --backup-cron-schedule=*)
        BACKUP_CRON_SCHEDULE="${arg#--backup-cron-schedule=}"
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unrecognized argument: $arg"
        ;;
    esac
  done

  case "$MODE" in
    auto|systemd-user|cron) ;;
    *) die "Unsupported --mode: $MODE (expected auto, systemd-user, or cron)" ;;
  esac
}

# systemd --user units are only reliable for unattended work if a user
# manager is reachable and remains alive after logout. Cron is the safe
# fallback, so auto-detection selects systemd-user only when lingering is on.
systemd_user_available() {
  systemctl --user show-environment >/dev/null 2>&1 \
    && command -v loginctl >/dev/null 2>&1 \
    && [[ "$(loginctl show-user "$(id -u)" --property=Linger --value 2>/dev/null)" == "yes" ]]
}

resolve_mode() {
  if [[ "$MODE" != "auto" ]]; then
    printf '%s\n' "$MODE"
    return
  fi
  if systemd_user_available; then
    printf 'systemd-user\n'
  else
    printf 'cron\n'
  fi
}

health_check_service_unit_content() {
  printf '%s\n' \
    '[Unit]' \
    'Description=HSclubs production health check' \
    '' \
    '[Service]' \
    'Type=oneshot' \
    "WorkingDirectory=$APP_DIR" \
    "EnvironmentFile=-$OBSERVABILITY_ENV_FILE" \
    "ExecStart=$APP_DIR/scripts/health-check.sh"
}

health_check_timer_unit_content() {
  printf '%s\n' \
    '[Unit]' \
    'Description=Run the HSclubs production health check periodically' \
    '' \
    '[Timer]' \
    'OnBootSec=1min' \
    "OnUnitActiveSec=$HEALTH_CHECK_INTERVAL" \
    'AccuracySec=30sec' \
    '' \
    '[Install]' \
    'WantedBy=timers.target'
}

backup_service_unit_content() {
  printf '%s\n' \
    '[Unit]' \
    'Description=HSclubs nightly MySQL backup' \
    '' \
    '[Service]' \
    'Type=oneshot' \
    "WorkingDirectory=$APP_DIR" \
    "EnvironmentFile=-$OBSERVABILITY_ENV_FILE" \
    "ExecStart=$APP_DIR/scripts/backup-mysql.sh"
}

backup_timer_unit_content() {
  printf '%s\n' \
    '[Unit]' \
    'Description=Run the HSclubs nightly MySQL backup' \
    '' \
    '[Timer]' \
    "OnCalendar=$BACKUP_SCHEDULE" \
    'Persistent=true' \
    '' \
    '[Install]' \
    'WantedBy=timers.target'
}

user_unit_dir() {
  printf '%s/systemd/user\n' "${XDG_CONFIG_HOME:-$HOME/.config}"
}

install_systemd_user_units() {
  local unit_dir
  unit_dir="$(user_unit_dir)"
  mkdir -p "$unit_dir"

  health_check_service_unit_content > "$unit_dir/hsclubs-health-check.service"
  health_check_timer_unit_content > "$unit_dir/hsclubs-health-check.timer"
  backup_service_unit_content > "$unit_dir/hsclubs-backup.service"
  backup_timer_unit_content > "$unit_dir/hsclubs-backup.timer"

  systemctl --user daemon-reload
  systemctl --user enable --now hsclubs-health-check.timer
  systemctl --user enable --now hsclubs-backup.timer
  log "Installed and started hsclubs-health-check.timer and hsclubs-backup.timer under $unit_dir"
}

uninstall_systemd_user_units() {
  local unit_dir
  unit_dir="$(user_unit_dir)"

  systemctl --user disable --now hsclubs-health-check.timer 2>/dev/null || true
  systemctl --user disable --now hsclubs-backup.timer 2>/dev/null || true
  rm -f \
    "$unit_dir/hsclubs-health-check.service" \
    "$unit_dir/hsclubs-health-check.timer" \
    "$unit_dir/hsclubs-backup.service" \
    "$unit_dir/hsclubs-backup.timer"
  systemctl --user daemon-reload
  log "Removed hsclubs health-check and backup systemd --user units"
}

# Marks every line this script manages so re-running install (or uninstall)
# can find and replace/remove exactly its own entries without disturbing any
# other crontab lines the user has.
CRON_MARKER="# managed by scripts/install-observability.sh -- do not edit by hand"

# Translates a systemd OnUnitActiveSec-style duration ("Nmin" or "Nh") into
# the equivalent cron minute/hour field. Only these two exact, evenly-spaced
# unit forms have an unambiguous cron equivalent; anything else (seconds,
# days, a combined "1h30min", or a minute/hour count that does not evenly
# divide 60/24, such as "7min" or "7h" -- cron's */N wraps at 60/24 and
# would leave a short period at the wrap point) must be provided directly
# via --health-cron-schedule.
translate_interval_to_cron() {
  local interval="$1"

  if [[ "$interval" =~ ^([0-9]+)min$ ]]; then
    local minutes=$((10#${BASH_REMATCH[1]}))
    [[ "$minutes" -ge 1 && "$minutes" -le 59 && $((60 % minutes)) -eq 0 ]] || return 1
    printf '*/%d * * * *\n' "$minutes"
    return 0
  fi

  if [[ "$interval" =~ ^([0-9]+)h$ ]]; then
    local hours=$((10#${BASH_REMATCH[1]}))
    [[ "$hours" -ge 1 && "$hours" -le 23 && $((24 % hours)) -eq 0 ]] || return 1
    printf '0 */%d * * *\n' "$hours"
    return 0
  fi

  return 1
}

# Translates a daily systemd OnCalendar expression ("*-*-* HH:MM:SS") into
# the equivalent cron "MM HH * * *" fields. Any other OnCalendar expression
# (a weekday filter, multiple times a day, a monthly date, ...) has no
# unambiguous cron equivalent and must be provided directly via
# --backup-cron-schedule.
translate_oncalendar_to_cron() {
  local schedule="$1"

  if [[ "$schedule" =~ ^\*-\*-\*\ ([0-9]{2}):([0-9]{2}):00$ ]]; then
    local hour=$((10#${BASH_REMATCH[1]}))
    local minute=$((10#${BASH_REMATCH[2]}))
    printf '%d %d * * *\n' "$minute" "$hour"
    return 0
  fi

  return 1
}

render_cron_entries() {
  local runner="set -a; [ ! -r \"\$1\" ] || . \"\$1\"; set +a; exec \"\$2\""
  local health_cron_schedule
  local backup_cron_schedule

  if [[ -n "$HEALTH_CHECK_CRON_SCHEDULE" ]]; then
    health_cron_schedule="$HEALTH_CHECK_CRON_SCHEDULE"
  else
    health_cron_schedule="$(translate_interval_to_cron "$HEALTH_CHECK_INTERVAL")" \
      || die "Cannot translate --health-interval=$HEALTH_CHECK_INTERVAL into an unambiguous cron schedule. Pass --health-cron-schedule='MIN HOUR * * *' explicitly."
  fi

  if [[ -n "$BACKUP_CRON_SCHEDULE" ]]; then
    backup_cron_schedule="$BACKUP_CRON_SCHEDULE"
  else
    backup_cron_schedule="$(translate_oncalendar_to_cron "$BACKUP_SCHEDULE")" \
      || die "Cannot translate --backup-schedule='$BACKUP_SCHEDULE' into an unambiguous cron schedule. Pass --backup-cron-schedule='MIN HOUR * * *' explicitly."
  fi

  # Cron does not understand systemd's EnvironmentFile directive. Run each
  # job through a small shell wrapper so cron and systemd-user mode load the
  # same optional private configuration file.
  printf "%s /bin/sh -c '%s' _ '%s' '%s' >> '%s/hsclubs-health-check.log' 2>&1 %s\n" \
    "$health_cron_schedule" "$runner" "$OBSERVABILITY_ENV_FILE" \
    "$APP_DIR/scripts/health-check.sh" "$APP_DIR" "$CRON_MARKER"
  printf "%s /bin/sh -c '%s' _ '%s' '%s' >> '%s/hsclubs-backup.log' 2>&1 %s\n" \
    "$backup_cron_schedule" "$runner" "$OBSERVABILITY_ENV_FILE" \
    "$APP_DIR/scripts/backup-mysql.sh" "$APP_DIR" "$CRON_MARKER"
}

existing_crontab_without_managed_entries() {
  crontab -l 2>/dev/null | grep -Fv "$CRON_MARKER" || true
}

install_cron_entries() {
  local existing
  local rendered
  # Captured fully before invoking `crontab -`, rather than piping both
  # `crontab -l` and `crontab -` together: some crontab implementations (and
  # this script's own tests) back the read and the write with the same
  # storage, and a single pipeline runs both concurrently, racing the write
  # against the read.
  existing="$(existing_crontab_without_managed_entries)"
  # Rendered (and any die() on an untranslatable schedule triggered) before
  # the crontab pipe below starts: die() inside `{ ...; render_cron_entries; } |
  # crontab -` only exits the pipeline's subshell, by which point the
  # existing lines have already been written to crontab -'s stdin, so a
  # failed re-install would otherwise still overwrite the crontab with the
  # existing lines and drop everything after the failure.
  rendered="$(render_cron_entries)"
  {
    [[ -z "$existing" ]] || printf '%s\n' "$existing"
    printf '%s' "$rendered"
  } | crontab -
  log "Installed cron entries for health-check.sh and backup-mysql.sh"
}

uninstall_cron_entries() {
  local existing
  existing="$(existing_crontab_without_managed_entries)"
  { [[ -z "$existing" ]] || printf '%s\n' "$existing"; } | crontab -
  log "Removed hsclubs health-check and backup cron entries"
}

# --uninstall must remove everything this script may have installed, not
# just whatever resolve_mode would choose today: lingering (and therefore
# auto mode's choice between systemd-user and cron) can change between an
# install and a later uninstall. Tears down both back-ends whenever their
# command is available; each individual teardown already tolerates units
# or cron lines that were never installed.
#
# Each back-end teardown is attempted independently: uninstall_systemd_user_units
# ends with `systemctl --user daemon-reload`, which fails on a host where
# systemctl exists but no user bus is reachable (a plain SSH session
# without lingering, or a container) -- exactly the hosts the cron
# fallback exists for. A failure there must not stop uninstall_cron_entries
# from running. Overall exit status is non-zero only when no back-end
# teardown that was actually attempted succeeded; a partial failure (one
# back-end torn down, the other failed) is logged but still exits zero,
# since --uninstall's job -- removing what it can -- was still done.
uninstall_all() {
  local torn_down_any=0
  local attempted_any=0

  if command -v systemctl >/dev/null 2>&1; then
    attempted_any=1
    if uninstall_systemd_user_units; then
      torn_down_any=1
    else
      log "Failed to remove systemd --user units; continuing with cron teardown"
    fi
  fi

  if command -v crontab >/dev/null 2>&1; then
    attempted_any=1
    if uninstall_cron_entries; then
      torn_down_any=1
    else
      log "Failed to remove cron entries"
    fi
  fi

  [[ "$attempted_any" == "1" ]] || die "Neither systemctl nor crontab is available; nothing to uninstall."
  [[ "$torn_down_any" == "1" ]] || die "Failed to remove any installed health-check/backup entries."
}

main() {
  parse_args "$@"

  if [[ "$ACTION" == "uninstall" ]]; then
    uninstall_all
    return
  fi

  local mode
  mode="$(resolve_mode)"

  case "$mode" in
    systemd-user)
      command -v systemctl >/dev/null 2>&1 || die "Missing required command: systemctl"
      install_systemd_user_units
      ;;
    cron)
      command -v crontab >/dev/null 2>&1 || die "Missing required command: crontab"
      install_cron_entries
      ;;
    *)
      die "Internal error: unresolved mode $mode"
      ;;
  esac
}

# Guarded so tests can source this file to reach the individual functions
# above without installing anything.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
