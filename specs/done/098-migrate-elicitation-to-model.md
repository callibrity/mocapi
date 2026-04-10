# Migrate elicitation request/response construction to model records

## What to build

`DefaultMcpStreamContext.sendElicitationAndWait(String message, ObjectNode
schemaNode)` currently constructs the `elicitation/create` request by hand
as an `ObjectNode` with `mode`, `message`, and `requestedSchema` fields.
The model module already defines `ElicitRequestFormParams` that mirrors
this shape exactly.

Additionally, the helper methods `extractAction`, `extractContent`, and
`parseRawResponse` / `parseResponse` hand-parse the client's response by
walking raw `JsonNode`s for the `action` and `content` fields. These
should deserialize the response subtree into the `ElicitResult` model
record (`ElicitResult(ElicitAction action, Map<String, Object> content)`)
and operate on the typed record.

## Acceptance criteria

- [ ] `DefaultMcpStreamContext.sendElicitationAndWait()` constructs its
      request params as an `ElicitRequestFormParams` record and passes
      `valueToTree(record)` to `JsonRpcCall.of`. No `ObjectNode` /
      `createObjectNode` field-by-field construction for elicit request
      params remains in the file.
- [ ] `extractAction(JsonNode)` is replaced by code that deserializes the
      response's `result` subtree into `ElicitResult` via
      `objectMapper.treeToValue`. The `ElicitAction` enum is then read
      directly from the record.
- [ ] `extractContent(JsonNode)` either becomes a lookup on the typed
      `ElicitResult.content()` map, or is removed if the parsed record is
      used directly by callers.
- [ ] The existing JSON-RPC error detection (checking for `errorNode`
      before attempting to deserialize the result) still happens before
      the typed deserialization, so a client-returned error still throws
      `McpElicitationException` with the same message.
- [ ] Both `parseResponse` overloads (the `Class<T>` and `TypeReference<T>`
      flavors for `elicitForm`) still work end-to-end: they validate the
      `content` map against the generated schema, then bind it into the
      caller's typed result.
- [ ] The public `BeanElicitationResult<T>` and `ElicitationResult` types
      in `mocapi-core` stay unchanged — the same ergonomic helpers
      (`accepted()`, `declined()`, `cancelled()`, `getString()`, etc.)
      remain on the tool-author-facing surface. This spec only changes
      the internal wire construction and parsing.
- [ ] All existing elicitation tests in `DefaultMcpStreamContextTest`
      pass unchanged. Wire-format assertions on the request
      (`mode == "form"`, `requestedSchema` present, etc.) must still
      hold.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- Model types already available:
  - `ElicitRequestFormParams` — the request envelope (`mode`, `message`,
    `requestedSchema`, plus optional fields; pass `null` for any that
    aren't used by the form flow).
  - `ElicitResult` — `(ElicitAction action, Map<String, Object> content)`
  - `ElicitAction` — enum: `ACCEPT`, `DECLINE`, `CANCEL`
- The `content` field on `ElicitResult` is typed as
  `Map<String, Object>`, which means Jackson will deserialize the content
  blob into a plain map. The existing schema validation logic operates on
  a `JsonNode` representation of the content — that's fine; convert back
  via `objectMapper.valueToTree(elicitResult.content())` for the
  validator call.
- The existing path that returns a typed `BeanElicitationResult<T>` via
  `objectMapper.treeToValue(content, type)` should continue to work from
  the `JsonNode` form of the content.
- When the content map is needed by `ElicitationResult` (builder-based
  elicit), keep the existing `JsonNode` type on that facade — don't
  break its public shape — but source the data from the typed
  `ElicitResult.content()`.
- JSON-RPC error detection: the raw response envelope check (`get("error")
  != null → McpElicitationException`) must happen *before* any attempt to
  deserialize into `ElicitResult`, because an error response has no
  `action`/`content` fields and would fail deserialization. Preserve this
  ordering.
- Sampling has an analogous migration in spec 096/097 — once that's
  landed, the two flows will look structurally identical and could share
  a small helper for "unwrap result node, check for error, deserialize"
  if desired. Don't introduce that helper in this spec unless it falls
  out naturally.
