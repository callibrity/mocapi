# Transport Inbound-Message Handling Consistency — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the two transports handle the same inbound-failure classes the same way, and remove the last bare error-code literal, so the validation layer reads consistently.

**Architecture:** No boundary changes — the audit confirmed layering is sound (transport-level validation stays in the transports; protocol validation stays in `mocapi-server`). This plan only closes three *consistency* gaps between `StreamableHttpController` (HTTP) and `StdioServer` (stdio), which today diverge on how they treat (a) an illegal client `JsonRpcResponse`, (b) an unparseable inbound message, and (c) the generic `-32000` code.

**Tech Stack:** Java 21, JUnit 5 + AssertJ, ripcurl (`JsonRpcProtocol` standard codes), Jackson (`tools.jackson`), Maven + Spotless.

## Global Constraints

- No star imports — explicit single-symbol imports only.
- No `@SuppressWarnings` (the only permitted one is the pre-existing `deprecation` suppression tied to `LegacyTitledEnumSchema`; do not add new ones).
- Formatting is enforced by Spotless in `verify`; run `mvn -q spotless:apply` before committing if needed.
- Standard JSON-RPC codes come from `com.callibrity.ripcurl.core.JsonRpcProtocol` (`PARSE_ERROR` = -32700, `INVALID_REQUEST` = -32600, `INTERNAL_ERROR` = -32603). Mocapi-private codes live in `com.callibrity.mocapi.server.JsonRpcErrorCodes`.
- Behavior parity target: **HTTP is the reference.** Where stdio differs, stdio moves to match HTTP unless a task's Decision Point says otherwise.

---

## ⚠ Decision Points (resolve during review)

- **Task 3 (stdio parse errors):** the plan makes stdio emit a `-32700` line (null id) for garbage input, matching HTTP. The alternative is to keep stdio's silent drop and instead *document* it as intentional. Both are defensible; the plan implements the "match HTTP" option. If you prefer the silent-drop option, skip Task 3's code steps and keep only its `transports.md` note (reworded to describe the intentional drop).
- **Task 1 (home of `SERVER_ERROR`):** the plan puts the named `-32000` constant in the core registry `JsonRpcErrorCodes` (which already owns the code-space documentation). If you'd rather keep a transport-only code out of the core, define it as a `private static final` in `StreamableHttpController` instead — same call sites, one different file.

---

### Task 1: Name the generic `-32000` code

