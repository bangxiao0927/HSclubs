/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BRAND_NAME?: string
  readonly VITE_SCHOOL_NAME?: string
  readonly VITE_SCHOOL_SHORT_NAME?: string
  readonly VITE_SCHOOL_TAGLINE?: string
  readonly VITE_SCHOOL_INTRO?: string
  readonly VITE_DEFAULT_COLOR_MODE?: 'light' | 'dark'
  readonly VITE_CALENDAR_LUNCH_START?: string
  readonly VITE_CALENDAR_LUNCH_END?: string
  readonly VITE_CALENDAR_AFTER_SCHOOL_START?: string
  readonly VITE_CALENDAR_AFTER_SCHOOL_END?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
