# Bump Methodical to 0.6.0 + adopt the interceptor chain

## What to build

With handlers already collapsed into concrete classes (specs 170–173),
migrate the invoker construction to Methodical 0.6's new API and wire
up bean-level interceptor autowiring so cross-cutting concerns
(metrics, tracing, MDC, validation, rate limits) can ship as plain
`MethodInterceptor` beans from downstream starters.

No behavior change beyond the mechanics of the Methodical API move
and the new "ambient interceptor" plumbing. Downstream specs add
actual interceptors.

### Premise

Methodical 0.6 delivers:

- `MethodInvokerFactory` — stateless; all config via
  `Consumer<MethodInvokerConfig<A>>` per `create(...)` call.
- `MethodInvokerConfig<A>` — `resolver(...)` + `interceptor(...)`.
- `MethodInterceptor<A>` — `Object intercept(MethodInvocation<? extends A>)`.
- `MethodInvocation<A>` — `method()`, `target()`, `argument()`,
  `resolvedParameters()`, `proceed()`.
- First-added-is-outermost ordering.

Ripcurl 2.7.0 (or a locally-installed 2.7.0-SNAPSHOT in the interim)
adjusts `DefaultAnnotationJsonRpcMethodProviderFactory`'s constructor
to match the new Methodical API.

---

## File-level changes

### 1. Version bumps

`pom.xml`:

```xml
<methodical.version>0.6.0</methodical.version>
<ripcurl.version>2.7.0</ripcurl.version>
```

(If 2.7.0 isn't on Central yet at implementation time, use
`2.7.0-SNAPSHOT` with a local `mvn install`-ed build and flip to the
release in a follow-up commit.)

### 2. Input schema validation becomes a per-handler interceptor

Extract the `validateInput(name, args, tool)` logic from
`McpToolsService` into a dedicated interceptor. Each `CallToolHandler`
is built with its own input-schema-validating interceptor closed
over that tool's compiled input schema, so there's no runtime
lookup or shared state.

**Input only** — we do *not* validate the handler's return value
against the output schema. The output schema is descriptive metadata
clients read from `tools/list`; the server trusts itself to produce
conformant output, so no server-side enforcement there. Only input
validation runs per call.

New file
`mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/InputSchemaValidatingInterceptor.java`:

```java
final class InputSchemaValidatingInterceptor implements MethodInterceptor<JsonNode> {
  private final Schema inputSchema;

  InputSchemaValidatingInterceptor(Schema inputSchema) {
    this.inputSchema = inputSchema;
  }

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    var args = invocation.argument();
    var failure = Validator.forSchema(inputSchema).validate(
        new JsonParser(args.toString()).parse());
    if (failure != null) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, failure.getMessage());
    }
    return invocation.proceed();
  }
}
```

In `CallToolHandlers.discover(...)`, for each discovered tool:

1. Compile the input schema from the descriptor's `inputSchema()`
   JSON (this is what `McpToolsService` does today in
   `getInputSchema`).
2. Build `new InputSchemaValidatingInterceptor(compiledSchema)`.
3. Include it in the interceptor list passed to the invoker
   customizer — **innermost** (added last), so it runs closest to the
   reflective call and after any ambient observability interceptors.

Delete `McpToolsService.validateInput(...)` and `getInputSchema(...)`
plus the `inputSchemas` cache field. `McpToolsService.callTool(...)`
stops calling `validateInput` — the interceptor handles it.

### 3. Update the four `*Handlers#discover(...)` helpers

`CallToolHandlers`, `GetPromptHandlers`, `ReadResourceHandlers`,
`ReadResourceTemplateHandlers` — all call `factory.create(...)` with
a `Consumer<MethodInvokerConfig<A>>` lambda. Each helper now takes an
additional `List<MethodInterceptor<? super A>>` parameter and threads
both resolvers and interceptors through the customizer:

```java
MethodInvoker<JsonNode> invoker = invokerFactory.create(
    method,
    bean,
    JsonNode.class,
    cfg -> {
      resolvers.forEach(cfg::resolver);
      interceptors.forEach(cfg::interceptor);
    });
```

