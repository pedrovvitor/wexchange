# ADR 0006 — Correcting metrics and an operable observability baseline

- **Status:** Accepted
- **Date:** 2026-09-04
- **Issue:** #9
- **Supersedes:** None
- **Superseded by:** None

## Context

`MetricsHelper` (issue #6-era `adapter.out.fiscal`) took the country-currency
sync's elapsed time as a raw, unlabelled `long` and recorded it directly as
Micrometer nanoseconds. Its caller,
`CountryCurrencyUpdaterService.synchronizeCountryCurrencies`, actually passed
`StopWatch.getNanoTime()` — already nanoseconds — so no live under-reporting
bug existed at the moment this issue was picked up. What the parameter shape
made possible is the point: nothing about a bare `long` tells a caller or a
reviewer which unit is expected, and a future caller passing milliseconds
(exactly the mistake this issue's title describes) would compile, run, and
silently under-report duration by a factor of a million with no test able to
catch it, because the parameter type carries no unit information at all. This
issue is scoped as fixing that class of risk, not chasing a specific defect
that turned out not to exist in the current codebase.

The same class had a second, unrelated instrumentation dead-end:
`incrementUnmappedExceptionMetric()` was never called from any production
code path, and its sibling metric name contained a double dot
(`wexchange.application.update..retrieval.time`) that would have produced a
malformed Prometheus series name.

Beyond the metrics bug, issue #9's broader scope is to take this service from
"metrics exist somewhere" to "an operator can actually run this": Actuator
exposure was intentionally left at `health` only pending this issue,
structured JSON logs did not exist even in the container-friendly production
profile, and there was no propagation at all of a request's identity from an
inbound HTTP call through to the outbound call this service makes to the
fiscal data provider.

## Decision

### The ambiguous-unit fix: delete `MetricsHelper`, extend `CountryCurrencySyncMetrics`

`MetricsHelper` is deleted outright, not deprecated in place — the pattern
being removed only reappears if something is left to copy. Its one live
responsibility (recording the sync's elapsed time) moves to
`CountryCurrencySyncMetrics.recordDuration(Duration)`, which already existed
for the sync's success/failure gauges. A `Duration` parameter is the type
itself carrying the unit — there is no way to call it with an ambiguous
number. `synchronizeCountryCurrencies` now calls `watch.stop()` before
reading `Duration.ofNanos(watch.getNanoTime())`; this is a minor tightening
(`StopWatch.getNanoTime()` was already documented to work on a running watch)
rather than a required fix, added because starting from an explicitly stopped
watch is the less surprising reading order.

The dead `incrementUnmappedExceptionMetric()` counter is not deleted outright;
it is actually wired up as `GlobalExceptionMetrics.incrementUnmappedError()`,
called from `GlobalExceptionHandler.handleUnexpected`. This is the one path
in the whole error-handling chain that catches a genuinely unclassified
failure — every other handler in that class maps to a specific, expected
error — so a rising rate on this one counter is meaningful on its own,
independent of any single exception's message (which is deliberately never
attached as a label; see below).

`GlobalExceptionMetrics` lives in `adapter.in.web`, not
`adapter.out.fiscal` where `MetricsHelper` lived, because
`GlobalExceptionHandler` is a web-layer class and `HexagonalBoundariesTest`
forbids `web` from depending on `fiscal`. Placing it correctly the first time
avoids repeating the exact layer-cycle mistake ADR 0001's own boundary rules
exist to catch (previously hit and fixed in issue #17, for an unrelated pair
of classes).

### Actuator: health, readiness/liveness, and Prometheus, still same-origin

`management.endpoints.web.exposure.include` becomes `health,prometheus`.
`show-details: never` is unchanged — component internals (datasource state,
disk space) still never reach an unauthenticated caller. Probes are turned
on (`management.endpoint.health.probes.enabled: true`,
`management.health.livenessstate.enabled`/`readinessstate.enabled: true`),
adding `/actuator/health/liveness` and `/actuator/health/readiness` as
distinct signals, per this codebase's own quality-foundation requirement that
liveness and readiness stay distinct.

Readiness is explicitly widened to include `db`
(`management.endpoint.health.group.readiness.include: readinessState,db`):
the database is a required dependency, and a replica that cannot reach it
should not receive traffic. The fiscal data provider is deliberately **not**
added to readiness — it already has its own circuit breaker (issue #3)
isolating callers from its failures, and a struggling upstream provider must
not pull an otherwise-healthy replica out of rotation over a dependency nothing
in the conversion flow blocks on synchronously.

Prometheus and health stay on the same port as the public API, rather than
moving to a separate `management.server.port`. A separate management port is
the more common production-hardening pattern, and was considered, but it
would require changes to the Docker healthcheck, compose networking, and any
future ingress configuration — none of which this issue's own dependency
("Secure and deterministic Docker quickstart") establishes as already solved
for a second port. Instead, this ADR records the same accepted trust boundary
ADR 0002 already established for the public API: nothing here carries its own
authentication, and keeping the Prometheus scrape endpoint off any public
ingress is an explicit **operator** responsibility, documented in
`docs/engineering/observability.md`, not an application-level concern. This
mirrors ADR 0002's own reasoning for CORS and security headers being
configuration the operator must set correctly for their deployment, rather
than something the application can enforce unilaterally from inside a
container.

`management.metrics.tags.application: wexchange` adds one stable,
low-cardinality tag to every series — enough to distinguish this service's
metrics from any other target a shared Prometheus scrapes, without adding a
per-instance identity (pod name, host) that belongs to the scraper's own
target labels instead. `management.metrics.distribution.percentiles-histogram`
is turned on for `http.server.requests`, which is what makes
histogram-based PromQL quantiles (`histogram_quantile`) possible at all for
that series.

### RED, JDBC pool, and worker metrics: mostly already free, one gap closed

Spring Boot's own `http.server.requests` timer, tagged by route template
(`uri`), method, status, and outcome, is auto-configured the moment Actuator
and Micrometer are present — this issue's actual gap was that nothing scraped
it, not that it didn't exist. The same is true for HikariCP: Spring Boot
auto-registers `hikaricp.connections.*` gauges the moment a `MeterRegistry`
bean exists in the context, with no extra wiring. Both are proven by
`ActuatorEndpointsIT`, which scrapes `/actuator/prometheus` and asserts on
`http_server_requests_seconds_count` and `hikaricp_connections` directly,
rather than trusting the framework's documented behaviour on faith — the same
principle ADR 0005 already applied to `pg_try_advisory_xact_lock`.

The one genuine gap was the fiscal client's total-deadline `ExecutorService`
(`HttpFiscalDataClient`'s `deadlineExecutor`): a plain, unmonitored
`Executors.newCachedThreadPool`. `FiscalClientMetrics.monitorExecutor` wraps
it with Micrometer's own `ExecutorServiceMetrics`, under Micrometer's
standard `executor.*` names tagged `name=fiscal-client-deadline` — not this
class's own `wexchange.application.integration.fiscal.*` prefix, because
those names describe fiscal-provider call outcomes specifically, while
`executor.*` is a generic, tool-recognized shape for any JVM thread pool.

Circuit-breaker (`FiscalClientMetrics.bindTo`, from issue #3) and rate-limiter
(`AbuseControlMetrics`, from issue #17) metrics already existed and are
unchanged by this issue — both were already wired to their respective
event sources rather than being unused instrumentation, so there was nothing
here for this issue to fix or replace. Scheduler-freshness likewise already
existed as `CountryCurrencySyncMetrics`'s last-success/last-failure gauges
(issue #6); this issue only added the duration timer to that same class (see
above).

### Structured logs: JSON in production, unchanged elsewhere, correlated everywhere

`logback-spring.xml`'s `production` profile now emits one `LogstashEncoder`
JSON object per stdout line instead of the prior plain-text pattern — every
MDC field, including `traceId` (below), is included automatically, with no
per-field wiring. Non-production output keeps its existing plain-text
pattern and file appender unchanged; both now also interpolate `%X{traceId}`,
so local development gets the same correlation without switching to JSON,
where a human is reading the console directly rather than a log aggregator
parsing it.

This does not touch the already-correct part of the prior setup: production
was already stdout-only (`application-production.yml`'s own "Twelve Factors"
note predates this issue), so "file-based logging is a poor fit for
containers" was already solved for the environment that actually runs in
containers. What this issue closes is that stdout output was not structured
and carried no correlation id, not that it was writing to a file in
production.

### A "light" `traceparent`: correlation, not distributed tracing

`TraceIdFilter` now honors an inbound W3C `traceparent` header when present
— reusing its trace-id field for this request rather than generating a new,
unrelated one — and always shapes the id it puts into the `traceId` request
attribute and MDC as a valid 32-character-hex trace-id, whether reused or
freshly minted. `HttpFiscalDataClient` reads that value (via SLF4J's `MDC`,
captured once on the calling thread before the total-deadline executor takes
over — see "Capturing MDC before the executor boundary" below) and forwards
it as its own outbound `traceparent` header on every attempt, including a
followed redirect, when a value is present; the scheduled country-currency
sync runs with no inbound request at all and simply sends none.

`TraceParent`, the shared parsing/generation logic, lives in
`application.tracing`, not in either adapter it connects. Both
`adapter.in.web` (`TraceIdFilter`) and `adapter.out.fiscal`
(`HttpFiscalDataClient`) need it, and `HexagonalBoundariesTest`'s layer rule
only allows adapter-to-adapter reuse to happen through the `application`
layer (`application.mayOnlyBeAccessedByLayers("web", "persistence", "fiscal",
"bootstrap")`) — there is no rule permitting `web` and `fiscal` to depend on
each other directly, and there should not be one.

This is deliberately **not** full distributed tracing. There are no spans, no
parent/child relationships tracked across calls, and no tracing backend
(`micrometer-tracing` plus a bridge such as Brave or OpenTelemetry) receiving
anything. What exists is enough to answer "which request produced this log
line / this error response / this outbound fiscal call" — the acceptance
criterion this issue is actually held to ("Problem Details include the same
traceId used by structured logs") — without introducing the infrastructure
real span tracking requires. Issue #19 (SLIs/SLOs/alerting, which lists
"trace spans" in its own acceptance criteria) is where that infrastructure
decision belongs, and depends on this issue's `traceId` plumbing already
being in place. JDBC-level propagation (e.g. injecting the trace id as a SQL
comment via a Hibernate `StatementInspector`) is not added either: every
JDBC call in this codebase's request-serving paths runs synchronously on the
same thread as the inbound request, so MDC correlation already ties any log
line a repository emits to the same `traceId` with no separate mechanism
needed. This should be revisited only if an async or reactive JDBC access
path is introduced.

### Capturing MDC before the executor boundary

`HttpFiscalDataClient.fetchAllPagesWithDeadline` submits the actual paginated
fetch to `deadlineExecutor` and blocks on `Future#get` with the configured
total deadline. `MDC` is thread-local, and a plain `ExecutorService.submit`
does not copy the calling thread's MDC context into the worker thread that
runs the task. `MDC.get("traceId")` is therefore read once, on the calling
thread, *before* the submission, and the resulting `String` (not a
thread-local lookup) is threaded explicitly through `fetchAllPages`,
`buildRequest`, `executeWithResilience`, `sendOnce`, and
`followRedirectOnce` as a plain parameter. This is the standard fix for MDC
not crossing a thread-pool boundary, applied narrowly rather than reaching
for a general-purpose MDC-propagating executor wrapper this codebase has no
other use for.

## Enforcement

- `HttpFiscalDataClientIT` proves outbound propagation against a real local
  HTTP server (not a mock): one test sets `MDC` on the calling thread and
  asserts the server observes a `traceparent` header whose trace-id field
  matches, spec-shaped (`00-<trace-id>-<16 hex>-01`); another asserts no
  header is sent at all when no trace id is present (the scheduled-sync
  case), and a third proves the total-deadline executor is genuinely
  monitored (`executor.pool.size` tagged `name=fiscal-client-deadline`).
- `TraceParentTest` covers generation shape, valid extraction, and every
  rejection path (wrong field count, wrong length, non-hex characters, the
  all-zero sentinel) for the header-parsing logic in isolation.
- `TraceIdFilterTest` proves reuse of a valid inbound header, fallback to a
  freshly minted id for a missing or malformed one, MDC population during the
  filter chain and clearing afterward — including when the chain throws.
- `ActuatorEndpointsIT` boots the real application (Testcontainers Postgres,
  random port, the actual filter chain) and asserts: `/actuator/health`
  reports `UP` with no `components` key, `/actuator/health/readiness` and
  `/actuator/health/liveness` both report `UP`, `/actuator/prometheus`
  returns histogram-enabled RED series, HikariCP pool series, and the
  scheduler-freshness gauge, every series carries `application="wexchange"`,
  and an unexposed endpoint (`/actuator/env`) is a 404. It declares
  `@AutoConfigureMetrics` because `@SpringBootTest` disables all
  metrics-export auto-configuration by default — without it, this exact test
  class would see `/actuator/prometheus` 404 even though the endpoint is
  genuinely present outside of tests, which is precisely the failure mode
  this ADR's own verification had to diagnose while writing it.
- `CountryCurrencySyncMetricsTest` and `GlobalExceptionHandlerTest`'s new case
  cover the two rewired metrics (`recordDuration`, the unmapped-error
  counter) directly against a `SimpleMeterRegistry`.
- `CountryCurrencyUpdaterServiceTest` verifies `syncMetrics.recordDuration`
  is called on a successful run, replacing its old assertion against the
  now-deleted `MetricsHelper`.

## Consequences

**Accepted costs.**

- Prometheus scraping and health both remain reachable on the same
  unauthenticated port as the public API. Keeping the scrape endpoint off any
  public ingress is now an explicit, documented operator responsibility
  (`docs/engineering/observability.md`) rather than something the application
  enforces for itself.
- `traceparent` propagation is correlation-only: it does not produce spans,
  is not visible to any tracing backend, and does not track call
  relationships beyond "these events shared one trace id." Genuine
  distributed tracing remains issue #19's scope.
- JDBC-level trace propagation (SQL comments) is not implemented. Correlation
  for JDBC-adjacent log lines relies on every such call happening
  synchronously on the request thread today; an async or reactive JDBC path
  introduced later would need its own fix.

**What this buys.**

- No metric helper in this codebase accepts an ambiguous raw duration
  anymore; the one that did is deleted, not merely deprecated.
- An operator can distinguish "the process is alive" from "this replica
  should receive traffic" via two real health groups, and this codebase's own
  test suite proves that a struggling fiscal provider does not fail the
  readiness check.
- A request can be found in structured production logs, in Problem Details,
  and in the fiscal provider's own logs (via the outbound header) using the
  same `traceId`, without introducing tracing infrastructure this issue was
  not scoped to build.
- RED metrics, JDBC pool health, worker-pool state, circuit-breaker/rate-
  limiter activity, and scheduler freshness are all now actually reachable
  through one scrape endpoint, proven against the real running application
  rather than assumed from documentation.

**Rejected alternatives.**

- *A separate `management.server.port` for Actuator.* The standard
  production-hardening pattern, and revisited once the Docker/ingress setup
  this issue depends on is extended to support a second port — but out of
  scope for this issue's own dependency, which only established a secure,
  deterministic single-port quickstart.
- *Full distributed tracing (`micrometer-tracing` + Brave/OpenTelemetry) now,
  instead of a light `traceparent`.* Would satisfy the letter of "propagate
  W3C traceparent" more completely, but introduces a tracing backend
  dependency and span-lifecycle concerns this issue's acceptance criteria
  (a shared `traceId` across logs, metrics labels, and Problem Details) do
  not actually require. Left to issue #19, which already lists trace spans
  as its own acceptance criterion and depends on this issue.
- *Hunting for the literal "ms passed as ns" defect described in the issue's
  title.* No such live call site existed; every current Timer/Duration/
  StopWatch use in the codebase was already unit-consistent. Treating this as
  "fix the parameter shape that made the mistake possible" produces a
  durable guarantee (the type system rejects an ambiguous call); chasing a
  non-existent bug would not.
