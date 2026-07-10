<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from '../stores/auth'
import { updateGraduationYear, fetchMyClubs, fetchMyMembershipRequests } from '../services/userService'
import type { Club, ClubMembershipRequest } from '../types/club'
import { clubImage } from '../utils/clubImages'

const reminders = [
  'Create an account to save favorite clubs.',
  'Sign in to track application status and event check-ins.',
  'Keeping details current helps advisors reach you quickly.',
]

const authStore = useAuthStore()
const { isAuthenticated, currentUser, userLoading, userError } = storeToRefs(authStore)

const handleLogout = () => authStore.logout()

// ---- My Clubs ----
const myClubs = ref<Club[]>([])
const myClubsLoading = ref(false)
const myClubsError = ref('')

const loadMyClubs = async () => {
  myClubsLoading.value = true
  myClubsError.value = ''
  try {
    myClubs.value = await fetchMyClubs()
  } catch (err) {
    myClubsError.value = err instanceof Error ? err.message : 'Failed to load clubs'
  } finally {
    myClubsLoading.value = false
  }
}

// ---- My Applications ----
const myRequests = ref<ClubMembershipRequest[]>([])
const myRequestsLoading = ref(false)
const myRequestsError = ref('')

const loadMyRequests = async () => {
  myRequestsLoading.value = true
  myRequestsError.value = ''
  try {
    myRequests.value = await fetchMyMembershipRequests()
  } catch (err) {
    myRequestsError.value = err instanceof Error ? err.message : 'Failed to load requests'
  } finally {
    myRequestsLoading.value = false
  }
}

watch(isAuthenticated, (val) => {
  if (val) {
    loadMyClubs()
    loadMyRequests()
  }
})

onMounted(() => {
  if (isAuthenticated.value) {
    loadMyClubs()
    loadMyRequests()
  }
})

