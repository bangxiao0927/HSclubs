import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/clubService', () => ({
  fetchClubById: vi.fn(),
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

import { fetchClubById } from '../services/clubService'
import {
  fetchClubMediaFeed,
  fetchClubPostComments,
  publishClubPost,
  deleteClubPost,
  pinClubPost,
  unpinClubPost,
  createClubPostComment,
  deleteClubPostComment,
} from '../services/clubPostService'
import { useAuthStore } from '../stores/auth'
import type { AuthUser } from '../types/auth'
import type { Club } from '../types/club'
import type { ClubPost, ClubPostComment, ClubPostFeedPage } from '../types/clubPost'
import ClubMediaView from './ClubMediaView.vue'

const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchClubMediaFeedMock = vi.mocked(fetchClubMediaFeed)
const fetchClubPostCommentsMock = vi.mocked(fetchClubPostComments)
const publishClubPostMock = vi.mocked(publishClubPost)
const deleteClubPostMock = vi.mocked(deleteClubPost)
const pinClubPostMock = vi.mocked(pinClubPost)
const unpinClubPostMock = vi.mocked(unpinClubPost)
const createClubPostCommentMock = vi.mocked(createClubPostComment)
const deleteClubPostCommentMock = vi.mocked(deleteClubPostComment)

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
  viewerCanDelete: false,
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
  viewerCanDelete: false,
  ...overrides,
})

const buildAuthUser = (overrides: Partial<AuthUser> = {}): AuthUser => ({
  id: '1',
  email: 'owner@example.com',
  displayName: 'Platform Owner',
  avatarUrl: '',
  provider: 'google',
  isOwner: true,
  ...overrides,
})

// Lets a test control exactly when and how a mocked fetch call settles (resolve or reject), so
// an ordering scenario (an older request's response arriving after a newer one) can be
// reproduced deterministically instead of hoping a real race happens to line up the same way.
interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason: unknown) => void
}

const createDeferred = <T,>(): Deferred<T> => {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

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
  setActivePinia(createPinia())
  fetchClubByIdMock.mockReset()
  fetchClubMediaFeedMock.mockReset()
  fetchClubPostCommentsMock.mockReset()
  publishClubPostMock.mockReset()
  deleteClubPostMock.mockReset()
  pinClubPostMock.mockReset()
  unpinClubPostMock.mockReset()
  createClubPostCommentMock.mockReset()
  deleteClubPostCommentMock.mockReset()
  vi.stubGlobal('URL', Object.assign(URL, {
    createObjectURL: vi.fn(() => 'blob:mock-preview'),
    revokeObjectURL: vi.fn(),
  }))
})

afterEach(() => {
  document.head.querySelectorAll('meta[name="robots"]').forEach((node) => node.remove())
  vi.unstubAllGlobals()
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

describe('ClubMediaView out-of-range pagination', () => {
  // Backend clampPage only ever clamps to max(0, page); it has no way to know the current
  // total when the request is made, so an out-of-range ?page=N (a stale bookmark, or a Next
  // click against a total another viewer has since shrunk) returns an empty page while
  // total > 0. That must read differently from -- and stay navigable unlike -- a club that
  // genuinely has zero posts.
  it('distinguishes an out-of-range page from a genuinely empty club, and keeps the pagination nav reachable', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [], page: 5, size: 12, total: 36 }))

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=5')
    await flushPromises()

    expect(wrapper.text()).not.toContain('No posts yet.')
    expect(wrapper.find('nav.mv-pagination').exists()).toBe(true)
    expect(wrapper.find('.mv-pagination button:first-of-type').attributes('disabled')).toBeUndefined()
  })

  it('still shows the genuinely-empty copy and hides the pagination nav when the club truly has no posts', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [], page: 0, size: 12, total: 0 }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.text()).toContain('No posts yet.')
    expect(wrapper.find('nav.mv-pagination').exists()).toBe(false)
  })

  it('can navigate back to a valid page from an out-of-range page using the still-reachable Previous button', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(buildFeed({ items: [], page: 5, size: 12, total: 36 }))
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1' })], page: 4, size: 12, total: 36 }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=5')
    await flushPromises()

    await wrapper.find('.mv-pagination button:first-of-type').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Post 1')
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

  // A stale post/comment action error or an unsent comment draft must never survive a feed
  // context reload: it is scoped to a specific post id on the page the viewer just left, and a
  // later page reusing that same id (however unlikely in production) must start clean rather
  // than silently resurface someone else's leftover error text or half-typed comment.
  it('clears a stale per-post action error and an unsent comment draft when the page changes', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 7, viewerCanDelete: true })], page: 0, size: 12, total: 24 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 7 })], page: 1, size: 12, total: 24 }),
    )
    fetchClubPostCommentsMock.mockResolvedValue([])
    deleteClubPostMock.mockRejectedValue(new Error('You do not have access to delete this post'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('You do not have access to delete this post')

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    await wrapper.find('.mv-comment-form .mv-comment-input').setValue('Draft that should not survive a page change')
    expect(wrapper.find<HTMLTextAreaElement>('.mv-comment-form .mv-comment-input').element.value).toBe(
      'Draft that should not survive a page change',
    )

    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('You do not have access to delete this post')

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.find<HTMLTextAreaElement>('.mv-comment-form .mv-comment-input').element.value).toBe('')
  })
})

