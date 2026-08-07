import { describe, expect, it } from 'vitest'

import { resolveErrorMessage } from './httpErrorMessage'

const problemResponse = (body: string, status = 413) =>
  new Response(body, { status, headers: { 'Content-Type': 'application/problem+json' } })

const textResponse = (body: string, status = 400) =>
  new Response(body, { status, headers: { 'Content-Type': 'text/plain' } })

describe('resolveErrorMessage', () => {
  it('extracts detail from a real application/problem+json 413 body', async () => {
    const body = JSON.stringify({
      title: 'Content Too Large',
      status: 413,
      detail: 'The uploaded file is too large. Please choose a smaller file and try again.',
      instance: '/api/clubs/42/posts',
    })

    const message = await resolveErrorMessage(problemResponse(body), 'fallback')

    expect(message).toBe('The uploaded file is too large. Please choose a smaller file and try again.')
  })

  // Every ResponseStatusException this backend's own controllers/services raise (400/403/404/409)
  // is now also application/problem+json (see ApiExceptionHandler); this covers the same
  // extraction for a representative non-413 status.
  it('extracts detail from a real application/problem+json 403 body', async () => {
    const body = JSON.stringify({
      title: 'Forbidden',
      status: 403,
      detail: 'You do not have access to delete this post',
      instance: '/api/clubs/1/posts/9',
    })

    const message = await resolveErrorMessage(problemResponse(body, 403), 'fallback')

    expect(message).toBe('You do not have access to delete this post')
  })

  // Defensive fallback, not a realistic shape of this app's own errors anymore: every
  // ResponseStatusException this app raises is application/problem+json today. This still
  // matters for anything outside that -- a reverse proxy's own error page, or an exception type
  // ApiExceptionHandler does not (and, per its own Javadoc, must not) handle.
  it('returns a plain-text error body verbatim', async () => {
    const message = await resolveErrorMessage(
      textResponse('Service temporarily unavailable'),
      'fallback',
    )

    expect(message).toBe('Service temporarily unavailable')
  })

  it('falls back to the raw body verbatim when the body is malformed JSON', async () => {
    // Declares a JSON content type but the body itself is truncated/invalid -- must not throw,
    // and must not silently swallow the (still useful to a developer) raw text either.
    const malformed = '{"detail": "The uploaded file is too'
    const message = await resolveErrorMessage(problemResponse(malformed), 'fallback')

    expect(message).toBe(malformed)
  })

  it('falls back to the provided fallback when the body is empty', async () => {
    const message = await resolveErrorMessage(textResponse(''), 'Request failed with status 500')

    expect(message).toBe('Request failed with status 500')
  })

  it('falls back to the raw JSON body verbatim when it has no string detail field', async () => {
    const body = JSON.stringify({ title: 'Conflict', status: 409 })

    const message = await resolveErrorMessage(problemResponse(body, 409), 'fallback')

    expect(message).toBe(body)
  })

  it('detects a JSON-shaped body even without a readable Content-Type header', async () => {
    const body = JSON.stringify({ detail: 'No content type on this one' })
    const response = { text: async () => body, headers: undefined } as unknown as Response

    const message = await resolveErrorMessage(response, 'fallback')

    expect(message).toBe('No content type on this one')
  })

  it('preserves a plain-text body verbatim when the mock response has no headers at all', async () => {
    const response = {
      text: async () => 'You do not have access to delete this post',
      headers: undefined,
    } as unknown as Response

    const message = await resolveErrorMessage(response, 'fallback')

    expect(message).toBe('You do not have access to delete this post')
  })
})
