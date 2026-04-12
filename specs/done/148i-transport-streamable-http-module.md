# Create mocapi-transport-streamable-http module

## What to build

Create the Streamable HTTP transport module with the two transport
implementations and the Spring MVC controller.

### Module

```
mocapi-transport-streamable-http/
├── pom.xml
└── src/main/java/com/callibrity/mocapi/transport/http/
    ├── StreamableHttpController.java
    ├── SynchronousTransport.java
    ├── OdysseyTransport.java
    └── McpRequestValidator.java
```

### pom.xml dependencies

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-protocol</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>org.jwcarman.odyssey</groupId>
  <artifactId>odyssey</artifactId>
  <version>${odyssey.version}</version>
</dependency>
```

### `SynchronousTransport`

For initialize (no session ID). Buffers a single
`JsonRpcResponse`. Captures `SessionInitialized` event to set
the `MCP-Session-Id` header. Throws on non-response messages.
Exposes `toResponseEntity()`.

### `OdysseyTransport`

For everything else. Wraps
`OdysseyPublisher<JsonRpcMessage>`. Publishes every message.
Auto-completes on `JsonRpcResponse`. Exposes `streamName()`.

### `StreamableHttpController`

Thin HTTP adapter:

- **POST without session ID**: `SynchronousTransport` →
  `mcpProtocol.handle()` → `transport.toResponseEntity()`.
- **POST with session ID + `JsonRpcResponse`**: deliver via
  `mcpProtocol.handle()` with no-op transport → return 202.
- **POST with session ID + anything else**:
  `OdysseyTransport` → kick off `mcpProtocol.handle()` on
  virtual thread → subscribe to stream → return `SseEmitter`.
- **GET**: subscribe to notification channel → return
  `SseEmitter`.
- **DELETE**: `mcpProtocol.terminate()` → 204.

Always-SSE for post-initialize POST responses. Session validation
is NOT in the controller — the protocol does it.

### Encrypted event IDs

The encrypting `SseEventMapper` lives in this module. It encrypts
Odyssey event IDs with session-bound AES-GCM keys for the SSE
wire format. This is a transport concern — the protocol doesn't
know about encrypted event IDs.

The encryption utility (`Ciphers`) will need to be accessible
from this module — either moved to a shared utility module or
duplicated (TBD based on what's cleanest).

### What NOT to do

- Do NOT delete mocapi-core yet. That's a separate spec.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `mocapi-transport-streamable-http` module exists.
- [ ] `SynchronousTransport` + unit tests.
- [ ] `OdysseyTransport` + unit tests.
- [ ] `StreamableHttpController` delegates to `McpProtocol`.
- [ ] Always-SSE for POST with session ID.
- [ ] JSON for initialize (no session ID).
- [ ] 202 for client responses.
- [ ] GET endpoint subscribes to notification channel.
- [ ] DELETE delegates to protocol termination.
- [ ] Virtual thread for POST with session ID.
- [ ] Encrypted event IDs via `SseEventMapper`.
- [ ] Controller tests verify thin-adapter behavior.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
