/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_CALENDAR_LUNCH_START?: string
  readonly VITE_CALENDAR_LUNCH_END?: string
  readonly VITE_CALENDAR_AFTER_SCHOOL_START?: string
  readonly VITE_CALENDAR_AFTER_SCHOOL_END?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
