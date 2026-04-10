# Use model types to build IT test request params

## What to build

Many integration tests in `mocapi-compat/src/test` construct
JSON-RPC request params by building `ObjectNode` trees field-by-
field:

```java
ObjectNode callParams = client.objectMapper().createObjectNode();
callParams.put("name", "echo");
ObjectNode arguments = callParams.putObject("arguments");
arguments.put("message", "hello world");
```

This is the same anti-pattern the framework's production code got
rid of earlier this session (specs 096, 097, 098, 100, 101, 102,
105, 107) — building wire payloads by hand from raw Jackson nodes
instead of using the typed records in `mocapi-model`. Tests should
follow the same principle: build request params as typed model
records, then let Jackson serialize them into the JSON-RPC
envelope.

Replace every hand-rolled `ObjectNode` / `objectMapper.createObjectNode()
.put(...)` construction in IT test request builders with the
corresponding typed record from `mocapi-model`.

### Example transformation

**Before**:
```java
ObjectNode callParams = client.objectMapper().createObjectNode();
callParams.put("name", "echo");
ObjectNode arguments = callParams.putObject("arguments");
arguments.put("message", "hello world");

client.post(sessionId, "tools/call", callParams, idNode)
    .andExpect(status().isOk())
    ...;
```

**After**:
```java
var arguments = client.objectMapper().createObjectNode();
arguments.put("message", "hello world");

var callParams = new CallToolRequestParams("echo", arguments, null, null);

client.post(sessionId, "tools/call", client.objectMapper().valueToTree(callParams), idNode)
    .andExpect(status().isOk())
    ...;
```

Or — if `McpClient` (the test helper) has a convenience method
that accepts a model record directly — use that:

```java
var arguments = client.objectMapper().createObjectNode();
arguments.put("message", "hello world");

var callParams = new CallToolRequestParams("echo", arguments, null, null);
client.callTool(sessionId, callParams, idNode)
    .andExpect(status().isOk())
    ...;
```

Adding such a convenience method to `McpClient` is part of this
spec (see below).

### Where the ObjectNode-construction anti-pattern lives

Grep for the common shapes in `mocapi-compat/src/test`:

```bash
grep -rn "createObjectNode\(\)" mocapi-compat/src/test/java --include="*.java"
grep -rn "putObject\(" mocapi-compat/src/test/java --include="*.java"
```