describe('ClubMediaView load staleness across overlapping navigations', () => {
  // Route changes (Back/Forward, or a fast page-to-page navigation) can overlap: the app is
  // reachable while an earlier load() is still in flight, and there is no guarantee the
  // network responses resolve in the same order the requests were issued.
  it('ignores a stale successful load response that resolves after a newer navigation already applied its own response', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1' })], page: 0, size: 12, total: 36 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    expect(wrapper.text()).toContain('Post 1')

    const olderNavigation = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(olderNavigation.promise)
    await router.push('/clubs/1/media?page=1')
    await flushPromises()

    const newerNavigation = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(newerNavigation.promise)
    await router.push('/clubs/1/media?page=2')
    await flushPromises()

    newerNavigation.resolve(buildFeed({ items: [buildPost({ id: 3, title: 'Post 3' })], page: 2, size: 12, total: 36 }))
    await flushPromises()
    expect(wrapper.text()).toContain('Post 3')

    olderNavigation.resolve(buildFeed({ items: [buildPost({ id: 2, title: 'Post 2' })], page: 1, size: 12, total: 36 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Post 3')
    expect(wrapper.text()).not.toContain('Post 2')
    expect(wrapper.find('.mv-status').exists()).toBe(false)
  })

  it('ignores a stale failed load response that rejects after a newer navigation already applied its own response', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1' })], page: 0, size: 12, total: 36 }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const olderNavigation = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(olderNavigation.promise)
    await router.push('/clubs/1/media?page=1')
    await flushPromises()

    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 3, title: 'Post 3' })], page: 2, size: 12, total: 36 }),
    )
    await router.push('/clubs/1/media?page=2')
    await flushPromises()
    expect(wrapper.text()).toContain('Post 3')

    olderNavigation.reject(new Error('Network error from an abandoned request'))
    await flushPromises()

    expect(wrapper.text()).toContain('Post 3')
    expect(wrapper.text()).not.toContain('Network error from an abandoned request')
    expect(wrapper.find('.mv-status--error').exists()).toBe(false)
  })

  it('ignores a stale load response that resolves after navigating to a different club before it settled', async () => {
    fetchClubByIdMock.mockImplementation(async (idOrSlug: unknown) =>
      buildClub({ name: idOrSlug === '1' ? 'Chess Club' : 'Robotics Club', viewerIsMember: idOrSlug === '2' }),
    )
    const club1Feed = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(club1Feed.promise)

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 9, title: 'Robotics post' })], total: 1 }),
    )
    await router.push('/clubs/2/media')
    await flushPromises()
    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')

    club1Feed.resolve(buildFeed({ items: [buildPost({ id: 1, title: 'Chess post' })], total: 1 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')
    expect(wrapper.text()).not.toContain('Chess post')
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

describe('ClubMediaView publish form', () => {
  it('hides the publish form from a non-member', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: false }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-publish').exists()).toBe(false)
  })

  it('shows the publish form, a public-visibility notice, and the supported formats to a member', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const publishSection = wrapper.find('.mv-publish')
    expect(publishSection.exists()).toBe(true)
    expect(publishSection.text()).toContain('visible to anyone')
    expect(publishSection.text()).toContain('logged in')
    expect(publishSection.text()).toContain('JPEG')
    expect(publishSection.text()).toContain('PNG')
    expect(publishSection.text()).toContain('WebP')
    expect(publishSection.text()).toContain('GIF')
    expect(publishSection.text()).toContain('HEIC')
    expect(publishSection.text()).toContain('not supported')
    // WebP's much stricter resolution cap (see docs/API.md) means it should not be advertised
    // as an unconditionally-supported format alongside JPEG/PNG/GIF.
    expect(publishSection.text()).toContain('3 megapixels')
  })

  it('counts down the title character limit as the member types', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-publish .mv-counter').text()).toBe('0/140')

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')

    expect(wrapper.find('.mv-publish .mv-counter').text()).toBe('13/140')
  })

  it('shows a local preview of the selected photo via URL.createObjectURL and revokes it when a new file replaces it', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-publish-preview').exists()).toBe(false)

    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    const firstFile = new File(['a'], 'first.jpg', { type: 'image/jpeg' })
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [firstFile] })
    await fileInput.trigger('change')

    expect(URL.createObjectURL).toHaveBeenCalledWith(firstFile)
    expect(wrapper.find('.mv-publish-preview').attributes('src')).toBe('blob:mock-preview')

    const secondFile = new File(['b'], 'second.jpg', { type: 'image/jpeg' })
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [secondFile] })
    await fileInput.trigger('change')

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-preview')
    expect(URL.createObjectURL).toHaveBeenCalledWith(secondFile)
  })

  it('revokes the preview URL on unmount', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['a'], 'first.jpg', { type: 'image/jpeg' })],
    })
    await fileInput.trigger('change')

    wrapper.unmount()

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-preview')
  })

  it('submits the title and file via publishClubPost and prepends the created post without a manual reload', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, title: 'Existing post' })], total: 1 }),
    )
    const created = buildPost({ id: 99, title: 'Meeting recap', viewerCanDelete: true })
    publishClubPostMock.mockResolvedValue(created)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')
    const file = new File(['a'], 'photo.jpg', { type: 'image/jpeg' })
    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [file] })
    await fileInput.trigger('change')

    await wrapper.find('.mv-publish-form').trigger('submit')
    await flushPromises()

    expect(publishClubPostMock).toHaveBeenCalledWith('1', 'Meeting recap', file)
    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(1)
    const titles = wrapper.findAll('.mv-post-title').map((node) => node.text())
    expect(titles).toEqual(['Meeting recap', 'Existing post'])
  })

  it('clears the form after a successful publish', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())
    publishClubPostMock.mockResolvedValue(buildPost({ id: 99 }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')
    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['a'], 'photo.jpg', { type: 'image/jpeg' })],
    })
    await fileInput.trigger('change')

    await wrapper.find('.mv-publish-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find<HTMLInputElement>('.mv-publish-title-input').element.value).toBe('')
    expect(wrapper.find('.mv-publish-preview').exists()).toBe(false)
  })

  it('shows a required-photo message and does not call publishClubPost when no file is selected', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')
    await wrapper.find('.mv-publish-form').trigger('submit')
    await flushPromises()

    expect(publishClubPostMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('A photo is required')
  })

  it('surfaces the server error message verbatim, including the unsupported-format wording', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed())
    publishClubPostMock.mockRejectedValue(
      new Error('Unsupported image type. Supported formats are JPEG, PNG, WebP, and GIF; HEIC is not supported.'),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')
    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['a'], 'photo.heic', { type: 'image/heic' })],
    })
    await fileInput.trigger('change')
    await wrapper.find('.mv-publish-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain(
      'Unsupported image type. Supported formats are JPEG, PNG, WebP, and GIF; HEIC is not supported.',
    )
  })
})

