import { computed, ref, watch } from 'vue'

import { schoolTemplate, type ColorMode } from '../config/schoolTemplate'
import { resolveInitialTheme } from '../utils/themeBootstrap'

// One shared piece of state for the whole app: the title bar's toggle and the profile page's
// toggle are the same preference, and a second copy of it would let the two disagree.
const theme = ref<ColorMode>(schoolTemplate.defaultColorMode)

const readStoredTheme = () => {
  try {
    return window.localStorage.getItem('theme')
  } catch (error) {
    console.warn('Failed to read theme preference.', error)
    return null
  }
}

const persistTheme = (value: ColorMode) => {
  try {
    window.localStorage.setItem('theme', value)
  } catch (error) {
    console.warn('Failed to persist theme preference.', error)
  }
}

// Registered once, at module scope, so the document always reflects `theme` no matter which
// component changed it -- including one that is unmounted right after toggling.
watch(
  theme,
  (value) => {
    document.documentElement.dataset.theme = value
    document.documentElement.style.colorScheme = value
  },
  { immediate: true },
)

export const useTheme = () => {
  // Re-synced for every consumer rather than read once at import: the stored value is the
  // source of truth and toggling writes it synchronously, so this can only ever agree with
  // the in-memory value -- while still picking up a preference stored by another tab.
  theme.value = resolveInitialTheme(readStoredTheme(), schoolTemplate.defaultColorMode)

  const themeLabel = computed(() => (theme.value === 'light' ? 'Dark mode' : 'Light mode'))
  const themeIcon = computed(() => (theme.value === 'light' ? '🌙' : '☀️'))

  const toggleTheme = () => {
    const nextTheme: ColorMode = theme.value === 'light' ? 'dark' : 'light'
    theme.value = nextTheme
    persistTheme(nextTheme)
  }

  return { theme, themeLabel, themeIcon, toggleTheme }
}
