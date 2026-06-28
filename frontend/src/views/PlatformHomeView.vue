<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'

import SkeletonLoader from '../components/SkeletonLoader.vue'
import { useSchoolStore } from '../stores/school'

const schoolStore = useSchoolStore()
const { schools, loading, error } = storeToRefs(schoolStore)

const activeSchools = computed(() => schools.value.filter((school) => school.status === 'ACTIVE'))
const featuredSchools = computed(() => activeSchools.value.slice(0, 3))

const platformStats = computed(() => [
  { label: 'Schools onboarded', value: activeSchools.value.length || schools.value.length },
  { label: 'Club directories', value: activeSchools.value.length || schools.value.length },
  { label: 'Member workflows', value: 'Built in' },
])

onMounted(() => {
  schoolStore.loadSchools()
})
</script>

<template>
  <div class="platform-home">
    <section class="platform-hero page-shell">
      <div class="hero-copy">
        <p class="section-label">HS Clubs Platform</p>
        <h1>One home for every school club community.</h1>
        <p>
          HS Clubs helps schools launch their own club directory, discovery pages, calendars, and
          membership workflows without making every campus feel like MVHS.
        </p>
        <div class="hero-actions">
          <RouterLink to="/schools" class="btn primary">Find your school</RouterLink>
          <RouterLink to="/auth?intent=register" class="btn ghost">Create an account</RouterLink>
        </div>
      </div>

      <div class="routing-card" aria-label="Platform flow">
        <span class="routing-pill">Platform</span>
        <h2>Start here, then choose a school.</h2>
        <div class="routing-steps">
          <div>
            <strong>1</strong>
            <span>Select your campus</span>
          </div>
          <div>
            <strong>2</strong>
            <span>Browse that school's clubs</span>
          </div>
          <div>
            <strong>3</strong>
            <span>Join, manage, and track activity</span>
          </div>
        </div>
      </div>
    </section>

    <section class="platform-stats page-shell" aria-label="Platform stats">
      <article v-for="stat in platformStats" :key="stat.label" class="stat-card">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
      </article>
    </section>

    <section class="school-preview page-shell">
      <div class="section-heading">
        <p class="section-label">School directories</p>
        <h2>Pick a school-specific experience</h2>
        <p class="section-subtitle">
          Each school keeps its own branding, clubs, calendars, and admin workflows under a
          dedicated school page.
        </p>
      </div>

      <div v-if="loading" class="school-preview-grid">
        <SkeletonLoader :count="3" height="120px" />
      </div>
      <div v-else-if="error" class="status-banner error">{{ error }}</div>
      <div v-else class="school-preview-grid">
        <RouterLink
          v-for="school in featuredSchools"
          :key="school.slug"
          :to="`/schools/${school.slug}`"
          class="school-card"
        >
          <div
            class="school-avatar"
            :style="{ '--school-accent': school.primaryColor || '#2563eb' }"
          >
            <img v-if="school.logoUrl" :src="school.logoUrl" :alt="school.schoolName" />
            <span v-else>{{ school.shortName?.charAt(0) || school.schoolName.charAt(0) }}</span>
          </div>
          <div>
            <h3>{{ school.schoolName }}</h3>
            <p>{{ school.shortName || school.slug }}</p>
          </div>
        </RouterLink>

        <div v-if="featuredSchools.length === 0" class="empty-state">
          <p>No schools are available yet.</p>
        </div>
      </div>

      <RouterLink to="/schools" class="view-all-link">View all schools</RouterLink>
    </section>

    <section class="platform-capabilities page-shell">
      <article>
        <span>Discover</span>
        <h3>Search clubs by school context</h3>
        <p>
          Students start at the platform level, then move into a school-specific directory for
          relevant clubs.
        </p>
      </article>
      <article>
        <span>Operate</span>
        <h3>Admin tools per campus</h3>
        <p>
          Owners can manage club details, pending members, and school-level workflows without
          crossing contexts.
        </p>
      </article>
      <article>
        <span>Scale</span>
        <h3>Built for more schools</h3>
        <p>
          The neutral platform home leaves room for each school to bring its own brand and identity.
        </p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.platform-home {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 5vw, 4rem);
  padding-block: clamp(2rem, 5vw, 4rem);
}

