# Track MCP 2026-07-28 PR #3002 (clientInfo optional + serverInfo response metadata) — Design

**Date:** 2026-07-17
**Status:** Approved (brainstorming), pending spec review
**Topic:** Conform mocapi to upstream schema change `71e30695` (PR #3002),
landed 2026-07-16, after our pinned snapshot `9a4ff8af`.

## Goal

Track the two changes in PR #3002 so mocapi stays compliant with the current
MCP 2026-07-28 draft:

1. **`io.modelcontextprotocol/clientInfo` is now OPTIONAL** in the request
   `_meta` envelope (was required). — a conformance **fix**: mocapi currently
   rejects valid requests that omit it.
2. **New `io.modelcontextprotocol/serverInfo` in `ResultMetaObject`** — servers
   SHOULD include their `Implementation` in **every** response's `_meta`. — a
   SHOULD-level behavior mocapi does not yet do (we adhere to SHOULD-level).

## Context (current state)

- `MetaEnvelopeParser` requires all three envelope keys and throws `-32602`
  when `clientInfo` is absent (`MetaEnvelopeParser.java:81`). The parser binds
  `clientInfo` into `McpExchange`, consumed by `AuditLoggingInterceptor`,
  `McpMdcInterceptor` (logging), and `McpObservationFilter` (o11y).
- mocapi emits `serverInfo` only inside `DiscoverResult`; ordinary responses
  carry no `serverInfo`, and the base `Result` model does not carry `_meta`
  (most result records are bare, e.g. `EmptyResult(resultType)`).
- The response path has a single central seam: `DefaultMcpServer.handleCall`
  runs `dispatcher.dispatch(call)` to produce one `JsonRpcResponse`, then calls
  `transport.send(response)`. The `Implementation` serverInfo is an existing
  bean (used by `DiscoverHandler`).

## Decisions (from brainstorming)

- `clientInfo` becomes optional; if present it must still be well-formed
  (name + version).
- `serverInfo` emission is **default-on with an opt-out property**
  (`mocapi.emit-server-info`, default `true`), matching the spec's
  "unless specifically configured not to."
- Inject `serverInfo` at the **central response seam** (one place), not by
  modeling `_meta` on every result type.
- This change warrants **one short ADR** — scoped to the durable decisions
  (the response-`_meta` injection seam as a reusable pattern, and the
  SHOULD-adherence stance) — not a ceremonial "implemented PR #3002" record.

## Design

### Part 1 — `clientInfo` optional (conformance fix)

- `MetaEnvelopeParser`: parse `clientInfo` only if the key is present. Absent →
  `McpExchange.clientInfo()` is `null`. Present → keep the existing "name and
  version required" validation (malformed still → `-32602`). Required envelope
  keys become `protocolVersion` + `clientCapabilities` only.
- Null-safety in the three consumers — emit the clientInfo-derived fields only
  when `clientInfo` is non-null:
  - `AuditLoggingInterceptor` (audit fields)
  - `McpMdcInterceptor` (MDC keys)
  - `McpObservationFilter` (observation tags)
- Tests: (a) request omitting `clientInfo` → dispatch succeeds, no `-32602`;
  (b) `clientInfo` present but missing name/version → still `-32602`;
  (c) consumers do not NPE when `clientInfo` is null.

### Part 2 — `serverInfo` on every response (`SHOULD`, default-on opt-out)

- New constant `McpMetaKeys.SERVER_INFO = "io.modelcontextprotocol/serverInfo"`.
- New config property `mocapi.emit-server-info` (default `true`) on the
  server properties; wired through autoconfigure.
- **Injection seam:** in `DefaultMcpServer.handleCall`, after
  `dispatcher.dispatch(call)` yields the successful `JsonRpcResponse` and before
  `transport.send`, merge `SERVER_INFO → <Implementation>` into the result's
  `_meta` object node. Rules:
  - Only successful results (a `JsonRpcResponse` result) — never `JsonRpcError`,
    never notifications.
  - **Merge, do not clobber:** if the handler already set `_meta`, add the
    serverInfo key alongside; if the key is somehow already present, leave the
    handler's value.
  - Skipped entirely when `emit-server-info` is `false`.
  - The seam is transport-agnostic (lives in the server core, not a transport).
- Tests: (a) every successful result carries `_meta.serverInfo` with the bean's
  name/version; (b) error responses carry none; (c) `emit-server-info=false`
  disables it; (d) a handler-set `_meta` entry is preserved alongside serverInfo.

### Part 3 — ADR + docs

- **New ADR** (next number): record the response-`_meta` injection seam as the
  canonical place to add server-side `_meta` keys, and the decision to adhere to
  the `serverInfo` SHOULD by default with an opt-out. Note the `clientInfo`
  -optional change as spec-tracking (no separate decision). Add it to the ADR
  index; link it from constitution invariant **I3** (spec-compliance target).
- **Design doc:** update the doc that describes the `_meta` envelope / request
  flow (`docs/design/architecture-overview.md` or `transports.md`) to state the
  new required/optional envelope keys and the serverInfo emission.
- **Schema snapshot:** re-pin `docs/plans/2026-07-28-schema.{ts,json}` to
  upstream `71e30695`; add a dated re-diff note to
  `docs/plans/2026-07-28-schema-diff.md` recording both PR #3002 changes.
- **Process doc (over-documenting, per maintainer):** add a short "spec vs ADR —
  when each" note to `docs/superpowers/README.md` so the judgment call
  (build-document vs durable citable decision) is written down.

## Success criteria

1. A request whose `_meta` omits `clientInfo` is processed normally (no
   `-32602`); a malformed `clientInfo` still fails with `-32602`.
2. Every successful response carries `io.modelcontextprotocol/serverInfo` in
   `_meta` by default; error responses do not; `mocapi.emit-server-info=false`
   turns it off; handler-set `_meta` is preserved.
3. Logging/audit/o11y degrade gracefully when `clientInfo` is absent.
4. The snapshot is re-pinned to `71e30695` with a dated diff note; an ADR
   records the injection-seam + SHOULD decision and is indexed + linked from the
   constitution.
5. `mvn verify` is green.

## Out of scope

- Modeling `_meta` on every result type (rejected — the central seam is DRYer).
- Emitting other optional `_meta` keys (only `serverInfo` is in scope).
- The two earlier cleanup plans (transport-consistency, ADR-0002) — separate
  roadmap items.
