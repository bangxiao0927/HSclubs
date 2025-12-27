<script setup lang="ts">
type Stat = {
  label: string
  value: string
  helper: string
}

type ClubFeature = {
  title: string
  category: string
  description: string
  members: number
  focus: string
}

type EventItem = {
  title: string
  date: string
  location: string
  status: string
}

type WorkflowStep = {
  title: string
  detail: string
}

const stats: Stat[] = [
  { label: 'Active Clubs', value: '36', helper: 'Arts · Athletics · STEM' },
  { label: 'Students Engaged', value: '1,480', helper: '72% of Spartans participate' },
  { label: 'Advisors & Coaches', value: '41', helper: 'Every program verified' },
]

const featuredClubs: ClubFeature[] = [
  {
    title: 'Spartan Robotics',
    category: 'Engineering · Thursdays',
    description: 'Competition robots, autonomous driving labs, and build nights in the innovation lab.',
    members: 48,
    focus: 'STEM Lab',
  },
  {
    title: 'Golden Sound Collective',
    category: 'Media · Tuesdays',
    description: 'Student-run studio captures podcasts, halftime shows, and live-streams pep events.',
    members: 34,
    focus: 'Creative Media',
  },
  {
    title: 'Trail Stewards',
    category: 'Service · Weekends',
    description: 'Outdoor crews partner with the city to restore Shoreline trails and teach sustainability.',
    members: 61,
    focus: 'Community Impact',
  },
]

const events: EventItem[] = [
  {
    title: 'Lunchtime Club Crawl',
    date: 'Aug 29 · 12:30',
    location: 'Spartan Plaza',
    status: 'Live booths',
  },
  {
    title: 'Leadership Lab Intensive',
    date: 'Sep 04 · 15:45',
    location: 'Room 302 · Activities',
    status: '8 seats left',
  },
  {
    title: 'Citywide Service Summit',
    date: 'Sep 16 · 09:00',
    location: 'Shoreline Amphitheatre',
    status: 'Invite only',
  },
]

const workflow: WorkflowStep[] = [
  {
    title: 'Pitch your idea',
    detail: 'Start with a one-page intent and faculty sponsor — digital intake routes to Activities.',
  },
  {
    title: 'Align resources',
    detail: 'Reserve space, budget, and permissions with pre-approved templates in 10 minutes.',
  },
  {
    title: 'Go live + measure',
    detail: 'Central dashboard tracks signups, messaging, and advisor feedback every week.',
  },
]
</script>

<template>
  <div class="home">
    <section class="hero-card">
      <div class="hero-copy">
        <p class="section-label">Mountain View Unified</p>
        <h1 class="section-title">Mountain View High School Clubs</h1>
        <p class="hero-subtitle">
          A single home for every Spartan club — real-time rosters, event drops, and advisor updates built with the black + gold identity of Mountain View High School.
        </p>
        <div class="hero-actions">
          <button class="primary-btn" type="button">Browse clubs</button>
          <button class="secondary-btn" type="button">Download overview</button>
        </div>

        <div class="hero-stats">
          <div v-for="item in stats" :key="item.label" class="stat-card">
            <p class="stat-value">{{ item.value }}</p>
            <p class="stat-label">{{ item.label }}</p>
            <p class="stat-helper">{{ item.helper }}</p>
          </div>
        </div>
      </div>

      <div class="hero-panel">
        <div class="panel-header">
          <p>This week · Activities</p>
          <span>Sync to calendar</span>
        </div>
        <ul class="panel-body">
          <li v-for="event in events" :key="event.title" class="panel-row">
            <div>
              <p class="panel-title">{{ event.title }}</p>
              <p class="panel-meta">{{ event.location }}</p>
            </div>
            <div class="panel-pill">
              <span>{{ event.date }}</span>
              <small>{{ event.status }}</small>
            </div>
          </li>
        </ul>
        <div class="panel-footer">Auto reminders out to 1,480 students · Advisors notified</div>
      </div>
    </section>

    <section class="feature-grid">
      <div class="section-heading">
        <p class="section-label">Spotlight</p>
        <h2 class="section-title">Black & Gold ready-to-use blocks</h2>
        <p class="section-subtitle">
          Modular cards carry the Mountain View brand so every club page, schedule tile, and signup entry stays consistent.
        </p>
      </div>

      <div class="grid">
        <article v-for="club in featuredClubs" :key="club.title" class="club-card">
          <div class="club-meta">
            <p class="club-category">{{ club.category }}</p>
            <span class="club-focus">{{ club.focus }}</span>
          </div>
          <h3>{{ club.title }}</h3>
          <p class="club-description">{{ club.description }}</p>
          <div class="club-footer">
            <p>{{ club.members }} members</p>
            <button class="link-btn" type="button">View details →</button>
          </div>
        </article>
      </div>
    </section>

    <section class="workflow">
      <div class="section-heading">
        <p class="section-label">Playbook</p>
        <h2 class="section-title">Three steps to launch a program</h2>
      </div>
      <ol class="workflow-list">
        <li v-for="(step, index) in workflow" :key="step.title" class="workflow-item">
          <div class="step-index">0{{ index + 1 }}</div>
          <div>
            <p class="step-title">{{ step.title }}</p>
            <p class="step-detail">{{ step.detail }}</p>
          </div>
        </li>
      </ol>
    </section>

    <section class="cta-card">
      <div>
        <p class="section-label">Next for Mountain View</p>
        <h2 class="section-title">Keep every club aligned to Spartan energy</h2>
        <p class="section-subtitle">
          Import your roster, auto-apply the black + gold system, and get faculty & student leaders moving with ready-to-share guides.
        </p>
      </div>
      <div class="cta-actions">
        <button class="primary-btn" type="button">Request deployment kit</button>
        <button class="secondary-btn" type="button">Download structure</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 3rem;
}

