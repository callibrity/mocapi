# Protocol JSON-RPC dispatch

## What to build

Add JSON-RPC method dispatch to `DefaultMcpProtocol`. When the
protocol receives a `JsonRpcCall`, it dispatches to the ripcurl
`JsonRpcDispatcher` and sends the result via the transport.

### What to add

1. **Add `ripcurl-autoconfigure` dependency** to mocapi-protocol
   so the `JsonRpcDispatcher` and annotation-based method
   registration are available.

2. **Wire `JsonRpcDispatcher` into `DefaultMcpProtocol`** as a
   constructor dependency.

3. **Handle `JsonRpcCall` messages**: dispatch via the dispatcher,
   send the response (result or error) via `transport.send()`.

4. **Handle `JsonRpcNotification` messages**: dispatch via the
   dispatcher. No response needed.

5. **Handle `JsonRpcResponse` messages**: these are client
   responses to server-initiated requests (elicitation, sampling).
   For now, **silently ignore them** — response correlation comes
   in a later spec. Just don't crash.

### Session scoping

Before dispatching, bind the session to a `ScopedValue` so
`@JsonRpcMethod` handlers can access it. Same pattern as the
current controller uses with `McpSession.CURRENT`.

### What NOT to do

- Do NOT implement specific method handlers (tools/call,
  resources/read, etc.) in this spec. Just wire the dispatcher.
  Method handlers will be registered in later specs.
- Do NOT implement streaming tool support. That comes later.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `DefaultMcpProtocol` dispatches `JsonRpcCall` via
      `JsonRpcDispatcher`.
- [ ] Dispatch result sent via `transport.send()`.
- [ ] `JsonRpcNotification` dispatched (no response).
- [ ] `JsonRpcResponse` handled without crashing (ignored for
      now).
- [ ] Session bound to `ScopedValue` before dispatch.
- [ ] Tests verify dispatch + transport output using
      `CapturingTransport`.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
