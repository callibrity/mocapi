# Extract McpStreamContext interface

## What to build

Refactor `McpStreamContext` from a concrete class into an interface with a separate
implementation class. This improves testability (tool handlers can mock the interface
without needing Odyssey) and decouples the public API from the implementation, which
will grow significantly when elicitation support is added (Substrate Mailbox, schema
generation, etc.).

### Extract interface

Create an `McpStreamContext` interface in `mocapi-core` with the public methods:

```java
public interface McpStreamContext {
    void sendProgress(long progress, long total);
    void sendNotification(String method, Object params);
}
```

The interface lives in `mocapi-core` so that tool modules can depend on it without
pulling in `mocapi-autoconfigure` or Odyssey.

### Rename implementation

Rename the existing `McpStreamContext` class in `mocapi-autoconfigure` to
`DefaultMcpStreamContext` (or similar). It implements the new interface and retains
its current constructor and Odyssey/ObjectMapper dependencies.

### Update detection logic

The controller's method handler introspection currently checks for the
`McpStreamContext` class to decide between JSON and SSE response paths. Update it to
check for the `McpStreamContext` interface instead.

### Update tests

- `McpStreamContextTest` should test `DefaultMcpStreamContext` directly
- Controller tests that mock `McpStreamContext` should mock the interface
- Tool handler tests can mock `McpStreamContext` without any Odyssey dependency

## Acceptance criteria

- [ ] `McpStreamContext` is an interface in `mocapi-core`
- [ ] `McpStreamContext` declares `sendProgress(long, long)` and
      `sendNotification(String, Object)`
- [ ] A concrete implementation exists in `mocapi-autoconfigure` (e.g.,
      `DefaultMcpStreamContext`)
- [ ] The implementation wraps `OdysseyStream` and `ObjectMapper` as before
- [ ] Controller SSE-vs-JSON detection checks for the interface, not the class
- [ ] Tool handler code can depend on `mocapi-core` and reference `McpStreamContext`
      without pulling in Odyssey
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- Moving the interface to `mocapi-core` means tool authors declare
  `McpStreamContext` as a parameter type without a dependency on
  `mocapi-autoconfigure`. This is important because tools are user-authored code
  that shouldn't need to know about Odyssey or Spring SSE internals.
- The elicitation spec (015) will add `elicit()` methods to this interface. Keeping
  it as an interface makes that addition clean — just add methods to the interface
  and implement them in `DefaultMcpStreamContext`.
- The package in `mocapi-core` could be `com.callibrity.mocapi.server` alongside
  `McpServer`, or a new `com.callibrity.mocapi.stream` package. Implementation
  decision.
