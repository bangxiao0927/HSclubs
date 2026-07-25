export type ColorMode = 'light' | 'dark'

const configuredColorMode = import.meta.env.VITE_DEFAULT_COLOR_MODE?.trim().toLowerCase()

export const schoolTemplate = {
  brandName: import.meta.env.VITE_BRAND_NAME || 'HS Clubs',
  schoolName: import.meta.env.VITE_SCHOOL_NAME || 'Sample High School',
  shortName: import.meta.env.VITE_SCHOOL_SHORT_NAME || 'School Clubs',
  tagline:
    import.meta.env.VITE_SCHOOL_TAGLINE ||
    'A reusable club directory template for one school community.',
  intro:
    import.meta.env.VITE_SCHOOL_INTRO ||
    'Browse active clubs, discover meeting times, and start membership workflows from a simple school-owned directory.',
  defaultColorMode: (configuredColorMode === 'dark' ? 'dark' : 'light') as ColorMode,
}
