# Enforce void return type for streaming tool methods

## What to build

Tool methods that declare an `McpStreamContext<R>` parameter must
return `void`. The streaming model sends the final result via
`ctx.sendResult(R)` — mixing "return the result" with "stream it
via ctx" is ambiguous and currently unenforced. This spec adds
validation at tool registration time that rejects any
`@ToolMethod` whose method declares both:

1. An `McpStreamContext<R>` parameter, AND
2. A non-`void` return type

and also updates the existing `CountdownTool` example (which
currently returns `CountdownResponse`) to match the enforced
pattern.

### Why

- **Unambiguous result delivery**: with the rule enforced,
  there is exactly one way to deliver the final result of a
  streaming tool (`ctx.sendResult(...)`). The framework
  doesn't have to decide which takes precedence if both are
  provided.
- **Forces explicit flow control**: void return + explicit
  `sendResult` makes the tool author think about *when* the
  result is sent, not just *what* it is. For long-running
  tools that stream progress, this is the right mental model.
- **Catches mistakes early**: a developer writing a streaming
  tool who forgets `ctx.sendResult(...)` gets a clear
  compile-time or registration-time error rather than a silent
  "no final result sent" bug at runtime.

### Validation location

The check should live in `AnnotationMcpTool`'s constructor (or
wherever `@ToolMethod`-annotated methods are reflected and
registered). When the tool is scanned:

```java
boolean hasStreamContext = /* existing detection logic */;
if (hasStreamContext && method.getReturnType() != void.class
    && method.getReturnType() != Void.class) {
  throw new IllegalStateException(
      "Streaming tool method "
          + targetClass.getName() + "." + method.getName()
          + " must return void — use ctx.sendResult(R) to deliver "
          + "the final result instead of returning it.");
}
```

Fail fast at application startup, not at the first tool
invocation.

### CountdownTool update

The current `CountdownTool` returns `CountdownResponse`. Rewrite
it to match the enforced pattern:

```java
@ToolService
public class CountdownTool {

  @ToolMethod(
      name = "countdown",
      description = "Counts down from the given number, sending progress updates via SSE")
  public void countdown(int from, McpStreamContext<CountdownResponse> ctx) {
    for (int i = from; i > 0; i--) {
      ctx.sendProgress((double) (from - i), from);
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    ctx.sendResult(new CountdownResponse("Countdown from " + from + " complete!"));
  }

  public record CountdownResponse(String message) {}
}
```

Key changes:
- Return type `CountdownResponse` → `void`
- Final `return new CountdownResponse(...)` replaced with
  `ctx.sendResult(new CountdownResponse(...))`
- Interrupt handling changed to `return` (void) instead of
  `break`

### Existing test updates

`CountdownToolTest` (unit test) and any `CountdownToolIT`
(integration test) must be updated to:

1. Declare a local `McpStreamContext` mock.
2. Invoke `countdown(...)` without expecting a return value.
3. Assert that `ctx.sendResult(...)` was called with the
   expected `CountdownResponse`.
4. Assert that `ctx.sendProgress(...)` was called the expected
   number of times.

### Integration test for registration validation

Add a new test class (e.g., `AnnotationMcpToolValidationTest`)
that attempts to register a tool class with the illegal
combination (`McpStreamContext<R>` parameter + non-void
return) and asserts that the tool registration throws
`IllegalStateException` with the expected message substring.

## Acceptance criteria

### Validation logic

- [ ] `AnnotationMcpTool` (or the equivalent tool-discovery
      class) rejects any `@ToolMethod` whose method declares
      both an `McpStreamContext<?>` parameter and a non-`void`
      return type.
- [ ] The rejection throws `IllegalStateException` with a
      message clearly identifying the offending method and
      explaining the rule.
- [ ] Non-streaming tool methods (no `McpStreamContext`
      parameter) with non-void returns continue to work as
      they do today — this is strictly about streaming
      methods.
- [ ] Streaming methods with void return types continue to
      work — they use `ctx.sendResult(...)` to deliver the
      final result.

### CountdownTool migration

- [ ] `CountdownTool.countdown(...)` returns `void` instead of
      `CountdownResponse`.
- [ ] The method ends by calling
      `ctx.sendResult(new CountdownResponse(...))`.
- [ ] The `InterruptedException` catch block uses `return`
      instead of `break` to exit the method (since the return
      type is now void).
- [ ] `CountdownToolTest` and any `CountdownToolIT` are
      updated to match the new signature: they invoke
      `countdown` without expecting a return value, and assert
      on `ctx.sendResult(...)` being called with the expected
      payload.

### Registration validation test

- [ ] A new test class `AnnotationMcpToolValidationTest` (or
      similar) exists that:
  - Defines a fixture tool class whose method has the illegal
    combination (streaming + non-void).
  - Registers the tool via `AnnotationMcpTool.createTools(...)`.
  - Asserts that an `IllegalStateException` is thrown with
    a message that includes the offending method name and a
    hint about `ctx.sendResult(...)`.

### Integration checks

- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.
- [ ] No other tool definitions in the reactor (in
      `mocapi-example`, `mocapi-compat`, any test fixtures)
      have the illegal pattern. Grep before committing; if any
      are found, fix them alongside CountdownTool.

## Implementation notes

- **`McpStreamContext<?>` detection**: the existing tool
  discovery already finds this parameter (to determine
  streamability and to extract the `<R>` type for output
  schema generation). Reuse that logic — don't duplicate it.
- **Error message format**: include the fully qualified
  class name and method name so developers can jump directly
  to the offending location in their IDE:
  ```
  Streaming tool method com.example.BadTool.doThing must return void —
  use ctx.sendResult(R) to deliver the final result instead of
  returning it.
  ```
- **`Void` vs `void`**: accept both the primitive `void` and
  the wrapper `Void` (some reflective tools produce `Void.TYPE`
  for void methods; equality with `void.class` catches the
  common case, but checking both is safe).
- **Don't deprecate and remove** — just reject outright.
  There's no evidence that any tool author is relying on the
  "return value from streaming method" path in a way that
  merits a deprecation cycle. Fail fast, give a clear error,
  done.
- **`ConformanceTools` may have streaming tools with non-void
  returns** — those need to be updated too. Grep before the
  validation check lands, or the compat module will fail at
  application startup.
- **Commit granularity**:
  1. Add the validation check in `AnnotationMcpTool`.
  2. Update `CountdownTool` + its tests.
  3. Update `ConformanceTools` (or any other offending tools).
  4. Add the validation test.
  5. Verify `mvn verify` is green end-to-end.
