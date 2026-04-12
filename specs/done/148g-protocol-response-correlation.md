# Protocol response correlation (elicitation + sampling)

## What to build

Add Mailbox-based response correlation to the protocol module so
interactive tools can elicit from and sample the client.

### What to add

1. **Add substrate dependency** to mocapi-protocol:
   ```xml
   <dependency>
     <groupId>org.jwcarman.substrate</groupId>
     <artifactId>substrate-core</artifactId>
   </dependency>
   ```

2. **Response correlation service** — internal to the protocol.
   When a tool calls `McpToolContext.elicit()`:
   - Creates a Mailbox with a UUID correlation ID
   - Sends a `JsonRpcCall` ("elicitation/create") via the
     transport with that ID
   - Blocks on the Mailbox subscription waiting for the client's
     response
   - Returns the parsed result

3. **Client response delivery** — when `DefaultMcpProtocol`
   receives a `JsonRpcResponse` via `handle()`, it delivers to
   the Mailbox by correlation ID. This replaces the "silently
   ignore" behavior from spec 148c.

4. **Implement `McpToolContext.elicit()`** — replace the
   `UnsupportedOperationException` placeholder with the real
   implementation using the correlation service.

5. **Implement `McpToolContext.sample()`** — same pattern as
   elicit, different method name and params.

### What NOT to do

- Do NOT add Odyssey dependency. The transport handles streaming.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `McpToolContext.elicit()` sends a request via transport
      and waits for a correlated response.
- [ ] `McpToolContext.sample()` sends a request via transport
      and waits for a correlated response.
- [ ] Client `JsonRpcResponse` messages are delivered to the
      correct Mailbox by correlation ID.
- [ ] Orphan responses (no waiting Mailbox) are silently dropped.
- [ ] Timeout produces the appropriate exception.
- [ ] Tests use `CapturingTransport` + real in-memory Mailbox.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
