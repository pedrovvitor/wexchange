# Test taxonomy and quality gates

This document is the operational companion to
[`quality-foundation.md`](quality-foundation.md). That document states the
strategy; this one states exactly which Gradle task enforces it, what each
number is, and what to do when a gate fails.

Thresholds live in `build.gradle` and nowhere else. CI (issue #10) invokes these
tasks; it must never restate a threshold in workflow YAML, because two copies of
a number drift and the weaker one wins.

## Suites

| Suite | Source set | Gradle task | What belongs here |
| --- | --- | --- | --- |
| Unit | `src/test/java` | `unitTest` (alias for `test`) | One class or one rule, collaborators stubbed, no Spring context. |
| Integration | `src/integrationTest/java` | `integrationTest` | Several real components together: controller plus validation plus JSON plus error mapping; repository queries against an embedded H2 database via `@DataJpaTest`; later, PostgreSQL through Testcontainers (#5) and stubbed provider HTTP (#3). |
| Architecture | `src/architectureTest/java` | `architectureTest` | ArchUnit fitness functions over compiled production and test classes, plus the boundary fixtures below. |

`unitTest` is a lifecycle alias rather than a second `Test` task, so the suite is
never executed twice in one build. Use whichever name reads better; `check` runs
all three.

The architecture suite reads the other suites' compiled output, so it depends on
`testClasses` and `integrationTestClasses` even when those suites are not run.

### Naming

- Test classes mirror the class under test: `Money` → `MoneyTest`. Integration
  tests end in `IT`. Tests live in the same package as their subject, which is
  also what lets the boundary fixtures below sit inside `domain` and
  `application`.
- Test methods read `given<Situation>_when<Action>_then<ObservableOutcome>`.
- Prefer `@DisplayName` sentences on anything whose intent is not obvious from
  the method name. The failure line should tell a reviewer what broke without
  opening the file.
- Group related cases with `@Nested` rather than by prefixing method names.

### Fixtures

- Build fixtures with the production factory methods (`Purchase.create`,
  `Money.of`, `PurchaseJpaEntity.newPurchase`) so a change to construction rules
  surfaces here.
- Money and rates are written as exact `BigDecimal` string literals. Never
  `double`, and never `BigDecimal.valueOf` on a literal where the scale matters:
  a test that uses binary floating point can pass against a wrong implementation.
- Dates are fixed literals (`LocalDate.of(2024, 1, 31)`), never `LocalDate.now()`,
  and instants are `Instant.parse(...)`, never `Instant.now()`.
- Identity and time are injected, never ambient. A use case takes an
  `IdentifierGenerator` and a `Clock`; a test supplies a fixed identifier and
  `Clock.fixed(...)` and asserts the exact values that reach the repository.
- Never assert the text of a Bean Validation message. It is translated to the
  JVM's default locale, so the assertion passes in English and fails elsewhere.
  Assert the constraint annotation that fired instead - it is stable, and it is
  the stronger claim.
- Country-currency fixtures use synthetic values. No production data, ever.

### Repository-level tests

Some defects live in the generated SQL, not in application logic — a JPQL
subquery scoped wrong is invisible to a mocked repository. `@DataJpaTest`
against an embedded H2 database exercises the real query. Point it at
`Main` explicitly with `@ContextConfiguration(classes = Main.class)`:
`@DataJpaTest`'s configuration search only walks up ancestor packages, and
`Main` sits in a sibling package (`bootstrap`) to the adapters under test. Add
`@EntityScan`/`@EnableJpaRepositories` with `basePackageClasses` rather than
relying on package-based scanning to resolve correctly through that override.

Override `spring.datasource.*` explicitly via `@TestPropertySource` rather than
selecting a profile: the base `application.yml` references
`${DATABASE_POSTGRES_URL}`, which has no default and is unresolved outside a
real deployment. A higher-precedence property source for the same key is
resolved directly and never touches that placeholder.

`ExchangeRateRepositoryIT` (issue #2) is the first of these. It is H2-backed,
not PostgreSQL: Testcontainers and Flyway are issue #5's scope. This is a known,
tracked gap, not silently accepted — issue #2 asked for PostgreSQL integration
tests specifically because the defect was a SQL scoping bug, and H2 and
PostgreSQL do not guarantee identical JPQL-to-SQL translation or collation
behaviour. Re-run `ExchangeRateRepositoryIT`'s assertions against real
PostgreSQL once #5 lands.

### Boundary fixtures

`HexagonalBoundariesTest` checks each rule twice: once against production code,
where it must pass, and once against a class written to break it, where it must
fail. `InwardDependencyFixture` and `SpringInDomainFixture` live in the
architectureTest source set inside `application` and `domain` respectively, so
the rules can actually reach them.

They are not mistakes and must not be "fixed". A green build on those two tests
would mean a rule had quietly stopped matching anything — which is how a gate
ends up enforcing nothing while reporting success. Production classes are
imported by path from `build/classes/java/main`, so the fixtures can never leak
into the set the real assertions run against.

### Offline guarantee

`OfflineTestsArchitectureTest` checks the compiled unit and integration suites
for calls that open a connection (`URL.openConnection`, `URL.openStream`,
`URL.getContent`, `HttpClient.newHttpClient`, `new Socket(host, port)`) and fails
the build if it finds one. Outbound HTTP in production goes through an injected
`HttpClient`, which every suite stubs. Stub the provider; never call it.

## Gates

| Gate | Task | Threshold |
| --- | --- | --- |
| Formatting | `spotlessCheck` / `spotlessApply` | Import order, unused imports, indentation, trailing whitespace, final newline. Config: `config/spotless/wexchange.importorder`. |
| Static analysis | `pmdBaselineVerification` | No PMD violation outside `config/pmd/baseline.txt`. Rules: `config/pmd/ruleset.xml` (production), `config/pmd/ruleset-test.xml` (tests). |
| Coverage, behavioural core | `jacocoTestCoverageVerification` | 90% line, 85% branch, **per package**, for `domain`, `application`, and the adapters. |
| Coverage, whole artefact | `jacocoTestCoverageVerification` | 80% line, 70% branch across the production bundle. |
| Mutation | `mutationTest` | Mutation score ≥ 80%, test strength ≥ 90% over the behavioural core. |
| Critical financial rules | `pitestCriticalRulesVerification` | **Zero** surviving mutants in `Money`, `ConversionWindow`, `ExchangeRate`, `Purchase`, and `ConvertPurchaseService`. No exemption list exists, and the task also fails if PIT produced no mutant for those classes at all. |
| Everything | `check` | All of the above, plus all three suites. |

The behavioural-core rule is evaluated per package, not as one aggregate, so a
well-covered package cannot mask a neglected one.

### Coverage exclusions

Exclusions are nominal and enumerated in `build.gradle`. Package-wide exclusions
are rejected; if a whole package is hard to cover, that is a design signal.

| Excluded | Why |
| --- | --- |
| `com.pedrolima.wexchange.Main` | Spring Boot entry point. Its body is `SpringApplication.run`; covering it means starting a real container for no assertion. |

Lombok-generated members are excluded structurally rather than by pattern, via
`lombok.addLombokGeneratedAnnotation` in `lombok.config`. JaCoCo skips anything
annotated `@lombok.Generated`, so generated accessors neither inflate nor deflate
the numbers.

### Known debt, and why it is recorded rather than hidden

One gate carries a baseline of pre-existing findings. It is a ratchet: a new
violation fails the build, and a fixed violation must be removed from the
baseline in the same pull request that fixes it, or the build fails for being
out of date.

| Baseline | Contents | Owner |
| --- | --- | --- |
| `config/pmd/baseline.txt` | Five `PreserveStackTrace` violations: exceptions rethrown in a catch block without the original cause. | #3, #7 |

The ArchUnit violation store that used to sit in `config/archunit/frozen/` is
gone. It held two `@Value` field injections; issue #1 replaced them with
constructor injection, so the store emptied and was deleted in the same change.
That is what the ratchet is for, and what it looks like when it works.

Seventeen mutants survive, none in the critical financial rules:

- nine in the persistence entities' `equals`/`hashCode`. `ExchangeRateCompositeKey`
  is a JPA `@IdClass` with no setters, so a unit test cannot build two keys that
  differ; three of these are reported as `NO_COVERAGE` for that reason. Issue #5
  brings Testcontainers, which is where that identity contract can finally be
  exercised against a real Hibernate session;
- four in the two `getRetryCount()` helpers, whose value only ever reaches a log
  message;
- four `StopWatch::stop` removals. commons-lang3 reports elapsed time on a
  running watch, and the only consumer of `formatTime()` is a debug log, so
  nothing observable changes.

They are counted against the score rather than suppressed, and the suite clears
both thresholds with them included.

The critical financial rules are held to a stricter standard than a score: every
mutant PIT generates for them must die, with no exemption list. When a mutant
there proves unkillable, the answer is to restructure the code so it is never
generated, not to record it. `ConvertPurchaseService.describeAmbiguity`
is written as concatenation rather than `String.formatted` for exactly this
reason: a varargs call carries an array-length constant that `INLINE_CONSTS` can
widen without altering the message, and no assertion could distinguish that.

Never lower a threshold, delete a rule, or widen a baseline to make a change
pass. Fix the finding, or record it against the issue that owns the code.

## Commands

Everything below is local-first and offline.

```bash
./gradlew check
```

The complete gate. Roughly a minute from clean.

```bash
./gradlew unitTest architectureTest
```

The fast loop, under 30 seconds from clean. Run this while developing.

```bash
./gradlew jacocoTestReport
```

Coverage report at `build/reports/jacoco/test/html/index.html`, plus XML and CSV
for tooling.

```bash
./gradlew mutationTest
```

Mutation report at `build/reports/pitest/index.html`.

```bash
./gradlew spotlessApply
```

Fix formatting in place.

### Toolchain

The build declares a Java 17 toolchain, so Gradle provisions and selects its own
JDK regardless of `JAVA_HOME`. No `-Dorg.gradle.java.home` flag is needed, and
the same bytecode is produced on a developer machine and on a CI runner.

PIT forks its own JVM, so `tasks.named('pitest')` is given the same toolchain
launcher explicitly. Without that it inherits the Gradle daemon's JDK and fails
with `Unsupported class file major version` whenever the daemon is newer than
the target.

### Locale and time

Nothing in the suite depends on the host's locale, default time zone, or wall
clock. Validation tests assert the constraint that fired rather than its
message, the application clock is `Clock.systemUTC()` injected as a bean, and
identifiers come from an injected `IdentifierGenerator`.

Verify determinism after touching anything in that area:

```bash
./gradlew clean test -Duser.language=pt -Duser.country=BR
./gradlew clean test -Duser.language=en -Duser.country=US
```

## Verifying that the gates still bite

A gate nobody has seen fail is a gate nobody should trust. PMD in particular
loads zero rules and reports success if a rule name is misspelled, which is how
a static-analysis task can pass while checking nothing.

Confirm each gate rejects a deliberate violation before relying on it, and
re-confirm after changing tool versions or configuration:

Each probe below was executed against this configuration and observed to fail.

| Gate | Introduce | Observed |
| --- | --- | --- |
| Formatting | Trailing whitespace on any Java line | `spotlessJavaCheck FAILED` |
| Static analysis | A new class that rethrows inside a `catch` without the cause | `pmdBaselineVerification` fails: `NEW PreserveStackTrace\|…\|(1 found, 0 allowed)` |
| Coverage | A new class in `utils` with untested branches | `Rule violated for package com.pedrolima.wexchange.utils: lines covered ratio is 0.83, but expected minimum is 0.90` |
| Mutation thresholds | Raise `testStrengthThreshold` to 99 | `Test strength score of 91 is below threshold of 99` |
| Critical rules | Weaken the `MultipleCountryCurrenciesException` message assertion in `ConvertPurchaseUseCaseTest` | `1 mutant(s) not killed in the critical financial rules: … describeAmbiguity … replaced return value with ""` |
| Offline | `new URL("https://example.test/rates").openStream()` in a test | `OfflineTestsArchitectureTest > no test opens a network connection FAILED` |

Two probes that look obvious do **not** fail, and are worth knowing about:

- Deleting `ConversionUtilsTest` does not breach the coverage gate. `ConversionUtils`
  is still exercised through `ConvertPurchaseUseCaseTest`. Coverage measures
  execution, not intent; that is what the mutation gate is for.
- Weakening the conversion-window assertions in `ConversionUtilsTest` does not
  breach the mutation gate either, because the window is independently pinned by
  `ConvertPurchaseUseCaseTest` (the captured date range) and by
  `ExchangeRateServiceTest` (the `effective_date:gte:` term in the outbound URI).
  Redundant coverage of a money rule is a feature, not a smell.
