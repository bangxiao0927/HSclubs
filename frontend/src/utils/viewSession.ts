/**
 * Ownership tokens for view state that several overlapping async flows write to.
 *
 * The rule this exists to make structural, rather than something each call site has to
 * re-derive correctly: a view's state belongs to exactly one *session* at a time, a session
 * ends only when a new one begins, and every write that happens after an `await` must go
 * through the session that was current when that work started. A write from a superseded
 * session is silently dropped instead of clobbering whatever the current session has already
 * put on screen.
 *
 * Two ordering problems fall out of that single rule:
 *
 * - *Across* sessions (a navigation): only the code that owns the view -- the loader -- calls
 *   `begin()`. Nothing else can invalidate the loader, so nothing else can strand a spinner
 *   the loader is responsible for clearing.
 * - *Within* a session (two of the same kind of request in flight at once): `claim(channel)`
 *   takes the newest slot on a named channel; an older claim on that same channel stops being
 *   current the moment a newer one is taken.
 *
 * A claim taken from an already-superseded holder is born dead and does not touch the channel,
 * so a straggler can never promote itself past the session that actually owns the view.
 */
export interface ViewSession {
  /** Whether this token still owns the view state it was issued for. */
  readonly isCurrent: boolean
  /** Runs `write` only while this token is still current; returns whether it ran. */
  apply(write: () => void): boolean
  /**
   * Takes the newest slot on `channel`, invalidating any earlier claim on it. Returns a token
   * that is current only while both this token and that slot are.
   */
  claim(channel: string): ViewSession
}

export interface ViewSessionOwner {
  /** Starts a new session, ending every session and claim issued before it. */
  begin(): ViewSession
  /** A token for the session in effect right now, without starting a new one. */
  current(): ViewSession
}

interface ClaimConstraint {
  channel: string
  id: number
}

const deadSession: ViewSession = {
  isCurrent: false,
  apply: () => false,
  claim: () => deadSession,
}

export const createViewSessionOwner = (): ViewSessionOwner => {
  let sessionId = 0
  let claims = new Map<string, number>()

  const token = (id: number, constraints: readonly ClaimConstraint[]): ViewSession => {
    const isCurrent = () =>
      id === sessionId
      && constraints.every((constraint) => claims.get(constraint.channel) === constraint.id)

    return {
      get isCurrent() {
        return isCurrent()
      },
      apply(write: () => void) {
        if (!isCurrent()) {
          return false
        }
        write()
        return true
      },
      claim(channel: string) {
        if (!isCurrent()) {
          return deadSession
        }
        const next = (claims.get(channel) ?? 0) + 1
        claims.set(channel, next)
        return token(id, [...constraints, { channel, id: next }])
      },
    }
  }

  return {
    begin() {
      sessionId += 1
      // Claims are per-session: a new session starts every channel from scratch, so no
      // bookkeeping from an abandoned session can make one of its stragglers look current.
      claims = new Map()
      return token(sessionId, [])
    },
    current() {
      return token(sessionId, [])
    },
  }
}
