# ADR-0002 — `McpServer` / `McpTransport` is the only coupling between protocol and transport

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

Before the protocol/transport split, a single `StreamableHttpController`
class mixed HTTP concerns (Accept headers, status codes, SSE emitter
management) with MCP protocol logic (session lifecycle, JSON-RPC dispatch,
elicitation/sampling correlation, tool dispatch). Every protocol bug had
to be reproduced through MockMvc; every transport bug looked like a
protocol bug; and adding a stdio transport required either copy-pasting
half the controller or breaking the controller open without a contract.

The MCP specification defines messages, methods, and capabilities — not a
wire protocol. Streamable HTTP, stdio, and any future transport (WebSocket,
Unix socket) all carry the same JSON-RPC payloads. Mocapi needs to
implement the protocol once and let transports plug in.

## Decision

`mocapi-server` exposes a two-interface contract that is the **only**
coupling between the protocol layer and any transport:

```java
public interface McpServer {
    McpContextResult createContext(String sessionId, String protocolVersion);
    void handleCall(McpContext context, JsonRpcCall call, McpTransport transport);
    void handleNotification(McpContext context, JsonRpcNotification notification);
    void handleResponse(McpContext context, JsonRpcResponse response);
    void terminate(String sessionId);
}

public interface McpTransport {
    void send(JsonRpcMessage message);
    void emit(McpEvent event);
}

public sealed interface McpEvent {
    record SessionInitialized(String sessionId, String protocolVersion) implements McpEvent {}
}
```

**Rules:**

1. The server never returns protocol output as a value. All outbound
   messages flow through `transport.send(...)`. Lifecycle signals (currently
   only `SessionInitialized`) flow through `transport.emit(...)`.
2. The server validates sessions and protocol versions via `createContext`
   ([ADR-0009](0009-mcpcontextresult-sealed-validation.md)). Transports map
   the resulting `McpContextResult` variants to their native error format.
3. The server is transport-agnostic. It depends on ripcurl (JSON-RPC),
   Substrate (storage SPIs — see [ADR-0007](0007-substrate-storage-spi.md)),
   and `mocapi-model`. It does **not** depend on Spring MVC, Servlet API,
   Odyssey, or any I/O framework.
4. Transports are server-agnostic in the other direction: they know
   nothing about sessions, registries, or tool dispatch. A transport
   constructs `McpContext` from its wire format, calls `createContext` to
   resolve/validate it, then delegates to one of the four `handle*`
   methods.
5. `JsonRpcResponse` from the client (responding to a server-initiated
   elicitation or sampling request) goes to `handleResponse`. The server
   delivers it to the awaiting Mailbox internally and does **not** call
   `transport.send` — there is no outgoing message. See
   [ADR-0008](0008-mailbox-elicitation-sampling.md).

## Consequences

**Wins:**

- The server is unit-testable in complete isolation. Tests build a
  capturing transport (a `List<JsonRpcMessage>` + `List<McpEvent>`),
  invoke `handleCall`, and assert on what was sent — no MockMvc, no
  Tomcat, no SSE plumbing.
- Adding a transport is a self-contained job. `mocapi-stdio-transport`
  was implemented against this contract with zero changes to
  `mocapi-server`. See [ADR-0003](0003-streamable-http-and-stdio.md).
- Session enforcement, protocol-version negotiation, capability
  declaration, and error formatting all live in one place. A bug fix in
  the server fixes every transport.

**Costs:**

- Transports must accept asynchrony. `handleCall` may run synchronously
  (returning before the response is sent) for stdio's loop thread or
  asynchronously (when the server spawns a virtual thread; see
  [ADR-0006](0006-virtual-thread-per-call.md)). Transports buffer, queue,
  or stream as appropriate.
- The contract is small but non-negotiable. Adding a per-transport hook
  (e.g., "give me the HTTP request headers") is not allowed at this layer
  — that data is captured by the transport before `handleCall` is invoked
  or piped through a `ScopedValue`.

**Non-goals:** the contract does not expose tool, prompt, or resource
APIs. Tool authors depend on `mocapi-api` (see
[ADR-0001](0001-module-structure-and-packaging.md)); the server resolves
those through registries built at startup.

**Code anchors:** `mocapi-server/.../McpServer.java`, `McpTransport.java`, `McpEvent.java`.
