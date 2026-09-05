import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest'

import App from './App.vue'
import { schoolTemplate } from './config/schoolTemplate'
import { useAuthStore } from './stores/auth'

const stubView = { template: '<div />' }
const originalLocalStorage = Object.getOwnPropertyDescriptor(window, 'localStorage')
const originalMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia')

const installMatchMedia = (matches: boolean) => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) =>
      ({
        matches,
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList,
  })
}

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

const mountAppWithRouter = async () => {
  const router = buildRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router] } })
  return { router, wrapper }
}

const mountApp = async () => (await mountAppWithRouter()).wrapper

beforeEach(() => {
  setActivePinia(createPinia())
  installBrowserLocalStorage()
  document.documentElement.removeAttribute('data-theme')
})

afterEach(() => {
  document.documentElement.removeAttribute('data-theme')
  if (originalMatchMedia) {
    Object.defineProperty(window, 'matchMedia', originalMatchMedia)
  } else {
    Reflect.deleteProperty(window, 'matchMedia')
  }
})

afterAll(() => {
  if (originalLocalStorage) {
    Object.defineProperty(window, 'localStorage', originalLocalStorage)
  } else {
    Reflect.deleteProperty(window, 'localStorage')
  }
})

describe('user center sheet theme toggle', () => {
  it('offers a theme toggle with English copy inside the user center sheet', async () => {
    const wrapper = await mountApp()
    await wrapper.find('.mobile-user-center').trigger('click')

    const toggle = wrapper.find('.mobile-theme-toggle')
    expect(toggle.exists()).toBe(true)
    expect(toggle.text()).toContain('Dark mode')
  })

  it('switches the document theme when tapped, without closing the mobile menu', async () => {
    const wrapper = await mountApp()
    await wrapper.find('.mobile-user-center').trigger('click')

    await wrapper.find('.mobile-theme-toggle').trigger('click')

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem('theme')).toBe('dark')
    expect(wrapper.find('.mobile-menu').exists()).toBe(true)
  })
})

describe('mobile navigation', () => {
  it('places primary navigation and a signed-out user center in the bottom bar', async () => {
    const wrapper = await mountApp()
    const tabs = wrapper.findAll('.mobile-tab-bar .mobile-tab')

    expect(tabs.map((tab) => tab.text())).toEqual(['Home', 'Category', 'Calendar', 'Sign in'])
    expect(tabs.slice(0, 3).map((tab) => tab.attributes('href'))).toEqual([
      '/',
      '/about',
      '/calendar',
    ])
    expect(tabs[3]?.classes()).toContain('mobile-user-center')
    expect(tabs[3]?.attributes('aria-label')).toBeUndefined()
  })

  it('keeps the search bar in the title bar rather than the user center sheet', async () => {
    const wrapper = await mountApp()
    await wrapper.find('.mobile-user-center').trigger('click')

    expect(wrapper.findAll('.header .search-bar')).toHaveLength(1)
    expect(wrapper.find('.mobile-menu .search-bar').exists()).toBe(false)
  })

  it('renders the open user center after its trigger and closes it with Escape', async () => {
    const wrapper = await mountApp()
    const trigger = wrapper.find('.mobile-user-center')
    await trigger.trigger('click')

    const sheet = wrapper.find('.mobile-menu')
    expect(
      trigger.element.compareDocumentPosition(sheet.element) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).not.toBe(0)

    await sheet.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('.mobile-menu').exists()).toBe(false)
  })

  it('shortens the search placeholder on a compact viewport so the title row fits', async () => {
    installMatchMedia(true)
    const wrapper = await mountApp()

    expect(wrapper.find('.header .search-input').attributes('placeholder')).toBe('Search clubs')
  })

  // The school's short name is the title bar's identity: the compact viewport shortens the
  // search placeholder instead of dropping or abbreviating the brand beside it.
  it('keeps the school short name in the title bar on a compact viewport', async () => {
    installMatchMedia(true)
    const wrapper = await mountApp()

    expect(wrapper.find('.header .logo-text').text()).toBe(schoolTemplate.shortName)
  })

  it('opens a user center sheet with profile access for an authenticated student', async () => {
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
    expect(shortcut.text()).toContain('Account')
    expect(shortcut.find('.profile-icon').text()).toBe('A')

    await shortcut.trigger('click')
    const sheet = wrapper.find('.mobile-menu')
    expect(sheet.find('.mobile-menu-title').text()).toBe('Alex')
    expect(sheet.find('.mobile-nav-link').attributes('href')).toBe('/profile')
  })
})

describe('header search', () => {
  it('trims the query and opens the search results route', async () => {
    const { router, wrapper } = await mountAppWithRouter()
    await wrapper.find('.header .search-input').setValue('  chess club  ')
    await wrapper.find('.header .search-bar').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('club-search')
    expect(router.currentRoute.value.query.q).toBe('chess club')
  })

  it('keeps results while editing but clears them when the native clear control is used', async () => {
    const { router, wrapper } = await mountAppWithRouter()
    await wrapper.find('.header .search-input').setValue('chess')
    await wrapper.find('.header .search-bar').trigger('submit')
    await flushPromises()

    await wrapper.find('.header .search-input').setValue('')
    expect(router.currentRoute.value.name).toBe('club-search')

    await wrapper.find('.header .search-input').trigger('search')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('home')
    expect(router.currentRoute.value.query).toEqual({})
  })
})
