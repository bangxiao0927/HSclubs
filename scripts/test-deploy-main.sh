#!/usr/bin/env bash
# Focused tests for the Node.js range/parser/runtime-selection functions in
# deploy-main.sh. No test framework: each check is a small function that
# reports pass/fail, run against fixtures under a throwaway temp directory.
# Nothing here touches the host's real nvm installation, system Node.js, or
# the network -- every PATH, NVM_DIR, and APP_DIR used below is a fixture
# created by this script.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_MAIN="$SCRIPT_DIR/deploy-main.sh"
DEPLOYMENT_DOC="$SCRIPT_DIR/../docs/DEPLOYMENT.md"

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

# Runs a snippet of code in a fresh, isolated shell: it sources deploy-main.sh
# (which never runs main() when sourced) with the given environment, then
# evaluates the snippet. Captures combined stdout+stderr and the exit code so
# tests can assert on either without depending on real system state.
run_isolated() {
  local env_assignments="$1"
  local snippet="$2"
  env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      $env_assignments
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      $snippet
    " 2>&1
}

### Fixtures ###############################################################

REPO_DIR="$FIXTURE_ROOT/repo"
mkdir -p "$REPO_DIR/frontend" "$REPO_DIR/.git" "$FIXTURE_ROOT/home" "$FIXTURE_ROOT/bin"
cat > "$REPO_DIR/frontend/package.json" <<'JSON'
{
  "name": "fixture-frontend",
  "engines": {
    "node": "^20.19.0 || >=22.12.0"
  }
}
JSON

NVM_ROOT="$FIXTURE_ROOT/nvm"
VERSIONS_DIR="$NVM_ROOT/versions/node"
mkdir -p "$VERSIONS_DIR"

make_fake_node() {
  local version_dir="$1"
  local reported_version="$2"
  mkdir -p "$version_dir/bin"
  cat > "$version_dir/bin/node" <<SH
#!/usr/bin/env bash
if [[ "\$1" == "--version" ]]; then
  echo "v$reported_version"
fi
SH
  chmod +x "$version_dir/bin/node"
}

# Compatible with `^20.19.0`.
make_fake_node "$VERSIONS_DIR/v20.19.0" "20.19.0"
make_fake_node "$VERSIONS_DIR/v20.25.3" "20.25.3"
# Not compatible with either clause: major 21 satisfies neither `^20.19.0`
# (major must stay 20) nor `>=22.12.0`.
make_fake_node "$VERSIONS_DIR/v21.0.0" "21.0.0"
# Compatible with `>=22.12.0`, including the highest installed version, so
# selection has to compare across both clauses rather than picking within one.
make_fake_node "$VERSIONS_DIR/v22.12.0" "22.12.0"
make_fake_node "$VERSIONS_DIR/v23.1.0" "23.1.0"
# A prerelease/nightly directory name: must never be selected, and must never
# be treated as a huge version by concatenating its suffix digits.
make_fake_node "$VERSIONS_DIR/v22.20.0-nightly20240101" "22.20.0-nightly20240101"

# A well-behaved fake nvm.sh for the selection tests below: defines the `nvm`
# function and activates a version by prepending its bin dir to PATH.
cat > "$NVM_ROOT/nvm.sh" <<'SH'
#!/usr/bin/env bash
nvm() {
  case "${1:-}" in
    use)
      local version="$2"
      local version_dir="$NVM_DIR/versions/node/v$version"
      [[ -x "$version_dir/bin/node" ]] || return 1
      export PATH="$version_dir/bin:$PATH"
      return 0
      ;;
    *)
      return 0
      ;;
  esac
}
SH

