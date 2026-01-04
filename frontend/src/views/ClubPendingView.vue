<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import {
  approveMembershipRequest,
  fetchClubById,
  fetchMembershipRequests,
  rejectMembershipRequest,
} from '../services/clubService'
import type { Club, ClubMembershipRequest } from '../types/club'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const club = ref<Club | null>(null)
const loading = ref(true)
const loadError = ref('')
const pendingRequests = ref<ClubMembershipRequest[]>([])
const pendingLoading = ref(false)
const pendingError = ref('')
const approvalError = ref('')
const approvingRequestId = ref<number | null>(null)
const decliningRequestId = ref<number | null>(null)

const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)
const canManageMembers = computed(
  () => Boolean(club.value?.canManage) || Boolean(currentUser.value?.isOwner)
)

const clubImage = (entity: Club) =>
  entity.imageUrl ?? `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(entity.name)}`

const loadPendingRequests = async (id: string) => {
  if (!id || !canManageMembers.value) {
    pendingRequests.value = []
    pendingLoading.value = false
    return
  }
  pendingLoading.value = true
  pendingError.value = ''
  approvalError.value = ''
  try {
    pendingRequests.value = await fetchMembershipRequests(id)
  } catch (err) {
    pendingError.value = err instanceof Error ? err.message : 'Failed to load pending requests'
    pendingRequests.value = []
  } finally {
    pendingLoading.value = false
  }
}

const loadClub = async (id: string) => {
  loading.value = true
  loadError.value = ''
  try {
    const response = await fetchClubById(id)
    club.value = response
    if (canManageMembers.value) {
      await loadPendingRequests(id)
    } else {
      pendingRequests.value = []
      pendingError.value = ''
      approvalError.value = ''
    }
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : 'Failed to load club details'
    club.value = null
    pendingRequests.value = []
  } finally {
    loading.value = false
  }
}

const refreshRequests = () => {
  if (club.value && canManageMembers.value) {
    void loadPendingRequests(String(club.value.id))
  }
}

const approveRequest = async (requestId: number) => {
  if (!club.value || !canManageMembers.value || approvingRequestId.value === requestId) {
    return
  }
  approvalError.value = ''
  pendingError.value = ''
  approvingRequestId.value = requestId
  try {
    await approveMembershipRequest(club.value.id, requestId)
    await loadPendingRequests(String(club.value.id))
  } catch (err) {
    approvalError.value = err instanceof Error ? err.message : 'Failed to approve request'
  } finally {
    approvingRequestId.value = null
  }
}

const rejectRequest = async (requestId: number) => {
  if (!club.value || !canManageMembers.value || decliningRequestId.value === requestId) {
    return
  }
  approvalError.value = ''
  pendingError.value = ''
  decliningRequestId.value = requestId
  try {
    await rejectMembershipRequest(club.value.id, requestId)
    await loadPendingRequests(String(club.value.id))
  } catch (err) {
    approvalError.value = err instanceof Error ? err.message : 'Failed to decline request'
  } finally {
    decliningRequestId.value = null
  }
}

const formatRequestTimestamp = (isoString: string) => {
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) {
    return 'Requested recently'
  }
  return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

