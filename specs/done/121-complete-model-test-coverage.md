# Complete serialization test coverage for all model records

## What to build

`mocapi-model` has 92 record/interface types in
`src/main/java/com/callibrity/mocapi/model/` but only 9 test files.
Each existing test covers several types, but the coverage is
incomplete — many model records have no explicit round-trip test.

Audit every model type and ensure it has at least one round-trip
serialization test asserting:
1. **Serialization shape**: constructing the record with
   representative field values and writing it to JSON produces
   the exact shape the MCP spec requires (field names,
   discriminator fields like `"type": "text"`, inclusion rules
   for null fields).
2. **Round-trip fidelity**: `mapper.readValue(mapper.writeValueAsString(record), Type.class)`
   produces an equal record.
3. **Polymorphic dispatch** (for sealed interfaces): given the
   wire JSON for each concrete variant, `mapper.readValue(json,
   SealedType.class)` returns the correct concrete subtype.

### Audit process

Run this command to list every model class without a dedicated
test mention:

```bash
for f in mocapi-model/src/main/java/com/callibrity/mocapi/model/*.java; do
  name=$(basename "$f" .java)
  if ! grep -rq "$name" mocapi-model/src/test; then
    echo "MISSING: $name"
  fi
done
```

Every class that shows up in the "MISSING" list needs at least
one test case added — either in an existing test file (grouped
by concern — e.g., resource types go in
`ResourceContentsSerializationTest`) or in a new file if the
class represents a new concern.

### Grouping convention

Keep the existing test file organization:
- **`ProtocolTypesSerializationTest`** — `InitializeRequestParams`,
  `InitializeResult`, `ServerCapabilities`, `ClientCapabilities`,
  `Implementation`, etc.
- **`ElicitationTypesSerializationTest`** — `ElicitRequestFormParams`,
  `ElicitRequestURLParams`, `ElicitResult`, `ElicitAction`,
  `RequestedSchema`, `PrimitiveSchemaDefinition` variants
- **`RequestAndNotificationTypesSerializationTest`** — JSON-RPC-level
  request params records
- **`RequestMetaAndProgressSerializationTest`** — `RequestMeta`,
  `ProgressNotification`, `ProgressNotificationParams`
- **`PrimitiveSchemaDefinitionHierarchyTest`** — sealed hierarchy
  instanceof checks for schema types
- **`ContentBlockSerializationTest`** — `TextContent`,
  `ImageContent`, `AudioContent`, `EmbeddedResource`,
  `ResourceLink`, and the `ContentBlock` sealed hierarchy
- **`ResourceContentsSerializationTest`** — `TextResourceContents`,
  `BlobResourceContents`, and the `ResourceContents` sealed
  hierarchy
- **`EmptyResultSerializationTest`** — `EmptyResult` (single test
  asserting it serializes to `{}`)
- **`CreateMessageResultTest`** — `CreateMessageResult` and its
  `text()` helper

Place each missing test in the most appropriate existing file.
Create new test files only if a genuinely new concern appears
(unlikely — almost everything fits into the nine categories
above).

### Test pattern (prefer `satisfies` chains per spec 119)

```java
@Test
void someRecordRoundTrip() throws Exception {
  var original = new SomeRecord(field1, field2, field3);
  String json = mapper.writeValueAsString(original);

  assertThat(json).satisfies(j -> {
    assertThat(j).contains("\"field1\":\"value1\"");
    assertThat(j).contains("\"field2\":42");
    // ...
  });

  var roundTripped = mapper.readValue(json, SomeRecord.class);
  assertThat(roundTripped).isEqualTo(original);
}
```

For polymorphic tests on sealed interfaces:

```java
@Test
void contentBlockDeserializesIntoCorrectSubtype() throws Exception {
  String textJson = "{\"type\":\"text\",\"text\":\"hello\"}";
  assertThat(mapper.readValue(textJson, ContentBlock.class))
      .isInstanceOf(TextContent.class)
      .extracting(c -> ((TextContent) c).text())
      .isEqualTo("hello");
}
```

## Acceptance criteria

- [ ] Running the audit script produces an empty "MISSING" list.
- [ ] Every concrete (non-sealed-parent) record in
      `com.callibrity.mocapi.model` has at least one test case
      asserting round-trip fidelity.
- [ ] Every sealed interface (e.g., `ContentBlock`,
      `ResourceContents`, `PrimitiveSchemaDefinition`, `EnumSchema`,
      `SingleSelectEnumSchema`, `MultiSelectEnumSchema`) has a
      test verifying that deserialization produces the correct
      concrete subtype for each wire-shape variant.
- [ ] Tests use the AssertJ chain patterns from spec 119 (no
      multiple consecutive `assertThat` on the same subject).
- [ ] `mvn -pl mocapi-model test` passes.
- [ ] `mvn verify` passes across the full reactor.
- [ ] Test coverage for `mocapi-model` (per JaCoCo) is at least
      90% line coverage and 80% branch coverage after this spec.

## Implementation notes

- **Use the schema.ts examples as the authoritative wire format
  source** when constructing test fixtures. The MCP
  `schema/2025-11-25/schema.ts` file in the
  `modelcontextprotocol/modelcontextprotocol` repo has inline
  JSON examples for most types. Copy them into test fixtures
  and assert that mocapi produces the same shapes.
- **Don't duplicate test coverage**. If
  `ProtocolTypesSerializationTest` already covers `InitializeResult`,
  don't write another test for it in a different file. The audit
  script catches missing coverage; use it as the source of truth.
- **Parameterized tests** are great for asserting multiple shapes
  of the same sealed hierarchy. See the pattern for
  `PrimitiveSchemaDefinitionHierarchyTest` — one test method
  parameterized over all leaf types.
- **`NON_NULL` include rules**: many model records have
  `@JsonInclude(NON_NULL)`. Test both "all fields set" and "some
  fields null" cases to verify the null-omission behavior on
  the wire.
- **Discriminator fields** (`"type": "..."`, `"role": "..."`):
  verify these are present in the serialized output — they're
  load-bearing for polymorphic deserialization downstream.
- **Commit granularity**: one commit per test file is a
  reasonable chunk. Don't commit half-finished test files.
- **Do NOT touch production code** in this spec. If a test
  reveals a bug in a model record, write a separate spec for
  fixing the bug — don't bundle with the test-writing work.
