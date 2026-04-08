# Rename elicit() to elicitForm()

## What to build

Rename `McpStreamContext.elicit()` to `elicitForm()` to make it explicit that this is
form-mode elicitation. This leaves room for `elicitUrl()` later without a naming
collision.

### Rename methods on `McpStreamContext` interface

```java
// Before
<T> ElicitationResult<T> elicit(String message, Class<T> type);
<T> ElicitationResult<T> elicit(String message, TypeReference<T> type);

// After
<T> ElicitationResult<T> elicitForm(String message, Class<T> type);
<T> ElicitationResult<T> elicitForm(String message, TypeReference<T> type);
```

### Update implementation

Rename the methods in `DefaultMcpStreamContext` (or whatever the implementation class
is called).

### Update all call sites and tests

Rename all references from `elicit(` to `elicitForm(`.

## Acceptance criteria

- [ ] `McpStreamContext` declares `elicitForm()` methods, not `elicit()`
- [ ] Implementation updated
- [ ] All call sites updated
- [ ] All tests updated
- [ ] No references to the old `elicit()` method name remain
- [ ] `mvn verify` passes

## Implementation notes

- Simple rename — no behavior changes.
