#!/usr/bin/env bash
# Focused tests for scripts/backup-mysql.sh. No test framework: each check is
# a small function that reports pass/fail, run against fixtures under a
# throwaway temp directory. Nothing here touches a real MySQL server -- every
# PATH, BACKEND_ENV_FILE, and BACKUP_DIR used below is a fixture created by
# this script, with a fake mysqldump standing in for the real client.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_MYSQL="$SCRIPT_DIR/backup-mysql.sh"

TESTS_RUN=0
TESTS_FAILED=0
TESTS_SKIPPED=0

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

skip() {
  TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
  printf 'skip - %s\n' "$*"
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

# Fake mysqldump: writes fixed, recognizable dummy SQL to stdout, or fails
# outright when MYSQLDUMP_SHOULD_FAIL=1 (simulating a real dump error, for
# example a dropped connection).
cat > "$FIXTURE_ROOT/bin/mysqldump" <<'SH'
#!/usr/bin/env bash
echo "mysqldump $*" >> "$MYSQLDUMP_LOG"
echo "ENV MYSQL_PWD=${MYSQL_PWD-<unset>}" >> "$MYSQLDUMP_LOG"
if [[ "${MYSQLDUMP_SHOULD_FAIL:-0}" == "1" ]]; then
  echo "mysqldump: simulated failure" >&2
  exit 1
fi
echo "-- dummy dump for testing"
echo "CREATE TABLE clubs (id INT);"
SH
chmod +x "$FIXTURE_ROOT/bin/mysqldump"

# On some filesystems (notably NTFS mounted under Git Bash on Windows),
# chmod is accepted but does not actually restrict permission bits, so a
# freshly created file always reports mode 644 regardless of what was
# requested. Detect that once up front so the two tests below can skip with
# an explicit message instead of failing on an environment they cannot pass
# in.
filesystem_honors_chmod() {
  local probe
  probe="$(mktemp -p "$FIXTURE_ROOT")"
  chmod 600 "$probe"
  local mode
  mode="$(stat -c '%a' "$probe" 2>/dev/null || stat -f '%Lp' "$probe")"
  rm -f "$probe"
  [[ "$mode" == "600" ]]
}

run_isolated() {
  local env_assignments="$1"
  local snippet="$2"
  env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    MYSQLDUMP_LOG="$FIXTURE_ROOT/mysqldump.log" \
    bash --noprofile --norc -c "
      $env_assignments
      # shellcheck source=/dev/null
      source '$BACKUP_MYSQL'
      $snippet
    " 2>&1
}

### Fixtures ###################################################################

ENV_FILE="$FIXTURE_ROOT/backend.env"
cat > "$ENV_FILE" <<'ENV'
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/mydb?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=root
DB_PASSWORD=super-secret-password
ENV

COMMON_ENV="export BACKEND_ENV_FILE='$ENV_FILE'"

### datasource_url_* parsing ##################################################

test_parses_host_from_datasource_url() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    datasource_url_host "jdbc:mysql://localhost:3306/mydb?useSSL=false"
  ')"
  assert_eq "localhost" "$output" "host must be parsed from the jdbc URL"
}

test_parses_port_from_datasource_url() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    datasource_url_port "jdbc:mysql://localhost:3306/mydb?useSSL=false"
  ')"
  assert_eq "3306" "$output" "port must be parsed from the jdbc URL"
}

test_parses_database_from_datasource_url() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    datasource_url_database "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Shanghai"
  ')"
  assert_eq "mydb" "$output" "database name must be parsed and stop at the query string"
}

test_malformed_datasource_url_fails_with_actionable_message() {
  local output
  local status=0
  output="$(run_isolated "$COMMON_ENV" '
    datasource_url_host "not-a-jdbc-url"
  ')" || status=$?
  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit, got 0\n' >&2; return 1; }
  [[ "$output" == *"Could not parse host"* ]] || {
    printf '  expected an actionable parse error, got: %s\n' "$output" >&2
    return 1
  }
}

### read_env_value reads credentials without sourcing ########################

