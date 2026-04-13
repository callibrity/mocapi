# Remove dead resource subscription code

## What to build

`McpResourcesService` has subscribe/unsubscribe handlers, a `subscriptions` set, and
an `isSubscribed()` method, but our capabilities declare `subscribe: false`. This is
dead code that should be removed.

### Remove from McpResourcesService

- `@JsonRpcMethod` handler for `resources/subscribe`
- `@JsonRpcMethod` handler for `resources/unsubscribe`
- `private final Set<String> subscriptions` field
- `public boolean isSubscribed(String uri)` method
- Any imports that become unused after removal (e.g., `ConcurrentHashMap`, `Set`,
  `EmptyResult`, `ResourceRequestParams` if no longer used, `McpMethods.RESOURCES_SUBSCRIBE`,
  `McpMethods.RESOURCES_UNSUBSCRIBE`)

### Remove any tests for subscription behavior

Check for tests that exercise subscribe/unsubscribe and remove them. The compliance
test `ResourcesSubscribeComplianceTest` may need to be deleted or reworked to verify
that the server does NOT advertise subscription support.

## Acceptance criteria

- [ ] No subscribe/unsubscribe handlers in `McpResourcesService`
- [ ] No `subscriptions` field or `isSubscribed` method
- [ ] No dead imports
- [ ] Capabilities still declare `ResourcesCapability(false, false)`
- [ ] All existing tests still pass

## Implementation notes

- `McpResourcesService` is at
  `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/McpResourcesService.java`
- `ResourcesSubscribeComplianceTest` is at
  `mocapi-server/src/test/java/com/callibrity/mocapi/server/compliance/ResourcesSubscribeComplianceTest.java`
- `ResourceRequestParams` may still be needed by `resources/read` — check before removing.
