# Mocapi Redis Example

A mocapi deployment with Redis-backed session store, mailbox, journal, and notifier. Sessions survive app restarts, enabling horizontal scaling and zero-downtime deploys.

## Prerequisites

- JDK 25
- Maven 3.6+
- Docker + Docker Compose

## Start infrastructure

```bash
cd examples/redis
docker compose up -d
```

Wait for Redis to be healthy:

```bash
docker compose ps
```

## Run the app

```bash
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

## Verify session persistence

1. Initialize a session and note the `Mcp-Session-Id` header value.
2. Stop the app (`Ctrl+C`).
3. Restart the app (`mvn spring-boot:run`).
4. Use the same session ID — the session is still valid because state is stored in Redis, not in-process memory.

## Tear down

```bash
docker compose down -v
```

The `-v` flag removes the Redis data volume for a clean slate.

## What's included

Three example tools from `mocapi-example-autoconfigure`:

- **HelloTool** (`hello`) — returns a greeting for the given name
- **Rot13Tool** (`rot13`) — applies ROT13 encoding to a string
- **CountdownTool** (`countdown`) — streams a countdown with progress notifications

## Cloud deployment

Point `REDIS_HOST` and `REDIS_PORT` environment variables at any Redis 6.0+ instance. For authentication, configure `spring.data.redis.username` and `spring.data.redis.password` via environment variables or a Spring profile.

Set `MOCAPI_MASTER_KEY` to a secure 32+ character value in any non-demo deployment.
