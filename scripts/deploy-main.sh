#!/usr/bin/env bash
set -Eeuo pipefail

# This script lives in scripts/, so the repository root is its parent directory.
APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
GIT_REMOTE="${GIT_REMOTE:-origin}"
FRONTEND_DIST_TARGET="${FRONTEND_DIST_TARGET:-/var/www/hsclubs/frontend/dist}"
BACKEND_SERVICE="${BACKEND_SERVICE:-hsclubs.service}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-$APP_DIR/backend/.env}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/api/clubs}"
BACKEND_RUN_USER="${BACKEND_RUN_USER:-hsclubs}"
SYSTEMD_SCOPE="${SYSTEMD_SCOPE:-system}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-0}"
SKIP_GIT_PULL="${SKIP_GIT_PULL:-0}"
SETUP_INSTALOADER="${SETUP_INSTALOADER:-1}"

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

on_error() {
  local exit_code=$?
  printf '\nERROR: Deployment failed at line %s (exit code %s).\n' "$1" "$exit_code" >&2
  exit "$exit_code"
}

trap 'on_error $LINENO' ERR

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

run() {
  log "$*"
  "$@"
}

service_cmd() {
  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    systemctl --user "$@"
  else
    sudo systemctl "$@"
  fi
}

install_service_file() {
  local source_file="$1"

  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    local user_unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
    run mkdir -p "$user_unit_dir"
    run install -m 0644 "$source_file" "$user_unit_dir/$BACKEND_SERVICE"
  else
    run sudo install -m 0644 "$source_file" "/etc/systemd/system/$BACKEND_SERVICE"
  fi
}

