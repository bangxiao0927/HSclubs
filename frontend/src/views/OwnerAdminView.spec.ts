import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../services/clubService', () => ({
  fetchAllClubs: vi.fn(),
  fetchClubCount: vi.fn(),
  createClub: vi.fn(),
}))

import { fetchAuthenticatedUser } from '../services/authService'
import { fetchAllClubs, fetchClubCount } from '../services/clubService'
import type { AuthUser } from '../types/auth'
import type { Club } from '../types/club'
import { useAuthStore } from '../stores/auth'
import OwnerAdminView from './OwnerAdminView.vue'

const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)
const fetchAllClubsMock = vi.mocked(fetchAllClubs)
const fetchClubCountMock = vi.mocked(fetchClubCount)

const ownerUser: AuthUser = {
  id: 'owner-1',
  email: 'owner@example.com',
  displayName: 'Site Owner',
  avatarUrl: '',
  provider: 'google',
  isOwner: true,
  graduationYear: 2026,
  acceptedTerms: true,
}

// Production has 106 clubs, one more than the backend's single-page cap of
// 100 (ClubService.findAllPaginated clamps `size` to 100). This builds the
// full 106-club roster the owner dashboard is expected to show.
const buildClubs = (count: number): Club[] =>
  Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    name: `Club ${index + 1}`,
    slug: null,
    aliasName: null,
    description: '',
    category: 'General',
    meetingSchedule: '',
    location: null,
    contactEmail: null,
    advisor: null,
    imageUrl: null,
    memberCount: 0,
    achievements: [],
  }))

// Primes the auth store the way the router guard does on a real visit, then
// mounts the view so its `watch(isOwner, ..., { immediate: true })` fires.
const mountAsOwner = async () => {
  fetchAuthenticatedUserMock.mockResolvedValue(ownerUser)
  await useAuthStore().ensureSessionChecked()
  const wrapper = mount(OwnerAdminView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchAuthenticatedUserMock.mockReset()
  fetchAllClubsMock.mockReset()
  fetchClubCountMock.mockReset()
})

describe('OwnerAdminView', () => {
  it('shows every club, not just the backend single page cap of the first 50 or 100', async () => {
    const allClubs = buildClubs(106)
    fetchAllClubsMock.mockResolvedValue(allClubs)
    fetchClubCountMock.mockResolvedValue(106)

    const wrapper = await mountAsOwner()

    expect(wrapper.findAll('.club-row')).toHaveLength(106)
  })
})