# A dedicated, separate nvm installation: a hostile fake nvm.sh standing in
# for a real installation with a stale
# `default` alias: sourced without `--no-use` it fails outright (simulating
# `nvm use default` failing because the aliased version was removed), and it
# references an unset variable (simulating known unset-variable spots in real
# nvm.sh). A correct load_nvm must pass `--no-use` and relax `set -u` around
# the source, or this aborts the caller before any version is scanned. Kept
# in its own directory so it never overwrites the well-behaved nvm.sh other
# tests rely on.
HOSTILE_NVM_ROOT="$FIXTURE_ROOT/hostile-nvm"
mkdir -p "$HOSTILE_NVM_ROOT"
cat > "$HOSTILE_NVM_ROOT/nvm.sh" <<'SH'
#!/usr/bin/env bash
if [[ "${1:-}" != "--no-use" ]]; then
  echo "hostile-nvm: stale default alias failed" >&2
  return 1
fi
: "$FAKE_NVM_UNSET_VAR"
nvm() {
  case "${1:-}" in
    use)
      local version="$2"
      local version_dir="$NVM_DIR/versions/node/v$version"
      [[ -x "$version_dir/bin/node" ]] || return 1
      export PATH="$version_dir/bin:$PATH"
      return 0
      ;;
    *)
      return 0
      ;;
  esac
}
SH

COMMON_ENV="export APP_DIR='$REPO_DIR' NVM_DIR='$NVM_ROOT'"

# A real, minimal git repository (unlike REPO_DIR's fixture .git directory,
# which only needs to exist for validate_configuration's directory check) so
# deployment-history tests can assert against a real HEAD sha.
HISTORY_REPO_DIR="$FIXTURE_ROOT/history-repo"
mkdir -p "$HISTORY_REPO_DIR"
git init -q "$HISTORY_REPO_DIR"
git -C "$HISTORY_REPO_DIR" config user.email "test@example.com"
git -C "$HISTORY_REPO_DIR" config user.name "Test"
echo "fixture" > "$HISTORY_REPO_DIR/README.md"
git -C "$HISTORY_REPO_DIR" add -A
git -C "$HISTORY_REPO_DIR" commit -q -m "fixture commit"
HISTORY_REPO_SHA="$(git -C "$HISTORY_REPO_DIR" rev-parse HEAD)"

# The real git binary is not necessarily under /usr/bin (for example under
# Git for Windows it lives under /mingw64/bin), so deployment-history tests
# use their own PATH instead of widening run_isolated's PATH for every test.
GIT_BIN_DIR="$(dirname "$(command -v git)")"

run_isolated_with_git() {
  local env_assignments="$1"
  local snippet="$2"
  env -i \
    PATH="$FIXTURE_ROOT/bin:$GIT_BIN_DIR:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      $env_assignments
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      $snippet
    " 2>&1
}

### node_version_satisfies_engine_range: boundary versions #################

test_range_lower_bound_caret_matches() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "20.19.0" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "yes" "$output" "20.19.0 is the inclusive floor of ^20.19.0"
}

test_range_below_lower_bound_caret_rejected() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "20.18.9" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "no" "$output" "20.18.9 is just below the ^20.19.0 floor"
}

test_range_next_major_rejected_by_caret() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "21.0.0" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "no" "$output" "21.x satisfies neither clause"
}

test_range_gte_lower_bound_matches() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "22.12.0" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "yes" "$output" "22.12.0 is the inclusive floor of >=22.12.0"
}

test_range_below_gte_bound_rejected() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "22.11.9" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "no" "$output" "22.11.9 is just below the >=22.12.0 floor"
}

test_range_far_above_gte_bound_matches() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "23.5.0" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "yes" "$output" "23.5.0 satisfies >=22.12.0"
}

test_range_rejects_prerelease_version_string() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "22.12.0-nightly20240101" "^20.19.0 || >=22.12.0" && echo yes || echo no
  ')"
  assert_eq "no" "$output" \
    "a prerelease/nightly version string must be rejected outright, not parsed as a huge release"
}

test_range_rejects_shorthand_gte_clause_instead_of_accepting() {
  local output
  local status=0
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "23.5.0" ">=22"
  ')" || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  a shorthand ">=22" clause must fail closed, not silently accept, got: %s\n' "$output" >&2
    return 1
  }
  [[ "$output" == *"Unsupported frontend engines.node clause"* ]] || {
    printf '  expected an actionable unsupported-range error, got: %s\n' "$output" >&2
    return 1
  }
}

