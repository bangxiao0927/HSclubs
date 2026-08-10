import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import App from './App.vue'

const stubView = { template: '<div />' }

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
  window.localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

afterEach(() => {
  document.documentElement.removeAttribute('data-theme')
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
    expect(localStorage.getItem('theme')).toBe('dark')
    expect(wrapper.find('.mobile-menu').exists()).toBe(true)
  })
})
