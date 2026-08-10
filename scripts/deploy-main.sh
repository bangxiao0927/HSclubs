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
# Run the service as the account performing the deployment unless an explicit
# service account is configured or an existing unit already selects one.
BACKEND_RUN_USER_EXPLICIT=0
if [[ -n "${BACKEND_RUN_USER:-}" ]]; then
  BACKEND_RUN_USER_EXPLICIT=1
fi
BACKEND_RUN_USER="${BACKEND_RUN_USER:-$(id -un)}"
SYSTEMD_SCOPE="${SYSTEMD_SCOPE:-system}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-0}"
SKIP_GIT_PULL="${SKIP_GIT_PULL:-0}"
SETUP_INSTALOADER="${SETUP_INSTALOADER:-1}"
# Optional exact nvm version to activate when the ambient Node.js does not
# satisfy frontend/package.json's engines.node (e.g. the server default is
# older than the frontend's minimum). Left unset, deployment auto-selects an
# already-installed nvm version that satisfies the requirement.
DEPLOY_NODE_VERSION="${DEPLOY_NODE_VERSION:-}"
NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
# Canonical path to systemctl, resolved lazily by resolve_systemctl_bin so
# the privileged restart command matches ops/hsclubs-deploy.sudoers exactly.
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-}"
# Where system-scope unit files live. Overridable so tests can point at a
# fixture directory instead of the real, root-owned /etc/systemd/system.
SYSTEM_UNIT_DIR="${SYSTEM_UNIT_DIR:-/etc/systemd/system}"

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

