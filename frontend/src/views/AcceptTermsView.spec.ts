import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
}))

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import { useRoute, useRouter } from 'vue-router'

import { fetchAuthenticatedUser } from '../services/authService'
import type { AuthUser } from '../types/auth'
import AcceptTermsView from './AcceptTermsView.vue'

const useRouteMock = vi.mocked(useRoute)
const useRouterMock = vi.mocked(useRouter)
const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)

// The user record `refreshUser()` returns *after* the accept-terms POST has
// succeeded server-side; acceptedTerms is already true at that point since
// the backend just recorded it.
const buildUser = (overrides: Partial<AuthUser> = {}): AuthUser => ({
  id: 'user-1',
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  avatarUrl: '',
  provider: 'google',
  isOwner: false,
  graduationYear: 2027,
  acceptedTerms: true,
  ...overrides,
})

let replaceMock: ReturnType<typeof vi.fn>
let fetchMock: ReturnType<typeof vi.fn>

const setRouteQuery = (query: Record<string, string>) => {
  useRouteMock.mockReturnValue({ query } as ReturnType<typeof useRoute>)
}

const mountView = () => mount(AcceptTermsView, { global: { stubs: { RouterLink: true } } })

// Checks the consent checkbox and submits the form, mirroring what a real
// user has to do before the accept button does anything.
const agreeAndSubmit = async (wrapper: ReturnType<typeof mountView>) => {
  await wrapper.find('input[type="checkbox"]').setValue(true)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchAuthenticatedUserMock.mockReset()
  replaceMock = vi.fn()
  useRouterMock.mockReturnValue({ replace: replaceMock } as unknown as ReturnType<typeof useRouter>)
  setRouteQuery({})
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AcceptTermsView', () => {
  it('sends a user with no graduation year to onboarding (not the final destination) after accepting terms', async () => {
    fetchMock.mockResolvedValue({ ok: true })
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser({ graduationYear: null }))
    setRouteQuery({ redirect: '/clubs/5' })
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/onboarding',
      query: { redirect: '/clubs/5' },
    })
  })

  it('sends a user who already has a graduation year on to the sanitized target', async () => {
    fetchMock.mockResolvedValue({ ok: true })
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser({ graduationYear: 2027 }))
    setRouteQuery({ redirect: '/clubs/5' })
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(replaceMock).toHaveBeenCalledWith('/clubs/5')
  })

  it('lands a user with no redirect target at all on the default profile page', async () => {
    fetchMock.mockResolvedValue({ ok: true })
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(replaceMock).toHaveBeenCalledWith('/profile')
  })

  it('does nothing until the agreement checkbox is checked: the submit button is disabled, and even a forced submit is a no-op', async () => {
    const wrapper = mountView()

    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    // Guards against a forced/programmatic submit too, not just the disabled
    // button: the checkbox is the real gate, not just a UI affordance.
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('enables the submit button and lets it submit once the agreement checkbox is checked', async () => {
    fetchMock.mockResolvedValue({ ok: true })
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('surfaces an error and does not navigate when the accept-terms request comes back non-ok', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 500 })
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(wrapper.text()).toContain('Failed to record acceptance')
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('surfaces an error and does not navigate when the accept-terms request itself rejects', async () => {
    fetchMock.mockRejectedValue(new Error('network down'))
    const wrapper = mountView()

    await agreeAndSubmit(wrapper)

    expect(wrapper.text()).toContain('network down')
    expect(replaceMock).not.toHaveBeenCalled()
  })
})
