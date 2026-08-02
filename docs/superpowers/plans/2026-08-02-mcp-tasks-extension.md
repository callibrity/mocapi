# MCP Tasks Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `io.modelcontextprotocol/tasks` extension as an optional `mocapi-tasks` module: `@McpTask`-annotated tools transparently run as polled background tasks, with MRTR-replay resume through a `TaskStore`.

**Architecture:** Three behavior-preserving seams are added to `mocapi-server` first (replay-core extraction, progress sink, tools/call dispatch hook + routing-header contribution), gated on the existing MRTR suite staying green. The new `mocapi-tasks` module then builds on them: extension wire types, an atomic-mutation `TaskStore` SPI with an in-memory default and contract TCK, a `TaskExecutionEngine` that spawns snapshot-wrapped virtual threads, and `McpTasksService` for `tasks/get|update|cancel`. Autoconfiguration follows the `mocapi-apps` precedent (autoconfig class lives in `mocapi-autoconfigure` behind `@ConditionalOnClass`).

**Tech Stack:** Java 25 (records, sealed interfaces, ScopedValue, virtual threads), Spring Boot 4.0.5 autoconfiguration, ripcurl 2.12 (`@JsonRpcMethod` auto-scan, `JsonRpcExceptionTranslator`), Jackson 3 (`tools.jackson.databind` types + `com.fasterxml.jackson.annotation` annotations), micrometer `context-propagation`, JUnit 5 + AssertJ + Mockito.

**Authoritative spec:** `docs/superpowers/specs/2026-08-02-mcp-tasks-extension-design.md`. Section references (§) below point there.

## Global Constraints

- Every new `.java` file needs the Apache-2 license header — `com.mycila:license-maven-plugin` fails `validate` without it. Run `mvn license:format` for new files if needed.
- google-java-format enforced (`spotless:check` bound to `validate`). Run `mvn spotless:apply` before committing.
- **No star imports. No warning suppressions** (`@SuppressWarnings` forbidden except the documented `LegacyTitledEnumSchema` deprecation case, which this work does not touch).
- Jackson import split: annotations from `com.fasterxml.jackson.annotation.*`, everything else (`JsonNode`, `ObjectNode`, `ObjectMapper`) from `tools.jackson.databind.*`.
- New module poms declare only parent/artifactId/name/description/dependencies (build plugins are inherited). Parent: `com.callibrity.mocapi:mocapi-parent:1.2.0-SNAPSHOT`.
- Model records: `@JsonInclude(JsonInclude.Include.NON_NULL)` at type level; `resultType` is a plain `String` component.
- Error codes: `-32602` = `com.callibrity.ripcurl.core.JsonRpcProtocol.INVALID_PARAMS`; missing capability = `-32021` = `MissingRequiredClientCapabilityErrorData.CODE` (mocapi-model). Never invent codes (constitution I9).
- Tasks 1–5 are **behavior-preserving refactors**: the full existing test suite of the touched module must pass unchanged (no test edits) before the task commits, except where a task explicitly adds new tests.
- Commit after every task (conventional commits; `refactor(server):` for tasks 1–5, `feat(tasks):` for 6–13, `docs:` for 14).
- Full-suite check between phases: `mvn -q verify` from the repo root.

---

### Task 1: Extract `ReplayExecutor` from `MrtrElicitationEngine` (behavior-preserving)

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/mrtr/ReplayExecutor.java`
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/mrtr/ReplayOutcome.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/mrtr/MrtrElicitationEngine.java`
- Test: `mocapi-server/src/test/java/com/callibrity/mocapi/server/mrtr/ReplayExecutorTest.java`

