# Rename McpLifecycleEvent → McpEvent (cleanup)

## What to build

If `McpLifecycleEvent` still exists anywhere after the prior
specs, rename it to `McpEvent`. If the protocol module already
uses `McpEvent` (as specified in 148a), this spec is a no-op —
just verify and close.

## Acceptance criteria

- [ ] No class named `McpLifecycleEvent` exists in the codebase.
- [ ] `McpEvent` is the sealed interface in `mocapi-protocol`.
- [ ] `mvn verify` passes.