.platform-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: clamp(1.5rem, 4vw, 3rem);
  align-items: stretch;
}

.hero-copy,
.routing-card,
.stat-card,
.platform-capabilities article,
.school-card {
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-card);
}

.hero-copy {
  border-radius: 36px;
  padding: clamp(2rem, 5vw, 4rem);
  background: var(--mv-surface-hero);
}

.hero-copy h1 {
  max-width: 780px;
  margin: 0.75rem 0 1rem;
  font-size: clamp(2.35rem, 5vw, 4.8rem);
  line-height: 1.02;
  letter-spacing: -0.06em;
}

.hero-copy p {
  max-width: 720px;
  color: var(--mv-text-muted);
  font-size: 1.05rem;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
  margin-top: 1.75rem;
}

.btn,
.view-all-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 0.8rem 1.2rem;
  font-weight: 700;
  text-decoration: none;
}

.btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
}

.btn.ghost,
.view-all-link {
  border: 1px solid var(--mv-ghost-border);
  color: var(--mv-ghost-text);
  background: var(--mv-surface-muted);
}

.routing-card {
  border-radius: 32px;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1.5rem;
}

.routing-card h2 {
  margin: 0;
  font-size: clamp(1.6rem, 3vw, 2.2rem);
}

.routing-pill {
  width: fit-content;
  padding: 0.3rem 0.8rem;
  border-radius: 999px;
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
  font-weight: 700;
}

.routing-steps {
  display: grid;
  gap: 0.9rem;
}

.routing-steps div {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem;
  border-radius: 18px;
  background: var(--mv-surface-soft);
}

.routing-steps strong {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
}

.platform-stats,
.school-preview-grid,
.platform-capabilities {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.stat-card {
  border-radius: 24px;
  padding: 1.25rem;
}

.stat-card span {
  color: var(--mv-text-muted);
}

.stat-card strong {
  display: block;
  margin-top: 0.45rem;
  font-size: clamp(1.6rem, 3vw, 2.3rem);
}

.section-heading {
  margin-bottom: 1.25rem;
}

.section-heading h2 {
  margin: 0.75rem 0 0;
  font-size: clamp(1.8rem, 3vw, 2.6rem);
}

.school-card {
  min-height: 128px;
  border-radius: 26px;
  padding: 1.25rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.school-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--mv-shadow-elevated);
}

.school-card h3 {
  margin: 0;
  font-size: 1.1rem;
}

.school-card p {
  margin: 0.2rem 0 0;
  color: var(--mv-text-muted);
}

.school-avatar {
  width: 66px;
  height: 66px;
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--school-accent);
  color: #fff;
  border: 1px solid var(--school-accent);
  font-size: 1.5rem;
  font-weight: 800;
}

.school-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.view-all-link {
  width: fit-content;
  margin-top: 1.25rem;
}

.platform-capabilities article {
  border-radius: 26px;
  padding: 1.5rem;
}

.platform-capabilities span {
  color: var(--mv-gold);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-size: 0.8rem;
}

.platform-capabilities h3 {
  margin: 0.55rem 0;
}

.platform-capabilities p {
  color: var(--mv-text-muted);
}

.empty-state,
.status-banner {
  grid-column: 1 / -1;
  padding: 1.25rem;
  border-radius: 22px;
  background: var(--mv-surface-muted);
  border: 1px dashed var(--mv-border-strong);
  color: var(--mv-text-muted);
}

.status-banner.error {
  border-style: solid;
  background: var(--mv-surface-danger);
  color: var(--mv-status-danger);
}

@media (max-width: 900px) {
  .platform-hero,
  .platform-stats,
  .school-preview-grid,
  .platform-capabilities {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .hero-copy,
  .routing-card {
    border-radius: 24px;
    padding: 1.4rem;
  }
}
</style>
