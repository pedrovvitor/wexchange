# ADR 0005 — Idempotent exchange-rate refresh and scheduled sync

- **Status:** Accepted
- **Date:** 2026-09-04
- **Issue:** #6
- **Supersedes:** None
- **Superseded by:** None

## Context

Two independent write paths populate `exchange_rate` and `country_currency`
from the fiscal data provider, and neither was safe under concurrency.

`ExchangeRateService` (async, triggered by purchase creation) and
`SynchronousExchangeRateLoader` (synchronous, triggered by a conversion
cache miss) both persisted through `ExchangeRatePersistence`, which checked
`existsById` for each quote before deciding whether to insert or update, and
deduplicated within a batch by hand. Two replicas refreshing the same
currency and effective-date window at the same instant raced on that
exists-check: both could observe "not present" and both attempt an insert,
one of them failing on the primary key. The same shape existed for
`country_currency` on the scheduled catalogue sync.

The scheduled sync (`CountryCurrencyUpdaterService.synchronizeCountryCurrencies`,
`@Scheduled`) had a second, distinct problem on top of that: Wexchange runs
multiple replicas behind the same PostgreSQL instance (ADR 0002), and nothing
stopped every replica's own scheduler from firing the same cron tick and each
calling the upstream fiscal provider independently. Unlike the exists-check
race, this was never a data-correctness bug — the old exists-check-then-save
would eventually converge — it was wasted, duplicate upstream HTTP calls for
work that only needs to happen once per tick, and a scheduled job that had no
way to report whether it was healthy, and no way for an automated test suite
to turn it off.

Issue #3 already gave the fiscal HTTP client its own retry, circuit-breaker,
bulkhead, and deadline policy (`FiscalDataClient`), and issue #14 established
the coverage, mutation, and architecture gates this change is held to. Both
were prerequisites: this issue does not re-litigate how the upstream call
retries, and every claim below is backed by a test that forces the specific
race or failure it describes, not by inspection.

## Decision

Replace exists-check-then-save with a PostgreSQL-native bulk upsert for both
tables, and add cross-replica mutual exclusion — a Postgres transaction-scoped
advisory lock plus a run-status tracker — around the scheduled sync only.
`ExchangeRatePersistence` and its in-batch deduplication are deleted outright;
nothing replaces them, because `ON CONFLICT` makes them unnecessary.

### `ON CONFLICT` is the concurrency defense for the write itself

`ExchangeRateUpsertRepository.upsertAll` and `CountryCurrencyUpsertRepository.upsertAll`
each issue one batched `INSERT ... ON CONFLICT (...) DO UPDATE ... WHERE
<column> <> EXCLUDED.<column>` statement. Two replicas upserting the same key
at the same instant both succeed unconditionally — Postgres serializes the
conflicting rows against each other internally, and whichever write commits
last is deterministically what is stored. This also doubles as the
correction rule for a value the provider revises after the fact: the newest
fetch always wins over what is currently stored, with no separate "is this an
update or an insert" branch in application code. The `WHERE` clause is a
no-op guard, not a correctness requirement: skipping the write when nothing
actually changed avoids rewriting an unchanged row's index entries and an
otherwise-empty WAL record, but the outcome would be identical without it.

This is why exchange-rate refresh (`ExchangeRateService`,
`SynchronousExchangeRateLoader`) needed no locking to close out this issue.
Refresh is request- and cache-miss-triggered: there is no upstream call to
deduplicate, because each caller already needs its own fetch to serve its own
request or purchase. Once the write is idempotent, a concurrent refresh for
the same window is safe by construction — there is nothing left to protect
against. `ExchangeRateService`'s own Javadoc states this directly: persisting
is a single bulk upsert, and a concurrent refresh for the exact same window
is safe by construction, not by a check the service makes.

