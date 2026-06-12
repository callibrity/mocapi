# ADR-0023 — Guard denial moves to `-32010`; spec claims `-32003`/`-32004`

- **Status:** Accepted — amends [ADR-0012](0012-guard-spi.md)
- **Date:** 2026-06-11

## Context

Since [ADR-0012](0012-guard-spi.md), a guard denial has surfaced as
JSON-RPC error `-32003` with message `"Forbidden: <reason>"`. That code
was chosen freely from JSON-RPC 2.0's implementation-defined
server-error range (`-32000` to `-32099`).

MCP 2026-07-28 now assigns meanings inside that same range
([ADR-0019](0019-clean-break-2026-07-28.md)): `-32003` is
`MissingRequiredClientCapabilityError` (the server needs a capability
the client did not declare in `clientCapabilities`) and `-32004` is
`UnsupportedProtocolVersionError`. Both carry spec-defined `data`
shapes. Additionally, `-32001` is used by the Streamable HTTP transport
prose for `HeaderMismatch`, and `-32002` historically meant
resource-not-found (this revision moves that to `-32602`).

Keeping guard denial on `-32003` would make an authorization denial
indistinguishable from a missing-client-capability error — same code,
different (and now spec-mandated) `data` payload. A 2026-07-28 client
is entitled to parse `error.data.requiredCapabilities` out of any
`-32003` it receives.

## Decision

Mocapi-private error codes start at `-32010`, leaving `-32000` to
`-32009` to the spec and transport prose (current and future). Guard
denial moves from `-32003` to `-32010`. The message contract
(`"Forbidden: <reason>"`) and all Guard SPI semantics from ADR-0012 are
unchanged — only the numeric code moves.

The spec-defined codes are referenced via their model-layer constants
(`MissingRequiredClientCapabilityErrorData.CODE`,
`UnsupportedProtocolVersionErrorData.CODE`); mocapi defines no
duplicate constants for them.

## Consequences

Clients keying on `-32003` to detect a guard denial must switch to
`-32010` — acceptable pre-1.0 and consistent with the clean break
(ADR-0019: no current consumers). Authorization denials and
missing-capability errors are now unambiguous on the wire. ADR-0012
remains the authority on Guard semantics; this ADR amends only the
error-code choice.

**Code anchors:** `mocapi-server/src/main/java/com/callibrity/mocapi/server/JsonRpcErrorCodes.java`, `mocapi-server/src/main/java/com/callibrity/mocapi/server/guards/GuardEvaluationInterceptor.java`.
