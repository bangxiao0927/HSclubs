#!/usr/bin/env node
// Guards the generated club SQL against drifting from the official clubs CSV.
//
// scripts/generate_clubs_sql.py owns the CSV parsing, the category map and the
// rename map, so this shells out to it inside a scratch directory rather than
// reimplementing any of that in JavaScript. It is the club-list counterpart of
// scripts/check-contract-sync.mjs, and it exists because the committed
// db/h2/data.sql had already drifted from its own generator: the generator
// wrote to a path that no longer existed, so regenerating silently left the
// real fixture stale.
//
// Usage: node scripts/check-club-sql-sync.mjs

import { execFileSync } from 'node:child_process'
import {
  copyFileSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const GENERATOR = 'scripts/generate_clubs_sql.py'
// Every file the generator writes, relative to the repository root. These paths
// have to match the constants in the generator.
const GENERATED = [
  'mvhs_clubs_seed.sql',
  'mvhs_clubs_refresh.sql',
  'backend/src/main/resources/db/h2/data.sql',
]
const CSV_PATTERN = /^Official .*\.csv$/

function fail(message) {
  console.error(message)
  process.exit(1)
}

function regenerate() {
  const scratch = mkdtempSync(join(tmpdir(), 'hsclubs-club-sql-'))
  try {
    // The generator resolves its inputs and outputs from the repository root,
    // so the scratch copy has to keep the same layout.
    mkdirSync(join(scratch, 'scripts'), { recursive: true })
    copyFileSync(join(ROOT, GENERATOR), join(scratch, GENERATOR))

    const csvFiles = readdirSync(ROOT).filter((name) => CSV_PATTERN.test(name))
    if (csvFiles.length === 0) {
      throw new Error('no "Official ... .csv" club list at the repository root')
    }
    for (const name of csvFiles) {
      copyFileSync(join(ROOT, name), join(scratch, name))
    }

    try {
      execFileSync('python3', [GENERATOR], { cwd: scratch, stdio: 'pipe' })
    } catch (error) {
      if (error.code === 'ENOENT') {
        throw new Error('python3 is not on PATH; the club SQL check needs it')
      }
      throw new Error(`generator failed:\n${error.stderr?.toString() ?? error.message}`)
    }

    const drifted = []
    for (const relative of GENERATED) {
      let committed
      try {
        committed = readFileSync(join(ROOT, relative), 'utf8')
      } catch {
        drifted.push(`${relative} (missing)`)
        continue
      }
      if (committed !== readFileSync(join(scratch, relative), 'utf8')) {
        drifted.push(relative)
      }
    }
    return drifted
  } finally {
    rmSync(scratch, { recursive: true, force: true })
  }
}

let drifted
try {
  drifted = regenerate()
} catch (error) {
  fail(`club SQL sync check could not run: ${error.message}`)
}

if (drifted.length > 0) {
  fail(
    'Generated club SQL is out of sync with the official list CSV:\n' +
      drifted.map((name) => `  - ${name}`).join('\n') +
      '\n\nRun `python3 scripts/generate_clubs_sql.py` and commit the result.',
  )
}

console.log(`Club SQL is in sync (${GENERATED.length} generated files).`)