.hero-card {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 2.2rem;
  padding: clamp(1.75rem, 4vw, 3rem);
  border-radius: 40px;
  background: linear-gradient(140deg, rgba(250, 204, 21, 0.22), rgba(5, 5, 5, 0.95) 65%), var(--mv-ink);
  border: 1px solid rgba(250, 204, 21, 0.28);
  box-shadow: 0 35px 70px rgba(0, 0, 0, 0.55);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.hero-subtitle {
  color: var(--mv-text-muted);
  font-size: 1rem;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}

.primary-btn,
.secondary-btn {
  border-radius: 16px;
  border: 1px solid transparent;
  padding: 0.85rem 1.6rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.primary-btn {
  background: linear-gradient(120deg, #fde047, #facc15, #f59e0b);
  color: #050505;
  box-shadow: 0 18px 32px rgba(250, 204, 21, 0.35);
}

.primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 25px 40px rgba(250, 204, 21, 0.45);
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.04);
  color: var(--mv-text);
  border-color: rgba(250, 204, 21, 0.25);
}

.link-btn {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--mv-gold);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 1rem;
}

.stat-card {
  padding: 1.1rem;
  border-radius: 20px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(250, 204, 21, 0.12);
  backdrop-filter: blur(8px);
}

.stat-value {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--mv-gold);
}

.stat-label {
  font-weight: 600;
  color: var(--mv-text);
}

.stat-helper {
  font-size: 0.85rem;
  color: var(--mv-text-muted);
}

.hero-panel {
  border-radius: 32px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  background: radial-gradient(circle at top, rgba(250, 204, 21, 0.22), rgba(6, 6, 6, 0.95));
  color: var(--mv-text);
  padding: 1.9rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  color: var(--mv-gold);
}

.panel-body {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0;
  margin: 0;
}

.panel-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(250, 250, 250, 0.08);
}

.panel-row:last-of-type {
  border-bottom: none;
  padding-bottom: 0;
}

.panel-title {
  font-weight: 600;
}

.panel-meta {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.65);
}

.panel-pill {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  font-size: 0.9rem;
  text-align: right;
}

.panel-pill span {
  font-weight: 600;
}

.panel-pill small {
  color: rgba(255, 255, 255, 0.6);
}

.panel-footer {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.75);
}

.feature-grid {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.section-heading {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.5rem;
}

.club-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  background: rgba(10, 10, 10, 0.8);
  padding: 1.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  box-shadow: 0 25px 45px rgba(0, 0, 0, 0.35);
}

.club-card h3 {
  font-size: 1.2rem;
  margin: 0;
}

.club-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: var(--mv-text-muted);
}

.club-focus {
  padding: 0.2rem 0.7rem;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.12);
  color: var(--mv-gold);
  font-size: 0.75rem;
}

.club-description {
  color: var(--mv-text-muted);
  flex: 1;
}

.club-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  color: var(--mv-text-muted);
}

.workflow {
  border-radius: 32px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  padding: clamp(1.25rem, 4vw, 2.8rem);
  background: rgba(11, 11, 11, 0.9);
  box-shadow: 0 25px 55px rgba(0, 0, 0, 0.45);
}

.workflow-list {
  list-style: none;
  margin: 1.75rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
}

.workflow-item {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.step-index {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: rgba(250, 204, 21, 0.12);
  color: var(--mv-gold);
  display: grid;
  place-items: center;
  font-weight: 700;
}

.step-title {
  font-weight: 600;
}

.step-detail {
  color: var(--mv-text-muted);
}

.cta-card {
  border-radius: 32px;
  padding: clamp(1.5rem, 5vw, 3.2rem);
  background: radial-gradient(circle at 15% 20%, rgba(250, 204, 21, 0.35), rgba(8, 8, 8, 0.95));
  color: var(--mv-text);
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  border: 1px solid rgba(250, 204, 21, 0.2);
}

.cta-card .section-subtitle {
  color: var(--mv-text-muted);
}

.cta-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}

.cta-card .primary-btn {
  box-shadow: none;
}

.cta-card .secondary-btn {
  border-color: rgba(250, 204, 21, 0.3);
}

@media (max-width: 600px) {
  .panel-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .panel-pill {
    align-items: flex-start;
    text-align: left;
  }
}
</style>
