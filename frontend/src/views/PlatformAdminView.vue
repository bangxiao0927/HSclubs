<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  fetchPlatformSchools,
  createSchool,
  type SchoolAdmin,
  type SchoolFormData,
} from '../services/platformAdminService'

const schools = ref<SchoolAdmin[]>([])
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const formError = ref('')
const showForm = ref(false)

const form = reactive<SchoolFormData>({
  slug: '',
  schoolName: '',
  shortName: '',
  timezone: 'America/Los_Angeles',
  status: 'active',
})

const loadSchools = async () => {
  loading.value = true
  error.value = ''
  try {
    schools.value = await fetchPlatformSchools()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schools'
  } finally {
    loading.value = false
  }
}

onMounted(loadSchools)

const resetForm = () => {
  form.slug = ''
  form.schoolName = ''
  form.shortName = ''
  form.timezone = 'America/Los_Angeles'
  form.status = 'active'
  formError.value = ''
}

const handleCreate = async () => {
  if (!form.slug.trim() || !form.schoolName.trim()) {
    formError.value = 'Slug and school name are required.'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await createSchool({ ...form })
    resetForm()
    showForm.value = false
    await loadSchools()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Failed to create school'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="platform-admin page-shell">
    <header class="admin-hero">
      <div>
        <p class="section-label">Platform</p>
        <h1>School administration</h1>
        <p>Create and manage schools on the platform. Each school operates independently.</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="btn primary" @click="showForm = !showForm">
          {{ showForm ? 'Cancel' : 'Add school' }}
        </button>
        <button type="button" class="btn ghost" @click="loadSchools" :disabled="loading">
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>
    </header>

    <section v-if="loading" class="status-card">Loading schools…</section>
    <section v-else-if="error" class="status-card error">{{ error }}</section>

    <form v-if="showForm" class="create-form" @submit.prevent="handleCreate">
      <h2>Create school</h2>
      <div class="form-grid">
        <label>
          <span>Slug *</span>
          <input v-model="form.slug" type="text" placeholder="mvhs" required />
        </label>
        <label>
          <span>School name *</span>
          <input v-model="form.schoolName" type="text" placeholder="Mountain View High School" required />
        </label>
        <label>
          <span>Short name</span>
          <input v-model="form.shortName" type="text" placeholder="MVHS" />
        </label>
        <label>
          <span>Timezone</span>
          <input v-model="form.timezone" type="text" placeholder="America/Los_Angeles" />
        </label>
      </div>
      <div class="form-actions">
        <p v-if="formError" class="form-feedback error">{{ formError }}</p>
        <button type="submit" class="btn primary" :disabled="saving">
          {{ saving ? 'Creating…' : 'Create school' }}
        </button>
      </div>
    </form>

    <section v-if="schools.length" class="school-list">
      <article v-for="school in schools" :key="school.slug" class="school-row">
        <div class="school-info">
          <h2>{{ school.schoolName }}</h2>
          <p class="school-slug">/schools/{{ school.slug }}</p>
          <div class="school-meta">
            <span class="badge" :class="school.status === 'active' ? 'badge-active' : 'badge-pending'">
              {{ school.status }}
            </span>
            <span v-if="school.shortName" class="meta-text">{{ school.shortName }}</span>
            <span class="meta-text">{{ school.timezone }}</span>
          </div>
        </div>
        <div class="school-actions">
          <RouterLink :to="`/schools/${school.slug}`" class="btn ghost small">View</RouterLink>
          <RouterLink :to="`/schools/${school.slug}/admin`" class="btn ghost small">Admin</RouterLink>
        </div>
      </article>
    </section>

    <section v-else-if="!loading" class="empty-state">
      <p>No schools have been created yet. Use the form above to add the first one.</p>
    </section>
  </div>
</template>

<style scoped>
.platform-admin {
  display: flex;
  flex-direction: column;
  gap: clamp(1.5rem, 4vw, 2.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.admin-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.admin-hero h1 { margin: 0.25rem 0 0.5rem; }
.admin-hero p { color: var(--mv-text-muted); max-width: 560px; }

.hero-actions {
  display: flex;
  gap: 0.75rem;
}

.btn {
  padding: 0.65rem 1.25rem;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  border: none;
  text-decoration: none;
}

.btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
}

.btn.ghost {
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
}

.btn.small { padding: 0.4rem 1rem; font-size: 0.85rem; }

.create-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem;
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card-strong);
}

.create-form h2 { margin: 0; }

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-grid span {
  font-size: 0.85rem;
  color: var(--mv-text-soft);
}

.form-grid input {
  padding: 0.55rem 0.75rem;
  border-radius: 12px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  color: var(--mv-text);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.form-feedback.error { color: var(--mv-status-danger); font-size: 0.9rem; margin: 0; }

.school-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.school-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
}

.school-info h2 { margin: 0; font-size: 1.1rem; }
.school-slug {
  margin: 0.25rem 0 0.5rem;
  font-size: 0.85rem;
  color: var(--mv-text-faint);
  font-family: monospace;
}

.school-meta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

.badge {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-active { background: rgba(34,197,94,0.12); color: var(--mv-status-success); }
.badge-pending { background: rgba(250,204,21,0.15); color: var(--mv-gold); }

.meta-text { font-size: 0.8rem; color: var(--mv-text-faint); }

.school-actions { display: flex; gap: 0.5rem; }

.status-card {
  padding: 0.9rem 1.25rem;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
}

.status-card.error {
  border-color: rgba(239,68,68,0.35);
  color: var(--mv-status-danger);
}

.empty-state {
  padding: 2rem;
  text-align: center;
  border-radius: 20px;
  border: 1px dashed var(--mv-border-strong);
  background: var(--mv-surface-muted);
  color: var(--mv-text-muted);
}
</style>
