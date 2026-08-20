#!/usr/bin/env bash
# Backs up the production MySQL database into a gzip-compressed dump.
#
# Reads SPRING_DATASOURCE_URL, DB_USERNAME, and DB_PASSWORD from
# backend/.env without sourcing the file (the same approach as
# scripts/deploy-main.sh's read_env_value, kept self-contained here so this
# script has no dependency on deploy-main.sh), so no secret from that file
# ever becomes a real environment variable.
#
# Safety properties:
#   - The credentials are passed to mysqldump through a private, mode-0600
#     --defaults-extra-file rather than a command-line flag or a plain
#     environment variable, so they never appear in `ps` output.
#   - The dump is written to a temp file in the backup directory and only
#     gzip'd and renamed into place after gzip's own integrity check
#     (`gzip -t`) passes, so a reader never observes a partial or corrupt
#     backup at the final path.
#   - The final backup file and its containing directory are mode 0600/0700.
#   - Backups older than the retention window are pruned after a successful
#     run.
#   - A flock-based lock prevents two overlapping runs (for example a slow
#     backup still running when the next scheduled one starts) from
#     colliding on the same temp file or corrupting each other's dump.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-$APP_DIR/backend/.env}"
BACKUP_DIR="${BACKUP_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/hsclubs/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
LOCK_FILE="${LOCK_FILE:-$BACKUP_DIR/.backup.lock}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

# Reads one KEY=value entry from BACKEND_ENV_FILE without sourcing it, so a
# malicious or malformed entry can never execute as shell code. Supports a
# bare value or one wrapped in matching single/double quotes; rejects
# embedded quotes or backslashes rather than guessing at escaping rules.
read_env_value() {
  local key="$1"

  awk -v key="$key" '
    BEGIN {
      found = 0
      single_quote = sprintf("%c", 39)
      double_quote = sprintf("%c", 34)
      backslash = sprintf("%c", 92)
    }
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      separator = index(line, "=")
      if (separator == 0) {
        next
      }
      candidate = substr(line, 1, separator - 1)
      sub(/[[:space:]]*$/, "", candidate)
      if (candidate == key) {
        value = substr(line, separator + 1)
        sub(/^[[:space:]]*/, "", value)
        sub(/[[:space:]]*$/, "", value)
        found = 1
      }
    }
    END {
      if (found) {
        first = substr(value, 1, 1)
        last = substr(value, length(value), 1)
        if ((first == single_quote || first == double_quote) && first == last && length(value) >= 2) {
          value = substr(value, 2, length(value) - 2)
          if (index(value, first) || index(value, backslash)) {
            printf "ERROR: Unsupported quoting or escaping for %s in %s.\n", key, FILENAME > "/dev/stderr"
            exit 2
          }
        } else if (index(value, single_quote) || index(value, double_quote) || index(value, backslash)) {
          printf "ERROR: Unsupported quoting or escaping for %s in %s.\n", key, FILENAME > "/dev/stderr"
          exit 2
        }
        print value
      }
    }
  ' "$BACKEND_ENV_FILE"
}