validate_configuration() {
  [[ -d "$APP_DIR/.git" ]] || die "APP_DIR is not a Git repository: $APP_DIR"
  [[ -f "$APP_DIR/frontend/package-lock.json" ]] || die "Frontend package-lock.json is missing."
  [[ -x "$APP_DIR/backend/mvnw" ]] || die "backend/mvnw is missing or not executable."
  [[ -r "$BACKEND_ENV_FILE" ]] || die "Backend environment file is missing or unreadable: $BACKEND_ENV_FILE"
  [[ "$BACKEND_SERVICE" != */* && "$BACKEND_SERVICE" == *.service ]] || \
    die "BACKEND_SERVICE must be a systemd service name ending in .service."
  [[ "$SYSTEMD_SCOPE" == "user" || "$SYSTEMD_SCOPE" == "system" ]] || \
    die "SYSTEMD_SCOPE must be either 'user' or 'system'."
  [[ "$SETUP_INSTALOADER" == "0" || "$SETUP_INSTALOADER" == "1" ]] || \
    die "SETUP_INSTALOADER must be either '0' or '1'."
}

read_env_value() {
  local key="$1"

  awk -v key="$key" '
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
        print value
        exit
      }
    }
  ' "$BACKEND_ENV_FILE"
}

resolve_absolute_path() {
  local path="$1"
  local directory
  local basename

  [[ "$path" = /* ]] || path="$APP_DIR/$path"
  directory="$(dirname "$path")"
  basename="$(basename "$path")"

  if [[ -d "$directory" ]]; then
    printf '%s/%s\n' "$(cd "$directory" && pwd -P)" "$basename"
    return
  fi

  local normalized_parent
  local current="$path"
  local suffix=""
  while [[ ! -d "$current" ]]; do
    suffix="/$(basename "$current")$suffix"
    current="$(dirname "$current")"
  done
  normalized_parent="$(cd "$current" && pwd -P)"
  printf '%s%s\n' "$normalized_parent" "$suffix"
}

initialize_instaloader() {
  local cache_enabled
  local configured_python
  local expected_python
  local venv_dir

  cache_enabled="$(read_env_value APP_INSTAGRAM_AVATAR_CACHE_ENABLED)"
  cache_enabled="${cache_enabled:-true}"
  cache_enabled="$(printf '%s\n' "$cache_enabled" | awk '{print tolower($0)}')"
  case "$cache_enabled" in
    false|0|no|off)
      log "Skipping Instaloader setup because the Instagram avatar cache is disabled."
      return
      ;;
  esac

  if [[ "$SETUP_INSTALOADER" == "0" ]]; then
    log "Skipping Instaloader setup because SETUP_INSTALOADER=0"
    return
  fi

  [[ -x "$APP_DIR/scripts/setup-instaloader.sh" ]] || \
    die "Instaloader setup script is missing or not executable."

  venv_dir="$(resolve_absolute_path "${INSTALOADER_VENV_DIR:-$APP_DIR/backend/.venv}")"
  expected_python="$venv_dir/bin/python"
  configured_python="$(read_env_value APP_INSTAGRAM_AVATAR_PYTHON_COMMAND)"
  if [[ "$configured_python" == "$expected_python" ]]; then
    run "$APP_DIR/scripts/setup-instaloader.sh" --env-file "$BACKEND_ENV_FILE"
    return
  fi

  [[ -w "$BACKEND_ENV_FILE" ]] || \
    die "Backend environment file must be writable to configure Instaloader: $BACKEND_ENV_FILE"
  run "$APP_DIR/scripts/setup-instaloader.sh" --configure-env --env-file "$BACKEND_ENV_FILE"
}

ensure_clean_worktree() {
  local status
  status="$(git status --porcelain)"
  [[ -z "$status" ]] || die "Working tree is not clean. Commit or stash changes before deploying."
}

update_source() {
  if [[ "$SKIP_GIT_PULL" == "1" ]]; then
    log "Skipping Git update because SKIP_GIT_PULL=1"
    return
  fi

  ensure_clean_worktree
  run git fetch "$GIT_REMOTE" "$DEPLOY_BRANCH"
  run git checkout "$DEPLOY_BRANCH"
  run git merge --ff-only "$GIT_REMOTE/$DEPLOY_BRANCH"
}

latest_backend_jar() {
  local jar_path=""
  local candidate
  local count=0

  while IFS= read -r candidate; do
    jar_path="$candidate"
    count=$((count + 1))
  done < <(
    find "$APP_DIR/backend/target" -maxdepth 1 -type f -name '*.jar' \
      ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar'
  )

  [[ "$count" -eq 1 ]] || die "Expected one runnable backend JAR from this build, found $count."
  printf '%s\n' "$jar_path"
}

publish_frontend() {
  local source_dir="$APP_DIR/frontend/dist/"
  local target_dir="$FRONTEND_DIST_TARGET/"

  [[ -d "$source_dir" ]] || die "Frontend dist was not created: $source_dir"
  [[ "$source_dir" != "$target_dir" ]] || {
    log "Frontend is already built at the configured publish target."
    return
  }

  if [[ -d "$FRONTEND_DIST_TARGET" && -w "$FRONTEND_DIST_TARGET" ]]; then
    run rsync -a --delete "$source_dir" "$target_dir"
    return
  fi

  require_cmd sudo
  run sudo mkdir -p "$FRONTEND_DIST_TARGET"
  run sudo rsync -a --delete "$source_dir" "$target_dir"
}

install_and_start_backend_service() {
  local jar_path="$1"
  local java_path
  local service_file

  java_path="$(command -v java)"
  service_file="$(mktemp)"

  {
    printf '%s\n' \
      '[Unit]' \
      'Description=HSclubs Backend' \
      'Wants=network-online.target' \
      'After=network-online.target mysql.service' \
      '' \
      '[Service]' \
      'Type=simple'
    if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
      printf 'User=%s\n' "$BACKEND_RUN_USER"
    fi
    printf '%s\n' \
      "WorkingDirectory=$APP_DIR/backend" \
      "EnvironmentFile=$BACKEND_ENV_FILE" \
      "ExecStart=$java_path -jar $jar_path" \
      'Restart=on-failure' \
      'RestartSec=5' \
      '' \
      '[Install]'
    if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
      printf 'WantedBy=multi-user.target\n'
    else
      printf 'WantedBy=default.target\n'
    fi
  } > "$service_file"

  if ! install_service_file "$service_file"; then
    rm -f "$service_file"
    die "Could not install the systemd service file."
  fi
  rm -f "$service_file"

  run service_cmd daemon-reload
  run service_cmd enable "$BACKEND_SERVICE"
  run service_cmd restart "$BACKEND_SERVICE"
}

wait_for_backend() {
  local attempt

  for attempt in {1..15}; do
    if curl -fsS --connect-timeout 2 --max-time 2 "$BACKEND_HEALTH_URL" >/dev/null; then
      log "Backend health check passed: $BACKEND_HEALTH_URL"
      return
    fi
    if [[ "$attempt" -lt 15 ]]; then
      sleep 2
    fi
  done

  service_cmd status "$BACKEND_SERVICE" --no-pager || true
  die "Backend health check failed after 60 seconds: $BACKEND_HEALTH_URL"
}

main() {
  require_cmd git
  require_cmd npm
  require_cmd rsync
  require_cmd java
  require_cmd curl
  require_cmd systemctl
  require_cmd install
  require_cmd mktemp
  if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
    require_cmd sudo
  fi

  validate_configuration
  cd "$APP_DIR"
  update_source
  initialize_instaloader

  log "Building frontend"
  cd "$APP_DIR/frontend"
  run npm ci --include=dev
  run npm run build

  log "Building backend"
  cd "$APP_DIR/backend"
  if [[ "$RUN_BACKEND_TESTS" == "1" ]]; then
    run ./mvnw clean package
  else
    run ./mvnw clean package -DskipTests
  fi

  local jar_path
  jar_path="$(latest_backend_jar)"
  [[ -n "$jar_path" ]] || die "Could not find the backend JAR in backend/target."

  log "Installing and restarting backend with $jar_path"
  install_and_start_backend_service "$jar_path"
  wait_for_backend

  log "Publishing frontend to $FRONTEND_DIST_TARGET"
  publish_frontend

  log "Deployment complete"
}

main "$@"
