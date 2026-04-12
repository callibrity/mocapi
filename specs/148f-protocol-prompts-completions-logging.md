# Protocol prompts, completions, and logging support

## What to build

Add the remaining MCP method handlers to the protocol module.

### What to add

1. **`McpPromptsService`** — manages prompt descriptors and
   pagination.

2. **Register prompt methods**:
   - `prompts/list`
   - `prompts/get`

3. **Completion handler**:
   - `completion/complete`

4. **Logging handler**:
   - `logging/setLevel` — updates the session's log level via
     `McpSessionService`.

5. **Ping handler**:
   - `ping` — returns empty result.

### What NOT to do

- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `McpPromptsService` exists in `mocapi-protocol`.
- [ ] All prompt, completion, logging, and ping methods dispatch.
- [ ] Tests use `CapturingTransport` — no HTTP.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
