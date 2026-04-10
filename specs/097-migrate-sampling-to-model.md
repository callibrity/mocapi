# Migrate sampling request/response construction to model records

## What to build

`DefaultMcpStreamContext.sample(String prompt, int maxTokens)` currently
constructs the `sampling/createMessage` request by hand as nested
`ObjectNode`s (content block, message, params). It then hand-parses the
client's response by pulling individual string fields out of a raw
`JsonNode` inside `parseSamplingResponse`.

Both sides should use the typed records already in `mocapi-model`:

- **Request construction** — build a `CreateMessageRequestParams` record
  containing a `SamplingMessage` with a `TextContent` content block, then
  `valueToTree` it for the wire. No more `createObjectNode` /
  `createArrayNode` / field-by-field `put`s for the sampling request.
- **Response parsing** — deserialize the `result` subtree of the raw
  response into `CreateMessageResult` via
  `objectMapper.treeToValue(resultNode, CreateMessageResult.class)`.

The public `SamplingResult` facade type in `mocapi-core` **stays**. Tool
authors call `ctx.sample(...)` and receive a `SamplingResult` with its
convenient `.text()` helper — this spec is about internal wire
construction, not about breaking the tool-author surface. Internally,
`sample()` builds a `CreateMessageResult`, then maps it into a
`SamplingResult` (and serializes the content block back to `JsonNode` if
`SamplingResult` still exposes it as `JsonNode`).

## Acceptance criteria

- [ ] `DefaultMcpStreamContext.sample()` constructs the request params as
      a `CreateMessageRequestParams` record containing a `SamplingMessage`
      with `TextContent`. No `ObjectNode` / `createObjectNode` remains on
      the request-construction path for sampling.
- [ ] `DefaultMcpStreamContext.parseSamplingResponse()` deserializes the
      result subtree into `CreateMessageResult` via
      `objectMapper.treeToValue`. The manual
      `resultNode.get("role").asString()` / `.get("content")` etc.
      extraction is gone.
- [ ] The public `SamplingResult` type and its `.text()` helper remain
      unchanged. Callers of `ctx.sample()` see identical behavior.
- [ ] Error paths are preserved: JSON-RPC error in the response still
      throws `McpSamplingException`; missing/empty result still handled;
      timeout still throws `McpSamplingTimeoutException`; client-lacks-
      sampling-support still throws `McpSamplingNotSupportedException`.
- [ ] All existing `DefaultMcpStreamContextTest` sampling tests pass
      without being loosened. If a test asserts on the exact wire format
      of the request, that's the regression guard we want — confirm the
      byte-level output matches.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- Model types already available:
  - `CreateMessageRequestParams` — request envelope with `messages`,
    `maxTokens`, and optional fields like `systemPrompt`,
    `includeContext`, `temperature`, etc. For this migration, populate
    only `messages` and `maxTokens`; pass `null` for the rest.
  - `SamplingMessage` — `(Role role, ContentBlock content)`
  - `TextContent` — one of the `ContentBlock` variants
  - `Role` — enum with `USER`, `ASSISTANT`
  - `CreateMessageResult` — `(Role role, ContentBlock content, String model, String stopReason)`
- `SamplingResult` currently types `content` as `JsonNode`. When mapping
  `CreateMessageResult` → `SamplingResult`, serialize the `ContentBlock`
  back to `JsonNode` via `objectMapper.valueToTree(createMessageResult.content())`
  so the `.text()` helper continues to work.
- If `SamplingResult.text()` would be cleaner as a direct access to
  `TextContent.text()` once we have typed content, that's a future
  refactor — keep it out of scope for this spec.
- Outgoing request envelope: `JsonRpcCall.of("sampling/createMessage",
  objectMapper.valueToTree(createMessageRequestParams), idNode)`. The
  envelope construction itself doesn't need to change.
- Do not delete `SamplingResult` or alter the public `McpStreamContext`
  surface in this spec — that would be a breaking change for tool
  authors and belongs in a separate spec.