Same shape for each kind, with the kind's argument type substituted.

### 4. Autoconfig bean wiring

Each of the four handler autoconfigs autowires an optional
`List<MethodInterceptor<? super T>>` and passes it into the
`discover(...)` call:

```java
@Bean
List<CallToolHandler> callToolHandlers(
    ApplicationContext context,
    MethodSchemaGenerator generator,
    MethodInvokerFactory invokerFactory,
    ObjectMapper objectMapper,
    @Autowired(required = false) List<MethodInterceptor<? super JsonNode>> toolInterceptors,
    StringValueResolver mcpAnnotationValueResolver) {
  var resolvers = List.of(
      new McpToolContextResolver(),
      new McpToolParamsResolver(objectMapper),
      new Jackson3ParameterResolver(objectMapper));
  return CallToolHandlers.discover(
      context,
      generator,
      invokerFactory,
      resolvers,
      toolInterceptors == null ? List.of() : toolInterceptors,
      mcpAnnotationValueResolver);
}
```

Four near-identical bean methods — one per kind, with type parameters
adjusted. The null-safe wrap on the `@Autowired(required = false)`
list is per Spring convention.

Note the addition of `Jackson3ParameterResolver` to the tool resolver
list: Methodical 0.6's factory no longer carries ambient resolvers,
so the Jackson fallback that used to be factory-held now rides with
the per-create resolver list.

### 5. Ripcurl factory call sites

`mocapi-server/src/test/java/com/callibrity/mocapi/server/compliance/ComplianceTestSupport.java`:
`new DefaultAnnotationJsonRpcMethodProviderFactory(MAPPER, invokerFactory)`
becomes
`new DefaultAnnotationJsonRpcMethodProviderFactory(MAPPER, invokerFactory, resolvers)`
— ripcurl 2.7.0 requires the resolver list at construction time. The
`new DefaultMethodInvokerFactory(List.of(...))` call in the same
method becomes `new DefaultMethodInvokerFactory()` (the new 0.6
factory is stateless).

### 6. Test constructor updates

Every test that constructs a `DefaultMethodInvokerFactory` with a
list of resolvers (`new DefaultMethodInvokerFactory(List.of(...))`)
becomes `new DefaultMethodInvokerFactory()` and moves the resolvers
into whatever per-invoker list the test already passes to its
handler or helper under test. Files to touch:

- `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/McpToolsServiceTest.java`
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/CallToolHandlerTest.java`
  (renamed from `AnnotationMcpToolTest` in spec 170)
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/resources/ReadResourceHandlerTest.java`
  (renamed from `AnnotationResourceTest` in spec 172)
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/prompts/GetPromptHandlerTest.java`
  (renamed from `AnnotationMcpPromptTest` in spec 171)
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/compliance/ComplianceTestSupport.java`
- Any remaining autoconfig-level tests that directly instantiate the
  factory.

### 7. Resolver ordering

When the Jackson fallback resolver joins the per-invoker list, it
has to come *after* any kind-specific resolver that also accepts
`JsonNode` (e.g. `McpToolContextResolver`, `McpToolParamsResolver`)
— Methodical's resolver chain is first-match-wins, and Jackson3 is
greedy. Order in `CallToolHandlers`:

```
[McpToolContextResolver, McpToolParamsResolver, Jackson3ParameterResolver]
```

### 8. Jakarta validation

`JakartaValidationIntegrationTest` currently asserts that Methodical
0.5's `MethodValidatorFactory` and `JakartaMethodValidatorFactory`
beans exist. In 0.6 those types are gone; the validation story is a
`JakartaValidationInterceptor` bean provided by
`methodical-autoconfigure`. Update the wiring test to assert the
interceptor bean is present:

```java
assertThat(context.getBeansOfType(JakartaValidationInterceptor.class)).isNotEmpty();
```

The interceptor is picked up by our new
`List<MethodInterceptor<? super T>>` autowiring for each kind, so the
actual validation behavior (Jakarta constraint violations surface as
`ConstraintViolationException` → translated to JSON-RPC `-32602` or
`CallToolResult.isError=true`) continues to work end-to-end.

