<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, useRoute } from 'vue-router'
import {
  fetchClubById,
  fetchClubMembers,
  invalidateClubCache,
  updateClub,
  updateClubMemberRole,
} from '../services/clubService'
import { searchUsers, assignPresident, removePresident } from '../services/userService'
import type { Club, ClubMember } from '../types/club'
import type { UserSearchResult } from '../services/userService'
import { useAuthStore } from '../stores/auth'
import { clubCategoryOptions } from '../utils/clubCategories'
import { buildApiUrl } from '../services/httpClient'
import { resolveErrorMessage } from '../services/httpErrorMessage'
import { clubImage } from '../utils/clubImages'
import { userAvatar } from '../utils/avatarImages'
import BackButton from '../components/BackButton.vue'

const route = useRoute()
const club = ref<Club | null>(null)
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const formError = ref('')
const successMessage = ref('')
const members = ref<ClubMember[]>([])
const membersLoading = ref(false)
const membersError = ref('')
const memberRoleError = ref('')
const roleSavingMemberId = ref<number | null>(null)
const memberRoleOptions = [
  { value: 'member', label: 'Member' },
  { value: 'president', label: 'President' },
]

// President management state
const presidentSearchQuery = ref('')
const presidentSearchResults = ref<UserSearchResult[]>([])
const presidentSearching = ref(false)
const presidentSearchError = ref('')
const presidentUpdating = ref<number | null>(null)
const presidentActionError = ref('')

const hasValidUserId = (userId: number | null | undefined): userId is number =>
  typeof userId === 'number' && Number.isFinite(userId) && userId > 0

const isPresidentUpdating = (userId: number | null | undefined) =>
  hasValidUserId(userId) && presidentUpdating.value === userId

const isOwner = computed(() => Boolean(currentUser.value?.isOwner))

const handlePresidentSearch = async () => {
  const q = presidentSearchQuery.value.trim()
  if (q.length < 2) {
    presidentSearchResults.value = []
    return
  }
  presidentSearching.value = true
  presidentSearchError.value = ''
  try {
    presidentSearchResults.value = await searchUsers(q, 10)
  } catch (err) {
    presidentSearchError.value = err instanceof Error ? err.message : 'Search failed'
    presidentSearchResults.value = []
  } finally {
    presidentSearching.value = false
  }
}

const handleAssignPresident = async (userId: number | null | undefined) => {
  if (!club.value) return
  if (!hasValidUserId(userId)) {
    presidentActionError.value = 'This user result is missing an account ID. Refresh the roster and try again.'
    return
  }
  presidentUpdating.value = userId
  presidentActionError.value = ''
  try {
    await assignPresident(club.value.id, userId)
    invalidateClubCache()
    await Promise.all([refreshMembers(), refreshClubSnapshot()])
    presidentSearchResults.value = []
    presidentSearchQuery.value = ''
  } catch (err) {
    presidentActionError.value = err instanceof Error ? err.message : 'Failed to assign president'
  } finally {
    presidentUpdating.value = null
  }
}

const handleRemovePresident = async (userId: number) => {
  if (!club.value) return
  presidentUpdating.value = userId
  presidentActionError.value = ''
  try {
    await removePresident(club.value.id, userId)
    await refreshMembers()
  } catch (err) {
    presidentActionError.value = err instanceof Error ? err.message : 'Failed to remove president'
  } finally {
    presidentUpdating.value = null
  }
}

const form = reactive<{
  name: string; aliasName: string; description: string; category: string
  meetingSchedule: string; scheduleNote: string; location: string
  contactEmail: string; advisor: string
  achievementsText: string; imageUrl?: string
}>({
  name: '',
  aliasName: '',
  description: '',
  category: '',
  meetingSchedule: '',
  scheduleNote: '',
  location: '',
  contactEmail: '',
  advisor: '',
  achievementsText: '',
  imageUrl: ''})

const achievementPreview = computed(() =>
  form.achievementsText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
)

const nullable = (value: string) => {
  const trimmed = value.trim()
  return trimmed.length ? trimmed : null
}

