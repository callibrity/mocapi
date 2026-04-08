# Update initialize types and introduce McpSessionStore

## What to build

Update the client and server type models for the `initialize` handshake to match the
MCP 2025-11-25 spec. Introduce an `McpSessionStore` SPI to persist session data
(client capabilities, client info, negotiated protocol version) and replace the
in-memory `McpSessionManager` and `McpSession` class.

### Update `ClientInfo`

Current: `record ClientInfo(String name, String title, String version)`

Add missing fields per spec:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientInfo(
    String name,
    String title,
    String version,
    String description,
    List<Icon> icons,
    String websiteUrl
) {}
```

### Update `ServerInfo`

Current: `record ServerInfo(String name, String title, String version)`

Add the same missing fields:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerInfo(
    String name,
    String title,
    String version,
    String description,
    List<Icon> icons,
    String websiteUrl
) {}
```

### Create shared `Icon` record

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Icon(String src, String mimeType, List<String> sizes) {}
```

### Update `ElicitationCapability`

Current: empty record. Add form and URL sub-modes:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitationCapability(
    FormCapability form,
    UrlCapability url
) {
    public record FormCapability() {}
    public record UrlCapability() {}
}
```

### Add `TasksCapability`

New in the spec:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TasksCapability(TaskRequests requests) {
    public record TaskRequests(
        ElicitationTaskRequest elicitation,
        SamplingTaskRequest sampling
    ) {
        public record ElicitationTaskRequest(Object create) {}
        public record SamplingTaskRequest(Object createMessage) {}
    }
}
```

### Update `ClientCapabilities`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCapabilities(
    RootsCapability roots,
    SamplingCapability sampling,
    ElicitationCapability elicitation,
    TasksCapability tasks,
    JsonNode experimental
) {}
```

### Redefine `McpSession` as a simple record

Replace the current `McpSession` class in `mocapi-autoconfigure` (which holds Odyssey
stream references and lifecycle state) with a simple data record in `mocapi-core`:

```java
public record McpSession(
    String protocolVersion,
    ClientCapabilities capabilities,
    ClientInfo clientInfo
) {
    public boolean supportsElicitationForm() { ... }
    public boolean supportsElicitationUrl() { ... }
    public boolean supportsSampling() { ... }
    public boolean supportsRoots() { ... }
}
```

### Introduce `McpSessionStore` SPI

Define in `mocapi-core`:

```java
public interface McpSessionStore {
    String save(McpSession session, Duration ttl);
    Optional<McpSession> find(String sessionId);
    void touch(String sessionId, Duration ttl);
    void delete(String sessionId);
}
```

### Implement `InMemoryMcpSessionStore`

Default in-memory implementation in `mocapi-autoconfigure`:

Uses `ConcurrentHashMap` with timestamps and a `ScheduledExecutorService` for periodic
cleanup — same approach as the current `McpSessionManager`, but storing `McpSession`
records instead of the old `McpSession` class with Odyssey references.

Auto-configured via `@ConditionalOnMissingBean`.

### Remove `McpSessionManager` and old `McpSession` class

Delete the current `McpSession` class from `mocapi-autoconfigure` (the one with
`OdysseyStream notificationStream`, `Instant createdAt`, `lastActivity`, etc.).

Delete `McpSessionManager` from `mocapi-autoconfigure`.

### Update `McpStreamingController`

Replace all `McpSessionManager` usage with `McpSessionStore`:

- **Initialize**: after the handler returns, create an `McpSession` record from the
  initialize params, call `store.save(session, ttl)`, return session ID in
  `MCP-Session-Id` header.
- **Non-initialize POST**: `store.find(sessionId)` → 404 if empty, else
  `store.touch(sessionId, ttl)` and proceed.
- **GET**: `store.find(sessionId)` → 404 if empty, else proceed. Odyssey notification
  stream is managed separately via `registry.channel(sessionId)` — the stream is NOT
  part of the session store.
- **DELETE**: `store.delete(sessionId)`. Also delete the Odyssey notification stream
  via `registry.stream(sessionId).delete()` or similar.

The session TTL should be configurable via `mocapi.session.timeout` property (default
1 hour).

### Update `MocapiAutoConfiguration`

- Remove `McpSessionManager` bean
- Add `InMemoryMcpSessionStore` bean with `@ConditionalOnMissingBean`
- Add session timeout property to `MocapiProperties`
- Update `McpStreamingController` bean to inject `McpSessionStore` instead of
  `McpSessionManager`

### Update `MocapiProperties`

Add session timeout:

```java
private Duration sessionTimeout = Duration.ofHours(1);
```

### Update initialize handler

The current `initializeServer` method in `MocapiAutoConfiguration` calls
`mcpServer.initialize()` and returns the response. After the RPC handler returns, the
controller needs to capture the initialize params (`protocolVersion`, `capabilities`,
`clientInfo`) and store them. The handler itself should return the response, and the
controller creates the session from the *request* params (not the response).

The controller's POST handler for `initialize`:

1. Dispatch via `McpProtocol` (returns the `InitializeResponse`)
2. Extract `protocolVersion`, `capabilities`, `clientInfo` from the request `params`
3. Create `McpSession` record
4. `store.save(session, timeout)` → session ID
5. Return response with `MCP-Session-Id` header

## Acceptance criteria

- [ ] `ClientInfo` has all spec fields
- [ ] `ServerInfo` has all spec fields
- [ ] `Icon` record exists
- [ ] `ElicitationCapability` has `form` and `url` sub-fields
- [ ] `TasksCapability` exists
- [ ] `ClientCapabilities` includes `tasks` and `experimental`
- [ ] `McpSession` is a record in `mocapi-core` with capability check methods
- [ ] `McpSessionStore` SPI exists in `mocapi-core`
- [ ] `InMemoryMcpSessionStore` exists in `mocapi-autoconfigure`
- [ ] `InMemoryMcpSessionStore` is auto-configured via `@ConditionalOnMissingBean`
- [ ] Old `McpSession` class is deleted
- [ ] `McpSessionManager` is deleted
- [ ] Controller uses `McpSessionStore` for all session operations
- [ ] Initialize flow stores client data and returns session ID
- [ ] Every non-initialize request calls `find()` then `touch()`
- [ ] DELETE calls `store.delete()` and cleans up Odyssey stream
- [ ] Expired/missing sessions return 404
- [ ] Session timeout is configurable via `mocapi.session.timeout`
- [ ] All nullable fields use `@JsonInclude(NON_NULL)`
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- All type records live in `mocapi-core` under `com.callibrity.mocapi.client` and
  `com.callibrity.mocapi.server`.
- `McpSessionStore` lives in `mocapi-core` (it's an SPI).
- `InMemoryMcpSessionStore` lives in `mocapi-autoconfigure`.
- The Odyssey notification stream is NOT stored in `McpSessionStore`. The controller
  uses `registry.channel(sessionId)` to get/create the stream on demand. This keeps
  the session store simple (just client data) and avoids serializing Odyssey objects.
- Jackson ignores unknown JSON fields by default, so adding new optional fields is
  backwards-compatible.
- The spec says empty `{"elicitation": {}}` defaults to form only. Handle this in
  the capability check method.
- Existing tests for `McpSession` and `McpSessionManager` need to be rewritten for
  the new `McpSessionStore`.
