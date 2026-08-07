/**
 * Recovery for a stale lazy-loaded view chunk.
 *
 * Every route component is code-split into a hash-named chunk. A redeploy
 * replaces those hashes, so a browser still holding the previous index.html
 * (open tab, cached document, back-forward cache) asks for a filename that no
 * longer exists. The host answers that request with the SPA fallback -- 200
 * and index.html, verified against production -- so the dynamic import fails
 * on a MIME/parse error rather than a clean 404, vue-router aborts the
 * navigation, and from the student's point of view the button they just
 * clicked does nothing at all: no error, no page change.
 *
 * Reloading the browser at the requested URL fetches the current index.html
 * and its current chunk manifest, which is the only way the old document can
 * recover.
 */
const RELOAD_MARKER_KEY = 'hsclubs.staleChunkReload'

// Covers the wording used by Chrome/Safari/Firefox for a failed dynamic
// import plus the bundler-level chunk errors, since none of them share a
// stable error type to match on.
const STALE_CHUNK_MESSAGE =
  /dynamically imported module|module script failed|failed to fetch dynamically|ChunkLoadError|Loading (?:CSS )?chunk .+ failed/i

export const isStaleChunkError = (error: unknown): boolean => {
  if (!error) {
    return false
  }
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
  return STALE_CHUNK_MESSAGE.test(message)
}

const defaultReload = (target: string) => {
  window.location.assign(target)
}

/**
 * Reloads at `target` once. The marker makes this at-most-once per target: if
 * the freshly fetched manifest still cannot load the chunk, reloading again
 * would spin forever. When sessionStorage is unavailable the marker cannot be
 * kept, so no reload is attempted at all -- an unrecoverable page beats an
 * infinite reload loop.
 *
 * @returns whether a reload was triggered.
 */
export const recoverFromStaleChunk = (
  target: string,
  reload: (target: string) => void = defaultReload,
): boolean => {
  try {
    if (sessionStorage.getItem(RELOAD_MARKER_KEY) === target) {
      return false
    }
    sessionStorage.setItem(RELOAD_MARKER_KEY, target)
  } catch {
    return false
  }
  reload(target)
  return true
}

/**
 * Drops the marker after any successful navigation, so a later deploy that
 * strands the same URL again can still be recovered from.
 */
export const clearStaleChunkRecovery = () => {
  try {
    sessionStorage.removeItem(RELOAD_MARKER_KEY)
  } catch {
    // Nothing to clean up if storage never worked.
  }
}
