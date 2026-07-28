import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  approveMembershipRequest,
  fetchAllClubs,
  fetchClubs,
  invalidateClubCache,
} from './clubService'

const jsonResponse = (body: unknown) => ({
  ok: true,
  text: async () => JSON.stringify(body),
})

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  invalidateClubCache()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  invalidateClubCache()
})

describe('fetchClubs', () => {
  it('sends only the size parameter when no page is given, instead of silently dropping it', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await fetchClubs({ size: 100 })

    const requestedUrl = fetchMock.mock.calls[0]?.[0] as string
    expect(requestedUrl).toContain('size=100')
  })

  it('sends only the page parameter when no size is given', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await fetchClubs({ page: 2 })

    const requestedUrl = fetchMock.mock.calls[0]?.[0] as string
    expect(requestedUrl).toContain('page=2')
  })

  it('issues a separate request for each distinct page/size combination instead of reusing an unrelated cache entry', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse([{ id: 1 }]))
      .mockResolvedValueOnce(jsonResponse([{ id: 2 }]))

    const first = await fetchClubs({ size: 50 })
    const second = await fetchClubs({ size: 100 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(first).toEqual([{ id: 1 }])
    expect(second).toEqual([{ id: 2 }])
  })
})

describe('fetchAllClubs', () => {
  it('keeps paging past the backend single-page cap until every club has been fetched', async () => {
    const firstPage = Array.from({ length: 100 }, (_, index) => ({ id: index + 1 }))
    const secondPage = Array.from({ length: 6 }, (_, index) => ({ id: 101 + index }))
    fetchMock
      .mockResolvedValueOnce(jsonResponse(firstPage))
      .mockResolvedValueOnce(jsonResponse(secondPage))

    const allClubs = await fetchAllClubs()

    expect(allClubs).toHaveLength(106)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

describe('approveMembershipRequest', () => {
  it('invalidates the cached club list so a member-count change is visible on the next fetch', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([{ id: 1, memberCount: 3 }]))
    await fetchAllClubs()

    fetchMock.mockResolvedValueOnce(jsonResponse({}))
    await approveMembershipRequest(1, 42)

    fetchMock.mockResolvedValueOnce(jsonResponse([{ id: 1, memberCount: 4 }]))
    const refreshed = await fetchAllClubs()

    expect(refreshed).toEqual([{ id: 1, memberCount: 4 }])
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })
})
