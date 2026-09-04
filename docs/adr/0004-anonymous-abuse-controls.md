# ADR 0004 — Anonymous abuse controls

- **Status:** Accepted
- **Date:** 2026-09-04
- **Issue:** #17
- **Supersedes:** None
- **Superseded by:** None

## Context

ADR 0002 set the anonymous API security baseline (CORS, security headers,
trusted-proxy stance) and was explicit about what it left out. Its Assets
section calls out "application availability and the fiscal data provider's
own rate limits (an anonymous caller could exhaust either; bounding that is
issue #17's scope, not this one's)". Its Abuse cases section lists "resource
exhaustion (unbounded purchase creation, oversized pages/payloads, upstream
cache-miss amplification)" as "a real abuse case against this product but ...
explicitly issue #17's scope, not this one's. This ADR's controls do not
bound request volume." Its Consequences close with "rate limiting,
request-size limits, and other abuse-resistance controls remain entirely
unaddressed by this decision. Issue #17 owns them."

This ADR is that follow-through. There is still no login and no session:
every caller is anonymous, and the assets, trust boundaries, and
`server.forward-headers-strategy: none` posture from ADR 0002 are unchanged
and assumed here. What changes is that four previously-unbounded surfaces are
now bounded: request rate (per route and globally), request-body size, page
size and sort-field selection on `GET /v1/country_currencies`, and how long
an anonymous purchase's data is kept.

## Decision

Add a `HandlerInterceptor`, `AbuseControlInterceptor`, registered by
`WebMvcConfig` on every `/v1/**` route, that enforces, in order: request-body
size (purchase creation only), page-size/sort/filter-length limits (the
country-currency catalogue only), then rate limiting (every route). Rejections
are thrown as `PayloadTooLargeException`, `IllegalArgumentException`, or
`RateLimitExceededException` and handled by the existing
`GlobalExceptionHandler`, which already knows how to render an RFC 9457
`ProblemDetail`. Add a scheduled `PurchaseRetentionService` that deletes
anonymous purchases past a configured age. Every threshold lives in
`AbuseControlProperties` (`app.abuse-control`) and
`PurchaseRetentionProperties` (`app.purchase-retention`), both deployment-
configurable, none asserted as a universal production truth.

### Rate limiting: an in-memory, per-instance token bucket

`TokenBucket` is a ~40-line class: a capacity (burst), a continuous refill
rate computed lazily from elapsed time on each call, and a `synchronized
consume()`. `PerKeyRateLimiter` keys a `ConcurrentHashMap<String,
TokenBucket>` by `"<remoteAddr>:<scope>"`, creating a bucket lazily on first
use. No external dependency was added for this — no Redis, no
`resilience4j-ratelimiter`, no Bucket4j — because none of those was already a
project dependency, and the mechanism a per-key limiter actually needs (an
independent, lazily-refilling counter per key, held in memory) is small
enough to own outright rather than to justify pulling in and learning a new
library for.

**Topology limitation, stated plainly:** state lives in the process's heap.
With a single running instance, a caller's budget is exactly what
`AbuseControlProperties` declares. With multiple replicas behind a load
balancer, each instance enforces its own independent budget for the same
key, because nothing is shared between them. A client whose requests are
spread evenly across *N* replicas experiences an effective rate roughly *N*
times the configured per-instance limit — three replicas triple it. This is
acceptable today because the deployment is single-instance (see ADR 0002's
own confirmation, against `docker-compose.yml` and the `Dockerfile`, that no
reverse proxy or load balancer fronts this application). It stops being
acceptable the moment horizontal scaling is introduced. Before running more
than one replica of this application, the limiter must move to something
replica-aware: either a shared, centralized store (for example Redis-backed
token buckets, so every instance consumes from the same counter) or an
edge/API-gateway limiter enforced before requests are distributed to any
replica at all. This is recorded here, rather than left to be rediscovered
under load, because `docs/engineering/quality-foundation.md` names
rate-limiting topology as one of the decisions that must live in an ADR.

### Client identity: the raw remote address, never a forwarded header

`AbuseControlInterceptor` keys every bucket off
`HttpServletRequest.getRemoteAddr()`. Its Javadoc states the reasoning
directly: per ADR 0002, no reverse proxy sits in front of this application
today and `server.forward-headers-strategy` is `none`, so no
`X-Forwarded-*` header is trusted here either. Honouring a forwarded header
with nothing in front of the application to have set it legitimately would
let any caller write its own rate-limit identity into the request and
consume another caller's budget, or evade its own. This must change together
with ADR 0002's own trusted-proxy stance: the day a reverse proxy is
introduced, both that ADR's `forward-headers-strategy` decision and this
one's `getRemoteAddr()` call need to move together to a trusted-proxy IP
allowlist that only honours a forwarded header from a known, trusted hop.

### Enforcement point: a `HandlerInterceptor`, not a `Filter`

A servlet `Filter` runs outside Spring MVC's exception-resolution machinery.
Rejecting a request from a `Filter` means hand-building an RFC 9457 response
by hand, duplicating what `GlobalExceptionHandler` already does correctly
for every other error this API produces. A `HandlerInterceptor`'s
`preHandle` runs inside the `DispatcherServlet`'s own request-handling flow,
so a rejection can simply be `throw`n as a plain exception
(`RateLimitExceededException`, `PayloadTooLargeException`,
`IllegalArgumentException`) and land in the same `@ExceptionHandler` methods
that already shape every other Problem Details response. `PayloadTooLarge`
and rate-limit rejections get their own handlers;
`IllegalArgumentException` from the query-limit checks reuses the handler
already registered for it. No new response-building code exists outside
`GlobalExceptionHandler`.

### Burst smaller than the sustained rate is intentional

The purchase-creation baseline is `capacity: 3, refillTokens: 10,
refillPeriod: 1m`. A capacity smaller than the per-period refill amount is
not a typo: capacity bounds the size of an *instantaneous* spike
independently of the *sustained* rate. A caller may spend 3 requests
immediately, then must wait for tokens to trickle back in (at 10 per minute,
one roughly every 6 seconds) rather than being able to save up a full
minute's worth of throughput and spend it all at once. Decoupling burst
tolerance from sustained rate this way is the point of a token bucket over a
fixed window counter; the two baseline route limits (`conversion` at
`capacity: 5, refillTokens: 30, refillPeriod: 1m`, `countryCurrencies` at
`capacity: 10, refillTokens: 60, refillPeriod: 1m`) follow the same shape.

### Request-body size checked at two layers

`AbuseControlInterceptor.enforceBodySizeLimit` reads
`HttpServletRequest.getContentLengthLong()` against
`app.abuse-control.max-request-body-bytes` (16 KiB by default) and throws
`PayloadTooLargeException` for a clean, RFC 9457 413. This check does
nothing for a request sent with chunked transfer-encoding, which carries no
`Content-Length` header at all and so reports `-1`. `server.undertow.
max-http-post-request-size` (also 16384, kept equal to the application-level
limit) is the defense-in-depth backstop: Undertow itself refuses to buffer a
POST body larger than that regardless of how it declares its own length.
Neither layer alone is sufficient — the interceptor gives the clean
application-level error and metric for the common case; the container limit
closes the gap the interceptor cannot see into.

### Page-size and sort validation in the interceptor, not via `Pageable`

`enforceQueryLimits` reads the raw `size`, `sort`, and `country_currency`
query parameters directly off the `HttpServletRequest`, ahead of any Spring
Data binding, and throws `IllegalArgumentException` (400) when `size`
exceeds `app.abuse-control.max-page-size`, when a `sort` property is outside
the fixed allowlist (`countryCurrency`, `country`, `currency`), or when
`country_currency` exceeds the configured filter length. This was chosen
over the alternative of binding through Spring Data's `Pageable` and
customizing a `PageableHandlerMethodArgumentResolver`
(`setMaxPageSize`/`setFallbackPageable`) for one concrete reason: Spring
Data's own default behaviour for an oversized page is to silently *clamp* it
to the configured maximum, not to reject the request. That does not satisfy
this issue's acceptance criterion, which requires an oversized page to fail
with 400, not to be quietly served at a smaller size the caller never asked
for. Doing the check in the interceptor also meant
`CountryCurrencyController`, `CountryCurrencyService`, and their existing
test suites needed no changes at all — the abuse control is entirely
additive at the web boundary, ahead of the handler method Spring Data would
otherwise bind into.

### Purchase retention: 90 days, shorter than the six-month conversion window

`PurchaseRetentionProperties` defaults `retention` to 90 days and
`cleanupInterval` to 1 hour. There is no account for this data to belong to,
so retention here is a storage and privacy-hygiene decision, not a business
rule about how long a purchase should remain useful.

That is worth stating honestly rather than glossing over: `ConversionWindow`
(`domain/exchange/ConversionWindow.java`) defines the six months of exchange
rates *preceding* a purchase's date as eligible for that purchase's
conversion — a fixed span relative to the purchase date itself, not a
rolling window measured from "now". In practice, though, once a purchase row
is deleted, `GET /v1/purchases/{id}/convert` has nothing left to look up and
simply 404s, regardless of what `ConversionWindow` would otherwise consider
eligible. Ninety days is shorter than the six-month (~180-day) span
`ConversionWindow` references. A caller who assumes "the conversion window
is six months, so I have six months to convert" is wrong in a way that
matters: the purchase itself stops existing at 90 days, well before that.
This is a deliberate, documented trade-off — not "keep the data exactly as
long as it stays useful" — and the practical guidance is that a caller must
convert a purchase well within the 90-day retention period, not within the
six months `ConversionWindow` happens to mention for a different purpose
(rate eligibility, not data lifetime).

Cleanup runs hourly (`@Scheduled(fixedDelayString =
"${app.purchase-retention.cleanup-interval}")`) as a single bulk
`PurchaseRepository.deleteByCreatedAtBefore(cutoff)` rather than loading and
deleting matched entities one at a time. There are no JPA lifecycle
callbacks or cascades on `PurchaseJpaEntity` that a bulk delete would skip,
so nothing is lost by bypassing the persistence context, and one `DELETE ...
WHERE created_at < ?` statement is materially cheaper than fetching rows
individually. `V3__purchase_retention_index.sql` adds an index on
`purchase.created_at` so that statement is not a full table scan. The job is
trivially idempotent: deleting zero rows because nothing is past the cutoff
is a no-op, and running it again over an already-cleaned table produces the
same empty result every time — there is no state the job could corrupt by
re-running.

## Rejected alternatives

- **Bucket4j.** Would add a new runtime dependency for token-bucket
  semantics a ~40-line `TokenBucket` already implements correctly for this
  project's needs. Not worth the dependency for a well-understood, small
  algorithm.
- **`resilience4j-ratelimiter`.** Its `RateLimiter` is designed around a
  single shared limiter instance, not a per-key/per-IP limiter out of the
  box. Using it per-caller would still require wrapping it in the same
  `ConcurrentHashMap<String, RateLimiter>` this ADR's own
  `PerKeyRateLimiter` already needs — so adding the dependency would not
  have simplified anything, only added one.
- **A servlet `Filter` instead of a `HandlerInterceptor`.** Runs outside
  Spring MVC's exception-resolution machinery, so a rejection would need its
  own hand-built RFC 9457 response instead of reusing
  `GlobalExceptionHandler`.
- **Clamping an oversized page instead of rejecting it.** Spring Data's own
  `Pageable` binding behaviour, but it fails this issue's explicit
  acceptance criterion that an oversized page be rejected with 400, not
  silently served at a smaller size.

## Consequences

- A single-instance deployment gets the rate limits configured in
  `app.abuse-control` exactly as configured. A multi-replica deployment does
  not, until the limiter is replaced with a shared, replica-aware one (see
  "Topology limitation" above) — this must not be forgotten when horizontal
  scaling is planned.
- Introducing a reverse proxy later requires revisiting ADR 0002's
  `forward-headers-strategy` decision and this ADR's `getRemoteAddr()` call
  together, with a trusted-proxy IP allowlist, not either one in isolation.
- A caller must convert a purchase well within 90 days of its creation, not
  within the six months `ConversionWindow` references for a different
  purpose (rate eligibility). This is a real, documented usability
  constraint, not an oversight.
- `CountryCurrencyController`, `CountryCurrencyService`, and their existing
  tests are unmodified; all new query-parameter validation lives in
  `AbuseControlInterceptor`.
- Rejection counters (`wexchange.application.web.abuse.rejected.count`,
  tagged by route and reason) and the retention job's deletion counter
  (`wexchange.application.purchase.retention.deleted.count`) are the
  operational signals this decision expects to be watched; both tag only
  fixed, low-cardinality values, never a caller's address or any
  request-supplied value.