test_range_rejects_garbage_gte_clause_instead_of_accepting() {
  local output
  local status=0
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "23.5.0" ">=garbage"
  ')" || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  a garbage ">=garbage" clause must fail closed, not silently accept, got: %s\n' "$output" >&2
    return 1
  }
  [[ "$output" == *"Unsupported frontend engines.node clause"* ]] || {
    printf '  expected an actionable unsupported-range error, got: %s\n' "$output" >&2
    return 1
  }
}

test_range_rejects_shorthand_caret_clause_instead_of_accepting() {
  local output
  local status=0
  output="$(run_isolated "$COMMON_ENV" '
    node_version_satisfies_engine_range "22.12.0" "^22"
  ')" || status=$?
  [[ "$status" -ne 0 ]] || {
    printf '  a shorthand "^22" clause must fail closed, not silently accept, got: %s\n' "$output" >&2
    return 1
  }
  [[ "$output" == *"Unsupported frontend engines.node clause"* ]] || {
    printf '  expected an actionable unsupported-range error, got: %s\n' "$output" >&2
    return 1
  }
}

### select_compatible_nvm_node: picks the highest compatible, real release ##

test_selects_highest_compatible_across_clauses() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    select_compatible_nvm_node "^20.19.0 || >=22.12.0" >/dev/null
    node --version
  ')"
  assert_eq "v23.1.0" "$output" \
    "must compare candidates across both clauses, not just within the first one that matches"
}

test_never_selects_nightly_directory() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    select_compatible_nvm_node ">=22.12.0" >/dev/null 2>&1
    node --version
  ')"
  assert_eq "v23.1.0" "$output" \
    "a nightly-suffixed directory must never be selected, even when it numerically looks newest"
}

### DEPLOY_NODE_VERSION override ############################################

test_deploy_node_version_override_selects_requested_version() {
  local output
  output="$(run_isolated "$COMMON_ENV; export DEPLOY_NODE_VERSION=20.19.0" '
    ensure_frontend_node_runtime >/dev/null
    node --version
  ')"
  assert_eq "v20.19.0" "$output" \
    "an explicit override must win even though a higher version is also installed"
}

test_deploy_node_version_incompatible_override_fails() {
  local output
  local status=0
  output="$(run_isolated "$COMMON_ENV; export DEPLOY_NODE_VERSION=21.0.0" '
    ensure_frontend_node_runtime
  ')" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ "$output" == *"does not satisfy"* ]] || {
    printf '  expected an actionable "does not satisfy" message, got: %s\n' "$output" >&2
    return 1
  }
}

### No nvm and no compatible system Node.js: fails with an actionable error #

test_no_nvm_and_incompatible_system_node_fails_with_actionable_message() {
  local output
  local status=0
  local system_node_dir="$FIXTURE_ROOT/system-node-incompatible"
  make_fake_node "$system_node_dir" "18.0.0"
  output="$(env -i \
    PATH="$system_node_dir/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR' NVM_DIR='$FIXTURE_ROOT/nonexistent-nvm'
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      ensure_frontend_node_runtime
    " 2>&1)" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ "$output" == *"nvm was not found"* ]] || {
    printf '  expected an actionable "nvm was not found" message, got: %s\n' "$output" >&2
    return 1
  }
}

### Hardened nvm.sh sourcing #################################################

test_load_nvm_survives_stale_default_alias_and_unset_variable() {
  local output
  local status=0
  output="$(run_isolated "export APP_DIR='$REPO_DIR' NVM_DIR='$HOSTILE_NVM_ROOT'" '
    load_nvm
    command -v nvm >/dev/null 2>&1 && echo nvm-loaded || echo nvm-missing
  ')" || status=$?
  [[ "$status" -eq 0 ]] || { printf '  expected exit 0, got %s: %s\n' "$status" "$output" >&2; return 1; }
  assert_eq "nvm-loaded" "$output" "load_nvm must define nvm despite the hostile fixture"
}

