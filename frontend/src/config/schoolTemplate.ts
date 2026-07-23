export const schoolTemplate = {
  schoolName: import.meta.env.VITE_SCHOOL_NAME || 'Sample High School',
  shortName: import.meta.env.VITE_SCHOOL_SHORT_NAME || 'School Clubs',
  tagline:
    import.meta.env.VITE_SCHOOL_TAGLINE ||
    'A reusable club directory template for one school community.',
  intro:
    import.meta.env.VITE_SCHOOL_INTRO ||
    'Browse active clubs, discover meeting times, and start membership workflows from a simple school-owned directory.',
}
