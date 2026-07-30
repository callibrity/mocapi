# mocapi Roadmap

Forward direction only — decisions already made live in [`adr/`](adr/),
invariants in [`constitution.md`](constitution.md).

**1.0.0 shipped 2026-07-30.** The public API now follows [semantic
versioning](https://semver.org): a **major** bump for breaking changes (with a
deprecation cycle), a **minor** for backward-compatible features, a **patch**
for fixes.

This file is maintainer-owned. Items are grouped by intent, not scheduled;
nothing here is a dated commitment. "Should-fix" items are known defects or
gaps with an owner-agreed direction; "Candidate" items are unresolved calls.

## Should-fix (known defects / gaps from the 1.0 review)

- **Metric cardinality from unvalidated tool/prompt names.**
  `McpServerOperationInterceptor` sets `gen_ai.tool.name` / `gen_ai.prompt.name`
  as **low-cardinality metric tags** from the client-supplied `params.name`
  *before* the handler resolves — so an authenticated caller can mint unbounded
  metric series by calling non-existent tool names. Fails as
  resource-exhaustion on the metrics backend, not as an auth hole, but it is a
  real defect introduced with the semconv work (ADR-0030). Fix: only tag once
  the handler is resolved (or cap/validate before the value reaches a metric
  dimension). Patch/minor.
- **Coarse-403 recipe verification.** The resource-level `required-scopes`
  path is covered by an integration test (`McpRequiredScopesTest`); keep it
  green as Spring Security evolves — the `403 insufficient_scope` shape depends
  on `BearerTokenAccessDeniedHandler` behavior we don't own.

## Security follow-through

Not defects — hardening depth beyond what code review establishes. See the
[security guide](guides/security.md) for the current user-facing contract.

- **Full-surface security audit** before large-scale production adoption. The
  two 1.0 reviews covered *diffs*, not a from-scratch audit of the whole
  authorization/crypto surface.
- **MRTR `requestState` codec adversarial/fuzz test.** The AES-256-GCM token
  path (ADR-0021) is sound by construction and inspection; a fuzz harness
  against tampered/truncated/cross-principal tokens would raise the assurance
  floor.
- **Revisit the deferred authorization features** if a concrete threat model
  needs them: DPoP / mTLS token binding, signed metadata, per-tool
  `insufficient_scope` step-up. All declined with rationale in
  [ADR-0022](adr/0022-2026-07-28-features-not-implemented.md) /
  [ADR-0029](adr/0029-authorization-should-level-challenges.md) — reopen there.

## Observability

- **Track OTel MCP semconv as it stabilizes.** Every MCP attribute mocapi
  emits is `development`-stability, and the conventions were relocated to a new
  repo recently ([ADR-0030](adr/0030-otel-mcp-semconv-alignment.md) pins the
  snapshot). Re-diff against the registry and adjust as attributes graduate.
- **ripcurl JSON-RPC semconv gaps** (upstream, in `ripcurl-o11y`, surfaces for
  vanilla JSON-RPC users only — mocapi's server span already covers itself):
  `SERVER` span kind, the semconv metric name, and coverage for calls to
  unroutable methods. Tracked on the ripcurl side; noted here because mocapi
  is the motivating consumer of that module's seam.

## Protocol & features

- **Explicit tool `inputSchema` support.** Today schemas are generated from
  Java types (ADR-0016); a path to supply a rich 2020-12 `inputSchema`
  directly, surfaced by the json-schema conformance check.
- **Handler API to require an arbitrary client capability** → `-32021`
  (missing-capability), surfaced by the missing-capability conformance check.
- **Native-image CI harness** — native profile + GraalVM job + an elicitation
  round-trip in CI, so the AOT hints stay verified (currently checked manually).

## Candidate (unresolved calls — maintainer to decide)

- Additional transports.
- Extension-track stance (Tasks / MCP Apps) revisits — declined for 1.0
  (ADR-0022); reopen if demand or the spec's extension model firms up.
- Promotion of `HeaderMismatch` into `mocapi-model` — architecturally
  significant, needs its own ADR before any code.

## How items move

A **Should-fix** or agreed feature gets a superpowers plan under
[`plans/`](plans/) (and an ADR first if it's architecturally significant — see
the ADR rule in the project `CLAUDE.md`) before code. This file is the index of
intent, not the design.
