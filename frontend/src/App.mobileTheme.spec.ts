import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest'

import App from './App.vue'
import { useAuthStore } from './stores/auth'

const stubView = { template: '<div />' }
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

const buildRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: stubView },
      { path: '/about', name: 'about', component: stubView },
      { path: '/calendar', name: 'calendar', component: stubView },
      { path: '/admin', name: 'owner-clubs', component: stubView },
      { path: '/profile', name: 'profile', component: stubView },
      { path: '/auth', name: 'auth-choice', component: stubView },
      { path: '/search', name: 'club-search', component: stubView },
      { path: '/terms', name: 'terms', component: stubView },
      { path: '/privacy', name: 'privacy', component: stubView },
    ],
  })

const mountApp = async () => {
  const router = buildRouter()
  await router.push('/')
  await router.isReady()
  return mount(App, { global: { plugins: [router] } })
}

beforeEach(() => {
  setActivePinia(createPinia())
  installBrowserLocalStorage()
  document.documentElement.removeAttribute('data-theme')
})

afterEach(() => {
  document.documentElement.removeAttribute('data-theme')
})

afterAll(() => {
  if (originalLocalStorage) {
    Object.defineProperty(window, 'localStorage', originalLocalStorage)
  } else {
    Reflect.deleteProperty(window, 'localStorage')
  }
})

describe('mobile menu theme toggle', () => {
  it('offers a theme toggle with English copy inside the mobile menu', async () => {
    const wrapper = await mountApp()
    await wrapper.find('.mobile-menu-toggle').trigger('click')

    const toggle = wrapper.find('.mobile-theme-toggle')
    expect(toggle.exists()).toBe(true)
    expect(toggle.text()).toContain('Dark mode')
  })

  it('switches the document theme when tapped, without closing the mobile menu', async () => {
    const wrapper = await mountApp()
    await wrapper.find('.mobile-menu-toggle').trigger('click')

    await wrapper.find('.mobile-theme-toggle').trigger('click')

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem('theme')).toBe('dark')
    expect(wrapper.find('.mobile-menu').exists()).toBe(true)
  })
})

describe('mobile header user center', () => {
  it('places a user center shortcut after the menu toggle', async () => {
    const wrapper = await mountApp()
    const actions = wrapper.find('.mobile-header-actions')

    expect(actions.find('.mobile-menu-toggle').exists()).toBe(true)
    expect(actions.element.lastElementChild?.classList.contains('mobile-user-center')).toBe(true)
    expect(actions.find('.mobile-user-center').attributes('href')).toBe('/auth?intent=login')
    expect(actions.find('.mobile-user-center').attributes('aria-label')).toBe('User center')
  })

  it('links authenticated users to their profile and shows their initial', async () => {
    const wrapper = await mountApp()
    const authStore = useAuthStore()
    authStore.currentUser = {
      id: 'student-1',
      email: 'alex@example.com',
      displayName: 'Alex',
      avatarUrl: '',
      provider: 'google',
      isOwner: false,
    }
    await wrapper.vm.$nextTick()

    const shortcut = wrapper.find('.mobile-user-center')
    expect(shortcut.attributes('href')).toBe('/profile')
    expect(shortcut.find('.profile-icon').text()).toBe('A')
  })
})
