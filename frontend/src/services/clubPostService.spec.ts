import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createClubPostComment,
  deleteClubPost,
  deleteClubPostComment,
  pinClubPost,
  publishClubPost,
  unpinClubPost,
} from './clubPostService'

const jsonResponse = (body: unknown) => ({
  ok: true,
  text: async () => JSON.stringify(body),
  json: async () => body,
})

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('publishClubPost', () => {
  it('sends a POST with FormData carrying the title and file, credentials included, and no explicit Content-Type', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 9, title: 'Meeting recap' }))
    const file = new File(['image'], 'photo.jpg', { type: 'image/jpeg' })

    await publishClubPost('1', 'Meeting recap', file)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.body).toBeInstanceOf(FormData)
    const body = init.body as FormData
    expect(body.get('title')).toBe('Meeting recap')
    expect(body.get('file')).toBe(file)
    // The browser must supply its own multipart boundary; an explicit Content-Type here would
    // corrupt it (see clubPostService.ts's own comment on why this cannot go through request()).
    expect(init.headers).toBeUndefined()
  })

  it('resolves with the created post parsed from the response body', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 9, title: 'Meeting recap' }))

    const created = await publishClubPost('1', 'Meeting recap', new File(['image'], 'photo.jpg'))

    expect(created).toEqual({ id: 9, title: 'Meeting recap' })
  })

  it('rejects with the server error message verbatim on a non-2xx response', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 400,
      text: async () => 'Title must be 140 characters or fewer',
    })

    await expect(publishClubPost('1', 'x'.repeat(141), new File(['image'], 'photo.jpg'))).rejects.toThrow(
      'Title must be 140 characters or fewer',
    )
  })

  // Spring's own multipart-size rejection (see MultipartUploadExceptionHandler and
  // docs/API.md's 413 row): an application/problem+json body, not plain text -- the one case
  // resolveErrorMessage exists to translate into a readable message instead of raw JSON.
  it('rejects with the ProblemDetail detail message on a 413 application/problem+json response', async () => {
    const problemBody = JSON.stringify({
      title: 'Content Too Large',
      status: 413,
      detail: 'The uploaded file is too large. Please choose a smaller file and try again.',
      instance: '/api/clubs/1/posts',
    })
    fetchMock.mockResolvedValue({
      ok: false,
      status: 413,
      headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/problem+json' : null) },
      text: async () => problemBody,
    })

    await expect(
      publishClubPost('1', 'Meeting recap', new File(['image'], 'huge.jpg')),
    ).rejects.toThrow('The uploaded file is too large. Please choose a smaller file and try again.')
  })

  it('rejects with the raw body verbatim when a JSON-declared error body is malformed', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 413,
      headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/problem+json' : null) },
      text: async () => '{"detail": "cut off mid-strea',
    })

    await expect(
      publishClubPost('1', 'Meeting recap', new File(['image'], 'huge.jpg')),
    ).rejects.toThrow('{"detail": "cut off mid-strea')
  })

  it('rejects with a status-based fallback message when the error body is empty', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      text: async () => '',
    })

    await expect(
      publishClubPost('1', 'Meeting recap', new File(['image'], 'photo.jpg')),
    ).rejects.toThrow('Request failed with status 500')
  })
})

describe('deleteClubPost', () => {
  it('sends a DELETE to the post resource with credentials included', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => '' })

    await deleteClubPost('1', 9)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts/9')
    expect(init.method).toBe('DELETE')
    expect(init.credentials).toBe('include')
  })

  it('rejects with the server error message verbatim on a 403', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 403,
      text: async () => 'You do not have access to delete this post',
    })

    await expect(deleteClubPost('1', 9)).rejects.toThrow('You do not have access to delete this post')
  })
})

describe('pinClubPost and unpinClubPost', () => {
  it('sends a PUT to pin', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => '' })

    await pinClubPost('1', 9)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts/9/pin')
    expect(init.method).toBe('PUT')
  })

  it('sends a DELETE to unpin', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => '' })

    await unpinClubPost('1', 9)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts/9/pin')
    expect(init.method).toBe('DELETE')
  })

  it('rejects with the pin cap message verbatim on a 409', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      text: async () => 'At most 3 posts can be pinned. Unpin one first.',
    })

    await expect(pinClubPost('1', 9)).rejects.toThrow('At most 3 posts can be pinned. Unpin one first.')
  })
})

describe('createClubPostComment', () => {
  it('sends a POST with a JSON body containing the comment text', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 5, body: 'Nice!' }))

    await createClubPostComment('1', 9, 'Nice!')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts/9/comments')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ body: 'Nice!' })
  })

  it('rejects with the comment cap message verbatim on a 409', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      text: async () => 'This post already has the maximum number of comments',
    })

    await expect(createClubPostComment('1', 9, 'Nice!')).rejects.toThrow(
      'This post already has the maximum number of comments',
    )
  })
})

describe('deleteClubPostComment', () => {
  it('sends a DELETE to the comment resource', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => '' })

    await deleteClubPostComment('1', 9, 5)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/clubs/1/posts/9/comments/5')
    expect(init.method).toBe('DELETE')
  })
})
