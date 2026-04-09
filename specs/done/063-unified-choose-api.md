# Unified builder API with customizers

## What to build

Rewrite the `ElicitationSchemaBuilder` with a clean, consistent API. Remove all
`*Property` methods. Use the customizer (`Consumer`) pattern for optional
constraints. Fix the `"type": "string"` bug on enum schemas.

### Primitive property methods

Each returns the builder for fluent chaining. Optional constraints are passed
via a `Consumer` customizer as the last argument.

```java
// String
string(String name, String description)
string(String name, String description, String defaultValue)
string(String name, String description, Consumer<StringConstraints> customizer)
string(String name, String description, String defaultValue, Consumer<StringConstraints> customizer)

// Integer
integer(String name, String description)
integer(String name, String description, int defaultValue)
integer(String name, String description, Consumer<NumericConstraints> customizer)
integer(String name, String description, int defaultValue, Consumer<NumericConstraints> customizer)

// Number
number(String name, String description)
number(String name, String description, double defaultValue)
number(String name, String description, Consumer<NumericConstraints> customizer)
number(String name, String description, double defaultValue, Consumer<NumericConstraints> customizer)

// Boolean
bool(String name, String description)
bool(String name, String description, boolean defaultValue)
```

### Constraint customizers

```java
public interface StringConstraints {
    StringConstraints title(String title);
    StringConstraints minLength(int min);
    StringConstraints maxLength(int max);
    StringConstraints pattern(String regex);
    StringConstraints format(StringFormat format);
}

public interface NumericConstraints {
    NumericConstraints title(String title);
    NumericConstraints minimum(Number min);
    NumericConstraints maximum(Number max);
}
```

### StringFormat enum

```java
public enum StringFormat {
    EMAIL("email"),
    URI("uri"),
    DATE("date"),
    DATE_TIME("date-time");
}
```

Example usage:

```java
schema.string("email", "Email address", s -> s.format(StringFormat.EMAIL).maxLength(255))
schema.integer("age", "Your age", n -> n.minimum(0).maximum(150))
```

### Choose/chooseMany — enum class

```java
choose(String name, Class<E> enumType)
choose(String name, Class<E> enumType, E defaultValue)
choose(String name, Class<E> enumType, Function<E,String> titleFn)
choose(String name, Class<E> enumType, Function<E,String> titleFn, E defaultValue)

chooseMany(String name, Class<E> enumType, E... defaults)
chooseMany(String name, Class<E> enumType, Function<E,String> titleFn, E... defaults)
chooseMany(String name, Class<E> enumType, List<E> defaults)
chooseMany(String name, Class<E> enumType, Function<E,String> titleFn, List<E> defaults)
```

Enum variants use `Enum::name` for `const`, `Object::toString` for title (unless
titleFn overrides). All delegate to arbitrary objects core.

### Choose/chooseMany — arbitrary objects

```java
choose(String name, List<T> items, Function<T,String> valueFn)
choose(String name, List<T> items, Function<T,String> valueFn, T defaultValue)
choose(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn)
choose(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn, T defaultValue)

chooseMany(String name, List<T> items, Function<T,String> valueFn, T... defaults)
chooseMany(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn, T... defaults)
chooseMany(String name, List<T> items, Function<T,String> valueFn, List<T> defaults)
chooseMany(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn, List<T> defaults)
```

### Choose/chooseMany — raw strings

```java
choose(String name, List<String> values)
choose(String name, List<String> values, String defaultValue)
chooseMany(String name, List<String> values, String... defaults)
chooseMany(String name, List<String> values, List<String> defaults)
```

### Multi-select constraints

`chooseMany` variants can take an optional `Consumer<MultiSelectConstraints>`:

```java
chooseMany(String name, Class<E> enumType, Consumer<MultiSelectConstraints> customizer, E... defaults)

public interface MultiSelectConstraints {
    MultiSelectConstraints minItems(int min);
    MultiSelectConstraints maxItems(int max);
}
```

### Schema generation fixes

- Single-select MUST generate `"type": "string"` + `"oneOf"` with `const`/`title`
- Multi-select MUST generate `"type": "array"` + `"items"` with `"type": "string"` + `"anyOf"` with `const`/`title`
- BUG: current `buildOneOfNode` omits `"type": "string"` — MCP SDK rejects without it

### Result types

`elicitForm(message, Consumer<Builder>)` returns `ElicitationResult`:
- `action()`, `isAccepted()`
- `getString(name)`, `getInteger(name)`, `getNumber(name)`, `getBool(name)`
- `getChoice(name)` → String, `getChoice(name, Class<E>)` → E
- `getChoices(name)` → `List<String>`, `getChoices(name, Class<E>)` → `List<E>`

`elicitBeanForm(message, Class<B>)` returns `BeanElicitationResult<B>`:
- `action()`, `isAccepted()`, `content()` → B

### Cleanup

Remove ALL old method names:
- `stringProperty`, `integerProperty`, `numberProperty`, `booleanProperty`
- `enumProperty`, `multiSelectProperty`, `titledEnumProperty`
- `TitledValue` record
- Disconnected sub-builder classes (`StringPropertyBuilder`, etc.) — replaced by customizer interfaces

### Required

```java
required(String... names)
```

## Acceptance criteria

- [ ] All `*Property` methods removed
- [ ] `string`, `integer`, `number`, `bool` with customizer `Consumer` pattern
- [ ] `StringFormat` enum with EMAIL, URI, DATE, DATE_TIME
- [ ] `StringConstraints`, `NumericConstraints`, `MultiSelectConstraints` interfaces
- [ ] `choose`/`chooseMany` with enum, arbitrary objects, and raw string variants
- [ ] All select schemas include `"type": "string"` (single) or `"type": "array"` + items `"type": "string"` (multi)
- [ ] `ElicitationResult` with typed getters
- [ ] `BeanElicitationResult<B>` for bean form
- [ ] `TitledValue`, old sub-builders removed
- [ ] Conformance tools updated to use new API
- [ ] `elicitation-sep1034-defaults` npx scenario passes
- [ ] `elicitation-sep1330-enums` npx scenario passes
- [ ] All tests pass
- [ ] `mvn verify` passes
