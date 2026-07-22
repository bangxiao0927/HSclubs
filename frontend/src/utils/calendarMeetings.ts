export type MeetingPeriod = 'lunch' | 'afterSchool'

export type ParsedMeetingSchedule = {
  days: string[]
  cadence: string
  periods: MeetingPeriod[]
}

const dayTokens: Record<string, string> = {
  mon: 'Mon',
  monday: 'Mon',
  tue: 'Tue',
  tues: 'Tue',
  tuesday: 'Tue',
  wed: 'Wed',
  weds: 'Wed',
  wednesday: 'Wed',
  thu: 'Thu',
  thur: 'Thu',
  thurs: 'Thu',
  thursday: 'Thu',
  fri: 'Fri',
  friday: 'Fri',
  sat: 'Sat',
  saturday: 'Sat',
  sun: 'Sun',
  sunday: 'Sun',
}

export function parseMeetingSchedule(meetingSchedule: string | null): ParsedMeetingSchedule | null {
  if (!meetingSchedule) return null

  const parts = meetingSchedule
    .split('\u00b7')
    .map((part) => part.trim())
    .filter(Boolean)
  const days = extractDays(parts[0] ?? '')
  if (!days.length) return null

  const normalizedSchedule = meetingSchedule.toLowerCase()
  const periods: MeetingPeriod[] = []
  if (/\blunch\b/.test(normalizedSchedule)) periods.push('lunch')
  if (/\bafter[\s-]*school\b/.test(normalizedSchedule)) periods.push('afterSchool')
  if (!periods.length) return null

  return {
    days,
    cadence: parts[1] ?? 'Weekly',
    periods,
  }
}

export function occursOnDate(cadenceLabel: string, date: Date) {
  const cadence = cadenceLabel.toLowerCase()

  if (cadence.includes('biweekly')) return getIsoWeek(date) % 2 === 0
  if (cadence.includes('weekly')) return true
  if (cadence.includes('first and last week of the month')) {
    return isFirstOccurrenceOfWeekday(date) || isLastOccurrenceOfWeekday(date)
  }
  if (cadence.includes('first week of the month')) return isFirstOccurrenceOfWeekday(date)
  if (cadence.includes('last week of the month')) return isLastOccurrenceOfWeekday(date)

  // Without an occurrence week, monthly entries cannot be placed honestly on a weekly calendar.
  if (
    cadence.includes('monthly') ||
    cadence.includes('once a month') ||
    /\b\d+\s+weeks?\s+per\s+month\b/.test(cadence)
  ) {
    return false
  }
  return true
}

function extractDays(value: string) {
  const matches = value
    .toLowerCase()
    .matchAll(
      /\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tues|tue|weds|wed|thurs|thur|thu|fri|sat|sun)(?:s)?\b/g,
    )

  return [
    ...new Set(Array.from(matches, (match) => dayTokens[match[1] as keyof typeof dayTokens])),
  ].filter((day): day is string => Boolean(day))
}

function getIsoWeek(date: Date) {
  const utcDate = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = utcDate.getUTCDay() || 7
  utcDate.setUTCDate(utcDate.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(utcDate.getUTCFullYear(), 0, 1))
  return Math.ceil(((utcDate.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
}

function isFirstOccurrenceOfWeekday(date: Date) {
  return date.getDate() <= 7
}

function isLastOccurrenceOfWeekday(date: Date) {
  const nextWeek = new Date(date)
  nextWeek.setDate(date.getDate() + 7)
  return nextWeek.getMonth() !== date.getMonth()
}
