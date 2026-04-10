# Upgrade client-response mailbox to Mailbox<JsonRpcResponse>

## What to build

The server waits for client responses to server-initiated requests
(`sampling/createMessage`, `elicitation/create`) via a substrate
`Mailbox`. Today the mailbox is typed as `Mailbox<JsonNode>`, the
controller delivers raw tree nodes into it, and the receivers sniff
the tree manually for `result` / `error` fields to distinguish success
from failure.

Now that ripcurl 2.2.0 has made `JsonRpcResponse` Jackson-polymorphic
(deserializable via `@JsonCreator`), we can tighten the entire
transport chain to `Mailbox<JsonRpcResponse>` and let the type system
do the discrimination. This spec applies the upgrade to **both**
sampling and elicitation flows, since they share the same delivery
path in the controller.

The primary motivation is **sampling** — the user asked for this
spec specifically because the sampling receiver currently walks a
raw `JsonNode` with `rawResponse.get("error")` / `rawResponse.get("result")`
probes, which is the same ad-hoc pattern that elicit was doing before
spec 098 tightened its internals. With a typed mailbox, both flows
collapse to a clean sealed `switch` over `JsonRpcResult` /
`JsonRpcError`.

### Controller delivery side — `StreamableHttpController`

`handleClientResult(JsonRpcResult result, String sessionId)` currently
does:

```java
Mailbox<JsonNode> mailbox =
    mailboxFactory.create("elicit:" + idNode.asString(), JsonNode.class);
mailbox.deliver(result.result());   // ← delivers only the inner payload
```

Change to:

```java
Mailbox<JsonRpcResponse> mailbox =
    mailboxFactory.create(idNode.asString(), JsonRpcResponse.class);
mailbox.deliver(result);   // ← delivers the whole typed response
```

Similarly for `handleClientError(JsonRpcError error, String sessionId)`:

```java
// today — wraps the error in an ad-hoc ObjectNode
ObjectNode errorWrapper = objectMapper.createObjectNode();
errorWrapper.set("error", objectMapper.valueToTree(error.error()));
Mailbox<JsonNode> mailbox =
    mailboxFactory.create("elicit:" + idNode.asString(), JsonNode.class);
mailbox.deliver(errorWrapper);
```

Change to:

```java
Mailbox<JsonRpcResponse> mailbox =
    mailboxFactory.create(idNode.asString(), JsonRpcResponse.class);
mailbox.deliver(error);
```

The ad-hoc `{"error": ...}` ObjectNode wrapper is **deleted**. The
`objectMapper.createObjectNode()` + `set("error", ...)` dance goes
away.

### Mailbox address — drop the `"elicit:"` prefix

The current channel name `"elicit:" + id` is a misnomer (sampling also
uses it). Both flows already converge on the same mailbox channel by
id, and UUIDs are globally unique, so there's zero collision risk
between sampling and elicitation flows. Drop the prefix — use just
the raw `jsonRpcId` (the UUID string) as the address in every
`mailboxFactory.create(...)` call.

Affected call sites:
- `StreamableHttpController.handleClientResult`
- `StreamableHttpController.handleClientError`
- `DefaultMcpStreamContext.sample` (creates the mailbox)
- `DefaultMcpStreamContext.sendElicitationAndWait` (creates the mailbox)

All four must use the same naming convention so caller and feeder
converge on the same channel.

### Sampling receiver — `DefaultMcpStreamContext.sample`

Replace today's `Mailbox<JsonNode>` + manual field probing with typed
pattern matching:

```java
// Today
Mailbox<JsonNode> mailbox = mailboxFactory.create("elicit:" + jsonRpcId, JsonNode.class);
try {
  stream.publishJson(toJsonTree(request));
  Optional<JsonNode> raw = mailbox.poll(elicitationTimeout);
  if (raw.isEmpty()) {
    throw new McpSamplingTimeoutException("Sampling timed out after " + elicitationTimeout);
  }
  return parseSamplingResponse(raw.get());
} finally {
  mailbox.delete();
}

// Where parseSamplingResponse is:
private SamplingResult parseSamplingResponse(JsonNode rawResponse) {
  JsonNode errorNode = rawResponse.get("error");
  if (errorNode != null) {
    throw new McpSamplingException("Client returned JSON-RPC error: " + errorNode);
  }
  JsonNode resultNode = rawResponse.get("result");
  if (resultNode == null) {
    resultNode = rawResponse;
  }
  CreateMessageResult result = objectMapper.treeToValue(resultNode, CreateMessageResult.class);
  ...
}
```

Becomes:

