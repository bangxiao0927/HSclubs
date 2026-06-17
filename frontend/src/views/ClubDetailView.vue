<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import { applyToClub, cancelMembershipRequest, fetchClubById, fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { useAuthStore } from '../stores/auth'
import { clubImage } from '../utils/clubImages'

const route = useRoute()
const club = ref<Club | null>(null)
const relatedClubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')
const joining = ref(false)
const joinError = ref('')
const joinSuccess = ref('')
const canceling = ref(false)

const canApply = computed(
  () =>
    Boolean(
      isAuthenticated.value &&
        club.value &&
        !club.value.viewerIsMember &&
        !club.value.viewerHasPendingRequest
    )
)
const hasPendingRequest = computed(() => Boolean(club.value?.viewerHasPendingRequest))


const schoolSlug = computed(() => {
  const slug = route.params.schoolSlug
  return typeof slug === 'string' ? slug : undefined
})

const authStore = useAuthStore()
const { currentUser, isAuthenticated } = storeToRefs(authStore)
const isOwner = computed(() => Boolean(currentUser.value?.isOwner))

const instagramHandle = (url?: string | null) => {
  if (!url) {
    return ''
  }
  const normalized = url.trim().replace(/\/+$/, '')
  const parts = normalized.split('/')
  const lastPart = parts[parts.length - 1] ?? ''
  return lastPart.startsWith('@') ? lastPart : `@${lastPart}`
}

const loadClub = async (id: string) => {
  loading.value = true
  error.value = ''
  club.value = null
  joinError.value = ''
  joinSuccess.value = ''
  try {
    const [clubResponse, allClubs] = await Promise.all([fetchClubById(id, schoolSlug.value), fetchClubs()])
    club.value = clubResponse
    relatedClubs.value = allClubs.filter((item) => item.id !== clubResponse.id).slice(0, 3)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load club'
    relatedClubs.value = []
  } finally {
    loading.value = false
  }
}

const refreshClubSnapshot = async () => {
  if (!club.value) {
    return
  }
  try {
    club.value = await fetchClubById(String(club.value.id), schoolSlug.value)
  } catch (err) {
    console.error(err)
  }
}

const handleApply = async () => {
  if (!club.value || !canApply.value || joining.value) {
    return
  }
  joining.value = true
  joinError.value = ''
  joinSuccess.value = ''
  try {
    await applyToClub(club.value.id, schoolSlug.value)
    joinSuccess.value = 'Request received. A club lead will reach out soon.'
    await refreshClubSnapshot()
  } catch (err) {
    joinError.value = err instanceof Error ? err.message : 'Unable to submit your request'
  } finally {
    joining.value = false
  }
}

const handleCancelRequest = async () => {
  if (!club.value || !hasPendingRequest.value || canceling.value) {
    return
  }
  canceling.value = true
  joinError.value = ''
  joinSuccess.value = ''
  try {
    await cancelMembershipRequest(club.value.id, schoolSlug.value)
    joinSuccess.value = 'Request withdrawn. You can apply again any time.'
    await refreshClubSnapshot()
  } catch (err) {
    joinError.value = err instanceof Error ? err.message : 'Unable to cancel your request'
  } finally {
    canceling.value = false
  }
}

watch(
  () => route.params.id,
  (newId) => {
    if (typeof newId === 'string' && newId) {
      void loadClub(newId)
    }
  },
  { immediate: true }
)
</script>

<template>
  <section v-if="loading" class="club-detail page-shell empty-state">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
    <p>Loading club details…</p>
  </section>

  <section v-else-if="error" class="club-detail page-shell empty-state">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
    <h1>Unable to load club</h1>
    <p>{{ error }}</p>
  </section>

  <section class="club-detail page-shell" v-else-if="club">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>

    <header class="club-hero">
      <div class="hero-top">
        <div>
          <p class="section-label">{{ club.category }}</p>
          <h1>{{ club.name }}</h1>
          <p class="hero-meta">
            {{ club.meetingSchedule }} · Advisor {{ club.advisor || 'TBD' }} · {{ club.memberCount }} members
          </p>
          <p class="hero-description">{{ club.description }}</p>
          <div class="hero-actions" v-if="canApply || joinSuccess || joinError || hasPendingRequest">
            <button v-if="canApply" type="button" class="apply-btn" @click="handleApply" :disabled="joining">
              {{ joining ? 'Sending…' : 'Apply to join' }}
            </button>
            <button
              v-else-if="hasPendingRequest"
              type="button"
              class="cancel-btn"
              @click="handleCancelRequest"
              :disabled="canceling"
            >
              {{ canceling ? 'Canceling…' : 'Withdraw request' }}
            </button>
            <p v-if="hasPendingRequest" class="join-message info">Your application is waiting for approval.</p>
            <p v-if="joinSuccess" class="join-message success">{{ joinSuccess }}</p>
            <p v-if="joinError" class="join-message error">{{ joinError }}</p>
          </div>
        </div>
        <div class="hero-side">
          <RouterLink
            v-if="club.canManage || isOwner"
            :to="schoolSlug ? `/schools/${schoolSlug}/clubs/${club.id}/admin` : `/clubs/${club.id}/admin`"
            class="admin-link"
          >
            Manage club
          </RouterLink>
          <div class="club-avatar xlarge">
            <img :src="clubImage(club)" :alt="`${club.name} avatar`" />
          </div>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-card">
          <span class="stat-label">Members</span>
          <p class="stat-value">{{ club.memberCount }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Primary advisor</span>
          <p class="stat-value">{{ club.advisor || 'Unassigned' }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Contact</span>
          <p class="stat-value">{{ club.contactEmail || 'Not provided' }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Room</span>
          <p class="stat-value">{{ club.location || 'TBD' }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Instagram</span>
          <p class="stat-value">
            <a
              v-if="club.instagramUrl"
              class="stat-link"
              :href="club.instagramUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <span class="instagram-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" role="img" focusable="false">
                  <rect x="4" y="4" width="16" height="16" rx="4" ry="4" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="17" cy="7" r="1.2" fill="currentColor" />
                </svg>
              </span>
              {{ instagramHandle(club.instagramUrl) }}
            </a>
            <span v-else>Not provided</span>
          </p>
        </div>
      </div>
    </header>

    <section class="club-body">
      <div class="spotlight">
        <h2>What we run</h2>
        <p>
          {{ club.description }} Use this guide to align with Mountain View's activities office, track recruiting, and prep
          for showcases.
        </p>
        <h3>Recent achievements</h3>
        <div v-if="club.scheduleNote" class="schedule-note">
          <h3>President update</h3>
          <p>{{ club.scheduleNote }}</p>
        </div>
        <ul v-if="club.achievements && club.achievements.length">
          <li v-for="achievement in club.achievements" :key="achievement">
            {{ achievement }}
          </li>
        </ul>
        <p v-else>No achievements logged yet.</p>
        <button type="button" :disabled="!club.contactEmail">
          Email {{ club.contactEmail || 'advisor' }} →
        </button>
      </div>

      <aside class="related" v-if="relatedClubs.length">
        <h3>Also trending</h3>
        <ul>
          <li v-for="item in relatedClubs" :key="item.id">
            <RouterLink :to="schoolSlug ? `/schools/${schoolSlug}/clubs/${item.id}` : `/clubs/${item.id}`" class="related-link">
              <div class="club-avatar small">
                <img :src="clubImage(item)" :alt="`${item.name} avatar`" loading="lazy" />
              </div>
              <div>
                <span>{{ item.name }}</span>
                <small>{{ item.memberCount }} members</small>
              </div>
            </RouterLink>
          </li>
        </ul>
      </aside>
    </section>
  </section>

  <section v-else class="club-detail page-shell empty-state">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
    <h1>Club not found</h1>
    <p>The club you requested is unavailable. Pick another entry from the directory.</p>
  </section>
</template>

<style scoped>
.club-detail {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.back-link {
  color: var(--mv-text-faint);
  font-weight: 600;
}

.club-hero {
  border-radius: 32px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 3rem);
  background: var(--mv-surface-hero);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  box-shadow: var(--mv-shadow-card);
}

.hero-top {
  display: flex;
  gap: 1.5rem;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}

.hero-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.75rem;
}

.hero-actions {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.apply-btn {
  border-radius: 999px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
  font-weight: 600;
  padding: 0.5rem 1.6rem;
  cursor: pointer;
}

.apply-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  border-radius: 999px;
  border: 1px solid rgba(239, 68, 68, 0.4);
  background: transparent;
  color: var(--mv-status-danger);
  font-weight: 600;
  padding: 0.5rem 1.6rem;
  cursor: pointer;
}

.cancel-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.join-message {
  margin: 0;
  font-size: 0.9rem;
}

.join-message.success {
  color: var(--mv-status-success);
}

.join-message.error {
  color: var(--mv-status-danger);
}

.join-message.info {
  color: var(--mv-text-soft);
}

.admin-link {
  border: 1px solid var(--mv-border-strong);
  border-radius: 999px;
  padding: 0.4rem 1rem;
  text-decoration: none;
  color: var(--mv-gold);
  font-weight: 600;
  background: var(--mv-surface-muted);
}

.club-hero h1 {
  margin: 0.25rem 0 0.5rem;
  font-size: clamp(2rem, 4vw, 3.2rem);
}

.hero-meta {
  margin: 0;
  color: var(--mv-text-faint);
}

.hero-description {
  color: var(--mv-text-muted);
}

.club-avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-accent);
  flex-shrink: 0;
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.club-avatar.xlarge {
  width: 120px;
  height: 120px;
  border-radius: 28px;
}

.club-avatar.small {
  width: 48px;
  height: 48px;
  border-radius: 14px;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-card {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  padding: 1.2rem;
  background: var(--mv-surface-soft);
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
}

.stat-label {
  font-size: 0.85rem;
  color: var(--mv-text-faint);
}

.stat-value {
  margin: 0.4rem 0 0;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--mv-gold);
  line-height: 1.35;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.stat-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--mv-gold);
  text-decoration: none;
}

.stat-link:hover,
.stat-link:focus-visible {
  text-decoration: underline;
}

.instagram-icon {
  width: 35px;
  height: 35px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.instagram-icon svg {
  width: 100%;
  height: 100%;
}

.club-body {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: clamp(1rem, 4vw, 2rem);
  align-items: flex-start;
}

.spotlight {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: var(--mv-surface-card-strong);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: var(--mv-shadow-card);
}

.spotlight ul {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--mv-text);
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.schedule-note {
  border-radius: 18px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  padding: 1rem 1.1rem;
}

.schedule-note h3,
.schedule-note p {
  margin: 0;
}

.schedule-note p {
  margin-top: 0.4rem;
  color: var(--mv-text-soft);
  white-space: pre-wrap;
}

.spotlight button {
  align-self: flex-start;
  border-radius: 20px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
  padding: 0.65rem 1.6rem;
  font-weight: 600;
  cursor: pointer;
}

.spotlight button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.related {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  padding: 1.5rem;
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-card);
}

.related ul {
  list-style: none;
  margin: 1rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}


.related-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: inherit;
}

.related small {
  color: var(--mv-text-dim);
}

.empty-state {
  gap: 0.75rem;
}

@media (max-width: 900px) {
  .club-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-side {
    width: 100%;
    align-items: flex-start;
  }

  .admin-link {
    width: 100%;
    text-align: center;
  }
}
</style>
