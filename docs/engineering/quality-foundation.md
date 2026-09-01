# Wexchange Quality Foundation

## Purpose

This document turns the engineering contract into a practical quality strategy.
It is intentionally risk-based: controls are introduced when the application has
the corresponding surface, not as decorative infrastructure.

The initial product is an anonymous public quotation experience. Authentication
is therefore not a baseline requirement. It becomes necessary when the product
stores private user data, exposes personalized history, or adds privileged
administrative operations.

## Definition of done

A change is complete only when:

1. Its issue scope and acceptance criteria are satisfied.
2. Architectural boundaries remain valid and are covered by executable checks.
3. Relevant success, boundary, and failure behavior is tested.
4. Public contracts and operational documentation are updated.
5. Security, privacy, resilience, and observability consequences are considered.
6. The complete available repository quality gate passes, or pre-existing
   failures are identified with reproducible evidence.
7. The pull request is small enough to review and contains no unrelated changes.

## Architecture baseline

Use ports and adapters with these conceptual areas:

- **Domain:** currencies, quotes, rates, conversion rules, monetary precision, and
  domain errors. Plain Java only.
- **Application:** use cases and inbound/outbound ports. Coordinates domain work
  but knows nothing about controllers, JPA, or concrete providers.
- **Inbound adapters:** REST controllers, request validation, API DTOs, exception
  mapping, and eventually UI-facing delivery concerns.
- **Outbound adapters:** exchange-rate providers, persistence, cache, telemetry,
  and messaging integrations.
- **Bootstrap/configuration:** Spring wiring and runtime configuration.

Dependency direction is enforced with ArchUnit. Provider DTOs and persistence
entities are translated at adapter boundaries and never become domain models.

Create ADRs for decisions that are expensive to reverse, including provider
fallback, cache semantics, rate-limiting topology, authentication, and deployment
architecture.

## Test strategy

### Unit tests

Prioritize deterministic tests for:

- monetary precision, scale, and rounding;
- direct, inverse, and cross-rate conversions;
- invalid, missing, stale, zero, or negative rates;
- time-dependent behavior through an injected `Clock`;
- orchestration, fallback, and error mapping in application use cases.

### Integration tests

- Use PostgreSQL Testcontainers for persistence behavior, migrations,
  constraints, transactions, and queries.
- Use a local HTTP stub such as WireMock for providers, covering latency,
  malformed payloads, throttling, timeouts, and server errors.
- Do not call live external services from automated tests.
- Test Spring wiring only where it provides meaningful integration confidence.

### Contract and architecture tests

- Verify the published OpenAPI contract and RFC 9457 error shape.
- Add ArchUnit rules for dependency direction and forbidden framework leakage.
- Add provider mapping tests so upstream changes fail at a controlled boundary.

### End-to-end tests

When the frontend exists, keep a small browser suite for the highest-value paths:
loading a quotation, changing currencies, entering an amount, seeing a converted
value, and receiving an accessible failure state. Do not duplicate the entire
unit suite in E2E tests.

### Coverage and mutation testing

[`test-taxonomy.md`](test-taxonomy.md) records how these targets are enforced
today: which Gradle task owns each one, what is excluded and why, and which
pre-existing findings are recorded as ratcheted debt.

Initial enforceable targets:

| Scope | Line coverage | Branch coverage |
| --- | ---: | ---: |
| Domain and application | 90% | 85% |
| Repository overall | 80% | 70% |

For critical financial/domain rules, target at least 80% mutation score and 90%
test strength. Establish a measured baseline before enforcing a gate; raise a
temporarily lower threshold incrementally and document the reason and expiry.

Generated code, configuration-only classes, and trivial framework bootstrap may
be excluded when the exclusion is explicit and justified. Do not exclude hard
business code to improve the number.

## API and boundary validation

- Define public APIs with OpenAPI and version them deliberately.
- Validate path, query, header, and body input before invoking a use case.
- Model supported currencies explicitly and reject unsupported combinations.
- Validate provider payload shape, currency identity, timestamps, and numeric
  invariants before trusting the data.
- Use RFC 9457 Problem Details consistently, with stable application error codes
  where callers need programmatic handling.
- Do not expose internal exceptions or provider response bodies.

## Security controls by surface

### Required now

- Secret scanning and dependency vulnerability scanning in CI.
- No credentials or sensitive data in code, fixtures, logs, or documentation.
- Strict input validation and output encoding.
- Explicit CORS allowlist for deployed clients.
- Safe HTTP headers, TLS at the deployment boundary, and sanitized error output.
- Rate limiting for public quotation endpoints once publicly deployed. The design
  must account for trusted proxies and must not blindly trust spoofable forwarding
  headers.

### Triggered by later features

- Add authentication and backend authorization when private user state or admin
  behavior is introduced.
- Add CSRF protection when browser credentials are cookie-based.
- Add upload validation and isolated object storage only if uploads are introduced.
- Add idempotency keys to mutating financial or transactional endpoints; a
  read-only quotation request does not need artificial idempotency machinery.

## Resilience and performance

- Set bounded connection and read timeouts for every outbound call.
- Retry only transient failures on idempotent operations with exponential backoff,
  jitter, and a strict attempt/time budget.
- Use circuit breakers and provider fallback only after defining freshness,
  correctness, and failure semantics.
- Define cache keys, TTL, stale-data behavior, and invalidation explicitly. Never
  cache incorrect or unvalidated provider responses.
- Use `BigDecimal` and document rounding rules at conversion boundaries.
- Measure before adding database indexes or distributed infrastructure. Verify
  important queries with realistic plans and data volumes.
- Establish performance budgets for API latency and, once a frontend exists, Core
  Web Vitals and bundle size.

## Observability

- Structured logs contain fields such as timestamp, level, service, environment,
  trace/correlation ID, operation, status, and duration.
- Do not use raw user input, IP addresses, secrets, or exception payloads as metric
  labels.
- Instrument request rate, latency, error rate, saturation, provider behavior, and
  cache outcomes with low-cardinality dimensions.
- Propagate W3C Trace Context across supported boundaries.
- Alert on user-impacting symptoms and sustained failure, not isolated noise.

## CI/CD baseline

Every pull request should eventually execute one reproducible top-level Gradle
gate containing:

- compilation and static analysis;
- unit, architecture, and integration tests;
- coverage verification;
- dependency and secret scanning;
- artifact/container build where applicable.

Run expensive mutation, container, and DAST checks on a schedule or targeted
workflow when their duration makes them unsuitable for every commit. They remain
release evidence and may not be silently skipped.

Deployments require immutable artifacts, separate configuration, liveness and
readiness probes, rollback documentation, and environment-specific smoke tests.
Terraform/OpenTofu becomes required when managed infrastructure is introduced;
do not add empty IaC merely to tick a box.

## Governance

- One issue, one branch, one reviewable pull request.
- Link pull requests to their issue and record verification evidence.
- Use conventional, descriptive commits if the repository adopts that convention.
- Record meaningful architectural decisions under `docs/adr/`.
- Treat the thresholds and controls here as a ratchet: changes require evidence
  and must not quietly reduce protection.
