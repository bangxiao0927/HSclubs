# HS Clubs v1 contracts

The wire between three repositories that ship on their own schedules: the school template
([HSclubs](https://github.com/bangxiao0927/HSclubs)), this guiding page, and the iOS app
([hsclubs-app](https://github.com/bangxiao0927/hsclubs-app)). Nothing here is code any of them
runs; it is the set of documents they must agree about, in a form a test can check.

This directory is the artifact. The other two repositories vendor it verbatim and compare their
copy against [`manifest.json`](manifest.json), so "we are on the same contract" is a checksum,
not a conversation.

## What is covered

| Contract | Producer | Consumer | Endpoint |
| --- | --- | --- | --- |
| `summary` | school template | guiding page | `GET /api/v1/summary` |
| `school-manifest` | school template | guiding page, app | `GET /.well-known/hsclubs-app.json` |
| `app-directory` | guiding page | app | `GET /api/v1/schools` |
| `mobile-auth-*` | school template | app | `/api/mobile-auth/{start,complete}` and the callback link |

Schemas are JSON Schema 2020-12 under [`schemas/`](schemas). Fixtures are under
[`fixtures/<contract>/`](fixtures); a file named `valid*.json` must validate and one named
`invalid-*.json` must not. Fixed protocol numbers live in
[`vectors/mobile-auth.json`](vectors/mobile-auth.json).

Run `npm run contracts:check` here. In the other repositories the equivalent check runs against
their vendored copy, using the same fixtures.

## Identity

`schoolId` is the identity. It is opaque, issued once by this registry, matches
`^sch_[A-Za-z0-9]{16,48}$`, and never changes -- not when a school is renamed, moves host, or
changes its slug. Nothing may be inferred from its bytes.

`slug` is a display and URL handle. It is mutable and carries no identity. A consumer that keys
anything durable -- a cache, a stored selection, a session -- on the slug has a bug that shows up
the first time a school renames itself, which is exactly why the app's stored selection migrates
to `schoolId`.

A school appears in the app directory only when the registry entry, the manifest and the summary
all carry the same `schoolId`. Two of three agreeing is a mismatch, not a majority.

## Compatibility rules

What a producer may do inside v1:

- Add a member. Consumers must ignore members they do not know; every schema above allows
  unknown members except where the document is a set of parameters the school will act on
  (`mobile-auth-start`, `mobile-auth-callback`, `mobile-auth-complete-request`), where an
  unrecognised parameter is refused rather than silently dropped.
- Add an enum member only where the schema is open. `integrationStatus` and the mobile auth
  `error` codes are closed sets: adding to either is a new version.
- Widen a value to also allow `null` only if the member was already nullable. Turning a
  non-nullable member nullable is a breaking change.

What requires v2:

- Removing or renaming a required member, narrowing a type, or changing the meaning of a value.
- Adding a required member, since every existing producer would instantly stop validating.
- Changing `schoolId` issuance, format, or lifetime.

How a version arrives: v2 lives at its own path (`/api/v2/...`) and its own schema directory.
Producers publish both for the announced overlap; consumers ask for the newest they understand
and fall back once. A version is never changed in place, because the whole point of the pinned
fixtures is that a document that validated yesterday still validates today.

## Deprecation and the unversioned endpoints

`GET /api/summary` on a school site and `GET /api/schools` on the guiding page keep their current
behaviour, unchanged and unversioned. They are not part of v1 and are not being edited by it: the
browser page and any existing reader must keep working while the v1 path is built beside them.
The [`summary/invalid-legacy-unversioned.json`](fixtures/summary/invalid-legacy-unversioned.json)
fixture pins that relationship from the other side -- a legacy body is *not* a v1 body, so nobody
can quietly serve one where the other is expected.

Retiring either endpoint needs, in writing: every listed school on v1, no non-app reads for a
stated observation window, and a separate issue that records both. Until then they stay.

## Error isolation

One school's bad data must never cost another school its listing:

- The guiding page validates each school separately. A school whose summary or manifest fails
  validation is published with `integrationStatus: "incompatible"` and a short
  `unavailableReason`; it is never dropped from the response and never fails the response.
- `/api/v1/schools` therefore stays valid with a mix of statuses --
  [`app-directory/valid-mixed.json`](fixtures/app-directory/valid-mixed.json) is the fixture that
  says so -- and an empty school list is a valid document, not an error.
- The app decodes schools one at a time and keeps the ones that decode. An `incompatible` school
  is shown and not openable; a school that fails to decode entirely is skipped without emptying
  the list.
- `unavailableReason` is written by the guiding page, never echoed from the failing school, and
  is capped at 200 characters.
- A school whose summary never validated has no name to show. `name` stays required anyway --
  the app must not have to render a nameless row -- and the guiding page falls back to the
  registry slug, as in
  [`app-directory/valid-incompatible-name-fallback.json`](fixtures/app-directory/valid-incompatible-name-fallback.json).
  The fallback is the guiding page's own text, so it can never be a string the failing school
  chose.

## Mobile authentication

The app cannot sign in inside its web view -- the identity provider refuses embedded web views,
and `ASWebAuthenticationSession` does not share cookies with `WKWebView`. So the system browser
performs the sign-in and hands back a one-time code, and the school's own web view spends that
code to obtain the session cookie it needs.

1. **start** -- the app opens `startUrl` from the school manifest with `schoolId`, `state`, an
   S256 `code_challenge`, `redirect_uri` and an optional site-relative `return_to`. The school
   refuses an unknown `schoolId`, a `redirect_uri` that is not a registered callback, an absolute
   or protocol-relative `return_to`, and any parameter it does not know. It stores the pending
   flow and redirects to the identity provider.
2. **callback** -- after the provider answers, the school redirects to the universal link with
   `schoolId`, the original `state`, and either a one-time `code` or an `error`. No token, cookie
   or profile field ever travels here. The fallback web page at that link must not display or
   consume the code.
3. **complete** -- the app matches `state` to its pending flow, then has the school's web view
   `POST` `schoolId`, `code` and `code_verifier` to `completeUrl`. The school hashes the verifier,
   compares it to the stored challenge, marks the code used, sets the session cookie on its own
   origin, and answers with where to go next.

Rules the schemas cannot express:

- The code lives 60-120 seconds, is accepted exactly once, and only the SHA-256 digest of it is
  stored. A second attempt with the same code is `invalid_grant`, whether or not the first
  succeeded.
- `state` is the app's; the school returns it unchanged and matches nothing on it. The app treats
  a `state` it does not recognise as an attack, not as a retry.
- The verifier never leaves the app until `complete`, and never travels in a URL.
- Cancelling is `access_denied` and is not an error to report; an expired code is `expired_code`
  and the app restarts the flow at most once without asking.
- The web sign-in path on a school site is untouched: mobile auth is a second entrance, not a
  replacement.

[`vectors/mobile-auth.json`](vectors/mobile-auth.json) pins the PKCE pair, the digest of a sample
code, the exact start and callback URLs, and which error each failure must produce. All three
repositories assert against those numbers.

The one official callback is `https://clubs.bangxiao.net/mobile-auth/callback` -- a Universal Link
on this domain, never a custom URL scheme another app could register. This guiding page serves the
Apple App Site Association at `/.well-known/apple-app-site-association` (only once a production app
id is configured) and a static fallback at the callback path that consumes nothing. See
hsclubs-app#2.
