<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from '../stores/auth'
import { updateGraduationYear } from '../services/userService'

const quickLinks = [
  { label: 'Complete profile', description: 'Add grade, contact info, and interests.' },
  { label: 'My clubs', description: 'Review clubs you follow or applied to.' },
  { label: 'Inbox', description: 'Stay on top of announcements and approvals.' },
]

const reminders = [
  'Create an account to save favorite clubs.',
  'Sign in to track application status and event check-ins.',
  'Keeping details current helps advisors reach you quickly.',
]


const route = useRoute()
const schoolSlug = computed(() => {
  const slug = route.params.schoolSlug
  return typeof slug === 'string' ? slug : undefined
})

const authStore = useAuthStore()
const { isAuthenticated, currentUser, userLoading, userError } = storeToRefs(authStore)

const handleLogout = () => authStore.logout()

const graduationYearOptions = computed(() => {
  const currentYear = new Date().getFullYear()
  return Array.from({ length: 4 }, (_, index) => currentYear + index)
})

const selectedGraduationYear = ref<string>('')
const graduationYearError = ref<string | null>(null)
const graduationYearSuccess = ref<string | null>(null)
const graduationYearSaving = ref(false)

watch(
  currentUser,
  (user) => {
    selectedGraduationYear.value = user?.graduationYear ? String(user.graduationYear) : ''
  },
  { immediate: true },
)

watch(selectedGraduationYear, () => {
  graduationYearError.value = null
  graduationYearSuccess.value = null
})

const graduationYearLabel = computed(() => {
  const year = currentUser.value?.graduationYear
  if (!year) {
    return 'Not shared'
  }
  return `${year} (Graduation year)`
})

const handleGraduationYearSave = async () => {
  if (!selectedGraduationYear.value) {
    graduationYearError.value = 'Please select your graduation year.'
    graduationYearSuccess.value = null
    return
  }

  const year = Number(selectedGraduationYear.value)
  if (Number.isNaN(year)) {
    graduationYearError.value = 'Please select a valid graduation year.'
    graduationYearSuccess.value = null
    return
  }

  graduationYearError.value = null
  graduationYearSuccess.value = null
  graduationYearSaving.value = true
  try {
    await updateGraduationYear(year)
    await authStore.refreshUser()
    graduationYearSuccess.value = 'Graduation year saved.'
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unable to save your graduation year.'
    graduationYearError.value = message
  } finally {
    graduationYearSaving.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <div v-if="userError" class="page-shell">
      <p class="alert error">{{ userError }}</p>
    </div>
    <div v-else-if="userLoading" class="page-shell">
      <p class="alert muted">Checking your session…</p>
    </div>

    <div v-if="isAuthenticated" class="profile page-shell">
      <section class="profile-hero">
        <div class="hero-copy">
          <p class="section-label">Personal hub</p>
          <h1>Welcome back, {{ currentUser?.displayName || 'Explorer' }}</h1>
          <p>
            You are signed in with {{ currentUser?.provider || 'your OAuth provider' }}. Keep club applications, favorites, and announcements synced
            everywhere.
          </p>
          <div class="user-meta">
            <div>
              <span>Email</span>
              <strong>{{ currentUser?.email || 'Not shared' }}</strong>
            </div>
            <div>
              <span>User ID</span>
              <strong>{{ currentUser?.id }}</strong>
            </div>
            <div>
              <span>Graduation year</span>
              <strong>{{ graduationYearLabel }}</strong>
            </div>
          </div>
          <div class="cta-group">
            <RouterLink to="/" class="btn primary">Browse clubs</RouterLink>
            <RouterLink
              v-if="currentUser?.isOwner"
              to="/admin"
              class="btn ghost"
            >Admin dashboard</RouterLink>
            <button type="button" class="btn ghost" @click="handleLogout">Sign out</button>
          </div>
        </div>
        <div class="hero-card">
          <div v-if="currentUser?.avatarUrl" class="avatar">
            <img :src="currentUser.avatarUrl" alt="Profile avatar" />
          </div>
          <div v-else class="avatar">👤</div>
          <p class="hero-card-label">{{ currentUser?.displayName || 'Member' }}</p>
          <p class="hero-card-desc">
            {{ currentUser?.provider ? `Signed in with ${currentUser.provider}` : 'Authenticated user' }}
          </p>
        </div>
      </section>

      <section class="profile-grid">
        <article class="card">
          <h2>Shortcuts</h2>
          <p class="card-subtitle">These actions stay in sync with your account.</p>
          <ul class="quick-links">
            <li v-for="link in quickLinks" :key="link.label">
              <h3>{{ link.label }}</h3>
              <p>{{ link.description }}</p>
            </li>
          </ul>
        </article>

        <article class="card graduation-card">
          <h2>Graduation year</h2>
          <p class="card-subtitle">Share when you plan to finish high school.</p>
          <form class="graduation-form" @submit.prevent="handleGraduationYearSave">
            <label for="graduationYearSelect">Select your graduation year</label>
            <select
              id="graduationYearSelect"
              v-model="selectedGraduationYear"
              :disabled="graduationYearSaving"
            >
              <option value="" disabled>Select your year</option>
              <option v-for="year in graduationYearOptions" :key="year" :value="String(year)">
                Class of {{ year }}
              </option>
            </select>
            <p class="form-hint">Options cover the four active high school classes.</p>
            <div class="form-actions">
              <button type="submit" class="btn primary" :disabled="graduationYearSaving">
                {{ graduationYearSaving ? 'Saving…' : 'Save year' }}
              </button>
              <p v-if="graduationYearError" class="form-feedback error">{{ graduationYearError }}</p>
              <p v-else-if="graduationYearSuccess" class="form-feedback success">{{ graduationYearSuccess }}</p>
            </div>
          </form>
        </article>

        <article class="card reminders">
          <h2>Reminders</h2>
          <ul>
            <li v-for="message in reminders" :key="message">{{ message }}</li>
          </ul>
        </article>
      </section>
    </div>

    <section v-else class="auth-gate page-shell">
      <div class="gate-card">
        <p class="section-label">Profile</p>
        <h1>Sign in to access your personal center</h1>
        <p>
          View saved clubs, manage applications, and keep advisors updated once you authenticate. Create an account or sign in to continue.
        </p>
        <div class="cta-group gate-actions">
          <RouterLink to="/auth?intent=login" class="btn primary">Sign in options</RouterLink>
          <RouterLink to="/auth?intent=register" class="btn ghost">Create account</RouterLink>
        </div>
      </div>
      <ul class="gate-benefits">
        <li v-for="message in reminders" :key="`gate-${message}`">{{ message }}</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.profile-view {
  width: 100%;
}

.profile {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 4vw, 3rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.profile-hero {
  display: flex;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--mv-shadow-card);
}

.hero-copy {
  max-width: 560px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.user-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
}

.user-meta div {
  padding: 0.75rem 1rem;
  border-radius: 18px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
}

.user-meta span {
  display: block;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 0.35rem;
  color: var(--mv-text-dim);
}

.user-meta strong {
  font-size: 1rem;
  font-weight: 600;
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3rem);
}

.cta-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.btn {
  padding: 0.85rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  text-decoration: none;
  transition: transform 0.2s, box-shadow 0.2s;
}

.btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
}

