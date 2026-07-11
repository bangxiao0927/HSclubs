export type ClubCategoryOption = {
  id: string
  title: string
  focus: string
  icon: string
  gradient: string
  description: string
}

export const clubCategoryOptions: ClubCategoryOption[] = [
  {
    id: 'stem',
    title: 'STEM & Innovation',
    focus: 'Engineering · Coding · Science',
    icon: '⚙️',
    gradient: 'linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%)',
    description: 'Engineering, robotics, coding, research, and science-focused clubs.',
  },
  {
    id: 'arts',
    title: 'Creative Arts & Media',
    focus: 'Design · Film · Performance',
    icon: '🎨',
    gradient: 'linear-gradient(135deg, #0f766e 0%, #38bdf8 100%)',
    description: 'Visual arts, music, film, theater, writing, and media clubs.',
  },
  {
    id: 'service',
    title: 'Service & Leadership',
    focus: 'Community · Advocacy · Outreach',
    icon: '🤝',
    gradient: 'linear-gradient(135deg, #14532d 0%, #22c55e 100%)',
    description: 'Clubs centered on volunteering, advocacy, civic work, and leadership.',
  },
  {
    id: 'culture',
    title: 'Culture & Identity',
    focus: 'Heritage · Community · Student unions',
    icon: '🌍',
    gradient: 'linear-gradient(135deg, #1f2937 0%, #7c3aed 100%)',
    description: 'Cultural, heritage, language, and identity-based student communities.',
  },
  {
    id: 'wellness',
    title: 'Wellness & Athletics',
    focus: 'Sports · Mental health · Physical wellbeing',
    icon: '🏅',
    gradient: 'linear-gradient(135deg, #991b1b 0%, #dc2626 100%)',
    description: 'Athletics, mental health, physical wellness, and recreation clubs.',
  },
  {
    id: 'competition',
    title: 'Competition & Strategy',
    focus: 'Debate · Games · Olympiads',
    icon: '♟️',
    gradient: 'linear-gradient(135deg, #111827 0%, #0f766e 100%)',
    description: 'Competitive academic, debate, games, and strategy-oriented clubs.',
  },
]

export const clubCategoryByTitle = Object.fromEntries(
  clubCategoryOptions.map((category) => [category.title, category]),
) as Record<string, ClubCategoryOption>
