# Fix conformance enum field names and add missing formats

## What to build

The elicitation conformance tests fail because of wrong field names and
missing schema formats. Fix the conformance tools to match exactly what
the `@modelcontextprotocol/conformance` suite expects.

### SEP-1034: status enum needs plain enum array

The `elicitation-sep1034-defaults` test expects the `status` property to use
the untitled format: `{"type": "string", "enum": ["active", "inactive", "pending"]}`.

We currently generate `oneOf` with `const`/`title` for all enums. For SEP-1034,
the status field needs the plain `enum` array format. Use the raw strings
`choose()` overload:

```java
schema.choose("status", List.of("active", "inactive", "pending"), "active");
```

### SEP-1330: field names must be camelCase

The conformance suite expects these exact field names:
- `untitledSingle` (not `untitled_single`)
- `titledSingle` (not `titled_single`)
- `legacyEnum` (NEW — not yet implemented)
- `untitledMulti` (not `untitled_multi`)
- `titledMulti` (not `titled_multi`)

### SEP-1330: need all 5 enum variants

1. **untitledSingle**: `{"type": "string", "enum": ["option1", "option2", "option3"]}`
2. **titledSingle**: `{"type": "string", "oneOf": [{"const": "value1", "title": "First Option"}, ...]}`
3. **legacyEnum**: `{"type": "string", "enum": ["opt1", "opt2", "opt3"], "enumNames": ["Option One", "Option Two", "Option Three"]}`
4. **untitledMulti**: `{"type": "array", "items": {"type": "string", "enum": ["option1", "option2", "option3"]}}`
5. **titledMulti**: `{"type": "array", "items": {"anyOf": [{"const": "value1", "title": "First Choice"}, ...]}}`

Variants 1 and 4 (untitled) need the plain `enum` array format, NOT `oneOf`.
Variant 3 (legacy) needs the deprecated `enumNames` format.

### Builder support for plain enum and legacy formats

The builder currently only generates `oneOf`/`anyOf`. We need:

- A way to generate plain `{"type": "string", "enum": [...]}` — the raw strings
  `choose(name, List<String>)` should produce this format instead of `oneOf`
- A way to generate legacy `enumNames` format — add a builder method or let
  the conformance tool build the schema node manually

For the plain enum format, change `choose(name, List<String>)` to generate
`enum` array (not `oneOf`) since there are no titles. For titled enums, keep
`oneOf`.

For legacy `enumNames`, add a method to the builder:
```java
chooseLegacy(String name, List<String> values, List<String> displayNames)
```

Or let the conformance tool construct the `ObjectNode` manually and add it
via a raw property method (escape hatch for conformance only).

### Also update the IT tests

The IT tests in `ElicitationSep1330EnumsIT` need to match the new field names.

## Acceptance criteria

- [ ] SEP-1034: `status` uses plain `enum` array with lowercase values
- [ ] SEP-1330: all 5 field names match camelCase expectations
- [ ] SEP-1330: untitled formats use plain `enum` array (not `oneOf`)
- [ ] SEP-1330: titled formats use `oneOf`/`anyOf` with `const`/`title`
- [ ] SEP-1330: legacy format uses `enum` + `enumNames`
- [ ] `elicitation-sep1034-defaults` conformance test passes (5/5)
- [ ] `elicitation-sep1330-enums` conformance test passes (5/5)
- [ ] IT tests updated to match new field names
- [ ] All tests pass
- [ ] `mvn verify` passes
