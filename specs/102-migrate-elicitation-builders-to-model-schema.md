# Migrate elicitation builders to produce model schema records

## What to build

The fluent elicitation builders in
`mocapi-core/.../stream/elicitation/` currently build their own parallel
hierarchy of JSON-Schema-shaped records (`StringPropertySchema`,
`NumberPropertySchema`, `IntegerPropertySchema`, `BooleanPropertySchema`,
`EnumPropertySchema`, `TitledEnumPropertySchema`,
`MultiSelectPropertySchema`, `TitledMultiSelectPropertySchema`,
`LegacyEnumPropertySchema`, `EnumItemsSchema`, `TitledEnumItemsSchema`,
`EnumOption`, and the `PropertySchema` parent interface). These are
duplicates of the canonical schema types already in `mocapi-model`:
`StringSchema`, `NumberSchema`, `BooleanSchema`,
`UntitledSingleSelectEnumSchema`, `TitledSingleSelectEnumSchema`,
`UntitledMultiSelectEnumSchema`, `TitledMultiSelectEnumSchema`,
`LegacyTitledEnumSchema`, `EnumItemsSchema`, `TitledEnumItemsSchema`,
`EnumOption`, all united under the sealed
`PrimitiveSchemaDefinition` interface that `RequestedSchema.properties`
already uses.

Migrate each property builder so its `build()` method returns the model
type directly, then delete the core duplicates. Rework `ElicitationSchema`
so it no longer hand-builds an `ObjectNode` — it produces a typed
`RequestedSchema` that flows straight into `ElicitRequestFormParams`
(which already declares `requestedSchema` as a `RequestedSchema`).

The **fluent builder API remains** — tool authors still call
`schema -> schema.string("name", "desc").integer(...).choose(...)`. Only
the return type of the builders' internal `build()` methods changes,
along with the records they produce.

## Scope

### Property builders (kept, return types changed)

| Builder | Current `build()` returns | New `build()` returns |
|---|---|---|
| `StringPropertyBuilder` | `StringPropertySchema` | `com.callibrity.mocapi.model.StringSchema` |
| `IntegerPropertyBuilder` | `IntegerPropertySchema` | `com.callibrity.mocapi.model.NumberSchema` with `type = "integer"` |
| `NumberPropertyBuilder` | `NumberPropertySchema` | `com.callibrity.mocapi.model.NumberSchema` with `type = "number"` |
| `BooleanPropertyBuilder` | `BooleanPropertySchema` | `com.callibrity.mocapi.model.BooleanSchema` |
| `ChooseOneBuilder<T>` | `EnumPropertySchema` / `TitledEnumPropertySchema` | `com.callibrity.mocapi.model.UntitledSingleSelectEnumSchema` or `TitledSingleSelectEnumSchema` (same single/titled discrimination as today) |
| `ChooseManyBuilder<T>` | `MultiSelectPropertySchema` / `TitledMultiSelectPropertySchema` | `com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema` or `TitledMultiSelectEnumSchema` |
| `ChooseLegacyBuilder` | `LegacyEnumPropertySchema` | `com.callibrity.mocapi.model.LegacyTitledEnumSchema` |

### Core records to delete

After migration, delete these files (they exist only as duplicates of
model types):

- `PropertySchema.java` (sealed parent interface)
- `StringPropertySchema.java`
- `IntegerPropertySchema.java`
- `NumberPropertySchema.java`
- `BooleanPropertySchema.java`
- `EnumPropertySchema.java`
- `TitledEnumPropertySchema.java`
- `MultiSelectPropertySchema.java`
- `TitledMultiSelectPropertySchema.java`
- `LegacyEnumPropertySchema.java`
- `EnumItemsSchema.java` (core duplicate)
- `TitledEnumItemsSchema.java` (core duplicate)
- `EnumOption.java` (core duplicate)

### `ElicitationSchema` rework

- Internal storage changes from `Map<String, PropertySchema>` to
  `Map<String, PrimitiveSchemaDefinition>` + a separate `List<String>
  requiredNames` maintained by the parent `ElicitationSchemaBuilder`.
- A new method `toRequestedSchema()` returns a model
  `RequestedSchema(properties, required)` directly. The old
  `toObjectNode(ObjectMapper)` method is deleted — no more hand-rolled
  `ObjectNode`.
