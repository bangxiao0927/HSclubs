import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
}))

vi.mock('../services/clubPostService', () => ({
  fetchClubMediaFeed: vi.fn(),
  fetchClubPostComments: vi.fn(),
}))

import { fetchClubById } from '../services/clubService'
import { fetchClubMediaFeed, fetchClubPostComments } from '../services/clubPostService'
import type { Club } from '../types/club'
import type { ClubPost, ClubPostComment, ClubPostFeedPage } from '../types/clubPost'
import ClubMediaView from './ClubMediaView.vue'

const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchClubMediaFeedMock = vi.mocked(fetchClubMediaFeed)
const fetchClubPostCommentsMock = vi.mocked(fetchClubPostComments)

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

const buildPost = (overrides: Partial<ClubPost> = {}): ClubPost => ({
  id: 1,
  clubId: 1,
  title: 'Weekly meeting recap',
  imageUrl: '/uploads/club-posts/a.jpg',
  pinnedAt: null,
  createdAt: new Date().toISOString(),
  authorDisplayName: 'Ada Lovelace',
  authorAvatarUrl: '/uploads/avatar-cache/ada.jpg',
  commentCount: 0,
  ...overrides,
})

const buildFeed = (overrides: Partial<ClubPostFeedPage> = {}): ClubPostFeedPage => ({
  items: [],
  page: 0,
  size: 12,
  total: 0,
  ...overrides,
})

const buildComment = (overrides: Partial<ClubPostComment> = {}): ClubPostComment => ({
  id: 1,
  postId: 1,
  authorDisplayName: 'Grace Hopper',
  authorAvatarUrl: null,
  body: 'Great photo!',
  createdAt: new Date().toISOString(),
  ...overrides,
})

let router: Router

const mountAtMediaRoute = async (path = '/clubs/1/media') => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/clubs/:id/media', name: 'club-media', component: ClubMediaView }],
  })
  await router.push(path)
  await router.isReady()
  return mount(ClubMediaView, { global: { plugins: [router] } })
}

beforeEach(() => {
  fetchClubByIdMock.mockReset()
  fetchClubMediaFeedMock.mockReset()
  fetchClubPostCommentsMock.mockReset()
})

afterEach(() => {
  document.head.querySelectorAll('meta[name="robots"]').forEach((node) => node.remove())
})

describe('ClubMediaView loading, error and empty states', () => {
  it('shows a loading state before the club and feed have resolved', async () => {
    fetchClubByIdMock.mockReturnValue(new Promise(() => {}))
    fetchClubMediaFeedMock.mockReturnValue(new Promise(() => {}))

    const wrapper = await mountAtMediaRoute()

    expect(wrapper.text()).toContain('Loading club media')
  })

  it('shows an error state and a retry action when the feed fails to load', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockRejectedValue(new Error('Club not found'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.text()).toContain('Club not found')
    expect(wrapper.find('button.mv-retry-btn').exists()).toBe(true)
  })

  it('retries loading when the retry button is clicked', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockRejectedValueOnce(new Error('Network error'))
    fetchClubMediaFeedMock.mockResolvedValueOnce(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    expect(wrapper.text()).toContain('Network error')

    await wrapper.find('button.mv-retry-btn').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No posts yet.')
  })

  it('shows an empty state when the club has no posts yet', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.text()).toContain('No posts yet.')
    expect(wrapper.findAll('li.mv-post-card')).toHaveLength(0)
  })
})

describe('ClubMediaView populated feed', () => {
  it('renders each post exactly once with its title, author, avatar, comment count, and a pinned badge only on the pinned post', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Pinned announcement', pinnedAt: '2024-06-01T00:00:00Z', commentCount: 3 }),
          buildPost({ id: 2, title: 'Regular update', pinnedAt: null, commentCount: 0 }),
        ],
        total: 2,
      }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const cards = wrapper.findAll('li.mv-post-card')
    expect(cards).toHaveLength(2)
    expect(wrapper.text()).toContain('Pinned announcement')
    expect(wrapper.text()).toContain('Regular update')
    expect(wrapper.text()).toContain('Ada Lovelace')
    expect(wrapper.text()).toContain('Show comments (3)')
    expect(wrapper.text()).toContain('Show comments (0)')

    const badges = wrapper.findAll('.mv-badge-pinned')
    expect(badges).toHaveLength(1)
    expect(cards[0]!.find('.mv-badge-pinned').exists()).toBe(true)
    expect(cards[1]!.find('.mv-badge-pinned').exists()).toBe(false)
  })

  it('shows a relative time for each post', async () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ createdAt: fiveMinutesAgo })], total: 1 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-post-time').text()).toContain('5 minutes ago')
  })

  it('routes the post image and author avatar through buildApiUrl for an absolute API origin, and lazy-loads them', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({
        items: [buildPost({ imageUrl: '/uploads/club-posts/a.jpg', authorAvatarUrl: '/uploads/avatar-cache/ada.jpg' })],
        total: 1,
      }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const postImage = wrapper.find('.mv-post-image')
    const avatarImage = wrapper.find('.mv-author-avatar')

    expect(postImage.attributes('src')).toBe('http://localhost:8080/uploads/club-posts/a.jpg')
    expect(postImage.attributes('loading')).toBe('lazy')
    expect(avatarImage.attributes('src')).toBe('http://localhost:8080/uploads/avatar-cache/ada.jpg')
    expect(avatarImage.attributes('loading')).toBe('lazy')
  })
})

