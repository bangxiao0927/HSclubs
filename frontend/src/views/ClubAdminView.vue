<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import { fetchClubById, fetchClubMembers, updateClub } from '../services/clubService'
import type { Club, ClubMember } from '../types/club'
import { useAuthStore } from '../stores/auth'
import { clubCategoryOptions } from '../utils/clubCategories'
import { clubImage } from '../utils/clubImages'

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

const form = reactive({
  name: '',
  aliasName: '',
  description: '',
  category: '',
  meetingSchedule: '',
  scheduleNote: '',
  location: '',
  contactEmail: '',
  advisor: '',
  memberCount: 0,
  achievementsText: '',
})

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
  form.memberCount = data.memberCount
  form.achievementsText = (data.achievements ?? []).join('\n')
}

const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)
const canManageMembers = computed(() => Boolean(club.value?.canManage) || Boolean(currentUser.value?.isOwner))

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

const refreshMembers = () => {
  if (club.value && canManageMembers.value) {
    void loadMembers(String(club.value.id))
  }
}

const retryLoad = () => {
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (id) {
    void loadClub(id)
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
      schoolId: club.value.schoolId,
      name: form.name,
      aliasName: nullable(form.aliasName ?? ''),
      description: form.description,
      category: form.category,
      meetingSchedule: form.meetingSchedule,
      scheduleNote: nullable(form.scheduleNote ?? ''),
      location: nullable(form.location ?? ''),
      contactEmail: nullable(form.contactEmail ?? ''),
      advisor: nullable(form.advisor ?? ''),
      memberCount: form.memberCount,
      achievements: achievementPreview.value,
    }

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
      <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
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
            <label>
              <span>Member count</span>
              <input v-model.number="form.memberCount" type="number" min="0" required />
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
            <p class="value">{{ form.memberCount }}</p>
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
          <ul v-else-if="members.length" class="member-list">
            <li v-for="member in members" :key="member.oauthUserId" class="member-entry">
              <div class="member-avatar">
                <img
                  :src="member.avatarUrl || 'https://api.dicebear.com/7.x/thumbs/svg?seed=' + encodeURIComponent(member.displayName || 'Member')"
                  :alt="member.displayName || 'Club member'"
                />
              </div>
              <div class="member-info">
                <p>{{ member.displayName || 'Unnamed member' }}</p>
                <small>{{ member.roleName || 'Member' }}</small>
              </div>
              <a v-if="member.email" class="member-email" :href="`mailto:${member.email}`">{{ member.email }}</a>
            </li>
          </ul>
          <p v-else class="member-empty">No members have been linked yet.</p>
        </section>
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
  background: var(--mv-gold);
  color: #111;
  border-color: var(--mv-gold);
}

.status-card {
  border-radius: 20px;
  border: 1px solid rgba(250, 204, 21, 0.15);
  padding: 1.25rem 1.5rem;
  background: rgba(7, 7, 7, 0.85);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.4);
  color: #fecaca;
}

.admin-hero {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: rgba(10, 10, 10, 0.9);
  display: flex;
  justify-content: space-between;
  gap: clamp(1rem, 3vw, 2rem);
  flex-wrap: wrap;
}

.hero-meta {
  color: rgba(254, 252, 232, 0.75);
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
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: rgba(253, 224, 71, 0.08);
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.club-id {
  font-size: 0.9rem;
  color: rgba(254, 252, 232, 0.7);
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
  border: 1px solid rgba(250, 204, 21, 0.35);
}

.pill.success {
  border-color: rgba(34, 197, 94, 0.35);
  color: #bbf7d0;
}

.pill.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: #fecaca;
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: clamp(1rem, 4vw, 2rem);
}

.admin-form {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: clamp(1.25rem, 4vw, 2rem);
  background: rgba(7, 7, 7, 0.9);
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
  color: rgba(254, 252, 232, 0.8);
}

input,
textarea {
  border-radius: 14px;
  border: 1px solid rgba(254, 252, 232, 0.15);
  background: rgba(15, 15, 15, 0.85);
  color: #fefce8;
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
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: clamp(1.25rem, 4vw, 1.75rem);
  background: rgba(10, 10, 10, 0.9);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.insight-card {
  border-radius: 20px;
  border: 1px solid rgba(254, 252, 232, 0.1);
  padding: 1rem;
  background: rgba(15, 15, 15, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
}

.insight-card .label {
  margin: 0;
  color: rgba(254, 252, 232, 0.65);
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
  color: rgba(254, 252, 232, 0.8);
}

.member-panel {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: rgba(7, 7, 7, 0.92);
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
  color: rgba(254, 252, 232, 0.7);
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

.member-email {
  color: rgba(250, 204, 21, 0.9);
  font-size: 0.9rem;
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
</style>
