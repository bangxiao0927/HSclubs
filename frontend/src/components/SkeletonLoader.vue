<script setup lang="ts">
defineProps<{
  count?: number
  height?: string
}>()
</script>

<template>
  <div class="skeleton-list" aria-busy="true" aria-label="Loading content">
    <div
      v-for="i in (count ?? 3)"
      :key="i"
      class="skeleton-item"
      :style="{ height: height ?? '72px' }"
    >
      <div class="skeleton-avatar" />
      <div class="skeleton-lines">
        <div class="skeleton-line w-60" />
        <div class="skeleton-line w-40" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.skeleton-item {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  background: var(--mv-surface-card);
  border: 1px solid var(--mv-border);
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--mv-surface-muted) 25%, var(--mv-surface-soft) 50%, var(--mv-surface-muted) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  flex-shrink: 0;
}

.skeleton-lines {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.skeleton-line {
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(90deg, var(--mv-surface-muted) 25%, var(--mv-surface-soft) 50%, var(--mv-surface-muted) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.w-60 { width: 60%; }
.w-40 { width: 40%; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
