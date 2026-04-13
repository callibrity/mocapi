# Remove CapturingTransport in favor of Mockito mocks

## What to build

Delete `CapturingTransport` and replace all usages with `mock(McpTransport.class)`
and Mockito `ArgumentCaptor`. This eliminates a custom test utility in favor of
standard Mockito patterns that every Java developer already knows.

### How to replace

`CapturingTransport` is used in two patterns:

**Pattern 1 — Single message capture (same as existing compliance tests):**
```java
// Before
var transport = new CapturingTransport();
server.handleCall(ctx, call, transport);
var result = transport.messages().getFirst();

// After
var transport = mock(McpTransport.class);
server.handleCall(ctx, call, transport);
var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
verify(transport).send(captor.capture());
var result = captor.getValue();
```

**Pattern 2 — Multiple message capture (interactive tools, progress, logging):**
```java
// Before
var transport = new CapturingTransport();
// ... invoke tool ...
var messages = transport.messages();
assertThat(messages).hasSizeGreaterThanOrEqualTo(3);

// After
var transport = mock(McpTransport.class);
// ... invoke tool ...
var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
verify(transport, atLeast(3)).send(captor.capture());
var messages = captor.getAllValues();
```

For `emit(McpEvent)` captures, use a separate `ArgumentCaptor.forClass(McpEvent.class)`
with `verify(transport).emit(captor.capture())`.

### Files to update

- `McpToolsServiceTest.java` — 1 usage
- `DefaultMcpToolContextTest.java` — 7 usages
- `DefaultMcpServerTest.java` — 1 usage (field)
- `ToolsCallInteractiveComplianceTest.java` — 4 usages
- `CapturingTransport.java` — DELETE this file

## Acceptance criteria

- [ ] `CapturingTransport.java` is deleted
- [ ] No references to `CapturingTransport` remain in the codebase
- [ ] All tests that previously used `CapturingTransport` now use
      `mock(McpTransport.class)` with `ArgumentCaptor`
- [ ] All existing tests still pass
- [ ] No new test utilities are introduced — use only standard Mockito

## Implementation notes

- `ComplianceTestSupport` already has `captureMessage`, `captureResult`,
  `captureError`, and `captureEvent` helpers that use this exact mock + captor
  pattern. Reuse those where possible.
- For tests that need to capture multiple `send()` calls, use
  `verify(transport, atLeast(N)).send(captor.capture())` and
  `captor.getAllValues()`.
- Some tests check both `send()` and `emit()` on the same transport — use
  separate captors for each.
