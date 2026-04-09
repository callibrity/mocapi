# Elicitation schema builder

## What to build

A type-safe builder for MCP elicitation schemas that ONLY supports the exact
types the spec allows. Uses Java enums for select/multi-select instead of raw
strings. Add a new `elicit` method on `McpStreamContext` that takes a
`Consumer<ElicitationSchemaBuilder>`.

### ElicitationSchemaBuilder

The builder produces an object-type JSON Schema. Only the following property
types are allowed — no escape hatch for arbitrary schemas.

**Primitives:**
- `string(name, description)`
- `string(name, description, defaultValue)`
- `integer(name, description)`
- `integer(name, description, defaultValue)`
- `number(name, description)`
- `number(name, description, defaultValue)`
- `bool(name, description)`
- `bool(name, description, defaultValue)`

**Enums (single-select):**
- `choose(name, Class<? extends Enum<?>> enumType)`
- `choose(name, Class<? extends Enum<?>> enumType, Enum<?> defaultValue)`

Generates enum values from `Enum.values()` in ordinal order. Uses `toString()`
for display names. If `toString()` differs from `name()`, generates titled enum
using `oneOf` with `const`/`title`. Otherwise generates plain `enum` array.

**Enums (multi-select):**
- `chooseMany(name, Class<? extends Enum<?>> enumType)`
- `chooseMany(name, Class<? extends Enum<?>> enumType, List<? extends Enum<?>> defaults)`

Same enum-to-schema logic, wrapped in `"type": "array", "items": { ... }`.

**Required fields:**
- `required(String... names)`

### New method on McpStreamContext

```java
ElicitationResult<JsonNode> elicit(String message, Consumer<ElicitationSchemaBuilder> schema);
```

Usage:

```java
ctx.elicit("Enter your info", schema -> schema
    .string("username", "Your name")
    .string("email", "Email address")
    .choose("role", Role.class, Role.USER)
    .required("username", "email"));
```

The method creates the builder, passes it to the consumer, builds the schema,
and sends the elicitation request. Returns `ElicitationResult` with typed
accessors that mirror the builder methods:

```java
ElicitationResult result = ctx.elicit("Enter info", schema -> schema
    .string("username", "Name")
    .integer("age", "Age")
    .number("score", "Score")
    .bool("verified", "Verified?")
    .choose("status", Status.class)
    .chooseMany("tags", Tag.class)
    .required("username"));

if (result.isAccepted()) {
    String username = result.getString("username");
    int age = result.getInteger("age");
    double score = result.getNumber("score");
    boolean verified = result.getBool("verified");
    Status status = result.getChoice("status", Status.class);
    List<Tag> tags = result.getChoices("tags", Tag.class);
}
```

`ElicitationResult` has:
- `action()` — returns `ElicitationAction` (ACCEPT, DECLINE, CANCEL)
- `isAccepted()` — convenience for `action() == ACCEPT`
- `getString(name)` — returns String value
- `getInteger(name)` — returns int value
- `getNumber(name)` — returns double value
- `getBool(name)` — returns boolean value
- `getChoice(name, Class<E>)` — returns enum value
- `getChoices(name, Class<E>)` — returns `List<E>` for multi-select

Getters throw if the result action is not ACCEPT.

The existing `elicitForm(message, Class<T>)` stays unchanged — it returns
`ElicitationResult<T>` with the typed content.

### Update DefaultMcpStreamContext

Add the `elicit` implementation:
1. Check elicitation support on the session
2. Create `ElicitationSchemaBuilder`, pass to consumer
3. Build `ObjectNode` from the builder
4. Call existing `sendElicitationAndWait`
5. Return `ElicitationResult<JsonNode>` with action and raw content

## Acceptance criteria

- [ ] `ElicitationSchemaBuilder` exists with exactly the methods listed above
- [ ] No way to add arbitrary schema properties — only the supported types
- [ ] `choose()` uses `Enum.values()` in ordinal order with `toString()` for display
- [ ] `choose()` generates `oneOf`/`const`/`title` when `toString()` differs from `name()`
- [ ] `choose()` generates plain `enum` array when `toString()` equals `name()`
- [ ] `chooseMany()` wraps enum schema in array type
- [ ] Default values work for all property types
- [ ] `McpStreamContext.elicit(String, Consumer<ElicitationSchemaBuilder>)` returns `ElicitationResult`
- [ ] `ElicitationResult` has `getString`, `getInteger`, `getNumber`, `getBool`, `getChoice`, `getChoices`
- [ ] Getters throw if action is not ACCEPT
- [ ] Existing `elicitForm(message, Class<T>)` unchanged
- [ ] All tests pass
- [ ] `mvn verify` passes