describe('ClubMediaView comment form', () => {
  it('hides the comment form from a non-member', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: false }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.find('.mv-comment-form').exists()).toBe(false)
  })

  it('shows a comment form with a 300 character counter to a member', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    const form = wrapper.find('.mv-comment-form')
    expect(form.exists()).toBe(true)
    expect(form.find('.mv-counter').text()).toBe('0/300')

    await form.find('.mv-comment-input').setValue('Nice photo!')

    expect(form.find('.mv-counter').text()).toBe('11/300')
  })

  it('submits a comment, appends it to the list, and bumps the comment count without a manual reload', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7, commentCount: 0 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValueOnce([])
    fetchClubPostCommentsMock.mockResolvedValueOnce([buildComment({ id: 5, body: 'Nice photo!' })])
    createClubPostCommentMock.mockResolvedValue(buildComment({ id: 5, body: 'Nice photo!' }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    await wrapper.find('.mv-comment-form .mv-comment-input').setValue('Nice photo!')
    await wrapper.find('.mv-comment-form').trigger('submit')
    await flushPromises()

    expect(createClubPostCommentMock).toHaveBeenCalledWith('1', 7, 'Nice photo!')
    expect(wrapper.find('.mv-comment-body').text()).toBe('Nice photo!')
    expect(wrapper.text()).toContain('Hide comments')
    expect(wrapper.find('.mv-comment-form .mv-comment-input').element).toHaveProperty('value', '')

    // The comment count comes from the reconciled server list's own length (1), not the
    // optimistic +1 layered on top of it -- collapsing back to the toggle's own label is what
    // surfaces post.commentCount, proving the two were never added together.
    await wrapper.find('.mv-comments-toggle').trigger('click')
    expect(wrapper.text()).toContain('Show comments (1)')
  })

  it('surfaces the comment cap error message verbatim', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([])
    createClubPostCommentMock.mockRejectedValue(new Error('This post already has 50 comments'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    await wrapper.find('.mv-comment-form .mv-comment-input').setValue('One more!')
    await wrapper.find('.mv-comment-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This post already has 50 comments')
  })

  // The final #82 review finding: a comments GET issued before a submit (e.g. from expanding
  // the panel) can still be in flight when that submit's own reconciliation fetch completes.
  // Deterministic via a manually-resolved (deferred) promise for the original GET, so the exact
  // "older request settles after the newer one" ordering is forced rather than hoped for.
  it('shows the complete server list and ignores a stale GET that resolves after a submit made while the initial load was still pending', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7, commentCount: 1 })], total: 1 }))
    const initialGetDeferred = createDeferred<ClubPostComment[]>()
    fetchClubPostCommentsMock.mockReturnValueOnce(initialGetDeferred.promise)
    fetchClubPostCommentsMock.mockResolvedValueOnce([
      buildComment({ id: 5, body: 'Earlier comment' }),
      buildComment({ id: 6, body: 'Nice photo!' }),
    ])
    createClubPostCommentMock.mockResolvedValue(buildComment({ id: 6, body: 'Nice photo!' }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    // Expanding comments issues a GET that is deliberately left pending below.
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Loading comments')

    // The member submits a comment while that GET is still in flight.
    await wrapper.find('.mv-comment-form .mv-comment-input').setValue('Nice photo!')
    await wrapper.find('.mv-comment-form').trigger('submit')
    await flushPromises()

    const commentBodies = () => wrapper.findAll('.mv-comment-body').map((node) => node.text())

    // The submit's own reconciliation fetch (the newer, second mocked call) has already
    // resolved with the authoritative, complete list -- immediate, useful feedback, not just
    // a lone optimistic comment.
    expect(wrapper.text()).not.toContain('Loading comments')
    expect(commentBodies()).toEqual(['Earlier comment', 'Nice photo!'])

    // The original, now-stale GET finally resolves with a pre-submission snapshot that is
    // missing the new comment entirely.
    initialGetDeferred.resolve([])
    await flushPromises()

    // That stale response must be discarded outright: the complete list stays exactly as the
    // reconciliation left it, and the new comment is never dropped.
    expect(commentBodies()).toEqual(['Earlier comment', 'Nice photo!'])
    await wrapper.find('.mv-comments-toggle').trigger('click')
    expect(wrapper.text()).toContain('Show comments (2)')
  })

  // Same race as above, but the stale, older GET settles by rejecting instead of resolving --
  // proving the generation guard discards a late error the same way it discards a late success.
  it('shows the complete server list and ignores a stale GET that errors after a submit made while the initial load was still pending', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7, commentCount: 1 })], total: 1 }))
    const initialGetDeferred = createDeferred<ClubPostComment[]>()
    fetchClubPostCommentsMock.mockReturnValueOnce(initialGetDeferred.promise)
    fetchClubPostCommentsMock.mockResolvedValueOnce([
      buildComment({ id: 5, body: 'Earlier comment' }),
      buildComment({ id: 6, body: 'Nice photo!' }),
    ])
    createClubPostCommentMock.mockResolvedValue(buildComment({ id: 6, body: 'Nice photo!' }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Loading comments')

    await wrapper.find('.mv-comment-form .mv-comment-input').setValue('Nice photo!')
    await wrapper.find('.mv-comment-form').trigger('submit')
    await flushPromises()

    const commentBodies = () => wrapper.findAll('.mv-comment-body').map((node) => node.text())
    expect(commentBodies()).toEqual(['Earlier comment', 'Nice photo!'])

    // The original GET finally settles, but by erroring rather than resolving.
    initialGetDeferred.reject(new Error('Network error'))
    await flushPromises()

    // The stale rejection must not revert the panel to an error state or drop the new comment.
    expect(commentBodies()).toEqual(['Earlier comment', 'Nice photo!'])
    expect(wrapper.text()).not.toContain('Network error')
    await wrapper.find('.mv-comments-toggle').trigger('click')
    expect(wrapper.text()).toContain('Show comments (2)')
  })
})

