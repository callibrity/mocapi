# Fix elicitation conformance enum values

## What to build

The `elicitation-sep1034-defaults` and `elicitation-sep1330-enums` conformance
tests fail because the enum `const` values are uppercase (`ACTIVE`) but the
conformance client returns lowercase (`active`).

The MCP spec examples use lowercase enum values. The conformance suite's mock
client returns lowercase values matching the spec examples.

### Fix the conformance tools

Update the enum definitions in `ConformanceTools` to produce lowercase values.
Either:

1. Override `toString()` on the enums to return lowercase, and use `toString()`
   for the `const` value in the builder
2. Or add a `valueFn` to the `choose()` call that lowercases the name

Option 2 is simpler and doesn't require changing the builder:

```java
schema.choose("status", DefaultsStatus.class, c ->
    c.defaultValue(DefaultsStatus.ACTIVE));
```

But the `const` values come from `Enum::name`. We need the builder's enum
handling to use `name().toLowerCase()` or let the conformance tool override.

### Recommended fix

The conformance enums should override `toString()` to return lowercase:

```java
enum DefaultsStatus {
    ACTIVE, INACTIVE, PENDING;
    @Override public String toString() { return name().toLowerCase(); }
}
```

Since the builder uses `Enum::name` for `const` and `Object::toString` for
`title`, this would make both `const` and `title` lowercase. But the spec
example has `"active"` as the value, so that's correct.

Wait — actually the builder should use `toString()` for BOTH const and title
when no titleFn is provided, since the user might want lowercase values.
Or better: use a `valueFn` like the arbitrary objects version.

The simplest fix: update the conformance tool enums to have lowercase names
or override toString, and if needed, use the arbitrary objects `choose()`
overload with explicit value/title functions.

### Also check schema validation

The response validation in `DefaultMcpStreamContext` might be too strict.
If the client returns `"active"` and the schema says `"const": "active"`,
it should pass. Verify the validator handles `oneOf` with `const` correctly.

## Acceptance criteria

- [ ] `elicitation-sep1034-defaults` conformance test passes
- [ ] `elicitation-sep1330-enums` conformance test passes (all 5 checks)
- [ ] All existing tests pass
- [ ] `mvn verify` passes
