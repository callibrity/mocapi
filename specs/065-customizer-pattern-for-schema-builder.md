# Customizer pattern for schema builder

## What to build

Reduce overload explosion on `ElicitationSchemaBuilder` by moving defaults,
titles, and constraints into customizer `Consumer` parameters. Each property
method has at most two overloads: with and without customizer.

### Primitive methods

Three overloads each: bare, with default, with customizer.

```java
string(String name, String description)
string(String name, String description, String defaultValue)
string(String name, String description, Consumer<StringConstraints> customizer)

integer(String name, String description)
integer(String name, String description, int defaultValue)
integer(String name, String description, Consumer<NumericConstraints> customizer)

number(String name, String description)
number(String name, String description, double defaultValue)
number(String name, String description, Consumer<NumericConstraints> customizer)

bool(String name, String description)
bool(String name, String description, boolean defaultValue)
bool(String name, String description, Consumer<BooleanConstraints> customizer)
```

When you need both default AND constraints, use the customizer:
```java
schema.string("name", "Name", s -> s.defaultValue("John Doe").maxLength(100))
```

### Constraint interfaces

```java
public interface StringConstraints {
    StringConstraints title(String title);
    StringConstraints defaultValue(String value);
    StringConstraints minLength(int min);
    StringConstraints maxLength(int max);
    StringConstraints pattern(String regex);
    StringConstraints email();     // shorthand for format("email")
    StringConstraints uri();       // shorthand for format("uri")
    StringConstraints date();      // shorthand for format("date")
    StringConstraints dateTime();  // shorthand for format("date-time")
}

public interface NumericConstraints {
    NumericConstraints title(String title);
    NumericConstraints defaultValue(Number value);
    NumericConstraints minimum(Number min);
    NumericConstraints maximum(Number max);
}

public interface BooleanConstraints {
    BooleanConstraints title(String title);
    BooleanConstraints defaultValue(boolean value);
}
```

Example:
```java
schema.string("email", "Email", s -> s.email().maxLength(255))
schema.integer("age", "Age", n -> n.minimum(0).maximum(150).defaultValue(30))
schema.bool("verified", "Verified?", b -> b.defaultValue(true))
```

### Choose methods

Three overloads each: bare, with default, with customizer.

```java
// Enum class
choose(String name, Class<E> enumType)
choose(String name, Class<E> enumType, E defaultValue)
choose(String name, Class<E> enumType, Consumer<ChoiceConstraints<E>> customizer)

// Arbitrary objects — valueFn required, everything else in customizer
choose(String name, List<T> items, Function<T,String> valueFn)
choose(String name, List<T> items, Function<T,String> valueFn, T defaultValue)
choose(String name, List<T> items, Function<T,String> valueFn, Consumer<ChoiceConstraints<T>> customizer)

// Raw strings
choose(String name, List<String> values)
choose(String name, List<String> values, String defaultValue)
choose(String name, List<String> values, Consumer<ChoiceConstraints<String>> customizer)
```

### ChooseMany methods

```java
chooseMany(String name, Class<E> enumType)
chooseMany(String name, Class<E> enumType, Consumer<MultiChoiceConstraints<E>> customizer)

chooseMany(String name, List<T> items, Function<T,String> valueFn)
chooseMany(String name, List<T> items, Function<T,String> valueFn, Consumer<MultiChoiceConstraints<T>> customizer)

chooseMany(String name, List<String> values)
chooseMany(String name, List<String> values, Consumer<MultiChoiceConstraints<String>> customizer)
```

### Choice constraint interfaces

```java
public interface ChoiceConstraints<T> {
    ChoiceConstraints<T> title(Function<T, String> titleFn);
    ChoiceConstraints<T> defaultValue(T value);
}

public interface MultiChoiceConstraints<T> {
    MultiChoiceConstraints<T> title(Function<T, String> titleFn);
    MultiChoiceConstraints<T> defaults(List<T> values);
    @SuppressWarnings("unchecked")
    MultiChoiceConstraints<T> defaults(T... values);
    MultiChoiceConstraints<T> minItems(int min);
    MultiChoiceConstraints<T> maxItems(int max);
}
```

Example:
```java
schema.choose("status", Status.class, c -> c.title(Status::getLabel).defaultValue(Status.ACTIVE))
schema.chooseMany("tags", Tag.class, c -> c.maxItems(3).defaults(Tag.JAVA, Tag.SPRING))
schema.choose("color", colors, Color::hex, c -> c.title(Color::displayName))
```

### Update conformance tools

Update `test_elicitation_sep1034_defaults` and `test_elicitation_sep1330_enums`
to use the new customizer API.

### Remove old overloads

Delete all overloads that had defaults or titleFn as positional parameters.
Remove `StringFormat` enum if it was created — replaced by shorthand methods.

## Acceptance criteria

- [ ] Each primitive method has 3 overloads: bare, default, customizer
- [ ] Each choose/chooseMany has 3 overloads per input type: bare, default, customizer
- [ ] `StringConstraints` with `email()`, `uri()`, `date()`, `dateTime()` shorthands
- [ ] `NumericConstraints` with `minimum`, `maximum`, `defaultValue`
- [ ] `BooleanConstraints` with `defaultValue`
- [ ] `ChoiceConstraints<T>` with `title(Function)` and `defaultValue`
- [ ] `MultiChoiceConstraints<T>` with `title`, `defaults`, `minItems`, `maxItems`
- [ ] No positional default or titleFn overloads remain
- [ ] Conformance tools updated
- [ ] `elicitation-sep1034-defaults` npx scenario passes
- [ ] `elicitation-sep1330-enums` npx scenario passes
- [ ] All tests pass
- [ ] `mvn verify` passes
