# ADR-0028 — Decline SEP-2243 custom parameter headers (`x-mcp-header`)

- **Status:** Accepted
- **Date:** 2026-07-28

## Context

MCP 2026-07-28 introduces SEP-2243 "custom parameter headers": a tool author
may annotate an input-schema property with `x-mcp-header`, declaring that the
argument is carried in a named HTTP request header (Base64-wrapped as
`=?base64?...?=` for non-ASCII) rather than in the JSON-RPC `params` body. The
official conformance suite exercises this via `http-custom-header-server-validation`
(SEP-2243): a server implementing it MUST validate the Base64 encoding, reject
invalid padding/characters, treat unwrapped values as literals, and reject a
request where the header is omitted but the value appears in the body.

The spec makes this optional — a server MAY designate no header-sourced
parameters. mocapi declares none, so the suite reports all five checks as
`NotTestable`. This ADR records *why* that is a deliberate, principled decline
rather than a temporary gap, so it is not re-litigated.

## Decision

mocapi does not implement SEP-2243 custom parameter headers, on architectural
grounds:

1. **It is an HTTP-transport-only feature.** The value source is an HTTP header.
   The stdio transport has no headers, so the feature cannot exist uniformly
   across mocapi's transports — it is intrinsically HTTP-shaped.
2. **It violates the transport-agnostic server contract** (ADR-0002, the
   [constitution](../constitution.md)). mocapi's handler/server layer deals in
   `JsonRpcCall` / `JsonRpcMessage` and knows nothing about HTTP. Sourcing a
   tool *argument* (a server-layer concern) from an HTTP *header* (a transport
   concern) would force the tool-parameter model to reach into transport-specific
   plumbing — either special-casing HTTP inside the server, or bolting on a
   per-transport "does this transport have headers?" conditional. Both blur the
   `McpServer` ↔ `McpTransport` boundary this project deliberately keeps clean.
3. **The legitimate use cases are already served idiomatically**, without leaking
   transport into the tool contract: caller identity / tenancy via OAuth2
   `SecurityContext` and the `McpPrincipalSource` SPI (ADR-0013); distributed
   tracing via W3C trace-context keys on the `_meta` envelope (ADR-0017); and
   request-scoped context via customizers and parameter resolvers.

## Consequences

- A tool cannot declare a header-sourced argument; every tool argument travels
  in the JSON-RPC `params` body. This is a narrow, opt-in feature to forgo.
- The `http-custom-header-server-validation` scenario is permanently baselined in
  `mocapi-conformance/conformance-expected-failures.yaml` (referencing this ADR),
  distinct from the *deprecated*-feature declines in ADR-0022.
- The transport-agnostic invariant (ADR-0002) is preserved: no HTTP concept
  reaches the handler/tool-parameter layer.
- **Reversal cost:** if a future need justifies it, header-sourced parameters
  would have to be modeled as a transport *capability* (only HTTP advertises it)
  with an explicit resolver seam — a larger change than a tool annotation, by
  design. This ADR would be superseded rather than quietly reversed.