const retryLoad = () => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (id) {
    void loadClub(id)
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

watch(
  canManageMembers,
  (allowed) => {
    if (allowed && club.value) {
      void loadPendingRequests(String(club.value.id))
    }
    if (!allowed) {
      pendingRequests.value = []
    }
  }
)
</script>

<template>
  <section class="pending-admin page-shell">
    <div class="admin-toolbar">
      <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
      <div class="toolbar-actions" v-if="club">
        <RouterLink :to="`/clubs/${club.id}`" class="ghost-btn">View public page</RouterLink>
        <RouterLink :to="`/clubs/${club.id}/admin`" class="ghost-btn">Club settings</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">Loading pending requests…</div>
    <div v-else-if="loadError" class="status-card error">
      <p>{{ loadError }}</p>
      <button type="button" class="ghost-btn" @click="retryLoad" :disabled="loading">Try again</button>
    </div>
    <template v-else-if="club">
      <header class="pending-hero">
        <div>
          <p class="section-label">Approvals · {{ club.name }}</p>
          <h1>Pending membership requests</h1>
          <p class="hero-meta">Only visible to club leaders and site owners.</p>
        </div>
        <div class="hero-side">
          <div class="club-avatar xlarge">
            <img :src="clubImage(club)" :alt="`${club.name} avatar`" />
          </div>
          <span class="club-id">Club ID · {{ club.id }}</span>
        </div>
      </header>

      <div class="status-row" v-if="approvalError || pendingError">
        <p v-if="approvalError" class="pill error" role="alert">{{ approvalError }}</p>
        <p v-if="pendingError" class="pill error" role="alert">{{ pendingError }}</p>
      </div>

      <section class="pending-panel">
        <div class="pending-panel__header">
          <div>
            <h2>Pending approvals</h2>
            <p>Applications awaiting a decision.</p>
          </div>
          <button
            type="button"
            class="ghost-btn"
            @click="refreshRequests"
            :disabled="pendingLoading || !canManageMembers"
          >
            {{ pendingLoading ? 'Refreshing…' : 'Refresh list' }}
          </button>
        </div>

        <p v-if="!canManageMembers" class="member-hint">
          Only club leaders or site owners can manage membership requests.
        </p>

        <div v-else>
          <div v-if="pendingLoading" class="status-card">Loading requests…</div>
          <ul v-else-if="pendingRequests.length" class="member-list pending-list">
            <li v-for="request in pendingRequests" :key="request.id" class="member-entry pending-entry">
              <div class="member-avatar">
                <img
                  :src="request.avatarUrl || 'https://api.dicebear.com/7.x/thumbs/svg?seed=' + encodeURIComponent(request.displayName || 'Member')"
                  :alt="request.displayName || 'Pending member'"
                />
              </div>
              <div class="member-info">
                <p>{{ request.displayName || 'Pending member' }}</p>
                <small>{{ request.email || 'No email on file' }}</small>
                <small class="request-meta">Requested {{ formatRequestTimestamp(request.createdAt) }}</small>
              </div>
              <div class="pending-actions">
                <button
                  type="button"
                  class="ghost-btn danger"
                  @click="rejectRequest(request.id)"
                  :disabled="decliningRequestId === request.id"
                >
                  {{ decliningRequestId === request.id ? 'Declining…' : 'Decline' }}
                </button>
                <button
                  type="button"
                  class="primary-btn"
                  @click="approveRequest(request.id)"
                  :disabled="approvingRequestId === request.id"
                >
                  {{ approvingRequestId === request.id ? 'Approving…' : 'Approve' }}
                </button>
              </div>
            </li>
          </ul>
          <p v-else class="member-empty">No open requests right now.</p>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.pending-admin {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.admin-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.back-link {
  color: rgba(254, 252, 232, 0.85);
  text-decoration: none;
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.ghost-btn,
.primary-btn {
  border-radius: 999px;
  padding: 0.55rem 1.4rem;
  font-weight: 600;
  border: 1px solid rgba(250, 204, 21, 0.35);
  background: transparent;
  color: rgba(254, 252, 232, 0.85);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.ghost-btn.danger {
  border-color: rgba(248, 113, 113, 0.35);
  color: #fecaca;
}

.ghost-btn:disabled,
.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.primary-btn {
  background: rgba(250, 204, 21, 0.15);
  color: #facc15;
}

.status-card {
  border-radius: 20px;
  border: 1px solid rgba(254, 252, 232, 0.2);
  padding: 1rem 1.25rem;
  background: rgba(12, 12, 12, 0.85);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.35);
  color: #fecaca;
}

.pending-hero {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  align-items: center;
  background: rgba(7, 7, 7, 0.92);
  justify-content: space-between;
}

.section-label {
  margin: 0;
  color: rgba(254, 252, 232, 0.65);
  font-size: 0.9rem;
  font-weight: 600;
}

.pending-hero h1 {
  margin: 0.35rem 0 0.5rem;
}

.hero-meta {
  margin: 0;
  color: rgba(254, 252, 232, 0.7);
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  align-items: flex-end;
}

.club-avatar {
  width: 80px;
  height: 80px;
  border-radius: 22px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.35);
  background: rgba(253, 224, 71, 0.12);
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.club-id {
  color: rgba(254, 252, 232, 0.65);
  font-size: 0.9rem;
}

.status-row {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.pill {
  border-radius: 999px;
  padding: 0.4rem 1rem;
  border: 1px solid rgba(254, 252, 232, 0.2);
}

.pill.error {
  border-color: rgba(248, 113, 113, 0.35);
  color: #fecaca;
}

.pending-panel {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: rgba(7, 7, 7, 0.92);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.pending-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
}

.pending-panel__header h2 {
  margin: 0;
}

.pending-panel__header p {
  margin: 0;
  color: rgba(254, 252, 232, 0.7);
}

.member-hint,
.member-empty {
  margin: 0;
  color: rgba(254, 252, 232, 0.75);
}

.member-hint.error {
  color: #fecaca;
}

.member-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.member-entry {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.75rem 0;
  border-bottom: 1px solid rgba(254, 252, 232, 0.08);
}

.member-entry:last-child {
  border-bottom: none;
}

.pending-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.member-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: rgba(253, 224, 71, 0.08);
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-info p {
  margin: 0;
  font-weight: 600;
}

.member-info small {
  display: block;
  color: rgba(254, 252, 232, 0.65);
}

.request-meta {
  color: rgba(254, 252, 232, 0.55);
}

@media (max-width: 900px) {
  .pending-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-side {
    align-items: flex-start;
  }
}
</style>