- `DefaultMcpStreamContext.elicit(...)` calls the new
  `toRequestedSchema()` and passes the typed record into
  `ElicitRequestFormParams`.

### `required` tracking

Model `PrimitiveSchemaDefinition` variants have no `required` flag —
required names live on `RequestedSchema.required` (a `List<String>`).
The property builders each already expose `.optional()` which flips a
`required = true` default. Preserve that builder-level API but track
required names in `ElicitationSchemaBuilder` / `ElicitationSchema`
instead of on the produced schema records.

### Prerequisite model fix — add `pattern` to `StringSchema`

The MCP elicitation spec
(https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation#requested-schema)
includes `pattern` as a valid field on a string primitive schema. The
canonical example from the spec docs is:

```json
{
  "type": "string",
  "title": "Display Name",
  "description": "Description text",
  "minLength": 3,
  "maxLength": 50,
  "pattern": "^[A-Za-z]+$",
  "format": "email",
  "default": "user@example.com"
}
```

`mocapi-model`'s current `StringSchema` record does **not** have a
`pattern` field — this is a pre-existing gap in the spec mirror. Fix it
as the **first step** of this migration, before touching any builder:

- Add `String pattern` to
  `com.callibrity.mocapi.model.StringSchema`, positioned between
  `maxLength` and `format` to match the spec example's field order.
- Add a unit test in `mocapi-model` that round-trips a `StringSchema`
  with a non-null `pattern` field and asserts the serialized JSON
  contains the `"pattern"` key.
- `@JsonInclude(NON_NULL)` is already on the record — a null pattern
  is omitted on the wire, preserving compatibility with existing
  `StringSchema` instances that don't set it.

### Field mapping gotchas

- **`StringPropertyBuilder.pattern(String)`**: the builder method
  **stays**. Once `StringSchema` has a `pattern` field (prerequisite
  above), map the builder's value directly into `StringSchema.pattern`.
  The existing tool-author API is preserved.
- **`StringPropertyBuilder.format(String)`**: core stores format as a
  free-form `String`; model's `StringSchema.format` is typed
  `StringFormat` (enum). In the builder, keep the shorthand methods
  (`email()`, `uri()`, `date()`, `dateTime()`), but have them set a
  `StringFormat` value internally rather than a raw string. Remove any
  `format(String)` overload that accepts arbitrary strings — the enum
  enforces the spec's closed set.
- **`IntegerPropertyBuilder` vs `NumberSchema`**: model's `NumberSchema`
  has a `String type` field that can be `"integer"` or `"number"`. The
  integer builder produces a `NumberSchema` with
  `type = "integer"`; the number builder produces one with
  `type = "number"`. Jackson will serialize identically regardless.

### `ElicitationSchemaValidator`

If still referenced **only** from the bean-elicit path (which spec 100
is removing), delete `ElicitationSchemaValidator.java` along with its
test as part of this spec. If any caller outside bean elicit remains
after spec 100 has landed, leave it alone and migrate it to operate on
the typed `RequestedSchema` in a follow-up spec.

## Acceptance criteria

- [ ] Every builder in
      `mocapi-core/src/main/java/com/callibrity/mocapi/stream/elicitation/`
      that has a `build()` method now returns a model type from
      `com.callibrity.mocapi.model` (never a core-local schema type).
- [ ] Every core-local schema record listed in the "Core records to
      delete" section is removed from `mocapi-core`.
- [ ] `ElicitationSchema` holds `Map<String, PrimitiveSchemaDefinition>`
      and exposes a `toRequestedSchema()` method returning a
      `com.callibrity.mocapi.model.RequestedSchema`. The
      `toObjectNode(ObjectMapper)` method is deleted.
- [ ] `DefaultMcpStreamContext.elicit(String,
      Consumer<ElicitationSchemaBuilder>)` passes a `RequestedSchema`
      (not an `ObjectNode`) into the request construction path.
- [ ] `com.callibrity.mocapi.model.StringSchema` has a new `String
      pattern` field matching the MCP spec. A unit test in
      `mocapi-model` round-trips a `StringSchema` with a non-null
      pattern and asserts the JSON round-trip preserves it.
- [ ] `ElicitationSchemaBuilder`'s fluent API is **source-compatible**
      for the supported subset: every method signature a tool author
      might call (`string`, `integer`, `number`, `bool`, `choose`,
      `chooseMany`, `chooseLegacy`) still exists with the same name and
      parameters. `StringPropertyBuilder.pattern(String)` is retained
      and now maps into the new `StringSchema.pattern` field. The only
      removal is any free-form `format(String)` setter — the shorthand
      methods continue to work via the typed `StringFormat` enum.
- [ ] `StringPropertyBuilder` still provides the `email()`, `uri()`,
      `date()`, `dateTime()` shorthand methods; they set a typed
      `StringFormat` value internally.
- [ ] The `required` marker is preserved: a builder constructed with
      default settings still results in its property name appearing in
      the final `RequestedSchema.required` list, and calling
      `.optional()` on the property builder excludes it.
- [ ] Wire-format compatibility is preserved: the JSON emitted for a
      given builder invocation is byte-equivalent to what was emitted
      before this migration, *except* for fields that were not in the
      MCP spec (e.g., `pattern`, if confirmed removed). Add a
      regression test that constructs a non-trivial schema via the
      builders and asserts its serialized JSON matches a golden
      fixture.
- [ ] Existing `DefaultMcpStreamContextTest` tests for the
      builder-based `elicit(...)` path pass unchanged.
- [ ] `mocapi-compat`'s `ConformanceTools` compiles (it uses
      `ctx.elicit(...)` with the builder — signatures must still
      match). Conformance suite still passes 39/39.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- Ordering: this spec should run **after** spec 098 (elicit wire
  migration to model records) and **after** spec 100 (remove bean
  elicit) have landed. Spec 098 wires `ElicitRequestFormParams` into
  the elicit flow; spec 100 removes the bean-elicit code paths that
  would otherwise complicate this migration; this spec then replaces
  the remaining hand-rolled schema types with model types.