describe('ClubMediaView post and comment deletion', () => {
  it('shows a delete button on a post only when the post grants viewerCanDelete', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({
        items: [
          buildPost({ id: 1, viewerCanDelete: true }),
          buildPost({ id: 2, viewerCanDelete: false }),
        ],
        total: 2,
      }),
    )

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    const cards = wrapper.findAll('li.mv-post-card')
    expect(cards[0]!.find('.mv-post-delete').exists()).toBe(true)
    expect(cards[1]!.find('.mv-post-delete').exists()).toBe(false)
  })

  it('deletes a post, backfills the current page from the server, and removes it from the feed without a manual reload', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, viewerCanDelete: true })], total: 1 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(buildFeed({ items: [], total: 0 }))
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(deleteClubPostMock).toHaveBeenCalledWith('1', 1)
    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(2)
    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(2, '1', 0, 12)
    expect(wrapper.find('li.mv-post-card').exists()).toBe(false)
    expect(wrapper.text()).toContain('No posts yet.')
  })

  it('surfaces a 403 delete error message verbatim and keeps the post visible', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, viewerCanDelete: true })], total: 1 }),
    )
    deleteClubPostMock.mockRejectedValue(new Error('You do not have access to delete this post'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You do not have access to delete this post')
    expect(wrapper.find('li.mv-post-card').exists()).toBe(true)
  })

  it('shows a delete button on a comment only when the comment grants viewerCanDelete', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([
      buildComment({ id: 1, viewerCanDelete: true }),
      buildComment({ id: 2, viewerCanDelete: false }),
    ])

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    const comments = wrapper.findAll('li.mv-comment')
    expect(comments[0]!.find('.mv-comment-delete').exists()).toBe(true)
    expect(comments[1]!.find('.mv-comment-delete').exists()).toBe(false)
  })

  it('deletes a comment and decrements the comment count without a manual reload', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 7, commentCount: 1 })], total: 1 }))
    fetchClubPostCommentsMock.mockResolvedValue([buildComment({ id: 1, viewerCanDelete: true })])
    deleteClubPostCommentMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    await wrapper.find('.mv-comment-delete').trigger('click')
    await flushPromises()

    expect(deleteClubPostCommentMock).toHaveBeenCalledWith('1', 7, 1)
    expect(wrapper.find('.mv-comment').exists()).toBe(false)
    expect(wrapper.text()).toContain('No comments yet.')
  })
})

