import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Club } from '../types/club'
import ClubSearchView from './ClubSearchView.vue'

const { fetchAllClubsMock } = vi.hoisted(() => ({
  fetchAllClubsMock: vi.fn(),
}))

vi.mock('../services/clubService', () => ({
  fetchAllClubs: fetchAllClubsMock,
}))

const club: Club = {
  id: 1,
  name: 'Chess Club',
  aliasName: null,
  description: 'Casual and competitive play',
  category: 'Competition & Strategy',
  meetingSchedule: 'Wednesday lunch',
  location: 'Room 214',
  contactEmail: null,
  advisor: 'Ms. Lee',
  imageUrl: null,
  memberCount: 12,
  achievements: [],
}

const mountSearch = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/search', name: 'club-search', component: ClubSearchView },
      { path: '/clubs/:id', name: 'club-detail', component: { template: '<div />' } },
    ],
  })
  await router.push('/search?q=chess')
  await router.isReady()
  return mount(ClubSearchView, { global: { plugins: [router] } })
}

beforeEach(() => {
  fetchAllClubsMock.mockReset()
})

describe('search result status', () => {
  it('does not claim there are zero matches while clubs are still loading', async () => {
    let resolveClubs!: (clubs: Club[]) => void
    fetchAllClubsMock.mockReturnValue(
      new Promise<Club[]>((resolve) => {
        resolveClubs = resolve
      }),
    )

    const wrapper = await mountSearch()
    expect(wrapper.text()).not.toContain('0 matching clubs')
    expect(wrapper.text()).not.toContain('Enter a club name')

    resolveClubs([club])
    await flushPromises()
    expect(wrapper.text()).toContain('1 matching club')
  })

  it('shows only the load error instead of an incorrect zero-match count', async () => {
    fetchAllClubsMock.mockRejectedValue(new Error('Directory unavailable'))

    const wrapper = await mountSearch()
    await flushPromises()

    expect(wrapper.text()).toContain('Directory unavailable')
    expect(wrapper.text()).not.toContain('0 matching clubs')
    expect(wrapper.text()).not.toContain('Enter a club name')
  })
})
