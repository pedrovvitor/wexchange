# Wexchange Engineering Contract

## Mission and product boundary

Wexchange is a production-like portfolio application for currency quotation and
conversion workflows. Treat every change as software intended to be deployed,
operated, reviewed, and evolved by a team.

The public quotation experience is anonymous and must use synthetic or otherwise
non-sensitive data. Do not add authentication merely to protect public quotation
flows. Introduce identity only when a feature owns private user data or privileged
operations, and document that decision.

Before implementing an issue, read:

- The complete GitHub issue, including dependencies and acceptance criteria.
- `docs/engineering/quality-foundation.md`.
- `docs/engineering/test-taxonomy.md` for suites, naming, fixtures, thresholds,
  exclusions, recorded debt, and the exact commands to run.
- Any ADR, API contract, or documentation linked by the issue.

## Scope and delivery discipline

- Work on one issue per branch and pull request unless the issue explicitly says
  otherwise.
- Do not make opportunistic product changes outside the issue scope.
- If the working tree already contains unrelated changes, preserve them and stop
  if safe isolation is impossible.
- Never merge a pull request, publish a release, deploy, or change remote
  infrastructure unless explicitly requested.
- Do not claim success without showing the commands run and their results.

## Architecture rules

- Keep domain rules in plain Java, independent of Spring, HTTP, persistence,
  serialization, and vendor SDKs.
- Application use cases orchestrate domain behavior through explicit ports.
- Inbound and outbound adapters own controllers, external APIs, persistence,
  messaging, and framework-specific models.
- Do not leak JPA entities, HTTP DTOs, Jackson annotations, or upstream provider
  payloads into the domain.
- Dependency direction points inward. Add or maintain ArchUnit tests to enforce
  boundaries rather than relying only on documentation.
- Prefer small cohesive changes and explicit names over speculative abstractions.

## Correctness and resilience

- Use `BigDecimal` with explicit scale and rounding for money and exchange rates;
  never use binary floating point for monetary calculations.
- Use UTC internally and inject `Clock` into time-dependent behavior.
- Validate all external input at the boundary and validate upstream responses
  before mapping them into trusted application types.
- Configure explicit connection and read timeouts for remote calls.
- Apply retries only to transient, idempotent operations, with bounded exponential
  backoff and jitter. Prevent retry amplification.
- Make transaction, concurrency, caching, and idempotency behavior explicit where
  the use case needs them.
- Return safe, consistent API errors based on RFC 9457 Problem Details. Never
  expose stack traces, secrets, or provider internals to clients.

## Security and privacy

- Never commit credentials, tokens, production data, or personal data.
- Keep configuration externalized and provide safe example values only.
- Enforce authorization in the backend when privileged features exist.
- Use restrictive CORS and security headers appropriate to the deployed clients.
- Add rate limiting at the correct trust boundary for abuse-prone public routes;
  document keying, limits, proxy behavior, and failure mode.
- Treat logs, metrics, traces, fixtures, screenshots, and error payloads as
  potential data-exfiltration surfaces.

## Tests and quality gates

- Tests must assert observable behavior and important failure paths, not framework
  implementation details.
- Unit-test domain and application rules. Integration-test PostgreSQL behavior
  with Testcontainers. Stub external HTTP providers; automated tests must not
  depend on live internet services.
- Maintain contract and architecture tests at system boundaries.
- Protect critical browser journeys with a small, stable E2E suite once a frontend
  exists.
- Target at least 90% line and 85% branch coverage for domain/application code,
  and 80% line and 70% branch coverage globally. Coverage is a guardrail, not a
  substitute for meaningful assertions.
- Use mutation testing for critical financial/domain rules, targeting at least
  80% mutation score and 90% test strength.
- Never weaken, skip, or delete a quality gate merely to make a change pass.

## Observability and operations

- Emit structured, useful, low-cardinality telemetry. Propagate correlation and
  W3C trace context where applicable.
- Do not put raw personal data, secrets, or unbounded values in telemetry.
- Maintain distinct liveness and readiness checks. Readiness must reflect required
  dependencies without turning transient optional failures into unnecessary
  outages.
- Document operationally relevant configuration, failure modes, and rollback.

## Required workflow

1. Inspect the issue, repository state, and affected code before proposing changes.
2. State the scope, assumptions, risks, and verification plan.
3. Establish a passing baseline or record pre-existing failures precisely.
4. Implement the smallest coherent solution, including tests and documentation.
5. Run the narrowest relevant checks first, then the complete available quality
   gate before declaring completion.
6. Review the diff for scope, security, compatibility, and accidental artifacts.
7. Create a pull request that links and closes the issue only after its acceptance
   criteria are satisfied. Do not merge it.

Use the repository's Gradle wrapper. `./gradlew clean check` is the top-level
gate: formatting, static analysis, the unit, integration, and architecture
suites, coverage verification, and mutation testing. Every threshold is owned by
`build.gradle`; CI consumes these tasks and must never restate a number.

Two environment constraints apply until issue #1 lands. The build declares no
Java toolchain, so run it on a JDK 17 (`-Dorg.gradle.java.home=...`); Lombok's
annotation processor fails on much newer JDKs. Six pre-existing tests assert
English Bean Validation messages, so run with `-Duser.language=en -Duser.country=US`
on a non-English machine, and never assert validation message text in new tests.

`config/archunit/frozen/` and `config/pmd/baseline.txt` record pre-existing debt.
They are ratchets: adding an entry to make a change pass is prohibited, and
fixing a violation means deleting its entry in the same pull request. Never
invent a task or imply that a planned gate already exists.

## Completion report

Report:

- What changed and why.
- Files and architectural boundaries affected.
- Tests and checks run, with outcomes.
- Acceptance criteria satisfied.
- Remaining risks, follow-ups, or pre-existing failures.
- Branch and pull-request URL when one was created.

Project skills available to Claude Code:

- `/execute-issue <number>`: implement one issue and prepare its pull request.
- `/review-pr <number>`: perform a read-only, evidence-based pull-request review.
- `/prepare-release <version>`: prepare and verify a release without publishing it.
