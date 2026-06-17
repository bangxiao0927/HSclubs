import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchSchools, fetchSchoolBySlug, type SchoolSummary } from '../services/schoolService'

export const useSchoolStore = defineStore('school', () => {
  const schools = ref<SchoolSummary[]>([])
  const currentSchool = ref<SchoolSummary | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const hasLoaded = ref(false)

  const currentSchoolSlug = computed(() => currentSchool.value?.slug ?? '')

  const loadSchools = async () => {
    if (hasLoaded.value && schools.value.length > 0) return
    loading.value = true
    error.value = null
    try {
      schools.value = await fetchSchools()
      hasLoaded.value = true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load schools'
    } finally {
      loading.value = false
    }
  }

  const setCurrentSchoolBySlug = async (slug: string) => {
    // Try cache first
    const cached = schools.value.find((s) => s.slug === slug)
    if (cached) {
      currentSchool.value = cached
      return
    }
    // Fetch from API
    loading.value = true
    error.value = null
    try {
      currentSchool.value = await fetchSchoolBySlug(slug)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load school'
    } finally {
      loading.value = false
    }
  }

  return {
    schools,
    currentSchool,
    currentSchoolSlug,
    loading,
    error,
    hasLoaded,
    loadSchools,
    setCurrentSchoolBySlug,
  }
})
