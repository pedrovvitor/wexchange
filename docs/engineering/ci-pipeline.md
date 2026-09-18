# CI pipeline

What `.github/workflows/gradle_ci.yml` runs, why it is shaped the way it is,
and what to do when one of its gates fails. See
[ADR 0007](../adr/0007-ci-supply-chain-hardening.md) for the decisions behind
this shape.

## Jobs

| Job | Trigger | What it proves |
| --- | --- | --- |
| `wrapper-validation` | every push/PR | The committed `gradlew`/`gradle-wrapper.jar` matches Gradle's own list of known-good wrapper checksums - catches a tampered or accidentally-modified wrapper before it can execute anything. |
| `build` | every push/PR, after `wrapper-validation` | `./gradlew check` - the one canonical pipeline: unit, integration, and architecture suites, formatting, PMD, JaCoCo coverage, and mutation testing. Every threshold lives in `build.gradle` (see [`test-taxonomy.md`](test-taxonomy.md)); this workflow never restates one. |
| `secret-scan` | every push/PR | Gitleaks scans the full commit history reachable from this ref for credentials, tokens, and keys. |
| `dependency-review` | pull requests only | GitHub's dependency-review-action flags newly-introduced dependencies with known high/critical vulnerabilities before merge. |
| `container` | every push/PR, after `build` | Builds the real `Dockerfile` image, runs it against a real Postgres service container, waits for `/actuator/health`, and calls one real API route - proving the image that will actually ship boots and serves traffic, not just that the jar compiles. Then generates a CycloneDX SBOM and scans the image with Trivy. |

## Why one canonical `check`, not `build` then `test`

The workflow this replaced ran `./gradlew build` (which already depends on
`check`, which already runs `test`) and then `./gradlew test` again
immediately after - the same suite, twice, in the same pipeline, for no
documented reason. `build` runs `check` and stops there now.

## Permissions and concurrency

The workflow declares `permissions: contents: read` once, at the top level.
Only `dependency-review` elevates its own job to add `pull-requests: write`,
because it is the one job that posts a PR comment; every other job stays
read-only. A newer push on the same branch or PR cancels an in-flight run for
an older commit (`concurrency` with `cancel-in-progress: true`) - a runner is
never spent proving a commit that is already stale.

## Actions are pinned to a commit SHA, not a version tag

Every third-party action is referenced as `owner/repo@<40-character-sha> #
<tag, for humans>`, not `owner/repo@v6`. A version tag can be moved to point
at different code after the fact; a commit SHA cannot. Dependabot's
`github-actions` ecosystem entry in `.github/dependabot.yml` keeps these
current - bumping a pinned SHA is exactly the kind of small, reviewable PR
Dependabot is for.

## Gate policies

| Gate | Policy | Where it's configured |
| --- | --- | --- |
| Dependency review | Fails the PR on a **high or critical** severity finding in a newly-introduced dependency. | `dependency-review` job, `fail-on-severity: high` |
| Container vulnerability scan | Fails the build on a **high or critical** severity finding in the built image, fixed or not. | `container` job, Trivy step, `severity: 'HIGH,CRITICAL'`, `ignore-unfixed: false` |
| Secret scanning | Any match against Gitleaks' default ruleset fails the job, except the two documented false positives in `.gitleaks.toml`. | `secret-scan` job, `.gitleaks.toml` |
| Gradle wrapper | Any wrapper JAR not in Gradle's own known-good list fails immediately, before anything else runs. | `wrapper-validation` job |

## Secret scanning: remediation flow

If `secret-scan` fails on a genuine finding (not one of the two documented
false positives in `.gitleaks.toml`):

1. **Treat the credential as already compromised.** It is in git history now,
   which is effectively public even if the repository is not - rotate or
   revoke it at the source (the provider, the database, the cloud account)
   before doing anything else. Do not wait for step 2.
2. Remove the secret from the current tip of the branch (a follow-up commit
   deleting or replacing it).
3. If the branch has not been merged and nobody else has pulled it, rewrite
   history to remove the secret from every commit it appears in
   (`git filter-repo` or an interactive rebase), then force-push. If the
   branch is shared or already merged, rewriting history stops being safe -
   rotating the credential (step 1) is what actually neutralizes the leak;
   scrubbing history at that point is cleanup, not the fix.
4. Re-run `secret-scan` to confirm the finding is gone.

**Never** add a real secret's path or pattern to `.gitleaks.toml`'s
allowlist to make the job pass. That file exists for exactly two verified
false positives (test fixture literals that happen to trip the
`generic-api-key` entropy heuristic) - each one documented with why it is not
a secret. Adding an entry for anything else defeats the entire gate.

## SBOM

The `container` job generates a CycloneDX SBOM (`sbom.cyclonedx.json`) for
the exact image it just built and scanned, via Syft
(`anchore/sbom-action`), and uploads it as a workflow artifact with a 90-day
retention. Issue #12's release process is where a tagged release's SBOM gets
attached to the GitHub release itself; this job produces the artifact that
step consumes.

## What is deliberately not here yet

- **Pushing the built image to a registry.** This workflow only builds the
  image long enough to smoke-test and scan it locally on the runner; nothing
  is pushed anywhere. Registry publication belongs to issue #23's deployment
  pipeline, once there is somewhere real to deploy to.
- **A scheduled, deeper scan cadence** (e.g., a nightly Trivy run against the
  last-published image to catch newly-disclosed CVEs in a dependency that
  was already fixed at build time). Worth adding once a real deployed image
  exists to scan.
