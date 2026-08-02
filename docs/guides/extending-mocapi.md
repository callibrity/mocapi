# Extending mocapi

The one-stop reference for writing a mocapi extension — a module like
`mocapi-apps` or `mocapi-tasks` that plugs into `mocapi-server` without
core knowing anything about it. If you're writing an ordinary MCP
server (tools, prompts, resources, guards on your own handlers), start
with the [Customizers guide](customizers.md) instead; this page is for
authors adding a new *seam consumer* — a Spring bean that attaches to
one of mocapi's extension points from another module.

For the design decisions behind everything on this page, see
[ADR-0039](../adr/0039-extension-seam-taxonomy-and-dispatch-interception.md)
(the taxonomy and the current shape of these seams) and
[ADR-0038](../adr/0038-server-seams-for-extensions.md) (the seams
ADR-0039 built on: `ReplayExecutor`, `ProgressSink`, routed-param
contribution).

## The taxonomy

Every mocapi extension point is named for one of five contracts. The
suffix tells you the lifecycle and the merge rule before you've read a
line of the interface:

| Word | Lifecycle | Merge rule | Members |
|---|---|---|---|
| `*Contributor` | Startup — called once, return value collected | Framework unions every contribution; **a collision fails the boot**, naming both parties | `ResourceContributor`, `RoutedParamContributor` |
| `*Customizer` | Startup — called once per handler/object being built; **never per-request** | Mutates or folds into a framework-owned object; multiple customizers apply in bean order | `CallToolHandlerCustomizer` + 3 siblings, `ServerCapabilitiesCustomizer`, the oauth2 filter-chain customizers |
| `*Interceptor` | Per-request; ordered by `@Order`/`Ordered`, ascending (lower runs outermost) | Each may call `proceed()`, replace the result, or throw; declining is side-effect-free | `McpDispatchInterceptor`, the six-strata `MethodInterceptor`s |
| `*Store` / `*Source` / `*Strategy` | Deployment-supplied strategy bean | `@ConditionalOnMissingBean` default; a deployment supplies exactly one replacement | `TaskStore`, `McpPrincipalSource`, `McpTokenStrategy` |
| `*Sink` | Runtime, one-way delivery callback, supplied at construction | Not mergeable — one sink per channel instance | `ProgressSink` |
| *(no seam suffix)* | An API you **call**, not an SPI you implement | N/A — this isn't an extension point | `ToolCallReplayInvoker`, `McpProgressSource`, `McpElicitor` |

`Mcp`-prefixed names are reserved for `mocapi-api` (author-facing) types
and genuinely cross-cutting server concepts (`McpPrincipalSource`, kept
prefixed as a grandfathered exception). An internal, `mocapi-server`-only
seam like `RoutedParamContributor` carries no prefix, matching its
sibling `ResourceContributor`.

## The eight seams, one worked example each

### 1. `ResourceContributor` — contribute resources/templates at startup

```java
public interface ResourceContributor {
  List<ReadResourceHandler> resources();
  List<ReadResourceTemplateHandler> resourceTemplates();

  static ResourceContributor of(
      List<ReadResourceHandler> resources, List<ReadResourceTemplateHandler> resourceTemplates) { … }
}
```

`McpResourcesService` merges every `ResourceContributor` bean at
construction into its immutable resource/template maps — there is no
runtime registration. `mocapi-apps`'s serve-mode
(`@McpUi(resource=…)`) uses `ResourceContributor.of(...)` to contribute
one classpath-served `ui://` resource per distinct URI, without a
hand-written `@McpResource` method. A duplicate URI across contributors
fails the boot.

### 2. `RoutedParamContributor` — extend `Mcp-Name` validation

```java
@FunctionalInterface
public interface RoutedParamContributor {
  Map<String, String> namedParamFields();
}
```

