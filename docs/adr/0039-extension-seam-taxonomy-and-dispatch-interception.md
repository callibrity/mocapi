# ADR-0039 — Extension-seam taxonomy and dispatch interception

- **Status:** Accepted
- **Date:** 2026-08-02

## Context

By 1.1.0 mocapi had accumulated seven-plus extension points across four
ADRs (0011, 0031, 0034, 0038), each named at the moment its forcing case
appeared: `*Customizer`, `*Contributor`, `*Strategy`, a dispatch "hook"
suffixed `Customizer` even though it ran per-request, and `Mcp`-prefixed
names inconsistent with the rest of `mocapi-server`'s internal package.
Two problems fell out of that growth:

1. **No shared vocabulary.** A reader meeting `ToolCallDispatchCustomizer`
   for the first time had no way to tell, from the name alone, that it
   ran on every request (not once at startup like every other
   `*Customizer`), that it could veto or replace the response, or that
   ordering mattered. `McpRoutedParamContributor` carried an `Mcp` prefix
   inconsistent with its sibling `ResourceContributor`, which has none.
2. **Duplicated invocation cores.** `McpToolsService.callTool` (wire
   path) and its detached `ToolCallReplayInvoker.invoke` implementation
   shared handler lookup, `DefaultMcpToolContext` construction, and the
   exception-to-`CallToolResult` cascade only by convention — both lived
   in the same class, but nothing enforced that they couldn't drift.
   `MocapiTasksAutoConfiguration` needed an `ObjectProvider<McpToolsService>`
   workaround to break a bean-graph cycle: `McpToolsService` needed the
   task dispatcher, which needed `TaskExecutionEngine`, which needed
   `McpToolsService` back as a `ToolCallReplayInvoker`.

Two smaller correctness gaps rode along: `TaskToolCallDispatcher` created
task records before any guard ran, so a denied capable client got a
`taskId` whose record later failed instead of the synchronous `-32010`
the sync path gives; and nothing enforced collision-fails-boot uniformly
across the contributor-shaped seams — `RoutedParamContributor` silently
let two contributors (or a contribution and a built-in) claim the same
method, unlike `ResourceContributor`'s existing duplicate-URI failure.

## Decision

**1. A five-word taxonomy, enforced by naming, not just documented.**
Every mocapi extension point is now named for one of five contracts —
readers unfamiliar with a specific interface can infer its lifecycle and
merge rule from the suffix alone:

| Word | Contract | Members after this refactor |
|---|---|---|
| `*Contributor` | Startup; returns values; framework unions them; **collisions fail the boot** | `ResourceContributor`, `RoutedParamContributor` |
| `*Customizer` | Startup; mutates/folds a framework-owned object; **never per-request** | `CallToolHandlerCustomizer` + 3 siblings (now descriptor-capable), `ServerCapabilitiesCustomizer`, oauth2 customizers |
| `*Interceptor` | Per-request; ordered (`@Order`); receives `proceed`; may short-circuit | `McpDispatchInterceptor`, the six-strata `MethodInterceptor`s |
| `*Store` / `*Source` / `*Strategy` | Deployment-supplied strategy bean, `@ConditionalOnMissingBean` default | `TaskStore`, `McpPrincipalSource`, `McpTokenStrategy` |
| `*Sink` | Runtime one-way delivery callback supplied at construction | `ProgressSink` |
| *(no seam suffix)* | API you **call** (documented as such, excluded from the SPI table) | `ToolCallReplayInvoker`, `McpProgressSource`, `McpElicitor` |

