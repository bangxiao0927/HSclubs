import { describe, expect, it } from 'vitest'

import { occursOnDate, parseMeetingSchedule } from './calendarMeetings'

describe('parseMeetingSchedule', () => {
  it('recognizes after-school periods in free-form schedules', () => {
    expect(parseMeetingSchedule('Tuesdays after school')).toEqual({
      days: ['Tue'],
      cadence: 'Weekly',
      periods: ['afterSchool'],
    })
  })

  it('recognizes lunch periods in free-form schedules', () => {
    expect(parseMeetingSchedule('Thursdays at lunch')?.periods).toEqual(['lunch'])
  })

  it('supports schedules containing both displayed periods', () => {
    expect(parseMeetingSchedule('Tuesday · Weekly · Lunch, Afterschool')?.periods).toEqual([
      'lunch',
      'afterSchool',
    ])
  })

  it('skips periods the weekly calendar does not display', () => {
    expect(parseMeetingSchedule('Monday · Weekly · Before School')).toBeNull()
  })
})

describe('occursOnDate', () => {
  const meetingDate = new Date(2026, 6, 7)

  it.each(['Monthly', 'Once a month', 'Once a week, 3 weeks per month'])(
    'does not expand %s entries into every week',
    (cadence) => {
      expect(occursOnDate(cadence, meetingDate)).toBe(false)
    },
  )

  it('continues to display weekly entries', () => {
    expect(occursOnDate('Weekly', meetingDate)).toBe(true)
  })
})
