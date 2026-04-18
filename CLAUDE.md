# Claude notes for mocapi

Project-specific overrides and additions. Global workflow, code-quality, and
OSS release process live in `~/CLAUDE.md` (see `# OSS Release Process` there).

## Workflow
Never write or edit code without being explicitly told to do so! You may ask if you are allowed to make changes, but do not do so unless I explicitly confirm.

When committing and pushing changes to CLAUDE.md only, include `[skip ci]` in the commit message to avoid triggering CI.

## Code quality — project-specific

The `@SuppressWarnings("deprecation")` exception in the global guide applies here
specifically because mocapi must keep supporting `LegacyTitledEnumSchema`: the
MCP spec defines it as a backward-compatibility variant, so the code that
instantiates it and the tests that exercise it need `@SuppressWarnings("deprecation")`
to compile cleanly. Every such suppression must have a comment explaining
which part of the spec requires the deprecated usage. This exception is specifically for
deprecations tied to the spec contract — it does NOT open the door to suppressing
other warning categories.

## Release — project-specific notes

Follow the global OSS release runbook in `~/CLAUDE.md`. The mocapi-specific bits:

- `mvn verify` covers every module. `mocapi-conformance` currently has
  no unit or integration tests of its own — it is a runnable Spring
  Boot app that's meant to be driven externally by the
  `@modelcontextprotocol/conformance` npx tool. Before cutting a release also run the release-profile
  javadoc build to catch doclint errors that plain `verify` misses:
  ```
  mvn -P release javadoc:jar -DskipTests
  ```
- For the Maven Central badge in `README.md`, use `mocapi-server` as the
  artifact. The Solr search index that shields.io queries has gaps — not all
  mocapi artifacts are indexed; `mocapi-server` is confirmed to work.
- Release cadence during 0.x: minor bumps carry headline features and may
  include breaking changes (pre-1.0); patches are reserved for hotfixes.