Prefix rule: `Mcp` is reserved for `mocapi-api` (author-facing) types and
cross-cutting server concepts; `McpPrincipalSource` keeps its prefix
(grandfathered — pre-dates this taxonomy and isn't worth the churn);
`McpRoutedParamContributor` is renamed **`RoutedParamContributor`**
(package unchanged: `com.callibrity.mocapi.server.routing`) since it's an
ordinary `mocapi-server`-internal contributor, not a cross-cutting `Mcp`
concept.

**2. `McpDispatchInterceptor<H, P>` replaces `ToolCallDispatchCustomizer`
as the per-request dispatch seam**, generalized to all three MRTR-capable
methods:

```java
@FunctionalInterface
public interface McpDispatchInterceptor<H, P> {
  Object intercept(H handler, P params, Supplier<Object> proceed);
}
```

`DispatchChains` sorts registered interceptors once per service at
construction time (`@Order`/`Ordered`, ascending — lower values run
outermost) and folds them per-dispatch around a `terminal` supplier — the
existing default MRTR path. An empty interceptor list degenerates to
calling `terminal` directly, so the zero-interceptor path is byte-for-byte
identical to dispatch before this seam existed. `McpToolsService`,
`McpPromptsService`, and `McpResourcesService` each collect their own
generically-typed interceptor list via Spring's generics-aware injection.

An interceptor runs **before** the handler chain, and therefore before
guards and schema validation. Returning `proceed.get()` continues into
that chain unchanged; returning a different value **owns** the call and
is responsible for everything the chain would otherwise have done
(including authorization — see the task-mode guard-parity decision
below); throwing aborts with a JSON-RPC error. `TaskToolCallDispatcher`
becomes `McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>`:
non-`@McpTask` or non-capable-and-not-required → `proceed.get()`;
`required` and non-capable → throw; capable + `@McpTask` → own the call,
returning a `CreateTaskResult`.

**Template-matched resource reads bypass the interceptor chain
(deliberate).** `McpResourcesService`'s template-match path (a
`resources/read` URI that matches a `ReadResourceTemplateHandler` rather
than a fixed `ReadResourceHandler`) resolves and invokes the template
handler directly; it does not currently route through the
`McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>`
chain, because that chain's generic parameters are typed to the
fixed-resource handler kind and template handlers are a distinct type.
No extension currently needs to intercept template reads, and adding a
second, template-typed interceptor list purely speculatively would
double the resource-service seam surface for zero forcing case. This is
recorded here, rather than silently left as a gap, so a future extension
author who *does* need template-read interception knows to open a new
ADR instead of assuming the existing chain already covers it.

**3. `ToolInvocationCore` unifies the two `tools/call` execution paths.**
It owns handler lookup (via the shared `CallToolHandlerRegistry`),
`DefaultMcpToolContext` construction, the exception-to-`CallToolResult`
cascade, and `ScopedValue` binding for both the on-dispatch-thread wire
path and the detached replay path, and implements
`ToolCallReplayInvoker` directly. `McpToolsService` delegates its
synchronous path to the same core instead of duplicating the mechanics.
The bean graph is now acyclic — `ToolInvocationCore` sits below both
`McpToolsService` and `TaskExecutionEngine` — so
`MocapiTasksAutoConfiguration`'s `ObjectProvider<McpToolsService>`
workaround is deleted.

`ReplayOutcome` becomes generic — `ReplayOutcome<R, Q>` sealed with
`Completed<R, Q>(R result)` and `InputRequired<R, Q>(String key, Q
request, List<ResponseLedgerEntry> ledger)` — so `ReplayExecutor.execute`
(`ReplayOutcome<Object, ElicitRequestFormParams>`) and
`ToolCallReplayInvoker.invoke` (`ReplayOutcome<CallToolResult,
ElicitRequest>`) share one hierarchy instead of two. The former nested
`ToolCallReplayInvoker.Outcome` sealed type is deleted; its
`progressOverride` parameter is renamed `progress`.

`ToolCallReplayInvoker` **keeps its name** but is reclassified in the
taxonomy as an **API you call**, not an SPI you implement — the
production implementation (`ToolInvocationCore`) is not meant to be
swapped out by extension authors the way a `*Customizer` or `*Contributor`
is.

**4. The descriptor-customizer fold amends ADR-0034.** `ToolDescriptorCustomizer`
and `ResourceDescriptorCustomizer` — 1.1.0 types — are **deleted**. All
four `*HandlerConfig`s (`CallToolHandlerConfig`, `GetPromptHandlerConfig`,
`ReadResourceHandlerConfig`, `ReadResourceTemplateHandlerConfig`) gain a
`void descriptor(T)` mutator alongside the existing `T descriptor()`
accessor; the four existing `*HandlerCustomizer` SPIs (ADR-0011) run at
the same point in the build pipeline descriptor customizers used to
(post-generation, pre-chain-assembly) and are now how descriptor
metadata is mutated — one customizer pass per handler, no second SPI.
`mocapi-apps`'s `AppsToolDescriptorCustomizer` / `AppsResourceDescriptorCustomizer`
become `AppsToolUiMetaCustomizer` (`CallToolHandlerCustomizer`) /
`AppsResourceUiMetaCustomizer` (`ReadResourceHandlerCustomizer`) with
identical behavior. Prompts and resource templates gain descriptor
mutators for parity even though no customizer uses them yet.

**Identity contract on `descriptor(T)`.** A customizer that calls
`descriptor(replacement)` must preserve the original's identity field
(`Tool.name()`, `Resource.uri()`, `Prompt.name()`,
`ResourceTemplate.uriTemplate()`) and, for tools, both compiled schemas.
Other customizers in the same chain (guards, audit, observability,
schema validation) commonly close over the descriptor snapshotted at
build time; replacing identity or schemas desynchronizes those closures
from what's actually enforced/registered. The mutator exists to replace
metadata (`title`, `description`, `_meta`) — replacing identity or
schemas is unsupported and done at the customizer's own risk. This
contract is documented on each `*HandlerConfig.descriptor(T)` javadoc,
not enforced at runtime (a runtime check would need to diff two
arbitrary records generically, at startup-only cost for a
should-never-happen author error).

