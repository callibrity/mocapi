# Streamline `StreamableHttpController` to a thin HTTP adapter

## What to build

Trim `mocapi-streamable-http-transport`'s `StreamableHttpController`
(currently ~262 lines) down to a thin HTTP-adapter shape. Split into
three focused specs so each can ship independently; "D" is the
headline change that subsumes the smaller tactical fixes.

### A — Fold `withContext` and `withContextAsync` into one method

**Fat:** two near-identical private methods (`withContext` lines
163–174, `withContextAsync` lines 176–189), both switching on the
same `McpContextResult` sealed type with identical error branches.
Only difference is whether error responses are returned as
`ResponseEntity<Object>` or `CompletableFuture.completedFuture(...)`.

**Fix:** add a fold method on `McpContextResult` (e.g.
`ifValid(Function<McpContext, ResponseEntity<Object>>)` or an
equivalent sealed-type fold) that collapses the 4-case switch to one
line. Controller holds no knowledge of the three error variants
(`SessionIdRequired`, `SessionNotFound`, `ProtocolVersionMismatch`).
For the POST path, wrap the result at the top level when Spring
needs a `CompletableFuture`.

**Impact:** ~30 lines gone. No behavior change. Completely
independent of B and D.

### B — Move Accept / Origin validation to a servlet filter

**Fat:** the same `acceptsJsonAndSse(accept)` / `acceptsSse(accept)`
+ `validator.isValidOrigin(origin)` checks are inlined in all three
mapping methods (POST lines 92–102, GET lines 131–139, DELETE lines
150–152), each followed by a JSON-RPC-shaped error response. Every
new endpoint would have to remember the same incantation.

**Fix:** extract into a servlet filter
(`McpRequestHeadersFilter` or similar) registered via autoconfig at
the MCP endpoint path. Short-circuits with the same JSON-RPC error
body on header violations. Controller no longer carries Accept /
Origin logic at all.

**Impact:** ~25 lines gone from controller; adds a small filter
class. Independent of A and D.

### D — Move `JsonRpcMessage` subtype dispatch into `McpServer` (subsumes C)

**Context (C, folded into D):** today the controller special-cases
`INITIALIZE` (lines 106–108) by passing `McpContext.empty()` instead
of resolving a session. The protocol-level knowledge that "initialize
has no session yet" lives in the transport, which is the wrong
side — stdio transport would need the same special-case.

**Fat:** the controller switches on `JsonRpcMessage` subtype (lines
104–116) and has three private dispatcher methods (`handleCall`,
`handleNotification`, `handleResponse`) that each just thread the
call through `server.handle*` and wrap the return. Plus the
initialize special-case.

**Fix:** server grows one entry point for the POST path:

```java
// mocapi-server (no Spring types)
sealed interface McpResponse {
    record Immediate(Object body) implements McpResponse {}
    record Deferred(CompletableFuture<Object> body) implements McpResponse {}
    record Accepted() implements McpResponse {}
    record Error(int code, String message, Severity severity) implements McpResponse {}
}

interface McpServer {
    McpResponse handle(JsonRpcMessage message, String sessionId,
                       String protocolVersion, Transport transport);
    // createContext + terminate stay for GET / DELETE
}
```

Server owns: context resolution (including `INITIALIZE` → empty
context), message-type dispatch, JSON-RPC error production. It does
**not** own: HTTP status mapping, VT spawning, `ResponseEntity`
shaping — those stay in the transport.

Controller POST body collapses to:

```java
return switch (server.handle(message, sessionId, protocolVersion, transport)) {
    case Immediate(var body)   -> CF.completedFuture(ResponseEntity.ok(body));
    case Deferred(var future)  -> future.thenApply(ResponseEntity::ok);
    case Accepted _            -> CF.completedFuture(ResponseEntity.accepted().build());
    case Error(var code, var msg, var severity)
                               -> CF.completedFuture(jsonRpcError(toStatus(severity), code, msg));
};
```

Three private handler methods gone. `INITIALIZE` special-case gone.
`withContext`/`withContextAsync` (if A hasn't landed yet) effectively
gone too — context resolution happens inside `server.handle`.

**VT spawn location:** stays in the transport. Server returns
`Deferred(CompletableFuture)` populated synchronously; the transport
spawns a VT around whatever fills that future and applies
`ContextSnapshot.wrap(...)`. Server never knows about virtual
threads.

**Impact:** ~40 lines gone from controller on top of A / B. Creates
`McpResponse` type in mocapi-server. Stdio transport can adopt the
same pattern later for consistency.

---

## Sequencing

Three independent specs when cleared to execute. Suggested order:

1. **A** — fold context-result fold. Pure refactor inside the
   transport module.
2. **B** — Accept / Origin filter. Pure refactor, new small filter
   class.
3. **D** — `McpServer.handle(...)` + `McpResponse`. Touches server
   API surface and therefore should land after the o11y / customizer
   work is out of the way (180 → 183) so there's no conflict
   between message dispatch changes and handler-wiring changes.

## Acceptance criteria (once promoted)

Per-spec criteria will be written when each gets promoted out of
backlog. Rough shape:

- A: `withContext` and `withContextAsync` no longer exist as
  separate methods; tests still pass with no behavior change.
- B: All three mapping methods lose their Accept / Origin
  preamble; a new filter runs ahead of the controller and returns
  the same JSON-RPC errors; tests still pass.
- D: `McpServer` gains a `handle(JsonRpcMessage, …)` method
  returning `McpResponse`; controller's POST body fits in the
  one-line-per-case switch above; `INITIALIZE` special-case is gone
  from the controller.

## Non-goals

- **Stdio transport rewrite.** If D lands and stdio looks like a
  fit, file a follow-up spec then.
- **Changing VT-per-call dispatch.** The spawn is intentional (SSE
  streaming); this work just cleans up where the spawn lives.
- **Replacing the `McpContextResult` sealed type.** It's a fine
  shape; A just adds a consumption helper on top.

## Background — why "inch of fat"

Current line count: 262. After A + B + D, target is ~140–150: three
`@*Mapping` methods, one utility filter-companion, `jsonRpcError` +
`rootCauseMessage` extracted to a `JsonRpcErrors` util class, no
duplicate context-resolution logic, no message-subtype switch, no
three-private-handler-methods. The controller becomes what its
Javadoc already claims: a "thin HTTP adapter for the MCP Streamable
HTTP transport."