---

## Acceptance criteria

- [ ] Methodical bumped to `0.6.0`; ripcurl to `2.7.0` (or
      `2.7.0-SNAPSHOT` with a locally-installed build).
- [ ] `InputSchemaValidatingInterceptor` added and attached as the
      innermost interceptor per `CallToolHandler` in
      `CallToolHandlers.discover(...)`. `McpToolsService.validateInput`,
      `getInputSchema`, and the `inputSchemas` cache field are
      removed.
- [ ] No output-schema validation is introduced — handler return
      values are not validated against the declared output schema.
- [ ] All four `*Handlers#discover(...)` helpers use the new
      `Consumer<MethodInvokerConfig<A>>` customizer API.
- [ ] All four helpers accept and thread a
      `List<MethodInterceptor<? super A>>` parameter.
- [ ] All four handler-autoconfig bean methods autowire
      `@Autowired(required = false) List<MethodInterceptor<? super A>>`
      (per kind) and pass it down.
- [ ] `Jackson3ParameterResolver` is included in the tool resolvers
      list (moved from the old factory-level wiring).
- [ ] `ComplianceTestSupport.buildDispatcher` updated for ripcurl
      2.7's new `DefaultAnnotationJsonRpcMethodProviderFactory`
      constructor.
- [ ] All test factory constructions updated.
- [ ] `JakartaValidationIntegrationTest` wiring assertion updated
      to `JakartaValidationInterceptor`.
- [ ] `mvn verify` + `mvn spotless:check` green on the whole
      reactor.
- [ ] No behavior change: every existing test that previously passed
      still passes. Jakarta integration tests still produce the
      correct `-32602` / `isError=true` responses for constraint
      violations.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` `### Changed`: entry for
      Methodical 0.6 / ripcurl 2.7 bump. Note the new
      `MethodInterceptor<? super T>` autowiring extension point:
      downstream starters can ship interceptors without any mocapi
      API addition.
- [ ] `docs/handlers.md` gains a section on the interceptor chain —
      how to write a `MethodInterceptor` bean, where it fits in the
      chain, and an example (a timing interceptor or similar
      trivial one).
- [ ] `docs/observability-roadmap.md` updated to remove the
      "interceptor premise" hedge — the premise is now reality.

## Commit

Suggested commit message:

```
Adopt Methodical 0.6 interceptor chain for handler invocation

Bumps Methodical to 0.6.0 (stateless MethodInvokerFactory +
Consumer<MethodInvokerConfig> customizer API) and ripcurl to 2.7.0
(matching constructor changes). Every CallToolHandlers /
GetPromptHandlers / ReadResourceHandlers / ReadResourceTemplateHandlers
`discover(...)` helper now threads a per-kind
List<MethodInterceptor<? super T>> through the new customizer.

The four handler autoconfigs autowire an optional List of
MethodInterceptor beans of the appropriate typed bound and pass it
into the discover call. That's the extension point downstream
starters (mocapi-logging, mocapi-metrics, mocapi-tracing, future
entitlements) hook into: ship a MethodInterceptor bean, pop the
starter on the classpath, it auto-wires without touching any mocapi
API.

Jackson3ParameterResolver moves from Methodical's old stateful
factory into the per-invoker tool resolver list (first-match-wins
ordering: context resolvers before the greedy Jackson fallback).
Jakarta validation wiring assertion updated for Methodical 0.6's
JakartaValidationInterceptor bean.

No behavior change. Jakarta constraint violations still surface as
-32602 / isError=true. All existing tests still pass.
```

## Implementation notes

- Order matters: specs 170–173 should be fully merged before this
  spec starts. If Ralph is working them in order, 174 just needs to
  be last.
- The interceptor autowiring is the foundation for every observability
  spec in `docs/observability-roadmap.md`. Those specs can be written
  and implemented independently once 174 is in — each is a new
  starter module that contributes a single `MethodInterceptor` bean.
- Don't sneak additional interceptors in via this spec (no MDC, no
  metrics, no tracing). Keep it purely a migration. Concrete
  interceptor modules are their own specs.