test_load_nvm_restores_shell_options_after_sourcing() {
  local output
  output="$(run_isolated "export APP_DIR='$REPO_DIR' NVM_DIR='$HOSTILE_NVM_ROOT'" '
    load_nvm
    case $- in *e*) errexit=on;; *) errexit=off;; esac
    case $- in *u*) nounset=on;; *) nounset=off;; esac
    case ":$SHELLOPTS:" in *:pipefail:*) pipefail=on;; *) pipefail=off;; esac
    echo "$errexit,$nounset,$pipefail"
  ')"
  assert_eq "on,on,on" "$output" \
    "errexit/nounset/pipefail must be restored once load_nvm returns"
}

### Restricted sudo deployment: restart-only fast path ######################

DEPLOY_FIXTURE_BIN="$FIXTURE_ROOT/deploy-bin"
mkdir -p "$DEPLOY_FIXTURE_BIN"
cat > "$DEPLOY_FIXTURE_BIN/java" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$DEPLOY_FIXTURE_BIN/java"

# Fake sudo: records every invocation (proving whether a privileged command
# ran at all) and then executes it for real, so a test can assert on both
# "was sudo called" and "what did the privileged command do".
cat > "$DEPLOY_FIXTURE_BIN/sudo" <<'SH'
#!/usr/bin/env bash
echo "sudo $*" >> "$SUDO_LOG"
# Strips the sudo flags this repo's scripts use (-H, -u USER, --) before
# executing the target command for realism; real sudo parses these itself.
while [[ $# -gt 0 ]]; do
  case "$1" in
    -H) shift ;;
    -u) shift 2 ;;
    --) shift; break ;;
    *) break ;;
  esac
done
exec "$@"
SH
chmod +x "$DEPLOY_FIXTURE_BIN/sudo"

# Fake systemctl: only understands the two subcommands restart_or_require_
# admin_setup uses. `is-enabled` and `restart` are logged separately from
# `sudo` so a test can tell a bare (non-root) status read apart from the
# privileged restart.
cat > "$DEPLOY_FIXTURE_BIN/systemctl" <<'SH'
#!/usr/bin/env bash
echo "systemctl $*" >> "$SYSTEMCTL_LOG"
case "$1" in
  is-enabled)
    [[ "$FAKE_UNIT_ENABLED" == "1" ]]
    exit $?
    ;;
  restart)
    exit 0
    ;;
  *)
    exit 1
    ;;
esac
SH
chmod +x "$DEPLOY_FIXTURE_BIN/systemctl"

# Runs restart_or_require_admin_setup against a fixture SYSTEM_UNIT_DIR and
# fake sudo/systemctl. Writes its exit status, combined output, and the two
# fake command logs to files under $FIXTURE_ROOT (rather than packing them
# into one returned string) since the output can itself contain newlines.
run_restart_or_require_admin_setup() {
  local unit_dir="$1"
  local unit_enabled="$2"
  local sudo_log="$FIXTURE_ROOT/sudo.log"
  local systemctl_log="$FIXTURE_ROOT/systemctl.log"
  local output_file="$FIXTURE_ROOT/restart-output.log"
  local status_file="$FIXTURE_ROOT/restart-status"
  local status=0
  local output

  : > "$sudo_log"
  : > "$systemctl_log"

  output="$(env -i \
    PATH="$DEPLOY_FIXTURE_BIN:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    SUDO_LOG="$sudo_log" \
    SYSTEMCTL_LOG="$systemctl_log" \
    FAKE_UNIT_ENABLED="$unit_enabled" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR' SYSTEM_UNIT_DIR='$unit_dir'
      export BACKEND_SERVICE='hsclubs.service' SYSTEMCTL_BIN='$DEPLOY_FIXTURE_BIN/systemctl'
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      backend_service_unit_content /fixture/backend.jar > '$FIXTURE_ROOT/desired.service'
      restart_or_require_admin_setup '$FIXTURE_ROOT/desired.service'
    " 2>&1)" || status=$?

  printf '%s\n' "$status" > "$status_file"
  printf '%s\n' "$output" > "$output_file"
}

