<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'

type DailyEvent = {
  id: number
  title: string
  category: string
  cadence: string
  timeLabel: string
  scheduleNote: string | null
  location: string | null
  advisor: string | null
}

const calendarDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const today = new Date()

const loading = ref(true)
const error = ref('')

const dailySchedule = ref<Record<string, DailyEvent[]>>(createEmptySchedule())

const weekDates = computed(() => {
  const current = new Date(today)
  const jsDay = current.getDay()
  const diffToMonday = jsDay === 0 ? -6 : 1 - jsDay
  const monday = new Date(current)
  monday.setDate(current.getDate() + diffToMonday)

  return calendarDays.reduce((acc, day, index) => {
    const date = new Date(monday)
    date.setDate(monday.getDate() + index)
    acc[day] = date
    return acc
  }, {} as Record<string, Date>)
})

const todayLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  }).format(today),
)

const todayShortLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(today),
)

const weekRangeLabel = computed(() => {
  const start = getWeekDate('Mon')
  const end = getWeekDate('Sun')

  if (start.getMonth() === end.getMonth()) {
    return `${start.toLocaleString('en-US', { month: 'long' })} ${start.getDate()}-${end.getDate()}`
  }

  return `${start.toLocaleString('en-US', { month: 'short' })} ${start.getDate()} - ${end.toLocaleString('en-US', { month: 'short' })} ${end.getDate()}`
})

const totalMeetings = computed(() =>
  calendarDays.reduce((sum, day) => sum + (dailySchedule.value[day]?.length ?? 0), 0),
)

const activeDays = computed(
  () => calendarDays.filter((day) => (dailySchedule.value[day]?.length ?? 0) > 0).length,
)

const todayBucket = computed(() => {
  return dailySchedule.value[todayShortLabel.value] ?? []
})

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const clubs = await fetchClubs()
    dailySchedule.value = buildSchedule(clubs)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schedule'
  } finally {
    loading.value = false
  }
})

function createEmptySchedule() {
  return calendarDays.reduce((acc, day) => {
    acc[day] = []
    return acc
  }, {} as Record<string, DailyEvent[]>)
}

function buildSchedule(clubs: Club[]) {
  const schedule = createEmptySchedule()
  clubs.forEach((club) => {
    const parsed = parseMeetingSchedule(club.meetingSchedule)
    if (!parsed) return
    const meetingDate = getWeekDate(parsed.day)
    if (!meetingDate || !occursOnDate(parsed, meetingDate)) return

    const entry: DailyEvent = {
      id: club.id,
      title: club.name,
      category: club.category,
      cadence: parsed.cadence,
      timeLabel: parsed.timeLabel,
      scheduleNote: club.scheduleNote ?? null,
      location: club.location ?? null,
      advisor: club.advisor ?? null,
    }

    const bucket = schedule[parsed.day] ?? []
    bucket.push(entry)
    schedule[parsed.day] = bucket
  })

  Object.values(schedule).forEach((events) => {
    events.sort((a, b) => a.title.localeCompare(b.title))
  })

  return schedule
}

function parseMeetingSchedule(meetingSchedule: string | null): { day: string; cadence: string; timeLabel: string } | null {
  if (!meetingSchedule) return null
  const day = extractDay(meetingSchedule)
  if (!day) return null

  const parts = meetingSchedule.split('\u00b7').map((part) => part.trim()).filter(Boolean)
  const cadence = parts[1] ?? 'Weekly'
  const timeLabel = parts[2] ?? parts[1] ?? 'See club details'
  return { day, cadence, timeLabel }
}

function extractDay(value: string) {
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

  const match = value.toLowerCase().match(/(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)/)
  if (!match) return null
  return dayTokens[match[1] as keyof typeof dayTokens] ?? null
}

function occursOnDate(
  schedule: { cadence: string; timeLabel: string },
  date: Date,
) {
  const cadence = schedule.cadence.toLowerCase()

  if (cadence.includes('biweekly')) {
    return getIsoWeek(date) % 2 === 0
  }

  if (cadence.includes('weekly')) {
    return true
  }

  if (cadence.includes('first and last week of the month')) {
    return isFirstOccurrenceOfWeekday(date) || isLastOccurrenceOfWeekday(date)
  }

  if (cadence.includes('first week of the month')) {
    return isFirstOccurrenceOfWeekday(date)
  }

  if (cadence.includes('last week of the month')) {
    return isLastOccurrenceOfWeekday(date)
  }

  return true
}

