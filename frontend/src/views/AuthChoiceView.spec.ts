import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// AuthChoiceView imports RouterLink directly from 'vue-router' (rather than
// relying on the router's auto-registered global component), so the mocked
// module needs to provide a working stand-in: a plain anchor rendering its
// `to` prop as `href`, which is enough to assert on the legal links below.
const { RouterLinkStub } = vi.hoisted(() => {
  return {
    RouterLinkStub: {
      name: 'RouterLink',
      props: ['to'],
      render(this: { to: unknown; $slots: { default?: () => unknown } }) {
        const href = typeof this.to === 'string' ? this.to : JSON.stringify(this.to)
        const children = this.$slots.default?.()
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        return h('a', { href }, children as any)
      },
    },
  }
})

vi.mock('vue-router', () => ({
  RouterLink: RouterLinkStub,
  useRoute: vi.fn(),
  useRouter: vi.fn(() => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() })),
}))

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import { useRoute } from 'vue-router'

import { fetchAuthProviders } from '../services/authService'
import type { AuthProvider } from '../types/auth'
import { useAuthStore } from '../stores/auth'
import AuthChoiceView from './AuthChoiceView.vue'

const useRouteMock = vi.mocked(useRoute)
const fetchAuthProvidersMock = vi.mocked(fetchAuthProviders)

const googleProvider: AuthProvider = {
  id: 'google',
  name: 'Google',
  authorizationUrl: '/oauth2/authorization/google',
}
const microsoftProvider: AuthProvider = {
  id: 'microsoft',
  name: 'Microsoft',
  authorizationUrl: '/oauth2/authorization/microsoft',
}

const setRouteQuery = (query: Record<string, string>) => {
  useRouteMock.mockReturnValue({ query } as ReturnType<typeof useRoute>)
}

// A deferred promise gives a test control over exactly when the providers
// fetch resolves, so the loading state can be observed before it settles.
const createDeferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchAuthProvidersMock.mockReset()
  setRouteQuery({})
})

describe('AuthChoiceView', () => {
  it('renders one enabled provider button per configured provider, with no checkbox gating them', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider, microsoftProvider])
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)

    const buttons = wrapper.findAll('button.provider-btn')
    expect(buttons).toHaveLength(2)
    expect(wrapper.text()).toContain('Sign in with Google')
    expect(wrapper.text()).toContain('Sign in with Microsoft')
    buttons.forEach((button) => {
      expect(button.attributes('disabled')).toBeUndefined()
    })
  })

  it('renders inline legal copy linking to both the Terms of Use and the Privacy Policy', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    const links = wrapper.findAll('a')
    const termsLink = links.find((link) => link.text() === 'Terms of Use')
    const privacyLink = links.find((link) => link.text() === 'Privacy Policy')

    expect(termsLink?.attributes('href')).toBe('/terms')
    expect(privacyLink?.attributes('href')).toBe('/privacy')
  })

  it('starts login with the clicked provider and the redirect target carried in the route query', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    setRouteQuery({ redirect: '/clubs/9' })
    const store = useAuthStore()
    const beginLoginSpy = vi.spyOn(store, 'beginLogin').mockImplementation(() => {})
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    await wrapper.find('button.provider-btn').trigger('click')

    expect(beginLoginSpy).toHaveBeenCalledWith('google', '/clubs/9')
  })

  it('starts login with no redirect target when the route query carries none', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    const store = useAuthStore()
    const beginLoginSpy = vi.spyOn(store, 'beginLogin').mockImplementation(() => {})
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    await wrapper.find('button.provider-btn').trigger('click')

    expect(beginLoginSpy).toHaveBeenCalledWith('google', null)
  })

  it('shows the route-level error banner when the URL carries an error param', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    setRouteQuery({ error: 'oauth2_login_failed' })
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('We could not complete your sign in. Please try again.')
  })

  // A student turned away by the school's sign-in restriction is told why: "try again" is
  // wrong advice for an account that can never be accepted.
  it('explains a rejected email domain instead of inviting a retry', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    setRouteQuery({ error: 'email_domain_not_allowed' })
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('Please use your school account')
    expect(wrapper.text()).not.toContain('Please try again')
  })

  it('explains an unverified email address', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    setRouteQuery({ error: 'email_not_verified' })
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('not verified')
  })

  it('falls back to the generic message for an unknown error code', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    setRouteQuery({ error: 'something_new' })
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('We could not complete your sign in. Please try again.')
  })

  it('shows a providers-error banner when loading the provider list fails', async () => {
    fetchAuthProvidersMock.mockRejectedValue(new Error('Providers unavailable'))
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('Providers unavailable')
    expect(wrapper.find('button.provider-btn').exists()).toBe(false)
  })

  it('shows the route error and the providers error banners together, since they are independent conditions', async () => {
    fetchAuthProvidersMock.mockRejectedValue(new Error('Providers unavailable'))
    setRouteQuery({ error: 'oauth2_login_failed' })
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('We could not complete your sign in. Please try again.')
    expect(wrapper.text()).toContain('Providers unavailable')
  })

  it('shows a loading message while providers are being fetched, and hides it once they arrive', async () => {
    const deferred = createDeferred<AuthProvider[]>()
    fetchAuthProvidersMock.mockReturnValue(deferred.promise)
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('Loading sign-in options…')
    expect(wrapper.find('button.provider-btn').exists()).toBe(false)

    deferred.resolve([googleProvider])
    await flushPromises()

    expect(wrapper.text()).not.toContain('Loading sign-in options…')
    expect(wrapper.text()).toContain('Sign in with Google')
  })

  it('shows a "no providers configured" message when the provider list loads but is empty', async () => {
    fetchAuthProvidersMock.mockResolvedValue([])
    const wrapper = mount(AuthChoiceView)
    await flushPromises()

    expect(wrapper.text()).toContain('No OAuth providers are configured yet.')
    expect(wrapper.find('button.provider-btn').exists()).toBe(false)
  })
})
