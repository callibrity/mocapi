# Integrate RipCurl for JSON-RPC dispatch

## What to build

Replace Mocapi's hand-built JSON-RPC dispatch infrastructure (`McpProtocol`,
`McpMethodRegistry`, `McpMethodHandler`, `JsonRpcMessages`, `McpRequestValidator`)
with RipCurl 0.2.0. RipCurl provides annotation-driven method routing, pluggable
parameter resolution, and JSON-RPC envelope validation — everything Mocapi reimplemented
in specs 012 and 017.

### Add `ripcurl-core` dependency

Add `com.callibrity.ripcurl:ripcurl-core:0.2.0` to `mocapi-core/pom.xml`. Do NOT add
`ripcurl-autoconfigure` or the starter — Mocapi provides its own controller.

Add `com.callibrity.ripcurl:ripcurl-autoconfigure:0.2.0` to
`mocapi-autoconfigure/pom.xml` for the `@JsonRpcService` bean scanning and dispatcher
auto-configuration.

### Create `@JsonRpcService` classes for MCP methods

Replace the inline handler registration in `MocapiAutoConfiguration` with annotated
service classes. Each MCP method becomes a `@JsonRpc`-annotated method on a
`@JsonRpcService` bean.

**McpServerMethods** (new class in `mocapi-core` or `mocapi-autoconfigure`):

```java
@JsonRpcService
public class McpServerMethods {

    @JsonRpc("initialize")
    public McpServer.InitializeResponse initialize(
            String protocolVersion,
            ClientCapabilities capabilities,
            ClientInfo clientInfo) {
        return mcpServer.initialize(protocolVersion, capabilities, clientInfo);
    }

    @JsonRpc("ping")
    public McpServer.PingResponse ping() {
        return mcpServer.ping();
    }

    @JsonRpc("notifications/initialized")
    public void initialized() {
        mcpServer.clientInitialized();
    }
}
```

RipCurl's `JsonMethodInvoker` automatically deserializes `protocolVersion`,
`capabilities`, and `clientInfo` from the JSON-RPC `params` object — no manual
`objectMapper.treeToValue()` calls needed.

**McpToolMethods** (new class, only created if `McpToolsCapability` is available):

```java
@JsonRpcService
public class McpToolMethods {

    @JsonRpc("tools/list")
    public McpToolsCapability.ListToolsResponse listTools(String cursor) {
        return toolsCapability.listTools(cursor);
    }

    @JsonRpc("tools/call")
    public McpToolsCapability.CallToolResponse callTool(
            String name,
            ObjectNode arguments) {
        return toolsCapability.callTool(name, arguments);
    }
}
```

### Register `McpStreamContext` param resolver

Create a `JsonRpcParamResolver` bean that provides `McpStreamContext` instances. When
a `@JsonRpc` method declares an `McpStreamContext` parameter, this resolver injects it.
When it doesn't, the resolver returns null and JSON resolution proceeds normally.

The resolver needs to be request-scoped or thread-local since each invocation may have
a different `McpStreamContext` (or none). The controller sets the current context before
dispatching and clears it after.

### Delete hand-built dispatch classes from `mocapi-core`

Remove:
- `McpProtocol` — replaced by RipCurl's `JsonRpcDispatcher`
- `McpMethodRegistry` — replaced by RipCurl's `@JsonRpcService` scanning
- `McpMethodHandler` — replaced by `@JsonRpc` annotated methods
- `JsonRpcMessages` — replaced by RipCurl's `JsonRpcResponse`/`JsonRpcException`

### Delete `McpException` hierarchy from `mocapi-core`

Mocapi's `McpException` (with `getCode()`) is now redundant. Throw
`JsonRpcException` from RipCurl directly with the appropriate error code:

```java
throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Tool not found: " + name);
```

Remove `McpException`, `McpInvalidParamsException`, `McpInternalErrorException`, and
update all throw sites to use `JsonRpcException`.

### Keep `McpRequestValidator`

The MCP-specific header validation (protocol version, origin, Accept headers) is NOT
part of JSON-RPC and RipCurl doesn't handle it. `McpRequestValidator` stays in
`mocapi-core` — the controller calls it before delegating to RipCurl's dispatcher.

