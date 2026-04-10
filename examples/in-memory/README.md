# Mocapi In-Memory Example

A minimal mocapi deployment with in-memory backends for session store, mailbox, journal, and notifier. No external infrastructure required — just Java and Maven.

## Prerequisites

- JDK 25
- Maven 3.6+

## Run

```bash
cd examples/in-memory
mvn spring-boot:run
```

The server starts on `http://localhost:8080` with the MCP endpoint at `/mcp`.

## Test it

Initialize an MCP session:

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {},
      "clientInfo": { "name": "curl", "version": "1.0" }
    }
  }'
```

The `Mcp-Session-Id` response header contains the session ID for subsequent requests.

Call the `hello` tool:

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <session-id-from-initialize-response-header>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "hello",
      "arguments": { "name": "World" }
    }
  }'
```

## What's included

Three example tools from `mocapi-example-autoconfigure`:

- **HelloTool** (`hello`) — returns a greeting for the given name
- **Rot13Tool** (`rot13`) — applies ROT13 encoding to a string
- **CountdownTool** (`countdown`) — streams a countdown with progress notifications

## Limitations

This example uses **in-memory backends only**:

- All state is lost on restart
- Sessions do not survive server restarts
- No clustering or horizontal scaling

**Not for production use.** For durable backends, see the Redis, Hazelcast, or PostgreSQL examples.

Set `MOCAPI_MASTER_KEY` to a secure 32+ character value in any non-demo deployment.