### docs/DEPLOYMENT.md sample must match backend_service_unit_content #######

test_deployment_doc_systemd_sample_matches_generated_unit() {
  local expected_ini actual_ini
  local fixture_java_dir="$FIXTURE_ROOT/usr-bin"
  mkdir -p "$fixture_java_dir"
  cat > "$fixture_java_dir/java" <<'SH'
#!/usr/bin/env bash
exit 0
SH
  chmod +x "$fixture_java_dir/java"

  expected_ini="$(awk '
    /^### Run as a service \(systemd\)/ { in_section = 1 }
    in_section && /^```ini$/ { in_block = 1; next }
    in_block && /^```$/ { exit }
    in_block { print }
  ' "$DEPLOYMENT_DOC")"
  expected_ini="${expected_ini//\/usr\/bin\/java/$fixture_java_dir/java}"

  actual_ini="$(env -i PATH="$fixture_java_dir:/usr/bin:/bin" HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR=/opt/hsclubs BACKEND_ENV_FILE=/opt/hsclubs/backend/.env
      export BACKEND_RUN_USER=your-deploy-user SYSTEMD_SCOPE=system
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      backend_service_unit_content /opt/hsclubs/backend/target/demo-0.0.1-SNAPSHOT.jar
    ")"

  assert_eq "$expected_ini" "$actual_ini" \
    "docs/DEPLOYMENT.md's sample unit must be byte-for-byte what backend_service_unit_content generates for its documented defaults"
}

test_restart_only_when_unit_matches_and_enabled() {
  local unit_dir="$FIXTURE_ROOT/unit-matches"
  mkdir -p "$unit_dir"
  # The installed unit must be byte-for-byte what backend_service_unit_content
  # would generate for the same jar path, so build it the same way.
  env -i PATH="$DEPLOY_FIXTURE_BIN:/usr/bin:/bin" HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR' SYSTEM_UNIT_DIR='$unit_dir' BACKEND_SERVICE='hsclubs.service'
      source '$DEPLOY_MAIN'
      backend_service_unit_content /fixture/backend.jar
    " > "$unit_dir/hsclubs.service"

  run_restart_or_require_admin_setup "$unit_dir" "1"
  local status output sudo_log systemctl_log
  status="$(cat "$FIXTURE_ROOT/restart-status")"
  output="$(cat "$FIXTURE_ROOT/restart-output.log")"
  sudo_log="$(cat "$FIXTURE_ROOT/sudo.log")"
  systemctl_log="$(cat "$FIXTURE_ROOT/systemctl.log")"

  [[ "$status" -eq 0 ]] || { printf '  expected exit 0, got %s: %s\n' "$status" "$output" >&2; return 1; }
  [[ "$sudo_log" == *"restart hsclubs.service"* ]] || {
    printf '  expected the restart-only sudo command, got: %s\n' "$sudo_log" >&2
    return 1
  }
  [[ "$systemctl_log" != *"daemon-reload"* && "$systemctl_log" != *"systemctl enable"* ]] || {
    printf '  must never daemon-reload or enable an unchanged unit, got: %s\n' "$systemctl_log" >&2
    return 1
  }
}

test_refuses_before_privileged_install_when_unit_is_missing() {
  local unit_dir="$FIXTURE_ROOT/unit-missing"
  mkdir -p "$unit_dir"

  run_restart_or_require_admin_setup "$unit_dir" "1"
  local status output sudo_log
  status="$(cat "$FIXTURE_ROOT/restart-status")"
  output="$(cat "$FIXTURE_ROOT/restart-output.log")"
  sudo_log="$(cat "$FIXTURE_ROOT/sudo.log")"

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ -z "$sudo_log" ]] || {
    printf '  must never invoke sudo when the unit is missing, got: %s\n' "$sudo_log" >&2
    return 1
  }
  [[ "$output" == *"one-time admin task"* ]] || {
    printf '  expected one-time admin setup instructions, got: %s\n' "$output" >&2
    return 1
  }
}

