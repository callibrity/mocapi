# MCP elicitation support via Substrate Mailbox

## What to build

Implement MCP elicitation (`elicitation/create`) so that server-side tool handlers can
request structured input from the user during execution. The handler blocks on a virtual
thread until the client responds, using Substrate's Mailbox as the distributed rendezvous
point.

### `McpStreamContext.elicit()` API

Add `elicit()` methods to `McpStreamContext` that accept a message and a type to generate
the form schema from:

```java
<T> ElicitationResult<T> elicit(String message, Class<T> type);
<T> ElicitationResult<T> elicit(String message, TypeReference<T> type);
```

Both overloads:

1. Generate a JSON Schema from the provided type using victools' `SchemaGenerator`
   (already used in the tools module for input/output schemas). MCP restricts
   elicitation schemas to flat objects with primitive properties — the generator should
   be configured to produce schemas within that constraint.
2. Build a JSON-RPC request: `method: "elicitation/create"`, a unique `id`, params
   containing `mode: "form"`, the `message`, and the generated `requestedSchema`.
3. Create a Substrate Mailbox: `mailboxFactory.create("elicit:" + jsonRpcId, JsonNode.class)`.
   The mailbox value type is raw `JsonNode`, not the typed result — validation and
   deserialization happen on the calling node (see below).
4. Publish the JSON-RPC request as an SSE event on the current Odyssey stream
   (no event type — `stream.publishJson(request)`).
5. Block: `Optional<JsonNode> raw = mailbox.poll(timeout)`.
6. If the mailbox times out, throw an unchecked exception (e.g.,
   `McpElicitationTimeoutException`).
7. Parse the JSON-RPC response to extract the `action` and `content` fields.
8. If `action` is `"accept"`, validate `content` against the generated schema. If
   validation fails, throw an exception. If it passes, deserialize `content` into `T`
   using Jackson's `ObjectMapper`.
9. Return an `ElicitationResult<T>` containing the action and the typed content.

### `ElicitationResult<T>`

A simple record or class:

```java
public record ElicitationResult<T>(ElicitationAction action, T content) {
    public boolean accepted() { return action == ElicitationAction.ACCEPT; }
    public boolean declined() { return action == ElicitationAction.DECLINE; }
    public boolean cancelled() { return action == ElicitationAction.CANCEL; }
}

public enum ElicitationAction { ACCEPT, DECLINE, CANCEL }
```

For `decline` and `cancel` actions, `content` is null.

### POST handler: route JSON-RPC responses to mailboxes

The POST handler's `handleNotificationOrResponse()` method currently acknowledges
JSON-RPC responses with 202 and discards them. Change it to:

1. If the incoming message is a JSON-RPC response (has `result` or `error`, no `method`):
2. Extract the `id` field.
3. Look up a Substrate Mailbox: `mailboxFactory.create("elicit:" + id, JsonNode.class)`.
   (Substrate's `create()` returns the existing mailbox for a given key if one exists.)
4. Call `mailbox.deliver(resultNode)` with the `result` field from the response.
5. Return 202 Accepted.

If no mailbox exists for the ID (no pending elicitation), the `deliver()` is a no-op —
the value goes nowhere. This is fine; the spec says the server accepts the response.

For JSON-RPC error responses from the client, deliver an error indicator so the calling
thread can handle it (e.g., deliver a sentinel or throw from `elicit()`).

### Schema generation and validation

Reuse the existing victools `SchemaGenerator` setup from the tools module. The tools
module already configures the generator with swagger-2, jackson, and jakarta-validation
modules. Either extract the generator configuration into a shared location (e.g.,
`mocapi-core`) or create a shared `SchemaService` bean.

Schema validation on the calling node uses the same `json-sKema` library already used
in `McpToolsCapability.validateInput()`.

### Capability negotiation

During `initialize`, the server should declare `elicitation` support in its response
only if the client declared elicitation capability in its request. Track the client's
elicitation capability in `McpSession` so that `McpStreamContext.elicit()` can check
it and throw if the client doesn't support elicitation.

### Substrate Mailbox dependency

`substrate-core` is already a transitive dependency via `odyssey-core`. The
`MailboxFactory` bean is auto-configured by `SubstrateAutoConfiguration`. Inject it
into `McpStreamingController` or `McpStreamContext`.

## Acceptance criteria

- [ ] `McpStreamContext` has `elicit(String, Class<T>)` method
- [ ] `McpStreamContext` has `elicit(String, TypeReference<T>)` method
- [ ] `ElicitationResult<T>` type exists with `action` and typed `content`
- [ ] Calling `elicit()` publishes a JSON-RPC `elicitation/create` request on the SSE
      stream with a generated `requestedSchema`
- [ ] Calling `elicit()` blocks the virtual thread until the client responds
- [ ] Client's JSON-RPC response POST is routed to the correct Substrate Mailbox
- [ ] The response `content` is validated against the generated schema on the calling node
- [ ] Valid responses are deserialized into the requested type `T`
- [ ] Invalid responses cause `elicit()` to throw
- [ ] Timeout causes `elicit()` to throw an unchecked exception
- [ ] `decline` and `cancel` actions return `ElicitationResult` with null content
- [ ] Elicitation works across nodes (mailbox is distributed via Substrate backend)
- [ ] Server only offers elicitation capability if client declared it during init
- [ ] `elicit()` throws if client doesn't support elicitation
- [ ] Schema generation reuses victools configuration from tools module
- [ ] All new behavior has unit tests
- [ ] `mvn verify` passes

## Implementation notes

- This spec depends on 011 (Odyssey migration) and 012 (McpStreamContext).
- The schema lives on the calling thread's stack (generated from `Class<T>` before
  the mailbox poll). It does NOT need to be stored in distributed infrastructure.
  Validation and deserialization happen after `poll()` returns, on the same node that
  called `elicit()`.
- The Substrate Mailbox key format `"elicit:" + jsonRpcId` must be unique per
  elicitation. Use a UUID for the JSON-RPC request ID.
- MCP elicitation schemas are restricted to flat objects with primitive properties.
  The schema generator should be configured or validated to reject nested/complex types.
  Simple Java records with `String`, `int`, `boolean`, and enum fields are the target.
- URL mode elicitation is a separate concern and can be added in a future spec.
- Sampling (`sampling/createMessage`) follows the same mailbox pattern and can be
  implemented similarly in a follow-up spec.
