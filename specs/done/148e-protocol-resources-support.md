# Protocol resources support

## What to build

Add resource registration and dispatch to the protocol module.

### What to add

1. **`McpResourcesService`** — manages resource descriptors,
   resource templates, pagination, and subscriptions.

2. **Register resource methods** with the `JsonRpcDispatcher`:
   - `resources/list`
   - `resources/read`
   - `resources/templates/list`
   - `resources/subscribe`
   - `resources/unsubscribe`

3. **Resource provider interfaces** — `McpResource`,
   `McpResourceTemplate` for user-defined resources.

### What NOT to do

- Do NOT implement resource change notifications (requires
  a notification channel — transport concern). The subscribe/
  unsubscribe methods register interest but don't deliver
  notifications yet.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `McpResourcesService` exists in `mocapi-protocol`.
- [ ] All five resource methods dispatch correctly.
- [ ] Tests use `CapturingTransport` — no HTTP.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
