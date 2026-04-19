# Handler customizer SPI (per-handler interceptor attachment)

## What to build

Introduce a per-handler extension point so starters (observability,
entitlements, rate-limiting, etc.) can attach a `MethodInterceptor` to
an individual handler while reading that handler's descriptor, target
method, and target bean.

The existing bean-level path — autowired
`List<MethodInterceptor<? super JsonNode>>` (and the prompt / resource /
resource-template analogs) consumed inside each `*Handlers` builder —
stays in place for this spec. It's coarse (applies to every handler of
that kind) and carries a ripcurl-2.7.0 overlap hazard that we'll deal
with in a follow-up. The new customizer tier sits alongside it.

### Why this is needed

1. **Per-handler scoping.** Observability wants handler-kind and
   handler-name tags baked into the interceptor at construction time
   (so the interceptor's hot path doesn't re-introspect the method on
   every invocation). That only works if the extension point hands the
   starter the descriptor/method/bean *before* the `MethodInvoker` is
   built.
2. **No ripcurl collision.** Customizers are a mocapi-internal contract
   — not Spring beans of type `MethodInterceptor<? super JsonNode>` —
   so ripcurl 2.7.0's `DefaultAnnotationJsonRpcMethodProviderFactory`
   (which auto-picks up `MethodInterceptor<? super JsonNode>` beans at
   the JSON-RPC dispatch layer) doesn't see them. A customizer-attached
   interceptor runs once, on the handler only.
3. **Future work (entitlements, guards) wants the same shape** — read
   annotation metadata off the method, decide whether to wire a guard,
   attach it. Same SPI.

### Shape

One Config interface + one Customizer interface per handler kind.
Config is read-only on descriptor/method/bean (set at construction
time, exposed via getters) and mutable for adding interceptors.

```java
// mocapi-api — com.callibrity.mocapi.api.tools
public interface CallToolHandlerConfig {
    Tool descriptor();
    Method method();
    Object bean();
    CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);
}

@FunctionalInterface
public interface CallToolHandlerCustomizer {
    void customize(CallToolHandlerConfig config);
}
```

And analogs:

| Kind | Config interface | Customizer interface | Descriptor type | Interceptor bound |
|---|---|---|---|---|
| tool | `CallToolHandlerConfig` | `CallToolHandlerCustomizer` | `Tool` | `MethodInterceptor<? super JsonNode>` |
| prompt | `GetPromptHandlerConfig` | `GetPromptHandlerCustomizer` | `Prompt` | `MethodInterceptor<? super Map<String, String>>` |
| resource | `ReadResourceHandlerConfig` | `ReadResourceHandlerCustomizer` | `Resource` | `MethodInterceptor<? super Object>` |
| resource_template | `ReadResourceTemplateHandlerConfig` | `ReadResourceTemplateHandlerCustomizer` | `ResourceTemplate` | `MethodInterceptor<? super Map<String, String>>` |

All eight interfaces live in `mocapi-api` under the existing
`com.callibrity.mocapi.api.tools`, `.prompts`, and `.resources`
packages (next to the `@McpTool` / `@McpPrompt` / `@McpResource` /
`@McpResourceTemplate` annotations).

### Builder integration

Each `*Handlers.build(...)` helper grows a new parameter: the list of
customizers for that kind. The build order inside each helper becomes:

1. Validate + compute descriptor (unchanged).
2. Construct a `*HandlerConfig` instance holding the descriptor, method,
   bean, and a mutable list initialized with the currently-supplied
   bean-level interceptors (the existing `List<MethodInterceptor<...>>`
   parameter). See "Why start the list with the bean-level interceptors"
   below.
3. Invoke each customizer with that config — customizers may call
   `config.interceptor(...)` zero or more times to append interceptors.
4. Freeze the config's interceptor list and pass it to
   `MethodInvoker` construction (plus any kind-specific trailing
   interceptors, e.g. `InputSchemaValidatingInterceptor` for tools —
   these still go last, innermost).
5. Construct the handler.

