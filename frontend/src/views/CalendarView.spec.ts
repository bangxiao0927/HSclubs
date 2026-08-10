import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchCalendar: vi.fn(),
}))

import { fetchCalendar } from '../services/clubService'
import CalendarView from './CalendarView.vue'

const fetchCalendarMock = vi.mocked(fetchCalendar)

const mountCalendar = async () => {
  const wrapper = mount(CalendarView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  fetchCalendarMock.mockReset()
  fetchCalendarMock.mockResolvedValue([])
})

describe('CalendarView wide-grid scroll wrapper', () => {
  it('wraps the wide weekly grid in an explicit horizontal scroll region', async () => {
    const wrapper = await mountCalendar()

    const scrollRegion = wrapper.find('.calendar-scroll')
    expect(scrollRegion.exists()).toBe(true)
    expect(scrollRegion.find('.weekly-calendar').exists()).toBe(true)
  })

  it('gives screen reader and mobile users an English hint that the grid scrolls horizontally', async () => {
    const wrapper = await mountCalendar()

    const hint = wrapper.find('.calendar-scroll-hint')
    expect(hint.exists()).toBe(true)
    expect(hint.text().toLowerCase()).toContain('scroll')
  })

  it('exposes the scroll region as a focusable, labeled landmark described by the hint', async () => {
    const wrapper = await mountCalendar()

    const scrollRegion = wrapper.find('.calendar-scroll')
    const hint = wrapper.find('.calendar-scroll-hint')

    expect(scrollRegion.attributes('role')).toBe('region')
    expect(scrollRegion.attributes('tabindex')).toBe('0')
    expect(scrollRegion.attributes('aria-label')).toBeTruthy()
    expect(scrollRegion.attributes('aria-label')?.toLowerCase()).toContain('schedule')
    expect(hint.attributes('id')).toBeTruthy()
    expect(scrollRegion.attributes('aria-describedby')).toBe(hint.attributes('id'))
  })
})