`Tool.withMeta` / `Resource.withMeta` / `Prompt.withMeta` /
`ResourceTemplate.withMeta` `deepCopy()` their `ObjectNode` argument so a
published descriptor's `_meta` can't be mutated out from under it by a
caller retaining a reference to the node they passed in.

**4a. `_meta` extended to `Prompt` and `ResourceTemplate`, reversing
ADR-0034's non-goal.** ADR-0034 declined `_meta` on prompts and resource
templates because no use case needed it yet. That reasoning no longer
holds against this task's I7 (1:1 schema fidelity): `docs/plans/2026-07-28-schema.ts`
declares `_meta?: MetaObject` on both wire interfaces (`ResourceTemplate`
at ~lines 1481/1506, `Prompt` at ~lines 1659/1670), so omitting it from
the mocapi records was a translation gap, not a deliberate scope
decision — the same gap ADR-0034 identified and closed for `Tool`/
`Resource`. This ADR owns closing it for the remaining two: `Prompt` and
`ResourceTemplate` each gain an optional `_meta` (`ObjectNode`,
`NON_NULL`) field and a `withMeta` method with the same deep-copy
protection as `Tool`/`Resource`. The change is purely additive — the
5/6-arg canonical constructors are new, the pre-existing 4/5-arg
constructors are preserved as back-compat overloads, and a descriptor
built with no customizer touching `_meta` serializes identically to
before. Code anchors: `mocapi-model/src/main/java/com/callibrity/mocapi/model/Prompt.java`
and `mocapi-model/src/main/java/com/callibrity/mocapi/model/ResourceTemplate.java`
(both `_meta`, `withMeta`); also listed in the ADR-wide anchors below.

**5. Semver-scope policy.** mocapi is pre-1.0-equivalent for its
*extension* SPI surface specifically: types introduced in 1.1.0
(`ToolDescriptorCustomizer`, `ResourceDescriptorCustomizer`,
`ToolCallDispatchCustomizer`, `McpRoutedParamContributor`) may be renamed
or deleted in the very next minor release without a major bump, provided
(a) a CHANGELOG migration table names the old → new mapping for every
change, and (b) the type had shipped for at most one prior minor release.
A type that has shipped for two or more minor releases is subject to
mocapi's normal 1.x compatibility discipline (additive-only,
deprecate-then-remove). This is a narrow, time-boxed exception — it does
not retroactively apply to 1.0.0 API surface, and it closes automatically
once 1.1.0's types have had one release to prove their names.

**6. Task-mode guard parity.** `TaskToolCallDispatcher.intercept`
evaluates `Guards.evaluate(handler.guards())` before minting a
`TaskRecord`, throwing the same `-32010 Forbidden` `JsonRpcException` the
synchronous path's `GuardEvaluationInterceptor` produces on a `Deny`. An
owning interceptor bypasses the handler chain entirely — including its
AUTHORIZATION stratum — so an interceptor that claims a call and skips
`proceed()` must re-run whatever pre-chain protections it's replacing.
Input-schema validation is deliberately **not** duplicated here: it
still runs once per execution inside the handler chain (both at task
creation and at every `tasks/update` resume), which is correct because
arguments don't change between executions and a schema failure inside an
execution fails that execution the same way a schema failure fails a
synchronous call.

**7. Collision semantics, made uniform.** `RoutedParamContributor` now
fails the boot on a duplicate method key across contributors, or a
contribution colliding with a transport built-in, naming both parties in
the failure message — mirroring `ResourceContributor`'s existing
duplicate-URI treatment. Every `*Contributor` in the taxonomy now shares
one merge rule: union at startup, name-both-parties boot failure on
collision.

`ServerCapabilitiesOverrideAuditor` (`SmartInitializingSingleton`,
registered **unconditionally** by `MocapiServerAutoConfiguration`) warns
once, in `afterSingletonsInstantiated()`, when `ServerCapabilitiesCustomizer`
beans exist but the default `ServerCapabilities` bean was backed off by a
user-supplied one (so the customizers were silently discarded).
Unconditional registration — rather than gating the auditor bean itself
on `ServerCapabilitiesCustomizer` beans existing at auto-configuration
processing time — makes detection immune to which auto-configuration
happens to run first; every singleton from every module is guaranteed to
exist by `afterSingletonsInstantiated()`.