test_admin_setup_instructions_never_copy_to_fixed_path() {
  local unit_dir="$FIXTURE_ROOT/unit-missing-fixed-path-check"
  mkdir -p "$unit_dir"

  run_restart_or_require_admin_setup "$unit_dir" "1"
  local status output
  status="$(cat "$FIXTURE_ROOT/restart-status")"
  output="$(cat "$FIXTURE_ROOT/restart-output.log")"

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ "$output" == *"$FIXTURE_ROOT/desired.service"* ]] || {
    printf '  expected the message to point at the original generated file, got: %s\n' "$output" >&2
    return 1
  }
  [[ "$output" != *"hsclubs.service.desired"* ]] || {
    printf '  must never copy the desired unit to a predictable fixed path, got: %s\n' "$output" >&2
    return 1
  }
}

test_refuses_before_privileged_install_when_unit_differs() {
  local unit_dir="$FIXTURE_ROOT/unit-differs"
  mkdir -p "$unit_dir"
  printf '[Unit]\nDescription=Some other unit\n' > "$unit_dir/hsclubs.service"

  run_restart_or_require_admin_setup "$unit_dir" "1"
  local status output sudo_log
  status="$(cat "$FIXTURE_ROOT/restart-status")"
  output="$(cat "$FIXTURE_ROOT/restart-output.log")"
  sudo_log="$(cat "$FIXTURE_ROOT/sudo.log")"

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ -z "$sudo_log" ]] || {
    printf '  must never invoke sudo when the installed unit differs, got: %s\n' "$sudo_log" >&2
    return 1
  }
}

test_refuses_before_privileged_install_when_unit_not_enabled() {
  local unit_dir="$FIXTURE_ROOT/unit-not-enabled"
  mkdir -p "$unit_dir"
  env -i PATH="$DEPLOY_FIXTURE_BIN:/usr/bin:/bin" HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR' SYSTEM_UNIT_DIR='$unit_dir' BACKEND_SERVICE='hsclubs.service'
      source '$DEPLOY_MAIN'
      backend_service_unit_content /fixture/backend.jar
    " > "$unit_dir/hsclubs.service"

  run_restart_or_require_admin_setup "$unit_dir" "0"
  local status output sudo_log
  status="$(cat "$FIXTURE_ROOT/restart-status")"
  output="$(cat "$FIXTURE_ROOT/restart-output.log")"
  sudo_log="$(cat "$FIXTURE_ROOT/sudo.log")"

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ -z "$sudo_log" ]] || {
    printf '  must never invoke sudo when the unit is not enabled, got: %s\n' "$sudo_log" >&2
    return 1
  }
}

### Restricted sudo deployment: run_as_backend_user skips sudo for self #####

test_run_as_backend_user_skips_sudo_when_same_user() {
  local output
  output="$(env -i PATH="/usr/bin:/bin" HOME="$FIXTURE_ROOT/home" \
    bash --noprofile --norc -c "
      export APP_DIR='$REPO_DIR' SYSTEMD_SCOPE=system
      export BACKEND_RUN_USER=\"\$(id -un)\"
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      run_as_backend_user echo backend-user-ran
    " 2>&1)"
  assert_eq "backend-user-ran" "$output" \
    "must run directly, without sudo, when BACKEND_RUN_USER is the current user"
}

