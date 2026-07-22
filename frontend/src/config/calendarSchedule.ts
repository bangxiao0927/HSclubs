const DEFAULT_LUNCH_START = 11 * 60 + 30
const DEFAULT_LUNCH_END = 13 * 60 + 30
const DEFAULT_AFTER_SCHOOL_END = 18 * 60

const parseTime = (value: string | undefined, fallback: number) => {
  const match = value?.trim().match(/^(\d{1,2}):(\d{2})$/)
  if (!match) return fallback

  const hours = Number(match[1])
  const minutes = Number(match[2])
  if (hours > 23 || minutes > 59) return fallback
  return hours * 60 + minutes
}

const requestedLunchStart = parseTime(
  import.meta.env.VITE_CALENDAR_LUNCH_START,
  DEFAULT_LUNCH_START,
)
const requestedLunchEnd = parseTime(import.meta.env.VITE_CALENDAR_LUNCH_END, DEFAULT_LUNCH_END)
const requestedAfterSchoolEnd = parseTime(
  import.meta.env.VITE_CALENDAR_AFTER_SCHOOL_END,
  DEFAULT_AFTER_SCHOOL_END,
)
const hasValidSequence =
  requestedLunchStart < requestedLunchEnd && requestedLunchEnd < requestedAfterSchoolEnd

export const calendarSchedule = {
  lunchStart: hasValidSequence ? requestedLunchStart : DEFAULT_LUNCH_START,
  lunchEnd: hasValidSequence ? requestedLunchEnd : DEFAULT_LUNCH_END,
  afterSchoolEnd: hasValidSequence ? requestedAfterSchoolEnd : DEFAULT_AFTER_SCHOOL_END,
}

export const formatScheduleTime = (totalMinutes: number) => {
  const date = new Date(2000, 0, 1, Math.floor(totalMinutes / 60), totalMinutes % 60)
  return new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}