test_read_env_value_reads_username() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    read_env_value DB_USERNAME
  ')"
  assert_eq "root" "$output" "must read DB_USERNAME from the env file"
}

test_read_env_value_reads_password() {
  local output
  output="$(run_isolated "$COMMON_ENV" '
    read_env_value DB_PASSWORD
  ')"
  assert_eq "super-secret-password" "$output" "must read DB_PASSWORD from the env file"
}

test_read_env_value_never_sources_a_malicious_entry() {
  local malicious_env="$FIXTURE_ROOT/malicious.env"
  local marker_file="$FIXTURE_ROOT/should-not-exist"
  cat > "$malicious_env" <<ENV
DB_USERNAME=root
DB_PASSWORD=x; touch $marker_file; echo pwned
ENV
  run_isolated "export BACKEND_ENV_FILE='$malicious_env'" '
    read_env_value DB_PASSWORD
  ' >/dev/null || true
  [[ ! -e "$marker_file" ]] || {
    printf '  the malicious entry executed as shell code; the file must never be sourced\n' >&2
    return 1
  }
}

### create_mysql_defaults_file: private credentials file ######################

test_defaults_file_has_restrictive_permissions() {
  local output
  local defaults_file
  if ! filesystem_honors_chmod; then
    skip "defaults file: mode 0600 (this filesystem does not enforce POSIX permission bits)"
    return 0
  fi
  output="$(run_isolated "$COMMON_ENV" '
    create_mysql_defaults_file "localhost" "3306" "root" "secret"
  ')"
  defaults_file="$(printf '%s' "$output" | tail -n1)"
  [[ -f "$defaults_file" ]] || { printf '  defaults file was not created: %s\n' "$output" >&2; return 1; }
  local mode
  mode="$(stat -c '%a' "$defaults_file" 2>/dev/null || stat -f '%Lp' "$defaults_file")"
  rm -f "$defaults_file"
  assert_eq "600" "$mode" "the defaults-extra-file must be mode 0600"
}

# Verifies the actual mysqldump invocation (command line and environment)
# recorded by the fixture never carries the password, rather than grepping
# the script's own source for one particular spelling of the flag -- that
# would miss -p"$pw", `--password <pw>` (space-separated), or MYSQL_PWD.
test_dump_and_compress_never_passes_credentials_via_command_line_or_env() {
  local backup_dir="$FIXTURE_ROOT/no-cli-password"
  mkdir -p "$backup_dir"
  local destination="$backup_dir/mydb-20990101T000000Z.sql.gz"
  local log="$FIXTURE_ROOT/mysqldump-no-password.log"
  run_isolated "$COMMON_ENV MYSQLDUMP_LOG='$log'" "
    defaults_file=\$(create_mysql_defaults_file localhost 3306 root 'super-secret-password')
    dump_and_compress \"\$defaults_file\" mydb '$destination' '$backup_dir'
    rm -f \"\$defaults_file\"
  " >/dev/null

  local invocation
  invocation="$(grep '^mysqldump ' "$log" | tail -n1)"
  [[ -n "$invocation" ]] || { printf '  mysqldump was never invoked\n' >&2; return 1; }
  [[ "$invocation" != *"super-secret-password"* ]] || {
    printf '  the password must never appear on the mysqldump command line, got: %s\n' "$invocation" >&2
    return 1
  }
  [[ "$invocation" != *"-p"* ]] || {
    printf '  must never pass -p on the mysqldump command line, got: %s\n' "$invocation" >&2
    return 1
  }
  [[ "$invocation" != *"--password"* ]] || {
    printf '  must never pass --password on the mysqldump command line, got: %s\n' "$invocation" >&2
    return 1
  }
  local env_line
  env_line="$(grep '^ENV MYSQL_PWD=' "$log" | tail -n1)"
  assert_eq "ENV MYSQL_PWD=<unset>" "$env_line" "must never set MYSQL_PWD in the mysqldump environment"
}

### dump_and_compress: atomic, validated gzip output ##########################

