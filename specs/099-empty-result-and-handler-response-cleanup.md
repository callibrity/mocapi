# Introduce EmptyResult and clean up handler response types

## What to build

Several MCP-spec handlers currently return loose or ad-hoc types to produce
an empty `{}` JSON-RPC result, and one handler uses nested duplicate types
for a response shape that already exists in `mocapi-model`. Clean all of
this up:

### Part 1 — Introduce `EmptyResult` in `mocapi-model`

Add a zero-field record:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmptyResult() {
  public static final EmptyResult INSTANCE = new EmptyResult();
}
```

Jackson serializes a zero-field record to `{}`, which is what every
"empty result" MCP handler wants on the wire.

### Part 2 — Migrate "empty object" handlers

Replace the current ad-hoc return values with `EmptyResult.INSTANCE` in
all four spec-defined handlers that return `{}`:

| Handler | Current | New |
|---|---|---|
| `McpSessionMethods.ping` | `Map<?, ?>` return + `return Map.of();` | `EmptyResult` return + `return EmptyResult.INSTANCE;` |
| `McpLoggingMethods.setLevel` | `Object` return + `return new Object() {};` | `EmptyResult` return + `return EmptyResult.INSTANCE;` |
| `McpResourceMethods.subscribe` | `Map<String, Object>` return + `return Map.of();` | `EmptyResult` return + `return EmptyResult.INSTANCE;` |
| `McpResourceMethods.unsubscribe` | `Map<String, Object>` return + `return Map.of();` | `EmptyResult` return + `return EmptyResult.INSTANCE;` |

### Part 3 — Replace `CompleteResponse` / `CompletionResult` with model types

`McpCompletionMethods` declares two nested records (`CompleteResponse` and
`CompletionResult`) as its response shape. The `mocapi-model` module
already defines the canonical equivalents:

- `CompleteResult(Completion completion)` — replaces `CompleteResponse`
- `Completion(List<String> values, Integer total, Boolean hasMore)` —
  replaces the nested `CompletionResult` (and adds `total`, which
  matches the MCP spec)

Migrate `complete()` to:

```java
@JsonRpcMethod("completion/complete")
public CompleteResult complete(JsonNode ref, JsonNode argument) {
  return new CompleteResult(new Completion(List.of(), null, false));
}
```

Delete the nested `CompleteResponse` and `CompletionResult` records from
`McpCompletionMethods.java` — they are used nowhere else in the codebase
(verified in the parent conversation that led to this spec).

## Acceptance criteria

- [ ] New type `com.callibrity.mocapi.model.EmptyResult` exists with a
      zero-field record definition and a public
      `EmptyResult.INSTANCE` singleton.
- [ ] A unit test in `mocapi-model` serializes `EmptyResult.INSTANCE` to
      JSON and asserts the output equals `"{}"`.
- [ ] `McpSessionMethods.ping()` is declared `public EmptyResult ping()`
      and returns `EmptyResult.INSTANCE`.
- [ ] `McpLoggingMethods.setLevel(...)` returns `EmptyResult` and uses
      `EmptyResult.INSTANCE` — no more anonymous-class `new Object() {}`.
- [ ] `McpResourceMethods.subscribe(...)` and `unsubscribe(...)` both
      return `EmptyResult` and use `EmptyResult.INSTANCE` — no more
      `Map.of()`.
- [ ] `McpCompletionMethods.complete(...)` returns
      `com.callibrity.mocapi.model.CompleteResult` and constructs it with
      a `com.callibrity.mocapi.model.Completion`.
- [ ] The nested `CompleteResponse` and `CompletionResult` records are
      deleted from `McpCompletionMethods.java`.
- [ ] Wire-format behavior is unchanged for all five handlers: every one
      of them still produces a JSON-RPC `result` value that serializes to
      `{}` (or to the same `{"completion":{...}}` shape for
      `completion/complete`). Any existing unit or integration test that
      asserts on the wire format must still pass.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- Jackson serializes a record with zero components to `{}`. No custom
  serializer is needed. Verify this in the new model-module unit test.
- `EmptyResult` is useful enough that future handlers adding new
  spec-defined empty-result methods should reuse it; do not inline
  equivalent types in their files.
- The singleton pattern (`EmptyResult.INSTANCE`) is optional but keeps
  callers from allocating a pointless object per request. Jackson
  serializes the singleton identically to a freshly constructed instance.
- `Completion` in the model has an `Integer total` field that the old
  `CompletionResult` in core lacked. For the placeholder empty
  implementation, pass `null` for `total` — `@JsonInclude(NON_NULL)` on
  `Completion` will omit it from the wire.
- Double-check via `grep` that `CompleteResponse` and `CompletionResult`
  (the core nested ones) are referenced *only* from
  `McpCompletionMethods.java` before deleting. If any test imports them,
  migrate the test to the model types first.
- This spec intentionally does not touch `McpSessionMethods.initialized`
  (a notification, no return value) or `McpSessionMethods.initialize`
  (already returns a typed `InitializeResult`).
- Order of operations suggested: (1) add `EmptyResult` + its test, (2)
  migrate the four empty-result handlers, (3) migrate
  `McpCompletionMethods`. Commit between each part so the history is
  bisectable.
