import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('../services/clubService', () => ({
  approveMembershipRequest: vi.fn(),
  fetchClubById: vi.fn(),
  fetchClubMembers: vi.fn(),
  fetchMembershipRequests: vi.fn(),
  invalidateClubCache: vi.fn(),
  rejectMembershipRequest: vi.fn(),
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
  approveMembershipRequest,
  fetchClubById,
  fetchClubMembers,
  fetchMembershipRequests,
  invalidateClubCache,
  rejectMembershipRequest,
  updateClub,
} from '../services/clubService'
import { assignPresident, searchUsers } from '../services/userService'
import { useAuthStore } from '../stores/auth'
import type { Club, ClubMember, ClubMembershipRequest } from '../types/club'
import ClubAdminView from './ClubAdminView.vue'

const useRouteMock = vi.mocked(useRoute)
const useRouterMock = vi.mocked(useRouter)
const fetchClubByIdMock = vi.mocked(fetchClubById)
const fetchClubMembersMock = vi.mocked(fetchClubMembers)
const fetchMembershipRequestsMock = vi.mocked(fetchMembershipRequests)
const approveMembershipRequestMock = vi.mocked(approveMembershipRequest)
const rejectMembershipRequestMock = vi.mocked(rejectMembershipRequest)
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
  // The default fixture is what a *manager* of this club sees, because that is the only viewer
  // the editor renders for now (see the canManageClub gate in the view); a visitor without
  // manage rights gets the read-only notice instead, covered by its own test below.
  canManage: true,
  ...overrides,
})

const buildPendingRequest = (
  overrides: Partial<ClubMembershipRequest> = {},
): ClubMembershipRequest => ({
  id: 1,
  clubId: 1,
  oauthUserId: 42,
  displayName: 'Riley Applicant',
  email: 'riley@example.com',
  avatarUrl: null,
  createdAt: '2024-01-01T00:00:00Z',
  ...overrides,
})

