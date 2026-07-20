#!/usr/bin/env bash
set -Eeuo pipefail

# This script lives in scripts/, so the repository root is its parent directory.
APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"
GIT_REMOTE="${GIT_REMOTE:-origin}"
FRONTEND_DIST_TARGET="${FRONTEND_DIST_TARGET:-/var/www/hsclubs/frontend/dist}"
BACKEND_SERVICE="${BACKEND_SERVICE:-hsclubs.service}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-$APP_DIR/backend/.env}"
BACKEND_LOG="${BACKEND_LOG:-$APP_DIR/backend/hsclubs.log}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/api/clubs}"
SYSTEMD_SCOPE="${SYSTEMD_SCOPE:-user}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-0}"
SKIP_GIT_PULL="${SKIP_GIT_PULL:-0}"

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

systemctl_cmd() {
  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    systemctl --user "$@"
  else
    sudo systemctl "$@"
  fi
}

systemd_run_cmd() {
  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    systemd-run --user "$@"
  else
    sudo systemd-run "$@"
  fi
}

validate_configuration() {
  [[ -d "$APP_DIR/.git" ]] || die "APP_DIR is not a Git repository: $APP_DIR"
  [[ -f "$APP_DIR/frontend/package-lock.json" ]] || die "Frontend package-lock.json is missing."
  [[ -x "$APP_DIR/backend/mvnw" ]] || die "backend/mvnw is missing or not executable."
  [[ -r "$BACKEND_ENV_FILE" ]] || die "Backend environment file is missing or unreadable: $BACKEND_ENV_FILE"
  [[ "$SYSTEMD_SCOPE" == "user" || "$SYSTEMD_SCOPE" == "system" ]] || \
    die "SYSTEMD_SCOPE must be either 'user' or 'system'."
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
  find "$APP_DIR/backend/target" -maxdepth 1 -type f -name '*.jar' \
    ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
    | sort | tail -n 1
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

start_backend_service() {
  local jar_path="$1"
  local java_path
  local load_state

  java_path="$(command -v java)"
  load_state="$(systemctl_cmd show "$BACKEND_SERVICE" --property=LoadState --value 2>/dev/null || true)"

  if [[ -n "$load_state" && "$load_state" != "not-found" ]]; then
    run systemctl_cmd restart "$BACKEND_SERVICE"
    return
  fi

  run systemd_run_cmd \
    --unit="$BACKEND_SERVICE" \
    --description="HSclubs Spring Boot backend" \
    --property="WorkingDirectory=$APP_DIR/backend" \
    --property="EnvironmentFile=$BACKEND_ENV_FILE" \
    --property="Restart=on-failure" \
    --property="RestartSec=5s" \
    --property="StandardOutput=append:$BACKEND_LOG" \
    --property="StandardError=append:$BACKEND_LOG" \
    "$java_path" -jar "$jar_path"
}

wait_for_backend() {
  local attempt

  for attempt in {1..30}; do
    if curl -fsS "$BACKEND_HEALTH_URL" >/dev/null; then
      log "Backend health check passed: $BACKEND_HEALTH_URL"
      return
    fi
    sleep 2
  done

  systemctl_cmd status "$BACKEND_SERVICE" --no-pager || true
  die "Backend health check failed after 60 seconds: $BACKEND_HEALTH_URL"
}

main() {
  require_cmd git
  require_cmd npm
  require_cmd rsync
  require_cmd java
  require_cmd curl
  require_cmd systemctl
  require_cmd systemd-run
  if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
    require_cmd sudo
  fi

  validate_configuration
  cd "$APP_DIR"
  update_source

  log "Building frontend"
  cd "$APP_DIR/frontend"
  run npm ci
  run npm run build

  log "Building backend"
  cd "$APP_DIR/backend"
  if [[ "$RUN_BACKEND_TESTS" == "1" ]]; then
    run ./mvnw package
  else
    run ./mvnw package -DskipTests
  fi

  local jar_path
  jar_path="$(latest_backend_jar)"
  [[ -n "$jar_path" ]] || die "Could not find the backend JAR in backend/target."

  log "Publishing frontend to $FRONTEND_DIST_TARGET"
  publish_frontend

  log "Restarting backend with $jar_path"
  start_backend_service "$jar_path"
  wait_for_backend

  log "Deployment complete"
}

main "$@"
