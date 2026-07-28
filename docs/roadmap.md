# mocapi Roadmap

Forward direction only — decisions already made live in [`adr/`](adr/),
invariants in [`constitution.md`](constitution.md). Cadence is 0.x: a **minor**
bump carries headline features and may include breaking changes (pre-1.0); a
**patch** is reserved for hotfixes.

This file is maintainer-owned. Lanes marked **TBD** are placeholders to be
filled in — they are not commitments.

## Now
- **Finalize MCP 2026-07-28 compliance.** Re-diff against the final released
  schema and re-pin the snapshot at spec release (migration plan Task 9.3).
- **Transport inbound-consistency cleanup** —
  [plan](superpowers/plans/2026-07-17-transport-inbound-consistency.md).
- **ADR-0002 contract truth-up** —
  [plan](superpowers/plans/2026-07-17-adr-0002-contract-truthup.md).

## Next
- **Jakarta Validation via Methodical** (once it ships): annotate model
  records and map violations to JSON-RPC `-32602`.
- **Demo code + runner script** for the stateless 2026-07-28 example server.
- **Resolve the deferred `HeaderMismatch`-into-`mocapi-model` ADR question**
  (currently sourced transport-side; promotion into the model is an
  architecturally significant call that needs its own ADR).

## Later (TBD — maintainer to fill)
- Path to 1.0.
- Additional transports.
- Extension-track stance (Tasks / MCP Apps) revisits.
