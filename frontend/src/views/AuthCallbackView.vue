<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const resolveRedirectTarget = () => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/profile'
}

onMounted(async () => {
  await authStore.refreshUser()

  if (authStore.isAuthenticated) {
    router.replace(resolveRedirectTarget())
  } else {
    router.replace({ path: '/auth', query: { error: 'login_failed' } })
  }
})
</script>

<template>
  <div class="callback-page">
    <div class="callback-card">
      <span class="spinner" aria-hidden="true" />
      <p class="title">Finishing sign in…</p>
      <p class="hint">This only takes a moment. You will be redirected automatically.</p>
    </div>
  </div>
</template>

<style scoped>
.callback-page {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: radial-gradient(circle at 20% 20%, rgba(253, 224, 71, 0.15), transparent 55%),
    radial-gradient(circle at 80% 0%, rgba(59, 130, 246, 0.2), transparent 60%),
    #06070b;
  color: #fefce8;
}

.callback-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: clamp(2rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  background: linear-gradient(120deg, rgba(250, 204, 21, 0.18), rgba(5, 5, 5, 0.95));
  max-width: 420px;
  text-align: center;
}

.spinner {
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  border: 3px solid rgba(254, 252, 232, 0.2);
  border-top-color: #fde047;
  animation: spin 1s linear infinite;
}

.title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.hint {
  margin: 0;
  color: rgba(254, 252, 232, 0.75);
  line-height: 1.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