The scheduled sync is different in kind, not just degree. Its correctness
was never in question — the upsert would have made concurrent scheduled
writes converge regardless — the problem is purely that a cron tick firing
identically on every replica means every replica calls the upstream provider,
and the acceptance criterion this issue is held to is that two application
instances produce **one** scheduled external sync for a given run. That is
an upstream-call-avoidance property, not a data-correctness property, and it
is why locking is scoped to `CountryCurrencyUpdaterService` alone.

### The auto-commit bug this change caught, and the rule it leaves behind

While implementing the upsert repositories, a batched `JdbcTemplate` write
with no enclosing Spring transaction was found to silently commit nothing.
`spring.datasource.hikari.auto-commit` is `false` for this application, so a
connection `JdbcTemplate` obtains outside of an active Spring-managed
transaction never auto-commits; the batch executes against the connection,
and the moment that connection is returned to the pool the work vanishes with
no exception raised anywhere. `ExchangeRateUpsertRepositoryIT`'s Javadoc
documents this directly: its own `@Transactional upsertAll` is what makes its
concurrency test meaningful in the first place, because each worker thread in
that test has no ambient transaction of its own to inherit.

`upsertAll` is therefore `@Transactional` on the repository method itself in
both upsert repositories, not merely for consistency with the rest of the
codebase's style. This is recorded here so it is not reintroduced: **every
future `JdbcTemplate`-based write in this codebase must declare its own
`@Transactional` boundary**, rather than relying on being called from within
a caller's transaction. A caller that happens to already be transactional
today can stop being one tomorrow — inlining a call, moving it to an async
method, calling it from a scheduled job with a different propagation — and a
write that depended on inheriting someone else's transaction would silently
stop committing with no test able to tell the difference short of asserting
on stored data after the fact, exactly as this bug did.

### A transaction-scoped Postgres advisory lock for cross-replica exclusion

`CountryCurrencySyncRunRepository.tryAcquireSyncLock` wraps
`pg_try_advisory_xact_lock`, called once at the top of
`synchronizeCountryCurrencies`'s own `@Transactional` method. A losing replica
gets `false` back immediately — it never blocks — and skips the run for that
tick entirely: no fetch, no upsert, no tracker write.

This follows the same reasoning ADR 0003 and ADR 0004 already established for
this codebase: Postgres is already the system of record every replica talks
to, so use it to arbitrate a race before reaching for new infrastructure.
ADR 0003 uses a unique-constraint compare-and-set for purchase-creation
idempotency; ADR 0004 keeps anonymous-abuse rate limiting in-process rather
than adding a shared store. This decision reuses that same preference for a
different problem: mutual exclusion between replicas for a job, rather than
compare-and-set for a resource.

The lock is specifically **transaction-scoped** (`_xact_`), not
session-scoped (`pg_try_advisory_lock`). A session-scoped lock has to be
explicitly released, which means an application crash mid-run — the exact
failure mode a scheduled background job has to tolerate — would leave the
lock held until the connection is detected as dead and reaped by the pool, an
interval this design has no reason to depend on. A transaction-scoped lock is
released automatically the instant the holding transaction commits, rolls
back, or its connection is lost, with no separate staleness check needed:
`CountryCurrencySyncRunRepository.tryAcquireSyncLock`'s own Javadoc states
this is true "including a replica crashing mid-run."

`CountryCurrencySyncLockIT` proves this against a real database rather than a
mock: `@Transactional(propagation = Propagation.NOT_SUPPORTED)` at the class
level turns off `@DataJpaTest`'s own ambient test transaction specifically so
each `TransactionTemplate` call in the test is a genuinely separate
transaction — in the concurrent case, on a genuinely separate thread — rather
than two "replicas" that are secretly the same transaction. A mock repository
would only record that the method was called twice and prove nothing about
Postgres's own mutual-exclusion guarantee.

### The lock is held for the whole job, including the upstream fetch

`synchronizeCountryCurrencies` acquires the lock, then calls
`fiscalDataClient.fetchCountryCurrencies()`, then upserts — all inside the one
`@Transactional` method the lock is scoped to. The lock is not narrowed to
just the final write.

