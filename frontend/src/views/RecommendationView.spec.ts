import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(),
    useRouter: vi.fn(),
  }
})

import RecommendationView from './RecommendationView.vue'
import { useRoute, useRouter } from 'vue-router'

vi.mock('../services/clubService', () => ({
  fetchAllClubs: vi.fn().mockResolvedValue([]),
}))

describe('RecommendationView', () => {
  let replaceMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.stubGlobal('scrollTo', vi.fn())
    replaceMock = vi.fn()
    vi.mocked(useRoute).mockReturnValue({ query: {} } as ReturnType<typeof useRoute>)
    vi.mocked(useRouter).mockReturnValue({ replace: replaceMock } as unknown as ReturnType<
      typeof useRouter
    >)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('moves focus to each new question and then to the results heading', async () => {
    const wrapper = mount(RecommendationView, {
      attachTo: document.body,
      global: {
        stubs: { RouterLink: true },
      },
    })
    await flushPromises()

    for (let questionIndex = 0; questionIndex < 4; questionIndex++) {
      await wrapper.find<HTMLInputElement>('.answer-input').setValue(true)
      await wrapper.find<HTMLButtonElement>('.primary-button').trigger('click')
      await flushPromises()

      const expectedHeading =
        questionIndex < 3
          ? wrapper.find<HTMLElement>('#quiz-question').element
          : wrapper.find<HTMLElement>('.results-header h1').element
      expect(document.activeElement).toBe(expectedHeading)
    }

    wrapper.unmount()
  })

  it('continues a first-time user to graduation-year onboarding after the quiz', async () => {
    vi.mocked(useRoute).mockReturnValue({
      query: { onboarding: 'true', redirect: '/clubs/5' },
    } as unknown as ReturnType<typeof useRoute>)
    const wrapper = mount(RecommendationView, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()

    for (let questionIndex = 0; questionIndex < 4; questionIndex++) {
      await wrapper.find<HTMLInputElement>('.answer-input').setValue(true)
      await wrapper.find<HTMLButtonElement>('.primary-button').trigger('click')
      await flushPromises()
    }

    await wrapper.find<HTMLButtonElement>('.onboarding-continue .primary-button').trigger('click')

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/onboarding',
      query: { redirect: '/clubs/5' },
    })
  })

  it('lets a first-time user skip the quiz straight to graduation-year onboarding', async () => {
    vi.mocked(useRoute).mockReturnValue({
      query: { onboarding: 'true', redirect: '/clubs/5' },
    } as unknown as ReturnType<typeof useRoute>)
    const wrapper = mount(RecommendationView, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()

    await wrapper.find<HTMLButtonElement>('.skip-quiz').trigger('click')

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/onboarding',
      query: { redirect: '/clubs/5' },
    })
  })

  it('keeps the onboarding-only affordances out of the standalone quiz', async () => {
    const wrapper = mount(RecommendationView, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()

    expect(wrapper.find('.skip-quiz').exists()).toBe(false)

    for (let questionIndex = 0; questionIndex < 4; questionIndex++) {
      await wrapper.find<HTMLInputElement>('.answer-input').setValue(true)
      await wrapper.find<HTMLButtonElement>('.primary-button').trigger('click')
      await flushPromises()
    }

    expect(wrapper.find('.onboarding-continue').exists()).toBe(false)
  })
})
