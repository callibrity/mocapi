# Fix remaining compat IT test failures

## What to build

After 149b fixes the transport deadlock, investigate and fix any
remaining compat IT test failures. Ralph's last run had 25 of 81
failures in several categories.

### Expected categories

1. **Elicitation/sampling timeouts** — should be fixed by 149b
   (the always-SSE + virtual thread fix). Verify.

2. **Empty resource/prompt lists** — Ralph converted providers
   to new interfaces (`McpPromptProvider`, `McpResourceProvider`).
   The auto-configuration may not be scanning for these beans
   correctly. Investigate the auto-config and fix.

3. **Session management edge cases** — verify session validation
   works correctly in the new server module.

4. **Content negotiation / response format** — verify SSE
   responses match what the conformance tests expect.

### Approach

Run `mvn -pl mocapi-compat verify` iteratively. Fix one category
at a time. Do NOT attempt to fix everything in one shot.

## Acceptance criteria

- [ ] `mvn verify` passes across the full reactor.
- [ ] MCP conformance suite: 39/39 against mocapi-compat.
- [ ] **mocapi-core is not modified.**