validate_deployment_user() {
  if [[ "$(id -u)" != "0" ]]; then
    return
  fi

  if [[ -n "${SUDO_USER:-}" && "$SUDO_USER" != "root" ]]; then
    die "Do not run deploy-main.sh with sudo. Run it directly as $SUDO_USER; " \
      "the script elevates only the steps that require it."
  fi
  die "Do not run deploy-main.sh as root. Use a non-root deployment account; " \
    "the script elevates only the steps that require it."
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

is_release_version() {
  [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

version_compare() {
  local -a left_parts right_parts
  local index left_component right_component

  is_release_version "$1" || die "Internal error: version_compare received a" \
    "non-release version string: $1"
  is_release_version "$2" || die "Internal error: version_compare received a" \
    "non-release version string: $2"

  IFS='.' read -r -a left_parts <<< "$1"
  IFS='.' read -r -a right_parts <<< "$2"

  for index in 0 1 2; do
    left_component="${left_parts[index]:-0}"
    right_component="${right_parts[index]:-0}"
    if (( 10#$left_component > 10#$right_component )); then
      printf '1\n'
      return
    fi
    if (( 10#$left_component < 10#$right_component )); then
      printf '\055\061\n'
      return
    fi
  done
  printf '0\n'
}

version_ge() {
  [[ "$(version_compare "$1" "$2")" != "-1" ]]
}

version_gt() {
  [[ "$(version_compare "$1" "$2")" == "1" ]]
}

node_version_matches_clause() {
  local version="$1"
  local clause="$2"

  case "$clause" in
    ^*)
      local base="${clause#^}"
      is_release_version "$base" || die "Unsupported frontend engines.node clause:" \
        "'$clause' (expected ^X.Y.Z)."
      [[ "${version%%.*}" == "${base%%.*}" ]] && version_ge "$version" "$base"
      ;;
    '>='*)
      local base="${clause#>=}"
      is_release_version "$base" || die "Unsupported frontend engines.node clause:" \
        "'$clause' (expected >=X.Y.Z)."
      version_ge "$version" "$base"
      ;;
    *)
      is_release_version "$clause" || die "Unsupported frontend engines.node clause:" \
        "'$clause' (expected an exact X.Y.Z version, ^X.Y.Z, or >=X.Y.Z)."
      [[ "$version" == "$clause" ]]
      ;;
  esac
}

# Range syntax supported: `||`-separated clauses of `^X.Y.Z`, `>=X.Y.Z`, or an
# exact version, which is what frontend/package.json's engines.node uses.
node_version_satisfies_engine_range() {
  local version="$1"
  local range="$2"
  local clause

  is_release_version "$version" || return 1

  while IFS= read -r clause; do
    clause="${clause#"${clause%%[![:space:]]*}"}"
    clause="${clause%"${clause##*[![:space:]]}"}"
    [[ -z "$clause" ]] && continue
    node_version_matches_clause "$version" "$clause" && return 0
  done <<< "${range//||/$'\n'}"
  return 1
}

frontend_node_engine_range() {
  local package_json="$APP_DIR/frontend/package.json"

  [[ -r "$package_json" ]] || die "Frontend package.json is missing or unreadable: $package_json"
  awk -F'"' '
    /"engines"[[:space:]]*:/ { in_engines = 1 }
    in_engines {
      for (i = 1; i <= NF; i++) {
        if ($i == "node") {
          print $(i + 2)
          exit
        }
      }
    }
    in_engines && /}/ { in_engines = 0 }
  ' "$package_json"
}

load_nvm() {
  local nvm_script="$NVM_DIR/nvm.sh"

  [[ -r "$nvm_script" ]] || die "Current Node.js does not satisfy the frontend's" \
    "engine requirement and nvm was not found at $nvm_script. Install a" \
    "compatible Node.js version, install nvm, or set NVM_DIR to where nvm is installed."

  # nvm.sh is not written for `set -Eeuo pipefail`: it references some
  # unset variables, and sourcing it normally auto-activates the `default`
  # alias, which returns non-zero (aborting this script under -e) whenever
  # that alias points at a version that has since been uninstalled -- before
  # the caller ever gets to scan installed versions. `--no-use` skips that
  # auto-activation, and errexit/nounset/pipefail/ERR are relaxed only for
  # the source itself; loading is verified afterward by checking for the
  # `nvm` function, so a real failure to load still produces an actionable
  # error.
  local restore_errexit=0 restore_nounset=0 restore_pipefail=0
  [[ $- == *e* ]] && restore_errexit=1
  [[ $- == *u* ]] && restore_nounset=1
  [[ ":$SHELLOPTS:" == *:pipefail:* ]] && restore_pipefail=1

  set +e
  set +u
  set +o pipefail
  trap - ERR
  # shellcheck source=/dev/null
  . "$nvm_script" --no-use

  (( restore_errexit )) && set -e
  (( restore_nounset )) && set -u
  (( restore_pipefail )) && set -o pipefail
  trap 'on_error $LINENO' ERR

  command -v nvm >/dev/null 2>&1 || die "Sourced $nvm_script but the nvm function did not load."
}

# Activates an installed nvm Node.js version matching DEPLOY_NODE_VERSION.
# Never installs a version; nvm reports a clear error when it is missing.
select_requested_nvm_node() {
  local requested_version="$1"
  local engine_range="$2"
  local resolved_version

  load_nvm
  nvm use "$requested_version" >/dev/null || \
    die "DEPLOY_NODE_VERSION=$requested_version is not installed via nvm." \
      "Install it with 'nvm install $requested_version' or unset" \
      "DEPLOY_NODE_VERSION to auto-select an installed compatible version."

  resolved_version="$(node --version)"
  resolved_version="${resolved_version#v}"
  node_version_satisfies_engine_range "$resolved_version" "$engine_range" || \
    die "DEPLOY_NODE_VERSION=$requested_version resolves to Node.js" \
      "$resolved_version, which does not satisfy frontend engines.node:" \
      "$engine_range."
  log "Using nvm-selected Node.js $resolved_version (DEPLOY_NODE_VERSION=$requested_version)"
}

# Picks the highest already-installed nvm Node.js version that satisfies
# engine_range by reading nvm's on-disk version directories directly, since
# their layout is stable across nvm releases and avoids parsing `nvm ls`
# output. Never installs a version.
select_compatible_nvm_node() {
  local engine_range="$1"
  local versions_dir
  local candidate_dir candidate_version
  local best_version=""

  load_nvm
  versions_dir="$NVM_DIR/versions/node"
  [[ -d "$versions_dir" ]] || die "No nvm-managed Node.js versions found in" \
    "$versions_dir. Install a version that satisfies frontend engines.node" \
    "($engine_range) with nvm, or set DEPLOY_NODE_VERSION."

  for candidate_dir in "$versions_dir"/v*; do
    [[ -x "$candidate_dir/bin/node" ]] || continue
    candidate_version="$(basename "$candidate_dir")"
    candidate_version="${candidate_version#v}"
    if node_version_satisfies_engine_range "$candidate_version" "$engine_range"; then
      if [[ -z "$best_version" ]] || version_gt "$candidate_version" "$best_version"; then
        best_version="$candidate_version"
      fi
    fi
  done

  if [[ -z "$best_version" ]]; then
    local installed
    installed="$(ls -1 "$versions_dir" 2>/dev/null | tr '\n' ' ')"
    die "No installed nvm Node.js version satisfies frontend engines.node" \
      "($engine_range). Installed versions: ${installed:-none}. Install a" \
      "compatible version with nvm (for example 'nvm install 22.12.0') or set" \
      "DEPLOY_NODE_VERSION to an installed compatible version."
  fi

  nvm use "$best_version" >/dev/null || die "Failed to activate nvm Node.js $best_version."
  log "Using nvm-selected Node.js $best_version (satisfies frontend engines.node: $engine_range)"
}

# Ensures the Node.js on PATH for the rest of the script satisfies
# frontend/package.json's engines.node, switching via nvm when needed. Must
# run before any `require_cmd npm` / npm invocation, since nvm changes PATH
# only for the current shell.
ensure_frontend_node_runtime() {
  local engine_range
  local current_version

  engine_range="$(frontend_node_engine_range)"
  [[ -n "$engine_range" ]] || die "Could not read engines.node from frontend/package.json."

  if [[ -n "$DEPLOY_NODE_VERSION" ]]; then
    select_requested_nvm_node "$DEPLOY_NODE_VERSION" "$engine_range"
    return
  fi

  if command -v node >/dev/null 2>&1; then
    current_version="$(node --version)"
    current_version="${current_version#v}"
    if node_version_satisfies_engine_range "$current_version" "$engine_range"; then
      log "Using system Node.js $current_version (satisfies frontend engines.node: $engine_range)"
      return
    fi
    log "System Node.js $current_version does not satisfy frontend engines.node: $engine_range"
  else
    log "No Node.js found on PATH; frontend requires engines.node: $engine_range"
  fi

  select_compatible_nvm_node "$engine_range"
}

run() {
  log "$*"
  "$@"
}

resolve_backend_run_user() {
  local existing_user

  if [[ "$SYSTEMD_SCOPE" != "system" || "$BACKEND_RUN_USER_EXPLICIT" == "1" ]]; then
    return
  fi

  existing_user="$(systemctl show "$BACKEND_SERVICE" --property=User --value 2>/dev/null || true)"
  if [[ -n "$existing_user" ]]; then
    BACKEND_RUN_USER="$existing_user"
    log "Preserving backend user from the existing $BACKEND_SERVICE unit: $BACKEND_RUN_USER"
  fi
}

backend_service_user() {
  if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
    printf '%s\n' "$BACKEND_RUN_USER"
  else
    id -un
  fi
}

run_as_backend_user() {
  if [[ "$SYSTEMD_SCOPE" == "system" && "$BACKEND_RUN_USER" != "$(id -un)" ]]; then
    sudo -H -u "$BACKEND_RUN_USER" -- "$@"
  else
    "$@"
  fi
}

service_cmd() {
  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    systemctl --user "$@"
  else
    sudo systemctl "$@"
  fi
}

# Resolves the exact, canonical path to the `systemctl` binary so the
# restart command run under sudo matches the literal path granted in
# ops/hsclubs-deploy.sudoers -- sudo does not consult PATH the way an
# interactive shell does, and a symlinked or non-standard location would
# otherwise silently fail to match that rule.
resolve_systemctl_bin() {
  local resolved

  if [[ -n "$SYSTEMCTL_BIN" ]]; then
    return
  fi

  resolved="$(command -v systemctl)" || die "Missing required command: systemctl"
  if command -v readlink >/dev/null 2>&1; then
    resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s\n' "$resolved")"
  fi
  SYSTEMCTL_BIN="$resolved"
}

# User-scope units are owned by the deployment account, so the script can
# install and enable them itself without elevation. System-scope units are
# root-owned and installed once by an admin; see restart_or_require_admin_setup.
install_user_service_file() {
  local source_file="$1"
  local user_unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"

  run mkdir -p "$user_unit_dir"
  run install -m 0644 "$source_file" "$user_unit_dir/$BACKEND_SERVICE"
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
  if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
    id "$BACKEND_RUN_USER" >/dev/null 2>&1 || \
      die "Backend service user does not exist: $BACKEND_RUN_USER"
  fi
  [[ "$SETUP_INSTALOADER" == "0" || "$SETUP_INSTALOADER" == "1" ]] || \
    die "SETUP_INSTALOADER must be either '0' or '1'."
}

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

validate_instaloader_session() {
  local cache_enabled
  local configured_python
  local service_user
  local session_file
  local session_user

  cache_enabled="$(read_env_value APP_INSTAGRAM_AVATAR_CACHE_ENABLED)"
  cache_enabled="${cache_enabled:-true}"
  cache_enabled="$(printf '%s\n' "$cache_enabled" | awk '{print tolower($0)}')"
  case "$cache_enabled" in
    false|0|no|off)
      return
      ;;
  esac

  configured_python="$(read_env_value APP_INSTAGRAM_AVATAR_PYTHON_COMMAND)"
  configured_python="${configured_python:-python3}"
  service_user="$(backend_service_user)"

  log "Validating the Instaloader runtime as backend user $service_user"
  if ! run_as_backend_user "$configured_python" -c 'import instaloader, browser_cookie3'; then
    die "Backend user $service_user cannot execute the configured Instaloader runtime: $configured_python"
  fi

  session_user="$(read_env_value APP_INSTAGRAM_AVATAR_SESSION_USER)"
  if [[ -z "$session_user" ]]; then
    log "Skipping Instaloader session validation because no session user is configured."
    return
  fi

  session_file="$(read_env_value APP_INSTAGRAM_AVATAR_SESSION_FILE)"

  log "Validating the Instaloader session as backend user $service_user"
  if ! run_as_backend_user "$configured_python" -c "
import sys
import instaloader

try:
    loader = instaloader.Instaloader(quiet=True)
    loader.load_session_from_file(sys.argv[1], sys.argv[2] or None)
except Exception as error:
    print(type(error).__name__, error, file=sys.stderr)
    raise SystemExit(1)
" "$session_user" "$session_file" >/dev/null; then
    if [[ -n "$session_file" ]]; then
      die "Configured Instaloader session cannot be loaded by backend user $service_user: $session_file"
    fi
    die "Default Instaloader session for $session_user cannot be loaded by backend user $service_user."
  fi

  log "Instaloader session validation passed for backend user $service_user"
}

validate_backend_runtime_access() {
  local jar_path="$1"
  local java_path
  local parent_dir
  local service_user
  local upload_dir

  java_path="$(command -v java)"
  service_user="$(backend_service_user)"
  upload_dir="$(read_env_value APP_UPLOAD_DIR)"
  upload_dir="${upload_dir:-uploads}"
  if [[ "$upload_dir" != /* ]]; then
    upload_dir="$APP_DIR/backend/$upload_dir"
  fi
  upload_dir="$(resolve_absolute_path "$upload_dir")"

  log "Validating backend runtime access as user $service_user"
  run_as_backend_user test -x "$APP_DIR/backend" || \
    die "Backend user $service_user cannot enter the working directory: $APP_DIR/backend"
  run_as_backend_user test -x "$java_path" || \
    die "Backend user $service_user cannot execute Java: $java_path"
  run_as_backend_user test -r "$jar_path" || \
    die "Backend user $service_user cannot read the backend JAR: $jar_path"

  if [[ -e "$upload_dir" ]]; then
    [[ -d "$upload_dir" ]] || die "Configured upload path is not a directory: $upload_dir"
    if ! run_as_backend_user test -w "$upload_dir" || \
      ! run_as_backend_user test -x "$upload_dir"; then
      die "Backend user $service_user cannot write to the upload directory: $upload_dir"
    fi
  else
    parent_dir="$upload_dir"
    while [[ ! -e "$parent_dir" ]]; do
      parent_dir="$(dirname "$parent_dir")"
    done
    [[ -d "$parent_dir" ]] || \
      die "Upload directory parent is not a directory: $parent_dir"
    if ! run_as_backend_user test -w "$parent_dir" || \
      ! run_as_backend_user test -x "$parent_dir"; then
      die "Backend user $service_user cannot create the upload directory under: $parent_dir"
    fi
  fi

  log "Backend runtime access validation passed for user $service_user"
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

backend_service_unit_content() {
  local jar_path="$1"
  local java_path

  java_path="$(command -v java)"
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
  }
}

install_and_start_backend_service() {
  local jar_path="$1"
  local service_file

  service_file="$(mktemp)"
  backend_service_unit_content "$jar_path" > "$service_file"
  chmod 0600 "$service_file"

  if [[ "$SYSTEMD_SCOPE" != "user" ]]; then
    restart_or_require_admin_setup "$service_file"
    rm -f "$service_file"
    return
  fi

  if ! install_user_service_file "$service_file"; then
    rm -f "$service_file"
    die "Could not install the systemd user service file."
  fi
  rm -f "$service_file"

  run service_cmd daemon-reload
  run service_cmd enable "$BACKEND_SERVICE"
  run service_cmd restart "$BACKEND_SERVICE"
}

# System-scope units are root-owned and already installed by an admin;
# routine deploys must not reinstall, daemon-reload, or re-enable them. This
# compares the desired unit against the installed one and, if they match and
# the unit is enabled, runs only the exact restart command granted by
# ops/hsclubs-deploy.sudoers. Otherwise it fails with the one-time setup an
# admin must run by hand, rather than attempting a broad privileged install.
restart_or_require_admin_setup() {
  local desired_file="$1"
  local installed_unit="$SYSTEM_UNIT_DIR/$BACKEND_SERVICE"

  if [[ -f "$installed_unit" ]] \
    && diff -q "$desired_file" "$installed_unit" >/dev/null 2>&1 \
    && systemctl is-enabled --quiet "$BACKEND_SERVICE" 2>/dev/null; then
    run sudo "$SYSTEMCTL_BIN" restart "$BACKEND_SERVICE"
    return
  fi

  fail_with_admin_setup_instructions "$desired_file" "$installed_unit"
}

fail_with_admin_setup_instructions() {
  local desired_file="$1"
  local installed_unit="$2"

  {
    printf '\nERROR: %s is missing, different from the unit this deploy\n' "$installed_unit"
    printf 'would install, or not enabled. Routine deploys never install, enable,\n'
    printf 'or daemon-reload a system-scope unit; that is a one-time admin task.\n\n'
    printf 'Generated the desired unit at: %s\n' "$desired_file"
    printf '(mode 0600, owned by this account; the path is unpredictable and not\n'
    printf 'reused across runs, so do not copy it to a fixed location.)\n\n'
    printf 'Run once, as an administrator, to install it:\n\n'
    printf '  sudo install -m 0644 "%s" "%s"\n' "$desired_file" "$installed_unit"
    printf '  sudo systemctl daemon-reload\n'
    printf '  sudo systemctl enable "%s"\n' "$BACKEND_SERVICE"
    printf '  sudo systemctl start "%s"\n\n' "$BACKEND_SERVICE"
    printf 'Then grant the deploy account the restart-only sudoers rule (see\n'
    printf 'ops/hsclubs-deploy.sudoers):\n\n'
    printf '  sudo visudo -cf ops/hsclubs-deploy.sudoers\n'
    printf '  sudo install -m 0440 ops/hsclubs-deploy.sudoers /etc/sudoers.d/hsclubs-deploy\n\n'
    printf 'Re-run this deploy once the unit is installed, enabled, and matches.\n'
    printf 'Once installed, remove the generated file: rm -f "%s"\n' "$desired_file"
  } >&2
  exit 1
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

  if [[ "$SYSTEMD_SCOPE" == "user" ]]; then
    service_cmd status "$BACKEND_SERVICE" --no-pager || true
  else
    # Reading unit status does not require root, unlike restart/enable.
    systemctl status "$BACKEND_SERVICE" --no-pager || true
  fi
  die "Backend health check failed after 60 seconds: $BACKEND_HEALTH_URL"
}

main() {
  validate_deployment_user

  require_cmd git
  require_cmd rsync
  require_cmd java
  require_cmd curl
  require_cmd systemctl
  require_cmd install
  require_cmd mktemp
  require_cmd diff
  require_cmd chmod
  if [[ "$SYSTEMD_SCOPE" == "system" ]]; then
    require_cmd sudo
    resolve_systemctl_bin
  fi

  resolve_backend_run_user
  validate_configuration
  cd "$APP_DIR"
  update_source
  initialize_instaloader
  validate_instaloader_session

  log "Building frontend"
  cd "$APP_DIR/frontend"
  ensure_frontend_node_runtime
  require_cmd npm
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
  validate_backend_runtime_access "$jar_path"

  log "Installing and restarting backend with $jar_path"
  install_and_start_backend_service "$jar_path"
  wait_for_backend

  log "Publishing frontend to $FRONTEND_DIST_TARGET"
  publish_frontend

  log "Deployment complete"
}

# Guarded so tests can source this file to reach the individual functions
# above without running the full deployment.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
