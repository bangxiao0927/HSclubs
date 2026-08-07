/**
 * Ownership tokens for view state that several overlapping async flows write to.
 *
 * The rule this exists to make structural, rather than something each call site has to
 * re-derive correctly: a view's state belongs to exactly one *session* at a time, a session
 * ends only when a new one begins or the owner ends it outright (`end()`, for a teardown),
 * and every write that happens after an `await` must go through the session that was current
 * when that work started. A write from a superseded session is silently dropped instead of
 * clobbering whatever the current session has already put on screen.
 *
 * Two ordering problems fall out of that single rule:
 *
 * - *Across* sessions (a navigation): only the code that owns the view -- the loader, plus the
 *   teardown hook that ends the last session -- starts or ends a session. No action can
 *   invalidate the loader, so no action can strand a spinner the loader is responsible for
 *   clearing.
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
  /**
   * Ends the current session without starting another, leaving no session in effect -- for a
   * teardown, where no successor exists to invalidate the work still in flight.
   */
  end(): void
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
  // Session ids are issued monotonically and never reused, so a token from a session that was
  // ended cannot be revived by a later begin(). Id 0 is reserved for "no session in effect".
  let issuedSessions = 0
  let currentSessionId = 0
  let claims = new Map<string, number>()

  const token = (id: number, constraints: readonly ClaimConstraint[]): ViewSession => {
    const isCurrent = () =>
      id !== 0
      && id === currentSessionId
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
      issuedSessions += 1
      currentSessionId = issuedSessions
      // Claims are per-session: a new session starts every channel from scratch, so no
      // bookkeeping from an abandoned session can make one of its stragglers look current.
      claims = new Map()
      return token(currentSessionId, [])
    },
    current() {
      return token(currentSessionId, [])
    },
    end() {
      currentSessionId = 0
      claims = new Map()
    },
  }
}
