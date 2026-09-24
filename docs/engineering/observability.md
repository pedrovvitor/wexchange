# Observability

What this service exposes, how to read it, and what an operator is
responsible for beyond what the application enforces on its own. See
[ADR 0006](../adr/0006-observability-baseline.md) for why each of these
decisions was made, including what was deliberately left out of scope.

## Endpoints

All of the below are served on the same port as the public API
(`server.port`, `8080` by default) — there is no separate management port.
None of them carry application-level authentication.

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Overall status only (`UP`/`DOWN`); `show-details: never`, so no component internals (datasource state, disk space) are ever returned to a caller. |
| `GET /actuator/health/liveness` | Is the process itself still running. Never reflects the database or the fiscal provider. |
| `GET /actuator/health/readiness` | Should this replica receive traffic. Includes the database (`db`) — a required dependency — but deliberately **excludes** the fiscal data provider: its own circuit breaker (issue #3) already isolates callers from its failures, and a struggling upstream must not pull an otherwise-healthy replica out of rotation. |
| `GET /actuator/prometheus` | Every metric below, in Prometheus exposition format. |

**Operator responsibility:** keep `/actuator/prometheus` (and, ideally,
`/actuator/health*`) off any public ingress. Nothing in the application
enforces this — it is a network/reverse-proxy concern, the same trust
boundary [ADR 0002](../adr/0002-anonymous-api-security-baseline.md) already
established for CORS and security headers. Scrape it from inside the
deployment's own trusted network only.

Every series carries a stable `application="wexchange"` tag
(`management.metrics.tags.application`) so a shared Prometheus instance
scraping more than one service can tell them apart. It is not a per-instance
identity — pod name or host belongs to the scraper's own target labels, not
this tag.

## Correlating one request across logs, metrics, and error responses

`TraceIdFilter` gives every inbound request a `traceId`: it reuses an inbound
W3C `traceparent` header's trace-id field when the caller sent one, or mints
a fresh, spec-shaped (32 lowercase hex characters) one otherwise. That id is:

- put into SLF4J MDC for the duration of the request, so every log line
  emitted while handling it carries the same `traceId` field;
- returned in every Problem Details error response's `traceId` property
  (`GlobalExceptionHandler`);
- forwarded as this service's own outbound `traceparent` header on every call
  `HttpFiscalDataClient` makes to the fiscal data provider while serving that
  request (including a followed redirect).

This is correlation, not distributed tracing: there are no spans, and no
tracing backend receives anything. It answers "which request produced this
log line / this error / this outbound call", not "how did this call's timing
break down across services." Full span tracing is issue #19's scope. The
scheduled country-currency sync runs with no inbound request in progress, so
it sends no `traceparent` at all on its own calls to the fiscal provider.

**Never search logs or metrics by:** purchase description, raw exception
message, client IP, purchase ID, or a complete request URL. None of these are
ever placed in a log field or a metric label — grep for `traceId` instead, or
for the route template a metric is tagged with.

## Structured logs

In the `production` profile, every log line is one JSON object on stdout
(`net.logstash.logback.encoder.LogstashEncoder`), with every MDC field —
`traceId` included — attached automatically. In every other profile, logs
stay in the existing human-readable console (and, outside `production`, file)
format, with `traceId` interpolated into the pattern for the same
correlation while reading logs by eye during local development.

## PromQL: latency (RED - the "duration" of RED)

P50/P95/P99 latency per route, over the last 5 minutes:

```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri, method)
)
```

Average latency per route (cheaper than a quantile, useful for a dashboard's
overview row):

```promql
sum(rate(http_server_requests_seconds_sum[5m])) by (uri, method)
/
sum(rate(http_server_requests_seconds_count[5m])) by (uri, method)
```

## PromQL: errors (RED - the "errors" of RED)

Error rate per route (5xx as a fraction of all responses):

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (uri)
/
sum(rate(http_server_requests_seconds_count[5m])) by (uri)
```

Rate of unclassified (truly unmapped) failures reaching
`GlobalExceptionHandler`'s sanitized-500 fallback — every other exception in
that class maps to a specific, expected outcome, so any nonzero rate here is
worth alerting on independently of any single exception's message:

```promql
rate(wexchange_application_web_unmapped_error_count_total[5m])
```

## PromQL: upstream retries and resilience (fiscal data provider)

Retry attempts against the fiscal provider:

```promql
rate(wexchange_application_integration_fiscal_retry_attempt_count_total[5m])
```

Circuit-breaker state transitions (watch for `to="OPEN"` specifically):

```promql
sum(rate(wexchange_application_integration_fiscal_circuit_state_transition_count_total[5m])) by (to)
```

Calls rejected because the circuit was open, or the bulkhead was full:

```promql
rate(wexchange_application_integration_fiscal_circuit_rejected_count_total[5m])
rate(wexchange_application_integration_fiscal_bulkhead_rejected_count_total[5m])
```

Fiscal call duration by outcome (success vs. error):

```promql
histogram_quantile(0.95,
  sum(rate(wexchange_application_integration_fiscal_call_duration_seconds_bucket[5m])) by (le, outcome)
)
```

Total-deadline executor pool state (is the fiscal client's worker pool
saturated):

```promql
executor_pool_size{name="fiscal-client-deadline"}
executor_queued{name="fiscal-client-deadline"}
```

## PromQL: scheduled sync freshness

Seconds since the country-currency catalogue sync last succeeded — alert if
this grows well past the scheduled interval (`app.country-currency-sync.cron`):

```promql
time() - wexchange_application_scheduler_country_currency_sync_last_success_epoch_seconds
```

Whether the last attempt failed more recently than the last success (a
currently-red job):

```promql
wexchange_application_scheduler_country_currency_sync_last_failure_epoch_seconds
>
wexchange_application_scheduler_country_currency_sync_last_success_epoch_seconds
```

Sync duration:

```promql
histogram_quantile(0.95,
  rate(wexchange_application_scheduler_country_currency_sync_duration_seconds_bucket[5m])
)
```

## JDBC pool

HikariCP registers its own pool metrics automatically once a `MeterRegistry`
bean exists — no extra configuration. Active vs. idle vs. max, to catch pool
exhaustion before it shows up as request latency:

```promql
hikaricp_connections_active{pool="master"}
hikaricp_connections_idle{pool="master"}
hikaricp_connections_max{pool="master"}
```

## Rate limiting (anonymous abuse controls)

Requests rejected by the per-key token-bucket limiter or the request-body/
query-parameter guards (issue #17), by route and rejection reason - never by
the caller's address or any request-supplied value:

```promql
sum(rate(wexchange_application_web_abuse_rejected_count_total[5m])) by (route, reason)
```