**Files:**
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/JsonRpcErrorCodes.java`
- Modify: `mocapi-streamable-http-transport/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java:100,106`
- Test: `mocapi-server/src/test/java/com/callibrity/mocapi/server/JsonRpcErrorCodesTest.java`

**Interfaces:**
- Produces: `JsonRpcErrorCodes.SERVER_ERROR` (`public static final int` = `-32000`).

This is a refactor (extract-constant), so the "test" pins the constant's value and existing controller tests guard behavior parity.

- [ ] **Step 1: Add a value test for the new constant**

In `JsonRpcErrorCodesTest.java`, add:

```java
@Test
void server_error_is_the_generic_implementation_defined_base_code() {
  assertThat(JsonRpcErrorCodes.SERVER_ERROR).isEqualTo(-32000);
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -pl mocapi-server test -Dtest=JsonRpcErrorCodesTest`
Expected: FAIL — `SERVER_ERROR` does not exist (compile error).

- [ ] **Step 3: Add the constant**

In `JsonRpcErrorCodes.java`, above `FORBIDDEN`:

```java
  /**
   * Generic implementation-defined server error ({@code -32000}), the base of JSON-RPC 2.0's
   * server-error range. Used for transport-level rejections that carry a JSON-RPC body but no more
   * specific code (e.g. an unacceptable {@code Accept} header or a disallowed {@code Origin}).
   */
  public static final int SERVER_ERROR = -32000;
```

- [ ] **Step 4: Reference it from the controller**

In `StreamableHttpController.java`, replace the two bare `-32000` literals (Not Acceptable at ~line 100, Forbidden at ~line 106) with `JsonRpcErrorCodes.SERVER_ERROR`, and add the import:

```java
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
```

- [ ] **Step 5: Run affected tests**

Run: `mvn -q -pl mocapi-server,mocapi-streamable-http-transport -am test`
Expected: PASS — the new value test passes and existing controller 406/403 tests are unchanged (wire code still `-32000`).

- [ ] **Step 6: Commit**

```bash
git add mocapi-server/src/main/java/com/callibrity/mocapi/server/JsonRpcErrorCodes.java \
        mocapi-server/src/test/java/com/callibrity/mocapi/server/JsonRpcErrorCodesTest.java \
        mocapi-streamable-http-transport/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java
git commit -m "refactor(transport): name the generic -32000 server-error code"
```

---

### Task 2: stdio rejects an illegal client `JsonRpcResponse` with `-32600`

**Files:**
- Modify: `mocapi-stdio-transport/src/main/java/com/callibrity/mocapi/transport/stdio/StdioServer.java:123-130` (`handleResponse`)
- Test: `mocapi-stdio-transport/src/test/java/com/callibrity/mocapi/transport/stdio/StdioServerTest.java`

**Context:** 2026-07-28 has no server-initiated requests, so a client-sent `JsonRpcResponse` is illegal. HTTP already answers it with `-32600 INVALID_REQUEST` (`StreamableHttpController:112`). stdio currently only logs and drops it. This makes stdio consistent. stdio's existing `sendError(JsonNode id, int code, String message)` helper (`:132`) does the work.

**Interfaces:**
- Consumes: `StdioServer.sendError(JsonNode, int, String)` (existing private helper), `JsonRpcProtocol.INVALID_REQUEST`.

- [ ] **Step 1: Write the failing test**

In `StdioServerTest.java` (follow the file's existing capturing-transport pattern):

```java
@Test
void client_response_is_rejected_with_invalid_request() {
  JsonNode id = JsonNodeFactory.instance.numberNode(7);
  // A JSON-RPC response line (has "result", no "method"):
  String line = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}";

  dispatchLine(line); // helper that runs StdioServer.dispatch on one line — see existing tests

  JsonRpcError sent = onlyCapturedError();
  assertThat(sent.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
  assertThat(sent.id()).isEqualTo(id);
}
```

> If `StdioServerTest` has no `dispatchLine`/`onlyCapturedError` helpers yet, add thin ones over the existing capturing `StdioTransport` test double and the package-private/di-injected reader the other tests already use. Do not widen `StdioServer`'s visibility beyond what current tests rely on.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -pl mocapi-stdio-transport test -Dtest=StdioServerTest`
Expected: FAIL — no error is captured (message is currently dropped).

- [ ] **Step 3: Emit the error in `handleResponse`**

Replace the body of `handleResponse(JsonRpcResponse response)` so it still logs *and* now answers:

```java
private void handleResponse(JsonRpcResponse response) {
  // MCP 2026-07-28 has no server-initiated requests (ADR-0020), so a client has nothing to
  // respond to. Match the HTTP transport: reject as an invalid request rather than dropping.
  log.warn(
      "Rejected unexpected client response (id {}): no server-initiated requests in MCP {}",
      response.id(),
      McpServer.PROTOCOL_VERSION);
  sendError(
      response.id(),
      JsonRpcProtocol.INVALID_REQUEST,
      "Invalid Request: MCP "
          + McpServer.PROTOCOL_VERSION
          + " has no server-initiated requests, so clients have no responses to deliver");
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -q -pl mocapi-stdio-transport test -Dtest=StdioServerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-stdio-transport/src/main/java/com/callibrity/mocapi/transport/stdio/StdioServer.java \
        mocapi-stdio-transport/src/test/java/com/callibrity/mocapi/transport/stdio/StdioServerTest.java
git commit -m "fix(stdio): reject illegal client responses with -32600, matching HTTP"
```

---

### Task 3: stdio emits `-32700` for unparseable input  ⚠ Decision Point

**Files:**
- Modify: `mocapi-stdio-transport/src/main/java/com/callibrity/mocapi/transport/stdio/StdioServer.java:90-97` (`dispatch`)
- Test: `mocapi-stdio-transport/src/test/java/com/callibrity/mocapi/transport/stdio/StdioServerTest.java`

**Context:** HTTP answers an unparseable body with `-32700 Parse error` (null id) via its `@ExceptionHandler` (`StreamableHttpController:134`). stdio currently logs and drops. This task makes stdio symmetric. A parse failure has no recoverable id, so the reply carries a null id — exactly like HTTP. **If review chooses the silent-drop option, skip Steps 1–4 and implement only the `transports.md` note in Task 4.**

- [ ] **Step 1: Write the failing test**

```java
@Test
void unparseable_line_is_answered_with_parse_error_and_null_id() {
  dispatchLine("{ this is not json");

  JsonRpcError sent = onlyCapturedError();
  assertThat(sent.error().code()).isEqualTo(JsonRpcProtocol.PARSE_ERROR);
  assertThat(sent.id().isNull()).isTrue();
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -pl mocapi-stdio-transport test -Dtest=StdioServerTest`
Expected: FAIL — nothing captured (line is dropped today).

- [ ] **Step 3: Emit the parse error in `dispatch`**

In `dispatch(String line)`, change the catch block so it answers instead of only logging:

```java
try {
  message = objectMapper.readValue(line, JsonRpcMessage.class);
} catch (Exception e) {
  log.warn("Unparseable JSON-RPC message: {}", e.getMessage());
  sendError(null, JsonRpcProtocol.PARSE_ERROR, "Parse error: " + e.getMessage());
  return;
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -q -pl mocapi-stdio-transport test -Dtest=StdioServerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-stdio-transport/src/main/java/com/callibrity/mocapi/transport/stdio/StdioServer.java \
        mocapi-stdio-transport/src/test/java/com/callibrity/mocapi/transport/stdio/StdioServerTest.java
git commit -m "fix(stdio): answer unparseable input with -32700, matching HTTP"
```

---

### Task 4: Document the unified inbound-handling behavior

**Files:**
- Modify: `docs/design/transports.md` (Stdio section, ~lines 156-161; and the HTTP "Lazy JSON-vs-SSE" note about POSTed responses, ~line 122)

- [ ] **Step 1: Update the stdio section**

Replace the current bullet 4 ("Client JSON-RPC responses are dropped with a warning…") and add parse-error behavior so the doc states the parity explicitly:

```markdown
4. Envelope semantics live in the server core: a request without the `_meta`
   envelope gets the server's `-32602`, relayed verbatim to stdout.
5. Inbound messages that cannot be dispatched are answered, not dropped —
   matching the HTTP transport: an unparseable line → `-32700` (null id); a
   client-sent JSON-RPC *response* → `-32600` (2026-07-28 has no
   server-initiated requests, so there is nothing to respond to).
```

(Renumber the following bullets accordingly.)

- [ ] **Step 2: Cross-reference from the HTTP section**

In the HTTP "Notifications POSTed by the client…" paragraph, confirm it still reads correctly and add a trailing sentence: `stdio applies the same rule.`

- [ ] **Step 3: Verify the full build is green**

Run: `mvn -q -T1C clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add docs/design/transports.md
git commit -m "docs(transports): document unified inbound-failure handling across transports"
```

---

## Self-Review

- **Coverage:** #1 (stray response) → Task 2; #2 (parse errors) → Task 3; #3 (magic number) → Task 1; doc parity → Task 4. All three audit findings covered.
- **Placeholders:** none — every code step shows the code. The one soft spot is the test helper names (`dispatchLine`/`onlyCapturedError`); the plan flags that they may need thin additions matching `StdioServerTest`'s existing double.
- **Type consistency:** `SERVER_ERROR` (int), `sendError(JsonNode,int,String)`, `JsonRpcProtocol.PARSE_ERROR`/`INVALID_REQUEST` used consistently across tasks.
