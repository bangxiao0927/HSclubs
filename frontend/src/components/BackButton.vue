<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(defineProps<{ fallbackTo?: string }>(), {
  fallbackTo: '/',
})

const router = useRouter()

const goBack = () => {
  const state = window.history.state as { back?: string | null } | null
  if (state?.back) {
    router.back()
    return
  }
  void router.push(props.fallbackTo)
}
</script>

<template>
  <button type="button" class="back-link app-back-button" @click="goBack">
    <slot>Back</slot>
  </button>
</template>

<style scoped>
.app-back-button {
  appearance: none;
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  cursor: pointer;
  text-align: left;
}
</style>
