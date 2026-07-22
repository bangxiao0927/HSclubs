<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { calendarSchedule, formatScheduleTime } from '../config/calendarSchedule'
import { fetchCalendar, type CalendarEvent } from '../services/clubService'
import { occursOnDate, parseMeetingSchedule, type MeetingPeriod } from '../utils/calendarMeetings'
import { clubImage } from '../utils/clubImages'

type DailyEvent = {
  id: number
  title: string
  avatarUrl: string
  location: string | null
}

type DaySchedule = Record<MeetingPeriod, DailyEvent[]>
type WeeklySchedule = Record<string, DaySchedule>

const calendarDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const meetingPeriods: Array<{ key: MeetingPeriod; label: string; time: string }> = [
  {
    key: 'lunch',
    label: 'Lunch',
    time: `${formatScheduleTime(calendarSchedule.lunchStart)} - ${formatScheduleTime(calendarSchedule.lunchEnd)}`,
  },
  {
    key: 'afterSchool',
    label: 'After School',
    time: `${formatScheduleTime(calendarSchedule.afterSchoolStart)} - ${formatScheduleTime(calendarSchedule.afterSchoolEnd)}`,
  },
]

const now = ref(new Date())
const loading = ref(true)
const error = ref('')
const calendarEvents = ref<CalendarEvent[]>([])
const weeklySchedule = ref<WeeklySchedule>(createEmptySchedule())
let clockTimer: ReturnType<typeof setInterval> | undefined

const weekDates = computed(() => {
  const current = new Date(now.value)
  const jsDay = current.getDay()
  const diffToMonday = jsDay === 0 ? -6 : 1 - jsDay
  const monday = new Date(current)
  monday.setDate(current.getDate() + diffToMonday)

  return calendarDays.reduce(
    (acc, day, index) => {
      const date = new Date(monday)
      date.setDate(monday.getDate() + index)
      acc[day] = date
      return acc
    },
    {} as Record<string, Date>,
  )
})

const todayLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  }).format(now.value),
)

const todayShortLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(now.value),
)

const weekRangeLabel = computed(() => {
  const start = getWeekDate('Mon')
  const end = getWeekDate('Sun')

  if (start.getMonth() === end.getMonth()) {
    return `${start.toLocaleString('en-US', { month: 'long' })} ${start.getDate()}-${end.getDate()}`
  }

  return `${start.toLocaleString('en-US', { month: 'short' })} ${start.getDate()} - ${end.toLocaleString('en-US', { month: 'short' })} ${end.getDate()}`
})

const currentTimeLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  }).format(now.value),
)

const currentTimeMarker = computed(() => {
  const minutes = now.value.getHours() * 60 + now.value.getMinutes()
  const { lunchStart, lunchEnd, afterSchoolStart, afterSchoolEnd } = calendarSchedule

  if (minutes >= lunchStart && minutes <= lunchEnd) {
    return {
      period: 'lunch' as MeetingPeriod,
      offset: clampPercentage(((minutes - lunchStart) / (lunchEnd - lunchStart)) * 100),
    }
  }

  if (minutes < afterSchoolStart || minutes > afterSchoolEnd) {
    return null
  }

  return {
    period: 'afterSchool' as MeetingPeriod,
    offset: clampPercentage(
      ((minutes - afterSchoolStart) / (afterSchoolEnd - afterSchoolStart)) * 100,
    ),
  }
})

const displayedWeekKey = computed(() => getWeekDate('Mon').toDateString())

watch(displayedWeekKey, () => {
  weeklySchedule.value = buildScheduleFromEvents(calendarEvents.value)
})

