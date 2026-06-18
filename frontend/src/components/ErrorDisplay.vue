<script setup lang="ts">
defineProps<{ message?: string }>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="error-display" role="alert">
    <div class="error-card">
      <p class="error-icon">⚠</p>
      <h2>Something went wrong</h2>
      <p>{{ message || 'An unexpected error occurred. Please try again.' }}</p>
      <button type="button" class="btn primary" @click="emit('retry')">Try again</button>
    </div>
  </div>
</template>

<style scoped>
.error-display {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(2rem, 8vw, 5rem) 2rem;
  min-height: 50vh;
}

.error-card {
  text-align: center;
  max-width: 420px;
}

.error-icon { font-size: 2.5rem; margin: 0 0 0.5rem; }

.error-card h2 { margin: 0 0 0.5rem; }
.error-card p { color: var(--mv-text-muted); margin: 0 0 1.5rem; }

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  border: none;
}

.btn.primary { background: var(--mv-primary-bg); color: var(--mv-primary-text); }
</style>
