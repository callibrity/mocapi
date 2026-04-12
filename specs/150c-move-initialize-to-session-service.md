# Move initialize handler to McpSessionService

## What to build

Move the `initialize` handler out of `DefaultMcpServer` and into
`McpSessionService` as a regular `@JsonRpcMethod`. The server no
longer knows about initialize — it just dispatches. The session
service handles it because initialize creates sessions.

### McpSessionService changes

Add `@JsonRpcService` and the initialize method:

```java
@JsonRpcService
public class McpSessionService {

  private final McpSessionStore store;
  private final Duration ttl;
  private final Implementation serverInfo;
  private final String instructions;
  private final List<ServerCapabilitiesContributor> contributors;

  @JsonRpcMethod(McpMethods.INITIALIZE)
  public InitializeResult initialize(
      @JsonRpcParams InitializeRequestParams params,
      McpTransport transport) {
    McpSession session = new McpSession(
        params.protocolVersion(),
        params.capabilities(),
        params.clientInfo());
    String sessionId = create(session);
    transport.emit(new McpEvent.SessionInitialized(
        sessionId, params.protocolVersion()));
    return new InitializeResult(
        McpMethods.PROTOCOL_VERSION,
        buildCapabilities(),
        serverInfo,
        instructions);
  }

  private ServerCapabilities buildCapabilities() {
    var builder = new ServerCapabilitiesBuilder();
    contributors.forEach(c -> c.contribute(builder));
    return builder.build();
  }

  // existing methods: create, find, delete, setLogLevel...
}
```

The `McpTransport` parameter is injected by the transport
resolver from `ScopedValue` — the server already binds it before
dispatch. The initialize handler uses it to emit the
`SessionInitialized` event.

### DefaultMcpServer changes

Remove ALL initialize-specific code:
- Delete the `handleInitialize` private method.
- Delete the `INITIALIZE` constant (or the check against it).
- Delete the `InitializeResult` constructor parameter.
- The `handleCall` method becomes simpler — no special-casing
  for initialize. Session enforcement still applies: if
  `sessionId` is null and the method is NOT initialize, the
  dispatcher will still fail because the session ScopedValue
  won't be bound.

**Wait** — there's a subtlety. The server currently skips session
validation for initialize. If we remove the special-case, the
server will try to validate the session for initialize (which
has no session ID) and fail.

**Fix**: the server should only validate/bind the session if a
session ID is present. If no session ID, dispatch anyway — let
the method itself decide whether it needs a session. Initialize
doesn't. Every other method does (because they declare
`McpSession` as a parameter or access it via `ScopedValue`).

```java
@Override
public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
  McpSession session = null;
  if (context.sessionId() != null && !context.sessionId().isBlank()) {
    var sessionOpt = sessionService.find(context.sessionId());
    if (sessionOpt.isEmpty()) {
      transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Unknown session"));
      return;
    }
    session = sessionOpt.get();
  }

  JsonRpcResponse response =
      ScopedValue.where(McpSession.CURRENT, session)
          .where(McpTransport.CURRENT, transport)
          .call(() -> dispatcher.dispatch(call));
  if (response != null) {
    transport.send(response);
  }
}
```

If `session` is null (no session ID provided) and the method
being called is NOT initialize, the method will fail when it
tries to access `McpSession.CURRENT` — which is the correct
behavior. The session enforcement is implicit via the ScopedValue
binding.

**But** — `ScopedValue.where(McpSession.CURRENT, null)` throws
because ScopedValue doesn't accept null. So we need to
conditionally bind:

```java
JsonRpcResponse response;
if (session != null) {
  response = ScopedValue.where(McpSession.CURRENT, session)
      .where(McpTransport.CURRENT, transport)
      .call(() -> dispatcher.dispatch(call));
} else {
  response = ScopedValue.where(McpTransport.CURRENT, transport)
      .call(() -> dispatcher.dispatch(call));
}
if (response != null) {
  transport.send(response);
}
```

### Auto-config changes

- `McpSessionService` bean creation needs `Implementation`,
  `instructions`, and `List<ServerCapabilitiesContributor>`
  injected.
- Remove `InitializeResult` bean entirely — it's no longer
  needed.
- Remove `InitializeResult` from `DefaultMcpServer` constructor.

### What about session enforcement for non-initialize calls?

Methods that need a session should declare `McpSession` as a
parameter. If the ScopedValue isn't bound (no session ID was
provided), the parameter resolver throws a `JsonRpcException`
with an appropriate error code. This moves session enforcement
to the method signature — if you need a session, declare it.

Alternatively, keep the explicit check in `handleCall`: if no
session ID and the method is not initialize, send an error.
**Decision: use the explicit check for now** — it produces a
cleaner error message and is auditable. The ScopedValue approach
is a future optimization.

Actually — the simplest approach: keep the `INITIALIZE` check
in the server. The user acknowledged this is the right place
for protocol-level enforcement. Just remove the handler logic
(session creation, event emission, result building) which moves
to `McpSessionService`.

```java
@Override
public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
  if (!INITIALIZE.equals(call.method())) {
    if (context.sessionId() == null || context.sessionId().isBlank()) {
      transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Missing session ID"));
      return;
    }
    var sessionOpt = sessionService.find(context.sessionId());
    if (sessionOpt.isEmpty()) {
      transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Unknown session"));
      return;
    }
    session = sessionOpt.get();
  }
  // dispatch as before...
}
```

The INITIALIZE check stays (protocol enforcement). The handler
logic moves to McpSessionService (method implementation).

## Acceptance criteria

- [ ] `McpSessionService` has `@JsonRpcMethod(McpMethods.INITIALIZE)`
      that creates session, emits event, builds capabilities,
      returns `InitializeResult`.
- [ ] `McpSessionService` receives `Implementation`, `String
      instructions`, and `List<ServerCapabilitiesContributor>`
      in its constructor.
- [ ] `DefaultMcpServer` no longer has `InitializeResult` as a
      constructor parameter.
- [ ] `DefaultMcpServer` no longer has a `handleInitialize`
      private method.
- [ ] `DefaultMcpServer` still checks for `INITIALIZE` to skip
      session enforcement — that stays.
- [ ] Auto-config updated: no `InitializeResult` bean,
      `McpSessionService` wired with server info + contributors.
- [ ] Compliance tests updated and passing.
- [ ] `mvn verify` passes.
