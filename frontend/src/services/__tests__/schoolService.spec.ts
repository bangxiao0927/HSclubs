import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchSchools, fetchSchoolBySlug } from '../schoolService'

const mockFetch = vi.fn()
globalThis.fetch = mockFetch

function mockResponse(data: unknown, ok = true) {
  return Promise.resolve({
    ok,
    text: () => Promise.resolve(JSON.stringify(data)),
  } as Response)
}

beforeEach(() => {
  mockFetch.mockReset()
})

describe('fetchSchools', () => {
  it('returns parsed school list', async () => {
    const schools = [{ id: 1, slug: 'mvhs', schoolName: 'MVHS', status: 'active' }]
    mockFetch.mockResolvedValueOnce(mockResponse(schools))

    const result = await fetchSchools()
    expect(result).toEqual(schools)
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('throws on non-ok response', async () => {
    mockFetch.mockResolvedValueOnce(mockResponse('Not Found', false))
    await expect(fetchSchools()).rejects.toThrow('Not Found')
  })
})

describe('fetchSchoolBySlug', () => {
  it('returns school by slug', async () => {
    const school = { id: 1, slug: 'mvhs', schoolName: 'MVHS', status: 'active' }
    mockFetch.mockResolvedValueOnce(mockResponse(school))

    const result = await fetchSchoolBySlug('mvhs')
    expect(result).toEqual(school)
  })
})
