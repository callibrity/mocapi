# Bind @JsonRpcParams to typed request records for all MCP spec methods

## What to build

Migrate every `@JsonRpcMethod` handler in `mocapi-core` that corresponds to
an MCP spec method so it takes a single `@JsonRpcParams`-annotated parameter
bound to the canonical request record from `mocapi-model`, instead of loose
primitive/map/`JsonNode` parameters.

`initialize` and `tools/call` have already been migrated — use those as the
reference pattern. The goal is that every handler signature reflects the
exact shape of the spec's request object.

### Handlers to migrate

| File | Method | Current signature | New signature |
|---|---|---|---|
| `McpLoggingMethods` | `logging/setLevel` | `setLevel(String level)` | `setLevel(@JsonRpcParams SetLevelRequestParams params)` |
| `McpToolMethods` | `tools/list` | `listTools(String cursor)` | `listTools(@JsonRpcParams PaginatedRequestParams params)` |
| `McpPromptMethods` | `prompts/list` | `listPrompts(String cursor)` | `listPrompts(@JsonRpcParams PaginatedRequestParams params)` |
| `McpPromptMethods` | `prompts/get` | `getPrompt(String name, Map<String, String> arguments)` | `getPrompt(@JsonRpcParams GetPromptRequestParams params)` |
| `McpCompletionMethods` | `completion/complete` | `complete(JsonNode ref, JsonNode argument)` | `complete(@JsonRpcParams CompleteRequestParams params)` |
| `McpResourceMethods` | `resources/list` | `listResources(String cursor)` | `listResources(@JsonRpcParams PaginatedRequestParams params)` |
| `McpResourceMethods` | `resources/templates/list` | `listResourceTemplates(String cursor)` | `listResourceTemplates(@JsonRpcParams PaginatedRequestParams params)` |
| `McpResourceMethods` | `resources/read` | `readResource(String uri)` | `readResource(@JsonRpcParams ResourceRequestParams params)` |
| `McpResourceMethods` | `resources/subscribe` | `subscribe(String uri)` | `subscribe(@JsonRpcParams ResourceRequestParams params)` |
| `McpResourceMethods` | `resources/unsubscribe` | `unsubscribe(String uri)` | `unsubscribe(@JsonRpcParams ResourceRequestParams params)` |

### Handlers that stay as-is

- `McpSessionMethods.ping()` — no params per spec.
- `McpSessionMethods.initialized()` — notification with optional `_meta`; no
  functional payload. Leave untouched unless migration is trivial.

Each migrated handler reads its data from the params record (e.g.
`params.cursor()`, `params.uri()`, `params.name()`, `params.arguments()`,
`params.level()`, `params.ref()`, `params.argument()`, `params.context()`)
and delegates to the same downstream logic it already does.

## Acceptance criteria

- [ ] Each handler listed above takes exactly one parameter annotated with
      `@JsonRpcParams` and typed to the corresponding record from
      `mocapi-model`.
- [ ] No handler listed above declares loose `String`/`Map`/`JsonNode`
      parameters for spec-defined request fields.
- [ ] All existing unit tests in `mocapi-core` pass without behavioral
      changes — if a test was asserting on the handler's parameter shape it
      may need updating, but the wire-level behavior must be identical.
- [ ] The `mocapi-compat` conformance suite continues to pass 39/39.
- [ ] `mvn verify` passes across the full reactor.
- [ ] `params == null` must be handled for methods where the request has no
      required fields (e.g., `tools/list` with no cursor sent by the
      client): treat a null params record the same as a record with all
      fields null.

## Implementation notes

- The reference pattern is already in place:
  - `McpSessionMethods.initialize(@JsonRpcParams InitializeRequestParams params)`
  - `McpToolMethods.callTool(@JsonRpcParams CallToolRequestParams params)`
- `JsonRpcParamsResolver` is registered with `HIGHEST_PRECEDENCE` via
  `RipCurlResolversAutoConfiguration`, so annotated parameters are
  deserialized correctly at runtime. No additional wiring is needed in
  production code.
- Any test that constructs `McpFooMethods` directly and bypasses the
  auto-configured method invoker factory must include
  `JsonRpcParamsResolver` in its resolver list alongside
  `Jackson3ParameterResolver`. This was already necessary for
  `StreamableHttpControllerTest` and may surface in other unit tests as
  they're migrated.
- If a client sends `tools/list` or `prompts/list` with no `params` object,
  `JsonRpcParamsResolver` should hand the handler a `null` record — the
  handler must tolerate that and treat it as "no cursor", matching current
  behavior. Add an explicit unit test for this.
- `PaginatedRequestParams` already exists and has `cursor` + `_meta`
  fields — it is the correct binding target for all four `*list*` methods.
- `ResourceRequestParams` binds `uri` and optional `_meta`; use it for
  `read`/`subscribe`/`unsubscribe`.
- Do not alter the wire format of responses — this spec only changes the
  *input* binding.
- Keep each handler's migration as a small, self-contained change; prefer
  migrating one file at a time and running the relevant tests between
  files.
- After migration, the pattern "every MCP handler binds to a typed record"
  should hold throughout `mocapi-core`, making future spec-compliance work
  straightforward (new fields appear on the record and are picked up
  automatically).