The tradeoff accepted here is one pooled database connection held idle for
the duration of the upstream HTTP call, on whichever replica wins the lock,
for every scheduled tick. That duration is bounded, not open-ended: it is
bounded by `FiscalDataClient`'s own total-deadline from issue #3, which
already exists to stop a hung upstream call from blocking a thread
indefinitely. In exchange, this is what actually satisfies the acceptance
criterion — a lock scoped to only the write would let every replica reach the
fetch and call the upstream provider independently, and only serialize them
at the very end, which defeats the entire point of avoiding duplicate
upstream calls in the first place. Narrowing the lock to just the write was
considered and rejected for exactly this reason (see Rejected alternatives).

### Run-status tracking survives the very failure it is recording

`CountryCurrencySyncRunTracker.recordRunning` / `recordSuccess` /
`recordFailure` each run in their own `@Transactional(propagation =
Propagation.REQUIRES_NEW)` transaction, deliberately independent of the
job's own transaction. `recordFailure` is called from inside a `catch` block
in `synchronizeCountryCurrencies`, immediately before the exception is
rethrown and the job's own transaction — the one holding the advisory lock —
rolls back. Without `REQUIRES_NEW`, the failure record would be written to a
transaction that is about to be discarded and would vanish along with
everything else the rollback undoes, defeating the entire purpose of
recording that the run failed. This mirrors the idempotency-key claim/state
pattern from ADR 0003 in shape — a durable record of an operation's outcome
that must survive independently of the operation's own transaction — reused
here for an unrelated problem (job observability, not request replay)
because the same durability need recurred, not because the two features are
otherwise related.

`CountryCurrencySyncRunJpaEntity`'s three factory methods (`running`,
`succeeded`, `failed`) each carry `lastSuccessAt` and `lastFailureAt` forward
from the previous singleton row independently of each other:
`succeeded` sets `lastSuccessAt` to the current run but copies the previous
row's `lastFailureAt` unchanged, and `failed` does the mirror image. A
failing run must never erase when the job last actually succeeded — an
operator asking "is this job still healthy" needs both facts at once — and a
successful run must never erase the record that it had previously failed,
which is itself useful history. `CountryCurrencySyncMetrics` exposes both
timestamps as gauges rather than counters for the same reason: an operator
needs *when*, not how many times.

### Disabling the scheduler removes the bean, not just its work

`CountryCurrencyUpdaterService` is gated by `@ConditionalOnProperty(prefix =
"app.country-currency-sync", name = "enabled", havingValue = "true",
matchIfMissing = true)` on the class itself, not by an early-return guard
inside `synchronizeCountryCurrencies`. Setting `app.country-currency-sync.enabled:
false` prevents the bean, and therefore its `@Scheduled` registration, from
existing at all — Spring's scheduler never has a trigger to fire in the first
place.

This distinction is the entire point: `CountryCurrencySyncProperties`'s own
Javadoc records that the job was previously a hardcoded `fixedRate`/
`initialDelay` pair with no way to disable it, meaning any test suite that
boots the real application context (issue #16/#17's `AbstractPostgresApplicationIT`-based
integration tests) unavoidably ran a real background job making a real
network call to the fiscal provider, on a timer, throughout the test run. An
early-return guard would still construct the bean and still register the
trigger; only removing the bean removes the side effect. Tests that boot the
full context now set the property to `false` and get a scheduler with
nothing registered on it, closing a real, previously-unavoidable noisy
side effect the codebase had been carrying since those earlier issues' own
test suites went in.

## Enforcement

- `ExchangeRateUpsertRepositoryIT` and the (analogous) `CountryCurrencyUpsertRepository`
  tests exercise the `ON CONFLICT` path against a real PostgreSQL via
  Testcontainers, including twenty concurrent upserts for the same key
  asserting exactly one row results with no integrity errors — a mock would
  only prove the method was called.
- `CountryCurrencySyncLockIT` exercises `pg_try_advisory_xact_lock` across two
  genuinely separate transactions (and, in the concurrent case, threads) to
  prove the lock actually serializes replicas rather than trusting the SQL
  function's documented behaviour on faith.
