# ADR 0002 — The anonymous API security baseline

- **Status:** Accepted
- **Date:** 2026-09-04
- **Issue:** #16
- **Supersedes:** nothing
- **Superseded by:** nothing

## Context

Wexchange has no login, no session, and no per-user data: it is a public
demonstration of currency quotation and purchase-conversion behaviour over
synthetic values. That is a legitimate product shape, but it is only a *safe*
one when the boundaries around it are explicit and enforced, not merely
implied by the absence of a login screen. Before this issue, none of the
public-facing hardening a browser client can act on existed: no CORS policy
(any origin's fetch would have been processed, credential-free or not), no
`X-Content-Type-Options`/`X-Frame-Options`/`Referrer-Policy`/`Permissions-Policy`
baseline, interactive API documentation (`/swagger-ui`, `/v3/api-docs`) live
unconditionally in every environment including production, and no stated
position on trusting `X-Forwarded-*` headers.

## Assets

- Synthetic purchase records (description, date, amount) and the exchange
  rates and country-currency catalogue mirrored from the fiscal data
  provider. None of this is real financial data, and none of it is
  personally identifying - there is no user account for it to belong to.
- Application availability and the fiscal data provider's own rate limits
  (an anonymous caller could exhaust either; bounding that is issue #17's
  scope, not this one's).
- The correctness of the RFC 9457 error contract issue #7 established -
  Problem Details responses must not become a channel for stack traces or
  internal configuration.

There is no credential, session token, or payment instrument anywhere in this
system for an attacker to steal, because none exists.

## Trust boundaries

1. **Public internet → this application.** Anonymous, unauthenticated, over
   plain HTTP today (see "HSTS" below). This is the boundary this ADR mostly
   concerns itself with.
2. **This application → the fiscal data provider.** Outbound only, resilience
   and timeout behaviour owned by issue #3's `FiscalDataClient`. Not a trust
   boundary a caller can influence.
3. **This application → PostgreSQL.** Same network (issue #5's Testcontainers
   integration tests exercise this engine directly); the schema has no
   caller-supplied identifiers beyond a server-generated purchase id and
   values Bean Validation has already checked.

There is currently no reverse proxy, load balancer, or TLS-terminating edge in
front of the application (confirmed against `docker-compose.yml` and the
`Dockerfile`: the app binds directly on `8080`). That absence is itself a
decision this ADR records, not an oversight - see "Trusted proxies" below.

## Abuse cases considered

- **Cross-site data exfiltration via a malicious page's `fetch()`.** Mitigated
  by an explicit CORS allowlist (`app.cors.allowed-origins`, empty by
  default) with `credentials: false` - an unconfigured deployment permits no
  browser origin at all, rather than accidentally permitting every one.
- **Clickjacking**, framing this API's JSON responses inside an attacker's
  page to misrepresent them to a user. Mitigated by `X-Frame-Options: DENY`.
  This API has no HTML surface meant to be framed, so denial is unconditional.
- **MIME-sniffing** turning a JSON or Problem Details response into
  executable content in an old browser. Mitigated by
  `X-Content-Type-Options: nosniff`.
- **Referrer leakage** - a link out of a future HTML surface leaking a full
  URL, including query parameters, to a third-party `Referer` header.
  Mitigated by `Referrer-Policy: strict-origin-when-cross-origin`.
- **IP spoofing via `X-Forwarded-For`**, were a caller to control the address
  the application believes it is talking to. Mitigated by never trusting a
  forwarded header at all (`server.forward-headers-strategy: none`) - the
  only correct posture when nothing sits in front of the application to have
  set that header legitimately in the first place.
- **Information disclosure through development tooling reaching
  production** - interactive API documentation revealing the full route
  surface and schema to anyone who requests it. Mitigated by disabling
  `springdoc.swagger-ui`/`springdoc.api-docs` under the `production` profile
  only; the development convenience stays intact everywhere else.
- **Resource exhaustion** (unbounded purchase creation, oversized
  pages/payloads, upstream cache-miss amplification) is a real abuse case
  against this product but is explicitly **issue #17's scope**, not this
  one's. This ADR's controls do not bound request volume.

## Decision

Authentication, authorization, and CSRF protection are **not applicable**
under current conditions:

- There is no user account, session, or cookie for CSRF to forge, or for an
  authorization check to gate.
- Every route is deliberately `permitAll()` in `SecurityConfig` - explicit,
  not an omission a future reviewer has to infer from the absence of a
  filter chain.
- Spring Security is a dependency here for its CORS and header
  infrastructure only. `UserDetailsServiceAutoConfiguration` is excluded on
  `Main` specifically so no throwaway in-memory user or generated password
  exists for nothing to ever challenge against.

**Conditions that make authentication mandatory** (any one of these reopens
this decision):

- Any endpoint begins accepting, storing, or returning data that identifies a
  real person or a real financial instrument.
- Purchases stop being anonymous and start being scoped to an account,
  tenant, or session.
- A privileged operation (administrative, bulk, or destructive) is added that
  an anonymous caller should not reach.
- The deployment becomes multi-tenant in any sense where one caller's data
  must be kept from another's.

CORS is enforced by allowlist, not wildcard:

- `app.cors.allowed-origins` defaults to an empty list. A deployment with a
  real frontend sets it explicitly.
- Allowed methods are `GET` and `POST` - this API's actual surface, nothing
  broader granted speculatively.
- Allowed headers include `Content-Type` and `Idempotency-Key`; the latter
  ahead of issue #18 landing, so introducing idempotent purchase creation
  later does not also require a CORS change.
- `allowCredentials(false)` always: this API never sets a cookie, so there is
  no credentialed cross-origin request to permit.

**Trusted proxies:** `server.forward-headers-strategy` stays at `none`
(Spring Boot's own default, declared explicitly here so the choice is
visible) because no reverse proxy or load balancer exists in this topology
today. The moment one is introduced, this must change together with an
explicit trusted-proxy IP allowlist configured at that boundary - trusting a
forwarded header with no proxy in front of the application to have set it
would let any caller claim any address.

**HSTS** uses Spring Security's default behaviour rather than a manual
toggle: its header writer only ever adds `Strict-Transport-Security` to an
already-secure (HTTPS) response, so it stays silent over today's plain-HTTP
topology by construction and activates automatically once a verified HTTPS
deployment profile terminates TLS in front of this application - no code
change needed then, just the deployment fact becoming true. **HSTS preload is
a separate, later decision**: preloading commits a domain (and every
subdomain, depending on directive) to HTTPS-only in browsers' built-in preload
lists, which is close to irreversible in practice. That decision needs its
own domain-wide readiness review and is explicitly out of scope here.

## Consequences

- A public frontend must be added to `app.cors.allowed-origins` explicitly
  before it can call this API from a browser; there is no wildcard fallback.
- Interactive API documentation genuinely disappears under the `production`
  profile. Anyone needing it in a deployed environment needs a different,
  explicitly-provisioned access path - not a change to make casually.
- Introducing a reverse proxy or load balancer later requires revisiting
  `server.forward-headers-strategy` and this ADR together, not just the proxy
  configuration in isolation.
- Rate limiting, request-size limits, and other abuse-resistance controls
  remain entirely unaddressed by this decision. Issue #17 owns them.