Expected hotspots (from the user's example):
- `ToolsCallSimpleTextIT.java`
- `ToolsCallImageIT.java`, `ToolsCallAudioIT.java`,
  `ToolsCallEmbeddedResourceIT.java`, `ToolsCallMixedContentIT.java`
- `ToolsCallWithLoggingIT.java`, `ToolsCallWithProgressIT.java`,
  `ToolsCallErrorIT.java`
- `ToolsCallSamplingIT.java`, `ToolsCallElicitationIT.java`
- `PromptsGetSimpleIT.java`, `PromptsGetWithArgsIT.java`,
  `PromptsGetEmbeddedResourceIT.java`, `PromptsGetWithImageIT.java`
- `ResourcesReadTextIT.java`, `ResourcesReadBinaryIT.java`,
  `ResourcesSubscribeIT.java`, `ResourcesUnsubscribeIT.java`,
  `ResourcesTemplatesReadIT.java`, `ResourcesListIT.java`
- `CompletionCompleteIT.java`
- `LoggingSetLevelIT.java`
- `PingIT.java`
- `ServerInitializeIT.java`
- `ElicitationSep1034DefaultsIT.java`,
  `ElicitationSep1330EnumsIT.java`
- Every other `*IT.java` that builds a request body

Basically every IT test that posts a JSON-RPC call to `/mcp`.

### Model types to use

| Request method | Model type |
|---|---|
| `initialize` | `InitializeRequestParams` |
| `tools/list` | `PaginatedRequestParams` |
| `tools/call` | `CallToolRequestParams` |
| `prompts/list` | `PaginatedRequestParams` |
| `prompts/get` | `GetPromptRequestParams` |
| `resources/list` | `PaginatedRequestParams` |
| `resources/templates/list` | `PaginatedRequestParams` |
| `resources/read` | `ResourceRequestParams` |
| `resources/subscribe` | `ResourceRequestParams` |
| `resources/unsubscribe` | `ResourceRequestParams` |
| `completion/complete` | `CompleteRequestParams` |
| `logging/setLevel` | `SetLevelRequestParams` |
| `elicitation/create` | `ElicitRequestFormParams` or `ElicitRequestURLParams` |
| `sampling/createMessage` | `CreateMessageRequestParams` |

All exist in `com.callibrity.mocapi.model`. All are records.
All serialize to the correct wire format via Jackson.

### `McpClient` helper extension

`McpClient` is the test helper class in `mocapi-compat/src/test`
that wraps `MockMvc` for JSON-RPC calls. It currently accepts
`ObjectNode` params directly:

```java
public ResultActions post(String sessionId, String method,
    ObjectNode params, JsonNode id) throws Exception {
  // ... builds a JsonRpcCall and POSTs it
}
```

Add an overload that accepts any model params record:

```java
public ResultActions post(String sessionId, String method,
    Object params, JsonNode id) throws Exception {
  JsonNode paramsNode = params == null ? null :
      params instanceof JsonNode jn ? jn : objectMapper.valueToTree(params);
  // ... same POST logic
}
```

With this overload, tests can pass a record directly:

```java
client.post(sessionId, "tools/call",
    new CallToolRequestParams("echo", arguments, null, null), idNode);
```

No manual `valueToTree` at the call site.

### What to leave alone

- **Raw `JsonNode` / `ObjectNode` uses that are genuinely
  dynamic** — e.g., tests that construct an arbitrary JSON
  payload to verify the server rejects malformed input.
  Those should stay as raw node construction because the
  whole point is that they're *not* valid per the typed
  model.
- **`arguments`-level object construction**. The `arguments`
  field on `CallToolRequestParams` is typed as `JsonNode`
  (not a specific record) because each tool's arguments
  schema is different. Tests can legitimately build the
  arguments via `objectMapper.createObjectNode().put(...)`
  — the outer `CallToolRequestParams` wrapper becomes typed,
  but the inner arguments stay as `JsonNode`. See the
  "Example transformation" section above.
- **`ElicitRequestFormParams` schema construction**. Similar
  story — `requestedSchema` is typed as `RequestedSchema`
  which has its own builder. Use the model type for the
  outer params, the builder for the schema.

## Acceptance criteria

- [ ] Every `mocapi-compat/src/test/java/**/*IT.java` that
      currently builds JSON-RPC request params via
      `ObjectNode` / `createObjectNode()` / `putObject()` is
      updated to use the corresponding `*RequestParams`
      record from `mocapi-model`.
- [ ] The `McpClient` test helper has a new overload
      `post(String sessionId, String method, Object params,
      JsonNode id)` that accepts a model record (or `null`,
      or a raw `JsonNode` for the edge cases).
- [ ] Tests that build `tools/call` arguments (the inner
      object) still use `createObjectNode()` for the
      arguments field — that's legitimate because the
      arguments schema is tool-specific. The outer
      `CallToolRequestParams` wrapper becomes typed.
- [ ] Tests that deliberately construct malformed or
      dynamic payloads (e.g., to test error handling for
      bad input) may retain their raw `ObjectNode`
      construction. Document this in a comment in each such
      test.
- [ ] Grep for `createObjectNode\(\)` in
      `mocapi-compat/src/test/java` shows only the
      legitimate cases after this spec lands.
- [ ] All existing IT tests continue to pass — this is a
      pure refactor; the wire format is unchanged.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **This is a docs/refactor-scale spec** — many files, each
  with a small mechanical change. Commit per test file or
  per test class nested group for reviewability.
- **Don't change test names, assertions, or expected
  responses**. The refactor is strictly about how the
  request is BUILT; everything else stays the same.
- **The pattern mirrors the production cleanup specs**:
  spec 095 (bind `@JsonRpcParams` to typed records) taught
  the production handlers to consume typed records; spec
  145 teaches the tests to produce them. The full loop is
  now typed on both sides.
- **Don't add new fields to the model records in this spec**.
  If an IT test currently omits a field and the typed record
  requires it, pass `null` for the missing field in the
  record constructor. If that's awkward, add a convenience
  static factory method on the record in a follow-up spec
  — but don't bundle model changes with test changes.
- **`McpClient.post` method naming**: if the existing method
  already accepts `ObjectNode`, don't delete it — add the
  `Object`-typed overload alongside. Tests that still use
  raw `ObjectNode` (the legitimate edge cases) continue to
  compile.
- **Commit granularity**:
  1. Add the `McpClient` overload (small, enables
     everything else).
  2. Migrate IT tests in logical groups (tools, prompts,
     resources, etc.) one commit per group.
  3. Final grep check: confirm only the legitimate raw
     `ObjectNode` cases remain.
- **Mirror with spec 121** (complete model test coverage):
  spec 121 adds round-trip tests for every model record.
  This spec (145) exercises those same records in end-to-
  end IT tests. Together, they cover the model from both
  the unit-test side and the integration side.
