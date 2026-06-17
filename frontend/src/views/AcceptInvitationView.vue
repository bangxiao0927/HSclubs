<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { buildApiUrl } from '../services/httpClient'

const route = useRoute()
const authStore = useAuthStore()
const { isAuthenticated } = storeToRefs(authStore)

const status = ref<'loading' | 'error' | 'success'>('loading')
const message = ref('')

const token = (route.query.token as string) ?? ''

const acceptInvitation = async () => {
  if (!token) {
    status.value = 'error'
    message.value = 'No invitation token found in the URL.'
    return
  }
  status.value = 'loading'
  try {
    const response = await fetch(buildApiUrl(`/api/platform/invitations/${token}/accept`), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    })
    const data = await response.json()
    if (response.ok) {
      status.value = 'success'
      message.value = data.message || 'Invitation accepted!'
      // Refresh auth to pick up new school membership
      await authStore.refreshUser()
    } else {
      status.value = 'error'
      message.value = data.message || 'Failed to accept invitation.'
    }
  } catch {
    status.value = 'error'
    message.value = 'Unable to reach the server. Please try again.'
  }
}

onMounted(() => {
  if (isAuthenticated.value) {
    acceptInvitation()
  }
})
</script>

<template>
  <div class="accept-invitation">
    <div class="invitation-card">
      <div v-if="!isAuthenticated" class="auth-gate">
        <h1>Accept school invitation</h1>
        <p>Sign in first to accept your invitation as a school administrator.</p>
        <RouterLink to="/auth?intent=login" class="btn primary">Sign in</RouterLink>
      </div>

      <div v-else-if="status === 'loading'" class="status">
        <p>Processing your invitation…</p>
      </div>

      <div v-else-if="status === 'success'" class="status success">
        <h1>🎉 Welcome!</h1>
        <p>{{ message }}</p>
        <RouterLink to="/schools" class="btn primary">Go to schools</RouterLink>
      </div>

      <div v-else class="status error">
        <h1>Something went wrong</h1>
        <p>{{ message }}</p>
        <button type="button" class="btn ghost" @click="acceptInvitation">Try again</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.accept-invitation {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.invitation-card {
  max-width: 480px;
  width: 100%;
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  text-align: center;
}

.invitation-card h1 { margin: 0 0 0.75rem; }
.invitation-card p { color: var(--mv-text-muted); margin: 0 0 1.5rem; }

.status { display: flex; flex-direction: column; gap: 1rem; align-items: center; }
.status.success h1 { color: var(--mv-status-success); }
.status.error h1 { color: var(--mv-status-danger); }

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: none;
  display: inline-block;
}
.btn.primary { background: var(--mv-primary-bg); color: var(--mv-primary-text); }
.btn.ghost { border: 1px solid var(--mv-ghost-border); background: transparent; color: var(--mv-ghost-text); }

.auth-gate { display: flex; flex-direction: column; gap: 1rem; align-items: center; }
</style>