describe('ClubMediaView post deletion pagination regressions', () => {
  it('backfills the current page with the item that slides up from the next page after a delete', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2' }),
        ],
        page: 0,
        size: 2,
        total: 3,
      }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [buildPost({ id: 2, title: 'Post 2' }), buildPost({ id: 3, title: 'Post 3' })],
        page: 0,
        size: 2,
        total: 2,
      }),
    )
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(2)
    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(2, '1', 0, 2)
    const titles = wrapper.findAll('.mv-post-title').map((node) => node.text())
    expect(titles).toEqual(['Post 2', 'Post 3'])
  })

  // The round-3 review's finding: backfill re-requests the *server-confirmed* page/size
  // (page.value/size.value), which can legitimately differ from the raw, unclamped values in
  // the URL (?size=999 clamped by the server to size=100, echoed back and stored in
  // size.value). A staleness check that reconstructs a "current" key from route.query instead
  // of comparing against what the request itself is for would never match in this case, so
  // the backfill's response would always look stale and get dropped -- silently breaking the
  // exact rescue this mechanism exists to provide.
  it('backfills the current page after a delete even when the requested size was clamped by the server', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2' }),
        ],
        page: 0,
        size: 100,
        total: 101,
      }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [buildPost({ id: 2, title: 'Post 2' }), buildPost({ id: 3, title: 'Post 3' })],
        page: 0,
        size: 100,
        total: 100,
      }),
    )
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=999')
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(2, '1', 0, 100)
    const titles = wrapper.findAll('.mv-post-title').map((node) => node.text())
    expect(titles).toEqual(['Post 2', 'Post 3'])
  })

  // The stranding scenario the issue calls out: deleting the only post on a nonzero page must
  // not leave the viewer looking at an empty page when earlier pages still have content.
  it('navigates back to the previous page when deleting the last item strands the viewer on an emptied nonzero page', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 2, title: 'Post 2', viewerCanDelete: true })], page: 1, size: 1, total: 2 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(buildFeed({ items: [], page: 1, size: 1, total: 1 }))
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1' })], page: 0, size: 1, total: 1 }),
    )
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=1&size=1')
    await flushPromises()
    expect(wrapper.text()).toContain('Post 2')

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(3)
    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(2, '1', 1, 1)
    expect(fetchClubMediaFeedMock).toHaveBeenNthCalledWith(3, '1', 0, 1)
    expect(router.currentRoute.value.query.page).toBe('0')
    expect(wrapper.text()).toContain('Post 1')
    expect(wrapper.text()).toContain('Page 1 of 1')
  })

  it('does not navigate away when deleting the last item leaves page zero empty', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, viewerCanDelete: true })], page: 0, size: 12, total: 1 }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(buildFeed({ items: [], page: 0, size: 12, total: 0 }))
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value.query.page).toBeUndefined()
    expect(wrapper.text()).toContain('No posts yet.')
  })

  // The exact race the second review round called out: a delete's own backfill fetch for the
  // page the viewer is leaving must not clobber an immediate Next click's own, newer load --
  // deterministic via a manually-resolved (deferred) promise rather than hoping a real race
  // lines up the same way on every test run.
  it('ignores a stale post-delete backfill response for the old page when Next was clicked before it resolved', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2' }),
        ],
        page: 0,
        size: 2,
        total: 4,
      }),
    )
    const backfillDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillDeferred.promise)
    const nextPageDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(nextPageDeferred.promise)
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    // Deleting post 1 leaves post 2 on the page (never an empty list), so the Next button stays
    // mounted and enabled while its own backfill fetch for page 0 is still in flight below.
    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Post 2')

    // The viewer navigates to the next page before that backfill fetch has resolved.
    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()

    // The Next navigation's own, newer fetch resolves first.
    nextPageDeferred.resolve(buildFeed({ items: [buildPost({ id: 4, title: 'Post 4' })], page: 1, size: 2, total: 3 }))
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('1')
    expect(wrapper.text()).toContain('Post 4')
    expect(wrapper.find('.mv-pagination-status').text()).toBe('Page 2 of 2')

    // The stale backfill for the abandoned page 0 finally resolves and must be ignored entirely:
    // no write to posts/page/size/total, and no navigation.
    backfillDeferred.resolve(
      buildFeed({ items: [buildPost({ id: 99, title: 'Stale Backfill Post' })], page: 0, size: 2, total: 1 }),
    )
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('1')
    expect(wrapper.text()).toContain('Post 4')
    expect(wrapper.text()).not.toContain('Stale Backfill Post')
    expect(wrapper.find('.mv-pagination-status').text()).toBe('Page 2 of 2')
  })

  // The exact race the audit called out: deleting post A, then post B before A's own backfill
  // has resolved, issues two backfill fetches for the same page/route. If A's older backfill
  // (still reflecting a world where B was not yet deleted) resolves after B's newer one, it
  // must not win and resurrect B.
  it('ignores an older backfill response that resolves after a newer backfill from a second, later delete', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2', viewerCanDelete: true }),
        ],
        page: 0,
        size: 2,
        total: 3,
      }),
    )
    const backfillAfterDeletingPost1 = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillAfterDeletingPost1.promise)
    const backfillAfterDeletingPost2 = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillAfterDeletingPost2.promise)
    deleteClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    const deleteButtons = () => wrapper.findAll('.mv-post-delete')
    await deleteButtons()[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Post 2')

    await deleteButtons()[0]!.trigger('click')
    await flushPromises()

    // Post 2's newer backfill resolves first: only Post 3 remains.
    backfillAfterDeletingPost2.resolve(
      buildFeed({ items: [buildPost({ id: 3, title: 'Post 3' })], page: 0, size: 2, total: 1 }),
    )
    await flushPromises()
    expect(wrapper.text()).toContain('Post 3')

    // Post 1's older backfill (a stale snapshot from before Post 2 was also deleted) resolves
    // last and must be ignored entirely, not resurrect Post 2.
    backfillAfterDeletingPost1.resolve(
      buildFeed({
        items: [buildPost({ id: 2, title: 'Post 2' }), buildPost({ id: 3, title: 'Post 3' })],
        page: 0,
        size: 2,
        total: 2,
      }),
    )
    await flushPromises()

    expect(wrapper.text()).not.toContain('Post 2')
    expect(wrapper.text()).toContain('Post 3')
  })

  // A second review round's finding: load()'s own `finally` only cleared `loading` when it
  // was still the newest request, but backfillCurrentPageAfterPostDeletion bumped the *same*
  // shared counter and never cleared `loading` itself. The precise ordering that breaks a flat
  // counter: load() must bump *first* (a Next click while the delete request itself, not just
  // its backfill, is still in flight) and the delete's backfill only bumps *after*, once the
  // delete resolves -- deferring deleteClubPost itself (not just its backfill fetch) is what
  // lets the test force that exact interleaving instead of hoping a real race lines up the
  // same way. Under the old shared counter, that made load()'s finally guard fail (the
  // backfill's later bump made load() look stale even though its page matched the URL), and
  // nothing was left to ever clear `loading` -- the view got stuck on the loading state.
  it('clears the loading state once the newer navigation resolves, even though a still-in-flight delete/backfill bumps request tracking after it started', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2' }),
        ],
        page: 0,
        size: 2,
        total: 4,
      }),
    )
    const deleteDeferred = createDeferred<void>()
    deleteClubPostMock.mockReturnValueOnce(deleteDeferred.promise)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    // Delete is clicked but its own request has not resolved yet.
    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    // Next is clicked while the delete is still in flight: load() bumps request tracking for
    // page=1 first.
    const nextPageDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(nextPageDeferred.promise)
    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()

    // Only now does the delete resolve, so backfillCurrentPageAfterPostDeletion bumps request
    // tracking *after* load() already did.
    const backfillDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillDeferred.promise)
    deleteDeferred.resolve()
    await flushPromises()

    nextPageDeferred.resolve(buildFeed({ items: [buildPost({ id: 4, title: 'Post 4' })], page: 1, size: 2, total: 3 }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('Loading club media')
    expect(wrapper.text()).toContain('Post 4')

    backfillDeferred.resolve(buildFeed({ items: [buildPost({ id: 2, title: 'Post 2' })], page: 0, size: 2, total: 1 }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('Loading club media')
    expect(wrapper.text()).toContain('Post 4')
  })

  // Same precise interleaving (delete's own request resolves *after* the Next click, so its
  // backfill's request-tracking bump happens after load()'s), but checking the write itself
  // rather than just `loading`: a flat, shared counter alone would let this stale backfill
  // apply since nothing bumped past it *after* it started -- only comparing against the live
  // route (which already moved on to page=1 the moment Next was clicked) catches it.
  it('ignores a stale backfill response for the abandoned page once the route has already moved on to Next, regardless of request-tracking order', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Post 2' }),
        ],
        page: 0,
        size: 2,
        total: 4,
      }),
    )
    const deleteDeferred = createDeferred<void>()
    deleteClubPostMock.mockReturnValueOnce(deleteDeferred.promise)

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    const nextPageDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(nextPageDeferred.promise)
    await wrapper.find('.mv-pagination button:last-of-type').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')

    const backfillDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillDeferred.promise)
    deleteDeferred.resolve()
    await flushPromises()

    // The stale backfill (for the abandoned page=0) resolves before the new page's own load()
    // has resolved at all.
    backfillDeferred.resolve(buildFeed({ items: [buildPost({ id: 2, title: 'Post 2' })], page: 0, size: 2, total: 1 }))
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('1')
    expect(wrapper.text()).not.toContain('Post 2')

    nextPageDeferred.resolve(buildFeed({ items: [buildPost({ id: 4, title: 'Post 4' })], page: 1, size: 2, total: 3 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Post 4')
  })

  // Same race, but the newer navigation is to a *different club* rather than a different
  // page: the stale backfill must not clobber club.value/loadedClubId with the previous
  // club's data once the viewer has moved on. Unlike page/size (only overwritten once a
  // load() actually resolves), routeClubId is a computed straight off route.params.id, so it
  // is already live/current the instant router.push resolves -- the only way a backfill can
  // capture the *old* club at all is if it starts (and captures routeClubId) before that
  // navigation happens, with its own fetch still in flight when the navigation lands.
  it('ignores a stale backfill response for the abandoned club once the route has already moved on to a different club', async () => {
    fetchClubByIdMock.mockImplementation(async (idOrSlug: unknown) =>
      buildClub({ name: idOrSlug === '1' ? 'Chess Club' : 'Robotics Club' }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true })], page: 0, size: 12, total: 2 }),
    )
    deleteClubPostMock.mockResolvedValueOnce(undefined)
    const backfillDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillDeferred.promise)

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    // The delete resolves immediately, so backfillCurrentPageAfterPostDeletion starts (and
    // captures club id '1') right away; its own fetch is left in flight (backfillDeferred).
    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 9, title: 'Robotics post' })], page: 0, size: 12, total: 1 }),
    )
    await router.push('/clubs/2/media')
    await flushPromises()
    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')

    // The stale backfill for club '1' finally resolves, after the viewer has already moved on
    // to club '2'.
    backfillDeferred.resolve(buildFeed({ items: [], page: 0, size: 12, total: 0 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')
    expect(wrapper.text()).not.toContain('Chess Club')
  })

  // Round-3 review's finding: unlike the page-based stuck-spinner test above, a delete's
  // backfill can *collide* with the new club's own load() key rather than merely be for a
  // different one. If the delete resolves after the club navigation has already landed,
  // backfillCurrentPageAfterPostDeletion reads the *already-updated* routeClubId -- and if
  // both clubs use the default page/size (no query params), the backfill's key and the new
  // club's load() key are the exact same string. A context-keyed generation counter then
  // treats the backfill as "the newest request for that key", so it can bump straight past
  // the new club's own load() -- which never touches `loading` -- and strand the spinner.
  it('clears the loading state after navigating to a different club even when the delete backfill key would collide with the new club load', async () => {
    fetchClubByIdMock.mockImplementation(async (idOrSlug: unknown) =>
      buildClub({ name: idOrSlug === '1' ? 'Chess Club' : 'Robotics Club' }),
    )
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Post 1', viewerCanDelete: true })], page: 0, size: 12, total: 1 }),
    )
    const deleteDeferred = createDeferred<void>()
    deleteClubPostMock.mockReturnValueOnce(deleteDeferred.promise)

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    // Delete is clicked but its own request has not resolved yet.
    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    // The viewer navigates to a different club (default page/size, same as club 1's) while the
    // delete is still in flight: load() for club 2 starts first.
    const club2LoadDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(club2LoadDeferred.promise)
    await router.push('/clubs/2/media')
    await flushPromises()

    // Only now does the delete resolve, so backfillCurrentPageAfterPostDeletion starts --
    // reading the *already-updated* routeClubId ('2') and the still-unchanged page/size (0/12,
    // the same defaults club 2's own load() also uses).
    const backfillDeferred = createDeferred<ClubPostFeedPage>()
    fetchClubMediaFeedMock.mockReturnValueOnce(backfillDeferred.promise)
    deleteDeferred.resolve()
    await flushPromises()

    club2LoadDeferred.resolve(
      buildFeed({ items: [buildPost({ id: 9, title: 'Robotics post' })], page: 0, size: 12, total: 1 }),
    )
    await flushPromises()

    expect(wrapper.text()).not.toContain('Loading club media')
    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')

    backfillDeferred.resolve(buildFeed({ items: [], page: 0, size: 12, total: 0 }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('Loading club media')
    expect(wrapper.text()).toContain('Robotics post')
    expect(wrapper.text()).toContain('Robotics Club')
  })
})


