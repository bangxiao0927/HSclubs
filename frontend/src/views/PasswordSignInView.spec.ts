import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { routerReplace, RouterLinkStub } = vi.hoisted(() => ({
  routerReplace: vi.fn(),
  RouterLinkStub: {
    name: 'RouterLink',
    props: ['to'],
    render(this: { to: unknown; $slots: { default?: () => unknown } }) {
      const children = this.$slots.default?.()
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return h('a', { 'data-to': JSON.stringify(this.to) }, children as any)
    },
  },
}))

vi.mock('vue-router', () => ({
  RouterLink: RouterLinkStub,
  useRoute: vi.fn(() => ({ query: {} })),
  useRouter: vi.fn(() => ({ replace: routerReplace })),
}))

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  loginWithReviewAccount: vi.fn(),
  logout: vi.fn(),
}))

import { useRoute } from 'vue-router'
import { fetchAuthProviders, loginWithReviewAccount } from '../services/authService'
import PasswordSignInView from './PasswordSignInView.vue'

const providersMock = vi.mocked(fetchAuthProviders)
const loginMock = vi.mocked(loginWithReviewAccount)
const routeMock = vi.mocked(useRoute)
const passwordProvider = {
  id: 'internal',
  name: 'Password',
  authorizationUrl: '/api/auth/internal/login',
}

beforeEach(() => {
  setActivePinia(createPinia())
  providersMock.mockReset()
  loginMock.mockReset()
  routerReplace.mockReset()
  routeMock.mockReturnValue({ query: {} } as ReturnType<typeof useRoute>)
})

describe('PasswordSignInView', () => {
  it('renders the credentials form when password sign-in is enabled', async () => {
    providersMock.mockResolvedValue([passwordProvider])
    const wrapper = mount(PasswordSignInView)
    await flushPromises()

    expect(wrapper.find('input[type="email"]').exists()).toBe(true)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Sign in with password')
  })

  it('signs in and continues to the original destination', async () => {
    routeMock.mockReturnValue({ query: { redirect: '/clubs/9' } } as unknown as ReturnType<
      typeof useRoute
    >)
    providersMock.mockResolvedValue([passwordProvider])
    loginMock.mockResolvedValue({
      id: 'internal-review:review@example.edu',
      email: 'review@example.edu',
      displayName: 'App Review',
      avatarUrl: '',
      provider: 'internal',
      isOwner: false,
      graduationYear: 2026,
      acceptedTerms: true,
    })
    const wrapper = mount(PasswordSignInView)
    await flushPromises()

    await wrapper.find('input[type="email"]').setValue('review@example.edu')
    await wrapper.find('input[type="password"]').setValue('secret password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginMock).toHaveBeenCalledWith('review@example.edu', 'secret password')
    expect(routerReplace).toHaveBeenCalledWith('/clubs/9')
  })

  it('does not render a form when the school has not enabled password sign-in', async () => {
    providersMock.mockResolvedValue([
      { id: 'google', name: 'Google', authorizationUrl: '/api/auth/authorize/google' },
    ])
    const wrapper = mount(PasswordSignInView)
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('Password sign-in is not available for this school.')
  })

  it('shows the generic credentials error without leaving the page', async () => {
    providersMock.mockResolvedValue([passwordProvider])
    loginMock.mockRejectedValue(new Error('Invalid email or password.'))
    const wrapper = mount(PasswordSignInView)
    await flushPromises()

    await wrapper.find('input[type="email"]').setValue('review@example.edu')
    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid email or password.')
    expect(routerReplace).not.toHaveBeenCalled()
  })
})
