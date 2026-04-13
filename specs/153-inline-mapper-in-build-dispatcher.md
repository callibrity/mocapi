# Inline MAPPER constant in ComplianceTestSupport.buildDispatcher

## What to build

Remove the `ObjectMapper mapper` parameter from `ComplianceTestSupport.buildDispatcher()`
and use the `MAPPER` constant directly. IntelliJ reports the parameter is always
`ComplianceTestSupport.MAPPER` at every call site.

### Change

```java
// Before
static JsonRpcDispatcher buildDispatcher(ObjectMapper mapper, Object... services) {
    var invokerFactory =
        new DefaultMethodInvokerFactory(
            List.of(
                new McpSessionResolver(),
                new McpTransportResolver(),
                new JsonRpcParamsResolver(mapper)));
    var factory = new DefaultAnnotationJsonRpcMethodProviderFactory(mapper, invokerFactory);
    ...
}

// After
static JsonRpcDispatcher buildDispatcher(Object... services) {
    var invokerFactory =
        new DefaultMethodInvokerFactory(
            List.of(
                new McpSessionResolver(),
                new McpTransportResolver(),
                new JsonRpcParamsResolver(MAPPER)));
    var factory = new DefaultAnnotationJsonRpcMethodProviderFactory(MAPPER, invokerFactory);
    ...
}
```

Update all call sites within `ComplianceTestSupport` to drop the `MAPPER` argument.

## Acceptance criteria

- [ ] `buildDispatcher` no longer takes an `ObjectMapper` parameter
- [ ] All call sites updated
- [ ] `ObjectMapper` import can be removed from `ComplianceTestSupport` if no longer needed
- [ ] All existing tests still pass

## Implementation notes

- The only call site is inside `ComplianceTestSupport.buildServer()` at the line
  `var dispatcher = buildDispatcher(MAPPER, allServices)`.
- This is a one-line fix — just remove the parameter and update the call site.
