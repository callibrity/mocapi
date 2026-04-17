# Lazy HTTP Transport Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the hard-coded JSON-vs-SSE branch in `StreamableHttpController.handleCall` with a single `LazyHttpTransport` that chooses its response shape based on the first outbound message. Unify the tools and non-tools paths so every JSON-RPC call goes through the same transport on a virtual thread.

**Architecture:**
- `handlePost` returns `CompletableFuture<ResponseEntity<Object>>` for all `JsonRpcCall` inputs (Spring MVC async).
- A new `LazyHttpTransport` holds a sealed `Writer` hierarchy with three states: `Pending` (initial), `JsonWriter` (single-response committed), `SseWriter` (stream committed). `send(msg)` returns the next writer.
- `Pending.send(JsonRpcResponse)` completes the future with a JSON body and transitions to `JsonWriter`. `Pending.send(JsonRpcRequest)` (Call or Notification) creates the Odyssey stream, subscribes with the encrypting mapper + priming onSubscribe, completes the future with an SSE body, publishes the first message, and transitions to `SseWriter`.
- `JsonWriter.send` always throws — the JSON response is committed, no further messages possible.
- `SseWriter.send` publishes to the stream; on `JsonRpcResponse` it also completes the stream. Same behavior as today's `OdysseyTransport`.
- Handler runs on a virtual thread for all calls (including initialize and `tools/list`). Virtual thread is cheap; uniformity simplifies the controller and test pattern.
- `SynchronousTransport` and `OdysseyTransport` are deleted — `LazyHttpTransport` replaces both.

**Tech Stack:** Java 21 sealed types + pattern switch, Spring MVC async (`CompletableFuture<ResponseEntity>`), Odyssey SSE, JUnit 5, Mockito, AssertJ.

---

## Key reference points (read before each task)

- `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java` — controller, specifically `handlePost` (line 89) and `handleCall` (line 192). Session header is set via `McpEvent.SessionInitialized` in the initialize path today (`SynchronousTransport.emit`).
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/McpTransport.java` — SPI (`send`, `emit`). Unchanged.
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/McpEvent.java` — only variant is `SessionInitialized(sessionId, protocolVersion)`.
- Ripcurl `JsonRpcMessage` is sealed: permits `JsonRpcRequest` (sealed: `JsonRpcCall`, `JsonRpcNotification`) and `JsonRpcResponse` (sealed: `JsonRpcResult`, `JsonRpcError`).

## Invariants to preserve

1. `MCP-Session-Id` header must land on the HTTP response for the `initialize` call.
2. Tool calls that emit notifications/requests before responding must still flow over SSE with the priming event.
3. Encryption of event IDs via `Ciphers.encryptAesGcm` must continue to use the session ID as associated data.
4. `GET /mcp` (notification channel) and `DELETE /mcp` (terminate) are untouched.
5. `handleNotification` and `handleResponse` (202 ACCEPTED) are untouched — they don't use a transport.

## Out of scope

- Async request timeout tuning. If a handler never sends anything we just rely on Spring's default async timeout. We *do* route exceptions through `future.completeExceptionally` inside the virtual thread body so a thrown handler surfaces properly.
- Changing the `McpTransport` SPI. Signatures stay the same.

---

### Task 1: Introduce `LazyHttpTransport` skeleton + `Pending.send(JsonRpcResponse)` path

**Files:**
- Create: `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/LazyHttpTransport.java`
- Create: `mocapi-transport-streamable-http/src/test/java/com/callibrity/mocapi/transport/http/LazyHttpTransportTest.java`

**Step 1: Write failing test — JSON-response path**

```java
@Test
void sendingResponseFromPendingCompletesFutureAsJson() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  var transport = new LazyHttpTransport(future, unusedStreamFactory(), unusedEmitterFactory());

  var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode().put("k", "v"), intNode(1));
  transport.send(result);

  ResponseEntity<Object> entity = future.getNow(null);
  assertThat(entity).isNotNull();
  assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  assertThat(entity.getBody()).isSameAs(result);
}
```

(Plus helper stubs that `throw new AssertionError("should not be called")` if invoked — these confirm the JSON path never touches Odyssey.)