test_run_as_backend_user_uses_sudo_for_a_different_user() {
  local output
  output="$(env -i PATH="$DEPLOY_FIXTURE_BIN:/usr/bin:/bin" HOME="$FIXTURE_ROOT/home" \
    SUDO_LOG="$FIXTURE_ROOT/different-user-sudo.log" \
    bash --noprofile --norc -c "
      : > \"\$SUDO_LOG\"
      export APP_DIR='$REPO_DIR' SYSTEMD_SCOPE=system BACKEND_RUN_USER=a-different-service-account
      # shellcheck source=/dev/null
      source '$DEPLOY_MAIN'
      run_as_backend_user echo should-not-run-directly >/dev/null 2>&1
      cat \"\$SUDO_LOG\"
    " 2>&1)"
  [[ "$output" == *"sudo -H -u a-different-service-account"* ]] || {
    printf '  expected sudo -H -u a-different-service-account, got: %s\n' "$output" >&2
    return 1
  }
}

### Run everything ###########################################################

### Deployment history recording #############################################

test_record_deployment_history_appends_success_with_sha() {
  local history_file="$FIXTURE_ROOT/history-success.log"
  rm -f "$history_file"
  run_isolated_with_git "export APP_DIR='$HISTORY_REPO_DIR' DEPLOY_HISTORY_FILE='$history_file'" '
    record_deployment_history success
  ' >/dev/null

  local line
  line="$(cat "$history_file")"
  [[ "$line" == *$'\t'"success"$'\t'"$HISTORY_REPO_SHA" ]] || {
    printf '  expected a line ending in success<TAB>%s, got: %s\n' "$HISTORY_REPO_SHA" "$line" >&2
    return 1
  }
}

test_record_deployment_history_appends_failure_with_sha() {
  local history_file="$FIXTURE_ROOT/history-failure.log"
  rm -f "$history_file"
  run_isolated_with_git "export APP_DIR='$HISTORY_REPO_DIR' DEPLOY_HISTORY_FILE='$history_file'" '
    record_deployment_history failure
  ' >/dev/null

  local line
  line="$(cat "$history_file")"
  [[ "$line" == *$'\t'"failure"$'\t'"$HISTORY_REPO_SHA" ]] || {
    printf '  expected a line ending in failure<TAB>%s, got: %s\n' "$HISTORY_REPO_SHA" "$line" >&2
    return 1
  }
}

test_record_deployment_history_appends_rather_than_overwrites() {
  local history_file="$FIXTURE_ROOT/history-append.log"
  rm -f "$history_file"
  run_isolated_with_git "export APP_DIR='$HISTORY_REPO_DIR' DEPLOY_HISTORY_FILE='$history_file'" '
    record_deployment_history success
    record_deployment_history failure
  ' >/dev/null

  local line_count
  line_count="$(wc -l < "$history_file")"
  assert_eq "2" "$line_count" "each recorded attempt must add a line, not replace the file"
}

test_record_deployment_history_falls_back_to_unknown_sha_without_git_repo() {
  local history_file="$FIXTURE_ROOT/history-no-repo.log"
  local non_repo_dir="$FIXTURE_ROOT/history-non-repo"
  rm -f "$history_file"
  mkdir -p "$non_repo_dir"
  run_isolated_with_git "export APP_DIR='$non_repo_dir' DEPLOY_HISTORY_FILE='$history_file'" '
    record_deployment_history success
  ' >/dev/null

  local line
  line="$(cat "$history_file")"
  [[ "$line" == *$'\t'"success"$'\t'"unknown" ]] || {
    printf '  expected the sha field to fall back to unknown, got: %s\n' "$line" >&2
    return 1
  }
}

test_default_history_file_lives_under_app_dir() {
  local output
  output="$(run_isolated_with_git "export APP_DIR='$HISTORY_REPO_DIR'" '
    echo "$DEPLOY_HISTORY_FILE"
  ')"
  assert_eq "$HISTORY_REPO_DIR/deploy-history.log" "$output" \
    "the default deployment history file must live under APP_DIR"
}

