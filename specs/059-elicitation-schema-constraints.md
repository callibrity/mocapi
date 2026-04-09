# Elicitation schema builder — add missing constraints

## What to build

The elicitation schema builder from spec 057 supports the basic property types
but is missing the optional constraints that the MCP spec allows. Add them.

### String constraints

The builder's `string()` method should return a sub-builder with:
- `.title(String)`
- `.minLength(int)`, `.maxLength(int)`
- `.pattern(String)` — regex
- `.format(String)` — one of `"email"`, `"uri"`, `"date"`, `"date-time"`

Example:
```java
schema.string("email", "Email address").format("email")
schema.string("code", "Zip code").pattern("^\\d{5}$").maxLength(5)
```

### Number/integer constraints

- `.title(String)`
- `.minimum(Number)`, `.maximum(Number)`

Example:
```java
schema.integer("age", "Age").minimum(0).maximum(150)
```

### Boolean constraints

- `.title(String)`

### Multi-select constraints

- `.minItems(int)`, `.maxItems(int)`

Example:
```java
schema.chooseMany("tags", Tag.class).maxItems(3)
```

### Split result types

Rename the existing `ElicitationResult<T>` to `BeanElicitationResult<B>`.
It stays the return type of `elicitForm(message, Class<B>)` and provides
`action()`, `isAccepted()`, and `content()` (the typed bean).

Create a new `ElicitationResult` (no generics) as the return type of
`elicit(message, Consumer<ElicitationSchemaBuilder>)`. It has:

- `action()` — returns `ElicitationAction` (ACCEPT, DECLINE, CANCEL)
- `isAccepted()` — convenience for `action() == ACCEPT`
- `getString(name)` — returns String
- `getInteger(name)` — returns int
- `getNumber(name)` — returns double
- `getBool(name)` — returns boolean
- `getChoice(name, Class<E>)` — returns enum value
- `getChoices(name, Class<E>)` — returns `List<E>` for multi-select

Getters throw `IllegalStateException` if action is not ACCEPT.

Both types share `action()` and `isAccepted()`. No need for a common base —
they're returned by different methods (`elicitForm` vs `elicit`).

## Acceptance criteria

- [ ] String sub-builder with title, minLength, maxLength, pattern, format
- [ ] Number/integer sub-builder with title, minimum, maximum
- [ ] Boolean sub-builder with title
- [ ] Multi-select sub-builder with minItems, maxItems
- [ ] `ElicitationResult` has typed getters: getString, getInteger, getNumber, getBool, getChoice, getChoices
- [ ] Getters throw if action is not ACCEPT
- [ ] All tests pass
- [ ] `mvn verify` passes
