# Bean-oriented elicit overload on McpStreamContext

## What to build

Now that `model.ElicitResult.content` is typed as `ObjectNode` (from the
elicitation cleanup that landed earlier this session), the stream
context has everything it needs to deserialize the elicitation content
into a tool-author-supplied bean on demand. Add a convenience overload
on `McpStreamContext` that does this in one call:

```java
<T> Optional<T> elicit(
    String message,
    Consumer<RequestedSchemaBuilder> schema,
    Class<T> resultType);
```

and a sibling overload for generic types:

```java
<T> Optional<T> elicit(
    String message,
    Consumer<RequestedSchemaBuilder> schema,
    TypeReference<T> resultType);
```

**Semantics:**

- The tool author still defines the schema via the fluent DSL (same
  `Consumer<RequestedSchemaBuilder>` parameter the current
  `elicit(...)` method takes).
- The framework calls the existing elicit flow, gets a model
  `ElicitResult` back.
- If the user accepted (`result.isAccepted() == true`), the framework
  deserializes `result.content()` into an instance of `T` via
  `objectMapper.treeToValue(result.content(), resultType)` and returns
  it wrapped in `Optional.of(...)`.
- If the user declined or cancelled (`action != ACCEPT`), the
  framework returns `Optional.empty()`. No bean deserialization
  happens in this case.
- Schema validation continues to run on accept (before bean
  deserialization) exactly as it does in the existing
  `elicit(message, schema)` method. A validation failure still throws
  `McpElicitationException`.

**Rationale:**

Spec 100 removed the bean-based `elicitForm(Class<T>)` / `elicitForm(TypeReference<T>)`
methods because they generated the schema *from* the class via
reflection, which dragged in Jakarta-validation and Victools
dependencies for every elicit call. The new overload is strictly
better:

1. **Schema is explicit** — tool author controls it via the DSL, not
   class reflection. No Jakarta-validation, no Victools schema
   generation.
2. **Bean binding is opt-in** — only happens when `resultType` is
   supplied. Callers who don't care about typed binding use the
   existing `elicit(message, schema)` method and get a plain
   `ElicitResult`.
3. **Same underlying flow** — just a post-hoc `treeToValue` step on
   top of the existing elicit path. No duplication of the request
   construction, mailbox correlation, or validation logic.

**Example tool-author usage:**

```java
public record UserForm(String username, String email, int age) {}

public CallToolResult collectUser(McpStreamContext<CallToolResult> ctx) {
  Optional<UserForm> form = ctx.elicit(
      "Please fill in your details",
      schema -> schema
          .string("username", "Your username")
          .string("email", "Your email", b -> b.email())
          .integer("age", "Your age"),
      UserForm.class);

  if (form.isEmpty()) {
    return new CallToolResult(
        List.of(new TextContent("User cancelled", null)), true, null);
  }
  UserForm userForm = form.get();
  // ... do something with the typed form
}
```

## Acceptance criteria

### Interface

- [ ] `McpStreamContext` declares two new overloads:
  - `<T> Optional<T> elicit(String message, Consumer<RequestedSchemaBuilder> schema, Class<T> resultType);`
  - `<T> Optional<T> elicit(String message, Consumer<RequestedSchemaBuilder> schema, TypeReference<T> resultType);`
- [ ] Both methods have javadoc describing: (a) that they delegate to
      the existing `elicit(message, schema)` path, (b) that they
      deserialize the content on accept via the framework's
      `ObjectMapper`, (c) that they return `Optional.empty()` for
      decline / cancel, (d) that schema validation still happens
      before bean binding.

### Implementation

- [ ] `DefaultMcpStreamContext` implements both overloads.
- [ ] The `Class<T>` overload body is approximately:
  ```java
  @Override
  public <T> Optional<T> elicit(
      String message, Consumer<RequestedSchemaBuilder> schema, Class<T> resultType) {
    ElicitResult result = elicit(message, schema);
    if (!result.isAccepted()) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.treeToValue(result.content(), resultType));
  }
  ```
- [ ] The `TypeReference<T>` overload mirrors this but uses
      `objectMapper.readerFor(objectMapper.constructType(resultType)).readValue(result.content())`
      or the equivalent Jackson 3 call for type-reference binding.
- [ ] Neither new overload duplicates the elicit flow — both delegate
      to the existing `elicit(String, Consumer<RequestedSchemaBuilder>)`
      method.
- [ ] A `JsonProcessingException` / `DatabindException` thrown by
      `treeToValue` propagates as-is to the tool author (it's a
      programming error — their bean type doesn't match the schema
      they built).

### Tests

- [ ] New unit tests in `DefaultMcpStreamContextTest` covering:
  - **Accept with matching bean**: stub the mailbox to return a
    `JsonRpcResult` whose content has the fields of a test record
    `UserForm(String name, int age)`; assert that
    `ctx.elicit(msg, schema, UserForm.class)` returns
    `Optional.of(new UserForm("Alice", 30))`.
  - **Accept with `TypeReference`**: same but for a generic type like
    `List<String>`.
  - **Decline returns empty**: stub the mailbox to return a
    decline response; assert `Optional.empty()`.
  - **Cancel returns empty**: same for cancel.
  - **Schema validation failure**: stub the mailbox to return an
    accept response whose content violates the schema (e.g., integer
    field with a string value); assert `McpElicitationException` is
    thrown BEFORE any attempt to bind to the bean.
  - **Mismatched bean type**: stub the mailbox to return valid
    content, but pass a bean class whose fields don't match; assert
    that the thrown exception is Jackson's
    `DatabindException`/`JsonProcessingException` (or a subclass),
    not `McpElicitationException`.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- **Dependency**: this spec depends on spec 105 having landed — the
  mailbox plumbing in the existing `elicit(String, Consumer)` method
  needs to be producing a model `ElicitResult` off a typed
  `Mailbox<JsonRpcResponse>`. Both are in place after 105.
- **ObjectMapper access**: `DefaultMcpStreamContext` already has
  `ObjectMapper` as a constructor-injected field. The new overloads
  use it directly.
- **`TypeReference` import**: Jackson 3's `TypeReference` lives at
  `tools.jackson.core.type.TypeReference`. It's the same shape as
  Jackson 2 — tool authors construct anonymous subclasses for
  generic type binding.
- **Don't add bean binding to model.ElicitResult itself**. Keep the
  framework-level convenience methods on the stream context, where
  the `ObjectMapper` dependency is already available. Adding
  `<T> T as(Class<T>)` to the model record would require either
  passing a mapper in at the call site (awkward API) or holding a
  mapper field on the record (breaks the "model is a spec mirror"
  principle).
- **Do not re-implement the elicit flow**. The two new overloads are
  thin wrappers over the existing `elicit(String, Consumer)` method.
  All the schema validation, mailbox correlation, and error handling
  lives in the existing method and must not be duplicated.
- **No sibling `sample(...)` overload**. Sampling returns free-form
  LLM output (text content blocks), not structured data. Bean
  binding doesn't make sense there.
- **Javadoc should reference that this is the replacement for the
  removed `elicitForm(Class<T>)` method** (which was deleted in spec
  100), so historical users searching for "elicit with class" find
  the new API.
- **Do not touch** the existing `elicit(String, Consumer)` method
  signature or behavior. It stays exactly as it is. The new overloads
  sit alongside it.
