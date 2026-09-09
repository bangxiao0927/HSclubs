import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PrivacyPolicyView from './PrivacyPolicyView.vue'

describe('PrivacyPolicyView', () => {
  it('explicitly discloses the advertising and cross-app tracking practices', () => {
    const wrapper = mount(PrivacyPolicyView)

    expect(wrapper.text()).toContain('We do not use account information')
    expect(wrapper.text()).toContain('We do not show third-party advertising')
    expect(wrapper.text()).toContain("track users across other companies' apps or websites")
  })
})