function getIsoWeek(date: Date) {
  const utcDate = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = utcDate.getUTCDay() || 7
  utcDate.setUTCDate(utcDate.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(utcDate.getUTCFullYear(), 0, 1))
  return Math.ceil((((utcDate.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)
}

function isFirstOccurrenceOfWeekday(date: Date) {
  return date.getDate() <= 7
}

function isLastOccurrenceOfWeekday(date: Date) {
  const nextWeek = new Date(date)
  nextWeek.setDate(date.getDate() + 7)
  return nextWeek.getMonth() !== date.getMonth()
}

function getWeekDate(day: string) {
  return weekDates.value[day] ?? today
}

function formatWeekDate(day: string) {
  return getWeekDate(day).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
</script>

<template>
  <div class="calendar-page">
    <section class="calendar-wrapper page-shell">
      <div class="calendar-heading">
        <div>
          <p class="section-label">Live meeting map</p>
          <h1 class="calendar-title">Calendar</h1>
          <p class="calendar-subtitle">Week of {{ weekRangeLabel }}. Parsed from each club's submitted cadence and lunch schedule.</p>
        </div>
        <p class="calendar-total">{{ todayLabel }}</p>
      </div>

      <div class="calendar-summary">
        <article class="summary-card">
          <span class="summary-label">Today</span>
          <strong>{{ todayShortLabel }}</strong>
          <p>{{ todayBucket.length }} meeting{{ todayBucket.length === 1 ? '' : 's' }} scheduled today.</p>
        </article>
        <article class="summary-card">
          <span class="summary-label">Active days</span>
          <strong>{{ activeDays }}</strong>
          <p>Days this week with at least one club meeting.</p>
        </article>
        <article class="summary-card">
          <span class="summary-label">This week</span>
          <strong>{{ totalMeetings }}</strong>
          <p>Meetings after applying weekly and biweekly cadence rules.</p>
        </article>
      </div>

      <section v-if="loading" class="status-banner">Loading schedule...</section>
      <section v-else-if="error" class="status-banner error">{{ error }}</section>

      <div v-else class="calendar-grid">
        <div
          v-for="day in calendarDays"
          :key="day"
          class="calendar-column"
          :class="{ active: Boolean(dailySchedule[day]?.length) }"
        >
          <div class="column-head">
            <div class="column-day">
              <span class="day-dot" />
              <span>{{ day }}</span>
            </div>
            <small>{{ formatWeekDate(day) }}</small>
          </div>
          <div class="column-body">
            <template v-if="dailySchedule[day]?.length">
              <RouterLink
                v-for="event in dailySchedule[day]"
                :key="event.id"
                class="event-card"
                :to="`/clubs/${event.id}`"
              >
                <div class="event-title-row">
                  <h3>{{ event.title }}</h3>
                  <span class="event-badge">{{ event.category }}</span>
                </div>
                <p class="event-detail">{{ event.timeLabel }}</p>
                <p v-if="event.scheduleNote" class="event-note">{{ event.scheduleNote }}</p>
                <div class="event-meta">
                  <p class="event-location">{{ event.location || 'Location TBD' }}</p>
                  <p class="event-cadence">{{ event.cadence }}</p>
                  <p v-if="event.advisor" class="event-advisor">{{ event.advisor }}</p>
                </div>
              </RouterLink>
            </template>
            <p v-else class="empty">No meetings scheduled</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.calendar-page {
  background: transparent;
  color: var(--mv-text);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding-block: clamp(2rem, 4vw, 4rem);
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

.calendar-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.summary-card {
  border-radius: 22px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  padding: 1rem 1.1rem;
  box-shadow: var(--mv-shadow-card);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.summary-card strong {
  font-size: 1.7rem;
  color: var(--mv-gold);
}

.summary-card p {
  margin: 0;
  color: var(--mv-text-faint);
  font-size: 0.9rem;
}

.summary-label {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.76rem;
  color: var(--mv-text-dim);
}

.calendar-heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-end;
  flex-wrap: wrap;
}

.calendar-title {
  font-size: clamp(2rem, 5vw, 2.8rem);
  margin-bottom: 0.25rem;
}

.calendar-subtitle {
  color: var(--mv-text-faint);
  max-width: 540px;
}

.calendar-total {
  font-weight: 600;
  color: var(--mv-gold);
  padding: 0.55rem 0.9rem;
  border-radius: 999px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.calendar-column {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  display: flex;
  flex-direction: column;
  box-shadow: var(--mv-shadow-card);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.calendar-column.active {
  border-color: var(--mv-border-strong);
}

.calendar-column:hover {
  transform: translateY(-2px);
}

.column-head {
  padding: 1.1rem 1.25rem;
  border-bottom: 1px solid var(--mv-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  background: var(--mv-surface-soft);
}

.column-day {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}

.day-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--mv-gold);
  box-shadow: 0 0 0 4px var(--mv-gold-soft);
}

.column-head small {
  color: var(--mv-text-dim);
  font-weight: 500;
}

.column-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.25rem 1.5rem;
}

.event-card {
  background: var(--mv-surface-soft);
  padding: 1rem 1.25rem;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  color: inherit;
  text-decoration: none;
  border: 1px solid var(--mv-border);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
  transition: border-color 150ms ease, background 150ms ease, transform 150ms ease, box-shadow 150ms ease;
}

.event-card:hover,
.event-card:focus-visible {
  border-color: var(--mv-border-strong);
  background: var(--mv-surface-accent);
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.16);
}

.event-card:focus-visible {
  outline: 2px solid var(--mv-gold);
  outline-offset: 4px;
}

.event-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.event-title-row h3 {
  margin: 0;
  font-size: 1rem;
  line-height: 1.35;
}

.event-badge {
  font-size: 0.75rem;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
}

.event-detail,
.event-note,
.event-location,
.event-advisor {
  margin: 0;
  font-size: 0.9rem;
}

.event-detail {
  color: var(--mv-text-soft);
}

.event-note {
  padding: 0.55rem 0.7rem;
  border-radius: 12px;
  background: var(--mv-surface-card);
  border: 1px solid var(--mv-border);
  color: var(--mv-text-soft);
  white-space: pre-wrap;
}

.event-meta {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.event-location,
.event-cadence,
.event-advisor {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  color: var(--mv-text-faint);
}

.event-cadence {
  color: var(--mv-gold);
}

:global(:root[data-theme='dark']) .calendar-column {
  background: rgba(12, 12, 12, 0.92);
}

:global(:root[data-theme='dark']) .column-head {
  background: rgba(250, 204, 21, 0.08);
  border-bottom-color: rgba(250, 204, 21, 0.18);
}

:global(:root[data-theme='dark']) .event-card {
  background: rgba(24, 24, 24, 0.96);
  border-color: rgba(250, 204, 21, 0.16);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 10px 20px rgba(0, 0, 0, 0.28);
}

:global(:root[data-theme='dark']) .event-card:hover,
:global(:root[data-theme='dark']) .event-card:focus-visible {
  background: rgba(38, 38, 38, 0.98);
  border-color: rgba(250, 204, 21, 0.45);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 14px 28px rgba(0, 0, 0, 0.34);
}

:global(:root[data-theme='dark']) .event-note {
  background: rgba(8, 8, 8, 0.9);
  border-color: rgba(250, 204, 21, 0.16);
}

.empty {
  color: var(--mv-text-dim);
  font-size: 0.9rem;
}

.status-banner {
  padding: 0.85rem 1.25rem;
  border-radius: 16px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-accent);
  color: var(--mv-text-soft);
}

.status-banner.error {
  border-color: rgba(239, 68, 68, 0.35);
  background: var(--mv-surface-danger);
  color: var(--mv-status-danger);
}

.section-label {
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-size: 0.75rem;
  color: var(--mv-text-dim);
}

@media (max-width: 640px) {
  .calendar-heading {
    flex-direction: column;
    align-items: flex-start;
  }

  .calendar-grid {
    grid-template-columns: 1fr;
  }
}
</style>