- `CountryCurrencyUpdaterServiceTest` covers the three outcomes at the unit
  level with a mocked lock, repository, and tracker: the lock won (fetch runs,
  success recorded), the lock lost (nothing else is touched — no fetch, no
  upsert, no tracker call), and a fetch failure (the failure is recorded and
  the exception still propagates, and nothing is upserted).
- `spring.jpa.hibernate.ddl-auto=validate` fails startup if
  `CountryCurrencySyncRunJpaEntity`'s columns drift from
  `V4__country_currency_sync_run.sql`, the same guarantee ADR 0001's other
  persistence adapters already rely on.
- `app.country-currency-sync.enabled` defaults to `true` in
  `application.yml` and is flipped to `false` for `AbstractPostgresApplicationIT`-based
  test configuration, so a missing override fails loudly (a real scheduled
  network call firing during a test run) rather than silently.

## Consequences

**Accepted costs.**

- One pooled database connection is held idle for the duration of the
  upstream fetch on whichever replica wins the sync lock, once per scheduled
  tick — bounded by the fiscal client's own total deadline, but not zero.
- A losing replica's scheduled tick does nothing observable: no fetch, no
  upsert, no failure record, only a debug log line. This is intentional (see
  Decision), but it means the run-status row only ever reflects the replica
  that won the lock for that tick, not "did every replica's scheduler fire."
- `CountryCurrencySyncRunTracker` adds three `REQUIRES_NEW` transactions per
  sync attempt (running, then success or failure) on top of the job's own
  transaction — more round trips than a single combined write, in exchange
  for the failure record surviving a rollback.

**What this buys.**

- Exchange-rate refresh and the country-currency sync both converge correctly
  under arbitrary concurrent writers, including two replicas writing the same
  key at the same instant, with no exists-check, no in-batch deduplication,
  and no locking anywhere in the refresh path.
- The scheduled sync produces exactly one upstream call per tick regardless
  of replica count, satisfying this issue's acceptance criterion directly,
  and survives a replica crashing mid-run without leaving the lock stuck.
- A failed run is durably recorded and does not erase the last known good
  run, giving an operator both facts (`last.success`, `last.failure`) at
  once via `CountryCurrencySyncMetrics`'s gauges.
- Test suites that boot the full application context no longer make a real,
  unbounded background network call on a timer, because the scheduler bean
  itself does not exist when the property is off.

**Rejected alternatives.**

- *Narrowing the advisory lock to just the final upsert, releasing it before
  the upstream fetch.* Would shorten the held-connection window, but every
  replica would then reach the fetch independently and call the upstream
  provider concurrently, only serializing at the write — which is precisely
  the duplicate-call outcome this issue exists to prevent. Rejected because
  it optimizes the wrong resource: a held connection is bounded and cheap;
  a duplicated upstream call is the actual thing to avoid.
- *A distributed lock via Redis/Redisson.* Would provide the same mutual
  exclusion, at the cost of a new infrastructure dependency, its own failure
  and expiry semantics, and a second system to keep consistent with Postgres
  — the same objection ADR 0003 raised against a Redis-based lock for
  purchase idempotency. Postgres is already the system of record every
  replica talks to and already offers exactly this primitive.
- *Resilience4j's `RateLimiter` or `Bulkhead`, repurposed as a cross-replica
  mutex.* Both are in-process concurrency primitives with no cross-replica
  awareness at all — they would only limit or bound calls within a single
  JVM, doing nothing to stop two different replicas from both proceeding.
  The wrong tool for a problem that is fundamentally about coordination
  between processes, not within one.
- *ShedLock or a similar scheduler-locking library.* Solves the same problem
  this issue's two-line native query (`SELECT pg_try_advisory_xact_lock(:key)`)
  already solves, at the cost of an entire new dependency, its own lock-table
  schema and configuration, and another API surface to learn — for
  functionality Postgres already exposes as a built-in function.
