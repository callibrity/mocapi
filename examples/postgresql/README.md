# Mocapi PostgreSQL Example

A mocapi deployment with PostgreSQL-backed session store. Sessions survive app restarts, enabling persistence without adding a Redis dependency.

## Prerequisites

- JDK 25
- Maven 3.6+
- Docker + Docker Compose

## Limitation

**Session store only.** The JDBC module (`mocapi-session-store-jdbc`) only implements `McpSessionStore`. The substrate Mailbox, Journal, and Notifier fall back to their **in-memory implementations**, which means:

- **`McpSessionStore`** — PostgreSQL (survives restarts, supports clustering)
- **`Mailbox`, `Journal`, `Notifier`** — in-memory (single-node only)

For a fully distributed deployment (all substrate SPIs backed by a shared store), use the [Redis](../redis/) or [Hazelcast](../hazelcast/) example instead. The PostgreSQL example is appropriate for deployments that already have a PostgreSQL database and want session persistence without adding a Redis or Hazelcast dependency.

## Start infrastructure

```bash
cd examples/postgresql
docker compose up -d
```

Wait for PostgreSQL to be healthy:

```bash
docker compose ps
```

## Run the app

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080` with the MCP endpoint at `/mcp`. The `mocapi_sessions` table is auto-initialized on first startup via Spring Boot's SQL init.

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

## Verify session persistence

1. Initialize a session and note the `Mcp-Session-Id` header value.
2. Stop the app (`Ctrl+C`).
3. Restart the app (`mvn spring-boot:run`).
4. Use the same session ID — the session is still valid because state is stored in PostgreSQL, not in-process memory.

Query the sessions table directly:

```bash
docker exec -it mocapi-example-postgres \
  psql -U mocapi mocapi -c 'SELECT session_id, expires_at FROM mocapi_sessions;'
```

## Tear down

```bash
docker compose down -v
```

The `-v` flag removes the PostgreSQL data volume for a clean slate.

## What's included

Three example tools from `examples-lib`:

- **HelloTool** (`hello`) — returns a greeting for the given name
- **Rot13Tool** (`rot13`) — applies ROT13 encoding to a string
- **CountdownTool** (`countdown`) — streams a countdown with progress notifications

## Cloud deployment

Point `DB_URL`, `DB_USER`, and `DB_PASSWORD` environment variables at any PostgreSQL instance. For example:

```bash
DB_URL=jdbc:postgresql://mydb.example.com:5432/mocapi \
DB_USER=myuser \
DB_PASSWORD=secret \
MOCAPI_MASTER_KEY=YourSecure32CharKeyHere1234567890 \
mvn spring-boot:run
```

Set `MOCAPI_MASTER_KEY` to a secure 32+ character value in any non-demo deployment. The default credentials (`mocapi/mocapi/mocapi`) in `docker-compose.yml` are intentionally trivial for local development — always change them for real deployments.
