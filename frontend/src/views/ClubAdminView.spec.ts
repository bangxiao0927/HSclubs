import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
  fetchClubMembers: vi.fn(),
  invalidateClubCache: vi.fn(),
  updateClub: vi.fn(),
  updateClubMemberRole: vi.fn(),
}))

vi.mock('../services/userService', () => ({
  searchUsers: vi.fn(),
  assignPresident: vi.fn(),
  removePresident: vi.fn(),
}))

import { useRoute, useRouter } from 'vue-router'

import {
  fetchClubById,
  fetchClubMembers,
  invalidateClubCache,
  updateClub,
} from '../services/clubService'
import { assignPresident, searchUsers } from '../services/userService'
import { useAuthStore } from '../stores/auth'
import type { Club } from '../types/club'
import ClubAdminView from './ClubAdminView.vue'

const useRouteMock = vi.mocked(useRoute)
const useRouterMock = vi.mocked(useRouter)
const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchClubMembersMock = vi.mocked(fetchClubMembers)
const invalidateClubCacheMock = vi.mocked(invalidateClubCache)
const updateClubMock = vi.mocked(updateClub)
const assignPresidentMock = vi.mocked(assignPresident)
const searchUsersMock = vi.mocked(searchUsers)

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
  fetchClubMembersMock.mockReset()
  fetchClubMembersMock.mockResolvedValue([])
  invalidateClubCacheMock.mockReset()
  updateClubMock.mockReset()
  assignPresidentMock.mockReset()
  searchUsersMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
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

  it('refreshes the club snapshot and invalidates list caches after assigning a president', async () => {
    const club = buildClub({ memberCount: 42, canManage: true })
    const refreshedClub = buildClub({ memberCount: 43, canManage: true })
    fetchClubByIdMock.mockResolvedValueOnce(club).mockResolvedValueOnce(refreshedClub)
    searchUsersMock.mockResolvedValue([
      { id: 7, email: 'president@example.com', displayName: 'New President', avatarUrl: null },
    ])
    assignPresidentMock.mockResolvedValue()
    const authStore = useAuthStore()
    authStore.currentUser = {
      id: '1',
      email: 'owner@example.com',
      displayName: 'Owner',
      avatarUrl: '',
      provider: 'google',
      isOwner: true,
    }

    const wrapper = await mountView(club)
    await wrapper.find('input[type="search"]').setValue('president@example.com')
    await wrapper.find('.president-search button').trigger('click')
    await flushPromises()
    await wrapper.find('.president-search .member-entry button').trigger('click')
    await flushPromises()

    expect(assignPresidentMock).toHaveBeenCalledWith(club.id, 7)
    expect(invalidateClubCacheMock).toHaveBeenCalledTimes(1)
    expect(fetchClubByIdMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('43 members')
  })

  it('updates the displayed image and invalidates list caches after an image upload', async () => {
    const club = buildClub({ imageUrl: 'https://cdn.example.com/old.png' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ imageUrl: 'https://cdn.example.com/new.png' }),
    }))
    const wrapper = await mountView(club)
    const input = wrapper.find<HTMLInputElement>('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'club.png', { type: 'image/png' })],
    })

    await input.trigger('change')
    await flushPromises()

    expect(invalidateClubCacheMock).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.club-avatar img').attributes('src')).toBe('https://cdn.example.com/new.png')
  })

  it('shows an ordinary error body verbatim when the image upload fails', async () => {
    const club = buildClub({ imageUrl: 'https://cdn.example.com/old.png' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      text: async () => 'Unsupported image type. Supported formats are JPEG, PNG, WebP, and GIF; HEIC is not supported.',
    }))
    const wrapper = await mountView(club)
    const input = wrapper.find<HTMLInputElement>('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'club.heic', { type: 'image/heic' })],
    })

    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('.upload-error').text()).toBe(
      'Unsupported image type. Supported formats are JPEG, PNG, WebP, and GIF; HEIC is not supported.',
    )
    expect(invalidateClubCacheMock).not.toHaveBeenCalled()
  })

  // Spring's own multipart-size rejection (see ApiExceptionHandler and
  // docs/API.md's 413 row): an application/problem+json body, not plain text.
  it('shows the ProblemDetail detail message when the image upload is rejected as too large', async () => {
    const club = buildClub({ imageUrl: 'https://cdn.example.com/old.png' })
    const problemBody = JSON.stringify({
      title: 'Content Too Large',
      status: 413,
      detail: 'The uploaded file is too large. Please choose a smaller file and try again.',
      instance: '/api/clubs/1/image',
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 413,
      headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/problem+json' : null) },
      text: async () => problemBody,
    }))
    const wrapper = await mountView(club)
    const input = wrapper.find<HTMLInputElement>('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['image'], 'huge.png', { type: 'image/png' })],
    })

    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('.upload-error').text()).toBe(
      'The uploaded file is too large. Please choose a smaller file and try again.',
    )
    expect(invalidateClubCacheMock).not.toHaveBeenCalled()
  })
})
