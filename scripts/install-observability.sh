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
BACKUP_CRON_SCHEDULE="${BACKUP_CRON_SCHEDULE:-0 3 * * *}"
HEALTH_CHECK_CRON_SCHEDULE="${HEALTH_CHECK_CRON_SCHEDULE:-*/5 * * * *}"

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

Installs (or removes, with --uninstall) a periodic health check and a
nightly MySQL backup for the current user. Requires no root privileges.
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

render_cron_entries() {
  local runner="set -a; [ ! -r \"\$1\" ] || . \"\$1\"; set +a; exec \"\$2\""

  # Cron does not understand systemd's EnvironmentFile directive. Run each
  # job through a small shell wrapper so cron and systemd-user mode load the
  # same optional private configuration file.
  printf "%s /bin/sh -c '%s' _ '%s' '%s' >> '%s/hsclubs-health-check.log' 2>&1 %s\n" \
    "$HEALTH_CHECK_CRON_SCHEDULE" "$runner" "$OBSERVABILITY_ENV_FILE" \
    "$APP_DIR/scripts/health-check.sh" "$APP_DIR" "$CRON_MARKER"
  printf "%s /bin/sh -c '%s' _ '%s' '%s' >> '%s/hsclubs-backup.log' 2>&1 %s\n" \
    "$BACKUP_CRON_SCHEDULE" "$runner" "$OBSERVABILITY_ENV_FILE" \
    "$APP_DIR/scripts/backup-mysql.sh" "$APP_DIR" "$CRON_MARKER"
}

existing_crontab_without_managed_entries() {
  crontab -l 2>/dev/null | grep -Fv "$CRON_MARKER" || true
}

install_cron_entries() {
  local existing
  # Captured fully before invoking `crontab -`, rather than piping both
  # `crontab -l` and `crontab -` together: some crontab implementations (and
  # this script's own tests) back the read and the write with the same
  # storage, and a single pipeline runs both concurrently, racing the write
  # against the read.
  existing="$(existing_crontab_without_managed_entries)"
  {
    [[ -z "$existing" ]] || printf '%s\n' "$existing"
    render_cron_entries
  } | crontab -
  log "Installed cron entries for health-check.sh and backup-mysql.sh"
}

uninstall_cron_entries() {
  local existing
  existing="$(existing_crontab_without_managed_entries)"
  { [[ -z "$existing" ]] || printf '%s\n' "$existing"; } | crontab -
  log "Removed hsclubs health-check and backup cron entries"
}

main() {
  parse_args "$@"

  local mode
  mode="$(resolve_mode)"

  case "$mode" in
    systemd-user)
      command -v systemctl >/dev/null 2>&1 || die "Missing required command: systemctl"
      if [[ "$ACTION" == "uninstall" ]]; then
        uninstall_systemd_user_units
      else
        install_systemd_user_units
      fi
      ;;
    cron)
      command -v crontab >/dev/null 2>&1 || die "Missing required command: crontab"
      if [[ "$ACTION" == "uninstall" ]]; then
        uninstall_cron_entries
      else
        install_cron_entries
      fi
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
