import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { RouterView } from 'vue-router'

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn().mockResolvedValue([]),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import { fetchAuthenticatedUser } from '../services/authService'
import type { AuthUser } from '../types/auth'
import router from './index'

const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)

// The server-side state of a brand-new student, mutated by the stubbed
// endpoints exactly as the real backend would: accepting the terms stamps
// acceptedTerms, saving the graduation year fills graduationYear. Every
// /api/auth/me read returns a snapshot of this, so the views only ever see
// what a real round trip would have told them.
let storedUser: AuthUser

const buildNewStudent = (): AuthUser => ({
  id: 'user-1',
  email: 'new-student@example.com',
  displayName: 'New Student',
  avatarUrl: '',
  provider: 'google',
  isOwner: false,
  graduationYear: null,
  acceptedTerms: false,
})

const jsonResponse = (body: unknown) =>
  ({
    ok: true,
    status: 200,
    json: async () => body,
    text: async () => JSON.stringify(body),
  }) as unknown as Response

const noContentResponse = () =>
  ({ ok: true, status: 204, json: async () => null, text: async () => '' }) as unknown as Response

// Renders whatever the real router resolves, so every navigation goes through
// the real guard and the real (lazily imported) view components.
const mountApp = () => mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })

// A navigation, the lazy import of its view component, and that component's
// own onMounted requests settle over an unpredictable number of turns (the
// first import of a chunk is the slowest), so wait for the outcome rather than
// guessing a fixed number of ticks.
const settle = async () => {
  for (let i = 0; i < 5; i++) {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const waitForRoute = async (fullPath: string) => {
  await vi.waitUntil(() => router.currentRoute.value.fullPath === fullPath, {
    timeout: 3000,
    interval: 10,
  })
  await settle()
}

beforeEach(async () => {
  setActivePinia(createPinia())
  storedUser = buildNewStudent()
  fetchAuthenticatedUserMock.mockReset()
  fetchAuthenticatedUserMock.mockImplementation(async () => ({ ...storedUser }))
  vi.stubGlobal('scrollTo', vi.fn())
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/auth/accept-terms')) {
        storedUser = { ...storedUser, acceptedTerms: true }
        return noContentResponse()
      }
      if (url.includes('/api/users/me/graduation-year')) {
        storedUser = { ...storedUser, graduationYear: 2027 }
        return noContentResponse()
      }
      if (url.includes('/api/clubs')) {
        return jsonResponse([])
      }
      if (url.includes('/api/users/me')) {
        return jsonResponse([])
      }
      return jsonResponse(null)
    }),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})

describe('first-time registration flow, driven through the real router', () => {
  it('walks a brand-new student from the terms page to the interest quiz and on to the graduation-year step', async () => {
    const wrapper = mountApp()
    await router.push('/accept-terms?redirect=/profile')
    await settle()

    expect(wrapper.text()).toContain('Before you continue')

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit')
    await settle()

    // The click has to actually move the browser off the terms page: the bug
    // being guarded against here left the URL untouched with no error shown.
    await waitForRoute('/recommendations?onboarding=true&redirect=/profile')
    expect(wrapper.text()).toContain('Find clubs that fit you')

    const skipButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Skip for now')
    expect(skipButton).toBeDefined()
    await skipButton!.trigger('click')
    await settle()

    await waitForRoute('/onboarding?redirect=/profile')
    expect(wrapper.text()).toContain('Graduation year')

    const select = wrapper.find('select')
    await select.setValue(String(new Date().getFullYear() + 1))
    await wrapper.find('form').trigger('submit')
    await settle()

    await waitForRoute('/profile')
  })

  it('answers the whole quiz and reaches the graduation-year step from the results screen', async () => {
    const wrapper = mountApp()
    await router.push('/accept-terms?redirect=/clubs/5')
    await settle()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit')
    await waitForRoute('/recommendations?onboarding=true&redirect=/clubs/5')

    // Four questions: pick the first option each time, then advance.
    for (let question = 0; question < 4; question++) {
      const firstOption = wrapper.findAll('input[type="radio"]')[0]
      expect(firstOption, `no options on question ${question + 1}`).toBeDefined()
      await firstOption!.setValue()
      const advance = wrapper
        .findAll('button')
        .find((button) => ['Next question', 'See my matches'].includes(button.text()))
      expect(advance, `no advance button on question ${question + 1}`).toBeDefined()
      expect(advance!.attributes('disabled')).toBeUndefined()
      await advance!.trigger('click')
      await settle()
    }

    const continueButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Continue profile setup')
    expect(continueButton).toBeDefined()
    await continueButton!.trigger('click')
    await settle()

    await waitForRoute('/onboarding?redirect=/clubs/5')
  })

  it('keeps the student on the terms page with a visible error when the server records nothing', async () => {
    // Emulates the production dead end: the POST answers 204 but /api/auth/me
    // still reports acceptedTerms=false.
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/api/auth/accept-terms')) {
          return noContentResponse()
        }
        return jsonResponse([])
      }),
    )

    const wrapper = mountApp()
    await router.push('/accept-terms?redirect=/profile')
    await settle()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit')
    await settle()

    expect(router.currentRoute.value.fullPath).toBe('/accept-terms?redirect=/profile')
    expect(wrapper.text()).toContain('We could not confirm your acceptance')
  })
})