const hydrateForm = (data: Club) => {
  form.name = data.name
  form.aliasName = data.aliasName ?? ''
  form.description = data.description
  form.category = data.category || clubCategoryOptions[0]?.title || ''
  form.meetingSchedule = data.meetingSchedule
  form.scheduleNote = data.scheduleNote ?? ''
  form.location = data.location ?? ''
  form.contactEmail = data.contactEmail ?? ''
  form.advisor = data.advisor ?? ''
  form.achievementsText = (data.achievements ?? []).join('\n')
  form.imageUrl = data.imageUrl ?? ''
}


const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)
const canManageMembers = computed(() => Boolean(club.value?.canManage) || Boolean(currentUser.value?.isOwner))
const presidents = computed(() => members.value.filter((m) => m.roleName?.toLowerCase() === 'president'))

const loadClub = async (id: string) => {
  loading.value = true
  loadError.value = ''
  formError.value = ''
  successMessage.value = ''
  try {
    const response = await fetchClubById(id)
    club.value = response
    hydrateForm(response)
    if (canManageMembers.value) {
      void loadMembers(id)
    } else {
      members.value = []
      membersError.value = ''
    }
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : 'Failed to load club details'
    club.value = null
    members.value = []
    membersError.value = ''
  } finally {
    loading.value = false
  }
}

const loadMembers = async (id: string) => {
  if (!id || !canManageMembers.value) {
    membersLoading.value = false
    members.value = []
    return
  }
  membersLoading.value = true
  membersError.value = ''
  try {
    members.value = await fetchClubMembers(id)
  } catch (err) {
    membersError.value = err instanceof Error ? err.message : 'Failed to load members'
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

const refreshMembers = async () => {
  if (club.value && canManageMembers.value) {
    await loadMembers(String(club.value.id))
  }
}

const handleMemberRoleChange = async (member: ClubMember, event: Event) => {
  if (!club.value || !canManageMembers.value || roleSavingMemberId.value) {
    return
  }
  const select = event.target as HTMLSelectElement
  const previousRole = member.roleName || 'member'
  const nextRole = select.value
  if (nextRole === previousRole) {
    return
  }

  memberRoleError.value = ''
  roleSavingMemberId.value = member.oauthUserId
  try {
    await updateClubMemberRole(club.value.id, member.oauthUserId, nextRole)
    await loadMembers(String(club.value.id))
    await refreshClubSnapshot()
  } catch (err) {
    member.roleName = previousRole
    memberRoleError.value = err instanceof Error ? err.message : 'Failed to update member role'
    await loadMembers(String(club.value.id))
  } finally {
    roleSavingMemberId.value = null
  }
}

const refreshClubSnapshot = async () => {
  if (!club.value) {
    return
  }
  try {
    club.value = await fetchClubById(String(club.value.id))
  } catch (err) {
    console.error(err)
  }
}

const retryLoad = () => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (id) {
    void loadClub(id)
  }
}

const imageUploading = ref(false)
const imageError = ref('')

const handleImageUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !club.value) return

  imageUploading.value = true
  imageError.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = buildApiUrl(`/api/clubs/${club.value.id}/image`)
    const response = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      body: formData})
    if (!response.ok) {
      const msg = await resolveErrorMessage(response, 'Upload failed')
      throw new Error(msg)
    }
    const data = await response.json()
    form.imageUrl = data.imageUrl
    club.value = { ...club.value, imageUrl: data.imageUrl }
    invalidateClubCache()
  } catch (err) {
    imageError.value = err instanceof Error ? err.message : 'Upload failed'
  } finally {
    imageUploading.value = false
  }
}

const handleReset = () => {
  if (club.value) {
    hydrateForm(club.value)
    successMessage.value = ''
    formError.value = ''
  }
}

