import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchCalendar: vi.fn(),
}))

import { fetchCalendar, type CalendarEvent } from '../services/clubService'
import CalendarView from './CalendarView.vue'

const fetchCalendarMock = vi.mocked(fetchCalendar)

const mountCalendar = async () => {
  // A slot-rendering stub, not `true`: the day view's event links carry the club name and
  // location in their default slot, which the auto-stub would swallow.
  const wrapper = mount(CalendarView, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
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

// Phones get a single-day view instead of the seven-column week grid, which only fits behind a
// horizontal scroll at that width. Both are always in the DOM; CSS picks one per viewport.
describe('CalendarView mobile day view', () => {
  const buildEvent = (overrides: Partial<CalendarEvent> = {}): CalendarEvent =>
    ({
      clubId: 1,
      clubName: 'Chess Club',
      meetingSchedule: 'Meets Monday at lunch',
      location: 'Room 12',
      imageUrl: null,
      instagramUrl: null,
      createdAt: null,
      updatedAt: null,
      ...overrides,
    }) as CalendarEvent

  it('offers one chip per day, with the current day selected on arrival', async () => {
    const wrapper = await mountCalendar()

    const chips = wrapper.findAll('.day-chip')
    expect(chips).toHaveLength(7)

    const todayShortLabel = new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(new Date())
    const selected = wrapper.findAll('.day-chip.selected')
    expect(selected).toHaveLength(1)
    expect(selected[0]!.text()).toContain(todayShortLabel)
  })

  it('lists the selected day\u2019s meetings by period, with the club name and location', async () => {
    fetchCalendarMock.mockResolvedValue([buildEvent({ clubName: 'Chess Club', location: 'Room 12' })])

    const wrapper = await mountCalendar()

    const monday = wrapper.findAll('.day-chip')[0]!
    await monday.trigger('click')

    expect(monday.classes()).toContain('selected')
    const dayView = wrapper.find('.calendar-day-view')
    expect(dayView.text()).toContain('Chess Club')
    expect(dayView.text()).toContain('Room 12')
    expect(dayView.text()).toContain('Lunch')
    expect(dayView.text()).toContain('After School')
  })

  it('says so when the selected day has no meetings', async () => {
    const wrapper = await mountCalendar()

    await wrapper.findAll('.day-chip')[0]!.trigger('click')

    expect(wrapper.find('.calendar-day-view').text()).toContain('No meetings')
    expect(wrapper.find('.day-summary-count').text()).toBe('0 meetings')
  })
})
