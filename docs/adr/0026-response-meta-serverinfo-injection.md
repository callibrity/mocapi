# ADR-0026 — Response-`_meta` injection seam; `serverInfo` SHOULD adherence

- **Status:** Accepted
- **Date:** 2026-07-17

## Context

MCP 2026-07-28 PR #3002 reshaped the `_meta` envelope on both sides of
the wire. On the request side, `clientInfo` moved from REQUIRED to
OPTIONAL in `_meta` — a client may omit it, leaving `protocolVersion`
and `clientCapabilities` as the only required keys
(`MetaEnvelopeParser` already tolerates a missing/null `clientInfo`).
On the response side, the spec now says servers SHOULD include
`io.modelcontextprotocol/serverInfo` in the `_meta` of every successful
response, and — as a consequence — `DiscoverResult` no longer carries
a top-level `serverInfo` field; identity is conveyed exclusively
through the response `_meta` envelope, the same way on every method,
not just `server/discover`.

Before this change, `mocapi-server` had no general mechanism for
stamping server-controlled keys into a response's `_meta` after a
handler returns its result — handlers built their own `_meta`, if any,
and `DefaultMcpServer` forwarded the JSON-RPC response unmodified. A
SHOULD-level, cross-cutting key like `serverInfo` doesn't belong in
every handler; it belongs at the one place that sees every successful
response before it reaches the transport.

## Decision

`DefaultMcpServer.handleCall` is the canonical seam for injecting
server-side `_meta` keys into outgoing responses. The rule for that
seam:

- Runs once, after `dispatcher.dispatch(call)` returns and before
  `transport.send(...)` — transport-agnostic, so both Streamable HTTP
  and stdio get it for free.
- Applies only to successful `JsonRpcResult` responses whose `result`
  is a JSON object; JSON-RPC errors and non-object results pass
  through untouched.
- Merges into the existing `_meta` object (`withObjectProperty`
  creates one if absent) and never clobbers a key the handler already
  set — `if (!meta.has(key))` before `set(...)`.

Mocapi adopts the `serverInfo` SHOULD by default: `DefaultMcpServer`
stamps `McpMetaKeys.SERVER_INFO` (`io.modelcontextprotocol/serverInfo`)
with the configured `Implementation` into every successful response's
`_meta`, unless a handler already populated that key. This is
default-on with an opt-out: the `mocapi.emit-server-info` property
(bound from the `mocapi`-prefixed `@ConfigurationProperties` record's
`emitServerInfo` field) defaults to `true`; setting it `false` disables
the injection entirely.

The `clientInfo`-optional request change and the removal of
`DiscoverResult.serverInfo` are spec-tracking consequences of PR #3002,
not separate decisions — they follow directly from `serverInfo` moving
to the response `_meta` as the single source of server identity.

## Consequences

Future server-side `_meta` keys that must appear on every successful
response (not just specific handlers) have one obvious place to live:
`DefaultMcpServer`'s injection step, following the same merge-don't-clobber,
successful-results-only, transport-agnostic rules. Handlers remain free
to set their own `_meta` keys directly on their result; the seam only
fills gaps, it never overrides handler-set values.

Operators who don't want `serverInfo` on the wire (bandwidth-sensitive
transports, custom identity conventions) set
`mocapi.emit-server-info=false`. Clients that depended on
`DiscoverResult.serverInfo` as a top-level field must instead read
`_meta["io.modelcontextprotocol/serverInfo"]` — acceptable pre-1.0 and
consistent with the clean-break precedent (ADR-0019).

**Code anchors:** `mocapi-server/src/main/java/com/callibrity/mocapi/server/DefaultMcpServer.java`, `mocapi-model/src/main/java/com/callibrity/mocapi/model/McpMetaKeys.java`, `mocapi-model/src/main/java/com/callibrity/mocapi/model/DiscoverResult.java`.
