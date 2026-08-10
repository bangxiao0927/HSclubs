import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
  fetchAllClubs: vi.fn(),
  applyToClub: vi.fn(),
  cancelMembershipRequest: vi.fn(),
}))

vi.mock('../services/clubPostService', () => ({
  fetchClubMediaFeed: vi.fn(),
  fetchClubPostComments: vi.fn(),
  publishClubPost: vi.fn(),
  deleteClubPost: vi.fn(),
  pinClubPost: vi.fn(),
  unpinClubPost: vi.fn(),
  createClubPostComment: vi.fn(),
  deleteClubPostComment: vi.fn(),
}))

import { fetchAllClubs, fetchClubById } from '../services/clubService'
import { fetchClubMediaFeed } from '../services/clubPostService'
import type { Club } from '../types/club'
import type { ClubPostFeedPage } from '../types/clubPost'
import ClubDetailView from './ClubDetailView.vue'

const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchAllClubsMock = vi.mocked(fetchAllClubs)
const fetchClubMediaFeedMock = vi.mocked(fetchClubMediaFeed)

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
  viewerIsMember: false,
  canManage: false,
  ...overrides,
})

const buildFeed = (overrides: Partial<ClubPostFeedPage> = {}): ClubPostFeedPage => ({
  items: [],
  page: 0,
  size: 12,
  total: 0,
  ...overrides,
})

let router: Router

const mountAtClubRoute = async (destination: string | { path: string; hash?: string } = '/clubs/1') => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/clubs/:id', name: 'club-detail', component: ClubDetailView }],
  })
  await router.push(destination)
  await router.isReady()
  return mount(ClubDetailView, { global: { plugins: [router] } })
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchClubByIdMock.mockReset()
  fetchAllClubsMock.mockReset()
  fetchClubMediaFeedMock.mockReset()
  fetchAllClubsMock.mockResolvedValue([])
  fetchClubMediaFeedMock.mockResolvedValue(buildFeed())
})

afterEach(() => {
  document.head.querySelectorAll('meta[name="robots"]').forEach((node) => node.remove())
})

describe('ClubDetailView embedded club media', () => {
  it('renders the club media feed below the club details, under a stable #media anchor', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    const wrapper = await mountAtClubRoute()
    await flushPromises()

    const mediaSection = wrapper.find('#media')
    expect(mediaSection.exists()).toBe(true)
    expect(mediaSection.text()).toContain('Club media')
  })

  it('does not render a nested back-to-club control or page-shell layout inside the media section', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    const wrapper = await mountAtClubRoute()
    await flushPromises()

    const mediaSection = wrapper.find('#media')
    expect(mediaSection.find('.app-back-button').exists()).toBe(false)
    expect(mediaSection.find('.page-shell').exists()).toBe(false)
  })

  it('uses a non-top-level heading for the embedded media section, leaving the club name as the only h1', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    const wrapper = await mountAtClubRoute()
    await flushPromises()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('h1').text()).toBe('Chess Club')
    expect(wrapper.find('#media h2').text()).toBe('Club media')
  })

  it('does not set the page robots meta tag to noindex', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    await mountAtClubRoute()
    await flushPromises()

    expect(document.head.querySelector('meta[name="robots"]')).toBeNull()
  })

  it('no longer shows a separate "View club media" link in the hero', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    const wrapper = await mountAtClubRoute()
    await flushPromises()

    expect(wrapper.text()).not.toContain('View club media')
  })
})

describe('ClubDetailView club GET deduplication', () => {
  it('fetches the club exactly once, passing the loaded snapshot to the embedded media view', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())

    await mountAtClubRoute()
    await flushPromises()

    expect(fetchClubByIdMock).toHaveBeenCalledTimes(1)
  })
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

describe('legacy club media redirect scroll behavior', () => {
  it('scrolls the #media section into view once the club has loaded when arriving with a #media hash', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtClubRoute({ path: '/clubs/1', hash: '#media' })
      await flushPromises()

      expect(scrollIntoViewSpy).toHaveBeenCalled()
    } finally {
      restore()
    }
  })

  it('does not attempt to scroll when there is no #media hash', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtClubRoute('/clubs/1')
      await flushPromises()

      expect(scrollIntoViewSpy).not.toHaveBeenCalled()
    } finally {
      restore()
    }
  })
})

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
}

const createDeferred = <T,>(): Deferred<T> => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

describe('overlapping club loads from a related-club link', () => {
  it('does not render a stale club when an earlier load resolves after a later one', async () => {
    const clubOne = buildClub({ id: 1, name: 'Chess Club' })
    const clubTwo = buildClub({ id: 2, name: 'Robotics Club' })
    const deferredOne = createDeferred<Club>()
    const deferredTwo = createDeferred<Club>()
    fetchClubByIdMock.mockImplementation((id: string | number) =>
      id === '1' ? deferredOne.promise : deferredTwo.promise
    )
    fetchAllClubsMock.mockResolvedValue([])

    router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/clubs/:id', name: 'club-detail', component: ClubDetailView }],
    })
    await router.push('/clubs/1')
    await router.isReady()
    const wrapper = mount(ClubDetailView, { global: { plugins: [router] } })
    await flushPromises()

    // The viewer follows a related-club link before club 1's own request has resolved.
    await router.push('/clubs/2')
    await flushPromises()

    // Club 1's slower request resolves last, after club 2's -- it must be dropped, not
    // overwrite the club 2 record already on screen at /clubs/2.
    deferredTwo.resolve(clubTwo)
    await flushPromises()
    deferredOne.resolve(clubOne)
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Robotics Club')
  })
})

describe('navigating to #media on an already-loaded club', () => {
  it('scrolls to the #media section when the hash changes to #media without refetching the club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    const { spy: scrollIntoViewSpy, restore } = stubScrollIntoView()

    try {
      await mountAtClubRoute('/clubs/1')
      await flushPromises()
      expect(scrollIntoViewSpy).not.toHaveBeenCalled()

      await router.push({ path: '/clubs/1', hash: '#media' })
      await flushPromises()

      expect(scrollIntoViewSpy).toHaveBeenCalled()
      expect(fetchClubByIdMock).toHaveBeenCalledTimes(1)
    } finally {
      restore()
    }
  })
})
