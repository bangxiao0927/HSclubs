<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import { storeToRefs } from 'pinia'
import { useSchoolStore } from '../stores/school'

const schoolStore = useSchoolStore()
const route = useRoute()
const { schools, loading, error } = storeToRefs(schoolStore)

const query = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const filteredSchools = computed(() => {
  if (!query.value) return schools.value
  const normalizedQuery = query.value.toLowerCase()
  return schools.value.filter((school) => {
    return [school.schoolName, school.shortName, school.slug]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(normalizedQuery))
  })
})

onMounted(() => {
  schoolStore.loadSchools()
})
</script>

<template>
  <div class="school-picker">
    <section class="picker-hero page-shell">
      <p class="section-label">Schools</p>
      <h1>Select your school</h1>
      <p>Choose a school to browse its club directory, calendar, and manage your memberships.</p>
      <p v-if="query" class="query-note">Showing schools matching "{{ query }}".</p>
    </section>

    <section v-if="loading" class="page-shell"><SkeletonLoader :count="3" height="80px" /></section>
    <section v-else-if="error" class="status-banner error page-shell">{{ error }}</section>

    <section v-else class="school-grid page-shell">
      <RouterLink
        v-for="school in filteredSchools"
        :key="school.slug"
        :to="`/schools/${school.slug}`"
        class="school-card"
      >
        <div class="school-avatar">
          <img v-if="school.logoUrl" :src="school.logoUrl" :alt="school.schoolName" />
          <span v-else class="school-initial">{{ school.schoolName.charAt(0) }}</span>
        </div>
        <div class="school-meta">
          <h2>{{ school.schoolName }}</h2>
          <p v-if="school.shortName" class="school-short">{{ school.shortName }}</p>
          <span class="school-badge">Active</span>
        </div>
      </RouterLink>

      <div v-if="filteredSchools.length === 0" class="empty-state">
        <p>{{ query ? 'No schools match that search yet.' : 'No schools are available yet.' }}</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.school-picker {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 5vw, 3.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.picker-hero {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.picker-hero h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3rem);
}

.query-note {
  color: var(--mv-text-muted);
}

.school-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.25rem;
}

.school-card {
  display: flex;
  gap: 1rem;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  text-decoration: none;
  color: inherit;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.school-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--mv-shadow-elevated);
}

.school-avatar {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.school-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.school-initial {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--mv-gold);
}

.school-meta {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.school-meta h2 {
  margin: 0;
  font-size: 1.15rem;
}

.school-short {
  margin: 0;
  color: var(--mv-text-faint);
  font-size: 0.9rem;
}

.school-badge {
  display: inline-block;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--mv-status-success);
  background: rgba(34, 197, 94, 0.12);
  width: fit-content;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 2rem;
  text-align: center;
  border-radius: 24px;
  border: 1px dashed var(--mv-border-strong);
  background: var(--mv-surface-muted);
  color: var(--mv-text-muted);
}

.status-banner {
  width: var(--page-content-width);
  margin: 0 auto;
  padding: 0.75rem 1.5rem;
  border-radius: 16px;
  background: var(--mv-surface-accent);
  border: 1px solid var(--mv-border-strong);
}

.status-banner.error {
  background: var(--mv-surface-danger);
  border-color: rgba(239, 68, 68, 0.35);
  color: var(--mv-status-danger);
}

@media (max-width: 720px) {
  .school-grid {
    grid-template-columns: 1fr;
  }

  .picker-hero h1 {
    font-size: clamp(1.6rem, 5vw, 2rem);
  }
}

@media (max-width: 480px) {
  .school-card {
    padding: 1rem 1.25rem;
    border-radius: 18px;
  }

  .school-avatar {
    width: 52px;
    height: 52px;
    border-radius: 16px;
  }

  .school-meta h2 {
    font-size: 1rem;
  }
}
</style>