Interceptor ordering: bean-level first, then customizer-added in
iteration order, then kind-specific trailing (schema validator for
tools). Customizers cannot reorder or remove earlier entries — if they
want that control, they own both sides (user owns the customizer and
controls whether a bean-level interceptor exists).

### Why start the list with the bean-level interceptors

The existing autowired `List<MethodInterceptor<? super JsonNode>>`
path already works and the MDC interceptor (spec 178) uses it. Keeping
it in the same list means:

- Customizers see the full pre-built chain when they read config (if
  we later add a `config.interceptors()` getter).
- Nothing changes for existing users of the bean-level path.
- A follow-up spec can deprecate the bean-level path and migrate MDC
  to a customizer once the new tier has bedded in.

### Autoconfiguration wiring

`MocapiServerToolsAutoConfiguration`,
`MocapiServerPromptsAutoConfiguration`, and
`MocapiServerResourcesAutoConfiguration` each gain an extra
`@Autowired(required = false) List<*HandlerCustomizer>` parameter and
pass it through to the corresponding `*Handlers.build(...)` call.
`null` / empty list is fine — the existing no-customizer case is the
default.

## Acceptance criteria

- [ ] Eight new interfaces exist in `mocapi-api`:
  `CallToolHandlerConfig`, `CallToolHandlerCustomizer`,
  `GetPromptHandlerConfig`, `GetPromptHandlerCustomizer`,
  `ReadResourceHandlerConfig`, `ReadResourceHandlerCustomizer`,
  `ReadResourceTemplateHandlerConfig`,
  `ReadResourceTemplateHandlerCustomizer`.
- [ ] Each Config exposes read-only `descriptor()`, `method()`,
      `bean()` getters plus an `interceptor(...)` mutator that returns
      the config for chaining.
- [ ] Each `*Handlers.build(...)` helper accepts a
      `List<*HandlerCustomizer>` parameter and invokes each one with
      the handler's config before the `MethodInvoker` is built.
- [ ] Customizer-added interceptors appear in the final chain in
      iteration order, *after* any bean-level interceptors and
      *before* any kind-specific trailing interceptors (e.g. tool
      schema validator).
- [ ] Autoconfigurations autowire `List<*HandlerCustomizer>` as an
      optional collection and pass it through. Zero-customizer case
      is the default and matches existing behavior exactly.
- [ ] Unit tests per kind: register a stub customizer, assert it
      received a config with the right descriptor/method/bean, and
      assert the interceptor it attached ran during a simulated
      invocation.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.
- [ ] No change in public behavior for users who don't define any
      customizers — existing integration tests pass untouched.

## Non-goals

- **Deprecating / removing the bean-level interceptor list.** Handled
  in a follow-up spec once the o11y starter is the first real customer
  of the customizer tier.
- **Migrating `McpMdcInterceptor` to a customizer.** Same follow-up.
- **Guards / entitlements.** The customizer config is the right
  attachment point for them eventually, but a separate spec designs
  the guard contract.
- **Ordering hints between customizers** (`@Order`, etc.). Spring's
  standard bean-ordering applies to the autowired list; no new
  ordering API here.

## Implementation notes

- `mocapi-api` currently has no dependency on Methodical; the
  customizer interfaces will add a transitive dependency on
  `methodical-api` (the `MethodInterceptor` interface). Confirm
  `methodical-api` is already a transitive of what `mocapi-api`
  exposes and add it to `mocapi-api/pom.xml` as a `compile`-scoped
  dependency if not. (It almost certainly isn't — `mocapi-api` today
  only holds annotation types and context interfaces.)
- Config implementations are package-private in `mocapi-server` next
  to each `*Handlers` helper. They're mutable internally (growing the
  interceptor list) but once `*Handlers.build` returns, nothing holds
  a reference to the config — the mutable list is snapshot into the
  final interceptor chain when the `MethodInvoker` is built.
- Spec 181 (`mocapi-o11y`, Micrometer Observation API) consumes this
  SPI as its first real client. That spec will land immediately after
  this one.