```java
Mailbox<JsonRpcResponse> mailbox =
    mailboxFactory.create(jsonRpcId, JsonRpcResponse.class);
try {
  stream.publishJson(toJsonTree(request));
  Optional<JsonRpcResponse> raw = mailbox.poll(elicitationTimeout);
  if (raw.isEmpty()) {
    throw new McpSamplingTimeoutException("Sampling timed out after " + elicitationTimeout);
  }
  return switch (raw.get()) {
    case JsonRpcError error ->
        throw new McpSamplingException("Client returned JSON-RPC error: " + error.error());
    case JsonRpcResult result -> parseSamplingResult(result);
  };
} finally {
  mailbox.delete();
}

// parseSamplingResult takes the typed JsonRpcResult directly
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

Gone: the `rawResponse.get("error")` probe, the `resultNode == null`
fallback (which existed to handle cases where the mailbox delivered
just the inner payload vs. the full envelope). With the typed
mailbox, the envelope is always present and correctly discriminated
at the sealed-switch level.

### Elicitation receiver — `DefaultMcpStreamContext.sendElicitationAndWait`

Same transformation. The method currently returns `JsonNode` so
`parseRawResponse` can probe it; after this spec it should return an
already-discriminated `JsonRpcResult` (or throw for errors/timeouts),
and `parseRawResponse` takes the typed result directly.

```java
private JsonNode sendElicitationAndWait(String message, RequestedSchema requestedSchema) { ... }
```

becomes:

```java
private JsonRpcResult sendElicitationAndWait(String message, RequestedSchema requestedSchema) {
  String jsonRpcId = UUID.randomUUID().toString();
  // ... build request ...
  Mailbox<JsonRpcResponse> mailbox =
      mailboxFactory.create(jsonRpcId, JsonRpcResponse.class);
  try {
    stream.publishJson(toJsonTree(request));
    Optional<JsonRpcResponse> raw = mailbox.poll(elicitationTimeout);
    if (raw.isEmpty()) {
      throw new McpElicitationTimeoutException("Elicitation timed out after " + elicitationTimeout);
    }
    return switch (raw.get()) {
      case JsonRpcError error ->
          throw new McpElicitationException("Client returned JSON-RPC error: " + error.error());
      case JsonRpcResult result -> result;
    };
  } finally {
    mailbox.delete();
  }
}
```

And `parseRawResponse(JsonNode, ObjectNode)` becomes
`parseRawResponse(JsonRpcResult, ObjectNode)` — taking the typed
result, calling `objectMapper.treeToValue(result.result(), ElicitResult.class)`,
and validating against the schema as it does today.

## Acceptance criteria

### Transport type

- [ ] Every `mailboxFactory.create(...)` call in
      `StreamableHttpController.handleClientResult`,
      `StreamableHttpController.handleClientError`,
      `DefaultMcpStreamContext.sample`, and
      `DefaultMcpStreamContext.sendElicitationAndWait` declares its
      mailbox as `Mailbox<JsonRpcResponse>` with
      `JsonRpcResponse.class` as the class token.
- [ ] No `Mailbox<JsonNode>` remains in any of those four call sites.
- [ ] The `"elicit:"` prefix is removed from the mailbox address in
      all four call sites — the address is just `jsonRpcId` (or
      `idNode.asString()` on the controller side).

### Controller delivery

- [ ] `handleClientResult` delivers the full `JsonRpcResult` (not just
      `result.result()`) into the mailbox.
- [ ] `handleClientError` delivers the full `JsonRpcError` directly.
      The ad-hoc `objectMapper.createObjectNode()` + `set("error", ...)`
      wrapper is deleted.

### Sampling receiver

- [ ] `DefaultMcpStreamContext.sample` polls a
      `Mailbox<JsonRpcResponse>` and pattern-matches the returned
      value with a sealed switch over `JsonRpcResult` / `JsonRpcError`.
- [ ] A `JsonRpcError` from the mailbox throws `McpSamplingException`
      with a message that includes the error detail from
      `error.error()` (the `JsonRpcErrorDetail`, not the raw JsonNode).
- [ ] A `JsonRpcResult` from the mailbox is deserialized via
      `objectMapper.treeToValue(result.result(), CreateMessageResult.class)`.
      The `rawResponse.get("error")` probe, the
      `rawResponse.get("result")` probe, and the `resultNode == null`
      fallback are all deleted.
- [ ] The `SamplingResult` returned to tool authors is unchanged on
      the wire/public surface. Only the internal plumbing that
      produces it changes.
- [ ] `McpSamplingTimeoutException`, `McpSamplingException`, and
      `McpSamplingNotSupportedException` continue to be thrown in the
      same scenarios as today.

### Elicitation receiver

- [ ] `DefaultMcpStreamContext.sendElicitationAndWait` returns a
      `JsonRpcResult` (not a `JsonNode`) and handles timeout / error
      cases via the sealed switch over `JsonRpcResponse`.
- [ ] `parseRawResponse` is updated to accept a `JsonRpcResult` and
      deserialize its `result()` tree into a model `ElicitResult`, as
      it does today — the intermediate `JsonNode` hop is removed.
- [ ] The public `elicit(...)` method on `McpStreamContext` has
      unchanged return semantics — same `ElicitResult` to tool
      authors, same behavior for decline / cancel / accept paths,
      same schema validation on accept.

### Tests

- [ ] `DefaultMcpStreamContextTest` tests that stub the mailbox with
      `mailboxFactory.create(any(String.class), eq(JsonNode.class))`
      are updated to stub for `JsonRpcResponse.class` instead.
- [ ] Mailbox mock return values are changed from `Optional<JsonNode>`
      to `Optional<JsonRpcResponse>`, with the mocks returning typed
      `JsonRpcResult` / `JsonRpcError` instances instead of raw
      `ObjectNode` trees.
- [ ] A new test verifies that a `JsonRpcError` delivered to the
      sampling mailbox throws `McpSamplingException` with the error
      detail's message — regression guard for the error branch.
- [ ] A new test verifies the analogous elicitation error branch
      throws `McpElicitationException`.
- [ ] `StreamableHttpControllerTest` tests that previously sent
      `{"jsonrpc":"2.0","result":...,"id":"x"}` POST bodies and
      checked that the mailbox received the parsed result continue to
      pass — `handleClientResult` now delivers the full
      `JsonRpcResult` but the semantics (mailbox delivery for
      correlated id) are the same.
- [ ] `mvn verify` passes across the full reactor.
- [ ] `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- **Ripcurl prerequisite is already satisfied** — ripcurl 2.2.0 (cut
  earlier this session) makes `JsonRpcResponse` Jackson-polymorphic
  via `@JsonCreator(mode = DELEGATING)`. Mocapi already depends on
  `ripcurl.version = 2.2.0-SNAPSHOT` (bumped as part of the
  controller cleanup that landed with the `@RequestBody JsonRpcMessage`
  change). No cross-repo work needed for this spec.
