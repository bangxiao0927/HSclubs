/**
 * Resolves a human-readable error message from a non-2xx fetch {@link Response} body.
 *
 * Every error this app's own controllers/services choose to produce -- a `ResponseStatusException`
 * (400/403/404/409) or Spring's own multipart-size rejection (413) -- is a real
 * `application/problem+json` body carrying an English `detail` field, thanks to
 * `ApiExceptionHandler` on the backend (see docs/API.md's shared error response shape). This
 * extracts that `detail` when present. Anything this handler does not recognize -- a plain-text
 * body, a body that is not JSON-shaped at all, or malformed JSON -- falls back to the raw
 * response body unchanged, so no error this backend might ever produce is silently hidden or
 * altered; an empty body falls back to `fallback`.
 */
export async function resolveErrorMessage(response: Response, fallback: string): Promise<string> {
  const rawBody = await response.text()
  return extractProblemDetail(rawBody, response) ?? (rawBody || fallback)
}

// Content-Type is the authoritative signal for "this body is JSON, worth attempting to parse"
// (application/problem+json for every case ApiExceptionHandler produces, but a bare
// application/json is treated the same in case some other endpoint ever starts returning a
// JSON error body without going through that handler). When a Response has no readable
// Content-Type -- e.g. a minimal test double -- this still attempts a parse for a body that
// looks JSON-shaped, so callers do not have to fabricate headers just to exercise the same code
// path a real fetch() Response would take.
function looksLikeJson(rawBody: string, response: Response): boolean {
  const contentType = typeof response.headers?.get === 'function' ? response.headers.get('content-type') : null
  if (contentType) {
    return contentType.includes('json')
  }
  return rawBody.trim().startsWith('{')
}

function extractProblemDetail(rawBody: string, response: Response): string | undefined {
  if (!looksLikeJson(rawBody, response)) {
    return undefined
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(rawBody)
  } catch {
    // Malformed JSON (or plain text that merely happens to look JSON-shaped): fall back to the
    // raw body, unchanged, rather than surfacing a JSON parse error to the user.
    return undefined
  }

  if (parsed === null || typeof parsed !== 'object') {
    return undefined
  }
  const detail = (parsed as Record<string, unknown>).detail
  return typeof detail === 'string' && detail.trim().length > 0 ? detail : undefined
}
