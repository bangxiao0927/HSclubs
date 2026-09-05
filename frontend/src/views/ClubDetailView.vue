<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute } from 'vue-router'
import { applyToClub, cancelMembershipRequest, fetchAllClubs, fetchClubById } from '../services/clubService'
import type { Club } from '../types/club'
import { useAuthStore } from '../stores/auth'
import { clubImage } from '../utils/clubImages'
import BackButton from '../components/BackButton.vue'
import ClubMediaView from './ClubMediaView.vue'
import { createViewSessionOwner } from '../utils/viewSession'

const route = useRoute()
const club = ref<Club | null>(null)
const mediaSectionRef = ref<HTMLElement | null>(null)
const relatedClubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')
const joining = ref(false)
const joinError = ref('')
const joinSuccess = ref('')
const canceling = ref(false)

// Related-club links in the sidebar change route.params.id without unmounting this view, so a
// slow load for club A can still be in flight when a fast load for club B (navigated to right
// after) resolves first. Without a guard, A's response would land last and overwrite B's data
// while the URL still says B -- and that stale record would also be handed to the embedded
// media view as its snapshot. One session per loadClub() call keeps only the newest load's
// writes on screen; see viewSession.ts for the general rule this follows.
const clubSessions = createViewSessionOwner()

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

// The legacy /clubs/:id/media route redirects here with a #media hash (see router/index.ts),
// but the browser's native anchor jump fires before this section exists in the DOM -- club
// detail loads asynchronously and the #media section is behind `v-if="club"`. So once the
// club has loaded and Vue has flushed that DOM update, jump to it ourselves. Guarded for
// jsdom, where elements exist but scrollIntoView is not implemented.
const scrollToMediaSection = async () => {
  const session = clubSessions.current()
  if (route.hash !== '#media') {
    return
  }
  await nextTick()
  if (!session.isCurrent) {
    return
  }
  const target = mediaSectionRef.value
  if (target && typeof target.scrollIntoView === 'function') {
    target.scrollIntoView()
  }
}

const loadClub = async (id: string) => {
  const session = clubSessions.begin()
  session.apply(() => {
    loading.value = true
    error.value = ''
    club.value = null
    joinError.value = ''
    joinSuccess.value = ''
  })
  try {
    const [clubResponse, allClubs] = await Promise.all([fetchClubById(id), fetchAllClubs()])
    session.apply(() => {
      club.value = clubResponse
      relatedClubs.value = allClubs.filter((item) => item.id !== clubResponse.id).slice(0, 3)
    })
  } catch (err) {
    session.apply(() => {
      error.value = err instanceof Error ? err.message : 'Failed to load club'
      relatedClubs.value = []
    })
  } finally {
    session.apply(() => {
      loading.value = false
    })
  }
  if (session.isCurrent && club.value) {
    await scrollToMediaSection()
  }
}

