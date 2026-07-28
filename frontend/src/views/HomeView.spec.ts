import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { invalidateClubCache } from '../services/clubService'
import HomeView from './HomeView.vue'

let fetchMock: ReturnType<typeof vi.fn>

const jsonResponse = (body: unknown) => ({
  ok: true,
  text: async () => JSON.stringify(body),
})

const mountHome = async () => {
  const wrapper = mount(HomeView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  invalidateClubCache()
  fetchMock = vi.fn().mockResolvedValue(jsonResponse([{ id: 1, name: 'Chess Club', memberCount: 5 }]))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  invalidateClubCache()
})

describe('HomeView', () => {
  it('reuses the shared club cache when revisited, instead of forcing a fresh request every time', async () => {
    const first = await mountHome()
    first.unmount()

    const second = await mountHome()
    second.unmount()

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
