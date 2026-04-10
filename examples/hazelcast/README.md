# Mocapi Hazelcast Example

A mocapi deployment with Hazelcast-backed session store, mailbox, journal, and notifier. Hazelcast runs **embedded in the JVM** by default — no Docker, no external services, no infrastructure to manage. Just start the app and go.

## Prerequisites

- JDK 25
- Maven 3.6+

No Docker needed — Hazelcast runs embedded in the application process.

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

## Verify Hazelcast-managed state

Unlike the in-memory example, state lives in Hazelcast `IMap` instances. You can verify this by connecting Hazelcast Management Center to the embedded instance for real-time introspection of sessions, mailbox entries, and journal data.

## What's included

Three example tools from `examples-lib`:

- **HelloTool** (`hello`) — returns a greeting for the given name
- **Rot13Tool** (`rot13`) — applies ROT13 encoding to a string
- **CountdownTool** (`countdown`) — streams a countdown with progress notifications

## Cluster mode

By default, the embedded Hazelcast instance runs as a standalone single-member cluster with multicast and TCP-IP discovery disabled (to prevent accidental clustering with other machines on the same network).

To form a multi-node cluster:

1. Edit `src/main/resources/hazelcast.yaml` to enable discovery:

   ```yaml
   hazelcast:
     cluster-name: mocapi-example
     network:
       join:
         tcp-ip:
           enabled: true
           member-list:
             - 192.168.1.10
             - 192.168.1.11
   ```

2. Start two instances on different ports:

   ```bash
   # Terminal 1
   SERVER_PORT=8080 mvn spring-boot:run

   # Terminal 2
   SERVER_PORT=8081 mvn spring-boot:run
   ```

3. Sessions created on one instance are visible from the other — Hazelcast replicates data across all cluster members automatically.

## Client/server mode

Instead of embedding Hazelcast, you can run a standalone Hazelcast server and have the app connect as a client:

1. Start a Hazelcast server (Docker or standalone):

   ```bash
   docker run -d --name hazelcast -p 5701:5701 hazelcast/hazelcast:5.4
   ```

2. Replace `hazelcast.yaml` with a `hazelcast-client.yaml`:

   ```yaml
   hazelcast-client:
     cluster-name: dev
     network:
       cluster-members:
         - 127.0.0.1:5701
   ```

3. Update `application.yml` to point at the client config:

   ```yaml
   spring:
     hazelcast:
       config: classpath:hazelcast-client.yaml
   ```

4. Add the Hazelcast client dependency to `pom.xml` if not already present, and remove the embedded server dependency.

See the [Spring Boot Hazelcast auto-configuration documentation](https://docs.spring.io/spring-boot/reference/io/hazelcast.html) for full details on client vs. embedded configuration.

## Production notes

Set `MOCAPI_MASTER_KEY` to a secure 32+ character value in any non-demo deployment.

Override `hazelcast.cluster-name` via environment variable or config for production to avoid mixing with dev clusters.
