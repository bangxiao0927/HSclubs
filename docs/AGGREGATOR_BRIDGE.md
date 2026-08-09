# Aggregator Bridge - Design

How a school site (this repo) and the future guiding page (the 2nd repo) exchange data, and how
the guiding page decides a school site is genuine.

> Status: design, not built. Only the pull half exists today (`GET /api/summary`). Nothing here
> should be implemented before the sample school workflow is stable
> (see `docs/DEVELOPMENT_SEQUENCE.md`, Guiding Rule).

## Decisions taken

1. **The guiding page is private and single-operator.** One instance, run by the project owner
   on their own machine; every school's data converges there. It is not a service other schools
   log into.
2. **The school publishes an address** alongside its name, so the guiding page can list where
   each school is. Shipped: `APP_SUMMARY_ADDRESS`.
3. **A school leaves by taking down its challenge file**, and the guiding page can also stop
   guiding a school on its own. Either side can end it; neither needs the other's cooperation.

What (1) changes about the shape below: **pull is not just authoritative, it is sufficient.** A
private aggregator on one machine can reach out to school sites, but school sites cannot
necessarily reach back -- that would mean exposing an inbound endpoint from a personal server,
with a certificate, a public name, and an open port, purely to save a few minutes of latency on
a directory that changes weekly. So:

- **Build the pull.** It works from behind NAT, needs nothing published, and survives the
  machine being asleep: a missed hour is just a later poll.
- **Treat the ping as optional and probably unnecessary.** It stays designed below, because the
  cost of writing it down is zero and the cost of retrofitting a protocol is not, but a
  single-operator private page should not open an inbound port for it.

The rest of this document therefore describes the full bridge; the ping half is the part to skip
unless a reason to expose that endpoint appears.

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

   The host that matters is the API host, not the school's brand domain, because a challenge
   served by a host that does not serve the data proves nothing about the data. So the registry
   stores one URL, the challenge is fetched from that same origin, and the pull follows no
   cross-origin redirect and is bounded by a short timeout and a response-size cap -- a
   registered site cannot steer that server-side fetch somewhere else (including inside the
   guiding page's own network) or hand back an unbounded body.

   On the single-origin layout `docs/DEPLOYMENT.md` gives proxy config for, the static root is
   already there and the file is genuinely a drop-in. A school that splits the API onto its own
   host (`VITE_API_BASE_URL=https://api.school.example.org`, mentioned there as a build setting
   with no server block of its own) has no static root on that host, so it adds one location
   returning the token -- still proxy configuration, still no backend change, but worth saying
   out loud rather than implying the file drops in anywhere.
2. **Slug agreement.** The `slug` in the school's `/api/summary` must equal the slug the
   registry holds for that origin. This stops one verified school from claiming another's
   identity, and it is a field this repo already exposes and configures
   (`APP_SUMMARY_SLUG`).

Re-verification runs on a schedule (monthly, say). A site that stops answering, or whose
challenge file disappears, is marked unverified and hidden from the guiding page rather than
deleted, so an outage is not a removal.

That is also how a school leaves: it deletes the challenge file and the next re-verification
stops listing it. No request to the operator, no coordination. The operator can equally drop a
school from the registry at any time. Both directions are one-sided on purpose.

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
  that change waiting for the hourly poll. An over-limit ping is answered `429` and is still
  recorded as the pending ping for the window, so "deferred" stays true for it too.
- Rate-limit **after** verifying the signature, not before. The limiter is keyed on the slug,
  which comes from the request, so limiting first would let an unauthenticated flood naming a
  real slug consume that school's budget and get its genuine pings rejected at the door -- which
  is a drop, not a deferral, and would quietly break the guarantee above. Verified-only budgets
  mean a flood costs signature verification (cheap, and itself rate-limitable by source) rather
  than a school's freshness.
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
  after it happens. The announced etag is a hint about *which* change fired the ping, not a
  value the pull is guaranteed to return: the cache TTL and the coalescing window can both
  elapse first, so the pull may legitimately come back with a newer etag -- or with a 304
  against one the guiding page already stored, if it pulled for an earlier ping in between.
  Treating a mismatch as an error would therefore be wrong; the pull's answer is simply the
  current truth.
- The ETag is a content hash, not a random or per-process value, so a restart or a redeploy does
  not invent a change. The first check after a restart has nothing to compare against and sends
  one ping; that is deliberate, since it also re-announces a school whose earlier ping was lost.
- Timeouts and failures are swallowed after a log line, with a bounded retry. **The school site
  must never fail, slow down, or leak an error to a student because a guiding page is down.**
  Retries are for transport failures, 5xx, and the throttling statuses the guiding page itself
  returns (`429`, `408`); any other 4xx means this school is misconfigured or deregistered, and
  retrying that forever would just be a slow flood.
- No student data leaves the school site. The ping carries a slug, an ETag and a timestamp.

## What stays out of this repo

- The registry of schools, verification state, tokens, and the polling scheduler: all guiding
  page.
- Any inbound endpoint for the guiding page to write to. There is none, by design.
- Any multi-school notion in this codebase (see `docs/PLAN.md`'s historical draft for why).

## Open questions

1. ~~Public or private backend?~~ Private, single-operator (see Decisions above). The registry
   can therefore be the simplest thing that works -- a file the operator edits -- rather than a
   database behind an admin UI, and no part of it needs to be reachable from the internet.
2. ~~Does the guiding page need more than the summary carries?~~ An address, now shipped as
   `APP_SUMMARY_ADDRESS`. Anything further (a region for map filtering, coordinates, a logo) is
   school identity too, so it is another `APP_SUMMARY_*` field rather than a new endpoint --
   worth adding only when the page actually renders it.
3. ~~Should `lastUpdatedAt` become an instant with a timezone?~~ Done: it is an ISO-8601 instant
   with an offset, taken from `app.summary.time-zone` (the zone the database writes timestamps
   in, defaulting to the application's own). Fixed before anything consumed it, which is the
   cheapest moment to change a published field.
4. ~~How is a school removed?~~ Both, independently. A school leaves by deleting its challenge
   file: the next re-verification fails and the page stops listing it, with no message to anyone.
   The guiding page can also stop guiding a school at any time by dropping it from the registry.
   Neither side can force the other to keep the link, which is the right property for a page one
   person runs and schools join voluntarily.

Nothing above is blocking. The remaining question is timing: the notifier this repo would gain
is only worth building if the ping half is built at all, and per the Decisions section a private
single-operator page probably should not.