.btn.ghost {
  border: 1px solid var(--mv-ghost-border);
  color: var(--mv-ghost-text);
  background: var(--mv-surface-muted);
}

.btn:hover {
  transform: translateY(-1px);
}

.hero-card {
  min-width: 220px;
  flex: 1;
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  padding: 2rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 25px;
  margin: 0 auto;
  background: var(--mv-surface-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-card-label {
  font-weight: 600;
}

.hero-card-desc {
  margin: 0;
  color: var(--mv-text-faint);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.5rem;
}

.card {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card-strong);
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: var(--mv-shadow-card);
}

.card h2 {
  margin: 0;
}

.card-subtitle {
  margin: 0;
  color: var(--mv-text-faint);
}

.quick-links {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.quick-links li {
  padding: 1rem;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
}

.quick-links h3 {
  margin: 0 0 0.35rem;
}

.quick-links p {
  margin: 0;
  color: var(--mv-text-faint);
}

.graduation-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.graduation-form label {
  font-weight: 600;
  color: var(--mv-text-soft);
}

.graduation-form select {
  border-radius: 14px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  color: var(--mv-text);
  padding: 0.65rem 0.75rem;
  font-size: 1rem;
}

.graduation-form select:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-hint {
  margin: 0;
  font-size: 0.9rem;
  color: var(--mv-text-dim);
}

.form-actions {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  align-items: flex-start;
}

.form-feedback {
  margin: 0;
  font-size: 0.9rem;
}

.form-feedback.error {
  color: var(--mv-status-danger);
}

.form-feedback.success {
  color: var(--mv-status-success);
}

.reminders ul {
  list-style: disc;
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.reminders li {
  color: var(--mv-text-faint);
}

.auth-gate {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.gate-card {
  border-radius: 32px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  padding: clamp(1.75rem, 4vw, 3rem);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: var(--mv-shadow-elevated);
}

.gate-actions {
  margin-top: 0.5rem;
}

.gate-benefits {
  list-style: disc;
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: 1.5rem 2rem;
  background: var(--mv-surface-card);
  color: var(--mv-text-soft);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.gate-benefits li {
  margin-left: 1rem;
}

.alert {
  margin: 1rem 0 0;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  border: 1px solid transparent;
}

.alert.error {
  border-color: rgba(239, 68, 68, 0.35);
  background: var(--mv-surface-danger);
  color: var(--mv-status-danger);
}

.alert.muted {
  border-color: var(--mv-border);
  background: var(--mv-surface-soft);
  color: var(--mv-text-soft);
}

@media (max-width: 640px) {
  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-card {
    width: 100%;
  }
}
</style>