**Interfaces:**
- Consumes: existing `ResponseLedgerEntry`, `InputRequiredException` (package-private ctor — `ReplayExecutor` is in the same package), `ElicitationLedgerMismatchException`, `Hashes`, `ElicitationDispatcher`.
- Produces (later tasks rely on these exact signatures):
  - `public final class ReplayExecutor implements ElicitationDispatcher` with:
    - `public ReplayExecutor(ObjectMapper objectMapper)`
    - `public ElicitResult elicit(ElicitRequestFormParams params)` (consults the ScopedValue; throws `IllegalStateException` when unbound — same message text as today's `MrtrElicitationEngine.elicit`)
    - `public ReplayOutcome execute(List<ResponseLedgerEntry> ledger, Supplier<Object> invocation)`
  - `public sealed interface ReplayOutcome permits ReplayOutcome.Completed, ReplayOutcome.InputRequired` with nested records `Completed(Object result)` and `InputRequired(String key, ElicitRequestFormParams params, List<ResponseLedgerEntry> entries)`.
  - `MrtrElicitationEngine` gains `public ReplayExecutor replayExecutor()`.

The move: `MrtrElicitationEngine`'s `EXECUTION` ScopedValue, `KEY_PREFIX`, `fingerprintOf`, and the private `ReplayExecution` class all move into `ReplayExecutor`. `ReplayExecutor.execute` binds the ScopedValue, runs the invocation, and converts `InputRequiredException` into `ReplayOutcome.InputRequired` (it does **not** catch `ElicitationLedgerMismatchException` — that keeps propagating; the wire engine translates it). `MrtrElicitationEngine` keeps its public API **unchanged** (`execute(method, requestParams, inputResponses, requestState, invocation)` and `elicit(...)` delegating to the executor) so `McpToolsService`/`McpPromptsService`/`McpResourcesService` and every existing test compile and pass untouched.

- [ ] **Step 1: Write the failing test**

```java
// mocapi-server/src/test/java/com/callibrity/mocapi/server/mrtr/ReplayExecutorTest.java
class ReplayExecutorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReplayExecutor executor = new ReplayExecutor(objectMapper);

  private ElicitRequestFormParams question(String message) {
    return new ElicitRequestFormParams(message, null);
  }

  @Test
  void first_unanswered_elicit_yields_input_required_outcome_with_key_elicit_1() {
    ReplayOutcome outcome =
        executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    assertThat(outcome).isInstanceOf(ReplayOutcome.InputRequired.class);
    var ir = (ReplayOutcome.InputRequired) outcome;
    assertThat(ir.key()).isEqualTo("elicit-1");
    assertThat(ir.params().message()).isEqualTo("Your email?");
    assertThat(ir.entries()).hasSize(1);
    assertThat(ir.entries().getFirst().isAnswered()).isFalse();
  }

  @Test
  void answered_ordinal_returns_result_and_completes() {
    // Round 1: capture the ledger.
    var round1 =
        (ReplayOutcome.InputRequired)
            executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    // Answer it.
    var answer = new ElicitResult(ElicitAction.ACCEPT, JsonNodeFactory.instance.objectNode());
    List<ResponseLedgerEntry> answered =
        List.of(round1.entries().getFirst().answeredWith(answer));
    // Round 2: replay completes.
    ReplayOutcome outcome =
        executor.execute(answered, () -> executor.elicit(question("Your email?")).action());
    assertThat(outcome).isInstanceOf(ReplayOutcome.Completed.class);
    assertThat(((ReplayOutcome.Completed) outcome).result()).isEqualTo(ElicitAction.ACCEPT);
  }

  @Test
  void fingerprint_mismatch_at_answered_ordinal_throws_ledger_mismatch() {
    var round1 =
        (ReplayOutcome.InputRequired)
            executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    var answer = new ElicitResult(ElicitAction.ACCEPT, JsonNodeFactory.instance.objectNode());
    List<ResponseLedgerEntry> answered =
        List.of(round1.entries().getFirst().answeredWith(answer));
    assertThatThrownBy(
            () -> executor.execute(answered, () -> executor.elicit(question("DIFFERENT?"))))
        .isInstanceOf(ElicitationLedgerMismatchException.class);
  }

  @Test
  void elicit_outside_execute_throws_illegal_state() {
    assertThatThrownBy(() -> executor.elicit(question("hi")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MRTR dispatch");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl mocapi-server test -Dtest=ReplayExecutorTest`
Expected: COMPILE ERROR — `ReplayExecutor` / `ReplayOutcome` do not exist.

- [ ] **Step 3: Implement `ReplayOutcome` and `ReplayExecutor`; gut `MrtrElicitationEngine` down to the wire-token carrier**

`ReplayOutcome.java`:

```java
public sealed interface ReplayOutcome {
  record Completed(Object result) implements ReplayOutcome {}

  record InputRequired(String key, ElicitRequestFormParams params, List<ResponseLedgerEntry> entries)
      implements ReplayOutcome {
    public InputRequired {
      entries = List.copyOf(entries);
    }
  }
}
```

`ReplayExecutor.java` — move `EXECUTION`, `KEY_PREFIX`, `ReplayExecution`, `fingerprintOf` verbatim from `MrtrElicitationEngine`; new `execute`:

```java
public ReplayOutcome execute(List<ResponseLedgerEntry> ledger, Supplier<Object> invocation) {
  ReplayExecution execution = new ReplayExecution(ledger);
  try {
    Object result = ScopedValue.where(EXECUTION, execution).call(invocation::get);
    return new ReplayOutcome.Completed(result);
  } catch (InputRequiredException signal) {
    return new ReplayOutcome.InputRequired(signal.key(), signal.params(), execution.entries());
  }
}
```

`MrtrElicitationEngine` after the extraction: constructor builds `this.replayExecutor = new ReplayExecutor(objectMapper);`; `elicit(...)` delegates to `replayExecutor.elicit(params)`; `execute(...)` keeps all its token/validation logic (`originalParamsOf`, `ledgerFor`, `decode`, `verifySamePrincipal`, `verifySameTarget`, `answer`) and its middle becomes (final form, replacing the try/catch around the ScopedValue call):

```java
public Object execute(String method, Object requestParams,
    Map<String, InputResponse> inputResponses, String requestState, Supplier<Object> invocation) {
  ObjectNode originalParams = originalParamsOf(requestParams);
  List<ResponseLedgerEntry> ledger = ledgerFor(method, originalParams, inputResponses, requestState);
  try {
    ReplayOutcome outcome = replayExecutor.execute(ledger, invocation);
    if (outcome instanceof ReplayOutcome.InputRequired ir) {
      String token = codec.encode(method, originalParams, ir.entries(), principalSource.currentPrincipal());
      return new InputRequiredResult(
          Map.of(ir.key(), new ElicitRequest(ir.params())), token, ResultTypes.INPUT_REQUIRED);
    }
    return ((ReplayOutcome.Completed) outcome).result();
  } catch (ElicitationLedgerMismatchException e) {
    throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, e.getMessage());
  }
}
```

Add `public ReplayExecutor replayExecutor() { return replayExecutor; }`.

- [ ] **Step 4: Run the new test AND the full mocapi-server suite (the refactor gate)**

Run: `mvn -q -pl mocapi-server test`
Expected: PASS — including `MrtrElicitationEngineTest`, `MrtrElicitationComplianceTest`, `ToolsCallInteractiveComplianceTest`, `McpToolsServiceTest`, `McpPromptsServiceTest`, `McpResourcesServiceTest`, all unmodified.

- [ ] **Step 5: Commit**

```bash
git add mocapi-server
git commit -m "refactor(server): extract ReplayExecutor core from MrtrElicitationEngine"
```

---

### Task 2: Progress sink seam + context progress override (behavior-preserving)

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/progress/ProgressSink.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/progress/ProgressChannel.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/progress/DefaultMcpProgressSource.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/context/AbstractMrtrContext.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/DefaultMcpToolContext.java`
- Test: `mocapi-server/src/test/java/com/callibrity/mocapi/server/progress/ProgressSinkTest.java`

**Interfaces:**
- Produces:
  - `@FunctionalInterface public interface ProgressSink { void accept(Number progress, Number total, String message); }` — called **after** the monotonic guard passes; implementations decide delivery.
  - `DefaultMcpProgressSource` gains `public DefaultMcpProgressSource(ProgressSink sink)`; the existing `(McpTransport, ValueNode)` constructor now builds the transport-notification sink internally and delegates.
  - `AbstractMrtrContext` gains a protected constructor `(McpProgressSource progress, ElicitationDispatcher elicitationDispatcher, McpExchange exchange, String handlerName)`; the existing 5-arg constructor delegates via `new DefaultMcpProgressSource(transport, progressToken)`.
  - `DefaultMcpToolContext` gains matching public constructor `(McpProgressSource progress, ElicitationDispatcher elicitationDispatcher, McpExchange exchange, String handlerName)`.
- Behavior invariants: monotonic-increase validation stays in `ProgressChannel` (throws `IllegalArgumentException` regardless of sink); the transport sink still no-ops when `progressToken == null || transport == null` (that check moves into the transport-sink lambda inside `DefaultMcpProgressSource`); wire shape of `notifications/progress` unchanged.

- [ ] **Step 1: Write the failing test**

```java
// mocapi-server/src/test/java/com/callibrity/mocapi/server/progress/ProgressSinkTest.java
class ProgressSinkTest {

  @Test
  void custom_sink_receives_progress_total_and_message() {
    List<String> seen = new ArrayList<>();
    var source =
        new DefaultMcpProgressSource((progress, total, message) ->
            seen.add(progress + "/" + total + ":" + message));
    var emitter = source.longProgress(10L);
    emitter.emit(3, "chunk a");
    emitter.emit(7);
    assertThat(seen).containsExactly("3/10:chunk a", "7/10:null");
  }

  @Test
  void monotonic_guard_still_applies_with_custom_sink() {
    var source = new DefaultMcpProgressSource((progress, total, message) -> {});
    var emitter = source.longProgress(null);
    emitter.emit(5);
    assertThatThrownBy(() -> emitter.emit(5)).isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl mocapi-server test -Dtest=ProgressSinkTest`
Expected: COMPILE ERROR — no `ProgressSink`, no sink constructor.

- [ ] **Step 3: Implement**

`ProgressChannel` changes: replace fields `McpTransport transport; ValueNode progressToken;` with `ProgressSink sink;` — constructor `ProgressChannel(ProgressSink sink, Number total)`. `emit(...)` keeps the monotonic guard, then calls `sink.accept(progress, total, message)` unconditionally.

`DefaultMcpProgressSource`:

```java
public DefaultMcpProgressSource(ProgressSink sink) { this.sink = Objects.requireNonNull(sink); }

public DefaultMcpProgressSource(McpTransport transport, ValueNode progressToken) {
  this(transportSink(transport, progressToken));
}

private static ProgressSink transportSink(McpTransport transport, ValueNode progressToken) {
  return (progress, total, message) -> {
    if (transport == null || progressToken == null) {
      return; // token gates only the network send; validation already ran (ADR-0025 rule 4)
    }
    // ... existing ObjectNode building + transport.send(new JsonRpcNotification(...)) moved here verbatim
  };
}
```

Factory methods pass `new ProgressChannel(sink, total)`. `AbstractMrtrContext`/`DefaultMcpToolContext` gain the delegating constructors described in **Interfaces**.

- [ ] **Step 4: Run the new test AND the full mocapi-server suite**

Run: `mvn -q -pl mocapi-server test`
Expected: PASS, including `DefaultMcpProgressSourceTest` and `NonToolProgressComplianceTest` unmodified.

- [ ] **Step 5: Commit**

```bash
git add mocapi-server
git commit -m "refactor(server): ProgressSink seam behind the progress emitters"
```

---

### Task 3: `ToolCallReplayInvoker` — public detached invocation on `McpToolsService`

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolCallReplayInvoker.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/McpToolsService.java`
- Test: `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/ToolCallReplayInvokerTest.java`

**Interfaces:**
- Produces:

```java
public interface ToolCallReplayInvoker {
  sealed interface Outcome {
    record Completed(CallToolResult result) implements Outcome {}
    record InputRequired(String key, ElicitRequest request, List<ResponseLedgerEntry> ledger)
        implements Outcome {}
  }

  Outcome invoke(
      String toolName,
      JsonNode arguments,
      List<ResponseLedgerEntry> ledger,
      McpProgressSource progressOverride,
      McpExchange exchange);
}
```

- `McpToolsService implements ToolCallReplayInvoker`. Unknown tool name → the same `JsonRpcException` `lookup(name)` already throws. `ElicitationLedgerMismatchException` propagates to the caller (the task engine maps it to `failed`; the wire path already translates it).
- Internal refactor: extract from `invokeTool` a private `CallToolResult invokeWithContext(String name, CallToolHandler handler, JsonNode args, DefaultMcpToolContext ctx)` holding the ScopedValue binding + `handler.call` + `resultMapper().map` + the entire existing catch cascade. `invokeTool` becomes: build ctx from `McpTransport.CURRENT`/`McpExchange.CURRENT`/progressToken exactly as today, then `invokeWithContext(...)`. `invoke(...)` (detached) becomes: `lookup`, build ctx via the Task-2 constructor (`new DefaultMcpToolContext(progressOverride, elicitationEngine, exchange, toolName)` — null transport implicit, no progress token), then `elicitationEngine.replayExecutor().execute(ledger, () -> invokeWithContext(...))`, mapping `ReplayOutcome` → `Outcome` (`ir.params()` wrapped as `new ElicitRequest(ir.params())`; `Completed.result()` cast to `CallToolResult` — safe because `invokeWithContext` always returns one).

- [ ] **Step 1: Write the failing test**

```java
// mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/ToolCallReplayInvokerTest.java
class ToolCallReplayInvokerTest {

  static class EchoTools {
    @McpTool(description = "interactive echo")
    public String confirmAndEcho(McpToolContext ctx) {
      ElicitResult answer = ctx.elicit("Proceed?", schema -> schema.bool("ok", "OK?"));
      return answer.isAccepted() ? "confirmed" : "declined";
    }
  }

  private McpToolsService service; // built in @BeforeEach exactly like McpToolsServiceTest builds one
                                   // (CallToolHandlers.build over EchoTools + MrtrElicitationEngine
                                   //  with RequestStateCodec.withEphemeralKey)

  private McpExchange formCapableExchange() {
    return new McpExchange("2026-07-28", null,
        new ClientCapabilities(null, null, null,
            new ElicitationCapability(JsonNodeFactory.instance.objectNode(), null), null));
  }

  @Test
  void detached_invoke_with_empty_ledger_yields_input_required() {
    var outcome = service.invoke("confirm_and_echo", JsonNodeFactory.instance.objectNode(),
        List.of(), new DefaultMcpProgressSource((p, t, m) -> {}), formCapableExchange());
    assertThat(outcome).isInstanceOf(ToolCallReplayInvoker.Outcome.InputRequired.class);
    var ir = (ToolCallReplayInvoker.Outcome.InputRequired) outcome;
    assertThat(ir.key()).isEqualTo("elicit-1");
    assertThat(ir.ledger()).hasSize(1);
  }

  @Test
  void detached_invoke_with_answered_ledger_completes() {
    var first = (ToolCallReplayInvoker.Outcome.InputRequired)
        service.invoke("confirm_and_echo", JsonNodeFactory.instance.objectNode(),
            List.of(), new DefaultMcpProgressSource((p, t, m) -> {}), formCapableExchange());
    var content = JsonNodeFactory.instance.objectNode(); content.put("ok", true);
    var answered = List.of(first.ledger().getFirst()
        .answeredWith(new ElicitResult(ElicitAction.ACCEPT, content)));
    var outcome = service.invoke("confirm_and_echo", JsonNodeFactory.instance.objectNode(),
        answered, new DefaultMcpProgressSource((p, t, m) -> {}), formCapableExchange());
    assertThat(outcome).isInstanceOf(ToolCallReplayInvoker.Outcome.Completed.class);
    var result = ((ToolCallReplayInvoker.Outcome.Completed) outcome).result();
    assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("confirmed");
  }

  @Test
  void tool_exception_maps_to_isError_result_not_a_throw() {
    // register a tool whose body throws IllegalStateException("boom") — reuse ThrowingTool
    // from mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/util/ThrowingTool.java
    var outcome = service.invoke("throwing_tool", JsonNodeFactory.instance.objectNode(),
        List.of(), new DefaultMcpProgressSource((p, t, m) -> {}), formCapableExchange());
    var completed = (ToolCallReplayInvoker.Outcome.Completed) outcome;
    assertThat(completed.result().isError()).isTrue();
  }
}
```

(Adjust tool registration names to what `Names.identifier` derives — check `McpToolsServiceTest` for the established fixture pattern and reuse it.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl mocapi-server test -Dtest=ToolCallReplayInvokerTest`
Expected: COMPILE ERROR — no `ToolCallReplayInvoker`, no `invoke` on the service.

- [ ] **Step 3: Implement** (as specified in **Interfaces** above)

- [ ] **Step 4: Run the new test AND the full mocapi-server suite**

Run: `mvn -q -pl mocapi-server test`
Expected: PASS; `McpToolsServiceTest`, `McpToolExceptionHandlingTest`, compliance tests unmodified.

- [ ] **Step 5: Commit**

```bash
git add mocapi-server
git commit -m "refactor(server): ToolCallReplayInvoker detached-invocation seam on McpToolsService"
```

---

### Task 4: `ToolCallDispatchCustomizer` hook in `callTool`

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolCallDispatchCustomizer.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/McpToolsService.java` (new constructor arm + hook in `callTool`)
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerToolsAutoConfiguration.java` (inject the list)
- Test: `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/ToolCallDispatchCustomizerTest.java`

**Interfaces:**
- Produces:

```java
@FunctionalInterface
public interface ToolCallDispatchCustomizer {
  /**
   * Returns the full tools/call response for this request, or {@link Optional#empty()} to fall
   * through. Consulted in bean order after handler lookup, before the default MRTR path.
   */
  Optional<Object> dispatch(CallToolHandler handler, CallToolRequestParams params);
}
```

- `McpToolsService` gains constructor parameter `List<ToolCallDispatchCustomizer> dispatchCustomizers` (new widest constructor; existing constructors delegate with `List.of()`). In `callTool`, after `lookup(name)`:

```java
for (ToolCallDispatchCustomizer customizer : dispatchCustomizers) {
  Optional<Object> claimed = customizer.dispatch(handler, params);
  if (claimed.isPresent()) {
    return claimed.get();
  }
}
```

- `MocapiServerToolsAutoConfiguration.mcpProtocolToolsService(...)` gains `@Autowired(required = false) List<ToolCallDispatchCustomizer> dispatchCustomizers`, null-coalesced to `List.of()`, passed through the new constructor.

- [ ] **Step 1: Write the failing test** — a customizer that claims one tool and asserts (a) claimed call returns the customizer's object and the handler is never invoked, (b) unclaimed calls hit the default path, (c) `Optional.empty()` from all customizers is identical to no customizers. Use the `HelloTool` fixture (`mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/util/HelloTool.java`) and build the service like `McpToolsServiceTest` does, once with and once without the customizer list.

```java
@Test
void claiming_customizer_short_circuits_and_skips_handler() {
  AtomicBoolean invoked = new AtomicBoolean();
  ToolCallDispatchCustomizer claim =
      (handler, params) -> params.name().equals("hello_tool")
          ? Optional.of("CLAIMED") : Optional.empty();
  McpToolsService svc = serviceWith(List.of(claim)); // helper building via new ctor
  Object response = svc.callTool(new CallToolRequestParams("hello_tool", null, null, null, null));
  assertThat(response).isEqualTo("CLAIMED");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl mocapi-server test -Dtest=ToolCallDispatchCustomizerTest`
Expected: COMPILE ERROR.

- [ ] **Step 3: Implement** (interface + constructor + hook + autoconfigure injection)

- [ ] **Step 4: Run the mocapi-server suite AND the autoconfigure suite**

Run: `mvn -q -pl mocapi-server,mocapi-autoconfigure test`
Expected: PASS unchanged.

- [ ] **Step 5: Commit**

```bash
git add mocapi-server mocapi-autoconfigure
git commit -m "refactor(server): ToolCallDispatchCustomizer hook ahead of the tools/call default path"
```

---

### Task 5: Routing-header contribution seam (`Mcp-Name` for extension methods)

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/routing/McpRoutedParamContributor.java`
- Modify: `mocapi-streamable-http-transport/src/main/java/com/callibrity/mocapi/transport/http/McpHeaderValidator.java`
- Modify: the `StreamableHttpAutoConfiguration` `mcpProtocolHeaderValidator()` bean (same module) to inject contributors
- Test: `mocapi-streamable-http-transport/src/test/java/com/callibrity/mocapi/transport/http/McpHeaderValidatorTest.java` (add cases; existing cases unmodified)

**Interfaces:**
- Produces:

```java
// mocapi-server (shared, transport-agnostic contract; stdio ignores it)
@FunctionalInterface
public interface McpRoutedParamContributor {
  /** method → params field name that the Mcp-Name header must mirror. */
  Map<String, String> namedParamFields();
}
```

- `McpHeaderValidator` gains `public McpHeaderValidator(Map<String, String> additionalNamedParamFields)`; the no-arg constructor delegates with `Map.of()`. The instance field is the merged, immutable union of the built-in `NAMED_PARAM_FIELDS` and the additions (built-ins win on key collision).
- Autoconfigure bean: `mcpProtocolHeaderValidator(@Autowired(required = false) List<McpRoutedParamContributor> contributors)` merges all contributed maps into one and passes it.

- [ ] **Step 1: Write the failing test** — add to `McpHeaderValidatorTest`:

```java
@Test
void contributed_method_requires_and_validates_mcp_name() {
  var validator = new McpHeaderValidator(Map.of("tasks/get", "taskId"));
  // missing Mcp-Name on tasks/get → failure mentioning the method
  // Mcp-Name matching params.taskId → Optional.empty()
  // Mcp-Name mismatching params.taskId → failure
  // (construct HttpHeaders + JsonRpcRequest exactly as the existing tests in this class do)
}

@Test
void built_in_methods_unaffected_by_contributions() {
  var validator = new McpHeaderValidator(Map.of("tasks/get", "taskId"));
  // tools/call behavior identical to the no-arg validator
}
```

- [ ] **Step 2: Run test to verify it fails** — `mvn -q -pl mocapi-streamable-http-transport test -Dtest=McpHeaderValidatorTest` → COMPILE ERROR (no map constructor).

- [ ] **Step 3: Implement** (constructor + merge + contributor interface + bean injection)

- [ ] **Step 4: Run both modules' suites** — `mvn -q -pl mocapi-server,mocapi-streamable-http-transport test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-server mocapi-streamable-http-transport
git commit -m "refactor(server,http): contributable Mcp-Name routing-header validation table"
```

---

### Task 6: `mocapi-tasks` module skeleton + extension wire types + `@McpTask`

**Files:**
- Create: `mocapi-tasks/pom.xml`
- Modify: `pom.xml` (root — add `<module>mocapi-tasks</module>` after `mocapi-apps`)
- Modify: `mocapi-bom/pom.xml` (add `mocapi-tasks` entry after `mocapi-apps`)
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/McpTask.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TasksExtension.java`
- Create in `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/model/`: `TaskStatus.java`, `CreateTaskResult.java`, `GetTaskResult.java`, `GetTaskParams.java`, `UpdateTaskParams.java`, `UpdateTaskResult.java`, `CancelTaskParams.java`, `CancelTaskResult.java`
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/model/TaskWireShapesTest.java`, `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/McpTaskAnnotationTest.java`

**Interfaces (produces — exact shapes later tasks depend on):**

```java
// pom: parent mocapi-parent; deps: mocapi-server, mocapi-api (compile, ${project.version});
// tools.jackson.core:jackson-databind; io.micrometer:context-propagation;
// org.springframework:spring-core (for AnnotatedElementUtils — already transitive via
// mocapi-server, declare it explicitly); spring-boot-autoconfigure <optional>true</optional>;
// spring-boot-starter-test (test). Plus maven-jar-plugin test-jar execution (see Step 3).

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface McpTask {
  String ttl() default "";           // ISO-8601 Duration; "" -> mocapi.tasks.default-ttl (PT1H)
  String pollInterval() default "";  // ISO-8601 Duration; "" -> mocapi.tasks.default-poll-interval (PT2S)
  boolean required() default false;  // true -> non-capable clients get -32021
}

public final class TasksExtension {
  public static final String EXTENSION_ID = "io.modelcontextprotocol/tasks";
  public static final String TASKS_GET = "tasks/get";
  public static final String TASKS_UPDATE = "tasks/update";
  public static final String TASKS_CANCEL = "tasks/cancel";
  public static final String RESULT_TYPE_TASK = "task";
  private TasksExtension() {}
}

public enum TaskStatus {
  @JsonProperty("working") WORKING,
  @JsonProperty("input_required") INPUT_REQUIRED,
  @JsonProperty("completed") COMPLETED,
  @JsonProperty("failed") FAILED,
  @JsonProperty("cancelled") CANCELLED;
  public boolean isTerminal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateTaskResult(String taskId, TaskStatus status, String statusMessage,
    String createdAt, String lastUpdatedAt, Long ttlMs, Long pollIntervalMs, String resultType) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetTaskResult(String taskId, TaskStatus status, String statusMessage,
    String createdAt, String lastUpdatedAt, Long ttlMs, Long pollIntervalMs,
    Map<String, InputRequest> inputRequests, CallToolResult result, JsonRpcErrorDetail error,
    String resultType) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetTaskParams(String taskId, @JsonProperty("_meta") RequestMeta meta) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateTaskParams(String taskId, Map<String, InputResponse> inputResponses,
    @JsonProperty("_meta") RequestMeta meta) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateTaskResult(String resultType) {}   // always ResultTypes.COMPLETE

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelTaskParams(String taskId, @JsonProperty("_meta") RequestMeta meta) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelTaskResult(String resultType) {}   // always ResultTypes.COMPLETE
```

(`InputRequest`, `CallToolResult`, `RequestMeta`, `InputResponse` come from `mocapi-model`; `JsonRpcErrorDetail` from ripcurl core; `ResultTypes.COMPLETE` from `mocapi-model`. `createdAt`/`lastUpdatedAt` are ISO-8601 strings on the wire — `Instant.toString()` at the mapping boundary.)

- [ ] **Step 1: Write the failing tests** — `TaskWireShapesTest`: serialize a `CreateTaskResult` and each `GetTaskResult` status variant with `new ObjectMapper()` and assert exact JSON (e.g. `{"taskId":"t1","status":"working","createdAt":"2026-08-02T14:00:00Z","lastUpdatedAt":"2026-08-02T14:00:00Z","ttlMs":3600000,"pollIntervalMs":2000,"resultType":"task"}` — field order per record declaration; assert via JSON tree equality, not string, following `Mcp20260728TypesSerializationTest`'s pattern in mocapi-model). Round-trip `UpdateTaskParams` with an `ElicitResult` inputResponse. `McpTaskAnnotationTest`: assert retention/targets/defaults reflectively (mirror `mocapi-apps` `AnnotationContractTest`).

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test` → module doesn't exist yet; expect Maven failure until Step 3 scaffolds it, then compile errors, then green.

- [ ] **Step 3: Implement** — root pom `<module>` entry, BOM entry, module pom (with the test-jar execution so Task 7's TCK ships):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-jar-plugin</artifactId>
      <executions>
        <execution>
          <goals><goal>test-jar</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

then the types above with license headers.

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS; `mvn -q verify` from root → PASS (reactor picks up the module).

- [ ] **Step 5: Commit**

```bash
git add pom.xml mocapi-bom/pom.xml mocapi-tasks
git commit -m "feat(tasks): mocapi-tasks module skeleton, @McpTask, extension wire types"
```

---

### Task 7: `TaskStore` SPI, `TaskRecord`, `InMemoryTaskStore`, contract TCK

**Files:**
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/TaskStore.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/TaskRecord.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/TaskAlreadyExistsException.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/InMemoryTaskStore.java`
- Create: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/store/TaskStoreContractTest.java` (abstract TCK — ships in the test-jar)
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/store/InMemoryTaskStoreTest.java`

**Interfaces (produces):**

```java
public interface TaskStore {
  /** Durably creates the record; MUST NOT return before a subsequent get(taskId) would find it.
   *  @throws TaskAlreadyExistsException on taskId collision */
  void create(TaskRecord record);

  /** Empty if unknown OR expired (createdAt + ttl before now). */
  Optional<TaskRecord> get(String taskId);

  /** Applies {@code mutation} atomically against the current record; returns the post-mutation
   *  record, or empty if unknown/expired. The mutation function MUST be deterministic and
   *  side-effect-free: implementations MAY invoke it more than once (optimistic retry); only the
   *  final invocation's result is stored. Returning the input unchanged is a no-op. */
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);

  /** Idempotent. */
  void delete(String taskId);
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRecord(
    String taskId, String toolName, JsonNode arguments, String principal,
    String protocolVersion, ClientCapabilities clientCapabilities,
    TaskStatus status, String statusMessage,
    Instant createdAt, Instant lastUpdatedAt, Duration ttl, Duration pollInterval,
    List<ResponseLedgerEntry> ledger, Map<String, InputRequest> inputRequests,
    CallToolResult result, JsonRpcErrorDetail error, long version) {

  public boolean isExpired(Instant now) { return now.isAfter(createdAt.plus(ttl)); }

  // Transition helpers — every one bumps lastUpdatedAt to `now` and version by 1.
  // Transitions FROM a terminal status return `this` unchanged (terminal states are final).
  public TaskRecord working(Instant now)
  public TaskRecord completed(CallToolResult result, Instant now)
  public TaskRecord failed(JsonRpcErrorDetail error, String statusMessage, Instant now)
  public TaskRecord cancelled(Instant now)
  public TaskRecord inputRequired(String key, InputRequest request,
      List<ResponseLedgerEntry> ledger, Instant now)          // replaces inputRequests with Map.of(key, request)
  public TaskRecord withStatusMessage(String message, Instant now)  // no-op when terminal
  public TaskRecord withLedger(List<ResponseLedgerEntry> ledger, Instant now) // no-op when terminal
}
```

`InMemoryTaskStore implements TaskStore, AutoCloseable` — `public InMemoryTaskStore(Clock clock)` and `public InMemoryTaskStore(Clock clock, Duration sweepInterval)`; `ConcurrentHashMap<String, TaskRecord>`; `create` = `putIfAbsent` (throw on non-null prior); `get` = read + lazy-expire (remove & empty if `isExpired`); `update` = `map.compute(taskId, (id, r) -> r == null || r.isExpired(now) ? null : mutation.apply(r))` (compute returning null removes — for the expired case; unknown stays empty); the sweeper is a daemon virtual thread (`Thread.ofVirtual().name("mocapi-tasks-sweeper").start(...)`) looping `sleep(sweepInterval)` + removing expired entries, interrupted by `close()`.

`TaskStoreContractTest` — abstract, JUnit 5, with `protected abstract TaskStore newStore(Clock clock);` covering: create-then-get round-trip; create collision throws `TaskAlreadyExistsException`; get unknown → empty; update unknown → empty; expiry (fixed `Clock` you can advance — use a mutable test clock inner class): get after ttl → empty and record purged; **atomicity**: N=8 threads × M=100 `update` calls each incrementing a counter stored in `statusMessage` (parse-increment-format) → final value N×M; **terminal finality**: `completed(...)` then `cancelled(now)` mutation → status stays `COMPLETED`; version strictly increases across transitions; delete idempotent.

- [ ] **Step 1: Write the failing tests** — `TaskStoreContractTest` (abstract, code as described — every assertion concrete) + `InMemoryTaskStoreTest extends TaskStoreContractTest` overriding `newStore`, plus one in-memory-only test: the sweeper physically removes an expired record without any `get` (create with short ttl, advance clock, poll `awaitility`-style loop with `Thread.sleep(10)` bounded at 2s asserting internal size via a second `get` — or expose `int size()` on `InMemoryTaskStore` for the test; do the latter, it's honest API).

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test -Dtest=InMemoryTaskStoreTest` → COMPILE ERROR.

- [ ] **Step 3: Implement** `TaskStore`, `TaskRecord` (all transition helpers exactly as specified — terminal-guard first, then rebuild with bumped `lastUpdatedAt`/`version`), `TaskAlreadyExistsException extends RuntimeException`, `InMemoryTaskStore`.

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-tasks
git commit -m "feat(tasks): TaskStore SPI, TaskRecord transitions, InMemoryTaskStore + contract TCK"
```

---

### Task 8: `TaskProgressSource` — progress → `statusMessage`

**Files:**
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/engine/TaskProgressSource.java`
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/engine/TaskProgressSourceTest.java`

**Interfaces:**
- Consumes: `ProgressSink` + `DefaultMcpProgressSource(ProgressSink)` from Task 2; `TaskStore.update` + `TaskRecord.withStatusMessage` from Task 7.
- Produces:

```java
public final class TaskProgressSource {
  private TaskProgressSource() {}

  /** McpProgressSource whose emits write the task's statusMessage. Format:
   *  with total: "<progress>/<total>" ; without: "<progress>" ; message appended as ": <message>". */
  public static McpProgressSource forTask(TaskStore store, String taskId, Clock clock) {
    return new DefaultMcpProgressSource((progress, total, message) -> {
      String label = total != null ? progress + "/" + total : String.valueOf(progress);
      String statusMessage = message != null ? label + ": " + message : label;
      store.update(taskId, r -> r.withStatusMessage(statusMessage, clock.instant()));
    });
  }
}
```

- [ ] **Step 1: Write the failing test** — with an `InMemoryTaskStore` + fixed clock: `longProgress(100L).emit(42, "resizing")` → record `statusMessage == "42/100: resizing"` and `lastUpdatedAt` bumped; `countingProgress(null).emit()` twice → `"2"`; emits against a **cancelled** record leave status `CANCELLED` and message untouched (terminal no-op via `withStatusMessage`); non-monotonic emit still throws `IllegalArgumentException`.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test -Dtest=TaskProgressSourceTest` → COMPILE ERROR.

- [ ] **Step 3: Implement** (code above).

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-tasks
git commit -m "feat(tasks): route progress emitters to Task.statusMessage"
```

---

### Task 9: `TaskExecutionEngine`

**Files:**
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/engine/TaskExecutionEngine.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/engine/TaskIds.java`
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/engine/TaskExecutionEngineTest.java`, `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/engine/TaskIdsTest.java`

**Interfaces:**
- Consumes: `ToolCallReplayInvoker` (Task 3), `TaskStore`/`TaskRecord` (Task 7), `TaskProgressSource` (Task 8), `ContextSnapshotFactory` (io.micrometer.context), `McpExchange` (mocapi-server).
- Produces:

```java
public final class TaskIds {
  private TaskIds() {}
  /** 256-bit SecureRandom, Base64URL without padding (43 chars). */
  public static String newTaskId()
}

public class TaskExecutionEngine {
  public TaskExecutionEngine(TaskStore store, ToolCallReplayInvoker invoker,
      ContextSnapshotFactory snapshotFactory, Clock clock)

  /** Durably creates the record, spawns execution #1 on a snapshot-wrapped virtual thread,
   *  returns the CreateTaskResult. */
  public CreateTaskResult createAndStart(TaskRecord record)

  /** Spawns the next execution (post-tasks/update flip). Caller has already flipped
   *  input_required -> working and merged the ledger. */
  public void resume(String taskId)
}
```

Execution body (`run(String taskId)`, executed inside `snapshotFactory.captureAll().wrap(...)` on `Thread.ofVirtual().name("mocapi-task-" + taskId).start(...)`):

```java
private void run(String taskId) {
  TaskRecord record = store.get(taskId).orElse(null);
  if (record == null || record.status() != TaskStatus.WORKING) {
    return; // expired, deleted, or already terminal (e.g. cancel won before we started)
  }
  McpExchange exchange = new McpExchange(
      record.protocolVersion(), null, record.clientCapabilities());
  McpProgressSource progress = TaskProgressSource.forTask(store, taskId, clock);
  try {
    var outcome = invoker.invoke(record.toolName(), record.arguments(),
        record.ledger(), progress, exchange);
    switch (outcome) {
      case ToolCallReplayInvoker.Outcome.Completed c ->
          store.update(taskId, r -> r.completed(c.result(), clock.instant()));
      case ToolCallReplayInvoker.Outcome.InputRequired ir ->
          store.update(taskId,
              r -> r.inputRequired(ir.key(), ir.request(), ir.ledger(), clock.instant()));
    }
  } catch (ElicitationLedgerMismatchException e) {
    // Handler violated the replay idempotency contract mid-task (spec §12): -32602, not -32603.
    store.update(taskId, r -> r.failed(
        new JsonRpcErrorDetail(JsonRpcProtocol.INVALID_PARAMS, e.getMessage(), null),
        "replay ledger mismatch", clock.instant()));
  } catch (Exception e) {
    store.update(taskId, r -> r.failed(
        new JsonRpcErrorDetail(JsonRpcProtocol.INTERNAL_ERROR, e.getMessage(), null),
        "task execution failed: " + e.getClass().getSimpleName(), clock.instant()));
  }
}
```

(All terminal writes go through `TaskRecord` transitions, which no-op when the record already went terminal — "cancelled sticks, output discarded" §7.4 falls out for free.) `createAndStart` maps record → `CreateTaskResult` via a private mapper: `new CreateTaskResult(r.taskId(), r.status(), r.statusMessage(), r.createdAt().toString(), r.lastUpdatedAt().toString(), r.ttl().toMillis(), r.pollInterval().toMillis(), TasksExtension.RESULT_TYPE_TASK)`.

- [ ] **Step 1: Write the failing tests** — use `InMemoryTaskStore` + a **stub invoker** (lambda-friendly since `ToolCallReplayInvoker` is an interface) + `ContextSnapshotFactory.builder().build()` + fixed clock. Await completion with a bounded poll helper (`await(store, taskId, status, 2s)` — plain loop + `Thread.sleep(10)`; no new test deps). Cases:
  - completed: stub returns `Completed(result)` → record `COMPLETED` with the result; `createAndStart`'s return has `resultType == "task"`, `status == WORKING`, `ttlMs`/`pollIntervalMs` correct.
  - input_required: stub returns `InputRequired("elicit-1", request, entries)` → record `INPUT_REQUIRED`, `inputRequests` has exactly `elicit-1`, ledger persisted.
  - invoker throws `RuntimeException("boom")` → record `FAILED`, `error.code() == -32603`.
  - invoker throws `ElicitationLedgerMismatchException` → record `FAILED`, `error.code() == -32602`, `statusMessage == "replay ledger mismatch"`.
  - cancel-wins race: stub invoker **blocks on a latch**; after `createAndStart`, cancel the record directly (`store.update(id, r -> r.cancelled(now))`), release the latch with a `Completed` outcome → final status stays `CANCELLED` (discarded output).
  - `TaskIdsTest`: 43-char Base64URL, no padding, 1000 ids all distinct.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test -Dtest=TaskExecutionEngineTest` → COMPILE ERROR.

- [ ] **Step 3: Implement** (code above; `TaskIds` uses `SecureRandom` + `Base64.getUrlEncoder().withoutPadding()`).

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-tasks
git commit -m "feat(tasks): TaskExecutionEngine — spawn, outcome writes, cancel-sticks discard"
```

---

### Task 10: `McpTasksService` — `tasks/get`, `tasks/update`, `tasks/cancel`

**Files:**
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/McpTasksService.java`
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/McpTasksServiceTest.java`

**Interfaces:**
- Consumes: everything above + `McpPrincipalSource` (mocapi-server mrtr), `@JsonRpcMethod`/`@JsonRpcParams` (ripcurl), `JsonRpcException`.
- Produces:

```java
public class McpTasksService {
  public McpTasksService(TaskStore store, TaskExecutionEngine engine,
      McpPrincipalSource principalSource, Clock clock)

  @JsonRpcMethod(TasksExtension.TASKS_GET)
  public GetTaskResult getTask(@JsonRpcParams GetTaskParams params)

  @JsonRpcMethod(TasksExtension.TASKS_UPDATE)
  public UpdateTaskResult updateTask(@JsonRpcParams UpdateTaskParams params)

  @JsonRpcMethod(TasksExtension.TASKS_CANCEL)
  public CancelTaskResult cancelTask(@JsonRpcParams CancelTaskParams params)
}
```

Shared lookup: `private TaskRecord requireOwned(String taskId)` — `store.get(taskId)` filtered by `Objects.equals(record.principal(), principalSource.currentPrincipal())`; absent/foreign → `throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Unknown task")` (identical message for unknown/expired/foreign — no existence leak, §7.6).

`getTask`: `requireOwned`, map to `GetTaskResult` — always taskId/status/statusMessage/createdAt/lastUpdatedAt/ttlMs/pollIntervalMs + `resultType = ResultTypes.COMPLETE`; `inputRequests` only when `INPUT_REQUIRED`; `result` only when `COMPLETED`; `error` only when `FAILED`.

`updateTask` (§7.3 — resume iff *this* mutation flipped):

```java
public UpdateTaskResult updateTask(UpdateTaskParams params) {
  requireOwned(params.taskId());
  Map<String, InputResponse> responses =
      params.inputResponses() != null ? params.inputResponses() : Map.of();
  var flipped = new AtomicBoolean(); // reset every mutation invocation — deterministic per contract
  store.update(params.taskId(), r -> {
    flipped.set(false);
    if (r.status() != TaskStatus.INPUT_REQUIRED) {
      return r; // ignore per spec SHOULD: keys not outstanding / duplicate update
    }
    List<ResponseLedgerEntry> merged = mergeResponses(r.ledger(), r.inputRequests(), responses);
    if (merged == null) {
      return r; // nothing outstanding was answered — no flip
    }
    flipped.set(true);
    return r.withLedger(merged, clock.instant()).working(clock.instant());
  });
  if (flipped.get()) {
    engine.resume(params.taskId());
  }
  return new UpdateTaskResult(ResultTypes.COMPLETE);
}
```

`mergeResponses(ledger, outstanding, responses)`: for each response key present in `outstanding` (currently size ≤ 1) whose ledger entry is unanswered and whose response is an `ElicitResult` — answer it (`entry.answeredWith(...)`); ignore unknown keys and non-`ElicitResult` responses (spec SHOULD); return the new list, or `null` when nothing was answered.

`cancelTask`: `requireOwned`, then `store.update(taskId, r -> r.cancelled(clock.instant()))` (terminal no-op built into the transition), return `new CancelTaskResult(ResultTypes.COMPLETE)`.

- [ ] **Step 1: Write the failing tests** — `InMemoryTaskStore`, real engine with stub invoker, `McpPrincipalSource` stub returning `"alice"`. Cases (each a `@Test`):
  - get on WORKING/INPUT_REQUIRED/COMPLETED/FAILED/CANCELLED records → correct field population per status, `resultType == "complete"`, absent fields null.
  - get unknown taskId → `JsonRpcException` code `-32602`, message `"Unknown task"`.
  - get as principal `"mallory"` (switch the stub) on alice's task → same `-32602` "Unknown task".
  - update answering the outstanding key: flips to WORKING, engine resumed exactly once (stub invoker counts invocations; second run completes and record ends COMPLETED).
  - duplicate update (same key again after flip): ack, **no second resume** (invocation count unchanged).
  - update with unknown key only: ack, still INPUT_REQUIRED, no resume.
  - update with no inputResponses: ack, no-op.
  - cancel non-terminal → CANCELLED; cancel COMPLETED → stays COMPLETED; both ack.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test -Dtest=McpTasksServiceTest` → COMPILE ERROR.

- [ ] **Step 3: Implement** (code above + the status→result mapper).

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-tasks
git commit -m "feat(tasks): McpTasksService — get/update/cancel with principal binding and single-resume"
```

---

### Task 11: `TaskToolCallDispatcher`, `-32021` translator, capability customizer, header contribution

**Files:**
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TaskToolCallDispatcher.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/McpTaskRequiredException.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TaskRequiredExceptionTranslator.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TasksCapabilityCustomizer.java`
- Create: `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TasksRoutedParamContributor.java`
- Test: `mocapi-tasks/src/test/java/com/callibrity/mocapi/tasks/TaskToolCallDispatcherTest.java`, `.../TaskRequiredExceptionTranslatorTest.java`, `.../TasksCapabilityCustomizerTest.java`

**Interfaces:**
- Consumes: `ToolCallDispatchCustomizer` + `CallToolHandler` (Task 4/existing), `McpRoutedParamContributor` (Task 5), `TaskExecutionEngine`/`TaskRecord`/`TaskIds` (Tasks 7/9), `McpPrincipalSource`, `ServerCapabilitiesCustomizer` (mocapi-server discover), `MissingRequiredClientCapabilityErrorData` (mocapi-model), `AnnotatedElementUtils` (spring-core).
- Produces:

```java
public class TaskToolCallDispatcher implements ToolCallDispatchCustomizer {
  public record Defaults(Duration ttl, Duration pollInterval) {}  // from mocapi.tasks.* properties

  public TaskToolCallDispatcher(TaskExecutionEngine engine, McpPrincipalSource principalSource,
      ObjectMapper objectMapper, Defaults defaults, Clock clock)

  @Override
  public Optional<Object> dispatch(CallToolHandler handler, CallToolRequestParams params) {
    McpTask annotation = AnnotatedElementUtils.findMergedAnnotation(handler.method(), McpTask.class);
    if (annotation == null) {
      return Optional.empty();                       // never a task
    }
    if (!isTaskCapable(params.meta())) {
      if (annotation.required()) {
        throw new McpTaskRequiredException(handler.name());
      }
      return Optional.empty();                       // graceful sync degrade
    }
    TaskRecord record = newRecord(handler, params, annotation);
    return Optional.of(engine.createAndStart(record));
  }

  static boolean isTaskCapable(RequestMeta meta) {
    return meta != null && meta.clientCapabilities() != null
        && meta.clientCapabilities().extensions() != null
        && meta.clientCapabilities().extensions().containsKey(TasksExtension.EXTENSION_ID);
  }
}
```

`newRecord`: taskId = `TaskIds.newTaskId()`; toolName = `handler.name()`; arguments = `params.arguments() != null ? params.arguments() : objectMapper.createObjectNode()`; principal = `principalSource.currentPrincipal()`; protocolVersion/clientCapabilities from `params.meta()`; status `WORKING`; timestamps `clock.instant()`; ttl/pollInterval = annotation value when non-blank (`Duration.parse`) else defaults; empty ledger/inputRequests; version 0.

`McpTaskRequiredException extends RuntimeException` — message `"Tool \"<name>\" requires the io.modelcontextprotocol/tasks client capability"`.

`TaskRequiredExceptionTranslator implements JsonRpcExceptionTranslator<McpTaskRequiredException>` — mirrors `ElicitationNotSupportedExceptionTranslator` verbatim but data is `new MissingRequiredClientCapabilityErrorData(new ClientCapabilities(null, null, null, null, Map.of(TasksExtension.EXTENSION_ID, JsonNodeFactory.instance.objectNode())))`, code `MissingRequiredClientCapabilityErrorData.CODE` (−32021).

`TasksCapabilityCustomizer implements ServerCapabilitiesCustomizer` — `capabilities.extension(TasksExtension.EXTENSION_ID, mapper.createObjectNode())` (mirror `UiCapabilityCustomizer`).

`TasksRoutedParamContributor implements McpRoutedParamContributor` — returns `Map.of(TASKS_GET, "taskId", TASKS_UPDATE, "taskId", TASKS_CANCEL, "taskId")`.

- [ ] **Step 1: Write the failing tests** — dispatcher: build a real `CallToolHandler` for a `@McpTask`-annotated tool method (reuse the `CallToolHandlers.build` fixture pattern from `ToolCallReplayInvokerTest`, Task 3) and a plain one; assert the four decision-rule rows of §6.2: no-annotation → empty; annotation + capable meta (RequestMeta with `ClientCapabilities` whose extensions map contains the id) → `Optional.of(CreateTaskResult)` and store contains a WORKING record with ttl from annotation (`@McpTask(ttl = "PT2M")` → 120000 ms); annotation + non-capable → empty; `required = true` + non-capable → `McpTaskRequiredException`. Translator: `translate(...)` yields code −32021 and `data.requiredCapabilities.extensions` containing the id. Capability customizer: builder → `build().extensions()` contains the id mapped to `{}`.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-tasks test -Dtest='Task*Test,TasksCapabilityCustomizerTest'` → COMPILE ERROR.

- [ ] **Step 3: Implement** (all five classes as specified).

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-tasks test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-tasks
git commit -m "feat(tasks): dispatch decision rule, -32021 translator, capability + routing contributions"
```

---

### Task 12: Autoconfiguration + properties

**Files:**
- Create: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/MocapiTasksAutoConfiguration.java`
- Create: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/MocapiTasksProperties.java`
- Modify: `mocapi-autoconfigure/pom.xml` (add `mocapi-tasks` `<optional>true</optional>` in the optional-feature block, after `mocapi-apps`)
- Modify: `mocapi-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (add `com.callibrity.mocapi.tasks.MocapiTasksAutoConfiguration` after the apps line)
- Test: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/tasks/MocapiTasksAutoConfigurationTest.java`

**Interfaces (produces):**

```java
@ConfigurationProperties(prefix = "mocapi.tasks")
public record MocapiTasksProperties(
    @DefaultValue("PT1H") Duration defaultTtl,
    @DefaultValue("PT2S") Duration defaultPollInterval,
    @DefaultValue("PT1M") Duration sweepInterval) {}

@AutoConfiguration(after = MocapiServerToolsAutoConfiguration.class)
@ConditionalOnClass(TaskExecutionEngine.class)
@EnableConfigurationProperties(MocapiTasksProperties.class)
public class MocapiTasksAutoConfiguration {
  @Bean @ConditionalOnMissingBean(Clock.class)  Clock mcpTasksClock() { return Clock.systemUTC(); }
  @Bean @ConditionalOnMissingBean(TaskStore.class)
  InMemoryTaskStore mcpTaskStore(Clock, MocapiTasksProperties)
  // MUST log at WARN when this default is chosen (mirror the mocapi.mrtr.secret ephemeral-key
  // warning in MocapiServerAutoConfiguration.mcpRequestStateCodec). Exact message:
  // "Using the in-memory TaskStore: task state is process-local — NOT multi-node safe, and
  //  in-flight tasks are lost on restart. Provide a shared TaskStore bean for clustered or
  //  durable deployments."
  @Bean @ConditionalOnMissingBean(ContextSnapshotFactory.class)
  ContextSnapshotFactory mcpTasksContextSnapshotFactory()   // builder().build()
  @Bean @ConditionalOnMissingBean(TaskExecutionEngine.class)
  TaskExecutionEngine mcpTaskExecutionEngine(TaskStore, McpToolsService, ContextSnapshotFactory, Clock)
  @Bean @ConditionalOnMissingBean(McpTasksService.class)
  McpTasksService mcpTasksService(TaskStore, TaskExecutionEngine, McpPrincipalSource, Clock)
  @Bean TaskToolCallDispatcher mcpTaskToolCallDispatcher(TaskExecutionEngine, McpPrincipalSource,
      ObjectMapper, MocapiTasksProperties, Clock)   // Defaults from properties
  @Bean TaskRequiredExceptionTranslator mcpTaskRequiredTranslator(ObjectMapper)
  @Bean TasksCapabilityCustomizer mcpTasksCapabilityCustomizer(ObjectMapper)
  @Bean TasksRoutedParamContributor mcpTasksRoutedParamContributor()
}
```

(`McpToolsService` is injected as the `ToolCallReplayInvoker` — it implements it. Note the engine bean depends on `McpToolsService`, hence `after = MocapiServerToolsAutoConfiguration.class`.)

- [ ] **Step 1: Write the failing test** — `ApplicationContextRunner` (mirror `MocapiAppsAutoConfigurationTest`): with `AutoConfigurations.of(MocapiServerAutoConfiguration.class, MocapiServerToolsAutoConfiguration.class, MocapiTasksAutoConfiguration.class)` + an `Infra` config providing `ObjectMapper` → context has single beans of `TaskStore` (type `InMemoryTaskStore`), `TaskExecutionEngine`, `McpTasksService`, `TaskToolCallDispatcher`, `TasksCapabilityCustomizer`, `TasksRoutedParamContributor`, `TaskRequiredExceptionTranslator`; the `ServerCapabilities` bean's `extensions()` contains `io.modelcontextprotocol/tasks`; property override `mocapi.tasks.default-ttl=PT5M` reaches the dispatcher (`Defaults.ttl()`); a user-supplied `TaskStore` bean backs off the in-memory default. Add `@ExtendWith(OutputCaptureExtension.class)`-style assertions (JUnit `CapturedOutput`, already available via spring-boot-starter-test): the default-store context logs the "NOT multi-node safe" WARN; the user-supplied-store context does **not**.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-autoconfigure test -Dtest=MocapiTasksAutoConfigurationTest` → COMPILE ERROR.

- [ ] **Step 3: Implement** (autoconfig + properties + pom + imports line).

- [ ] **Step 4: Run** — `mvn -q -pl mocapi-autoconfigure test` → PASS (whole module suite).

- [ ] **Step 5: Commit**

```bash
git add mocapi-autoconfigure
git commit -m "feat(tasks): auto-configuration, mocapi.tasks.* properties, optional wiring"
```

---

### Task 13: End-to-end integration tests (dispatcher-level conversations)

**Files:**
- Test: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/tasks/TasksEndToEndTest.java`

**Interfaces:** consumes everything; produces no new API. Pattern: `GuardIntegrationTest` — `@SpringBootTest(classes = TasksEndToEndTest.TestApp.class, webEnvironment = WebEnvironment.NONE)`, nested `@SpringBootConfiguration @EnableAutoConfiguration static class TestApp` registering a test `@Component` with three tools: `@McpTool @McpTask slowEcho(...)` (elicit-free), `@McpTool @McpTask confirmTwice(McpToolContext ctx)` (two sequential elicits), `@McpTool @McpTask(required = true) mustTask(...)`. Autowire `JsonRpcDispatcher` + `ObjectMapper`; build params with `_meta` carrying `io.modelcontextprotocol/protocolVersion` and `clientCapabilities` (with/without the tasks extension entry); a poll helper loops `tasks/get` dispatches until the target status or 2 s timeout.

- [ ] **Step 1: Write the failing tests**
  - **create → poll → complete:** task-capable `tools/call` on `slow_echo` → result JSON has `resultType == "task"` + 43-char `taskId`; poll → eventually `completed` with the tool's `CallToolResult` under `result`.
  - **two-elicit, three executions:** call `confirm_twice` → poll to `input_required` with key `elicit-1`; `tasks/update` answering it → poll to `input_required` with key `elicit-2`; update again → poll to `completed`. Assert the handler ran three times (counter in the test component) — documenting the replay contract.
  - **cancel mid-input_required:** cancel → `tasks/get` shows `cancelled`; a late `tasks/update` acks but status stays `cancelled` and no resume runs.
  - **sync degrade:** non-capable `tools/call` on `slow_echo` → plain `CallToolResult` (`resultType == "complete"`), no task created.
  - **required:** non-capable call on `must_task` → JSON-RPC error `-32021` with `data.requiredCapabilities.extensions["io.modelcontextprotocol/tasks"]`.
  - **cross-principal:** with a test `McpPrincipalSource` bean switchable between "alice"/"mallory": alice creates; mallory's `tasks/get` → `-32602` "Unknown task".
  - **unknown task:** random taskId → `-32602`.

- [ ] **Step 2: Run to verify failure** — `mvn -q -pl mocapi-autoconfigure test -Dtest=TasksEndToEndTest` → failures (missing test component wiring) then legitimate assertion failures until green.

- [ ] **Step 3: Fix anything the integration surfaces** (this task expects no main-code changes; if one is needed, make it minimally and note it in the commit body).

- [ ] **Step 4: Run the full reactor** — `mvn -q verify` → PASS.

- [ ] **Step 5: Commit**

```bash
git add mocapi-autoconfigure
git commit -m "feat(tasks): end-to-end conversation tests — poll, two-elicit replay, cancel, degrade, -32021"
```

---

### Task 14: Governance docs (ADR-0037/0038, constitution, ADR-0022 flip, design docs, guides)

**Files:**
- Create: `docs/adr/0037-mcp-tasks-extension.md`, `docs/adr/0038-server-seams-for-extensions.md` (use `docs/adr/_template.md`; status Accepted; date = implementation date)
- Modify: `docs/adr/README.md` (index both), `docs/adr/0022-2026-07-28-features-not-implemented.md` (flip Tasks entry to "Accepted — implemented in ADR-0037", mirroring the Apps flip wording; update the Extensions entry; note `notifications/tasks` remains declined), `docs/constitution.md` (I1 gains the scoped exception + ADR-0037 link)
- Create: `docs/design/tasks.md`
- Modify: `docs/design/elicitation-mrtr.md` (ReplayExecutor as shared core; task store as second ledger carrier), `docs/design/handlers.md` (`@McpTask`), `docs/design/transports.md` (routed-param contribution), `docs/design/extension-spi.md` (ToolCallDispatchCustomizer + ToolCallReplayInvoker rows)
- Create: `docs/guides/tasks.md` (annotation usage, idempotency contract restated from the interactive-tools guide, deployment topology §10, custom-store how-to + TCK usage via the test-jar)
- Modify: `docs/superpowers/specs/2026-07-31-mcp-apps-extension-design.md`'s dangling link → point at `2026-08-02-mcp-tasks-extension-design.md` (the file is named `2026-07-31-mcp-apps-extension-design.md` in `docs/superpowers/specs/`)

**Content requirements (summarize from the spec, do not invent):** ADR-0037 records decisions-locked §13 items 1–3, 5–12 + rejected alternatives (07-31 blocking-TaskContext design, Substrate-in-mocapi, in-tree JDBC/JPA, event sourcing, client version echo) + the −32021-vs−32003 verification outcome from Task 15. ADR-0038 records §13 item 4 + the three seams with **Code anchors** lines pointing at `ReplayExecutor.java`, `ToolCallDispatchCustomizer.java`, `ToolCallReplayInvoker.java`, `McpRoutedParamContributor.java`, `ProgressSink.java`.

- [ ] **Step 1: Write all documents** (each ADR ≤ 150 lines, design doc structured like `docs/design/apps.md`)
- [ ] **Step 2: Verify cross-links** — every relative link in changed files resolves (`grep -o '\](\.\..*\.md' docs/adr/0037* docs/design/tasks.md ...` and spot-check).
- [ ] **Step 3: Run** `mvn -q verify` (docs don't affect it — this is the pre-commit gate habit).
- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: ADR-0037/0038, constitution I1 amendment, ADR-0022 flip, tasks design doc + guide"
```

---

### Task 15: Conformance wiring

**Files:**
- Modify: `mocapi-conformance/src/main/java/com/callibrity/mocapi/conformance/` — add `TasksConformanceTools.java` (a `@McpTask` tool + a `@McpTask(required = true)` tool, named per the suite's scenario expectations)
- Modify: `mocapi-conformance/pom.xml` (add `mocapi-tasks` dependency)
- Modify: `mocapi-conformance/conformance-expected-failures.yaml` (waive scenarios covered by declared non-goals: `notifications/tasks`, non-tools/call augmentation — exact scenario ids discovered from the run)
- Modify: `mocapi-conformance/README.md` (note the tasks extension coverage)

- [ ] **Step 1: Check the suite for tasks scenarios** — `npx @modelcontextprotocol/conformance@0.2.0-alpha.10 server --list 2>/dev/null | grep -i task` (or run the full suite and inspect). If the suite has **no** tasks scenarios yet, record that in the README + ADR-0037 and skip Steps 2–3.
- [ ] **Step 2: Add the conformance tools; start the app** (`mvn -q -pl mocapi-conformance spring-boot:run`) **and run the suite** with `--suite all --spec-version 2026-07-28 --expected-failures mocapi-conformance/conformance-expected-failures.yaml`.
- [ ] **Step 3: Reconcile** — every failure maps to a declared non-goal (waive with a comment) or is a bug (fix it). **Specifically verify the missing-capability error code the suite expects (−32021 vs the extension site's stale −32003) and record the outcome in ADR-0037** (spec §3 flag).
- [ ] **Step 4: Run** `mvn -q verify` → PASS.
- [ ] **Step 5: Commit**

```bash
git add mocapi-conformance docs/adr/0037-mcp-tasks-extension.md
git commit -m "feat(conformance): tasks-extension scenarios + expected-failures reconciliation"
```