test_dump_and_compress_produces_valid_gzip_with_restrictive_permissions() {
  if ! filesystem_honors_chmod; then
    skip "dump_and_compress: valid gzip, mode 0600 (this filesystem does not enforce POSIX permission bits)"
    return 0
  fi
  local backup_dir="$FIXTURE_ROOT/dump-ok"
  mkdir -p "$backup_dir"
  local destination="$backup_dir/mydb-20990101T000000Z.sql.gz"
  run_isolated "$COMMON_ENV" "
    defaults_file=\$(create_mysql_defaults_file localhost 3306 root secret)
    dump_and_compress \"\$defaults_file\" mydb '$destination' '$backup_dir'
    rm -f \"\$defaults_file\"
  " >/dev/null

  [[ -f "$destination" ]] || { printf '  destination backup file was not created\n' >&2; return 1; }
  gzip -t "$destination" || { printf '  destination is not a valid gzip stream\n' >&2; return 1; }
  local mode
  mode="$(stat -c '%a' "$destination" 2>/dev/null || stat -f '%Lp' "$destination")"
  assert_eq "600" "$mode" "the backup file must be mode 0600"
}

test_dump_and_compress_leaves_no_destination_when_mysqldump_fails() {
  local backup_dir="$FIXTURE_ROOT/dump-mysqldump-fails"
  mkdir -p "$backup_dir"
  local destination="$backup_dir/mydb-20990101T000000Z.sql.gz"
  local status=0
  run_isolated "$COMMON_ENV MYSQLDUMP_SHOULD_FAIL=1" "
    defaults_file=\$(create_mysql_defaults_file localhost 3306 root secret)
    dump_and_compress \"\$defaults_file\" mydb '$destination' '$backup_dir'
  " >/dev/null || status=$?

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit when mysqldump fails\n' >&2; return 1; }
  [[ ! -e "$destination" ]] || {
    printf '  must never leave a destination file behind after a failed dump\n' >&2
    return 1
  }
  local leftovers
  leftovers="$(find "$backup_dir" -maxdepth 1 -type f | wc -l)"
  assert_eq "0" "$leftovers" "must clean up its own temp file after a failed dump"
}

test_dump_and_compress_leaves_no_files_when_gzip_fails() {
  local backup_dir="$FIXTURE_ROOT/dump-gzip-fails"
  mkdir -p "$backup_dir"
  local failing_bin="$FIXTURE_ROOT/gzip-fails-bin"
  mkdir -p "$failing_bin"
  # A hostile `gzip` standing in for one that fails outright (for example,
  # out of disk space), rather than producing a corrupt stream.
  cat > "$failing_bin/gzip" <<'SH'
#!/usr/bin/env bash
echo "gzip: simulated failure" >&2
exit 1
SH
  chmod +x "$failing_bin/gzip"

  local destination="$backup_dir/mydb-20990101T000000Z.sql.gz"
  local status=0
  local output
  output="$(env -i \
    PATH="$failing_bin:$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    MYSQLDUMP_LOG="$FIXTURE_ROOT/mysqldump.log" \
    bash --noprofile --norc -c "
      $COMMON_ENV
      source '$BACKUP_MYSQL'
      defaults_file=\$(create_mysql_defaults_file localhost 3306 root secret)
      dump_and_compress \"\$defaults_file\" mydb '$destination' '$backup_dir'
    " 2>&1)" || status=$?

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit when gzip fails\n' >&2; return 1; }
  [[ ! -e "$destination" ]] || {
    printf '  must never leave a destination file behind after a failed gzip\n' >&2
    return 1
  }
  local leftovers
  leftovers="$(find "$backup_dir" -maxdepth 1 -type f | wc -l)"
  assert_eq "0" "$leftovers" "must clean up the plaintext dump when gzip itself fails"
}

