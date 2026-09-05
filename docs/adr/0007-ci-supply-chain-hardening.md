# ADR 0007 — Modernizing CI and adding supply-chain quality gates

- **Status:** Accepted
- **Date:** 2026-09-05
- **Issue:** #10
- **Supersedes:** None
- **Superseded by:** None

## Context

`.github/workflows/gradle_ci.yml` predated every quality gate this codebase
now has. It pinned actions to a moving major-version tag rather than an
immutable revision, declared no explicit permissions, concurrency policy, or
job timeouts, and ran `./gradlew build` (which already runs `check`, which
already runs `test`) followed immediately by a second, redundant
`./gradlew test`. There was no dependency-update policy, no SBOM, no
container image ever built or scanned in CI, no secret scanning, and no
validation that the committed Gradle wrapper itself had not been tampered
with.

Every gate this issue's acceptance criteria ask for already exists as a
Gradle task (`docs/engineering/test-taxonomy.md`) except the
supply-chain-specific ones (SBOM, container scan, secret scan, dependency
review, wrapper validation) - none of which have anything to do with Gradle
and all of which are naturally GitHub Actions' job to run.

## Decision

### One canonical `check` invocation, not `build` then `test`

The `build` job now runs `./gradlew check` exactly once. `build` (the Gradle
Java plugin's own task) already depends on `check`, so the previous
`./gradlew build` followed by `./gradlew test` executed the entire suite
twice per pipeline run for no reason anyone had documented. `check` is also
what `test-taxonomy.md` already establishes as the one command that runs
every gate this repository owns; restating any of its constituent tasks in
workflow YAML would risk the exact drift `test-taxonomy.md` warns about
("thresholds live in `build.gradle` and nowhere else").

### Actions pinned to a commit SHA, resolved and verified, not guessed

Every third-party action reference is `owner/repo@<40-char-sha> # <tag>`,
resolved via the GitHub API against each action's actual latest release
before being written down (`gh api repos/<owner>/<repo>/releases/latest` for
the tag, then `gh api .../git/refs/tags/<tag>` and, where the tag is
annotated, one further `git/tags/<sha>` hop to reach the underlying commit).
A version tag can be repointed after the fact by whoever controls that
repository; a commit SHA cannot. This is the literal meaning of "supported
actions pinned to immutable revisions" in the issue's own scope, not a
convention adopted loosely - and pinning to a SHA obtained by guesswork
(copying one from memory or another project) would have reintroduced the
exact risk this line item exists to close, just at review time instead of
runtime.

### Permissions and concurrency: read-only by default, overridden per-job only where needed

The workflow declares `permissions: contents: read` once, at the top level.
Only `dependency-review` elevates its own job to add `pull-requests: write`,
the one thing it needs to post its summary comment. No other job's
`GITHUB_TOKEN` can write anything. `concurrency` cancels an in-flight run for
the same branch/PR the moment a newer commit lands, since a runner proving
an already-superseded commit is wasted work regardless of whether it
finishes.

### The container job builds and runs the real image, not a stand-in

`container` builds the actual `Dockerfile`, starts it with
`--network host` against a real `postgres:16-alpine` GitHub Actions
`services:` container, waits for `/actuator/health` to report healthy, and
calls one real route (`GET /v1/country_currencies`) end to end - Flyway
migration, Hikari connection, JPA/Hibernate, and the actual JSON response, in
that order. `--network host` is the mechanism, deliberately: GitHub's
`ubuntu-22.04` runners execute jobs directly on the runner's own Linux host
(not inside a container), so a `services:` container's published ports and a
plain `docker run` both land in the same network namespace, and
`--network host` on the app container is what lets it reach `localhost:5432`
without a purpose-built Docker network only existing for this one job. This
was verified locally against a real Postgres container and the real image
before being trusted in CI (this codebase's own standing rule: a gate nobody
has watched fail, or in this case succeed for the right reason, is a gate
nobody should trust) - with one caveat recorded here rather than rediscovered
later: `--network host` behaves identically to a bare Linux host only on an
actual Linux Docker daemon. Docker Desktop on Windows/Mac runs containers
inside a hidden Linux VM, so `--network host` there does not expose the
container's ports on the developer machine's own `localhost`, even though
the container itself is reachable and functioning correctly (proven here by
exec-ing into it directly). `ubuntu-22.04` GitHub-hosted runners are real
Linux hosts, so this gap does not apply to the environment that actually
matters.

### SBOM and vulnerability scanning target the image CI actually built

`anchore/sbom-action` (Syft) generates a CycloneDX SBOM for the exact image
tag (`wexchange:ci-<sha>`) the `container` job just proved boots and serves
traffic, uploaded as a build artifact rather than committed - it is a
point-in-time statement about one specific image, not something that belongs
in version control. `aquasecurity/trivy-action` scans that same image and
fails the job on any `HIGH` or `CRITICAL` finding, `ignore-unfixed: false`:
an unfixed finding still describes a real exposure the base image carries,
and exempting it silently would be indistinguishable from never having
scanned at all. The documented remedy is bumping the base image
(`eclipse-temurin:17-jre-alpine`), which Dependabot's new `docker` ecosystem
entry tracks, not adding a suppression.

### Secret scanning: Gitleaks against full history, with a narrow, documented allowlist

`secret-scan` runs Gitleaks (`gitleaks/gitleaks-action`) with
`fetch-depth: 0` so it can scan the full commit history reachable from the
current ref, not just the working tree. This was run locally first against
this repository's actual git history before being trusted, and found two
real matches: `PurchaseIdempotencyStoreAdapterIT.java`'s `"purchase-123"`
test fixture literal, flagged by the default ruleset's entropy-based
`generic-api-key` rule purely because of its character distribution, not
because it is a credential. Rather than let this fail CI on its very first
run, `.gitleaks.toml` extends Gitleaks' default ruleset
(`[extend] useDefault = true`) with one narrow allowlist entry scoped to that
exact file and a regex matching only that literal's shape - not a blanket
exemption for the file, still less for the whole `integrationTest` source
set. `docs/engineering/ci-pipeline.md` documents the remediation flow for a
real finding explicitly so this allowlist is never mistaken for the template
to follow when one shows up.

Both directions of this gate were proven, not assumed: a throwaway local
commit adding a synthetic AWS-access-key-shaped string was scanned and
correctly flagged (`aws-access-token` rule) before that commit was discarded
via `git reset --hard` and confirmed never pushed - proving the gate still
catches a genuine-shaped secret even with the allowlist in place, not just
that the allowlist suppresses the one thing it was written for.

### Dependabot across every ecosystem this repository actually has

`.github/dependabot.yml` adds weekly update checks for `gradle` (production
and build dependencies), `github-actions` (the SHA-pinned actions above -
Dependabot already knows how to bump a pinned SHA to a newer release's SHA,
not just a tag), and `docker` (the `Dockerfile`'s base images). No ecosystem
this repository does not use (npm, pip, and so on) is declared.

## Enforcement

- Every job and gate described above was run against this repository's real
  state before being trusted, not left to "should work": the container job's
  full sequence (build, run, health-poll, smoke-test route, SBOM, scan) was
  reproduced locally end to end via `docker build` and `docker run`; Gitleaks
  was run twice locally, once proving the allowlist suppresses exactly the
  two documented false positives and nothing else, once proving it still
  catches a genuine synthetic secret.
- `OpenApiSpecTest`, already part of `check`, continues to fail the `build`
  job the moment `docs/openapi/wexchange-v1.yaml` drifts from `PurchaseApi`'s
  or `CountryCurrencyApi`'s actual `@RequestMapping`s - this issue adds no
  parallel OpenAPI check, because one already exists and a second would only
  risk drifting from it.
- `wrapper-validation` runs before `build` (via `needs:`), so a tampered
  wrapper is caught before any Gradle task from this checkout ever executes.

## Consequences

**Accepted costs.**

- The `container` job adds real wall-clock time to every push and pull
  request (a full Docker build, a live Postgres, an SBOM generation, and a
  Trivy scan) rather than running only on a schedule or only pre-release.
  This was chosen over deferring it, because "the built image passes a local
  API smoke test" is one of this issue's own acceptance criteria, and an
  image that only gets built at release time is an image whose failure mode
  is discovered too late to be cheap to fix.
- Trivy's `ignore-unfixed: false` policy means a CVE in the base image with
  no patch available yet can fail CI with nothing this repository's own code
  can do about it beyond waiting for or switching base images. Accepted
  because the alternative - silently ignoring unfixed findings - hides
  exactly the exposures most worth knowing about now rather than later.
- `.gitleaks.toml`'s allowlist is one more file whose correctness has to be
  trusted; a future edit widening it carelessly could hide a real secret.
  Mitigated by scoping it as narrowly as technically possible (one file, one
  regex) and documenting the remediation flow prominently enough that widening
  the allowlist is not the instinctive response to a real failure.

**What this buys.**

- A pull request cannot merge past a high/critical vulnerability in a new
  dependency, a high/critical vulnerability in the built image, a committed
  secret, or a tampered Gradle wrapper - each proven to actually fire, not
  merely configured.
- CI proves the same artifact that would actually ship (the real
  multi-stage `Dockerfile` image) boots against a real database and serves a
  real request, not just that `bootJar` produces a file.
- Every third-party action this pipeline depends on is pinned to a specific,
  verifiable commit, with Dependabot already wired to propose the next one.
- The redundant second test run is gone, and the workflow no longer restates
  any threshold `build.gradle` already owns.

**Rejected alternatives.**

- *GitHub's native secret scanning / push protection instead of Gitleaks.*
  Native secret scanning requires GitHub Advanced Security, which is not
  available on every plan this repository might run under, and push
  protection only blocks a push at the moment of pushing - it does not
  re-scan a full existing history the way a workflow-based tool can, and
  cannot be exercised locally the way this ADR's verification steps did for
  Gitleaks. A repository-owned Gitleaks configuration works identically
  regardless of plan and can be proven correct offline.
- *Splitting this into two workflow files (a fast Gradle-only one and a
  slower supply-chain one).* Considered for the sake of a shorter
  time-to-green on the primary quality gate, but rejected in favor of one
  file with independent jobs: GitHub Actions already parallelizes
  independent jobs within one workflow, so the split would have bought
  nothing but a second file to keep in sync, while one file keeps the whole
  pipeline's shape visible in one place.
- *A separate `management.server.port` or otherwise more elaborate network
  topology for the container smoke test.* `--network host` is the simplest
  mechanism that is also exactly correct for a same-process, same-runner
  Linux job; anything more elaborate (a custom bridge network, Testcontainers
  from within the workflow) would only be solving a problem `--network host`
  does not have on the actual `ubuntu-22.04` runner.
- *Pushing the built image to a registry from this workflow.* There is no
  registry for it yet - that arrives with issue #22/#23's AWS environment and
  deployment pipeline. Building only far enough to smoke-test and scan
  locally on the runner, without pushing anywhere, is the correct amount of
  scope for this issue.