describe('ClubMediaView pagination', () => {
  it('requests the next page using the page and size the envelope reported, not the values requested', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    // The request asks for size=999; the server clamps that to 100 and echoes the clamp back.
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1 })], page: 0, size: 100, total: 250 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 2 })], page: 1, size: 100, total: 250 }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=999')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(1, '1', 0, 999)
    expect(wrapper.text()).toContain('Page 1 of 3')

    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(2, '1', 1, 100)
    expect(wrapper.text()).toContain('Page 2 of 3')
  })

  it('disables the previous button on the first page and the next button on the last page', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost()], page: 0, size: 12, total: 1 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const [previousButton, nextButton] = wrapper.findAll('.mv-pagination button')
    expect(previousButton!.attributes('disabled')).toBeDefined()
    expect(nextButton!.attributes('disabled')).toBeDefined()
  })

  it('does not refetch the unchanged club detail when only the page changes', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1 })], page: 0, size: 12, total: 24 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 2 })], page: 1, size: 12, total: 24 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    expect(fetchClubByIdMock).toHaveBeenCalledTimes(1)

    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(2)
    expect(fetchClubByIdMock).toHaveBeenCalledTimes(1)
  })
})

describe('ClubMediaView comments', () => {
  it('loads and shows escaped comments when a post is expanded', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([
      buildComment({ id: 1, body: '<script>alert(1)</script>' }),
    ])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-comment').exists()).toBe(false)

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(fetchClubPostCommentsMock).toHaveBeenCalledWith('1', 7)
    const commentBody = wrapper.find('.mv-comment-body')
    expect(commentBody.text()).toBe('<script>alert(1)</script>')
    expect(commentBody.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('Grace Hopper')
  })

  it('shows a comments error state and does not render a comment list', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockRejectedValue(new Error('Failed to load comments'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to load comments')
    expect(wrapper.find('.mv-comment-list').exists()).toBe(false)
  })

  it('retries the comments fetch when a post is collapsed and re-expanded after a failed load', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockRejectedValueOnce(new Error('Network error'))
    fetchClubPostCommentsMock.mockResolvedValueOnce([buildComment({ body: 'Loaded on retry' })])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Network error')

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(fetchClubPostCommentsMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Loaded on retry')
    expect(wrapper.text()).not.toContain('Network error')
  })

  it('shows a comments empty state when a post has no comments', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No comments yet.')
  })

  it('collapses the comments and hides the loaded list when toggled again', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([buildComment()])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const isCommentsRegionHidden = () =>
      (wrapper.find('.mv-comments').attributes('style') ?? '').includes('display: none')

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    expect(isCommentsRegionHidden()).toBe(false)

    await wrapper.find('.mv-comments-toggle').trigger('click')
    expect(isCommentsRegionHidden()).toBe(true)

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(fetchClubPostCommentsMock).toHaveBeenCalledTimes(1)
    expect(isCommentsRegionHidden()).toBe(false)
  })

  it('exposes aria-expanded and aria-controls on the toggle, pointing at a stable comment region id', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 42 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([buildComment()])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const toggle = wrapper.find('.mv-comments-toggle')
    const regionId = toggle.attributes('aria-controls')
    expect(regionId).toBeTruthy()
    expect(toggle.attributes('aria-expanded')).toBe('false')

    const region = wrapper.find(`#${regionId}`)
    expect(region.exists()).toBe(true)
    expect(region.classes()).toContain('mv-comments')

    await toggle.trigger('click')
    await flushPromises()

    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(toggle.attributes('aria-controls')).toBe(regionId)
  })
})

describe('ClubMediaView robots meta', () => {
  it('sets a noindex robots meta tag while mounted and restores prior head state on unmount', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const meta = document.head.querySelector('meta[name="robots"]')
    expect(meta?.getAttribute('content')).toBe('noindex')

    wrapper.unmount()

    expect(document.head.querySelector('meta[name="robots"]')).toBeNull()
  })

  it('restores a pre-existing robots meta tag content on unmount instead of removing it', async () => {
    const existing = document.createElement('meta')
    existing.setAttribute('name', 'robots')
    existing.setAttribute('content', 'index, follow')
    document.head.appendChild(existing)

    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex')

    wrapper.unmount()

    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('index, follow')
  })
})

// This file's tsconfig scope (tsconfig.app.json) has no "node" types, so `process` is declared
// locally here rather than pulling in @types/node project-wide for one test's TZ override.
declare const process: { env: Record<string, string | undefined> }

describe('ClubMediaView relative time under a non-UTC browser timezone', () => {
  // Both post.createdAt and comment.createdAt are java.time.Instant on the backend, always
  // serialized with an explicit "Z" (see PublicClubPost's, PublicClubPostComment's, and
  // EpochSecondsInstantTypeHandler's Javadoc). Forcing a real, non-UTC host timezone here
  // proves formatRelativeTime's plain `new Date(iso)` -- no "guess the offset" workaround --
  // still can't misread a just-created post or a just-posted comment as being in the future
  // purely because of the viewer's own timezone.
  const originalTz = process.env.TZ

  beforeAll(() => {
    process.env.TZ = 'America/New_York'
  })

  afterAll(() => {
    process.env.TZ = originalTz
  })

  it('renders a just-created post as recent, never as being in the future, given the backend\'s offset-bearing instant', async () => {
    const justCreatedInstant = new Date().toISOString()
    expect(justCreatedInstant).toMatch(/Z$/)

    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ createdAt: justCreatedInstant })], total: 1 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const relativeTimeText = wrapper.find('.mv-post-time').text()
    expect(relativeTimeText).not.toMatch(/^in /)
  })

  it('renders a just-posted comment as recent, never as being in the future, given the backend\'s offset-bearing instant', async () => {
    const justPostedInstant = new Date().toISOString()
    expect(justPostedInstant).toMatch(/Z$/)

    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([buildComment({ createdAt: justPostedInstant })])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    const relativeTimeText = wrapper.find('.mv-comment-time').text()
    expect(relativeTimeText).not.toMatch(/^in /)
  })
})
