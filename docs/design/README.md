# Mocapi Design Documents

These describe the *current* architecture. They are living documents:
when an ADR changes a decision, the relevant design doc is updated in
the same change. If a design doc and an ADR disagree, the design doc
is wrong — fix it.

## Index

- [Architecture Overview](architecture-overview.md) — module layering, request flow, ScopedValues
- [Transports](transports.md) — Streamable HTTP, stdio, the `McpServer` ↔ `McpTransport` contract
- [Extension SPI](extension-spi.md) — customizer SPI, the six interceptor strata, parameter resolver model
- [Handlers](handlers.md) — internal handler classes and how they are built at startup
- [Elicitation — MRTR Replay](elicitation-mrtr.md) — requestState codec, response ledger, replay engine, idempotency contract
- [MCP Apps](apps.md) — the `mocapi-apps` module, `_meta.ui` shapes, descriptor customizers, `@McpAppResource`/`@McpUi`
- [Authorization Model](authorization-model.md) — how OAuth2 + Guard SPI compose at runtime
- [Observability Stack](observability-stack.md) — design of the four-module observability story
- [Native Image](native-image.md) — what mocapi contributes to GraalVM native-image

## Keeping these synchronized with code

When you make an architecturally significant change:

1. Decide whether it warrants an ADR (a *decision*, not a tweak).
2. If yes: write the ADR under [`../adr/`](../adr/) using the template,
   *and* update the affected design doc(s) here in the same change.
3. If no: just update the design doc.

Docs and code drift fast when nobody owns the synchronization. The
rule is: if the design doc is wrong, fix it in the PR that made it
wrong.
