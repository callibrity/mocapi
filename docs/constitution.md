# mocapi Constitution — Architectural Invariants

These are the load-bearing invariants of mocapi: the properties a change must
not violate **without a superseding ADR**. This file is an *index of
invariants*, not a restatement of the ADRs — each entry links its governing
decision. Conduct rules (formatting, warnings, imports, workflow) live in
[`../CLAUDE.md`](../CLAUDE.md); product direction lives in
[`roadmap.md`](roadmap.md).

Changing an invariant means writing a new ADR (status Accepted) that supersedes
the cited one, updating the affected [`design/`](design/) doc(s) in the same
change, and updating the entry here.

## Invariants

### I1 — Stateless request model
Every request is self-contained: no sessions, no handshake, and no
server-initiated request channel. Correlation state travels in the request
envelope, not in server-held session state.
→ [ADR-0019](adr/0019-clean-break-2026-07-28.md),
[ADR-0020](adr/0020-stateless-request-model.md)

### I2 — Single protocol/transport coupling
`McpServer` ↔ `McpTransport` is the only seam between the protocol layer and
any transport. `mocapi-server` depends on no I/O framework (no Servlet API, no
Spring MVC); transports own their wire-format validation and map results to
their native error format.
→ [ADR-0002](adr/0002-protocol-transport-contract.md)

### I3 — Spec-compliance target
mocapi tracks the current MCP revision (**2026-07-28**) with a clean-break
philosophy: features the same revision deprecates at introduction are not
adopted.
→ [ADR-0019](adr/0019-clean-break-2026-07-28.md),
[ADR-0022](adr/0022-2026-07-28-features-not-implemented.md)

### I4 — Module boundaries & packaging
The module split (api / model / server / transports / autoconfigure /
observability / security / prompts) and its packaging rules are deliberate;
dependencies flow one way and modules do not reach across their boundaries.
→ [ADR-0001](adr/0001-module-structure-and-packaging.md)

### I5 — Static handler discovery
Tools, prompts, and resources are discovered from annotated Spring components
at startup. There is no dynamic registration and no list-changed / resource
-update push; `subscriptions/listen` is answered as unimplemented.
→ [ADR-0010](adr/0010-annotation-driven-handler-discovery.md)

### I6 — Declared not-implemented surface
The canonical "does mocapi do X?" list. Anything on it is a deliberate,
rationale-backed omission; conformance tooling asserts against it.
→ [ADR-0022](adr/0022-2026-07-28-features-not-implemented.md)

### I7 — Model is 1:1 with the MCP schema
`mocapi-model` mirrors the MCP `schema.ts` shapes (params, results, shared data
types); deprecated spec types stay as `@Deprecated` rather than being deleted.
→ [ADR-0014](adr/0014-mocapi-model-from-schema-ts.md)

### I8 — Authorization model
Bearer-token validation with **mandatory audience enforcement**, RFC 9728
Protected Resource Metadata, and the composable Guard SPI. These are the
resource-server obligations mocapi guarantees.
→ [ADR-0013](adr/0013-oauth2-and-reference-guards.md),
[ADR-0012](adr/0012-guard-spi.md)

### I9 — Error-code allocation
mocapi-private JSON-RPC codes live only in the implementation-defined sub-range
(`-32000`..`-32019`); spec-defined codes (`-32020`..`-32099`) are used
verbatim, never reassigned.
→ [ADR-0023](adr/0023-guard-denial-code-relocation.md)
