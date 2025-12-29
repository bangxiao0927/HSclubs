import { ref } from 'vue'
import { defineStore } from 'pinia'

const AUTH_STORAGE_KEY = 'hsclubs-auth'

const getInitialAuthState = () => {
  if (typeof window === 'undefined') {
    return false
  }
  return window.localStorage.getItem(AUTH_STORAGE_KEY) === 'true'
}

export const useAuthStore = defineStore('auth', () => {
  const isAuthenticated = ref(getInitialAuthState())

  const persistState = (state: boolean) => {
    if (typeof window === 'undefined') {
      return
    }
    if (state) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, 'true')
    } else {
      window.localStorage.removeItem(AUTH_STORAGE_KEY)
    }
  }

  const login = () => {
    isAuthenticated.value = true
    persistState(true)
  }

  const logout = () => {
    isAuthenticated.value = false
    persistState(false)
  }

  return { isAuthenticated, login, logout }
})
