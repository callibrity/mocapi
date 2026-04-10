# Add intermediate sealed enum schema interfaces to match schema.ts

## What to build

The MCP `schema/2025-11-25/schema.ts` file defines `PrimitiveSchemaDefinition`
as a **two-level sealed hierarchy** with three intermediate union types
that group the enum variants:

```typescript
export type PrimitiveSchemaDefinition =
  | StringSchema
  | NumberSchema
  | BooleanSchema
  | EnumSchema;

export type EnumSchema =
  | SingleSelectEnumSchema
  | MultiSelectEnumSchema
  | LegacyTitledEnumSchema;

export type SingleSelectEnumSchema =
  | UntitledSingleSelectEnumSchema
  | TitledSingleSelectEnumSchema;

export type MultiSelectEnumSchema =
  | UntitledMultiSelectEnumSchema
  | TitledMultiSelectEnumSchema;
```

`mocapi-model`'s current `PrimitiveSchemaDefinition` is **flattened**:
it permits all eight concrete leaves directly and has no counterparts
for `EnumSchema`, `SingleSelectEnumSchema`, or `MultiSelectEnumSchema`.
The wire format is unaffected (Jackson serializes the leaf records
identically either way), but Java callers can't pattern-match on "any
enum" or "any single-select" as a category — they have to enumerate
the leaves manually.

Add three new sealed marker interfaces in `mocapi-model` to mirror
schema.ts exactly, and re-parent the existing leaves so the hierarchy
is fully faithful.

## Scope

### New sealed interfaces (no body — pure type-level markers)

- `com.callibrity.mocapi.model.SingleSelectEnumSchema`
  - `sealed interface SingleSelectEnumSchema extends EnumSchema
     permits UntitledSingleSelectEnumSchema, TitledSingleSelectEnumSchema {}`
- `com.callibrity.mocapi.model.MultiSelectEnumSchema`
  - `sealed interface MultiSelectEnumSchema extends EnumSchema
     permits UntitledMultiSelectEnumSchema, TitledMultiSelectEnumSchema {}`
- `com.callibrity.mocapi.model.EnumSchema`
  - `sealed interface EnumSchema extends PrimitiveSchemaDefinition
     permits SingleSelectEnumSchema, MultiSelectEnumSchema, LegacyTitledEnumSchema {}`

### Updated `PrimitiveSchemaDefinition`

Change from permitting eight leaves to permitting four direct children
(three concrete records + one sealed `EnumSchema`):

```java
public sealed interface PrimitiveSchemaDefinition
    permits StringSchema, NumberSchema, BooleanSchema, EnumSchema {}
```

### Re-parented leaf records

The five enum-family concrete records must each declare the new
intermediate interface as their `implements` clause (they currently
implement `PrimitiveSchemaDefinition` directly):

| Record | Current `implements` | New `implements` |
|---|---|---|
| `UntitledSingleSelectEnumSchema` | `PrimitiveSchemaDefinition` | `SingleSelectEnumSchema` |
| `TitledSingleSelectEnumSchema` | `PrimitiveSchemaDefinition` | `SingleSelectEnumSchema` |
| `UntitledMultiSelectEnumSchema` | `PrimitiveSchemaDefinition` | `MultiSelectEnumSchema` |
| `TitledMultiSelectEnumSchema` | `PrimitiveSchemaDefinition` | `MultiSelectEnumSchema` |
| `LegacyTitledEnumSchema` | `PrimitiveSchemaDefinition` | `EnumSchema` |

The three non-enum leaves (`StringSchema`, `NumberSchema`,
`BooleanSchema`) continue to implement `PrimitiveSchemaDefinition`
directly — unchanged.

The re-parented records are still *transitive* subtypes of
`PrimitiveSchemaDefinition` through the new intermediate interfaces, so
any existing reference to them as a `PrimitiveSchemaDefinition` keeps
working.

## Acceptance criteria

- [ ] Three new sealed interfaces exist in `mocapi-model`:
      `EnumSchema`, `SingleSelectEnumSchema`, `MultiSelectEnumSchema`,
      each with an empty body (no methods), Apache license header, and
      the `permits` clause listed above.
- [ ] `PrimitiveSchemaDefinition` now permits exactly four direct
      children: `StringSchema`, `NumberSchema`, `BooleanSchema`,
      `EnumSchema`. It no longer permits any of the five enum-family
      leaves directly.
- [ ] Each of the five enum-family leaf records implements the
      appropriate new intermediate interface per the table above.
      Their record components, `@JsonInclude`, `@JsonProperty`, and
      `type()` accessors are unchanged.
- [ ] Wire format is byte-identical: serialize an instance of each of
      the eight concrete leaf types and compare the output to a
      pre-change snapshot captured from `main`. All eight must match
      byte-for-byte.
- [ ] A new test in `mocapi-model` covers the type-level hierarchy:
  - `UntitledSingleSelectEnumSchema` instanceof `SingleSelectEnumSchema`
  - `SingleSelectEnumSchema` instanceof `EnumSchema`
  - `EnumSchema` instanceof `PrimitiveSchemaDefinition`
  - Same for the multi-select branch
  - `LegacyTitledEnumSchema` instanceof `EnumSchema` (and transitively
    `PrimitiveSchemaDefinition`)
  - `StringSchema`, `NumberSchema`, `BooleanSchema` are NOT instanceof
    `EnumSchema`
- [ ] A pattern-match exhaustiveness test compiles and runs: a
      `switch` over `PrimitiveSchemaDefinition` with four cases
      (`StringSchema`, `NumberSchema`, `BooleanSchema`, `EnumSchema`)
      is exhaustive. A switch over `EnumSchema` with three cases
      (`SingleSelectEnumSchema`, `MultiSelectEnumSchema`,
      `LegacyTitledEnumSchema`) is exhaustive.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- These are pure marker interfaces. Do not add any methods — schema.ts
  uses them as union types, not carriers of shared shape.
- Before committing, grep the full reactor for existing `switch`
  statements or `instanceof` checks against
  `PrimitiveSchemaDefinition` and its leaves. Any code that
  currently enumerates all eight leaves in a switch will still
  compile after this change (exhaustiveness is preserved — all eight
  remain transitive subtypes). If any code pattern-matches and relies
  on the flat structure for some other reason, flag it in the PR and
  address case-by-case.
- The intermediate interfaces need the same Apache 2.0 license header
  as every other file in the model module. Follow the existing file
  format.
- File placement: all three new interfaces live in
  `mocapi-model/src/main/java/com/callibrity/mocapi/model/`, matching
  the existing model module flat structure.
- Order of operations suggested:
  1. Add `EnumSchema` interface (but don't change any leaves yet).
  2. Add `SingleSelectEnumSchema` and `MultiSelectEnumSchema`
     interfaces.
  3. Re-parent the five enum-family records in a single commit
     (they all move at once so `EnumSchema`'s `permits` clause is
     satisfied).
  4. Update `PrimitiveSchemaDefinition.permits` to the new four-way
     list in the same commit as step 3, or immediately after.
  5. Add the hierarchy-assertion test.
- This spec intentionally does NOT touch `RequestedSchema`,
  `ElicitationSchemaBuilder`, or any consumer of
  `PrimitiveSchemaDefinition`. It's purely a type-level fidelity
  improvement in the model module.
- If a future spec wants to add behavior to `EnumSchema` (e.g., a
  default method like `values()` that returns `List<String>` for
  whichever concrete variant is present), this spec sets up the
  right place to put it. Don't do that now.
