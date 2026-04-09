# Warn when using in-memory session store

## What to build

Log a warning when the `InMemoryMcpSessionStore` fallback is auto-configured,
matching the pattern used by Substrate for its in-memory fallbacks.

In `MocapiAutoConfiguration.mcpSessionStore()`, log:

```
No McpSessionStore implementation found; using in-memory fallback (single-node only).
For clustered deployments, provide a McpSessionStore bean.
```

## Acceptance criteria

- [ ] Warning is logged when `InMemoryMcpSessionStore` is created by auto-configuration
- [ ] Message follows the same pattern as Substrate's fallback warnings
- [ ] `mvn verify` passes