- Recommended commit granularity (for bisectability):
  1. Add `pattern` field to `mocapi-model/StringSchema` plus its
     round-trip test. This is the prerequisite and must land first so
     subsequent commits can map `StringPropertyBuilder.pattern(...)`
     into it.
  2. `StringPropertyBuilder` → `StringSchema`, delete
     `StringPropertySchema`.
  3. `IntegerPropertyBuilder` → `NumberSchema(type="integer")`, delete
     `IntegerPropertySchema`.
  4. `NumberPropertyBuilder` → `NumberSchema(type="number")`, delete
     `NumberPropertySchema`.
  5. `BooleanPropertyBuilder` → `BooleanSchema`, delete
     `BooleanPropertySchema`.
  6. `ChooseOneBuilder` (untitled + titled variants) → model single-select
     schemas, delete core `EnumPropertySchema` and
     `TitledEnumPropertySchema`.
  7. `ChooseManyBuilder` (untitled + titled variants) → model
     multi-select schemas, delete core `MultiSelectPropertySchema` and
     `TitledMultiSelectPropertySchema`.
  8. `ChooseLegacyBuilder` → `LegacyTitledEnumSchema`, delete core
     `LegacyEnumPropertySchema`.
  9. Delete core `EnumItemsSchema` / `TitledEnumItemsSchema` /
     `EnumOption` once no core callers remain.
  10. `ElicitationSchema` rework: change storage and add
      `toRequestedSchema()`, delete `toObjectNode()`; update
      `DefaultMcpStreamContext.elicit` to use the new method.
  11. If `ElicitationSchemaValidator` has no remaining callers after
      spec 100, delete it and its test.
- Before deleting each core schema record, grep the entire reactor for
  direct references — prompts, tools, resources, and stream-context
  code should not depend on any of them, but verify.
- The golden-JSON regression test should exercise every primitive
  type plus at least one single-select, one multi-select, and one
  legacy enum, with a mix of required and optional fields and at least
  one `defaultValue` per primitive type. Keep the fixture small enough
  to eyeball, but comprehensive enough that a field-ordering or
  missing-field regression would fail loudly.
- Do not move the fluent builders themselves into `mocapi-model` —
  they're a mocapi-core ergonomic layer. Only the produced record
  types move to (/ are replaced by) the model equivalents.
- `ElicitationAction` in `mocapi-core` is distinct from model's
  `ElicitAction`. This spec does not touch either — that's a possible
  future cleanup but out of scope here.
