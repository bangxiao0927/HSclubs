<script setup lang="ts">
type DailyEvent = {
  title: string
  time: string
  location: string
  category: string
  advisor: string
}

const week = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri']

const dailySchedule: Record<string, DailyEvent[]> = week.reduce((acc, day) => {
  acc[day] = []
  return acc
}, {} as Record<string, DailyEvent[]>)
</script>

<template>
  <div class="calendar-page">
    <section class="calendar-wrapper page-shell">
      <h1 class="calendar-title">Calendar</h1>

      <div class="calendar-grid">
        <div
          v-for="day in week"
          :key="day"
          class="calendar-column"
        >
          <div class="column-head">
            <span>{{ day }}</span>
            <small>{{ dailySchedule[day]?.length || 0 }} events</small>
          </div>
          <div class="column-body">
            <p class="empty">No meetings scheduled</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.calendar-page {
  background: linear-gradient(180deg, #050505 0%, #0b0b0b 70%, #111 100%);
  color: #fefce8;
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

.calendar-title {
  font-size: clamp(2rem, 5vw, 2.8rem);
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.calendar-column {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.15);
  background: rgba(10, 10, 10, 0.85);
  display: flex;
  flex-direction: column;
}

.column-head {
  padding: 1.1rem 1.25rem;
  border-bottom: 1px solid rgba(250, 204, 21, 0.1);
  display: flex;
  justify-content: space-between;
  font-weight: 600;
}

.column-head small {
  color: rgba(254, 252, 232, 0.65);
}

.column-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.1rem 1.25rem 1.5rem;
}

.empty {
  color: rgba(254, 252, 232, 0.45);
  font-size: 0.9rem;
}
</style>