### Refactor `McpStreamingController`

The controller becomes simpler:

1. Validate MCP headers (protocol version, origin, Accept) via `McpRequestValidator`
2. Parse the `JsonRpcRequest` from the POST body
3. Detect notifications/responses — handle MCP-specific routing (202, mailbox delivery)
4. For requests: call `dispatcher.dispatch(request)`
5. RipCurl returns a `JsonRpcResponse` (success or error) — no need for try/catch or
   error formatting
6. If the dispatched method had an `McpStreamContext` parameter, the response was
   delivered via SSE. Otherwise, return it as `application/json`.

The controller no longer needs `McpProtocol`, `JsonRpcMessages`, or `ObjectMapper` for
response formatting. RipCurl handles all of that.

### Refactor `MocapiAutoConfiguration`

- Remove `mcpProtocol` bean and all handler registration logic
- Remove `initializeServer` and `callTool` static helper methods
- Register `McpServerMethods` and `McpToolMethods` as beans (the latter conditionally
  on `McpToolsCapability`)
- RipCurl's `RipCurlAutoConfiguration` handles dispatcher, service scanning, and
  factory wiring automatically

### Update `mocapi-core/pom.xml`

Remove the `JsonMethodInvoker` that exists in `mocapi-core` — it's superseded by
RipCurl's invoker. Check if `mocapi-core` still has its own `JsonMethodInvoker` from
the tools module.

## Acceptance criteria

- [ ] `ripcurl-core` is a dependency of `mocapi-core`
- [ ] `ripcurl-autoconfigure` is a dependency of `mocapi-autoconfigure`
- [ ] MCP methods are registered via `@JsonRpcService` / `@JsonRpc` annotations
- [ ] RipCurl's `JsonRpcDispatcher` handles all JSON-RPC dispatch
- [ ] `McpStreamContext` is injected via a `JsonRpcParamResolver`
- [ ] `McpProtocol`, `McpMethodRegistry`, `McpMethodHandler`, `JsonRpcMessages` are
      deleted from `mocapi-core`
- [ ] `McpException` hierarchy is replaced by `JsonRpcException`
- [ ] `McpRequestValidator` remains for MCP-specific header validation
- [ ] Controller delegates to RipCurl for dispatch and uses `McpRequestValidator`
      for header checks
- [ ] Notification handling (202 responses) still works
- [ ] JSON-RPC response routing for elicitation (future) still has a hook
- [ ] SSE streaming for methods with `McpStreamContext` still works
- [ ] JSON responses for methods without `McpStreamContext` still work
- [ ] All existing MCP protocol behaviors are preserved
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- RipCurl 0.2.0 Maven coordinates: `com.callibrity.ripcurl:ripcurl-core:0.2.0` and
  `com.callibrity.ripcurl:ripcurl-autoconfigure:0.2.0`.
- RipCurl's `JsonRpcDispatcher.dispatch()` returns `null` for notifications (no `id`).
  The MCP spec requires 202 for notifications. The controller checks for null response
  and returns 202.
- RipCurl's `JsonRpcDispatcher` throws `JsonRpcException` for errors. The controller
  can catch this and return a JSON error response, or use `@ExceptionHandler` /
  `@ControllerAdvice`.
- The `McpStreamContext` resolver is the bridge between RipCurl (transport-agnostic
  JSON-RPC dispatch) and Odyssey (SSE transport). The controller sets the current
  context before dispatch and the resolver provides it to the method.
- The controller still needs to know whether the dispatched method uses SSE (to decide
  between JSON and SSE response). It can check if the resolver provided a context
  for the current request — if yes, the response is already being delivered via SSE.
- `@JsonRpcService` needs `@Component` meta-annotation (or explicit `@Bean` registration)
  for Spring to discover the service beans. RipCurl's `@JsonRpcService` is NOT a
  stereotype — register the service beans explicitly in `MocapiAutoConfiguration`.
- The existing `JsonMethodInvoker` in `mocapi-core` (used by tools) may need to be
  reconciled with RipCurl's invoker. Check whether `McpToolsCapability` uses it
  directly or if tools are now invoked through RipCurl's dispatch.