const handleSave = async () => {
  if (!club.value || saving.value) return

  saving.value = true
  successMessage.value = ''
  formError.value = ''

  try {
    const payload: Partial<Club> = {
      name: form.name,
      aliasName: nullable(form.aliasName ?? ''),
      description: form.description,
      category: form.category,
      meetingSchedule: form.meetingSchedule,
      scheduleNote: nullable(form.scheduleNote ?? ''),
      location: nullable(form.location ?? ''),
      contactEmail: nullable(form.contactEmail ?? ''),
      advisor: nullable(form.advisor ?? ''),
      achievements: achievementPreview.value,
      imageUrl: form.imageUrl || null}

    const updated = await updateClub(club.value.id, payload)
    club.value = updated
    hydrateForm(updated)
    successMessage.value = 'Changes saved'
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Failed to save club'
  } finally {
    saving.value = false
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
      void loadMembers(String(club.value.id))
    } else {
      members.value = []
    }
  }
)
</script>

<template>
  <section class="club-admin page-shell">
    <div class="admin-toolbar">
      <BackButton>← Back to clubs</BackButton>
      <div class="toolbar-actions" v-if="!loading">
        <RouterLink
          v-if="club"
          :to="`/clubs/${club.id}`"
          class="ghost-btn"
        >View public page</RouterLink>
        <button type="button" class="ghost-btn" @click="handleReset" :disabled="!club || saving">
          Reset changes
        </button>
        <button type="button" class="primary-btn" @click="handleSave" :disabled="!club || saving">
          {{ saving ? 'Saving…' : 'Save changes' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="status-card">Loading club settings…</div>
    <div v-else-if="loadError" class="status-card error">
      <p>{{ loadError }}</p>
      <button type="button" class="ghost-btn" @click="retryLoad" :disabled="loading">Try again</button>
    </div>
    <template v-else-if="club">
      <header class="admin-hero">
        <div>
          <p class="section-label">Admin · {{ club.category || 'Uncategorized' }}</p>
          <h1>{{ club.name }}</h1>
          <p class="hero-meta">
            {{ club.memberCount }} members · Advisor {{ club.advisor || 'TBD' }} · {{ club.meetingSchedule }}
          </p>
        </div>
        <div class="hero-side">
          <div class="club-avatar xlarge">
            <img :src="clubImage(club)" :alt="`${club.name} avatar`" />
          </div>
          <div class="image-upload">
            <label class="upload-btn">
              {{ imageUploading ? 'Uploading…' : 'Change image' }}
              <input type="file" accept="image/*" hidden @change="handleImageUpload" />
            </label>
            <p v-if="imageError" class="upload-error">{{ imageError }}</p>
          </div>
          <span class="club-id">Club ID · {{ club.id }}</span>
        </div>
      </header>

      <div class="status-row" v-if="successMessage || formError">
        <p v-if="successMessage" class="pill success" role="status">{{ successMessage }}</p>
        <p v-if="formError" class="pill error" role="alert">{{ formError }}</p>
      </div>

      <div class="admin-grid">
        <form class="admin-form" @submit.prevent="handleSave">
          <div class="form-grid">
            <label>
              <span>Club name</span>
              <input v-model="form.name" type="text" required />
            </label>
            <label>
              <span>Alias / short name</span>
              <input v-model="form.aliasName" type="text" placeholder="Optional" />
            </label>
            <label class="wide">
              <span>Description</span>
              <textarea v-model="form.description" rows="4" required></textarea>
            </label>
            <label>
              <span>Category</span>
              <select v-model="form.category" required>
                <option v-for="category in clubCategoryOptions" :key="category.title" :value="category.title">
                  {{ category.title }}
                </option>
              </select>
            </label>
            <label>
              <span>Meeting schedule</span>
              <input v-model="form.meetingSchedule" type="text" required />
            </label>
            <label class="wide">
              <span>President schedule note</span>
              <textarea
                v-model="form.scheduleNote"
                rows="3"
                placeholder="Add exceptions, meeting dates, room changes, or extra context for the calendar."
              ></textarea>
            </label>
            <label>
              <span>Location</span>
              <input v-model="form.location" type="text" placeholder="Optional" />
            </label>
            <label>
              <span>Advisor</span>
              <input v-model="form.advisor" type="text" placeholder="Advisor name" />
            </label>
            <label>
              <span>Contact email</span>
              <input v-model="form.contactEmail" type="email" placeholder="club@example.com" />
            </label>
            <label class="wide">
              <span>Achievements (one per line)</span>
              <textarea v-model="form.achievementsText" rows="4" placeholder="Add each highlight on its own line"></textarea>
            </label>
          </div>
          <div class="form-actions">
            <button type="button" class="ghost-btn" @click="handleReset" :disabled="saving">Discard edits</button>
            <button type="submit" class="primary-btn" :disabled="saving">{{ saving ? 'Saving…' : 'Save changes' }}</button>
          </div>
        </form>

        <aside class="insights-panel">
          <h2>Live preview</h2>
          <div class="insight-card">
            <p class="label">Members</p>
            <p class="value">{{ club.memberCount }}</p>
          </div>
          <div class="insight-card">
            <p class="label">Contact</p>
            <p class="value">{{ form.contactEmail || 'Not provided' }}</p>
          </div>
          <div class="insight-card">
            <p class="label">President note</p>
            <p class="value note-preview">{{ form.scheduleNote || 'No president note added' }}</p>
          </div>
          <div class="insight-card achievements">
            <p class="label">Achievements</p>
            <ul>
              <li v-for="(item, index) in achievementPreview" :key="index">{{ item }}</li>
              <li v-if="!achievementPreview.length">No achievements listed</li>
            </ul>
          </div>
        </aside>
      </div>

      <section class="member-panel">
        <div class="member-panel__header">
          <div>
            <h2>Member management</h2>
            <p>Review everyone currently linked to {{ club.name }}.</p>
          </div>
          <div class="member-panel__actions">
            <RouterLink
              v-if="club && canManageMembers"
              :to="`/clubs/${club.id}/admin/pending`"
              class="ghost-btn"
            >
              Review pending requests
            </RouterLink>
            <button type="button" class="ghost-btn" @click="refreshMembers" :disabled="membersLoading || !canManageMembers">
              {{ membersLoading ? 'Refreshing…' : 'Refresh roster' }}
            </button>
          </div>
        </div>

        <p v-if="!canManageMembers" class="member-hint">
          Only club leaders or site owners can view the roster.
        </p>

        <section v-else class="roster-panel">
          <div v-if="membersLoading" class="status-card">Loading members…</div>
          <div v-else-if="membersError" class="status-card error">
            <p>{{ membersError }}</p>
            <button type="button" class="ghost-btn" @click="refreshMembers">Try again</button>
          </div>
          <template v-else>
            <p v-if="memberRoleError" class="member-hint error" role="alert">{{ memberRoleError }}</p>
            <ul v-if="members.length" class="member-list">
              <li v-for="member in members" :key="member.oauthUserId" class="member-entry">
                <div class="member-avatar">
                  <img
                    :src="userAvatar(member.avatarUrl, member.displayName || 'Member')"
                    :alt="member.displayName || 'Club member'"
                  />
                </div>
                <div class="member-info">
                  <p>{{ member.displayName || 'Unnamed member' }}</p>
                  <small>{{ member.roleName || 'Member' }}</small>
                </div>
                <label v-if="isOwner" class="member-role">
                  <span>Role</span>
                  <select
                    :value="member.roleName || 'member'"
                    :disabled="roleSavingMemberId === member.oauthUserId"
                    @change="handleMemberRoleChange(member, $event)"
                  >
                    <option v-for="option in memberRoleOptions" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                </label>
                <a v-if="member.email" class="member-email" :href="`mailto:${member.email}`">{{ member.email }}</a>
              </li>
            </ul>
            <p v-else class="member-empty">No members have been linked yet.</p>
          </template>
        </section>
      </section>

      <!-- President management (owner only) -->
      <section v-if="isOwner" class="member-panel president-panel">
        <div class="member-panel__header">
          <div>
            <h2>President management</h2>
            <p>Assign or remove club presidents. Presidents can edit club details and manage members.</p>
          </div>
        </div>

        <div class="president-search">
          <label>
            <span>Search users by name or email</span>
            <div class="search-row">
              <input
                v-model="presidentSearchQuery"
                type="search"
                placeholder="e.g. maya.chen@example.com"
                @keyup.enter="handlePresidentSearch"
              />
              <button type="button" class="primary-btn small" @click="handlePresidentSearch" :disabled="presidentSearching">
                {{ presidentSearching ? 'Searching…' : 'Search' }}
              </button>
            </div>
          </label>

          <p v-if="presidentActionError" class="upload-error">{{ presidentActionError }}</p>

          <ul v-if="presidentSearchResults.length" class="member-list">
            <li v-for="user in presidentSearchResults" :key="user.id ?? user.email" class="member-entry">
              <div class="member-info">
                <p>{{ user.displayName || 'Unnamed' }}</p>
                <small>{{ user.email }}</small>
              </div>
              <button
                type="button"
                class="primary-btn small"
                :disabled="presidentUpdating !== null || !hasValidUserId(user.id)"
                @click="handleAssignPresident(user.id)"
              >
                {{ isPresidentUpdating(user.id) ? 'Assigning…' : 'Assign as President' }}
              </button>
            </li>
          </ul>
          <p v-else-if="presidentSearchQuery && !presidentSearching" class="member-empty">
            No users found. Try a different search term.
          </p>
        </div>

        <!-- Current presidents list -->
        <div class="current-presidents">
          <h3>Current Presidents</h3>
          <ul v-if="presidents.length" class="member-list">
            <li v-for="member in presidents" :key="member.oauthUserId" class="member-entry">
              <div class="member-info">
                <p>{{ member.displayName || 'Unnamed' }}</p>
                <small>{{ member.email }}</small>
              </div>
              <button
                type="button"
                class="ghost-btn danger small"
                :disabled="presidentUpdating === member.oauthUserId"
                @click="handleRemovePresident(member.oauthUserId)"
              >
                {{ presidentUpdating === member.oauthUserId ? 'Removing…' : 'Remove President' }}
              </button>
            </li>
          </ul>
          <p v-else class="member-empty">No presidents assigned yet.</p>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.club-admin {
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

.toolbar-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.back-link {
  color: var(--mv-text-soft);
  text-decoration: none;
  font-weight: 600;
}

.ghost-btn,
.primary-btn {
  border-radius: 999px;
  padding: 0.55rem 1.4rem;
  font-weight: 600;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.ghost-btn.danger {
  border-color: rgba(248, 113, 113, 0.35);
  color: var(--mv-status-danger);
}

.ghost-btn:disabled,
.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.primary-btn {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  border-color: var(--mv-primary-bg);
}

.status-card {
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  padding: 1.25rem 1.5rem;
  background: var(--mv-surface-card-strong);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.4);
  color: var(--mv-status-danger);
}

.admin-hero {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: var(--mv-surface-hero);
  display: flex;
  justify-content: space-between;
  gap: clamp(1rem, 3vw, 2rem);
  flex-wrap: wrap;
}

.hero-meta {
  color: var(--mv-text-faint);
}

.hero-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.club-avatar {
  width: 110px;
  height: 110px;
  border-radius: 28px;
  overflow: hidden;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-accent);
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.club-id {
  font-size: 0.9rem;
  color: var(--mv-text-faint);
}

.status-row {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.pill {
  border-radius: 999px;
  padding: 0.4rem 1rem;
  font-size: 0.9rem;
  border: 1px solid var(--mv-border);
}

.pill.success {
  border-color: rgba(34, 197, 94, 0.35);
  color: var(--mv-status-success);
}

.pill.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: var(--mv-status-danger);
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: clamp(1rem, 4vw, 2rem);
}

.admin-form {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.25rem, 4vw, 2rem);
  background: var(--mv-surface-card-strong);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
  color: var(--mv-text-soft);
}

input,
select,
textarea {
  border-radius: 14px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  color: var(--mv-text);
  padding: 0.65rem 0.9rem;
  font: inherit;
}

.wide {
  grid-column: 1 / -1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.25rem;
}

.insights-panel {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.25rem, 4vw, 1.75rem);
  background: var(--mv-surface-card-strong);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.insight-card {
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  padding: 1rem;
  background: var(--mv-surface-card);
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
}

.insight-card .label {
  margin: 0;
  color: var(--mv-text-dim);
  font-size: 0.85rem;
}

.insight-card .value {
  margin: 0.35rem 0 0;
  font-size: 1.1rem;
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.note-preview {
  white-space: pre-wrap;
  font-size: 0.98rem;
}

.insight-card.achievements ul {
  margin: 0.5rem 0 0;
  padding-left: 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  color: var(--mv-text-soft);
}

.member-panel {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: var(--mv-surface-card-strong);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.roster-panel {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.member-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
}

.member-panel__header h2 {
  margin: 0;
}

.member-panel__header p {
  margin: 0.2rem 0 0;
  color: var(--mv-text-faint);
}

.member-panel__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.member-hint,
.member-empty {
  margin: 0;
  color: var(--mv-text-faint);
}

.member-hint.error {
  color: var(--mv-status-danger);
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
  grid-template-columns: auto minmax(0, 1fr) minmax(150px, auto) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--mv-border);
}

.member-entry:last-child {
  border-bottom: none;
}

.member-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-accent);
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
  color: var(--mv-text-dim);
}

.member-role {
  min-width: 150px;
  gap: 0.25rem;
}

.member-role span {
  font-size: 0.75rem;
  color: var(--mv-text-dim);
}

.member-role select {
  min-height: 2.25rem;
  padding: 0.4rem 0.75rem;
}

.member-email {
  color: var(--mv-gold);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

@media (max-width: 900px) {
  .admin-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .form-actions button {
    width: 100%;
  }
}
.image-upload { margin-top: 0.5rem; }
.upload-btn {
  display: inline-block;
  padding: 0.4rem 1rem;
  border-radius: 999px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  color: var(--mv-text-soft);
  font-size: 0.85rem;
  cursor: pointer;
}
.upload-btn:hover { background: var(--mv-surface-accent); }
.upload-error { color: var(--mv-status-danger); font-size: 0.85rem; margin: 0.25rem 0 0; }

@media (max-width: 720px) {
  .admin-hero {
    flex-direction: column;
    padding: 1.5rem;
    border-radius: 22px;
  }

  .admin-hero .hero-side {
    align-items: flex-start;
  }

  .admin-form {
    padding: 1.25rem;
    border-radius: 22px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .admin-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions .ghost-btn,
  .toolbar-actions .primary-btn {
    flex: 1;
    text-align: center;
  }

  .insights-panel,
  .roster-panel {
    padding: 1.25rem;
    border-radius: 20px;
  }

  .member-entry {
    grid-template-columns: auto 1fr;
  }

  .member-role,
  .member-email {
    grid-column: 1 / -1;
  }

  .member-role {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .admin-hero {
    padding: 1.15rem;
    border-radius: 18px;
  }

  .admin-hero h1 {
    font-size: 1.5rem;
  }

  .club-avatar.xlarge {
    width: 80px;
    height: 80px;
    border-radius: 20px;
  }

  .form-actions {
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .form-actions button {
    width: 100%;
  }
}
.president-panel {
  margin-top: 1rem;
}

.president-search {
  margin-top: 1rem;
}

.search-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.search-row input {
  flex: 1;
  min-width: 0;
}

.president-search .member-list {
  margin-top: 0.75rem;
}

.current-presidents {
  margin-top: 1.5rem;
}

.current-presidents h3 {
  font-size: 1rem;
  margin: 0 0 0.5rem;
}

.primary-btn.small,
.ghost-btn.small {
  padding: 0.35rem 0.85rem;
  font-size: 0.85rem;
  white-space: nowrap;
}
</style>