test_dump_and_compress_rejects_a_corrupt_gzip_stream() {
  local backup_dir="$FIXTURE_ROOT/dump-corrupt-gzip"
  mkdir -p "$backup_dir"
  local corrupt_bin="$FIXTURE_ROOT/corrupt-bin"
  mkdir -p "$corrupt_bin"
  # A hostile `gzip` standing in for a real one that produced a corrupted
  # stream: compresses normally, but always fails `-t` integrity validation,
  # so dump_and_compress must never move that file to its destination.
  cat > "$corrupt_bin/gzip" <<'SH'
#!/usr/bin/env bash
if [[ "$1" == "-t" ]]; then
  exit 1
fi
exec /usr/bin/gzip "$@"
SH
  chmod +x "$corrupt_bin/gzip"

  local destination="$backup_dir/mydb-20990101T000000Z.sql.gz"
  local status=0
  local output
  output="$(env -i \
    PATH="$corrupt_bin:$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    MYSQLDUMP_LOG="$FIXTURE_ROOT/mysqldump.log" \
    bash --noprofile --norc -c "
      $COMMON_ENV
      source '$BACKUP_MYSQL'
      defaults_file=\$(create_mysql_defaults_file localhost 3306 root secret)
      dump_and_compress \"\$defaults_file\" mydb '$destination' '$backup_dir'
    " 2>&1)" || status=$?

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit when gzip integrity validation fails\n' >&2; return 1; }
  [[ "$output" == *"gzip integrity validation"* ]] || {
    printf '  expected an actionable gzip validation error, got: %s\n' "$output" >&2
    return 1
  }
  [[ ! -e "$destination" ]] || {
    printf '  must never move a stream that failed gzip -t to the destination\n' >&2
    return 1
  }
}

### prune_old_backups: retention ##############################################

test_prune_old_backups_deletes_only_files_past_retention() {
  local backup_dir="$FIXTURE_ROOT/prune"
  mkdir -p "$backup_dir"
  local old_file="$backup_dir/mydb-old.sql.gz"
  local new_file="$backup_dir/mydb-new.sql.gz"
  echo old | gzip > "$old_file"
  echo new | gzip > "$new_file"
  touch -d '30 days ago' "$old_file"
  touch -d '1 day ago' "$new_file"

  run_isolated "" "
    prune_old_backups '$backup_dir' 14
  " >/dev/null

  [[ ! -e "$old_file" ]] || { printf '  a backup past the retention window must be deleted\n' >&2; return 1; }
  [[ -e "$new_file" ]] || { printf '  a backup within the retention window must be kept\n' >&2; return 1; }
}

### perform_backup: end-to-end orchestration ##################################

test_perform_backup_creates_one_file_named_after_database() {
  local backup_dir="$FIXTURE_ROOT/perform-backup"
  run_isolated "$COMMON_ENV BACKUP_DIR='$backup_dir'" '
    perform_backup
  ' >/dev/null

  local matches
  matches="$(find "$backup_dir" -maxdepth 1 -type f -name 'mydb-*.sql.gz' | wc -l)"
  assert_eq "1" "$matches" "perform_backup must create exactly one backup file named after the database"
}

test_perform_backup_leaves_no_defaults_file_when_mysqldump_fails() {
  local tmp_dir="$FIXTURE_ROOT/tmp-defaults-fail"
  mkdir -p "$tmp_dir"
  local backup_dir="$FIXTURE_ROOT/perform-backup-mysqldump-fails"
  local status=0
  run_isolated "$COMMON_ENV MYSQLDUMP_SHOULD_FAIL=1 BACKUP_DIR='$backup_dir' TMPDIR='$tmp_dir'" '
    perform_backup
  ' >/dev/null || status=$?

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit when mysqldump fails\n' >&2; return 1; }
  local leftovers
  leftovers="$(find "$tmp_dir" -maxdepth 1 -type f | wc -l)"
  assert_eq "0" "$leftovers" "the mode-0600 defaults file must not be left behind after a failed backup"
}

test_perform_backup_dies_without_required_env_values() {
  local incomplete_env="$FIXTURE_ROOT/incomplete.env"
  echo "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/mydb" > "$incomplete_env"
  local backup_dir="$FIXTURE_ROOT/perform-backup-incomplete"
  local status=0
  local output
  output="$(run_isolated "export BACKEND_ENV_FILE='$incomplete_env' BACKUP_DIR='$backup_dir'" '
    perform_backup
  ')" || status=$?

  [[ "$status" -ne 0 ]] || { printf '  expected a non-zero exit without DB_USERNAME/DB_PASSWORD\n' >&2; return 1; }
  [[ "$output" == *"DB_USERNAME"* ]] || {
    printf '  expected an actionable message naming the missing variable, got: %s\n' "$output" >&2
    return 1
  }
}