# SPRING_DATASOURCE_URL looks like:
#   jdbc:mysql://host:port/database?param=value&...
# Parsing is deliberately strict (an explicit host, port, and database name
# are all required) so a malformed URL fails loudly instead of silently
# backing up the wrong database.
datasource_url_host() {
  local url="$1"
  [[ "$url" =~ ^jdbc:mysql://([^:/?]+): ]] || die "Could not parse host from SPRING_DATASOURCE_URL: $url"
  printf '%s\n' "${BASH_REMATCH[1]}"
}

datasource_url_port() {
  local url="$1"
  [[ "$url" =~ ^jdbc:mysql://[^:/?]+:([0-9]+)/ ]] || die "Could not parse port from SPRING_DATASOURCE_URL: $url"
  printf '%s\n' "${BASH_REMATCH[1]}"
}

datasource_url_database() {
  local url="$1"
  [[ "$url" =~ ^jdbc:mysql://[^/]+/([^?]+) ]] || die "Could not parse database name from SPRING_DATASOURCE_URL: $url"
  printf '%s\n' "${BASH_REMATCH[1]}"
}

# Writes a private (mode 0600) my.cnf-style credentials file and prints its
# path, so the password never appears as a mysqldump command-line argument
# (visible to other users via `ps`) or as a plain environment variable.
create_mysql_defaults_file() {
  local host="$1"
  local port="$2"
  local user="$3"
  local password="$4"
  local defaults_file

  defaults_file="$(mktemp)"
  chmod 0600 "$defaults_file"
  {
    printf '[client]\n'
    printf 'host="%s"\n' "$host"
    printf 'port="%s"\n' "$port"
    printf 'user="%s"\n' "$user"
    printf 'password="%s"\n' "$password"
  } > "$defaults_file"
  printf '%s\n' "$defaults_file"
}

# Deletes backup files older than BACKUP_RETENTION_DAYS. Only ever touches
# this script's own *.sql.gz naming pattern inside BACKUP_DIR.
prune_old_backups() {
  local backup_dir="$1"
  local retention_days="$2"

  find "$backup_dir" -maxdepth 1 -type f -name '*.sql.gz' -mtime "+$retention_days" -print -delete
}

# Runs mysqldump for `database`, gzips the result, validates the gzip
# stream, and only then moves it into place at `destination` with mode 0600.
# Every intermediate file lives in `backup_dir` (the same filesystem as
# `destination`) so the final `mv` is an atomic rename, never a copy.
dump_and_compress() {
  local defaults_file="$1"
  local database="$2"
  local destination="$3"
  local backup_dir="$4"
  local raw_dump
  local gzipped

  raw_dump="$(mktemp "$backup_dir/.dump-XXXXXX.sql")"
  gzipped="$raw_dump.gz"

  if ! mysqldump --defaults-extra-file="$defaults_file" --single-transaction --routines \
    "$database" > "$raw_dump"; then
    rm -f "$raw_dump"
    die "mysqldump failed for database: $database"
  fi

  if ! gzip "$raw_dump"; then
    rm -f "$raw_dump" "$gzipped"
    die "gzip failed while compressing: $raw_dump"
  fi
  if ! gzip -t "$gzipped"; then
    rm -f "$gzipped"
    die "Backup failed gzip integrity validation: $gzipped"
  fi

  chmod 0600 "$gzipped"
  mv "$gzipped" "$destination"
}

perform_backup() {
  local timestamp
  local database
  local host
  local port
  local user
  local password
  local defaults_file
  local destination
  local datasource_url

  [[ -r "$BACKEND_ENV_FILE" ]] || die "Backend environment file is missing or unreadable: $BACKEND_ENV_FILE"

  datasource_url="$(read_env_value SPRING_DATASOURCE_URL)"
  [[ -n "$datasource_url" ]] || die "SPRING_DATASOURCE_URL is not set in $BACKEND_ENV_FILE"
  user="$(read_env_value DB_USERNAME)"
  [[ -n "$user" ]] || die "DB_USERNAME is not set in $BACKEND_ENV_FILE"
  password="$(read_env_value DB_PASSWORD)"
  [[ -n "$password" ]] || die "DB_PASSWORD is not set in $BACKEND_ENV_FILE"

  host="$(datasource_url_host "$datasource_url")"
  port="$(datasource_url_port "$datasource_url")"
  database="$(datasource_url_database "$datasource_url")"

  mkdir -p "$BACKUP_DIR"
  chmod 0700 "$BACKUP_DIR"

  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  destination="$BACKUP_DIR/$database-$timestamp.sql.gz"

  defaults_file="$(create_mysql_defaults_file "$host" "$port" "$user" "$password")"
  # EXIT rather than RETURN: `die` (used by dump_and_compress on failure)
  # calls `exit`, which a RETURN trap never sees, leaving the mode-0600
  # defaults file (containing DB_PASSWORD) behind in $TMPDIR.
  trap 'rm -f "$defaults_file"' EXIT

  log "Backing up database $database from $host:$port to $destination"
  dump_and_compress "$defaults_file" "$database" "$destination" "$BACKUP_DIR"
  log "Backup complete: $destination"

  prune_old_backups "$BACKUP_DIR" "$BACKUP_RETENTION_DAYS"
}

main() {
  command -v mysqldump >/dev/null 2>&1 || die "Missing required command: mysqldump"
  command -v gzip >/dev/null 2>&1 || die "Missing required command: gzip"
  command -v flock >/dev/null 2>&1 || die "Missing required command: flock"

  mkdir -p "$BACKUP_DIR"
  chmod 0700 "$BACKUP_DIR"

  # Holds an exclusive, non-blocking lock for the lifetime of this shell
  # (released automatically on exit) so an overlapping run exits immediately
  # instead of racing this one on the same temp files.
  exec 9> "$LOCK_FILE"
  if ! flock -n 9; then
    log "Another backup is already running; exiting without starting a new one."
    exit 0
  fi

  perform_backup
}

# Guarded so tests can source this file to reach the individual functions
# above without running the full backup.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
