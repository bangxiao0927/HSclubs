<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'

type DailyEvent = {
  id: number
  title: string
  category: string
  detail: string
  location: string | null
  advisor: string | null
}

const calendarDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const loading = ref(true)
const error = ref('')
const dailySchedule = ref<Record<string, DailyEvent[]>>(createEmptySchedule())

const totalMeetings = computed(() =>
  calendarDays.reduce((sum, day) => sum + (dailySchedule.value[day]?.length ?? 0), 0),
)

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

    const entry: DailyEvent = {
      id: club.id,
      title: club.name,
      category: club.category,
      detail: parsed.detail,
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

function parseMeetingSchedule(meetingSchedule: string | null): { day: string; detail: string } | null {
  if (!meetingSchedule) return null
  const day = extractDay(meetingSchedule)
  if (!day) return null

  const [, detailPart = ''] = meetingSchedule.split('\u00b7')
  const detail = detailPart.trim() || 'See club details'
  return { day, detail }
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
</script>

<template>
  <div class="calendar-page">
    <section class="calendar-wrapper page-shell">
      <div class="calendar-heading">
        <div>
          <p class="section-label">Live meeting map</p>
          <h1 class="calendar-title">Calendar</h1>
          <p class="calendar-subtitle">Automatically organized from each club's submitted meeting schedule.</p>
        </div>
        <p class="calendar-total">{{ totalMeetings }} total meetings</p>
      </div>

      <section v-if="loading" class="status-banner">Loading schedule...</section>
      <section v-else-if="error" class="status-banner error">{{ error }}</section>

      <div v-else class="calendar-grid">
        <div
          v-for="day in calendarDays"
          :key="day"
          class="calendar-column"
        >
          <div class="column-head">
            <span>{{ day }}</span>
            <small>{{ dailySchedule[day]?.length || 0 }} events</small>
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
                <p class="event-detail">{{ event.detail }}</p>
                <p class="event-location">
                  <span>{{ event.location || 'Location TBD' }}</span>
                  <span v-if="event.advisor">&middot; {{ event.advisor }}</span>
                </p>
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
}

.column-head {
  padding: 1.1rem 1.25rem;
  border-bottom: 1px solid var(--mv-border);
  display: flex;
  justify-content: space-between;
  font-weight: 600;
}

.column-head small {
  color: var(--mv-text-dim);
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
  border: 1px solid transparent;
  transition: border-color 150ms ease, background 150ms ease;
}

.event-card:hover,
.event-card:focus-visible {
  border-color: var(--mv-border-strong);
  background: var(--mv-surface-accent);
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
}

.event-badge {
  font-size: 0.75rem;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
}

.event-detail,
.event-location {
  margin: 0;
  font-size: 0.9rem;
  color: var(--mv-text-soft);
}

.event-location {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  color: var(--mv-text-faint);
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
}
</style>
