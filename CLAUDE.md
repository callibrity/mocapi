# Claude notes for mocapi

Project-specific overrides and additions. Global workflow, code-quality, and
OSS release process live in `~/CLAUDE.md` (see `# OSS Release Process` there).

## Workflow
Never write or edit code without being explicitly told to do so! You may ask if you are allowed to make changes, but do not do so unless I explicitly confirm.

When committing and pushing changes to CLAUDE.md only, include `[skip ci]` in the commit message to avoid triggering CI.

## Documentation layout

Docs live under `docs/` in three trees:

- `docs/guides/` — user-facing how-tos for library consumers.
- `docs/design/` — living design docs describing the current architecture; updated in lockstep with the code.
- `docs/adr/` — Architecture Decision Records (point-in-time, status: Accepted/Superseded/etc.). Template at `docs/adr/_template.md`.

The Ralph spec workflow has been retired; the historical specs that
produced 0.1.0 → 0.17.0 were mined for ADRs and removed from the tree.

New non-trivial work follows the superpowers SDD pipeline — see
[`docs/superpowers/README.md`](docs/superpowers/README.md) for when it applies
and where spec/plan artifacts live.

### When a change requires an ADR + design-doc update (rule)

**Before writing code for an architecturally significant change, stop and
produce two artifacts.** "Architecturally significant" means the change
falls into any of these buckets:

- A new module, or removal/merger of an existing module.
- A new SPI (interface intended for users to implement) or a
  backwards-incompatible change to an existing SPI.
- A new transport, or a change to the `McpServer` ↔ `McpTransport`
  contract.
- A change to handler discovery, customizer composition, the stratum
  ordering, or the descriptor pattern.
- A change to session storage, mailbox/journal usage, or the Substrate
  SPI contract.
- A change to the authorization model (filter chains, Guard semantics,
  `McpTokenStrategy`, RFC 9728 metadata shape).
- A change to what mocapi declares not implemented in
  `docs/adr/0018-*.md` (adding/removing an item from the list).
- A change to declared MCP capabilities (`subscribe`, `listChanged`, etc.).

For each such change:

1. Add a new ADR under `docs/adr/NNNN-<short-title>.md` using the
   template (`docs/adr/_template.md`). Status `Accepted`. Date is
   today's date. Include `**Code anchors:**` pointing at the file(s)
   the decision manifests as.
2. Update the affected `docs/design/*.md` to reflect the new state.
   The design doc should never describe behavior that is no longer
   true; if a decision changes, the design doc changes in the same
   PR.
3. Add the new ADR to the index in `docs/adr/README.md`.
4. If the new ADR supersedes an existing one, flip the old ADR's
   status to "Superseded by ADR-NNNN" with a back-link.

Bug fixes, refactors that don't change a public contract, dependency
bumps, test additions, and documentation-only changes do **not**
require an ADR.

If you're not sure whether a change qualifies, ask before writing code.
"I should have written an ADR for that" is harder to fix later than
"I asked first and we agreed it didn't need one."

## Architectural invariants

The load-bearing invariants live in [`docs/constitution.md`](docs/constitution.md).
Do not violate one without a superseding ADR (see the ADR rule above).

## Read-before-you-touch (subsystems)

Load the matching docs at the start of work in that area — and name them in
every subagent dispatch, so a fresh-context worker loads the right material:

- Transports / `McpServer`↔`McpTransport` → `docs/design/transports.md`, ADR-0002/0003/0004
- Authorization (OAuth2, guards) → `docs/design/authorization-model.md`, ADR-0012/0013
- Model / wire types → ADR-0014, `docs/plans/2026-07-28-schema.ts`
- Handlers & discovery → `docs/design/handlers.md`, ADR-0010/0011
- Elicitation / MRTR → `docs/design/elicitation-mrtr.md`, ADR-0021/0024/0025
- Observability → `docs/design/observability-stack.md`, ADR-0017

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
