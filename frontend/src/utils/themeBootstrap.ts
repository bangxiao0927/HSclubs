import type { ColorMode } from '../config/schoolTemplate'

// Shared with the inline pre-mount bootstrap in index.html, which sets
// document.documentElement.dataset.theme before Vue mounts to avoid a
// light/dark flash. Keep the decision rule here in sync with that script.
export const resolveInitialTheme = (
  storedTheme: string | null,
  fallback: ColorMode,
): ColorMode => {
  if (storedTheme === 'light' || storedTheme === 'dark') {
    return storedTheme
  }
  return fallback
}
