export type ColorMode = 'light' | 'dark'

const configuredColorMode = import.meta.env.VITE_DEFAULT_COLOR_MODE?.trim().toLowerCase()

export const schoolTemplate = {
  // Defaults are this deployment's school (MVHS, on mvhs.hsclubs.net); every one of them is
  // overridable, and a school copying this repo overrides all of them in its own .env rather
  // than editing this file. "HS Clubs" is the platform, not the school, so it stays put.
  brandName: import.meta.env.VITE_BRAND_NAME || 'HS Clubs',
  schoolName: import.meta.env.VITE_SCHOOL_NAME || 'Monta Vista High School',
  shortName: import.meta.env.VITE_SCHOOL_SHORT_NAME || 'MVHS',
  tagline:
    import.meta.env.VITE_SCHOOL_TAGLINE || 'Clubs at Monta Vista High School, in one place.',
  intro:
    import.meta.env.VITE_SCHOOL_INTRO ||
    'Browse active clubs, discover meeting times, and start membership workflows from a simple school-owned directory.',
  defaultColorMode: (configuredColorMode === 'dark' ? 'dark' : 'light') as ColorMode,
}
