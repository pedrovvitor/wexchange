# ADR 0003 — Purchase-creation idempotency

- **Status:** Accepted
- **Date:** 2026-09-04
- **Issue:** #18
- **Supersedes:** None
- **Superseded by:** None

## Context

`POST /v1/purchases` had no way to make a retry safe. A client that times out
waiting for a response - or whose response is lost on the way back - cannot
tell whether the purchase was created, and its only recourse was to retry and
risk a duplicate purchase record, or not retry and risk having none. Wexchange
runs multiple replicas behind the same PostgreSQL instance (ADR 0002), so any
answer has to hold under concurrent requests landing on different application
instances, not just concurrent threads in one process.

The purchase-creation flow already does one thing conditionally: it only
refreshes the exchange-rate cache once per purchase date
(`CreatePurchaseService.createNew`, `countByPurchaseDate(...) <= 1`). Idempotent
replay had to compose with that rule rather than bypass it - a replayed
request must never re-trigger the refresh, because from the caller's
perspective nothing new happened.

## Decision

Make the `Idempotency-Key` request header optional. When absent, `execute`
takes the exact code path it always has (`createNew`) - old behaviour is
preserved byte-for-byte for every caller that does not opt in. When present,
the key gates a claim/poll/replay state machine backed by a dedicated
`idempotency_key` table (`V2__idempotency_key.sql`), fronted by the
`PurchaseIdempotencyStore` port and the `IdempotencyKeyRepository` /
`PurchaseIdempotencyStoreAdapter` pair that implement it.

### The state machine

Each key owns one row, in one of three states (`IdempotencyStatus`):

- **`IN_PROGRESS`** - the state a fresh claim starts in.
- **`COMPLETED`** - the request that owned the claim finished; `resource_id`
  holds the created purchase's id.
- **`FAILED`** - the request that owned the claim threw; the row is left
  behind rather than deleted, specifically so a later request with the same
  key can retry instead of replaying a failure forever.

`CreatePurchaseService.execute` calls `claim(key, fingerprint, now, expiresAt)`
first. The winner (`createAsOwner`) runs the real purchase-creation logic -
including the once-per-date rate refresh - and then transitions its own row to
`COMPLETED` or `FAILED`. Every other caller for that key, on any replica,
enters `joinExistingClaim` and polls `find(key)`:

- **`COMPLETED`** -> look up `purchases.findById(record.resourceId())` and
  return that purchase. This is the replay path: no purchase-creation logic
  runs a second time, so the rate-refresh guard is never even reached.
- **`FAILED`**, or **`IN_PROGRESS`** past its `expiresAt` (the owning request
  crashed or was abandoned before reaching `COMPLETED`/`FAILED`) -> reclaimable.
  The poller calls `reclaim`, and the one caller whose `UPDATE` actually
  matches a still-reclaimable row becomes the new owner and runs
  `createAsOwner` itself.
- **`IN_PROGRESS`** and not yet expired -> keep polling.
- A record whose `fingerprint` does not match the current request's is an
  immediate **conflict** (`IdempotencyKeyConflictException`, mapped to `409` in
  `GlobalExceptionHandler`), regardless of status.

Polling is bounded by `app.idempotency.max-wait` (default `5s`), checked at
`app.idempotency.poll-interval` (default `50ms`). A poller that reaches the
deadline still `IN_PROGRESS` throws `RetryableException`, mapped to `503` -
the client is told to retry rather than being blocked or guessing.

**Why bounded polling, not the alternatives.** Blocking indefinitely ties up a
request thread for as long as the owning replica takes, with no way for the
caller to bound its own wait - a slow or wedged owner would cascade into every
follower. An async, `202`-plus-poll-endpoint flow would solve the same problem
but changes the public contract for every caller, including the ones that
never send an `Idempotency-Key` at all, for a case (two requests for the same
purchase arriving within milliseconds of each other) that is expected to be
rare and short-lived. Bounded synchronous polling keeps the contract
unchanged for opted-in callers, keeps the common case fast (most polls resolve
within one or two intervals), and gives the caller a `503` it already knows
how to retry against - the failure mode this API uses everywhere else for
"try again shortly" (see the fiscal-provider `503` in ADR 0002's abuse
cases).

