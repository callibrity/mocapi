# Extract elicitation into separate package with testable property builders

## What to build

Move all elicitation classes into `com.callibrity.mocapi.stream.elicitation`
and split the monolithic `ElicitationSchema` into individually testable,
consistently named property builder classes.

### New package: `com.callibrity.mocapi.stream.elicitation`

### Property builders

Each property type gets its own builder that produces an `ObjectNode`.
All builders follow the same pattern: constructor takes the description,
fluent methods for constraints, `build(ObjectMapper)` returns the node.

**`StringPropertyBuilder`**
```java
new StringPropertyBuilder("description")
    .title("Title")
    .defaultValue("default")
    .email()
    .minLength(1).maxLength(255)
    .pattern("^[a-z]+$")
    .build(objectMapper)  // → {"type": "string", "description": "...", ...}
```

**`IntegerPropertyBuilder`**
```java
new IntegerPropertyBuilder("description")
    .title("Title")
    .defaultValue(30)
    .minimum(0).maximum(150)
    .build(objectMapper)
```

**`NumberPropertyBuilder`**
```java
new NumberPropertyBuilder("description")
    .title("Title")
    .defaultValue(95.5)
    .minimum(0.0).maximum(100.0)
    .build(objectMapper)
```

**`BooleanPropertyBuilder`**
```java
new BooleanPropertyBuilder("description")
    .title("Title")
    .defaultValue(true)
    .build(objectMapper)
```

**`ChooseOneBuilder<T>`**
```java
// From enum
ChooseOneBuilder.fromEnum(Status.class)
    .titleFn(Status::getLabel)
    .defaultValue(Status.ACTIVE)
    .build(objectMapper)

// From arbitrary items
ChooseOneBuilder.from(colors, Color::hex)
    .titleFn(Color::name)
    .build(objectMapper)

// From raw strings (produces plain enum array)
ChooseOneBuilder.from(List.of("a", "b", "c"))
    .defaultValue("a")
    .build(objectMapper)
```

Untitled (raw strings, or no titleFn where title == value) produces
`{"type": "string", "enum": [...]}`.
Titled (titleFn provided, or enum with overridden toString) produces
`{"type": "string", "oneOf": [{"const": ..., "title": ...}]}`.

**`ChooseManyBuilder<T>`**
Same factory methods and naming as `ChooseOneBuilder` but wraps in array:
```java
ChooseManyBuilder.fromEnum(Tag.class)
    .maxItems(3)
    .defaults(List.of(Tag.JAVA))
    .build(objectMapper)
```

**`ChooseLegacyBuilder`** (deprecated)
```java
@Deprecated
new ChooseLegacyBuilder(List.of("opt1", "opt2"), List.of("Option One", "Option Two"))
    .build(objectMapper)
```

### ElicitationSchemaBuilder

Simplified — delegates to property builders. The `Consumer` customizer
receives the property builder directly:

```java
schema.string("email", "Email", s -> s.email().maxLength(255))
//                                 ^ StringPropertyBuilder
schema.choose("status", Status.class, c -> c.defaultValue(Status.ACTIVE))
//                                     ^ ChooseOneBuilder<Status>
schema.chooseMany("tags", Tag.class, c -> c.maxItems(3))
//                                    ^ ChooseManyBuilder<Tag>
```

### Files in the new package

- `ElicitationSchema.java` — schema record with `toObjectNode`
- `ElicitationSchemaBuilder.java` — orchestrator
- `StringPropertyBuilder.java`
- `IntegerPropertyBuilder.java`
- `NumberPropertyBuilder.java`
- `BooleanPropertyBuilder.java`
- `ChooseOneBuilder.java`
- `ChooseManyBuilder.java`
- `ChooseLegacyBuilder.java` (deprecated)
- `ElicitationResult.java`
- `BeanElicitationResult.java`
- `ElicitationAction.java`
- `ElicitationSchemaValidator.java`

### Move exception classes

Move into the new package:
- `McpElicitationException`
- `McpElicitationTimeoutException`
- `McpElicitationNotSupportedException`

## Acceptance criteria

- [ ] All builders consistently named: `*PropertyBuilder` or `Choose*Builder`
- [ ] Each builder independently constructable and testable
- [ ] Each builder has `build(ObjectMapper)` returning `ObjectNode`
- [ ] `ElicitationSchemaBuilder` delegates to property builders
- [ ] Customizer consumers receive the property builder directly
- [ ] Unit tests for each property builder in isolation
- [ ] All elicitation classes in `com.callibrity.mocapi.stream.elicitation`
- [ ] All imports updated throughout codebase
- [ ] All existing functionality preserved
- [ ] All tests pass
- [ ] `mvn verify` passes
