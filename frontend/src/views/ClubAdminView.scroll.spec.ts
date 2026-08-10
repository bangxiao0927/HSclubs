import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
  fetchClubMembers: vi.fn(),
  fetchMembershipRequests: vi.fn(),
  invalidateClubCache: vi.fn(),
  updateClub: vi.fn(),
  updateClubMemberRole: vi.fn(),
}))

vi.mock('../services/userService', () => ({
  searchUsers: vi.fn(),
  assignPresident: vi.fn(),
  removePresident: vi.fn(),
}))

import { fetchClubById, fetchClubMembers, fetchMembershipRequests } from '../services/clubService'
import type { Club } from '../types/club'
import ClubAdminView from './ClubAdminView.vue'

const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchClubMembersMock = vi.mocked(fetchClubMembers)
const fetchMembershipRequestsMock = vi.mocked(fetchMembershipRequests)

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
  canManage: true,
  ...overrides,
})

let router: Router

const mountAtAdminRoute = async (destination: string | { path: string; hash?: string }) => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/clubs/:id', name: 'club-detail', component: { template: '<div />' } },
      { path: '/clubs/:id/admin', name: 'club-admin', component: ClubAdminView },
    ],
  })
  await router.push(destination)
  await router.isReady()
  return mount(ClubAdminView, { global: { plugins: [router] } })
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchClubByIdMock.mockReset()
  fetchClubMembersMock.mockReset()
  fetchClubMembersMock.mockResolvedValue([])
  fetchMembershipRequestsMock.mockReset()
  fetchMembershipRequestsMock.mockResolvedValue([])
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const stubScrollIntoView = () => {
  const spy = vi.fn()
  const original = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    value: spy,
    configurable: true,
    writable: true,
  })
  return {
    spy,
    restore: () => {
      if (original) {
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', original)
      } else {
        delete (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView
      }
    },
  }
}

describe('legacy club admin pending redirect scroll behavior', () => {
  it('scrolls the #members section into view once the club has loaded when arriving with a #members hash', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtAdminRoute({ path: '/clubs/1/admin', hash: '#members' })
      await flushPromises()

      expect(scrollIntoViewSpy).toHaveBeenCalled()
    } finally {
      restore()
    }
  })

  it('does not attempt to scroll when there is no #members hash', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtAdminRoute('/clubs/1/admin')
      await flushPromises()

      expect(scrollIntoViewSpy).not.toHaveBeenCalled()
    } finally {
      restore()
    }
  })
})

describe('navigating to #members on an already-loaded club admin page', () => {
  it('scrolls to the #members section when the hash changes to #members without refetching the club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtAdminRoute('/clubs/1/admin')
      await flushPromises()
      expect(scrollIntoViewSpy).not.toHaveBeenCalled()

      await router.push({ path: '/clubs/1/admin', hash: '#members' })
      await flushPromises()

      expect(scrollIntoViewSpy).toHaveBeenCalled()
      expect(fetchClubByIdMock).toHaveBeenCalledTimes(1)
    } finally {
      restore()
    }
  })
})
