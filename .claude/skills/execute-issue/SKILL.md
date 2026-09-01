---
description: Execute one Wexchange GitHub issue end to end under the repository engineering contract, including tests, documentation, and a pull request. Use with an issue number.
---

# Execute Wexchange issue `$0`

If `$0` is missing or is not an issue number, ask for the number and do not begin
implementation.

1. Read `CLAUDE.md`, `docs/engineering/quality-foundation.md`,
   `docs/engineering/test-taxonomy.md`, and the complete issue using
   `gh issue view $0`.
2. Inspect `git status`, the current branch, repository structure, Gradle tasks,
   and all code and tests affected by the issue.
3. Confirm that dependencies listed by the issue are complete. Identify unclear
   acceptance criteria before making a change that would depend on guessing.
4. State a compact execution plan containing scope, architectural impact, tests,
   risks, and validation commands.
5. Establish the existing test/build baseline. Preserve unrelated user changes;
   if they prevent safe isolation, stop and explain the conflict.
6. Create or use one focused branch for this issue. Do not combine other roadmap
   work.
7. Implement the smallest complete solution. Add or update tests, contracts,
   migration/configuration, ADRs, and operational documentation required by the
   change.
8. Run focused tests during development, followed by every relevant available
   repository quality check. Do not weaken gates or invent tasks that do not yet
   exist.
9. Review the full diff for issue scope, architecture, security, compatibility,
   generated files, and accidental secrets.
10. If all acceptance criteria are satisfied, create a pull request whose body
    explains the change, lists verification evidence, calls out risk, and includes
    `Closes #$0`. Do not merge it.

Finish with the completion report required by `CLAUDE.md`. If blocked, give the
exact blocker, evidence gathered, and the smallest decision or external change
needed to continue.
