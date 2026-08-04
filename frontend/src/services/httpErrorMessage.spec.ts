import { describe, expect, it } from 'vitest'

import { resolveErrorMessage } from './httpErrorMessage'

const jsonResponse = (body: string) =>
  new Response(body, { status: 413, headers: { 'Content-Type': 'application/problem+json' } })

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

    const message = await resolveErrorMessage(jsonResponse(body), 'fallback')

    expect(message).toBe('The uploaded file is too large. Please choose a smaller file and try again.')
  })

  it('returns an ordinary plain-text error body verbatim', async () => {
    const message = await resolveErrorMessage(
      textResponse('Title must be 140 characters or fewer'),
      'fallback',
    )

    expect(message).toBe('Title must be 140 characters or fewer')
  })

  it('falls back to the raw body verbatim when the body is malformed JSON', async () => {
    // Declares a JSON content type but the body itself is truncated/invalid -- must not throw,
    // and must not silently swallow the (still useful to a developer) raw text either.
    const malformed = '{"detail": "The uploaded file is too'
    const message = await resolveErrorMessage(jsonResponse(malformed), 'fallback')

    expect(message).toBe(malformed)
  })

  it('falls back to the provided fallback when the body is empty', async () => {
    const message = await resolveErrorMessage(textResponse(''), 'Request failed with status 500')

    expect(message).toBe('Request failed with status 500')
  })

  it('falls back to the raw JSON body verbatim when it has no string detail field', async () => {
    const body = JSON.stringify({ title: 'Conflict', status: 409 })

    const message = await resolveErrorMessage(jsonResponse(body), 'fallback')

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