- **In-memory substrate** (the fallback mocapi uses by default)
  stores Java references directly — no serialization happens, so the
  `@JsonCreator` machinery isn't exercised for the in-memory path.
  But Redis-backed substrate would round-trip through Jackson, and
  `@JsonCreator` handles that correctly (verified by the ripcurl 2.2.0
  test suite). So this spec works in both substrate modes.
- **Drop the `"elicit:"` prefix across all four call sites in a
  single commit** — partial application would mean the caller and
  feeder look for different channel names and the response never
  arrives.
- **Mailbox mock updates in tests** — the existing
  `DefaultMcpStreamContextTest` uses `mockMailbox()` helpers. Update
  the helper return type from `Mailbox<JsonNode>` to
  `Mailbox<JsonRpcResponse>`, and update all `Optional.of(responseNode)`
  stubs to pass typed `JsonRpcResult(...)` or `JsonRpcError(...)`
  instances.
- **Error message equivalence** — today the sampling error message is
  `"Client returned JSON-RPC error: " + errorNode` (where errorNode
  is a JsonNode rendered as JSON). After this spec it becomes
  `"Client returned JSON-RPC error: " + error.error()` (where
  `error.error()` is a `JsonRpcErrorDetail` record whose toString
  includes `code` and `message`). The message wording will differ
  slightly. If any test asserts on the exact error message string, it
  needs updating.
- **`rawResponse.get("result")` fallback deletion** — the old code
  had a `resultNode == null → resultNode = rawResponse` fallback. This
  existed because the mailbox sometimes delivered the full envelope
  and sometimes just the inner payload, depending on the code path.
  With the typed mailbox, the delivered value is always a
  `JsonRpcResult` whose `.result()` is always the inner payload. The
  fallback is no longer needed and should be deleted.
- **Commit granularity suggested**:
  1. Controller delivery side: change `handleClientResult` and
     `handleClientError` to use `Mailbox<JsonRpcResponse>`, drop the
     `"elicit:"` prefix, drop the ObjectNode error wrapper. Update
     any test that verifies the controller's delivery behavior.
  2. Sampling receiver: retype, pattern-match, simplify
     `parseSamplingResponse` → `parseSamplingResult`. Update sampling
     tests.
  3. Elicitation receiver: retype, pattern-match, simplify
     `sendElicitationAndWait` return, simplify `parseRawResponse`.
     Update elicitation tests.
  Each commit should leave the full reactor green.
- **Do not touch** `SamplingResult`, `McpStreamContext.sample`
  signature, `McpStreamContext.elicit` signature, or any
  tool-author-facing API. This spec is purely internal plumbing.
