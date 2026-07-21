#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
ENV_FILE="${BACKEND_ENV_FILE:-$APP_DIR/backend/.env}"
REQUIREMENTS_FILE="$APP_DIR/backend/requirements-instaloader.txt"
CONFIGURE_ENV=0
CHECK_ONLY=0

resolve_absolute_path() {
  local path="$1"
  local directory
  local basename

  [[ "$path" = /* ]] || path="$APP_DIR/$path"
  directory="$(dirname "$path")"
  basename="$(basename "$path")"

  if [[ ! -d "$directory" ]]; then
    local normalized_parent
    local current="$directory"
    local suffix=""

    while [[ ! -d "$current" ]]; do
      suffix="/$(basename "$current")$suffix"
      current="$(dirname "$current")"
    done
    normalized_parent="$(cd "$current" && pwd -P)"
    printf '%s%s/%s\n' "$normalized_parent" "$suffix" "$basename"
    return
  fi

  printf '%s/%s\n' "$(cd "$directory" && pwd -P)" "$basename"
}

VENV_DIR="$(resolve_absolute_path "${INSTALOADER_VENV_DIR:-$APP_DIR/backend/.venv}")"
REQUIREMENTS_MARKER="$VENV_DIR/.hsclubs-instaloader-requirements"

log() {
  printf '\n[Instaloader setup] %s\n' "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  printf '%s\n' \
    'Usage: ./scripts/setup-instaloader.sh [options]' \
    '' \
    'Create or update the Linux environment used by the Instagram avatar cache.' \
    '' \
    '  --configure-env   Enable the cache and write its Python command to backend/.env.' \
    '  --env-file PATH   Use PATH instead of backend/.env when configuring or checking.' \
    '  --check           Check the existing installation without changing it.' \
    '  -h, --help        Show this help.' \
    '' \
    'Environment overrides: PYTHON_BIN, INSTALOADER_VENV_DIR, BACKEND_ENV_FILE'
}

linux_package_hint() {
  local distro_id=""

  if [[ -r /etc/os-release ]]; then
    distro_id="$(awk -F= '$1 == "ID" {gsub(/^"|"$/, "", $2); print tolower($2); exit}' /etc/os-release)"
  fi

  case "$distro_id" in
    ubuntu|debian|linuxmint|pop)
      printf 'Install prerequisites with:\n  sudo apt update && sudo apt install -y python3 python3-venv python3-pip\n' >&2
      ;;
    fedora|rhel|centos|rocky|almalinux)
      printf 'Install prerequisites with:\n  sudo dnf install -y python3 python3-pip\n' >&2
      ;;
    arch|manjaro)
      printf 'Install prerequisites with:\n  sudo pacman -S --needed python python-pip\n' >&2
      ;;
    opensuse*|sles)
      printf 'Install prerequisites with:\n  sudo zypper install python3 python3-pip python3-virtualenv\n' >&2
      ;;
    *)
      printf 'Install Python 3, pip, and the Python venv module with your Linux package manager.\n' >&2
      ;;
  esac
}

read_env_value() {
  local key="$1"
  local file="$2"

  [[ -r "$file" ]] || return 1
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
  ' "$file"
}

upsert_env_value() {
  local key="$1"
  local value="$2"
  local temp_file

  [[ "$value" != *$'\n'* ]] || die "Environment value for $key contains a newline."
  mkdir -p "$(dirname "$ENV_FILE")"
  if [[ ! -e "$ENV_FILE" ]]; then
    (umask 077; : > "$ENV_FILE")
  fi
  [[ -f "$ENV_FILE" && -w "$ENV_FILE" ]] || die "Environment file is not writable: $ENV_FILE"

  temp_file="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
  if ! awk -v key="$key" -v value="$value" '
    BEGIN { updated = 0 }
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      separator = index(line, "=")
      candidate = separator == 0 ? "" : substr(line, 1, separator - 1)
      sub(/[[:space:]]*$/, "", candidate)
      if (candidate == key) {
        if (!updated) {
          print key "=" value
          updated = 1
        }
        next
      }
      print
    }
    END {
      if (!updated) {
        print key "=" value
      }
    }
  ' "$ENV_FILE" > "$temp_file"; then
    rm -f "$temp_file"
    die "Could not update environment file: $ENV_FILE"
  fi
  chmod --reference="$ENV_FILE" "$temp_file"
  mv "$temp_file" "$ENV_FILE"
}

check_installation() {
  local cache_enabled=""
  local configured_python=""
  local failed=0

  if [[ ! -x "$VENV_DIR/bin/python" ]]; then
    printf 'ERROR: Instaloader Python is missing: %s\n' "$VENV_DIR/bin/python" >&2
    failed=1
  elif ! "$VENV_DIR/bin/python" -c 'import instaloader, browser_cookie3' >/dev/null 2>&1; then
    printf 'ERROR: Instaloader dependencies cannot be imported from %s\n' "$VENV_DIR/bin/python" >&2
    failed=1
  else
    "$VENV_DIR/bin/python" -c 'from importlib.metadata import version; print("Instaloader:", version("instaloader"))'
  fi

  if [[ ! -f "$REQUIREMENTS_MARKER" ]] || ! cmp -s "$REQUIREMENTS_FILE" "$REQUIREMENTS_MARKER"; then
    printf 'ERROR: The environment is not synchronized with %s\n' "$REQUIREMENTS_FILE" >&2
    failed=1
  fi

  if [[ "$CONFIGURE_ENV" == "1" ]]; then
    configured_python="$(read_env_value APP_INSTAGRAM_AVATAR_PYTHON_COMMAND "$ENV_FILE" || true)"
    if [[ "$configured_python" != "$VENV_DIR/bin/python" ]]; then
      printf 'ERROR: %s does not configure APP_INSTAGRAM_AVATAR_PYTHON_COMMAND=%s\n' \
        "$ENV_FILE" "$VENV_DIR/bin/python" >&2
      failed=1
    fi

    cache_enabled="$(read_env_value APP_INSTAGRAM_AVATAR_CACHE_ENABLED "$ENV_FILE" || true)"
    cache_enabled="$(printf '%s\n' "$cache_enabled" | awk '{print tolower($0)}')"
    case "$cache_enabled" in
      true|1|yes|on)
        ;;
      *)
        printf 'ERROR: %s does not enable APP_INSTAGRAM_AVATAR_CACHE_ENABLED\n' \
          "$ENV_FILE" >&2
        failed=1
        ;;
    esac
  fi

  [[ "$failed" == "0" ]] || return 1
  printf 'Instaloader environment check passed.\n'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --configure-env)
      CONFIGURE_ENV=1
      ;;
    --env-file)
      [[ $# -ge 2 ]] || die "--env-file requires a path."
      ENV_FILE="$2"
      shift
      ;;
    --check)
      CHECK_ONLY=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "Unknown option: $1"
      ;;
  esac
  shift
done

[[ "$(uname -s)" == "Linux" ]] || die "This initializer currently supports Linux hosts only."
[[ -f "$REQUIREMENTS_FILE" ]] || die "Requirements file is missing: $REQUIREMENTS_FILE"

if [[ "$CHECK_ONLY" == "1" ]]; then
  check_installation
  exit 0
fi

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  linux_package_hint
  die "Python executable not found: $PYTHON_BIN"
fi

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  log "Creating Python virtual environment at $VENV_DIR"
  if ! "$PYTHON_BIN" -m venv "$VENV_DIR"; then
    linux_package_hint
    die "Python could not create the virtual environment."
  fi
fi

if ! "$VENV_DIR/bin/python" -c 'import instaloader, browser_cookie3' >/dev/null 2>&1 \
  || [[ ! -f "$REQUIREMENTS_MARKER" ]] \
  || ! cmp -s "$REQUIREMENTS_FILE" "$REQUIREMENTS_MARKER"; then
  log "Installing Instaloader dependencies"
  "$VENV_DIR/bin/python" -m pip install --disable-pip-version-check -r "$REQUIREMENTS_FILE"
  cp "$REQUIREMENTS_FILE" "$REQUIREMENTS_MARKER"
else
  log "Instaloader dependencies are already current"
fi

if [[ "$CONFIGURE_ENV" == "1" ]]; then
  log "Configuring $ENV_FILE"
  upsert_env_value APP_INSTAGRAM_AVATAR_CACHE_ENABLED true
  upsert_env_value APP_INSTAGRAM_AVATAR_PYTHON_COMMAND "$VENV_DIR/bin/python"
fi

check_installation
printf 'Python: %s\n' "$VENV_DIR/bin/python"
if [[ "$CONFIGURE_ENV" != "1" ]]; then
  printf '\nTo enable the cache in %s, rerun with --configure-env.\n' "$ENV_FILE"
fi
printf 'Configure an authenticated Instagram session before relying on avatar refreshes.\n'
