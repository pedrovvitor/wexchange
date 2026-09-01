---
description: Perform a read-only, evidence-based review of a Wexchange pull request. Use with a pull-request number.
---

# Review Wexchange pull request `$0`

If `$0` is missing or is not a pull-request number, ask for the number.

This is a read-only review. Do not modify files, push commits, merge, approve, or
submit review comments unless the user explicitly asks for that additional action.

1. Read `CLAUDE.md`, `docs/engineering/quality-foundation.md`,
   `docs/engineering/test-taxonomy.md`, the pull request,
   its linked issue, changed files, commits, and reported checks.
2. Inspect the surrounding implementation and tests rather than judging the diff
   in isolation.
3. Validate behavior against acceptance criteria and check architecture,
   correctness, security, privacy, resilience, compatibility, operability, and
   meaningful test coverage.
4. Run safe, relevant checks locally when possible. Distinguish verified failures
   from risks or questions.
5. Report findings first, ordered by severity. For each finding include the file
   and tight line range, concrete impact, reproduction or reasoning, and the
   smallest viable correction.
6. Then report open questions, validation performed, and a concise assessment of
   residual risk. If no actionable findings exist, say so explicitly and name any
   testing or environment gaps that remain.
