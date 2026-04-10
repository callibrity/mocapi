# Add elicitation types to mocapi-model

## What to build

Add the elicitation schema, request, and result types to `mocapi-model`.
Just create the types — do NOT migrate mocapi-core to use them yet. A
follow-up spec will handle the migration.

### ElicitAction enum

```java
public enum ElicitAction {
    ACCEPT, DECLINE, CANCEL;
    @JsonValue public String toJson() { return name().toLowerCase(); }
    @JsonCreator public static ElicitAction fromJson(String v) { return valueOf(v.toUpperCase(Locale.ROOT)); }
}
```

### ElicitResult

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitResult(ElicitAction action, Map<String, Object> content) {}
```

### StringFormat enum

```java
public enum StringFormat {
    EMAIL, URI, DATE, DATE_TIME;
    @JsonValue public String toJson() {
        return this == DATE_TIME ? "date-time" : name().toLowerCase();
    }
    @JsonCreator public static StringFormat fromJson(String v) {
        return "date-time".equals(v) ? DATE_TIME : valueOf(v.toUpperCase(Locale.ROOT));
    }
}
```

### PrimitiveSchemaDefinition sealed interface

Flatten the nested `EnumSchema` union from the spec into a single sealed
hierarchy:

```java
public sealed interface PrimitiveSchemaDefinition
    permits StringSchema, NumberSchema, BooleanSchema,
            UntitledSingleSelectEnumSchema, TitledSingleSelectEnumSchema,
            UntitledMultiSelectEnumSchema, TitledMultiSelectEnumSchema,
            LegacyTitledEnumSchema {}
```

### StringSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StringSchema(
    String title,
    String description,
    Integer minLength,
    Integer maxLength,
    StringFormat format,
    @JsonProperty("default") String defaultValue
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "string"; }
}
```

### NumberSchema (covers both number and integer)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NumberSchema(
    String type,  // "number" or "integer"
    String title,
    String description,
    Number minimum,
    Number maximum,
    @JsonProperty("default") Number defaultValue
) implements PrimitiveSchemaDefinition {}
```

### BooleanSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BooleanSchema(
    String title,
    String description,
    @JsonProperty("default") Boolean defaultValue
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "boolean"; }
}
```

### EnumOption (shared by titled enum variants)

```java
public record EnumOption(
    @JsonProperty("const") String value,
    String title
) {}
```

### UntitledSingleSelectEnumSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UntitledSingleSelectEnumSchema(
    String title,
    String description,
    @JsonProperty("enum") List<String> values,
    @JsonProperty("default") String defaultValue
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "string"; }
}
```

### TitledSingleSelectEnumSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TitledSingleSelectEnumSchema(
    String title,
    String description,
    List<EnumOption> oneOf,
    @JsonProperty("default") String defaultValue
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "string"; }
}
```

### UntitledMultiSelectEnumSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UntitledMultiSelectEnumSchema(
    String title,
    String description,
    Integer minItems,
    Integer maxItems,
    EnumItemsSchema items,
    @JsonProperty("default") List<String> defaultValues
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "array"; }
}

public record EnumItemsSchema(@JsonProperty("enum") List<String> values) {
    @JsonProperty("type") public String type() { return "string"; }
}
```

### TitledMultiSelectEnumSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TitledMultiSelectEnumSchema(
    String title,
    String description,
    Integer minItems,
    Integer maxItems,
    TitledEnumItemsSchema items,
    @JsonProperty("default") List<String> defaultValues
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "array"; }
}

public record TitledEnumItemsSchema(List<EnumOption> anyOf) {}
```

### LegacyTitledEnumSchema

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@Deprecated
public record LegacyTitledEnumSchema(
    String title,
    String description,
    @JsonProperty("enum") List<String> values,
    List<String> enumNames,
    @JsonProperty("default") String defaultValue
) implements PrimitiveSchemaDefinition {
    @JsonProperty("type") public String type() { return "string"; }
}
```

### RequestedSchema

The top-level schema object used in elicitation requests:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestedSchema(
    Map<String, PrimitiveSchemaDefinition> properties,
    List<String> required
) {
    @JsonProperty("type") public String type() { return "object"; }
}
```

### Tests

Unit tests verifying each type serializes to the expected JSON Schema shape.

## Acceptance criteria

- [ ] `ElicitAction` enum with `@JsonValue`/`@JsonCreator`
- [ ] `ElicitResult` record
- [ ] `StringFormat` enum
- [ ] `PrimitiveSchemaDefinition` sealed interface with 9 subtypes
- [ ] `StringSchema`, `NumberSchema`, `BooleanSchema` records
- [ ] All 5 enum schema variants (4 modern + 1 legacy)
- [ ] `EnumOption`, `EnumItemsSchema`, `TitledEnumItemsSchema` support records
- [ ] `RequestedSchema` record
- [ ] JSON serialization tests for each type
- [ ] Matches MCP 2025-11-25 schema.ts exactly
- [ ] `mvn verify` passes
- [ ] NO changes to mocapi-core — migration is a follow-up spec