### The database unique constraint is the final concurrency defense

`claim` and `reclaim` are each application-level compare-and-set operations,
but neither is trustworthy on its own under concurrent replicas: a
`find`-then-write from two different JVMs can both observe "no row" or both
observe "reclaimable" before either writes. The primary key on
`idempotency_key.idempotency_key` is what actually arbitrates that race -
exactly one `INSERT` (or one `UPDATE` matching `reclaim`'s `WHERE` clause) can
ever succeed for a given key, no matter how many replicas attempt it at the
same instant. An in-memory map or a coordinated lock (see rejected
alternatives) would each need to reintroduce their own consistency mechanism
across replicas; the constraint gives it for free because Postgres is already
the single system of record every replica talks to.

**Why `repository.save()` cannot be used to detect the race.**
`IdempotencyKeyJpaEntity` uses an application-assigned id (the idempotency key
itself) with no `@Version` field - the same shape `PurchaseJpaEntity` has, and
for the same reason (`PurchaseStoreAdapter`'s own documentation). Spring Data
decides whether an entity is "new" by inspecting its id, and an
application-assigned id is never null, so Spring Data treats every `save()` as
an update candidate and issues a `merge`, not an `insert`. Two concurrent
`save()` calls for the same key would silently merge into the same row instead
of one of them hitting a constraint violation - the exact detection this
method exists to provide would never fire.

`PurchaseIdempotencyStoreAdapter.claim` instead calls
`entityManager.persist(...)` followed by an explicit `flush()`, which forces a
real `INSERT` immediately rather than deferring it to end-of-transaction. That
call runs inside its own `REQUIRES_NEW` transaction so a losing claim's
constraint violation cannot poison a caller's outer transaction. Catching the
resulting `PersistenceException`, marking that inner transaction
rollback-only, and returning `false` keeps the JPA/persistence exception type
from ever crossing the `PurchaseIdempotencyStore` port - the caller only ever
sees a boolean.

### The fingerprint

`fingerprint(description, purchaseDate, amount)` is a SHA-256 hash over the
canonical, already-validated fields - `description.length() + ":" +
description`, the `LocalDate`, and `new Money(amount)`'s canonical string -
not over the raw JSON request body. Hashing the validated `Money` value rather
than the input `BigDecimal` means `10.00`, `10.0`, and `10` all fingerprint
identically, matching how the domain already treats them as the same amount.
Hashing raw bytes would instead fingerprint whitespace, key ordering, and
numeric formatting - properties of the transport encoding that have no
business meaning and that a client's JSON serializer is free to change between
otherwise-identical retries, which would turn a legitimate replay into a false
conflict.

### Replaying an id, not a stored response

`markCompleted` stores only `resourceId` - the created purchase's id - not a
serialized copy of the HTTP response. `PurchaseApiOutput` is a pure,
deterministic function of the `Purchase` domain object and the request path
(`PurchaseApiOutput.with(purchase, links)`, called by `PurchaseController`
identically whether `CreatePurchaseService.execute` returned a freshly created
or a replayed `Purchase` - the controller cannot tell the two apart, and does
not need to), so refetching the `Purchase` by id and re-running that mapping
produces the same response the original request would have produced. Storing
a response body would duplicate data already recoverable from the system of
record and would need its own versioning story the moment `PurchaseApiOutput`'s
shape changes.

### The header contract

`Idempotency-Key` (`PurchaseApi.IDEMPOTENCY_KEY_PATTERN`,
`^[A-Za-z0-9_-]{1,255}$`) is declared `required = false`. A missing header
takes the pre-existing `createNew` path with no idempotency bookkeeping at
all - opting in is a client decision, not a mandatory one. A malformed key
(wrong characters or length) fails Bean Validation before reaching the service
and is a `400`, not a `409` or `503`. `PurchaseApi`'s `@ApiResponses` documents
the full contract: `201` for both a genuine creation and a replay, `409` when
the same key is reused with a different body, and `503` when a concurrent
request with the same key is still in flight past `max-wait`.

### Retention as configurable policy

`IdempotencyProperties` (`app.idempotency.retention`, `max-wait`,
`poll-interval`; defaults `24h`, `5s`, `50ms`) makes how long a claim is
honored an operational knob, not a constant buried in the service. `retention`
sets each claim's `expiresAt` at creation time; it does not schedule deletion.
An expired row is not removed - it becomes *eligible for reclaim* the next
time a request with that key arrives (`IdempotencyKeyRepository.reclaim`'s
`WHERE ... OR e.expires_at < :now`) and is otherwise inert. **No cleanup job
exists yet.** The table carries `idx_idempotency_key_expires_at` explicitly to
support one (per the migration's own comment), but nothing schedules a delete
against it in this change. This is a known limitation, not an oversight this
ADR is hiding: left unaddressed, the table grows unbounded with one row per
distinct key ever used. A follow-up issue should own a periodic
delete-where-expired job before idempotency-key volume becomes large enough
for that to matter operationally.

## Enforcement

- `IdempotencyKeyJpaEntity`'s columns are pinned to `V2__idempotency_key.sql`
  exactly, and `spring.jpa.hibernate.ddl-auto=validate` fails startup on any
  drift between them, the same guarantee ADR 0001's persistence adapters rely
  on elsewhere.
- The claim race, the reclaim race, the conflict path, and the expiry path are
  each behaviour a test can force independently: `claim` returning `false` for
  a duplicate key, `reclaim` returning `0` rows affected for a
  non-reclaimable row, and `joinExistingClaim`'s fingerprint check are each one
  method call, not a timing-dependent scenario to reproduce end-to-end.
- `PurchaseIdempotencyStoreAdapter` is the only class permitted to touch
  `EntityManager` directly for this concern; `IdempotencyKeyRepository`'s
  `@Modifying` queries carry the reclaim precondition in the query itself
  rather than in application code, so the precondition cannot be bypassed by
  a future caller of the repository.

## Consequences

**Accepted costs.**

- A second table and a second persistence adapter exist solely to make one
  endpoint idempotent; the state machine (three statuses, two race-prone
  transitions) is real complexity for a feature most callers will never
  invoke.
- The losing side of a race pays with polling latency (up to `max-wait`)
  instead of an immediate answer.
- No cleanup job ships in this change - the table's growth is currently
  unbounded until a follow-up adds one.

**What this buys.**

- A client can safely retry `POST /v1/purchases` after any ambiguous failure
  (timeout, dropped connection, 5xx) without risking a duplicate purchase,
  across any number of replicas, using only a header it already controls.
- The guarantee holds after a process crash: an owner that dies mid-request
  leaves its row `IN_PROGRESS` until `expiresAt`, at which point another
  request reclaims it rather than being blocked forever.
- Callers that never send the header pay nothing - no extra table read, no
  behaviour change.

**Rejected alternatives.**

- *An in-memory idempotency cache (e.g. a `ConcurrentHashMap` keyed by
  idempotency key).* Fails outright once there is more than one replica: two
  requests for the same key landing on different instances would each see an
  empty cache and both create a purchase. It also loses every in-flight claim
  on a restart, which is exactly the crash scenario this feature has to
  survive.
- *Storing the full serialized HTTP response instead of just the resource
  id.* Rejected because `PurchaseApiOutput` is already a deterministic
  function of the `Purchase` domain object plus the request path - persisting
  the id and re-deriving the response on replay avoids storing derived data a
  second time and avoids a second place that has to change whenever
  `PurchaseApiOutput`'s shape does.
- *A distributed lock service (e.g. Redis-based `SETNX`).* Would provide the
  same mutual-exclusion guarantee `claim` needs, but at the cost of a new
  infrastructure dependency, its own failure and expiry semantics to reason
  about, and a second system of record to keep consistent with Postgres.
  Postgres is already the system of record for purchases and already
  guarantees exactly this kind of uniqueness through a primary key; adding
  Redis would buy nothing this table does not already provide.