### Overlap lock: main() refuses to run a second time concurrently ###########

test_main_skips_when_lock_is_already_held() {
  if ! command -v flock >/dev/null 2>&1; then
    skip "overlap lock (flock is not available in this environment)"
    return 0
  fi

  local backup_dir="$FIXTURE_ROOT/lock-held"
  mkdir -p "$backup_dir"
  local lock_file="$backup_dir/.backup.lock"
  exec 8>"$lock_file"
  flock -n 8 || { printf '  test setup could not acquire its own lock\n' >&2; return 1; }

  local status=0
  local output
  output="$(env -i \
    PATH="$FIXTURE_ROOT/bin:/usr/bin:/bin" \
    HOME="$FIXTURE_ROOT/home" \
    MYSQLDUMP_LOG="$FIXTURE_ROOT/mysqldump-lock-test.log" \
    bash --noprofile --norc -c "
      $COMMON_ENV
      export BACKUP_DIR='$backup_dir' LOCK_FILE='$lock_file'
      '$BACKUP_MYSQL'
    " 2>&1)" || status=$?
  exec 8>&-

  [[ "$status" -eq 0 ]] || { printf '  a skipped run must still exit 0, got %s: %s\n' "$status" "$output" >&2; return 1; }
  [[ "$output" == *"already running"* ]] || {
    printf '  expected an "already running" message, got: %s\n' "$output" >&2
    return 1
  }
  local matches
  matches="$(find "$backup_dir" -maxdepth 1 -type f -name '*.sql.gz' | wc -l)"
  assert_eq "0" "$matches" "a run that is skipped because of the lock must never produce a backup file"
}

### Run everything #############################################################

run_test "datasource url: parses host" test_parses_host_from_datasource_url
run_test "datasource url: parses port" test_parses_port_from_datasource_url
run_test "datasource url: parses database" test_parses_database_from_datasource_url
run_test "datasource url: malformed url fails with actionable message" test_malformed_datasource_url_fails_with_actionable_message
run_test "read_env_value: reads DB_USERNAME" test_read_env_value_reads_username
run_test "read_env_value: reads DB_PASSWORD" test_read_env_value_reads_password
run_test "read_env_value: never sources a malicious entry" test_read_env_value_never_sources_a_malicious_entry
run_test "defaults file: mode 0600" test_defaults_file_has_restrictive_permissions
run_test "dump_and_compress: credentials never passed via command line or environment" test_dump_and_compress_never_passes_credentials_via_command_line_or_env
run_test "dump_and_compress: valid gzip, mode 0600" test_dump_and_compress_produces_valid_gzip_with_restrictive_permissions
run_test "dump_and_compress: no destination left behind on mysqldump failure" test_dump_and_compress_leaves_no_destination_when_mysqldump_fails
run_test "dump_and_compress: no files left behind when gzip fails" test_dump_and_compress_leaves_no_files_when_gzip_fails
run_test "dump_and_compress: rejects a corrupt gzip stream" test_dump_and_compress_rejects_a_corrupt_gzip_stream
run_test "prune_old_backups: deletes only files past retention" test_prune_old_backups_deletes_only_files_past_retention
run_test "perform_backup: creates exactly one file named after the database" test_perform_backup_creates_one_file_named_after_database
run_test "perform_backup: leaves no defaults file when mysqldump fails" test_perform_backup_leaves_no_defaults_file_when_mysqldump_fails
run_test "perform_backup: dies without required env values" test_perform_backup_dies_without_required_env_values
run_test "overlap lock: a second concurrent run skips instead of colliding" test_main_skips_when_lock_is_already_held

printf '\n%d run, %d failed, %d skipped\n' "$TESTS_RUN" "$TESTS_FAILED" "$TESTS_SKIPPED"
[[ "$TESTS_FAILED" -eq 0 ]]
