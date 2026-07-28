# Mocapi Documentation

Three audiences, three trees:

- **[guides/](guides/)** — you are building an MCP server with mocapi.
  Start here. How to write tools, configure transports, plug in
  observability, externalize metadata.
- **[design/](design/)** — you are extending mocapi or reasoning about
  its internals. Living architecture documents, kept synchronized with
  the code.
- **[adr/](adr/)** — you want to know *why* a thing is the way it is.
  Point-in-time architecture decisions with status.

Alongside those three trees:

- **[constitution.md](constitution.md)** — the non-negotiable architectural
  invariants (each links its governing ADR).
- **[roadmap.md](roadmap.md)** — where mocapi is going.
- **[superpowers/](superpowers/)** — the spec-driven-development workflow, with
  per-feature [specs](superpowers/specs/) and [plans](superpowers/plans/).

Top-level project documents (README, CHANGELOG, CONTRIBUTING, PRD,
SECURITY) live at the repo root.

## Suggested reading order

**If you are building a server,** read these in order:

1. The repo-root [`README.md`](../README.md) — what mocapi is, the
   quick start.
2. [`guides/tools.md`](guides/tools.md) — your first `@McpTool`.
3. [`guides/configuration.md`](guides/configuration.md) — the property
   surface.
4. [`guides/observability.md`](guides/observability.md) — wire up
   logging, metrics, audit.
5. [`guides/authorization.md`](guides/authorization.md) (only if you
   need OAuth2).

**If you are extending mocapi or reviewing a design,** read these in order:

1. [`design/architecture-overview.md`](design/architecture-overview.md) — module layering, request flow, ScopedValues.
2. [`design/transports.md`](design/transports.md) — Streamable HTTP and stdio.
3. [`design/extension-spi.md`](design/extension-spi.md) — customizers, strata, parameter resolvers.
5. The [ADR index](adr/README.md) — pick decisions relevant to your area.

**If you are looking for a specific decision,** the [ADR index](adr/README.md)
is the entry point. ADRs cross-link to the design docs they implement.
