# Architecture Decision Records

ADRs capture **point-in-time decisions** about mocapi's architecture.
They are immutable in spirit: a decision that no longer holds gets a
new ADR with status "Supersedes ADR-NNNN", and the old ADR's status
flips to "Superseded by ADR-MMMM".

## Provenance

ADRs 0001–0018 were reconstructed retroactively on 2026-05-07 from
the project's now-retired numbered-spec corpus and from git history.
The `Date:` field on each ADR records when the **decision landed in
the codebase** (the commit that introduced the central artifact —
class, sealed type, module, or annotation — that the decision
manifests as), not when the ADR document itself was authored. Where a
decision evolved across multiple commits, the date is the introduction
of the canonical artifact. ADRs added from this point on should be
authored at decision time and dated accordingly.

## Status legend

- **Accepted** — currently in effect, reflected in the code and the
  design docs.
- **Proposed** — under discussion, not yet implemented.
- **Superseded** — replaced by a later ADR. The replacement is linked.
- **Deprecated** — no longer in effect but not replaced (rare).

## How to add an ADR

1. Copy [`_template.md`](_template.md) to `NNNN-short-title.md` using
   the next free number.
2. Fill in Status / Context / Decision / Consequences.
3. Update the relevant [`../design/*.md`](../design/) in the same
   commit so the living docs stay synchronized.
4. Add the ADR to the index below.

## Index

- [ADR-0001 — Module structure and starter packaging](0001-module-structure-and-packaging.md)
- [ADR-0002 — Protocol/transport contract](0002-protocol-transport-contract.md) *(Superseded by ADR-0020)*
- [ADR-0003 — Streamable HTTP and stdio as peer transports](0003-streamable-http-and-stdio.md)
- [ADR-0004 — Lazy JSON-vs-SSE response shape via state machine](0004-lazy-json-vs-sse-state-machine.md)
- [ADR-0005 — Encrypted SSE event IDs](0005-encrypted-sse-event-ids.md) *(Superseded by ADR-0020)*
- [ADR-0006 — Virtual-thread-per-call with context propagation](0006-virtual-thread-per-call.md)
- [ADR-0007 — Substrate as the storage SPI and pluggable session store](0007-substrate-storage-spi.md) *(Superseded by ADR-0020)*
- [ADR-0008 — Substrate Mailbox for elicitation/sampling rendezvous](0008-mailbox-elicitation-sampling.md) *(Superseded by ADR-0021)*
- [ADR-0009 — `McpContextResult` sealed type for transport-portable validation](0009-mcpcontextresult-sealed-validation.md) *(Superseded by ADR-0020)*
- [ADR-0010 — Annotation-driven handler discovery and naming](0010-annotation-driven-handler-discovery.md)
- [ADR-0011 — Customizer SPI, interceptor strata, and handler descriptors](0011-customizer-spi-and-strata.md)
- [ADR-0012 — Guard SPI: visibility ≡ invocation](0012-guard-spi.md)
- [ADR-0013 — OAuth2 module and reference Guard implementation](0013-oauth2-and-reference-guards.md)
- [ADR-0014 — `mocapi-model` translated 1:1 from MCP `schema.ts`](0014-mocapi-model-from-schema-ts.md)
- [ADR-0015 — Constrained elicitation schema builder](0015-constrained-elicitation-schema-builder.md)
- [ADR-0016 — Tool schema generation via victools](0016-victools-tool-schema-generation.md)
- [ADR-0017 — Observability stack: Micrometer + audit + actuator](0017-observability-stack.md)
- [ADR-0018 — MCP spec features deliberately not implemented](0018-mcp-spec-features-not-implemented.md) *(Superseded by ADR-0022)*
- [ADR-0019 — Adopt MCP 2026-07-28 as the sole protocol (clean break)](0019-clean-break-2026-07-28.md)
- [ADR-0020 — Stateless request model; sessions removed](0020-stateless-request-model.md)
- [ADR-0021 — MRTR elicitation via replay](0021-mrtr-elicitation-replay.md)
- [ADR-0022 — MCP 2026-07-28 features deliberately not implemented](0022-2026-07-28-features-not-implemented.md)
- [ADR-0023 — Guard denial moves to `-32010`; spec claims `-32003`/`-32004`](0023-guard-denial-code-relocation.md)
- [ADR-0024 — `McpElicitor`: elicitation from prompt and resource handlers](0024-mcp-elicitor-spi.md)
- [ADR-0025 — Typed progress emitters and the `MrtrContext` super-interface](0025-progress-emitters-and-mrtr-context.md)
- [ADR-0026 — Response-`_meta` injection seam; `serverInfo` SHOULD adherence](0026-response-meta-serverinfo-injection.md)
- [ADR-0027 — Remove the `DRAFT-2026-v1` protocol alias on 2026-07-28 finalization](0027-remove-draft-2026-v1-alias.md)
