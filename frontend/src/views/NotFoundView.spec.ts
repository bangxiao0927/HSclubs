import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import NotFoundView from './NotFoundView.vue'

describe('NotFoundView', () => {
  it('regression: no longer links to /schools, a route that was never registered (dead multi-school invitation flow)', () => {
    const wrapper = mount(NotFoundView, { global: { stubs: { RouterLink: true } } })

    const links = wrapper.findAll('a, router-link-stub').map((link) => link.attributes('to'))

    expect(links).not.toContain('/schools')
  })

  it('offers a working way back home', () => {
    const wrapper = mount(NotFoundView, { global: { stubs: { RouterLink: true } } })

    const links = wrapper.findAll('router-link-stub').map((link) => link.attributes('to'))

    expect(links).toContain('/')
  })
})
