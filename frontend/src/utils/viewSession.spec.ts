import { describe, expect, it, vi } from 'vitest'

import { createViewSessionOwner } from './viewSession'

describe('createViewSessionOwner', () => {
  it('applies a write from the session that is still current', () => {
    const owner = createViewSessionOwner()
    const session = owner.begin()
    const write = vi.fn()

    expect(session.isCurrent).toBe(true)
    expect(session.apply(write)).toBe(true)
    expect(write).toHaveBeenCalledTimes(1)
  })

  it('drops a write from a session that a newer begin() has superseded', () => {
    const owner = createViewSessionOwner()
    const superseded = owner.begin()
    owner.begin()
    const write = vi.fn()

    expect(superseded.isCurrent).toBe(false)
    expect(superseded.apply(write)).toBe(false)
    expect(write).not.toHaveBeenCalled()
  })

  it('hands out a handle to the session in effect right now, valid until the next begin()', () => {
    const owner = createViewSessionOwner()
    owner.begin()
    const handle = owner.current()

    expect(handle.isCurrent).toBe(true)

    owner.begin()

    expect(handle.isCurrent).toBe(false)
  })

  it('lets only the newest claim on a channel write, whichever order they settle in', () => {
    const owner = createViewSessionOwner()
    const session = owner.begin()
    const older = session.claim('feed')
    const newer = session.claim('feed')
    const olderWrite = vi.fn()
    const newerWrite = vi.fn()

    expect(newer.apply(newerWrite)).toBe(true)
    expect(older.apply(olderWrite)).toBe(false)
    expect(newerWrite).toHaveBeenCalledTimes(1)
    expect(olderWrite).not.toHaveBeenCalled()
  })

  it('keeps channels independent, so a claim on one does not invalidate a claim on another', () => {
    const owner = createViewSessionOwner()
    const session = owner.begin()
    const feed = session.claim('feed')
    session.claim('comments:7')

    expect(feed.isCurrent).toBe(true)
  })

  it('invalidates every claim of a session once a newer session begins', () => {
    const owner = createViewSessionOwner()
    const superseded = owner.begin()
    const claim = superseded.claim('feed')
    owner.begin()

    expect(claim.isCurrent).toBe(false)
  })

  it('gives a new session a clean slate on a channel the previous session had already claimed', () => {
    const owner = createViewSessionOwner()
    owner.begin().claim('feed')
    const claim = owner.begin().claim('feed')

    expect(claim.isCurrent).toBe(true)
  })

  // The trap a flat counter falls into: an already-superseded holder taking a *new* claim would
  // otherwise become "the newest request" on that channel and win against the session that
  // actually owns the view, so a claim derived from a dead session must be born dead -- and
  // must not disturb the channel for the session that is genuinely current.
  it('makes a claim derived from a superseded session dead on arrival without disturbing the live session', () => {
    const owner = createViewSessionOwner()
    const superseded = owner.begin()
    const live = owner.begin()
    const liveClaim = live.claim('feed')

    const zombie = superseded.claim('feed')
    const zombieWrite = vi.fn()

    expect(zombie.isCurrent).toBe(false)
    expect(zombie.apply(zombieWrite)).toBe(false)
    expect(zombieWrite).not.toHaveBeenCalled()
    expect(liveClaim.isCurrent).toBe(true)
  })

  it('supports a follow-up claim taken from a claim, ordered on the follow-up channel', () => {
    const owner = createViewSessionOwner()
    const session = owner.begin()
    const first = session.claim('feed').claim('backfill')
    const second = session.claim('feed').claim('backfill')

    expect(second.isCurrent).toBe(true)
    expect(first.isCurrent).toBe(false)
  })

  // Teardown is a handover to nobody: no new session follows it, so the only way in-flight work
  // stops owning the view is an explicit end().
  it('ends the current session without starting a new one', () => {
    const owner = createViewSessionOwner()
    const session = owner.begin()
    const claim = session.claim('feed')
    const write = vi.fn()

    owner.end()

    expect(session.isCurrent).toBe(false)
    expect(claim.isCurrent).toBe(false)
    expect(claim.apply(write)).toBe(false)
    expect(owner.current().isCurrent).toBe(false)
    expect(write).not.toHaveBeenCalled()
  })

  it('can start a new session again after end()', () => {
    const owner = createViewSessionOwner()
    owner.begin()
    owner.end()

    expect(owner.begin().isCurrent).toBe(true)
  })
})
