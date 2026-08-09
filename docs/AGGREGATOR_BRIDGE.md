# Aggregator Bridge - Design

How a school site (this repo) and the future guiding page (the 2nd repo) exchange data, and how
the guiding page decides a school site is genuine.

> Status: design, not built. Only the pull half exists today (`GET /api/summary`). Nothing here
> should be implemented before the sample school workflow is stable
> (see `docs/DEVELOPMENT_SEQUENCE.md`, Guiding Rule).

## What exists today

`GET /api/summary` on each school site: anonymous, read-only, any origin (its own
credential-less CORS policy), returning school identity, club and member counts, category
counts, `lastUpdatedAt`, a `dataHash`, and an `ETag` that supports conditional polling. There is
no write path: `POST /api/summary` answers 401, and that is deliberate.

## The decision this document makes

**The school never pushes data. It may push a notification.**

A push that carries the numbers would make the guiding page's contents depend on whatever a
caller sends it, which turns every school site (and anyone who can impersonate one) into a
writer of platform-wide content. A push that carries only "something changed" leaves the school
site as the single source of truth: the guiding page still fetches `/api/summary` itself, over a
URL it already had, and validates what it gets.

So the bridge has two parts:

1. **Pull (authoritative).** The guiding page polls each verified school's `/api/summary` on a
   slow interval (say hourly), using `If-None-Match` so an unchanged school costs one 304.
2. **Ping (optional, an optimisation only).** A school site may tell the guiding page "my
   summary changed", so the page can pull immediately instead of waiting for the next poll.
   Losing a ping must never lose data; the poll is the guarantee.

```text
school site                                   guiding page
-----------                                   ------------
  (1) something changes
  (2) POST /api/schools/{slug}/ping  ------->  verify signature + slug
      {slug, etag, sentAt}, signed              |
                                                v
  (4) GET /api/summary   <-------------------  (3) pull the truth
      304 or fresh body  ------------------->  store
```

## Verifying a school site

Registration is a human decision (a school is approved once), but the guiding page still has to
prove that a given URL belongs to the school it claims to be. Two checks, both cheap:

