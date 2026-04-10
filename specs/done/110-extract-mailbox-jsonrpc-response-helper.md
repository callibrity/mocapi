# Extract shared mailbox-polling helper for client response requests

## What to build

After spec 105 lands, both `DefaultMcpStreamContext.sample()` and
`DefaultMcpStreamContext.sendElicitationAndWait()` follow an almost
identical pattern:

1. Generate a random `jsonRpcId` (UUID).
2. Build a `JsonRpcCall` with the target method name and params.
3. Create a `Mailbox<JsonRpcResponse>` keyed on the `jsonRpcId`.
4. Publish the call to the stream.
5. Poll the mailbox with a timeout.
6. Handle timeout → throw a flow-specific exception
   (`McpSamplingTimeoutException` or `McpElicitationTimeoutException`).
7. Pattern match the result:
   - `JsonRpcError` → throw a flow-specific exception
     (`McpSamplingException` or `McpElicitationException`) with the
     error detail.
   - `JsonRpcResult` → return the typed `JsonRpcResult` to the caller
     for flow-specific parsing.
8. Close the mailbox in a `finally` block.

Extract this into a private helper method on `DefaultMcpStreamContext`
so the two flows share a single code path:

```java
private JsonRpcResult sendAndWaitForResult(
    String method,
    JsonNode params,
    Duration timeout,
    Function<String, ? extends RuntimeException> timeoutExceptionFactory,
    Function<String, ? extends RuntimeException> errorExceptionFactory) {
  String jsonRpcId = UUID.randomUUID().toString();
  JsonRpcCall request =
      JsonRpcCall.of(method, params, objectMapper.valueToTree(jsonRpcId));
  Mailbox<JsonRpcResponse> mailbox =
      mailboxFactory.create(jsonRpcId, JsonRpcResponse.class);
  try {
    stream.publishJson(toJsonTree(request));
    Optional<JsonRpcResponse> raw = mailbox.poll(timeout);
    if (raw.isEmpty()) {
      throw timeoutExceptionFactory.apply(
          method + " timed out after " + timeout);
    }
    return switch (raw.get()) {
      case JsonRpcError error ->
          throw errorExceptionFactory.apply(
              "Client returned JSON-RPC error: " + error.error());
      case JsonRpcResult result -> result;
    };
  } finally {
    mailbox.delete();
  }
}
```

Then `sample()` and `sendElicitationAndWait()` become concise
consumers:

```java
@Override
public CreateMessageResult sample(String prompt, int maxTokens) {
  requireSamplingSupport();
  var requestParams = new CreateMessageRequestParams(...);
  JsonRpcResult result =
      sendAndWaitForResult(
          "sampling/createMessage",
          objectMapper.valueToTree(requestParams),
          samplingTimeout,  // or elicitationTimeout if they're the same
          McpSamplingTimeoutException::new,
          McpSamplingException::new);
  return objectMapper.treeToValue(result.result(), CreateMessageResult.class);
}

private JsonRpcResult sendElicitationAndWait(String message, RequestedSchema requestedSchema) {
  ElicitRequestFormParams formParams =
      new ElicitRequestFormParams("form", message, requestedSchema, null, null);
  return sendAndWaitForResult(
      "elicitation/create",
      objectMapper.valueToTree(formParams),
      elicitationTimeout,
      McpElicitationTimeoutException::new,
      McpElicitationException::new);
}
```

## Acceptance criteria

- [ ] A new private method
      `sendAndWaitForResult(String method, JsonNode params, Duration
      timeout, Function<String, ? extends RuntimeException>
      timeoutExceptionFactory, Function<String, ? extends
      RuntimeException> errorExceptionFactory)` exists on
      `DefaultMcpStreamContext` and encapsulates the mailbox
      create / publish / poll / close sequence.
- [ ] The helper method pattern-matches the polled
      `JsonRpcResponse` via a sealed switch:
  - `JsonRpcError` → invokes `errorExceptionFactory.apply(...)` and
    throws the result.
  - `JsonRpcResult` → returns it.
- [ ] `DefaultMcpStreamContext.sample()` uses the helper. The
    inline mailbox creation, publish, poll, timeout check, error
    pattern match, and try/finally are all deleted from
    `sample()`.
- [ ] `DefaultMcpStreamContext.sendElicitationAndWait()` uses the
    helper. Same deletion of inline mailbox plumbing.
- [ ] Neither method has any remaining `Mailbox<JsonRpcResponse>`
    instantiation inside its own body — both delegate to the
    helper.
- [ ] The sampling and elicitation timeout values
    (`samplingTimeout`, `elicitationTimeout`) are unchanged. If
    they're the same field today (e.g., both use
    `elicitationTimeout`), that quirk is preserved — this spec is
    not about unifying timeouts.
- [ ] Existing tests in `DefaultMcpStreamContextTest` continue to
    pass without changes. The refactor is internal; tool-author
    behavior is identical.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- **Dependency**: this spec depends on spec 105 having landed
  (mailbox retype to `Mailbox<JsonRpcResponse>`). Both the sampling
  and elicitation flows need to already be using the typed mailbox
  before you can extract the shared helper — otherwise there's
  nothing to share.
- **Why extract it**: DRY. The two flows have ~15 lines of identical
  plumbing each, and the only differences are the method name,
  params, timeout field, and exception types. A parameterized helper
  eliminates the duplication and makes future changes
  (e.g., adding retry logic, instrumentation, a cancellation hook)
  apply to both flows in one place.
- **Exception factories as `Function<String, RuntimeException>`**:
  the factories take the pre-built message string and return a
  matched exception. This keeps the helper ignorant of the
  exception types — it just calls `.apply(msg)` and throws the
  result. Each caller supplies its own exception type via method
  reference (`McpSamplingTimeoutException::new`), which requires
  each exception to have a single-arg `(String message)` constructor.
  Verify this is the case before implementing; if any exception
  class has a different constructor shape, either add a
  `(String)` constructor or use a `Supplier<RuntimeException>` /
  custom functional interface instead.
- **Do NOT merge the sampling and elicitation timeout fields in this
  spec.** They may be conceptually different (elicitation waits for
  user input, sampling waits for LLM response) and could want
  independent tunings. If today they're both `elicitationTimeout`,
  that's a pre-existing quirk worth cleaning up in a separate spec.
- **The helper returns `JsonRpcResult`, not the deserialized payload**.
  Each caller is responsible for doing the final
  `treeToValue(result.result(), ...)` step into its own result type
  (`CreateMessageResult` for sampling, `ElicitResult` for elicitation).
  This is because the two flows deserialize into different types and
  may also want different schema validation.
- **Commit suggestion**: single commit for the extraction.
  Bisectability isn't a concern for a pure refactor that doesn't
  change behavior.
- **Do NOT touch** the error message wording. Keep it as-is so that
  any existing tests asserting on the message string continue to
  pass. The helper builds messages the same way the inline code
  does today.
