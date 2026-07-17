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

## 2026-07-15 update — spec renumbered its codes off the low band

Upstream renumbered the spec-defined error codes (commits `f505a6c7` +
`73ab7d2f`, still protocol version `2026-07-28`). The implementation-defined
server-error range is now explicitly partitioned: `-32000` to `-32019` stays
implementation-defined (grandfathered SDK usage), and `-32020` to `-32099`
is reserved for spec-defined errors. Consequently the spec-defined codes moved
out of the low band:

- `MissingRequiredClientCapabilityError`: `-32003` → **`-32021`**
- `UnsupportedProtocolVersionError`: `-32004` → **`-32022`**
- `HeaderMismatch`: `-32001` → **`-32020`**

`-32003` is therefore **no longer spec-claimed** — the original collision
that motivated relocating guard denial is gone. The decision nonetheless
stands: **guard denial remains `-32010`**, which sits squarely inside the
implementation-defined sub-range (`-32000` to `-32019`) that the spec has now
formally reserved for implementations. No re-relocation is warranted; keeping
`-32010` stable avoids churn and stays clear of the spec-reserved band. Status
remains **Accepted**.

See `docs/plans/2026-07-28-schema-diff.md` (§ "2026-07-15 re-diff") for the
full re-diff.

**Code anchors:** `mocapi-server/src/main/java/com/callibrity/mocapi/server/JsonRpcErrorCodes.java`, `mocapi-server/src/main/java/com/callibrity/mocapi/server/guards/GuardEvaluationInterceptor.java`.
