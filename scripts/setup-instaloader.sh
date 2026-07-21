#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="${INSTALOADER_VENV_DIR:-$APP_DIR/backend/.venv}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  printf 'ERROR: Python executable not found: %s\n' "$PYTHON_BIN" >&2
  exit 1
fi

"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install -r "$APP_DIR/backend/requirements-instaloader.txt"

printf '\nInstaloader environment ready.\n'
printf 'Python: %s\n' "$VENV_DIR/bin/python"
"$VENV_DIR/bin/python" -c 'import instaloader; print("Instaloader:", instaloader.__version__)'
printf '\nSet this in backend/.env:\n'
printf 'APP_INSTAGRAM_AVATAR_PYTHON_COMMAND=%s\n' "$VENV_DIR/bin/python"
