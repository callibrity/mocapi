# Simplify duplicate property check

## What to build

Replace scattered `requireUniqueName(name)` calls with a single `addProperty`
helper that checks for duplicates via the `Map.put()` return value.

### Replace requireUniqueName

Delete the `requireUniqueName` method. Add:

```java
private void addProperty(String name, PropertySchema schema) {
    if (properties.put(name, schema) != null) {
        throw new IllegalArgumentException("Duplicate property: " + name);
    }
}
```

All `string()`, `integer()`, `number()`, `bool()`, `choose()`, `chooseMany()`,
`chooseLegacy()` methods call `addProperty(name, builder.build())` instead of
`requireUniqueName(name)` + `properties.put(name, ...)`.

## Acceptance criteria

- [ ] `requireUniqueName` method deleted
- [ ] `addProperty` helper with duplicate check via `put()` return value
- [ ] All builder methods use `addProperty`
- [ ] All tests pass
- [ ] `mvn verify` passes