describe('ClubMediaView pin and unpin', () => {
  it('shows pin controls only when the viewer canManage the club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: false }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 1 })], total: 1 }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-pin-toggle').exists()).toBe(false)
  })

  // A platform owner is not necessarily a member of every club, so they will never be its
  // president -- club.canManage stays false for them -- but the pin/unpin power must still
  // reach them, the same club.canManage-or-owner pattern ClubAdminView already relies on.
  it('shows pin controls for a platform owner even when the club reports canManage as false', async () => {
    const authStore = useAuthStore()
    authStore.currentUser = buildAuthUser({ isOwner: true })
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: false }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 1 })], total: 1 }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-pin-toggle').exists()).toBe(true)
  })

  it('pins a post as a platform owner who is not the club president', async () => {
    const authStore = useAuthStore()
    authStore.currentUser = buildAuthUser({ isOwner: true })
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: false }))
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, pinnedAt: null })], total: 1 }),
    )
    pinClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-pin-toggle').trigger('click')
    await flushPromises()

    expect(pinClubPostMock).toHaveBeenCalledWith('1', 1)
    expect(wrapper.find('.mv-badge-pinned').exists()).toBe(true)
  })

  it('hides pin controls from an ordinary member who is neither the president nor a platform owner', async () => {
    const authStore = useAuthStore()
    authStore.currentUser = buildAuthUser({ isOwner: false })
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: false, viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValue(buildFeed({ items: [buildPost({ id: 1 })], total: 1 }))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-pin-toggle').exists()).toBe(false)
  })

  it('pins an unpinned post and relabels the control to Unpin', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: true }))
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, pinnedAt: null })], total: 1 }),
    )
    pinClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-pin-toggle').text()).toBe('Pin')
    await wrapper.find('.mv-pin-toggle').trigger('click')
    await flushPromises()

    expect(pinClubPostMock).toHaveBeenCalledWith('1', 1)
    expect(wrapper.find('.mv-pin-toggle').text()).toBe('Unpin')
    expect(wrapper.find('.mv-badge-pinned').exists()).toBe(true)
  })

  it('unpins a pinned post and relabels the control to Pin', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: true }))
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, pinnedAt: '2024-06-01T00:00:00Z' })], total: 1 }),
    )
    unpinClubPostMock.mockResolvedValue(undefined)

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    expect(wrapper.find('.mv-pin-toggle').text()).toBe('Unpin')
    await wrapper.find('.mv-pin-toggle').trigger('click')
    await flushPromises()

    expect(unpinClubPostMock).toHaveBeenCalledWith('1', 1)
    expect(wrapper.find('.mv-pin-toggle').text()).toBe('Pin')
    expect(wrapper.find('.mv-badge-pinned').exists()).toBe(false)
  })

  it('surfaces the pin cap 409 message verbatim', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: true }))
    fetchClubMediaFeedMock.mockResolvedValue(
      buildFeed({ items: [buildPost({ id: 1, pinnedAt: null })], total: 1 }),
    )
    pinClubPostMock.mockRejectedValue(new Error('At most 3 posts can be pinned. Unpin one first.'))

    const wrapper = await mountAtMediaRoute()
    await flushPromises()

    await wrapper.find('.mv-pin-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('At most 3 posts can be pinned. Unpin one first.')
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

// Every one of these covers the same structural rule rather than a single incident: an async
// user action captures the view session that was current when the gesture started, and any
// write it makes afterwards is dropped if a navigation has since begun a new session. Before
// that rule existed, only the post-delete *backfill* was guarded, so the delete's own
// optimistic write -- and publish, pin/unpin, comment delete, and a stale comments GET --
// could all still land on a feed the viewer had already navigated away from.
describe('ClubMediaView view-state ownership across navigations', () => {
  it('drops a post delete that resolves after the viewer already navigated to another club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Club One Post One', viewerCanDelete: true }),
          buildPost({ id: 2, title: 'Club One Post Two' }),
        ],
        page: 0,
        size: 2,
        total: 4,
      }),
    )
    const deleteDeferred = createDeferred<void>()
    deleteClubPostMock.mockReturnValueOnce(deleteDeferred.promise)
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({
        items: [
          buildPost({ id: 1, title: 'Club Two Post One' }),
          buildPost({ id: 9, title: 'Club Two Post Nine' }),
        ],
        page: 0,
        size: 2,
        total: 5,
      }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    await wrapper.find('.mv-post-delete').trigger('click')
    await flushPromises()

    await router.push('/clubs/2/media?page=0&size=2')
    await flushPromises()
    expect(wrapper.text()).toContain('Club Two Post One')
    expect(wrapper.find('.mv-pagination-status').text()).toBe('Page 1 of 3')

    // The delete for the abandoned club finally succeeds server-side. Its optimistic local
    // removal and its total decrement belong to a feed that is no longer on screen: post id 1
    // here is a *different* club's post that happens to share the id, and the total is club
    // two's, not club one's.
    deleteDeferred.resolve()
    await flushPromises()

    expect(wrapper.text()).toContain('Club Two Post One')
    expect(wrapper.find('.mv-pagination-status').text()).toBe('Page 1 of 3')
    // No backfill either: a backfill for the abandoned session would be a wasted request whose
    // response could only ever be discarded.
    expect(fetchClubMediaFeedMock).toHaveBeenCalledTimes(2)
  })

  it('drops a publish that resolves after the viewer already navigated to another club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ viewerIsMember: true }))
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 1, title: 'Club One Post One' })], page: 0, size: 2, total: 4 }),
    )
    const publishDeferred = createDeferred<ClubPost>()
    publishClubPostMock.mockReturnValueOnce(publishDeferred.promise)
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 9, title: 'Club Two Post Nine' })], page: 0, size: 2, total: 5 }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media?page=0&size=2')
    await flushPromises()

    await wrapper.find('.mv-publish-title-input').setValue('Meeting recap')
    const fileInput = wrapper.find<HTMLInputElement>('.mv-publish-file-input')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['a'], 'photo.jpg', { type: 'image/jpeg' })],
    })
    await fileInput.trigger('change')
    await wrapper.find('.mv-publish-form').trigger('submit')
    await flushPromises()

    await router.push('/clubs/2/media?page=0&size=2')
    await flushPromises()

    publishDeferred.resolve(buildPost({ id: 42, title: 'Freshly Published' }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('Freshly Published')
    expect(wrapper.find('.mv-pagination-status').text()).toBe('Page 1 of 3')
  })

  it('drops a pin that resolves after the viewer already navigated to another club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub({ canManage: true }))
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 7, title: 'Club One Post Seven', pinnedAt: null })], total: 1 }),
    )
    const pinDeferred = createDeferred<void>()
    pinClubPostMock.mockReturnValueOnce(pinDeferred.promise)
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 7, title: 'Club Two Post Seven', pinnedAt: null })], total: 1 }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    await wrapper.find('.mv-pin-toggle').trigger('click')
    await flushPromises()

    await router.push('/clubs/2/media')
    await flushPromises()
    expect(wrapper.text()).toContain('Club Two Post Seven')

    pinDeferred.resolve()
    await flushPromises()

    // Post ids are global, but "post 7 is pinned" was decided for the club the viewer left.
    expect(wrapper.find('.mv-badge-pinned').exists()).toBe(false)
    expect(wrapper.find('.mv-pin-toggle').text()).toBe('Pin')
  })

  it('drops a comment delete that resolves after the viewer already navigated to another club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 5, title: 'Club One Post Five', commentCount: 1 })], total: 1 }),
    )
    fetchClubPostCommentsMock.mockResolvedValueOnce([
      buildComment({ id: 11, postId: 5, body: 'Club one comment', viewerCanDelete: true }),
    ])
    const deleteCommentDeferred = createDeferred<void>()
    deleteClubPostCommentMock.mockReturnValueOnce(deleteCommentDeferred.promise)
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 5, title: 'Club Two Post Five', commentCount: 3 })], total: 1 }),
    )

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()
    await wrapper.find('.mv-comment-delete').trigger('click')
    await flushPromises()

    await router.push('/clubs/2/media')
    await flushPromises()
    expect(wrapper.text()).toContain('Club Two Post Five')

    deleteCommentDeferred.resolve()
    await flushPromises()

    expect(wrapper.find('.mv-comments-toggle').text()).toContain('3')
  })

  it('drops a comments response that resolves after the viewer already navigated to another club', async () => {
    fetchClubByIdMock.mockResolvedValue(buildClub())
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 5, title: 'Club One Post Five', commentCount: 1 })], total: 1 }),
    )
    const staleComments = createDeferred<ClubPostComment[]>()
    fetchClubPostCommentsMock.mockReturnValueOnce(staleComments.promise)
    fetchClubMediaFeedMock.mockResolvedValueOnce(
      buildFeed({ items: [buildPost({ id: 5, title: 'Club Two Post Five', commentCount: 1 })], total: 1 }),
    )
    fetchClubPostCommentsMock.mockResolvedValueOnce([
      buildComment({ id: 22, postId: 5, body: 'Club two comment' }),
    ])

    const wrapper = await mountAtMediaRoute('/clubs/1/media')
    await flushPromises()

    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    await router.push('/clubs/2/media')
    await flushPromises()

    staleComments.resolve([buildComment({ id: 11, postId: 5, body: 'Club one comment' })])
    await flushPromises()

    // Expanding the same post id under the new club must issue a fresh fetch rather than reuse
    // whatever the abandoned club's in-flight request happened to deliver.
    await wrapper.find('.mv-comments-toggle').trigger('click')
    await flushPromises()

    expect(fetchClubPostCommentsMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Club two comment')
    expect(wrapper.text()).not.toContain('Club one comment')
  })
})
