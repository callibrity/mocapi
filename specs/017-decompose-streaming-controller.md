# Extract MCP protocol logic from controller

## What to build

`McpStreamingController` is over 500 lines and mixes HTTP transport concerns with MCP
protocol logic. Extract the protocol logic into `mocapi-core` so it's independently
testable without HTTP, SSE, or Odyssey. The controller becomes a thin HTTP adapter.

### Extract `McpProtocol` into `mocapi-core`

Create an `McpProtocol` class (or small set of classes) in `mocapi-core` that owns
all MCP protocol logic:

**JSON-RPC validation and formatting:**
- Validate JSON-RPC envelope (`jsonrpc: "2.0"`, valid `id` type)
- Detect notification vs response vs request
- Build JSON-RPC success responses
- Build JSON-RPC error responses

**MCP header validation:**
- Protocol version validation (known version list)
- Origin validation (URI parsing + host check against allowed origins)
- Accept header validation (POST requires both JSON and SSE; GET requires SSE)

**Session resolution:**
- `initialize` creates a new session
- Other methods require a session ID, return error if missing or expired

**Method dispatch:**
- Handler registry: `Map<String, McpMethodHandler>`
- `McpMethodHandler` sealed interface with `Json` and `Streaming` variants
- Handler lookup by method name
- The `Json` variant takes params and returns a result
- The `Streaming` variant takes params and an `McpStreamContext` and returns a result

The protocol layer tells the transport layer "this method needs streaming" but does not
know what streaming means. It has no dependency on Odyssey, SseEmitter, or Spring Web.

### Slim down `McpStreamingController`

After extraction, the controller is a thin HTTP adapter (~150 lines):

1. Read HTTP headers and request body
2. Delegate to `McpProtocol` for validation — map validation failures to HTTP status
   codes (400, 403, 404, 406)
3. For notifications/responses — call protocol handler, return 202
4. For JSON method handlers — call protocol handler, return `application/json`
5. For streaming method handlers — create Odyssey ephemeral stream, create
   `DefaultMcpStreamContext`, dispatch on virtual thread, return `SseEmitter`
6. GET handler — subscribe to session's notification stream via Odyssey
7. DELETE handler — terminate session

The controller owns the Odyssey and SSE wiring. The protocol layer owns everything else.

### What stays in `mocapi-autoconfigure`

- `McpStreamingController` — HTTP adapter
- `DefaultMcpStreamContext` — implements `McpStreamContext` using Odyssey + Substrate
- `McpSessionManager` — manages session lifecycle (may move to core if it has no
  transport dependencies)
- Auto-configuration

### What moves to `mocapi-core`

- `McpProtocol` (or equivalent classes) — all validation, dispatch, JSON-RPC formatting
- `McpMethodHandler` sealed interface
- `McpStreamContext` interface (from spec 016)
- Session resolution logic

## Acceptance criteria

- [ ] MCP protocol logic (validation, dispatch, JSON-RPC formatting) lives in
      `mocapi-core` with no dependency on Spring Web, Odyssey, or SSE
- [ ] Protocol logic is independently unit tested without HTTP infrastructure
- [ ] `McpStreamingController` delegates to the protocol layer
- [ ] `McpStreamingController` is under 200 lines
- [ ] `McpStreamingController` only contains HTTP/SSE/Odyssey transport concerns
- [ ] No behavior changes — all existing HTTP-level tests still pass
- [ ] New unit tests exist for the extracted protocol classes
- [ ] `mvn verify` passes

## Implementation notes

- This is a refactoring spec — no new features, just restructuring. All existing
  behavior must be preserved.
- `McpProtocol` does not need to be a single class. It could be a few focused classes
  (e.g., `JsonRpcMessages` for formatting, `McpRequestValidator` for header checks,
  `McpMethodRegistry` for dispatch). The implementation should choose whatever
  structure keeps each class small and focused.
- The sealed `McpMethodHandler` interface should move to `mocapi-core` alongside
  `McpStreamContext`. This lets capabilities register handlers that reference
  `McpStreamContext` without depending on `mocapi-autoconfigure`.
- `McpSession` and `McpSessionManager` may be candidates for moving to core as well,
  since session identity and lifecycle are protocol concepts. The only transport-coupled
  part is the `OdysseyStream` reference on `McpSession` — that could be held in the
  controller or autoconfigure layer instead.
- This spec should run BEFORE 016 (McpStreamContext interface). Once protocol logic is
  cleanly separated, extracting the interface is straightforward.
