# Record-based property schemas

## What to build

Replace the manual `ObjectNode` building in the property builders with
immutable records that implement `PropertySchema`. Jackson serializes them
directly to JSON Schema. Tests assert on record fields — no JSON parsing.

### PropertySchema sealed interface

`@JsonInclude(NON_NULL)` on the interface — inherited by all records. No need
to annotate individual fields.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface PropertySchema {
    String type();
    String description();
    String title();
    @JsonIgnore boolean required();  // defaults true, used by schema builder
}
```

### Record types

Each record is immutable. Builders accumulate state, `build()` produces the record.

**StringPropertySchema**
```java
public record StringPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("default") String defaultValue,
    Integer minLength,
    Integer maxLength,
    String pattern,
    String format
) implements PropertySchema {
    public String type() { return "string"; }
}
```

**IntegerPropertySchema**
```java
public record IntegerPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("default") Integer defaultValue,
    Number minimum,
    Number maximum
) implements PropertySchema {
    public String type() { return "integer"; }
}
```

**NumberPropertySchema**
```java
public record NumberPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("default") Double defaultValue,
    Number minimum,
    Number maximum
) implements PropertySchema {
    public String type() { return "number"; }
}
```

**BooleanPropertySchema**
```java
public record BooleanPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("default") Boolean defaultValue
) implements PropertySchema {
    public String type() { return "boolean"; }
}
```

**EnumOption** (shared by choose types)
```java
public record EnumOption(
    @JsonProperty("const") String value,
    String title
) {}
```

**EnumPropertySchema** (untitled single-select)
```java
public record EnumPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("enum") List<String> values,
    @JsonProperty("default") String defaultValue
) implements PropertySchema {
    public String type() { return "string"; }
}
```

**TitledEnumPropertySchema** (titled single-select)
```java
public record TitledEnumPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    List<EnumOption> oneOf,
    @JsonProperty("default") String defaultValue
) implements PropertySchema {
    public String type() { return "string"; }
}
```

**MultiSelectPropertySchema** (untitled multi-select)
```java
public record MultiSelectPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    EnumItemsSchema items,
    @JsonProperty("default") List<String> defaultValues,
    Integer minItems,
    Integer maxItems
) implements PropertySchema {
    public String type() { return "array"; }
}

public record EnumItemsSchema(
    @JsonProperty("enum") List<String> values
) {
    @JsonProperty("type") public String type() { return "string"; }
}
```

**TitledMultiSelectPropertySchema** (titled multi-select)
```java
public record TitledMultiSelectPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    TitledEnumItemsSchema items,
    @JsonProperty("default") List<String> defaultValues,
    Integer minItems,
    Integer maxItems
) implements PropertySchema {
    public String type() { return "array"; }
}

public record TitledEnumItemsSchema(
    List<EnumOption> anyOf
) {
    @JsonProperty("type") public String type() { return "string"; }
}
```

**LegacyEnumPropertySchema** (deprecated)
```java
@Deprecated
public record LegacyEnumPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    @JsonProperty("enum") List<String> values,
    List<String> enumNames,
    @JsonProperty("default") String defaultValue
) implements PropertySchema {
    public String type() { return "string"; }
}
```

### Property builders

Each builder accumulates state and produces its record via `build()`.
All builders have `description()`, `title()`, `optional()` (sets required=false).
Required defaults to true.

Example:
```java
var schema = new StringPropertyBuilder()
    .description("Email address")
    .email()
    .maxLength(255)
    .build();
// Returns StringPropertySchema with required=true, format="email", maxLength=255
```

### ElicitationSchemaBuilder

Stores `Map<String, PropertySchema>`. The consumer receives the property builder:

```java
schema.string("email", b -> b.description("Email").email().maxLength(255))
```

At build time:
1. Iterates the map
2. Collects property names where `required() == true` into the `required` array
3. Serializes each `PropertySchema` via `mapper.valueToTree()`
4. Assembles the final `{"type": "object", "properties": {...}, "required": [...]}`

### Testing

Property builder tests are pure Java — no JSON, no ObjectMapper:

```java
@Test
void emailStringProperty() {
    var schema = new StringPropertyBuilder()
        .description("Email")
        .email()
        .maxLength(255)
        .build();

    assertThat(schema.type()).isEqualTo("string");
    assertThat(schema.description()).isEqualTo("Email");
    assertThat(schema.format()).isEqualTo("email");
    assertThat(schema.maxLength()).isEqualTo(255);
    assertThat(schema.required()).isTrue();
}

@Test
void optionalField() {
    var schema = new StringPropertyBuilder()
        .description("Nickname")
        .optional()
        .build();

    assertThat(schema.required()).isFalse();
}

@Test
void titledEnumWithDefault() {
    var schema = ChooseOnePropertyBuilder.fromEnum(Status.class)
        .titleFn(s -> s.name().toLowerCase())
        .defaultValue(Status.ACTIVE)
        .build();

    assertThat(schema).isInstanceOf(TitledEnumPropertySchema.class);
    var titled = (TitledEnumPropertySchema) schema;
    assertThat(titled.oneOf()).hasSize(3);
    assertThat(titled.defaultValue()).isEqualTo("ACTIVE");
}
```

Serialization tests verify the JSON output separately:

```java
@Test
void serializesCorrectly() {
    var schema = new StringPropertyBuilder()
        .description("Email")
        .email()
        .build();

    var json = mapper.writeValueAsString(schema);
    assertThat(json).contains("\"type\":\"string\"");
    assertThat(json).contains("\"format\":\"email\"");
    assertThat(json).doesNotContain("required");
}
```

## Acceptance criteria

- [ ] `PropertySchema` sealed interface with `type()`, `description()`, `title()`, `required()`
- [ ] All record types implement `PropertySchema`
- [ ] `@JsonProperty` handles reserved words (`enum`, `const`, `default`)
- [ ] `@JsonIgnore` on `required` field
- [ ] `@JsonInclude(NON_NULL)` on all optional fields
- [ ] Each property builder produces its corresponding record via `build()`
- [ ] All builders default `required=true`, support `optional()`
- [ ] All builders support `description()` and `title()`
- [ ] `ElicitationSchemaBuilder` stores `Map<String, PropertySchema>`
- [ ] Required array built from `required()` field at schema build time
- [ ] Unit tests for each builder — record field assertions, no JSON parsing
- [ ] Serialization tests verify JSON output
- [ ] All conformance tests still pass
- [ ] `mvn verify` passes
