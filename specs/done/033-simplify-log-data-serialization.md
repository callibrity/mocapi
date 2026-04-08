# Simplify log data serialization

## What to build

Remove unnecessary `instanceof String` special case in log notification building.
`objectMapper.valueToTree(data)` handles all types including strings.

### Fix

Replace:

```java
if (data instanceof String s) {
    params.put("data", s);
} else {
    params.set("data", objectMapper.valueToTree(data));
}
```

With:

```java
params.set("data", objectMapper.valueToTree(data));
```

## Acceptance criteria

- [ ] No `instanceof String` check in log notification building
- [ ] String, object, array, and null data all serialize correctly
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- One-line fix. Check `DefaultMcpStreamContext` or wherever the log notification
  is built.
