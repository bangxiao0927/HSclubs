import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSchoolStore } from '../school'

const mockFetch = vi.fn()
globalThis.fetch = mockFetch

function mockResponse(data: unknown, ok = true) {
  return Promise.resolve({
    ok,
    text: () => Promise.resolve(JSON.stringify(data)),
  } as Response)
}

beforeEach(() => {
  setActivePinia(createPinia())
  mockFetch.mockReset()
})

describe('useSchoolStore', () => {
  it('starts with empty schools', () => {
    const store = useSchoolStore()
    expect(store.schools).toEqual([])
    expect(store.currentSchool).toBeNull()
    expect(store.hasLoaded).toBe(false)
  })

  it('loadSchools fetches and populates schools', async () => {
    const schools = [{ id: 1, slug: 'mvhs', schoolName: 'MVHS', status: 'active' }]
    mockFetch.mockResolvedValueOnce(mockResponse(schools))

    const store = useSchoolStore()
    await store.loadSchools()

    expect(store.schools).toEqual(schools)
    expect(store.hasLoaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('loadSchools does not refetch if already loaded', async () => {
    const schools = [{ id: 1, slug: 'mvhs', schoolName: 'MVHS', status: 'active' }]
    mockFetch.mockResolvedValueOnce(mockResponse(schools))

    const store = useSchoolStore()
    await store.loadSchools()
    await store.loadSchools() // second call

    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('setCurrentSchoolBySlug uses cache', async () => {
    const schools = [{ id: 1, slug: 'mvhs', schoolName: 'MVHS', status: 'active' }]
    mockFetch.mockResolvedValueOnce(mockResponse(schools))

    const store = useSchoolStore()
    await store.loadSchools()
    await store.setCurrentSchoolBySlug('mvhs')

    expect(store.currentSchool).toEqual(schools[0])
    expect(store.currentSchoolSlug).toBe('mvhs')
  })

  it('setCurrentSchoolBySlug fetches if not cached', async () => {
    const school = { id: 2, slug: 'pahs', schoolName: 'Palo Alto High', status: 'active' }
    mockFetch.mockResolvedValueOnce(mockResponse(school))

    const store = useSchoolStore()
    await store.setCurrentSchoolBySlug('pahs')

    expect(store.currentSchool).toEqual(school)
    expect(store.currentSchoolSlug).toBe('pahs')
  })

  it('handles error gracefully', async () => {
    mockFetch.mockResolvedValueOnce(mockResponse('Server Error', false))

    const store = useSchoolStore()
    await store.loadSchools()

    expect(store.error).toContain('Server Error')
    expect(store.schools).toEqual([])
  })

  it('currentSchoolSlug is empty when no school selected', () => {
    const store = useSchoolStore()
    expect(store.currentSchoolSlug).toBe('')
  })
})