onMounted(async () => {
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 60_000)

  loading.value = true
  error.value = ''
  try {
    const events = await fetchCalendar()
    calendarEvents.value = events
    weeklySchedule.value = buildScheduleFromEvents(events)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schedule'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})

function createEmptySchedule(): WeeklySchedule {
  return calendarDays.reduce((schedule, day) => {
    schedule[day] = { lunch: [], afterSchool: [] }
    return schedule
  }, {} as WeeklySchedule)
}

function buildScheduleFromEvents(events: CalendarEvent[]) {
  const schedule = createEmptySchedule()
  events.forEach((event) => {
    const parsed = parseMeetingSchedule(event.meetingSchedule)
    if (!parsed) return

    const entry: DailyEvent = {
      id: event.clubId,
      title: event.clubName,
      avatarUrl: clubImage({
        id: event.clubId,
        name: event.clubName,
        imageUrl: event.imageUrl,
        instagramUrl: event.instagramUrl,
        createdAt: event.createdAt,
        updatedAt: event.updatedAt,
      }),
      location: event.location ?? null,
    }

    parsed.days.forEach((day) => {
      const meetingDate = getWeekDate(day)
      const cadence = cadenceForDay(parsed.cadence, day)
      if (!meetingDate || !occursOnDate(cadence, meetingDate)) return

      parsed.periods.forEach((period) => {
        schedule[day]?.[period].push({ ...entry })
      })
    })
  })

  Object.values(schedule).forEach((day) => {
    meetingPeriods.forEach(({ key }) => {
      day[key].sort((a, b) => a.title.localeCompare(b.title))
    })
  })

  return schedule
}

function cadenceForDay(cadenceLabel: string, day: string) {
  const fullDayName = getWeekDate(day).toLocaleDateString('en-US', { weekday: 'long' })
  const daySpecificCadence = cadenceLabel.match(
    new RegExp(`${fullDayName} meetings? are (biweekly|weekly)`, 'i'),
  )
  return daySpecificCadence?.[1] ?? cadenceLabel
}

function getWeekDate(day: string) {
  return weekDates.value[day] ?? now.value
}

function formatWeekDate(day: string) {
  return getWeekDate(day).getDate()
}

function clampPercentage(value: number) {
  return Math.min(100, Math.max(0, value))
}
</script>

<template>
  <div class="calendar-page">
    <section class="calendar-wrapper page-shell">
      <div class="calendar-heading">
        <div>
          <p class="section-label">Weekly club schedule</p>
          <h1 class="calendar-title">Calendar</h1>
          <p class="calendar-subtitle">
            {{ weekRangeLabel }} · Lunch and after-school meetings in one weekly view.
          </p>
        </div>
        <p class="calendar-total">{{ todayLabel }}</p>
      </div>

      <section v-if="loading" class="status-banner">Loading schedule...</section>
      <section v-else-if="error" class="status-banner error">{{ error }}</section>

      <div v-else class="calendar-scroll">
        <div class="weekly-calendar">
          <div class="week-grid week-header">
            <div class="time-axis-heading">
              <span>Local time</span>
              <strong>{{ currentTimeLabel }}</strong>
            </div>
            <div
              v-for="day in calendarDays"
              :key="day"
              class="day-heading"
              :class="{ today: day === todayShortLabel }"
            >
              <span class="day-name">{{ day }}</span>
              <span class="day-date">{{ formatWeekDate(day) }}</span>
            </div>
          </div>

          <div v-for="period in meetingPeriods" :key="period.key" class="week-grid period-row">
            <div class="period-label">
              <strong>{{ period.label }}</strong>
              <span>{{ period.time }}</span>
            </div>

            <section
              v-for="day in calendarDays"
              :key="day"
              class="calendar-cell"
              :class="{ 'current-day': day === todayShortLabel }"
            >
              <div
                v-if="
                  currentTimeMarker &&
                  day === todayShortLabel &&
                  period.key === currentTimeMarker.period
                "
                class="current-time-line"
                :style="{ '--current-time-offset': `${currentTimeMarker.offset}%` }"
                aria-hidden="true"
              >
                <span>{{ currentTimeLabel }}</span>
              </div>

              <div v-if="weeklySchedule[day]?.[period.key].length" class="event-stack">
                <RouterLink
                  v-for="event in weeklySchedule[day]?.[period.key]"
                  :key="event.id"
                  class="event-card"
                  :to="`/clubs/${event.id}`"
                  :aria-label="`${event.title}, ${event.location || 'Location TBD'}`"
                >
                  <img
                    class="event-avatar"
                    :src="event.avatarUrl"
                    :alt="`${event.title} avatar`"
                    loading="lazy"
                  />
                  <p class="event-location">{{ event.location || 'Location TBD' }}</p>
                </RouterLink>
              </div>
              <p v-else class="empty">No meetings</p>
            </section>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.calendar-page {
  min-height: 100vh;
  padding-block: clamp(2rem, 4vw, 4rem);
  color: var(--mv-text);
}

.page-shell {
  width: var(--page-content-width);
  margin: 0 auto;
  padding-inline: var(--page-padding-inline);
}

.calendar-wrapper {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.calendar-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.calendar-title {
  margin-bottom: 0.25rem;
  font-size: clamp(2rem, 5vw, 2.8rem);
}

.calendar-subtitle {
  max-width: 620px;
  color: var(--mv-text-faint);
}

.calendar-total {
  padding: 0.55rem 0.9rem;
  border: 1px solid var(--mv-border);
  border-radius: 999px;
  background: var(--mv-surface-soft);
  color: var(--mv-gold);
  font-weight: 600;
}

.calendar-scroll {
  overflow-x: auto;
  border: 1px solid var(--mv-border);
  border-radius: 24px;
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-card);
  scrollbar-color: var(--mv-border-strong) transparent;
}

.weekly-calendar {
  min-width: 1120px;
}

.week-grid {
  display: grid;
  grid-template-columns: 120px repeat(7, minmax(140px, 1fr));
}

.week-header {
  position: sticky;
  top: 0;
  z-index: 4;
  border-bottom: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-card);
}

.time-axis-heading,
.period-label {
  position: sticky;
  left: 0;
  z-index: 3;
  background: var(--mv-surface-card);
}

.time-axis-heading {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.2rem;
  min-height: 88px;
  padding: 0.85rem;
  color: var(--mv-text-dim);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.time-axis-heading strong {
  color: var(--mv-text-soft);
  font-size: 0.82rem;
  letter-spacing: 0;
  text-transform: none;
}

.day-heading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  min-height: 88px;
  padding: 0.75rem;
  border-left: 1px solid var(--mv-border);
}

.day-heading.today {
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
}

.day-name {
  color: var(--mv-text-faint);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.day-date {
  display: grid;
  width: 2.4rem;
  height: 2.4rem;
  place-items: center;
  border-radius: 999px;
  font-size: 1.15rem;
  font-weight: 700;
}

.day-heading.today .day-date {
  background: var(--mv-gold);
  color: var(--mv-surface-card);
}

.period-row + .period-row {
  border-top: 1px solid var(--mv-border-strong);
}

.period-label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1rem 0.85rem;
  border-right: 1px solid var(--mv-border);
}

.period-label strong {
  font-size: 0.9rem;
}

.period-label span {
  color: var(--mv-text-dim);
  font-size: 0.7rem;
  line-height: 1.4;
}

.calendar-cell {
  position: relative;
  min-width: 0;
  min-height: 180px;
  padding: 0.65rem;
  border-left: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
}

.week-header + .period-row .calendar-cell {
  min-height: 420px;
}

.calendar-cell.current-day {
  background: color-mix(in srgb, var(--mv-surface-accent) 45%, var(--mv-surface-card));
}

.event-stack {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.event-card {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  padding: 0.45rem;
  border: 1px solid var(--mv-border);
  border-radius: 13px;
  background: var(--mv-surface-soft);
  color: inherit;
  text-decoration: none;
  transition:
    border-color 150ms ease,
    background 150ms ease,
    transform 150ms ease;
}

.event-card:hover,
.event-card:focus-visible {
  z-index: 2;
  border-color: var(--mv-border-strong);
  background: var(--mv-surface-accent);
  transform: translateY(-1px);
}

.event-card:focus-visible {
  outline: 2px solid var(--mv-gold);
  outline-offset: 2px;
}

.event-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border: 1px solid var(--mv-border);
  border-radius: 11px;
  background: var(--mv-surface-accent);
  object-fit: cover;
}

.event-location {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--mv-text-faint);
  font-size: 0.78rem;
  line-height: 1.25;
}