test_exit_trap_records_failure_for_explicit_exit() {
  local history_file="$FIXTURE_ROOT/history-exit.log"
  rm -f "$history_file"
  local status=0
  run_isolated_with_git "export APP_DIR='$HISTORY_REPO_DIR' DEPLOY_HISTORY_FILE='$history_file'" '
    DEPLOYMENT_STARTED=1
    trap on_exit EXIT
    exit 23
  ' >/dev/null || status=$?

  assert_eq "23" "$status" "the original failure status must be preserved" || return 1
  local line
  line="$(cat "$history_file")"
  [[ "$line" == *$'\t'"failure"$'\t'"$HISTORY_REPO_SHA" ]] || {
    printf '  expected the EXIT trap to record a failure line, got: %s\n' "$line" >&2
    return 1
  }
}

run_test "deployment history: records success with the current sha" test_record_deployment_history_appends_success_with_sha
run_test "deployment history: records failure with the current sha" test_record_deployment_history_appends_failure_with_sha
run_test "deployment history: appends rather than overwrites" test_record_deployment_history_appends_rather_than_overwrites
run_test "deployment history: falls back to unknown sha outside a git repo" test_record_deployment_history_falls_back_to_unknown_sha_without_git_repo
run_test "deployment history: default path lives under APP_DIR" test_default_history_file_lives_under_app_dir
run_test "deployment history: the EXIT trap records explicit-exit failures" test_exit_trap_records_failure_for_explicit_exit

run_test "engine range: caret lower bound matches" test_range_lower_bound_caret_matches
run_test "engine range: below caret lower bound is rejected" test_range_below_lower_bound_caret_rejected
run_test "engine range: next major is rejected by caret" test_range_next_major_rejected_by_caret
run_test "engine range: >= lower bound matches" test_range_gte_lower_bound_matches
run_test "engine range: below >= lower bound is rejected" test_range_below_gte_bound_rejected
run_test "engine range: far above >= lower bound matches" test_range_far_above_gte_bound_matches
run_test "engine range: prerelease version string is rejected" test_range_rejects_prerelease_version_string
run_test "engine range: shorthand >= clause fails closed" test_range_rejects_shorthand_gte_clause_instead_of_accepting
run_test "engine range: garbage >= clause fails closed" test_range_rejects_garbage_gte_clause_instead_of_accepting
run_test "engine range: shorthand ^ clause fails closed" test_range_rejects_shorthand_caret_clause_instead_of_accepting
run_test "compatible selection: highest across clauses" test_selects_highest_compatible_across_clauses
run_test "compatible selection: never picks a nightly directory" test_never_selects_nightly_directory
run_test "override: DEPLOY_NODE_VERSION selects requested version" test_deploy_node_version_override_selects_requested_version
run_test "override: incompatible DEPLOY_NODE_VERSION fails" test_deploy_node_version_incompatible_override_fails
run_test "no nvm + incompatible system node fails" test_no_nvm_and_incompatible_system_node_fails_with_actionable_message
run_test "load_nvm survives a stale default alias and an unset variable" test_load_nvm_survives_stale_default_alias_and_unset_variable
run_test "load_nvm restores caller shell options" test_load_nvm_restores_shell_options_after_sourcing
run_test "restart-only fast path when unit matches and is enabled" test_restart_only_when_unit_matches_and_enabled
run_test "docs/DEPLOYMENT.md systemd sample matches the generated unit" test_deployment_doc_systemd_sample_matches_generated_unit
run_test "refuses before privileged install: unit missing" test_refuses_before_privileged_install_when_unit_is_missing
run_test "admin setup instructions never copy to a fixed path" test_admin_setup_instructions_never_copy_to_fixed_path
run_test "refuses before privileged install: unit differs" test_refuses_before_privileged_install_when_unit_differs
run_test "refuses before privileged install: unit not enabled" test_refuses_before_privileged_install_when_unit_not_enabled
run_test "run_as_backend_user skips sudo for the same user" test_run_as_backend_user_skips_sudo_when_same_user
run_test "run_as_backend_user uses sudo for a different user" test_run_as_backend_user_uses_sudo_for_a_different_user

printf '\n%d run, %d failed\n' "$TESTS_RUN" "$TESTS_FAILED"
[[ "$TESTS_FAILED" -eq 0 ]]
