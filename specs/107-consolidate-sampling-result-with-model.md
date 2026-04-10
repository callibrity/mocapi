# Consolidate SamplingResult with model.CreateMessageResult

## What to build

`mocapi-core/.../stream/SamplingResult.java` is a facade that mirrors
`mocapi-model/.../CreateMessageResult.java` but with a different
content type (`JsonNode` instead of `ContentBlock`) and an extra
`text()` helper method. This is the exact same duplication pattern
that was just cleaned up for `ElicitationResult` → `model.ElicitResult`.

Collapse it the same way:

1. **Add a `text()` helper to `model.CreateMessageResult`** that
   extracts the text from a `TextContent` content block. This mirrors
   how `model.ElicitResult` gained the typed accessors
   (`getString`, `getInteger`, etc.) from the core facade.
2. **Delete `core.SamplingResult`** entirely.
3. **Change `McpStreamContext.sample(...)` return type** from
   `SamplingResult` to `model.CreateMessageResult`.
4. **Simplify `DefaultMcpStreamContext.sample(...)` / `parseSamplingResult(...)`**
   to return the deserialized `CreateMessageResult` directly without
   re-wrapping.

### Model change — `CreateMessageResult.text()`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateMessageResult(
    Role role, ContentBlock content, String model, String stopReason) {

  /**
   * Returns the text of the {@code content} block if it is a {@link TextContent},
   * or {@code null} otherwise.
   */
  public String text() {
    return content instanceof TextContent textContent ? textContent.text() : null;
  }
}
```

Note: the old `SamplingResult.text()` worked by walking a `JsonNode`
with `content.get("text").asString()`. The new version uses a sealed
pattern match against `TextContent` — cleaner, type-safe, and
handles the "content is an `ImageContent`/`AudioContent`/etc." case
explicitly by returning `null`.

### Core deletion

`mocapi-core/src/main/java/com/callibrity/mocapi/stream/SamplingResult.java`
is deleted. Its only existing consumer is the return type of
`McpStreamContext.sample()` and the internal
`parseSamplingResult` helper in `DefaultMcpStreamContext`.

### Public API change on `McpStreamContext`

```java
// Today
SamplingResult sample(String prompt, int maxTokens);

// After
com.callibrity.mocapi.model.CreateMessageResult sample(String prompt, int maxTokens);
```

Tool authors calling `ctx.sample(prompt, maxTokens).text()` continue
to work unchanged — the `text()` helper method moves to
`CreateMessageResult` with the same signature and semantics (modulo
the type-safe improvement noted above).

Tool authors calling `result.role()` get `Role` (enum) instead of
`String`. If any existing caller does
`result.role().equals("assistant")` they need to update to
`result.role() == Role.ASSISTANT` or `"assistant".equals(result.role().toJson())`.
This is a minor breaking change that should be called out in the
commit message.

Tool authors calling `result.content()` get a `ContentBlock` (sealed
interface) instead of a `JsonNode`. If any existing caller walks the
JsonNode with `.get(...).asString()`, they need to pattern match on
the sealed type instead.

### Internal plumbing — `DefaultMcpStreamContext.parseSamplingResult`

Today (after spec 105 landed):

```java
private SamplingResult parseSamplingResult(JsonRpcResult result) {
  CreateMessageResult createMessageResult =
      objectMapper.treeToValue(result.result(), CreateMessageResult.class);
  String role = createMessageResult.role() != null ? createMessageResult.role().toJson() : null;
  JsonNode content = createMessageResult.content() != null
      ? objectMapper.valueToTree(createMessageResult.content())
      : null;
  return new SamplingResult(role, content, createMessageResult.model(), createMessageResult.stopReason());
}
```

Becomes:

```java
private CreateMessageResult parseSamplingResult(JsonRpcResult result) {
  return objectMapper.treeToValue(result.result(), CreateMessageResult.class);
}
```

The whole valueToTree hop and the String-role coercion vanish. The
method becomes a one-liner.

## Acceptance criteria

### Model changes

- [ ] `model.CreateMessageResult` gains a `text()` instance method
      that returns the text string of a `TextContent` content block,
      or `null` if the content is a different block type or null.
- [ ] A new unit test in `mocapi-model` covers:
  - `text()` returns the string when `content` is a `TextContent`.
  - `text()` returns `null` when `content` is any other `ContentBlock`
    variant (at least `ImageContent`).
  - `text()` returns `null` when `content` is null.

### Core deletion

- [ ] `mocapi-core/.../stream/SamplingResult.java` is deleted.
- [ ] No file or class named `SamplingResult` remains in
      `mocapi-core/src/main`.

### Public API

- [ ] `McpStreamContext.sample(String, int)` now declares a return
      type of `com.callibrity.mocapi.model.CreateMessageResult`.
- [ ] Javadoc on the method is updated to reference the typed
      `Role` and `ContentBlock` result fields.

### Internal plumbing

- [ ] `DefaultMcpStreamContext.parseSamplingResult(JsonRpcResult)`
      returns `CreateMessageResult` directly via
      `objectMapper.treeToValue(result.result(), CreateMessageResult.class)`.
      No re-wrapping, no `valueToTree` step, no String-role coercion.
- [ ] The sealed-switch pattern match in `sample()` continues to
      convert `JsonRpcError` responses to `McpSamplingException` as
      it does today.

### Downstream callers

- [ ] `mocapi-compat/.../ConformanceTools.java` (if it calls
      `ctx.sample(...)`) is updated to consume the new typed result.
- [ ] `mocapi-example/.../` if any example uses `ctx.sample(...)`,
      update it similarly.
- [ ] `DefaultMcpStreamContextTest`'s sampling tests are updated:
  - Assertions that used to check `result.role()` as a String now
    check `result.role() == Role.ASSISTANT` (or similar).
  - Assertions that used to walk `result.content()` as `JsonNode`
    now pattern-match the `ContentBlock`.
  - The `SamplingResult` import is removed; `CreateMessageResult` is
    added.

### Tests and build

- [ ] All existing sampling tests pass with the updated type.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- **Dependency**: this spec depends on spec 105 having landed (which
  retyped the mailbox to `Mailbox<JsonRpcResponse>` and refactored
  `parseSamplingResult` to take a typed `JsonRpcResult`). Both are in
  place after 105.
- **Pattern mirror**: this is the sampling analogue of the
  `ElicitationResult` → `model.ElicitResult` consolidation that
  landed earlier this session. Follow the same structure: add the
  helper to the model record, delete the core facade, update the
  `McpStreamContext` return type, update call sites.
- **Breaking change**: this IS a source-level breaking change for
  tool authors who access `result.role()` (was `String`, now `Role`
  enum) or `result.content()` (was `JsonNode`, now `ContentBlock`).
  The `text()` helper still works the same way for the common case
  of a `TextContent` response. Callers that only use `.text()` need
  no changes.
- **Why not keep SamplingResult as a thin wrapper**: The same logic
  that led to deleting `ElicitationResult` applies here: two types
  holding the same data in different shapes is strictly worse than
  one type with a richer public API. The `text()` helper moves cleanly
  to the model.
- **The `role()` type change from `String` to `Role` enum is
  desirable** — it prevents typos and lets callers pattern-match on
  the enum values. Any caller doing `.equals("assistant")` is fragile
  anyway.
- **Commit suggested as a single atomic change** — this is a small
  blast radius (one record added method, one facade deleted, one
  return type changed, one parser simplified, a handful of test
  updates). Splitting it doesn't help bisectability much.