**Step 2: Run — expect compile failure (class doesn't exist).**

`mvn -pl mocapi-transport-streamable-http test -Dtest=LazyHttpTransportTest` → compile error.

**Step 3: Implement `LazyHttpTransport` + `Pending` + `JsonWriter` enough to pass**

```java
final class LazyHttpTransport implements McpTransport {

  private final Supplier<OdysseyStream<JsonRpcMessage>> streams;
  private final Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitters;
  private Writer writer;
  private McpEvent.SessionInitialized sessionInitialized;

  LazyHttpTransport(
      CompletableFuture<ResponseEntity<Object>> future,
      Supplier<OdysseyStream<JsonRpcMessage>> streams,
      Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitters) {
    this.streams = streams;
    this.emitters = emitters;
    this.writer = new Pending(future);
  }

  @Override
  public void send(JsonRpcMessage message) {
    writer = writer.send(message);
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      sessionInitialized = si;
    }
  }

  sealed interface Writer permits Pending, JsonWriter, SseWriter {
    Writer send(JsonRpcMessage message);
  }

  final class Pending implements Writer {
    private final CompletableFuture<ResponseEntity<Object>> future;
    Pending(CompletableFuture<ResponseEntity<Object>> future) { this.future = future; }

    @Override
    public Writer send(JsonRpcMessage message) {
      return switch (message) {
        case JsonRpcResponse resp -> {
          var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
          if (sessionInitialized != null) {
            builder.header("MCP-Session-Id", sessionInitialized.sessionId());
          }
          future.complete(builder.body(resp));
          yield new JsonWriter();
        }
        case JsonRpcRequest req -> upgradeToSse(req, future);
      };
    }

    private Writer upgradeToSse(JsonRpcRequest first, CompletableFuture<ResponseEntity<Object>> future) {
      var stream = streams.get();
      var emitter = emitters.apply(stream);
      var builder = ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM);
      if (sessionInitialized != null) {
        builder.header("MCP-Session-Id", sessionInitialized.sessionId());
      }
      future.complete(builder.body(emitter));
      stream.publish(first);
      return new SseWriter(stream);
    }
  }

  static final class JsonWriter implements Writer {
    @Override
    public Writer send(JsonRpcMessage message) {
      throw new IllegalStateException(
          "JSON response already committed; cannot send " + message.getClass().getSimpleName());
    }
  }

  static final class SseWriter implements Writer {
    private final OdysseyStream<JsonRpcMessage> stream;
    private boolean completed;
    SseWriter(OdysseyStream<JsonRpcMessage> stream) { this.stream = stream; }

    @Override
    public Writer send(JsonRpcMessage message) {
      if (completed) {
        throw new IllegalStateException("Stream completed");
      }
      stream.publish(message);
      if (message instanceof JsonRpcResponse) {
        stream.complete();
        completed = true;
      }
      return this;
    }
  }
}
```

Note: `Pending` is an inner class so it can access `sessionInitialized` and the factories. `JsonWriter` and `SseWriter` are static — they don't need outer state.

**Step 4: Run — expect PASS.**

**Step 5: Commit.**

```
git add .../LazyHttpTransport.java .../LazyHttpTransportTest.java
git commit -m "Add LazyHttpTransport with JSON-response path"
```

---

### Task 2: Add JSON path session-header behavior + post-commit guard

**Files:**
- Modify: `.../LazyHttpTransportTest.java`
- Modify (if needed): `.../LazyHttpTransport.java`

**Step 1: Write failing tests**

```java
@Test
void jsonResponseIncludesSessionInitializedHeader() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  var transport = new LazyHttpTransport(future, unusedStreamFactory(), unusedEmitterFactory());
  transport.emit(new McpEvent.SessionInitialized("session-42", "2025-11-25"));
  transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

  var entity = future.getNow(null);
  assertThat(entity.getHeaders().getFirst("MCP-Session-Id")).isEqualTo("session-42");
}

@Test
void sendAfterJsonCommitThrows() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  var transport = new LazyHttpTransport(future, unusedStreamFactory(), unusedEmitterFactory());
  transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

  assertThatThrownBy(() ->
          transport.send(new JsonRpcNotification("2.0", "notifications/progress", null)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("JSON response already committed");
}
```

**Step 2: Run — expect the header test to pass (already implemented), post-commit guard to pass (JsonWriter.send throws).**

If both pass, skip to commit. If either fails, fix.

**Step 3: Commit.**

```
git commit -m "Cover JSON-path session header and post-commit guard"
```

---

### Task 3: Upgrade-to-SSE path (`Pending.send(JsonRpcRequest)`)

**Files:**
- Modify: `.../LazyHttpTransportTest.java`

**Step 1: Write failing tests**

```java
@Test
void sendingNotificationFromPendingUpgradesToSse() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  OdysseyStream<JsonRpcMessage> stream = mock(OdysseyStream.class);
  SseEmitter emitter = new SseEmitter();
  var transport = new LazyHttpTransport(future, () -> stream, s -> emitter);

  var notification = new JsonRpcNotification("2.0", "notifications/progress", null);
  transport.send(notification);

  var entity = future.getNow(null);
  assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
  assertThat(entity.getBody()).isSameAs(emitter);
  verify(stream).publish(notification);
}

@Test
void sendingServerInitiatedCallFromPendingUpgradesToSse() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  OdysseyStream<JsonRpcMessage> stream = mock(OdysseyStream.class);
  SseEmitter emitter = new SseEmitter();
  var transport = new LazyHttpTransport(future, () -> stream, s -> emitter);

  var call = JsonRpcCall.of("elicitation/create", null, intNode(7));
  transport.send(call);

  assertThat(future.getNow(null).getBody()).isSameAs(emitter);
  verify(stream).publish(call);
}

@Test
void sseSubsequentResponseCompletesStream() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  OdysseyStream<JsonRpcMessage> stream = mock(OdysseyStream.class);
  var transport = new LazyHttpTransport(future, () -> stream, s -> new SseEmitter());

  transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
  var response = new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1));
  transport.send(response);

  verify(stream).publish(response);
  verify(stream).complete();
}

@Test
void sseAfterStreamCompleteThrows() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  OdysseyStream<JsonRpcMessage> stream = mock(OdysseyStream.class);
  var transport = new LazyHttpTransport(future, () -> stream, s -> new SseEmitter());

  transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
  transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

  assertThatThrownBy(() ->
          transport.send(new JsonRpcNotification("2.0", "notifications/late", null)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Stream completed");
}

@Test
void sseUpgradeIncludesSessionInitializedHeader() {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  OdysseyStream<JsonRpcMessage> stream = mock(OdysseyStream.class);
  var transport = new LazyHttpTransport(future, () -> stream, s -> new SseEmitter());
  transport.emit(new McpEvent.SessionInitialized("session-99", "2025-11-25"));

  transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));

  assertThat(future.getNow(null).getHeaders().getFirst("MCP-Session-Id")).isEqualTo("session-99");
}
```

**Step 2: Run — expect PASS (code already implemented in Task 1).**

**Step 3: Commit.**

```
git commit -m "Cover LazyHttpTransport SSE upgrade, stream completion, and header handling"
```

---

### Task 4: Rewire controller to use `LazyHttpTransport` on a virtual thread

**Files:**
- Modify: `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
  - `handlePost` return type → `CompletableFuture<ResponseEntity<Object>>`.
  - Replace the `JsonRpcCall` branch (INITIALIZE/non-tools synchronous vs. tools streaming) with a single path that creates a `LazyHttpTransport`, spawns a virtual thread to run `server.handleCall`, and returns the transport's future.
  - Keep `handleNotification` / `handleResponse` returning completed futures wrapping `ResponseEntity.accepted().build()` so the method signature is uniform.

**Step 1: Sketch the new `handleCall`**

```java
private CompletableFuture<ResponseEntity<Object>> handleCall(McpContext context, JsonRpcCall call) {
  var future = new CompletableFuture<ResponseEntity<Object>>();
  String sessionId = context.sessionId(); // null for initialize
  Supplier<OdysseyStream<JsonRpcMessage>> streams =
      () -> odyssey.stream(UUID.randomUUID().toString(), JsonRpcMessage.class);
  Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitters =
      stream ->
          stream.subscribe(
              cfg ->
                  cfg.mapper(encryptingMapper(sessionId))
                      .onSubscribe(
                          e ->
                              e.send(
                                  SseEmitter.event()
                                      .id(encrypt(sessionId, stream.name() + ":" + PRIMING_EVENT_ID))
                                      .data(""))));
  var transport = new LazyHttpTransport(future, streams, emitters);
  Thread.ofVirtual().start(() -> {
    try {
      server.handleCall(context, call, transport);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
  });
  return future;
}
```

For `INITIALIZE`, `sessionId` is null when building the encrypting mapper / priming — but initialize only ever sends a single `JsonRpcResponse`, so the SSE path is never taken and those closures never execute. Safe.

`handlePost` becomes:

```java
public CompletableFuture<ResponseEntity<Object>> handlePost(...) {
  // validation branches return CompletableFuture.completedFuture(errorResponse)
  return switch (message) {
    case JsonRpcCall call -> {
      if (INITIALIZE.equals(call.method())) yield handleCall(McpContext.empty(), call);
      yield withContextAsync(sessionId, protocolVersion, ctx -> handleCall(ctx, call));
    }
    case JsonRpcNotification n ->
        withContextAsync(sessionId, protocolVersion,
            ctx -> { server.handleNotification(ctx, n); return CompletableFuture.completedFuture(ResponseEntity.accepted().build()); });
    case JsonRpcResponse r ->
        withContextAsync(sessionId, protocolVersion,
            ctx -> { server.handleResponse(ctx, r); return CompletableFuture.completedFuture(ResponseEntity.accepted().build()); });
  };
}
```

`withContextAsync` is a variant of the existing `withContext` that returns `CompletableFuture<ResponseEntity<Object>>` and wraps error branches in `CompletableFuture.completedFuture(...)`.

**Step 2: Run existing controller test — expect multiple failures** (signature mismatch on `handlePost`, `SynchronousTransport`/`OdysseyTransport` assertions).

**Step 3: Update controller test**

- `post` helper now returns `CompletableFuture<ResponseEntity<Object>>`; tests call `.get(5, TimeUnit.SECONDS)` or use `.getNow(null)` where appropriate.
- Assertions that previously checked `SynchronousTransport.class`/`OdysseyTransport.class` now check `LazyHttpTransport.class`.
- The "tools/call returns SSE" test now has to induce the handler to send a notification (or just a response) to choose the mode; adjust `doAnswer` to call `transport.send(...)` with the appropriate message type.
- `initializeReturnsJsonWithSessionHeader` — handler must `emit` then `send(JsonRpcResult)`; assertions unchanged except via future.

**Step 4: Run controller test + transport test — expect PASS.**

`mvn -pl mocapi-transport-streamable-http test`

**Step 5: Commit.**

```
git commit -m "Route all POST calls through LazyHttpTransport on a virtual thread"
```

---

### Task 5: Delete `SynchronousTransport` and `OdysseyTransport`

**Files:**
- Delete: `.../SynchronousTransport.java`
- Delete: `.../OdysseyTransport.java`
- Delete: `.../SynchronousTransportTest.java`
- Delete: `.../OdysseyTransportTest.java`

**Step 1: Verify no remaining references**

```
grep -r "SynchronousTransport\|OdysseyTransport" mocapi-transport-streamable-http/src
```

Expected: empty after the controller refactor.

**Step 2: Delete the four files; run full module test suite.**

`mvn -pl mocapi-transport-streamable-http test` → all green.

**Step 3: Commit.**

```
git commit -m "Remove SynchronousTransport and OdysseyTransport"
```

---

### Task 6: Verify `spotless:check` + release-profile javadoc + full build

**Step 1:**

```
mvn -pl mocapi-transport-streamable-http -am verify
mvn spotless:check
mvn -P release javadoc:jar -DskipTests
```

All three must pass. Javadoc failures in particular block the Maven Central publish later, so catch them here.

**Step 2: If any fail, fix + commit.**

---

## Edge cases worth a sanity check during review

- Handler that never sends anything (misbehaving tool) — future stays incomplete until Spring async timeout. Exception path in Task 4's virtual thread wraps covers thrown failures, but silent no-send is still a hang. Out of scope for this PR (same behavior as today).
- Handler that sends notification, then throws — SSE already committed; `completeExceptionally` is a no-op on an already-completed future. The thrown exception is dropped on the virtual thread floor. Acceptable for now (again same behavior as today's OdysseyTransport path); worth a follow-up to log it.
- `SseWriter` after `stream.complete()` throws `IllegalStateException`. That matches today's `OdysseyTransport` semantics. Preserved.