`mocapi-tasks`'s `TasksRoutedParamContributor` returns
`{"tasks/get": "taskId", "tasks/update": "taskId", "tasks/cancel": "taskId"}`
so the Streamable HTTP transport's `Mcp-Name` header validation
(`-32020 HeaderMismatch`) covers task methods without the transport
module depending on `mocapi-tasks`. A method key colliding with another
contributor's, or with a transport built-in, fails the boot naming both
parties.

### 3. `CallToolHandlerCustomizer` (+ 3 siblings) — mutate a handler's descriptor or strata

```java
public class AppsToolUiMetaCustomizer implements CallToolHandlerCustomizer {
  @Override
  public void customize(CallToolHandlerConfig config) {
    Method method = config.method();
    McpUi ui = method.getAnnotation(McpUi.class);
    if (ui == null) return;
    McpUiToolMeta uiMeta = new McpUiToolMeta(ui.value(), List.of(ui.visibility()));
    Tool descriptor = config.descriptor();
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    config.descriptor(descriptor.withMeta(meta));
  }
}
```

(`mocapi-apps`'s `AppsToolUiMetaCustomizer`, real code.) Every
`*HandlerConfig` (`CallToolHandlerConfig`, `GetPromptHandlerConfig`,
`ReadResourceHandlerConfig`, `ReadResourceTemplateHandlerConfig`) exposes
both `T descriptor()` and `void descriptor(T)` — the mutator folded a
former pair of standalone descriptor-only SPIs into the existing
per-handler-kind customizer ([ADR-0039](../adr/0039-extension-seam-taxonomy-and-dispatch-interception.md),
amending [ADR-0034](../adr/0034-descriptor-meta-and-customizer-seams.md)).
**Identity contract:** a replacement descriptor must keep the
original's identity field (`name()`/`uri()`/`uriTemplate()`) and, for
tools, both compiled schemas — other customizers in the same chain close
over the descriptor snapshotted at build time, and replacing identity or
schemas here desynchronizes what's advertised from what's enforced. Use
`descriptor(T)` to add or change `title`/`description`/`_meta` only.

The same `*HandlerConfig` also exposes one interceptor mutator per
stratum (`correlationInterceptor`, `observationInterceptor`,
`auditInterceptor`, `validationInterceptor`, `invocationInterceptor`),
`guard(Guard)`, and `resolver(ParameterResolver)` — see the
[Customizers guide](customizers.md) for the full six-stratum picture.

### 4. `ServerCapabilitiesCustomizer` — declare an extension capability

```java
public class UiCapabilityCustomizer implements ServerCapabilitiesCustomizer {
  @Override
  public void customize(ServerCapabilities.Builder capabilities) {
    ObjectNode config = mapper.createObjectNode();
    config.putArray("mimeTypes").add("text/html;profile=mcp-app");
    capabilities.extension("io.modelcontextprotocol/ui", config);
  }
}
```

(`mocapi-apps`'s `UiCapabilityCustomizer`, real code.) Applied when
mocapi builds its own default `ServerCapabilities` bean
(`@ConditionalOnMissingBean`). If a deployment supplies its own
`ServerCapabilities` bean, mocapi's factory backs off entirely and every
registered customizer is silently skipped — `ServerCapabilitiesOverrideAuditor`
(a `SmartInitializingSingleton`, registered unconditionally so its
detection is immune to auto-configuration ordering) logs one WARN naming
the discarded customizer beans when this happens, so the override
doesn't fail silently.

### 5. `McpDispatchInterceptor<H, P>` — intercept a dispatch before the handler chain runs

```java
@FunctionalInterface
public interface McpDispatchInterceptor<H, P> {
  Object intercept(H handler, P params, Supplier<Object> proceed);
}
```

`mocapi-tasks`'s `TaskToolCallDispatcher` — the worked example, and the
full contract, are below in their own section.

### 6. `TaskStore` — a deployment-supplied persistence strategy

```java
public interface TaskStore {
  void create(TaskRecord record);
  Optional<TaskRecord> get(String taskId);
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);
  void delete(String taskId);
}
```

`InMemoryTaskStore` is the `@ConditionalOnMissingBean` default (with a
startup WARN about its non-durability); a production, multi-node
deployment supplies its own bean. See the atomicity contract in
[Tasks](tasks.md#writing-a-custom-taskstore) and the `TaskStoreContractTest` TCK
before writing one.

### 7. `ProgressSink` — a one-way delivery callback

```java
@FunctionalInterface
public interface ProgressSink {
  void accept(Number progress, Number total, String message);
}
```

`mocapi-tasks`'s `TaskProgressSource.forTask` builds an
`McpProgressSource` whose sink writes a formatted `statusMessage`
through `TaskStore.update` instead of sending a wire
`notifications/progress` message — `ProgressChannel`'s monotonic-increase
validation runs identically either way; only the delivery differs.

### 8. `McpPrincipalSource` / `McpTokenStrategy` — strategy beans

```java
@FunctionalInterface
public interface McpPrincipalSource {
  String currentPrincipal(); // null if unauthenticated
}
```

The `@ConditionalOnMissingBean` default returns `null` (mocapi core is
authentication-agnostic); `mocapi-oauth2` supplies one reading the
Spring Security context. `McpTokenStrategy` is the analogous seam for
JWT-vs-opaque-token validation in the oauth2 filter chain. Both are
one-bean-per-deployment strategies, not multi-instance contributions.

## The interceptor contract: proceed / own / abort

`McpDispatchInterceptor<H, P>` is the seam for per-request behavior on
the three MRTR-capable methods (`tools/call`, `prompts/get`,
`resources/read`). `DispatchChains` sorts registered interceptors once
per service at construction (`@Order`, ascending — lower values run
outermost) and folds them per-dispatch around the existing default
path. With zero interceptors, dispatch is byte-for-byte what it was
before this seam existed.

An interceptor's `intercept(handler, params, proceed)` has exactly
three outcomes:

- **Proceed.** Call `proceed.get()` (optionally decorating the result)
  and return it. The request continues into the handler chain
  unchanged.
- **Own.** Return a different `Object` without calling `proceed()`.
  The interceptor's return value becomes the response as-is.
- **Abort.** Throw. The exception becomes the JSON-RPC error.

**Interceptors run *before* the handler chain — and therefore before
guards and schema validation.** An interceptor that owns the call
bypasses the six-stratum chain entirely, which means it inherits
responsibility for anything that chain would otherwise have provided.
`TaskToolCallDispatcher` (`mocapi-tasks`) is the worked example of
getting this right:

```java
@Override
public Object intercept(
    CallToolHandler handler, CallToolRequestParams params, Supplier<Object> proceed) {
  McpTask annotation = AnnotatedElementUtils.findMergedAnnotation(handler.method(), McpTask.class);
  if (annotation == null) {
    return proceed.get(); // never a task
  }
  if (!isTaskCapable(params.meta())) {
    if (annotation.required()) {
      throw new McpTaskRequiredException("Tool \"" + handler.name() + "\"");
    }
    return proceed.get(); // graceful sync degrade
  }
  // Guards normally run inside the handler chain (GuardEvaluationInterceptor), but the task
  // path never enters that chain — it short-circuits to createAndStart. Evaluate the same
  // guard list here, before minting the task record, so an unauthorized capable client gets
  // the identical synchronous -32010 the sync path would produce, instead of a taskId whose
  // record later lands FAILED.
  if (Guards.evaluate(handler.guards()) instanceof GuardDecision.Deny deny) {
    throw new JsonRpcException(JsonRpcErrorCodes.FORBIDDEN, "Forbidden: " + deny.reason());
  }
  TaskRecord rec = newRecord(handler, params, annotation);
  return engine.createAndStart(rec);
}
```

Three cases fall through to `proceed()` (no annotation; non-capable and
not required — call runs normally through the chain, guards included).
The fourth case — capable client, `@McpTask` present — **owns** the
call: before it does, it manually re-runs `Guards.evaluate` against the
handler's own guard list, throwing the identical `-32010 Forbidden`
`JsonRpcException` the chain's `GuardEvaluationInterceptor` would have
thrown. Without this, a denied capable client would get back a `taskId`
whose task record later fails asynchronously, instead of the
synchronous rejection every other guarded call gives. Input-schema
validation is deliberately *not* duplicated the same way — it still
runs once per execution inside the handler chain (both at task creation
and at every resume), which is correct because arguments don't change
between executions.

**The rule for any interceptor that owns a call:** enumerate what the
handler chain would have done before the point you're short-circuiting
at (guards, in mocapi's case — schema validation and the later strata
still run once execution actually happens), and either re-run the
security-relevant parts yourself or prove the call can't reach anything
they protect.

Template-matched `resources/read` (a URI resolving to a
`ReadResourceTemplateHandler` rather than a fixed resource) does not
currently route through `McpDispatchInterceptor` — this is a deliberate,
documented gap (ADR-0039), not an oversight; open a new ADR before
assuming it's already covered if you need it.

## Client-capability detection: `hasExtension`

```java
public boolean hasExtension(String id) {
  return extensions != null && extensions.containsKey(id);
}
```

`ClientCapabilities.hasExtension(String)` (`mocapi-model`, null-safe) is
the one place to check whether a request declared a given extension —
mere presence of the key counts, even as an empty object (`{}`).
`TaskToolCallDispatcher.isTaskCapable` and `McpTasksService`'s gating
both delegate to it rather than re-implementing the null/containsKey
check:

```java
static boolean isTaskCapable(RequestMeta meta) {
  return meta != null
      && meta.clientCapabilities() != null
      && meta.clientCapabilities().hasExtension(TasksExtension.EXTENSION_ID);
}
```

Write your own extension's capability gate the same way instead of
inlining the null checks.

## Error-code guidance for extensions

mocapi partitions JSON-RPC's implementation-defined server-error range
per [I9](../constitution.md#i9--error-code-allocation):
`-32000`..`-32019` is **mocapi-core-private** (currently just `-32010
Forbidden`); `-32020`..`-32099` is the spec-reserved band, where the
2026-07-28 core spec and its extension SEPs allocate codes (`-32020
HeaderMismatch`, `-32021 MissingRequiredClientCapabilityError`, `-32022
UnsupportedProtocolVersionError`).

**Extensions never allocate a new private code in `-32000`..`-32019`
— that range is reserved for mocapi core itself.** If your extension's
SEP defines its own error code (the way the Tasks extension's draft
text specifies one, even though mocapi ultimately follows the core
registry's `MissingRequiredClientCapabilityErrorData.CODE` for that
particular case — see
[tasks.md](../design/tasks.md#the-decision-rule)), use the spec-defined code
verbatim. If your extension has no SEP-defined code for a given
failure, prefer an existing spec-generic code (`-32602 Invalid params`,
`-32603 Internal error`) over minting a new one. This mirrors ADR-0023's
rationale for why mocapi's own `-32010` sits where it does — a shared,
documented sub-range keeps every module's error codes distinguishable
from the wire's spec-defined ones at a glance.

## API vs. SPI: what you call vs. what you implement

Not every public type in `mocapi-server` is an extension point. A
handful are **APIs you call** — `ToolCallReplayInvoker`,
`McpProgressSource`, `McpElicitor` — with a single production
implementation you're not expected to replace. They carry no seam
suffix precisely so they don't read as one; the taxonomy table above
excludes them from its "members" column for the same reason. If you
find yourself writing a class that `implements ToolCallReplayInvoker`
to intercept tool calls, you want `McpDispatchInterceptor` instead —
`ToolCallReplayInvoker` is the detached re-invocation seam
`TaskExecutionEngine` calls, not a customization point.

When in doubt: if the type name ends in `Contributor`, `Customizer`,
`Interceptor`, `Store`/`Source`/`Strategy`, or `Sink`, it's an SPI —
read its section above. If it doesn't, check whether it's documented as
an API before writing an implementation of it.
