import { describe, it, expect, beforeEach, beforeAll, afterAll, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { server } from '../mocks/server'
import { useSchoolStore } from '../../stores/school'
import { useAuthStore } from '../../stores/auth'

beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('School Discovery Flow', () => {
  it('loads all active schools', async () => {
    const store = useSchoolStore()
    await store.loadSchools()
    expect(store.schools).toHaveLength(2)
    expect(store.schools[0]!.slug).toBe('mvhs')
    expect(store.schools[1]!.slug).toBe('pahs')
  })

  it('fetches school by slug', async () => {
    const store = useSchoolStore()
    await store.setCurrentSchoolBySlug('mvhs')
    expect(store.currentSchool?.slug).toBe('mvhs')
    expect(store.currentSchool?.schoolName).toBe('Mountain View High School')
  })

  it('authenticated user has school memberships', async () => {
    const authStore = useAuthStore()
    await authStore.refreshUser()

    expect(authStore.isAuthenticated).toBe(true)
    expect(authStore.currentUser?.email).toBe('student@mvhs.edu')
    expect(authStore.currentUser?.schoolMemberships).toHaveLength(1)
    expect(authStore.currentUser?.schoolMemberships?.[0]!.slug).toBe('mvhs')
  })

  it('user can sign out', async () => {
    const authStore = useAuthStore()
    await authStore.refreshUser()
    expect(authStore.isAuthenticated).toBe(true)

    await authStore.logout()
    expect(authStore.currentUser).toBeNull()
    expect(authStore.isAuthenticated).toBe(false)
  })
})
