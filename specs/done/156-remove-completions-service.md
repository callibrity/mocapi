# Remove McpCompletionsService

## What to build

Delete `McpCompletionsService` and its bean registration. We don't provide any
actual completions — the handler always returns an empty list. Rather than
advertising an unused capability, just don't declare it. Clients MUST only use
negotiated capabilities, so a well-behaved client will never send
`completion/complete` if we don't advertise it. If one does, `-32601` (method
not found) is appropriate.

### Delete

- `McpCompletionsService.java` in `com.callibrity.mocapi.server.completions`
- Its bean definition in `MocapiServerAutoConfiguration`
- Any tests for `McpCompletionsService` or `completion/complete`
  (check `CompletionComplianceTest` and any unit tests)
- Remove unused imports from `MocapiServerAutoConfiguration`

### Keep

- `CompleteRequestParams`, `CompleteResult`, `Completion`, `CompletionsCapability`
  in mocapi-model — these are model types that may be useful later if we
  re-introduce completions. Check if anything else references them; if not,
  they can be deleted too but it's not required.
- `McpMethods.COMPLETION_COMPLETE` constant — harmless to keep.

## Acceptance criteria

- [ ] `McpCompletionsService` is deleted
- [ ] No bean definition for completions in auto-configuration
- [ ] Server capabilities no longer include `completions`
- [ ] Related tests deleted or updated
- [ ] All existing tests still pass

## Implementation notes

- `McpCompletionsService` is at
  `mocapi-server/src/main/java/com/callibrity/mocapi/server/completions/McpCompletionsService.java`
- Bean is in `MocapiServerAutoConfiguration` — look for `mcpProtocolCompletionsService`
- `CompletionComplianceTest` is at
  `mocapi-server/src/test/java/com/callibrity/mocapi/server/compliance/CompletionComplianceTest.java`
