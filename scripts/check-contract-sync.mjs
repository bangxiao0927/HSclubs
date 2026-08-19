#!/usr/bin/env node
// Verifies this repository's vendored contracts/v1 is the same one the guiding page publishes.
//
// The per-repo contract check only proves the vendored files match their own manifest -- it cannot
// see that the copy has drifted from upstream, which is exactly how a callback-path change once
// slipped through in one repo and not the others. This step closes that gap: manifest.json holds a
// sha-256 of every shared file, so comparing this repo's manifest to the canonical one on
// hsclubs-guiding-page main is enough to prove every shared file is identical.
//
// A real drift (a successful fetch that disagrees) fails the build. A network failure only skips
// the check, so an unrelated PR is never turned red by GitHub being briefly unreachable.
import { readFileSync } from 'node:fs'
import process from 'node:process'

const UPSTREAM =
  process.env.HSCLUBS_CONTRACT_MANIFEST_URL ??
  'https://raw.githubusercontent.com/bangxiao0927/hsclubs-guiding-page/main/contracts/v1/manifest.json'
const LOCAL_PATH = 'contracts/v1/manifest.json'

// Line endings are normalised so a checkout on a platform with other endings is not read as drift.
const normalize = (text) => text.replace(/\r\n/g, '\n').trimEnd()

const fetchWithRetry = async (url, attempts = 3) => {
  let lastError
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, { headers: { 'user-agent': 'hsclubs-contract-sync' } })
      if (!response.ok) throw new Error(`${url} answered ${response.status}`)
      return await response.text()
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, 1000 * attempt))
    }
  }
  throw lastError
}

const main = async () => {
  const local = normalize(readFileSync(LOCAL_PATH, 'utf8'))

  let upstream
  try {
    upstream = normalize(await fetchWithRetry(UPSTREAM))
  } catch (error) {
    console.warn(`Skipping contract sync check: could not fetch the canonical manifest (${error.message}).`)
    return
  }

  if (local !== upstream) {
    console.error('Vendored contracts/v1 has drifted from hsclubs-guiding-page main.')
    console.error('Re-copy the contracts/ directory from the guiding page; see contracts/v1/README.md.')
    process.exit(1)
  }
  console.log('contracts/v1 matches the canonical manifest published by the guiding page.')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