const buildMember = (overrides: Partial<ClubMember> = {}): ClubMember => ({
  oauthUserId: 42,
  displayName: 'Riley Applicant',
  email: 'riley@example.com',
  avatarUrl: null,
  roleName: 'member',
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
  fetchMembershipRequestsMock.mockReset()
  fetchMembershipRequestsMock.mockResolvedValue([])
  approveMembershipRequestMock.mockReset()
  rejectMembershipRequestMock.mockReset()
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

  // /clubs/:id/admin is only guarded by requiresAuth and fetchClubById is a public endpoint, so
  // the editor used to render for any signed-in visitor and only fail once they pressed Save.
  // The backend was always enforced; this is about not inviting the edit in the first place.
  // Loading a club both calls loadMembers directly and flips canManageClub from false to true
  // for a club president, which fires the watcher: two concurrent roster requests whose
  // responses race to write members.value.
  it('loads the roster once for a club president, not twice', async () => {
    const club = buildClub({ canManage: true })
    fetchClubMembersMock.mockResolvedValue([])

    await mountView(club)

    expect(fetchClubMembersMock).toHaveBeenCalledTimes(1)
  })

  it('renders a read-only notice instead of the editor for someone who does not manage the club', async () => {
    const club = buildClub({ canManage: false })

    const wrapper = await mountView(club)

    expect(wrapper.find('form.admin-form').exists()).toBe(false)
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('You do not manage Chess Club')
  })

  it('still renders the editor for a platform owner who is not a club member', async () => {
    const club = buildClub({ canManage: false })
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

    expect(wrapper.find('form.admin-form').exists()).toBe(true)
  })

  // The update endpoint answers with the stored row, which carries no viewer-scoped fields.
  // Assigning it wholesale erased canManage, so a *successful* save flipped a club president
  // straight into the "you do not manage this club" notice.
  it('keeps the editor open after a successful save by a club president', async () => {
    const club = buildClub({ canManage: true })
    const storedRowFromUpdate = buildClub({ name: 'Chess Team' })
    delete storedRowFromUpdate.canManage
    updateClubMock.mockResolvedValue(storedRowFromUpdate as Club)

    const wrapper = await mountView(club)
    await wrapper.find('form.admin-form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Changes saved')
    expect(wrapper.find('form.admin-form').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('You do not manage')
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

describe('single settings save/discard action', () => {
  it('renders exactly one Save changes button and one discard/reset button', async () => {
    const club = buildClub({ canManage: true })

    const wrapper = await mountView(club)

    const saveButtons = wrapper.findAll('button').filter((btn) => btn.text() === 'Save changes')
    const discardButtons = wrapper
      .findAll('button')
      .filter((btn) => btn.text() === 'Discard edits' || btn.text() === 'Reset changes')
    expect(saveButtons).toHaveLength(1)
    expect(discardButtons).toHaveLength(1)
  })
})

describe('ClubAdminView inline pending membership requests', () => {
  it('loads and lists pending requests inline under the Members section', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockResolvedValue([buildPendingRequest()])

    const wrapper = await mountView(club)

    const membersSection = wrapper.find('#members')
    expect(membersSection.exists()).toBe(true)
    expect(membersSection.text()).toContain('Pending approvals')
    expect(membersSection.text()).toContain('Riley Applicant')
  })

  it('shows a loading state while pending requests are in flight', async () => {
    const club = buildClub({ canManage: true })
    let resolveRequests!: (value: never[]) => void
    fetchMembershipRequestsMock.mockImplementation(
      () => new Promise((resolve) => { resolveRequests = resolve }),
    )

    useRouteMock.mockReturnValue({ params: { id: String(club.id) } } as unknown as ReturnType<typeof useRoute>)
    useRouterMock.mockReturnValue({ push: vi.fn(), back: vi.fn() } as unknown as ReturnType<typeof useRouter>)
    fetchClubByIdMock.mockResolvedValue(club)
    const wrapper = mount(ClubAdminView, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    expect(wrapper.find('#members').text()).toContain('Loading requests…')

    resolveRequests([])
    await flushPromises()
    expect(wrapper.find('#members').text()).not.toContain('Loading requests…')
  })

  it('shows an empty state when there are no open requests', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockResolvedValue([])

    const wrapper = await mountView(club)

    expect(wrapper.find('#members').text()).toContain('No open requests right now.')
  })

  it('shows an error state with a retry action when loading pending requests fails', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockRejectedValueOnce(new Error('Failed to load pending requests'))

    const wrapper = await mountView(club)

    expect(wrapper.find('#members').text()).toContain('Failed to load pending requests')

    fetchMembershipRequestsMock.mockResolvedValueOnce([buildPendingRequest()])
    const retryButtons = wrapper.findAll('#members button').filter((btn) => btn.text() === 'Try again')
    await retryButtons[0]!.trigger('click')
    await flushPromises()

    expect(wrapper.find('#members').text()).toContain('Riley Applicant')
  })

  it('refreshes the pending list on demand', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockResolvedValue([])

    const wrapper = await mountView(club)
    fetchMembershipRequestsMock.mockClear()

    const refreshButton = wrapper
      .findAll('#members button')
      .find((btn) => btn.text() === 'Refresh list')
    await refreshButton!.trigger('click')
    await flushPromises()

    expect(fetchMembershipRequestsMock).toHaveBeenCalledTimes(1)
  })

  it('approves a pending request and reloads the list', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock
      .mockResolvedValueOnce([buildPendingRequest({ id: 5, displayName: 'Riley Applicant' })])
      .mockResolvedValueOnce([])
    approveMembershipRequestMock.mockResolvedValue()

    const wrapper = await mountView(club)
    const approveButton = wrapper
      .findAll('#members button')
      .find((btn) => btn.text() === 'Approve')
    await approveButton!.trigger('click')
    await flushPromises()

    expect(approveMembershipRequestMock).toHaveBeenCalledWith(club.id, 5)
    expect(wrapper.find('#members').text()).toContain('No open requests right now.')
  })

  it('declines a pending request and reloads the list', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock
      .mockResolvedValueOnce([buildPendingRequest({ id: 5, displayName: 'Riley Applicant' })])
      .mockResolvedValueOnce([])
    rejectMembershipRequestMock.mockResolvedValue()

    const wrapper = await mountView(club)
    const declineButton = wrapper
      .findAll('#members button')
      .find((btn) => btn.text() === 'Decline')
    await declineButton!.trigger('click')
    await flushPromises()

    expect(rejectMembershipRequestMock).toHaveBeenCalledWith(club.id, 5)
    expect(wrapper.find('#members').text()).toContain('No open requests right now.')
  })

  it('shows an approval error without disturbing the roster', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockResolvedValue([buildPendingRequest({ id: 5 })])
    approveMembershipRequestMock.mockRejectedValue(new Error('Failed to approve request'))

    const wrapper = await mountView(club)
    const approveButton = wrapper
      .findAll('#members button')
      .find((btn) => btn.text() === 'Approve')
    await approveButton!.trigger('click')
    await flushPromises()

    expect(wrapper.find('#members').text()).toContain('Failed to approve request')
  })

  // Regression for a review finding: approve used to only reload the pending list, so an
  // approved applicant kept showing the pre-approval member count and was missing from the
  // roster until someone pressed "Refresh roster" by hand.
  it('refreshes the roster and the member count shown in the hero after approving, without a manual refresh', async () => {
    const club = buildClub({ canManage: true, memberCount: 42 })
    fetchMembershipRequestsMock
      .mockResolvedValueOnce([buildPendingRequest({ id: 5, displayName: 'Riley Applicant' })])
      .mockResolvedValueOnce([])
    fetchClubMembersMock
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([buildMember({ displayName: 'Riley Applicant' })])
    approveMembershipRequestMock.mockResolvedValue()

    const wrapper = await mountView(club)
    // The read fired by the approval carries the club's updated memberCount; queued only now so
    // the initial mount above still consumes the plain `club` fixture from mockResolvedValue.
    fetchClubByIdMock.mockResolvedValueOnce(buildClub({ canManage: true, memberCount: 43 }))

    const approveButton = wrapper
      .findAll('#members button')
      .find((btn) => btn.text() === 'Approve')
    await approveButton!.trigger('click')
    await flushPromises()

    expect(fetchClubByIdMock).toHaveBeenCalledTimes(2)
    expect(fetchClubMembersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('43 members')
    expect(wrapper.find('#members').text()).toContain('Riley Applicant')
  })

  // Regression for a review finding: an early-return guard in loadPendingRequests dropped a
  // reload triggered while an earlier one was still in flight, so a second, newer decision
  // never actually re-fetched -- leaving a decided request on screen and risking a stale
  // re-decide error later. The newest reload must always run and always be the one displayed,
  // whichever response happens to arrive first.
  it('applies the newer pending-list reload even when an earlier one is still in flight', async () => {
    const club = buildClub({ canManage: true })
    fetchMembershipRequestsMock.mockResolvedValueOnce([])

    const wrapper = await mountView(club)
    const refreshButton = wrapper.find('.pending-panel__header button')

    let resolveOlder: ((value: ClubMembershipRequest[]) => void) | undefined
    let resolveNewer: ((value: ClubMembershipRequest[]) => void) | undefined
    fetchMembershipRequestsMock
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOlder = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveNewer = resolve }))

    await refreshButton.trigger('click')
    // The second click lands while the button is already disabled (pendingLoading is true), so
    // @vue/test-utils' trigger() would refuse to dispatch it -- dispatch the native event
    // directly to simulate the underlying race (two decisions landing close together) without
    // that guard getting in the way.
    refreshButton.element.dispatchEvent(new Event('click'))
    await flushPromises()

    // Both reloads must have actually been issued -- the old guard would have silently dropped
    // the second one instead of ever calling fetchMembershipRequests for it.
    expect(fetchMembershipRequestsMock).toHaveBeenCalledTimes(3)

    resolveNewer!([buildPendingRequest({ id: 9, displayName: 'Newer Applicant' })])
    await flushPromises()
    resolveOlder!([buildPendingRequest({ id: 1, displayName: 'Stale Applicant' })])
    await flushPromises()

    const membersText = wrapper.find('#members').text()
    expect(membersText).toContain('Newer Applicant')
    expect(membersText).not.toContain('Stale Applicant')
    expect(membersText).not.toContain('Failed to load pending requests')
  })
})

describe('ClubAdminView members section permission gating', () => {
  it('does not fetch or render members or pending requests for a viewer who does not manage the club', async () => {
    const club = buildClub({ canManage: false })

    const wrapper = await mountView(club)

    expect(wrapper.find('#members').exists()).toBe(false)
    expect(fetchMembershipRequestsMock).not.toHaveBeenCalled()
    expect(fetchClubMembersMock).not.toHaveBeenCalled()
  })
})