1. **Origin challenge.** The guiding page issues a one-time token. The school publishes it at
   `/.well-known/hsclubs-site.txt` **on the same host that serves `/api/summary`** (a static
   file the reverse proxy can serve; no backend change needed). The guiding page fetches it over
   HTTPS and matches.

   The host that matters is the API host, not the school's brand domain: `docs/DEPLOYMENT.md`
   documents both a single-origin layout (`https://school.example.org/api/summary`) and a split
   one (`https://api.school.example.org/api/summary`, with the SPA elsewhere), and a challenge
   published on a host that does not serve the data proves nothing about the data. So the
   registry stores one URL, the challenge is fetched from that same origin, and the pull follows
   no cross-origin redirect and is bounded by a short timeout and a response-size cap -- a
   registered site cannot steer that server-side fetch somewhere else (including inside the
   guiding page's own network) or hand back an unbounded body.
2. **Slug agreement.** The `slug` in the school's `/api/summary` must equal the slug the
   registry holds for that origin. This stops one verified school from claiming another's
   identity, and it is a field this repo already exposes and configures
   (`APP_SUMMARY_SLUG`).

Re-verification runs on a schedule (monthly, say). A site that stops answering, or whose
challenge file disappears, is marked unverified and hidden from the guiding page rather than
deleted, so an outage is not a removal.

## Authenticating a ping

The ping is the only inbound call, so it is the only thing needing a credential. Per verified
school, the guiding page issues a shared secret. The school signs each ping:

```
POST /api/schools/{slug}/ping
X-HSClubs-Timestamp: 2026-08-08T15:32:58Z
X-HSClubs-Signature: sha256=<hex HMAC of "{timestamp}\n{slug}\n{etag}">

{"slug": "mvhs", "etag": "\"<etag>\"", "sentAt": "2026-08-08T15:32:58Z"}
```

Rules for the guiding page:

- Reject an unknown slug, a bad signature, or a timestamp outside a few minutes (replay window),
  and reject a signature already seen inside that window. The window alone does not stop a
  verbatim replay of a ping read in transit (a proxy, a mis-set access log, a TLS-terminating
  middlebox), so keep a small per-slug cache of recently seen signatures.
- Rate-limit per slug; a ping storm must never turn into a pull storm. Coalesce: at most one
  pull per school per minute regardless of how many pings arrive. Coalescing means **defer, not
  drop**: the last ping inside a window still results in exactly one pull when the window ends.
  Dropping it would lose immediacy in precisely the bursty case the ping exists for, and leave
  that change waiting for the hourly poll. Note the rate-limit key comes from the request, so
  an unauthenticated flood naming a real slug can delay that school's pulls -- which costs
  freshness only, never data, because the poll is the guarantee.
- Nothing above is load-bearing for correctness. Every one of these rules exists to keep a bad
  actor from making the guiding page do work, not to keep it from showing wrong numbers; the
  numbers always come from the school's own verified URL.
- Treat the body as a hint only. Never store a number from it. The pull decides.
- Answer every accepted ping the same way (`202`, no body). The school gets no signal it could
  act on and none it could probe: only authentication failures are distinguishable, and the
  guiding page's own state -- when it last pulled, whether the etag was new -- is never
  reflected back.

The signature covers the exact bytes of the three fields as they appear in the request: the
timestamp header, the slug, and the etag **in its HTTP form, quotes included** (`"abc123"`, not
`abc123`). Both sides must agree on that or every signature fails; it is called out because the
quoting is the easy thing to get wrong.

Secret lifecycle: issued once at registration over a channel that is not this bridge, rotated by
accepting the old and new secret together for a short overlap, revoked by deregistering the
school. It lives in the school's `.env` alongside the database password, so it must never be
logged -- not even a prefix.

Bearer tokens would be simpler, but a signature keeps the secret off the wire, which matters
because the ping is sent from a school server that a volunteer maintains.

## What this repo would add (and only this)

Outbound only, off by default, so a school that never joins a guiding page is unaffected:

| Setting | Meaning |
| --- | --- |
| `app.aggregator.ping.enabled` | Default `false`. Nothing is sent unless a school opts in. |
| `app.aggregator.ping.url` | The guiding page's ping endpoint. |
| `app.aggregator.ping.slug` | Defaults to `app.summary.slug`. |
| `app.aggregator.ping.secret` | The shared secret issued at registration. |

Behaviour:

- One scheduled check (every few minutes) compares the current summary ETag with the last one
  sent. Different -> ping. This deliberately reuses the existing cached summary rather than
  hooking every write path, so no club/media code has to know the guiding page exists.
- The ping is therefore never instant, and does not need to be: a change is announced at most
  one summary cache TTL (`app.summary.cache-ttl-ms`, 60s by default) plus one check interval
  after it happens. Reading the same cache is also what keeps the two sides consistent -- the
  etag the school announces is the etag the guiding page's pull will get.
- The ETag is a content hash, not a random or per-process value, so a restart or a redeploy does
  not invent a change. The first check after a restart has nothing to compare against and sends
  one ping; that is deliberate, since it also re-announces a school whose earlier ping was lost.
- Timeouts and failures are swallowed after a log line, with a bounded retry. **The school site
  must never fail, slow down, or leak an error to a student because a guiding page is down.**
  Retries are for transport failures and 5xx only; a 4xx means this school is misconfigured or
  deregistered, and retrying it forever would just be a slow flood.
- No student data leaves the school site. The ping carries a slug, an ETag and a timestamp.

## What stays out of this repo

- The registry of schools, verification state, tokens, and the polling scheduler: all guiding
  page.
- Any inbound endpoint for the guiding page to write to. There is none, by design.
- Any multi-school notion in this codebase (see `docs/PLAN.md`'s historical draft for why).

## Open questions

1. Is the guiding page's backend public or private? `docs/REPO_STRATEGY.md` says the backend may
   stay private if it collects status data; that decides whether the registry is a file in a
   repo or a database behind an admin UI.
2. Does the guiding page need anything the summary does not already carry -- a contact address,
   a region for map/filtering, a logo? Those are school identity, so they would be new
   `APP_SUMMARY_*` settings here rather than a new endpoint.
3. Should `lastUpdatedAt` become an instant with a timezone before other systems consume it? It
   is currently a local date-time, which is ambiguous across schools in different zones.
4. How is a school removed -- deregistered by the guiding page, or by the school taking down the
   challenge file? The second is self-service and needs no support request.