// ---- Graduation Year ----
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
  if (!year) return 'Not shared'
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
        <div class="hero-avatar-pane">
          <img
            class="profile-avatar"
            :src="currentUser?.avatarUrl"
            :alt="`${currentUser?.displayName || 'Member'} avatar`"
          />
        </div>
        <div class="hero-copy">
          <p class="section-label">Personal hub</p>
          <h1>Welcome back, {{ currentUser?.displayName || 'Explorer' }}</h1>
          <p>
            You are signed in with {{ currentUser?.provider || 'your OAuth provider' }}.
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
            <div v-if="currentUser?.createdAt">
              <span>Member since</span>
              <strong>{{ new Date(currentUser.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long' }) }}</strong>
            </div>
          </div>
          <div class="cta-group">
            <RouterLink to="/" class="btn primary">Browse clubs</RouterLink>
            <RouterLink
              v-if="currentUser?.isOwner"
              to="/platform/admin"
              class="btn ghost"
            >Platform admin</RouterLink>
            <button type="button" class="btn ghost" @click="handleLogout">Sign out</button>
          </div>
        </div>
      </section>

      <!-- My Clubs -->
      <section class="card">
        <h2>My clubs</h2>
        <p class="card-subtitle">Clubs you've joined.</p>
        <div v-if="myClubsLoading" class="status-muted">Loading…</div>
        <div v-else-if="myClubsError" class="status-error">{{ myClubsError }}</div>
        <ul v-else-if="myClubs.length" class="club-list">
          <li v-for="club in myClubs" :key="club.id">
            <RouterLink :to="`/clubs/${club.id}`" class="club-row">
              <div class="club-avatar small">
                <img :src="clubImage(club)" :alt="club.name" />
              </div>
              <div class="club-info">
                <strong>{{ club.name }}</strong>
                <span>{{ club.category }}</span>
              </div>
              <span class="member-badge">{{ club.memberCount }} members</span>
            </RouterLink>
          </li>
        </ul>
        <p v-else class="empty-hint">You haven't joined any clubs yet. Browse the directory to find one!</p>
      </section>

      <!-- My Applications -->
      <section class="card">
        <h2>Inbox</h2>
        <p class="card-subtitle">Pending membership applications.</p>
        <div v-if="myRequestsLoading" class="status-muted">Loading…</div>
        <div v-else-if="myRequestsError" class="status-error">{{ myRequestsError }}</div>
        <ul v-else-if="myRequests.length" class="request-list">
          <li v-for="req in myRequests" :key="req.id" class="request-row">
            <span class="request-status pending">{{ req.status }}</span>
            <span>Applied to club #{{ req.clubId }}</span>
            <span class="request-date">{{ new Date(req.createdAt).toLocaleDateString() }}</span>
          </li>
        </ul>
        <p v-else class="empty-hint">No pending applications. Apply to a club to see it here!</p>
      </section>

      <!-- Graduation Year -->
      <section class="card graduation-card">
        <h2>Graduation year</h2>
        <p class="card-subtitle">Share when you plan to finish high school.</p>
        <form class="graduation-form" @submit.prevent="handleGraduationYearSave">
          <label for="graduationYearSelect">Select your graduation year</label>
          <select id="graduationYearSelect" v-model="selectedGraduationYear" :disabled="graduationYearSaving">
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
      </section>

      <section class="card reminders">
        <h2>Reminders</h2>
        <ul>
          <li v-for="message in reminders" :key="message">{{ message }}</li>
        </ul>
      </section>
    </div>

    <section v-else class="auth-gate page-shell">
      <div class="gate-card">
        <p class="section-label">Profile</p>
        <h1>Sign in to access your personal center</h1>
        <p>
          View saved clubs, manage applications, and keep advisors updated once you authenticate.
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
.profile-view { width: 100%; }

.profile {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 4vw, 3rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.profile-hero {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(0, 1fr);
  gap: clamp(1.5rem, 3vw, 2.5rem);
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  align-items: center;
  box-shadow: var(--mv-shadow-card);
}

.hero-avatar-pane {
  min-height: clamp(220px, 28vw, 320px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-avatar {
  width: clamp(140px, 20vw, 220px);
  height: clamp(140px, 20vw, 220px);
  border-radius: 32px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  object-fit: cover;
  box-shadow: var(--mv-shadow-elevated);
}

.hero-copy { max-width: 560px; display: flex; flex-direction: column; gap: 1rem; }

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

.user-meta strong { font-size: 1rem; font-weight: 600; }

.hero-copy h1 { margin: 0; font-size: clamp(2rem, 5vw, 3rem); }

.cta-group { display: flex; flex-wrap: wrap; gap: 0.75rem; }

.btn {
  padding: 0.85rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: none;
}

.btn.primary { background: var(--mv-primary-bg); color: var(--mv-primary-text); box-shadow: var(--mv-primary-shadow); }
.btn.ghost { border: 1px solid var(--mv-ghost-border); color: var(--mv-ghost-text); background: var(--mv-surface-muted); }
.btn:hover { transform: translateY(-1px); }

/* Cards */
.card {
  padding: 1.5rem;
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
}

.card h2 { margin: 0; }
.card-subtitle { color: var(--mv-text-muted); margin: 0.25rem 0 1rem; }

/* Club list */
.club-list, .request-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.club-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.75rem;
  border-radius: 14px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  text-decoration: none;
  color: inherit;
  transition: background 0.15s;
}

.club-row:hover { background: var(--mv-surface-accent); }

.club-avatar.small {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--mv-border);
  flex-shrink: 0;
}

.club-avatar.small img { width: 100%; height: 100%; object-fit: cover; }

.club-info { display: flex; flex-direction: column; gap: 0.15rem; flex: 1; }
.club-info strong { font-size: 0.95rem; }
.club-info span { font-size: 0.8rem; color: var(--mv-text-faint); }

.member-badge {
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background: rgba(250,204,21,0.12);
  color: var(--mv-gold);
}

/* Request list */
.request-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 12px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  font-size: 0.9rem;
}

.request-status.pending {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  background: rgba(250,204,21,0.15);
  color: var(--mv-gold);
}

.request-date { margin-left: auto; font-size: 0.8rem; color: var(--mv-text-faint); }

/* Graduation */
.graduation-form { display: flex; flex-direction: column; gap: 0.5rem; max-width: 320px; }

.graduation-form label { font-size: 0.85rem; color: var(--mv-text-soft); }

.graduation-form select {
  padding: 0.55rem 0.75rem;
  border-radius: 12px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  color: var(--mv-text);
}

.form-hint { margin: 0; font-size: 0.8rem; color: var(--mv-text-faint); }

.form-actions { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.5rem; }
.form-feedback { margin: 0; font-size: 0.9rem; }
.form-feedback.error { color: var(--mv-status-danger); }
.form-feedback.success { color: var(--mv-status-success); }

/* Status */
.status-muted { color: var(--mv-text-faint); }
.status-error { color: var(--mv-status-danger); }

.empty-hint { color: var(--mv-text-muted); font-size: 0.9rem; margin: 0; }

/* Reminders */
.reminders ul { padding-left: 1.25rem; margin: 0; display: flex; flex-direction: column; gap: 0.4rem; }
.reminders li { color: var(--mv-text-muted); font-size: 0.9rem; }

/* Auth gate */
.auth-gate { display: flex; flex-direction: column; align-items: center; gap: 2rem; padding-block: 3rem; }
.gate-card { text-align: center; max-width: 480px; }
.gate-card h1 { margin: 0.5rem 0; }
.gate-actions { justify-content: center; }
.gate-benefits { display: flex; flex-direction: column; gap: 0.5rem; color: var(--mv-text-muted); }

@media (max-width: 720px) {
  .profile-hero {
    grid-template-columns: 1fr;
    padding: 1.5rem;
    border-radius: 24px;
  }

  .hero-avatar-pane {
    min-height: 180px;
  }

  .hero-copy {
    max-width: 100%;
  }

  .user-meta {
    grid-template-columns: 1fr;
  }

  .card {
    padding: 1.25rem;
    border-radius: 20px;
  }
}

@media (max-width: 480px) {
  .hero-copy h1 {
    font-size: 1.5rem;
  }

  .cta-group {
    flex-direction: column;
  }

  .cta-group .btn {
    width: 100%;
    text-align: center;
  }

  .gate-actions {
    flex-direction: column;
  }

  .gate-actions .btn {
    width: 100%;
  }
}
</style>