.current-time-line {
  position: absolute;
  top: var(--current-time-offset);
  right: 0;
  left: 0;
  z-index: 3;
  height: 2px;
  background: #ef4444;
  pointer-events: none;
}

.current-time-line::before {
  position: absolute;
  top: 50%;
  left: -4px;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #ef4444;
  content: '';
  transform: translateY(-50%);
}

.current-time-line span {
  position: absolute;
  top: 0;
  right: 0.3rem;
  padding: 0.1rem 0.3rem;
  border-radius: 0 0 6px 6px;
  background: #ef4444;
  color: #fff;
  font-size: 0.64rem;
  font-weight: 700;
  transform: translateY(-1px);
}

.empty {
  margin: 0;
  color: var(--mv-text-dim);
  font-size: 0.78rem;
}

.status-banner {
  padding: 0.85rem 1.25rem;
  border: 1px solid var(--mv-border-strong);
  border-radius: 16px;
  background: var(--mv-surface-accent);
  color: var(--mv-text-soft);
}

.status-banner.error {
  border-color: rgba(239, 68, 68, 0.35);
  background: var(--mv-surface-danger);
  color: var(--mv-status-danger);
}

.section-label {
  color: var(--mv-text-dim);
  font-size: 0.75rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

@media (max-width: 720px) {
  .calendar-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .calendar-subtitle {
    max-width: 100%;
  }

  .weekly-calendar {
    min-width: 980px;
  }

  .week-grid {
    grid-template-columns: 96px repeat(7, minmax(126px, 1fr));
  }

  .time-axis-heading,
  .period-label {
    padding-inline: 0.65rem;
  }
}

@media (max-width: 480px) {
  .calendar-title {
    font-size: 1.8rem;
  }

  .calendar-scroll {
    margin-inline: calc(var(--page-padding-inline) * -1);
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }
}
</style>
