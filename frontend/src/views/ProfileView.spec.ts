import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/userService', () => ({
  updateGraduationYear: vi.fn(),
  fetchMyClubs: vi.fn(),
  fetchMyMembershipRequests: vi.fn(),
}))

import { fetchMyClubs, fetchMyMembershipRequests } from '../services/userService'
import { useAuthStore } from '../stores/auth'
import ProfileView from './ProfileView.vue'

const fetchMyClubsMock = vi.mocked(fetchMyClubs)
const fetchMyMembershipRequestsMock = vi.mocked(fetchMyMembershipRequests)

const originalLocalStorage = Object.getOwnPropertyDescriptor(window, 'localStorage')

const installBrowserLocalStorage = () => {
  const values = new Map<string, string>()
  const storage: Storage = {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => values.delete(key),
    setItem: (key, value) => values.set(key, String(value)),
  }
  Object.defineProperty(window, 'localStorage', { configurable: true, value: storage })
}

const mountProfile = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/profile', name: 'profile', component: ProfileView },
      { path: '/admin', name: 'owner-clubs', component: { template: '<div />' } },
      { path: '/auth', name: 'auth-choice', component: { template: '<div />' } },
    ],
  })
  await router.push('/profile')
  await router.isReady()

  const authStore = useAuthStore()
  authStore.currentUser = {
    id: 'student-1',
    email: 'alex@example.com',
    displayName: 'Alex',
    avatarUrl: '',
    provider: 'google',
    isOwner: false,
  }

  const wrapper = mount(ProfileView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  installBrowserLocalStorage()
  document.documentElement.removeAttribute('data-theme')
  fetchMyClubsMock.mockReset()
  fetchMyMembershipRequestsMock.mockReset()
  fetchMyClubsMock.mockResolvedValue([])
  fetchMyMembershipRequestsMock.mockResolvedValue([])
})

afterAll(() => {
  if (originalLocalStorage) {
    Object.defineProperty(window, 'localStorage', originalLocalStorage)
  } else {
    Reflect.deleteProperty(window, 'localStorage')
  }
  document.documentElement.removeAttribute('data-theme')
})

describe('ProfileView appearance control', () => {
  it('offers the theme toggle directly above sign out', async () => {
    const wrapper = await mountProfile()

    const actions = wrapper.findAll('.account-actions button')
    expect(actions.map((button) => button.text())).toEqual(['🌙Dark mode', 'Sign out'])
  })

  it('switches and stores the theme when tapped', async () => {
    const wrapper = await mountProfile()

    await wrapper.find('.account-actions .theme-toggle').trigger('click')

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem('theme')).toBe('dark')
    expect(wrapper.find('.account-actions .theme-toggle').text()).toContain('Light mode')
  })
})