const refreshClubSnapshot = async () => {
  if (!club.value) {
    return
  }
  const session = clubSessions.current()
  const clubId = String(club.value.id)
  try {
    const response = await fetchClubById(clubId)
    session.apply(() => {
      club.value = response
    })
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
    await applyToClub(club.value.id)
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
    await cancelMembershipRequest(club.value.id)
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

// A navigation that only changes the hash (e.g. pressing a "jump to media"
// link, or a browser Back/Forward step within the same club) does not touch
// route.params.id, so the watch above never re-runs and the club is not
// refetched. Scroll independently whenever the hash becomes #media on a club
// that is already loaded.
watch(
  () => route.hash,
  () => {
    void scrollToMediaSection()
  }
)

onBeforeUnmount(() => {
  clubSessions.end()
})
</script>

<template>
  <section v-if="loading" class="club-detail page-shell empty-state">
    <BackButton>← Back to clubs</BackButton>
    <p>Loading club details…</p>
  </section>

  <section v-else-if="error" class="club-detail page-shell empty-state">
    <BackButton>← Back to clubs</BackButton>
    <h1>Unable to load club</h1>
    <p>{{ error }}</p>
  </section>

  <section class="club-detail page-shell" v-else-if="club">
    <BackButton>← Back to clubs</BackButton>

    <header class="club-hero">
      <div class="hero-top">
        <div class="hero-main">
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
            :to="`/clubs/${club.id}/admin`"
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
          {{ club.description }} Use this section to share expectations, recruiting notes,
          showcase plans, and advisor guidance for this club.
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
            <RouterLink :to="`/clubs/${item.id}`" class="related-link">
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

    <section id="media" ref="mediaSectionRef" class="club-media-section">
      <ClubMediaView embedded :snapshot="club" />
    </section>
  </section>

  <section v-else class="club-detail page-shell empty-state">
    <BackButton>← Back to clubs</BackButton>
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

.hero-main {
  flex: 1 1 320px;
  min-width: 0;
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
  flex-direction: column;
  gap: 0.5rem;
  align-items: stretch;
}

/* One full-width, solid-color bar: the primary action on the page has to be
   unmistakable on a phone, where it sits between the description and the stats. */
.apply-btn {
  width: 100%;
  min-height: 56px;
  padding: 0.9rem 1.5rem;
  border: 0;
  border-radius: 16px;
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    filter 0.15s ease;
}

.apply-btn:hover:not(:disabled),
.apply-btn:focus-visible {
  filter: brightness(1.05);
  transform: translateY(-1px);
}

.apply-btn:focus-visible {
  outline: 3px solid var(--mv-gold);
  outline-offset: 3px;
}

.apply-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  width: 100%;
  min-height: 52px;
  border-radius: 16px;
  border: 2px solid rgba(239, 68, 68, 0.55);
  background: var(--mv-surface-danger);
  color: var(--mv-status-danger);
  font-size: 1rem;
  font-weight: 700;
  padding: 0.75rem 1.5rem;
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

.club-media-section {
  border-top: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  padding-inline: 0;
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
  .admin-link {
    padding: 0.35rem 0.85rem;
    font-size: 0.85rem;
  }
}

@media (max-width: 720px) {
  .club-hero {
    padding: 1.5rem;
    border-radius: 24px;
  }

  .hero-top {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  /* Avatar and the manage link ride on one row above the club name, so the
     apply button below stays the first thing a thumb reaches. */
  .hero-side {
    order: -1;
    width: 100%;
    flex-direction: row;
    align-items: flex-start;
    justify-content: space-between;
  }

  .hero-main {
    width: 100%;
  }

  .section-label {
    font-size: 0.72rem;
  }

  .club-avatar.xlarge {
    width: 72px;
    height: 72px;
    border-radius: 20px;
  }

  .hero-description {
    font-size: 0.92rem;
  }

  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.6rem;
  }

  .stat-card {
    padding: 0.7rem 0.8rem;
    border-radius: 14px;
  }

  .stat-label {
    font-size: 0.72rem;
  }

  .stat-value {
    margin-top: 0.2rem;
    font-size: 0.92rem;
  }

  .instagram-icon {
    width: 20px;
    height: 20px;
  }

  .spotlight {
    padding: 1.25rem;
    border-radius: 22px;
  }

  .spotlight button {
    align-self: stretch;
    min-height: 48px;
  }

  .related {
    padding: 1.25rem;
    border-radius: 20px;
  }

  .club-media-section {
    padding-block: 1.25rem;
  }
}

@media (max-width: 480px) {
  .club-hero {
    padding: 1.15rem;
    border-radius: 20px;
  }

  .club-hero h1 {
    font-size: 1.45rem;
  }

  .hero-meta {
    font-size: 0.8rem;
  }

  .hero-description {
    font-size: 0.88rem;
  }

  .back-link {
    font-size: 0.9rem;
  }
}
</style>
