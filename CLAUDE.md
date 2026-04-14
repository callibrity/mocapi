# Claude notes for mocapi

## Workflow
Never write or edit code without being explicitly told to do so! You may ask if you are allowed to make changes, but do not do so unless I explicitly confirm.

When committing and pushing changes to CLAUDE.md only, include `[skip ci]` in the commit message to avoid triggering CI.

## Code Quality
Never suppress warnings. Do not use `@SuppressWarnings` annotations or any equivalent suppression mechanism. Instead, fix the underlying issue.

**Narrow exception**: `@SuppressWarnings("deprecation")` is acceptable when a deprecated API is being used legitimately because the specification the project implements mandates support for the deprecated form. Every such suppression must have a comment explaining why the deprecated usage is intentional and which part of the spec requires it.

## Release process

Mocapi is published to Maven Central via a GitHub Actions workflow
(`.github/workflows/maven-publish.yml`) that triggers on
`release: created`. The workflow runs `mvn versions:set` to strip
`-SNAPSHOT` and then deploys. **The pom's `<version>` stays at
`-SNAPSHOT` in git at all times** — the workflow sets the release
version ephemerally inside the CI build environment only.

### Cutting a release

Assume we're cutting `X.Y.Z` (for example, `0.2.0`) and the pom
currently says `X.Y.Z-SNAPSHOT`.

1. **Verify main is green** — `mvn verify` passes (excluding mocapi-compat
   which has no tests), `spotless:check` passes, all commits pushed.

   **Also run the release-profile javadoc build explicitly:**
   ```
   mvn -P release javadoc:jar -DskipTests
   ```
   The `release` profile's `maven-javadoc-plugin` has `failOnError=true`
   by default, which means doclint errors will fail the Maven Central
   publish workflow. These errors do **not** show up in a plain
   `mvn verify` — they only surface in the release profile. Catch them
   locally before cutting the release.

2. **Update `CHANGELOG.md`** (if one exists):
   - Rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` (use today's
     date in ISO format).
   - Add a fresh empty `## [Unreleased]` section at the top.
   - Add a new link reference at the bottom of the file:
     `[X.Y.Z]: https://github.com/callibrity/mocapi/releases/tag/X.Y.Z`
   - Follow Keep-a-Changelog categories: `### Breaking changes`,
     `### Added`, `### Changed`, `### Fixed`, `### Documentation`,
     `### Requirements`. Drop any sections that have no entries.

3. **Commit** with a descriptive message:
   ```
   X.Y.Z: short one-line summary of the headline changes
   ```

4. **Push main:**
   ```
   git push origin main
   ```

5. **Create the GitHub Release** (this is what actually triggers the
   Maven Central publish):
   ```
   gh release create X.Y.Z --title "X.Y.Z" --notes-file <notes-file>
   ```
   Tag, title, Maven version, and CHANGELOG header are all the exact
   same bare semver string (e.g., `0.1.0`). No `v` prefix anywhere.
   The release notes should mirror the CHANGELOG section for this
   version. Write them in a temp file first (e.g., `/tmp/release-notes.md`)
   and pass via `--notes-file`.

   Once the release is created, GitHub Actions fires, runs
   `mvn versions:set -DnewVersion=X.Y.Z`, and deploys to Maven Central.
   Monitor the workflow run via `gh run list`.

6. **Bump the dev version** (this is the ONLY place we touch the pom
   version locally):
   ```
   mvn versions:set -DnewVersion=X.(Y+1).0-SNAPSHOT -DgenerateBackupPoms=false
   ```
   So `0.1.0` → `0.2.0-SNAPSHOT`. Always bump the **minor** version
   after a release during 0.x (we're pre-1.0, so minor bumps are our
   main release cadence; patch releases like 0.1.1 are for hotfixes only).

7. **Commit and push the bump:**
   ```
   git commit -am "Bump to X.(Y+1).0-SNAPSHOT"
   git push
   ```

### CRITICAL: NO `v` prefix, ANYWHERE

The version is a bare three-part semver string — e.g. `0.1.0` — and
that same exact string is used in every single place:

- Git tag: `0.1.0`
- GitHub release title: `0.1.0`
- `mvn versions:set -DnewVersion=0.1.0`
- pom `<version>` (the non-SNAPSHOT equivalent)
- CHANGELOG header: `## [0.1.0] - YYYY-MM-DD`
- CHANGELOG link target URL path segment: `.../releases/tag/0.1.0`
- Commit messages, release notes body, everywhere else

**Never type `v` before a version number. Not on the tag, not on the
title, not on the Maven version, not anywhere.** Maven Central
artifacts are immutable — a version string with a `v` in it cannot be
deleted or overwritten and pollutes the artifact's version history
forever.

### What NOT to do

- **Don't prefix the version with `v` anywhere.** Tag, title, Maven
  version, CHANGELOG — all the same bare `X.Y.Z` string.
- **Don't change the `<version>` in pom.xml to the release version.**
  The pom always says `-SNAPSHOT` in git. The CI workflow strips it in
  its ephemeral build environment. Manually setting the release version
  in git would cause the next build to publish the wrong artifact.
- **Don't skip the GitHub Release step and rely on a plain tag push.**
  The Maven publish workflow triggers on `release: created`, not on
  tag push. A bare tag without a release will not publish anything.
- **Don't squash or amend release commits after tagging.** The tag
  points at a specific SHA — amending changes the SHA and orphans the
  tag.

### Release cadence

- **0.x minor releases** (0.1.0 → 0.2.0): headline features, breaking
  changes allowed (we're pre-1.0). Cut whenever a logical set of
  changes has accumulated.
- **0.x patch releases** (0.1.0 → 0.1.1): bug fixes and tiny additive
  changes only. No breaking changes.
- **1.0.0**: API stability commitment. After 1.0, breaking changes
  require a major bump and a deprecation cycle.

### Release notes style

- Lead with **breaking changes** if there are any, with migration guidance.
- Use flat Markdown — no banners, no emoji, no marketing copy.
- The audience is developers migrating code.
- For the Maven Central badge, use `mocapi-server` as the artifact
  (confirmed to be indexed by shields.io).

### Maven Central badge

The badge in README.md references `mocapi-server` because the Solr search
index that shields.io queries has gaps — not all artifacts are indexed.
`mocapi-server` is confirmed to work.
