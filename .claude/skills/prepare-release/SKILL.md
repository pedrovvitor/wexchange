---
description: Prepare and verify a Wexchange release without publishing, tagging, deploying, or merging it. Use with the intended semantic version.
---

# Prepare Wexchange release `$0`

If `$0` is missing or is not a valid intended semantic version, ask for the
version before continuing.

Preparation is not publication. Do not create or push a tag, publish artifacts,
merge pull requests, deploy, or create the GitHub release unless the user
explicitly authorizes that separate action.

1. Read `CLAUDE.md`, `docs/engineering/quality-foundation.md`,
   `docs/engineering/test-taxonomy.md`, the current release documentation, and the issues/pull requests included since the prior release.
2. Verify the working tree, branch, version history, and existing tags. Detect
   incomplete roadmap dependencies or changes not represented by an issue.
3. Run the complete available repository quality gate plus release-relevant
   security, integration, packaging, and smoke checks.
4. Confirm API/configuration compatibility, migrations, rollback needs,
   deployment prerequisites, and operational documentation.
5. Prepare the version changes, changelog, and release notes required by the
   repository convention. Keep them in a focused branch or pull request.
6. Summarize included changes, verification evidence, known limitations,
   deployment/rollback notes, and any blocker to publication.
7. Stop for explicit approval before any irreversible or externally visible
   release action.
