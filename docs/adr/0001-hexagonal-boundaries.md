# ADR 0001 — Hexagonal boundaries and incremental migration

- **Status:** Accepted
- **Date:** 2026-09-01
- **Issue:** #15
- **Supersedes:** nothing
- **Superseded by:** nothing

## Context

The repository already has packages called `usecases`, `entities`, `repositories`,
and `services`, which look like layers but do not behave as ones. Dependency
direction runs in every direction at once:

- `DefaultConvertPurchaseUseCase` imports `ExchangeRateJpaEntity`,
  `PurchaseRepository`, `ExchangeRateRepository`, `ExchangeRateService`,
  `MetricsHelper`, the HTTP DTOs in `purchase.models`, `ApiLink`, and
  `org.springframework.stereotype.Service`.
- `PurchaseJpaEntity` — a JPA `@Entity` — carries the rounding rule for money.
- `ConversionUtils`, which owns the two financial invariants of the product,
  takes a `PurchaseJpaEntity` as its parameter, so the conversion window cannot
  be evaluated without the persistence model.
- `services.async.ExchangeRateService` is an outbound HTTP client that a use case
  calls directly, and which also writes to a repository.

The practical consequence is that the domain model *is* the persistence model.
Changing a column changes the money rules; testing the money rules requires
constructing a JPA entity. Issues #2 through #7 all want to change behaviour in
one of these areas, and each would currently have to reach through the others.

Issue #14 added coverage, mutation, and architecture gates, so there is now a way
to prove a refactor preserves behaviour. Issue #1 made the runtime and the tests
deterministic, and introduced the first two application-owned abstractions
(`Clock` and `IdentifierGenerator`). Those were the prerequisites.

## Decision

Adopt ports and adapters with six areas and one rule: **dependencies point
inward, and nothing points outward.**

| Package | Holds | May depend on |
| --- | --- | --- |
| `domain` | Money, exchange rates, the conversion window, purchase invariants, domain errors. Plain Java. | The JDK. Nothing else. |
| `application` | Two use-case interfaces, two services implementing them, and four outbound ports under `application.port`. | `domain`, the JDK. |
| `adapter.in.web` | Controllers, HTTP DTOs, request validation, RFC 9457 error mapping, HATEOAS links. | `application`, `domain`, Spring Web, Jackson. |
| `adapter.out.persistence` | JPA entities, Spring Data repositories, domain↔entity mappers. | `application.port.out`, `domain`, Spring Data, JPA. |
| `adapter.out.fiscal` | The Fiscal Data HTTP client, its wire beans, URL building, JSON parsing. | `application.port.out`, `domain`, `java.net.http`, Jackson. |
| `bootstrap` | `Main`, Spring configuration, bean wiring, scheduling. | Everything. Nothing depends on it. |

Two adapters never see each other. `adapter.out.persistence` and
`adapter.out.fiscal` communicate only through the application.

### How small this deliberately is

The whole structure is five domain records, two application services, and four
one-method ports. There are no command objects, no result objects beyond the one
a use case actually returns, no mapper classes, and no generic `UseCase<IN, OUT>`
base. Mapping between a domain type and its row is two methods on the entity that
already exists.

Every abstraction here carries a dependency across a boundary. Anything that
would only have forwarded a call was left out, and the pre-existing generic
`UseCase` base class was deleted for exactly that reason. A small application
does not earn a large diagram, and the last acceptance criterion of issue #15
says so in as many words.

### What goes in the domain

These four rules move into it, because they are the things the product is
actually about:

- **`Money`** — a `BigDecimal` at scale 2, `HALF_EVEN`. Every construction and
  every arithmetic result is normalised, so a wrong scale cannot exist as a
  value rather than being caught at an assertion.
- **`ExchangeRate`** — a country-currency, an effective date, and a rate value.
  Knows how to convert a `Money`.
- **`ConversionWindow`** — the six months preceding a purchase date, inclusive.
  Knows whether a rate is eligible.
- **`Purchase`** — identifier, description, date, amount, timestamps, and the
  invariants over them.

All are records. Generated equality and accessors are correct here and cost
nothing to read; the description-length limit stayed in Bean Validation at the
web boundary rather than being copied into the domain, because two copies of the
number would be free to drift apart.

These are the classes issue #14 named as critical financial rules, and they keep
that status: mutation testing must leave no survivor in them.

### The country-currency slice

The catalogue read path has no domain rules: it lists rows, pages them, and
attaches links. It was moved into the adapter packages along with everything
else, but no domain model, port, or use case was invented for it. Its read
service sits in `adapter.in.web`, where the pagination and link assembly
actually belong, and reads through the persistence adapter.

That single web-to-persistence edge is the one place two adapters meet. It is
allowed by name in the layered rule rather than being hidden, and it buys back a
paging port, a read model, and a mapper that would exist only to satisfy a
diagram. Issue #6 owns the scheduled sync and can revisit it with a reason.

The refresh path keeps its `@Async` and `@Retryable` exactly where they were,
behind the one-method `ExchangeRateRefresher` port. Splitting fetch from store
into an application-level orchestration would have moved the proxy boundary and
changed what a retry wraps, for no gain this issue needs.

## Enforcement

Documentation does not hold a boundary. `architectureTest` gains:

- a layered-architecture rule for the table above, so an inward dependency on an
  adapter fails the build;
- a rule that `domain` and `application` import nothing from Spring, JPA,
  Jackson, `java.net.http`, or any `*JpaEntity`;
- slice-based cycle detection across the top-level packages;
- a fixture package containing a deliberate violation, asserted to be rejected,
  so the rules are proven to bite rather than assumed to.

Mapping between the domain and the persistence model is covered in both
directions, because a silent mapping bug is exactly the failure this structure
makes possible.

## Consequences

**Accepted costs.**

- A domain record and a JPA entity now exist for the same concept, with two
  mapping methods between them. That is duplication, and it is the price of being
  able to change the schema without changing the money rules.
- The diff is large and touches almost every file. It is behaviour-preserving,
  and the characterization suite from #14 is what makes that claim checkable
  rather than hopeful.
- `Money` wrapping `BigDecimal` adds a layer to every monetary expression.

**What this buys.**

- #2 (ambiguous country-currency) and #4 (false 404s) become changes to
  application logic, not to controllers and repositories at once.
- #3 can replace the Fiscal Data client wholesale behind `FiscalRateProviderPort`
  without any use case noticing.
- #5 can introduce Flyway and Testcontainers inside the persistence adapter.
- #7 can restate the HTTP contract in the web adapter alone.

**Rejected alternatives.**

- *Gradle modules per layer.* Stronger enforcement, but it turns every future
  issue into a build-file change too. ArchUnit gives most of the benefit at a
  fraction of the friction. Revisit if the codebase outgrows one module.
- *Big-bang rewrite of all slices at once.* Larger blast radius, no intermediate
  green state, and it would mix the catalogue slice's behavioural questions into
  a structural change.
- *Leaving the JPA entity as the domain model and only adding ports.* Cheaper,
  but it preserves the actual defect: the money rules would still live in an
  `@Entity` and still be untestable without persistence types.
