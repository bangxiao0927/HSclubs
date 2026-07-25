import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import RecommendationView from './RecommendationView.vue'

vi.mock('../services/clubService', () => ({
  fetchAllClubs: vi.fn().mockResolvedValue([]),
}))

describe('RecommendationView', () => {
  beforeEach(() => {
    vi.stubGlobal('scrollTo', vi.fn())
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
})
