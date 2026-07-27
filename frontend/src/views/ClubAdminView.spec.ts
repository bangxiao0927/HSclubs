import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
  fetchClubMembers: vi.fn(),
  updateClub: vi.fn(),
  updateClubMemberRole: vi.fn(),
}))

vi.mock('../services/userService', () => ({
  searchUsers: vi.fn(),
  assignPresident: vi.fn(),
  removePresident: vi.fn(),
}))

import { useRoute, useRouter } from 'vue-router'

import { fetchClubById, updateClub } from '../services/clubService'
import type { Club } from '../types/club'
import ClubAdminView from './ClubAdminView.vue'

const useRouteMock = vi.mocked(useRoute)
const useRouterMock = vi.mocked(useRouter)
const fetchClubByIdMock = vi.mocked(fetchClubById)
const updateClubMock = vi.mocked(updateClub)

// member_count is now derived server-side from club_member rows (see
// ClubMapper.xml's BaseColumnList): a club record from the API always
// carries the real, already-correct count.
const buildClub = (overrides: Partial<Club> = {}): Club => ({
  id: 1,
  name: 'Chess Club',
  slug: null,
  aliasName: null,
  description: 'Play chess on Fridays',
  category: 'General',
  meetingSchedule: 'Fridays 3pm',
  location: null,
  contactEmail: null,
  advisor: null,
  imageUrl: null,
  memberCount: 42,
  achievements: [],
  canManage: false,
  ...overrides,
})

const mountView = async (club: Club) => {
  useRouteMock.mockReturnValue({ params: { id: String(club.id) } } as unknown as ReturnType<typeof useRoute>)
  useRouterMock.mockReturnValue({ push: vi.fn(), back: vi.fn() } as unknown as ReturnType<typeof useRouter>)
  fetchClubByIdMock.mockResolvedValue(club)
  const wrapper = mount(ClubAdminView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchClubByIdMock.mockReset()
  updateClubMock.mockReset()
})

describe('ClubAdminView member count', () => {
  it('shows the derived member count as read-only text instead of an editable input', async () => {
    const club = buildClub({ memberCount: 42 })

    const wrapper = await mountView(club)

    expect(wrapper.find('input[type="number"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('42 members')
  })

  it('does not submit a memberCount field when saving club changes', async () => {
    const club = buildClub({ memberCount: 42 })
    updateClubMock.mockResolvedValue(club)

    const wrapper = await mountView(club)
    await wrapper.find('form.admin-form').trigger('submit.prevent')
    await flushPromises()

    expect(updateClubMock).toHaveBeenCalledTimes(1)
    const [, payload] = updateClubMock.mock.calls[0]!
    expect(payload).not.toHaveProperty('memberCount')
  })
})
