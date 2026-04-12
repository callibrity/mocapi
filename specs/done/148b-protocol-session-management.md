# Protocol session management

## What to build

Add session management to `mocapi-protocol`. This is a clean-room
implementation — write new code in the protocol module, do NOT
copy/move from mocapi-core.

### What to add

1. **`McpSession` record** — in `com.callibrity.mocapi.protocol.session`.
   Carries session state: protocol version, client capabilities,
   client info, log level. Same shape as the existing
   `com.callibrity.mocapi.session.McpSession` in mocapi-core, but
   a fresh implementation in the new package.

2. **`McpSessionStore` interface** — in the same package. The
   SPI for session persistence: `save`, `find`, `update`, `touch`,
   `delete`. Same contract as the existing one in mocapi-core.

3. **`McpSessionService`** — orchestrates session lifecycle:
   create (generate ID, save to store), find (lookup + touch TTL),
   delete, update log level. This replaces the session-related
   parts of `McpSessionService` in mocapi-core. Does NOT include
   stream management or encryption — those are transport concerns
   that stay out of the protocol module.

4. **`DefaultMcpProtocol` skeleton** — implements `McpProtocol`.
   For now, handles ONLY:
   - Session enforcement: reject non-initialize messages without
     a session ID.
   - Initialize: create session via `McpSessionService`, emit
     `SessionInitialized` event, send `InitializeResult` via
     transport.
   - Session lookup: validate session exists for all other
     messages. Send error if not found.
   - **All other messages are ignored for now** — tools, resources,
     prompts dispatch will be added in later specs.

5. **`CapturingTransport`** — test utility that captures sent
   messages and emitted events. Lives in `src/test/java`.

### Dependencies to add to mocapi-protocol pom

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-model</artifactId>
  <version>${project.version}</version>
</dependency>
```

The model module provides `InitializeResult`, `ClientCapabilities`,
`Implementation`, `ServerCapabilities`, etc. that the protocol
needs for the initialize response.

Also add test dependencies:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

### What NOT to do

- Do NOT add substrate/Atom session store dependency. The
  `McpSessionStore` is an interface — the Atom implementation
  stays in mocapi-core for now.
- Do NOT add encryption (Ciphers). That's a transport concern
  for encrypted event IDs.
- Do NOT add Odyssey. Stream management is a transport concern.
- Do NOT add `MailboxFactory`. Response correlation comes in a
  later spec.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `McpSession` record exists in
      `com.callibrity.mocapi.protocol.session`.
- [ ] `McpSessionStore` interface exists with save/find/update/
      touch/delete methods.
- [ ] `McpSessionService` exists and handles create/find/delete/
      setLogLevel.
- [ ] `DefaultMcpProtocol` implements `McpProtocol.handle()`.
- [ ] Session enforcement: no session + non-initialize → error
      via transport.
- [ ] Initialize: creates session, emits `SessionInitialized`,
      sends result.
- [ ] Unknown session → error via transport.
- [ ] `CapturingTransport` exists in test sources.
- [ ] All tests are pure unit tests — no HTTP, no Spring context.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