`ClientCapabilities.hasExtension(String id)` is added to `mocapi-model`
(null-safe) as the one place extension-capability detection lives;
`TaskToolCallDispatcher.isTaskCapable` and `McpTasksService`'s gating
delegate to it instead of re-implementing the null/containsKey check.

`McpResourcesService`'s constructor overloads are consolidated to two
public constructors (the contributor-based primary, and a
convenience no-interceptor/default-settings overload) — down from
whatever ad hoc set of overloads had accumulated across ADR-0035 and
ADR-0038's interceptor addition.

## Deferred (spec §9 — named, not silently dropped)

- **Guard/audit/strata participation for extension-owned JSON-RPC
  methods** (`tasks/get`/`tasks/update`/`tasks/cancel`, and any future
  extension's own methods). These currently bypass the six-stratum chain
  entirely; `McpTasksService` keeps its own hand-rolled principal checks.
  Next design cycle's forcing case: a second extension that also wants
  guards on its own methods, which would justify generalizing the strata
  to method-level rather than handler-level attachment.
- **`mocapi-o11y` openness to extension methods/attributes.** The
  semconv observation attached via `JsonRpcMethodHandlerCustomizer` uses
  a hardcoded method-name switch; extension methods (`tasks/*`) get no
  `mcp.method.name`-specific enrichment. Deferred until a second
  extension's methods make the hardcoded switch visibly unscalable.
- **An `McpExtension` aggregate façade** bundling an extension's
  capability customizer, dispatch interceptor, routed-param contributor,
  and autoconfiguration into one registration unit. Two extensions
  (`mocapi-apps`, `mocapi-tasks`) isn't enough evidence to generalize the
  shape correctly; revisit at the third extension.
- **Renaming already-well-named 1.1.0 seams.** `ResourceContributor`,
  `ServerCapabilitiesCustomizer`, and the four `*HandlerCustomizer`s
  already match this taxonomy and are not touched.

## Consequences

**What this buys us.** One vocabulary for every extension point —
lifecycle and merge rule are readable off the type name, which is the
`docs/guides/extending-mocapi.md` guide's organizing structure. One
tool-invocation core instead of two mechanically-synchronized copies,
closing a bean-graph cycle as a side effect. Task-mode calls now get the
same synchronous authorization outcome as sync-mode calls. Collision
handling is one rule instead of an ad hoc per-seam decision.

**Costs.** `ToolCallDispatchCustomizer`, `ToolDescriptorCustomizer`,
`ResourceDescriptorCustomizer`, and `McpRoutedParamContributor` — all
1.1.0-vintage types — are deleted or renamed one release after shipping;
this is deliberate (see the semver-scope policy) but is still real
migration work for anyone who had already built against 1.1.0's shape.
`ReplayOutcome<R, Q>`'s double type parameter is less immediately
readable than two separate sealed hierarchies would have been; the trade
was made explicitly rather than silently reverted, per this task's design
spec (§4).

**Supersedes.** [ADR-0038](0038-server-seams-for-extensions.md)'s
dispatch-hook (`ToolCallDispatchCustomizer`) and invoker-placement
(`McpToolsService implements ToolCallReplayInvoker`) decisions are
superseded by decisions 2 and 3 above. ADR-0038's other three decisions —
the `ReplayExecutor` extraction, `ProgressSink`, and the routing
contribution seam (renamed here, semantics unchanged) — stand unchanged.

**Amends.** [ADR-0034](0034-descriptor-meta-and-customizer-seams.md)'s
descriptor-customizer seams (`ToolDescriptorCustomizer`,
`ResourceDescriptorCustomizer`) are folded into the four handler
customizers per decision 4 above; ADR-0034's `_meta`-on-descriptors
decision for `Tool`/`Resource` stands unchanged. ADR-0034's non-goal of
extending `_meta` to `Prompt`/`ResourceTemplate` is reversed by decision
4a above, which is this ADR's own addition, not a restatement of
ADR-0034.

**Code anchors:**

- `mocapi-server/src/main/java/com/callibrity/mocapi/server/dispatch/McpDispatchInterceptor.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/dispatch/DispatchChains.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolInvocationCore.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlerRegistry.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolCallReplayInvoker.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/mrtr/ReplayOutcome.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlerConfig.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/routing/RoutedParamContributor.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TaskToolCallDispatcher.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/ServerCapabilitiesOverrideAuditor.java`
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/ClientCapabilities.java` (`hasExtension`)
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/Prompt.java` (`_meta`, `withMeta`)
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/ResourceTemplate.java` (`_meta`, `withMeta`)
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/McpResourcesService.java`
- `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/AppsToolUiMetaCustomizer.java`
- `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/AppsResourceUiMetaCustomizer.java`
