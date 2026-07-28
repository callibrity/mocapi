# ADR-0027 — Remove the `DRAFT-2026-v1` protocol alias on 2026-07-28 finalization

- **Status:** Accepted
- **Date:** 2026-07-28

## Context

The clean break to MCP 2026-07-28 (ADR-0019) targeted a *release
candidate* of the spec. During the RC window the official conformance
suite — and other draft-era clients — identified the protocol with the
sentinel string `DRAFT-2026-v1` rather than the dated `2026-07-28`
version. To remain testable against that tooling, mocapi accepted
`DRAFT-2026-v1` as an alias of `2026-07-28`: it appeared in
`McpServer.DRAFT_PROTOCOL_VERSION`, in the `MetaEnvelopeParser`
supported-version set, and in the `server/discover` advertised list.
Every site carried a comment marking it for removal "at the RC→final
re-verification once the final spec ships."

On 2026-07-28 the spec was **finalized** upstream: it was promoted out
of `schema/draft/` into a dated `schema/2026-07-28/` directory, and the
conformance tool now treats `2026-07-28` as a first-class dated
spec-version (targeted via `--spec-version 2026-07-28`, which drives the
stateless lifecycle) instead of the `--suite draft` track that sent the
sentinel. The alias's removal trigger has therefore fired.

Leaving the alias in place would ship a permanent, spec-nonexistent
accepted protocol string into 1.0.0. Because the supported-version set
is observable protocol behavior (advertised by `server/discover` and
enforced by the envelope parser), removing `DRAFT-2026-v1` *after* 1.0.0
would be a breaking change requiring a 2.0. It must go before the first
stable release.

## Decision

Remove `DRAFT-2026-v1` entirely. `2026-07-28` is the sole protocol
version mocapi accepts and advertises.

1. Delete the `McpServer.DRAFT_PROTOCOL_VERSION` constant.
2. `MetaEnvelopeParser.SUPPORTED_VERSIONS` is `List.of(PROTOCOL_VERSION)`.
3. `DiscoverHandler` advertises `List.of(PROTOCOL_VERSION)`.
4. Conformance is run against the finalized spec with
   `--spec-version 2026-07-28` (stateless lifecycle), not `--suite draft`.

## Consequences

- The `_meta` protocol version and the `server/discover`
  `supportedVersions` list now contain exactly `2026-07-28`. A request
  carrying `DRAFT-2026-v1` is rejected with `UnsupportedProtocolVersion`
  like any other unknown version.
- The supported-version surface is frozen for 1.0.0; a future protocol
  revision is additive (a new dated version), never a re-add of a
  sentinel.
- **Follow-up (ADR-scope):** the conformance baseline must be
  regenerated against `--spec-version 2026-07-28`; the RC-era
  `--suite draft` run and its expected-failures file are stale (tracked
  separately in the 1.0.0 readiness plan).
- Supersedes the RC-window aliasing described in ADR-0019 §"release
  candidate is the build target"; ADR-0019 remains Accepted for the
  clean-break decision itself.
