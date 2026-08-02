# Extension-Seam Elegance Refactor — Design Spec

- **Date:** 2026-08-02
- **Status:** Approved design (pre-implementation)
- **Provenance:** the three-way extensibility audit run after mocapi-apps + mocapi-tasks
  shipped (seam inventory, per-seam elegance judgment, design-coherence/stress tests).
  James's directive: the core must be a textbook example of an extensible framework.
- **Semver posture:** per James's stated policy, internal SPIs/plumbing with no external
  implementers are NOT semver-frozen; this refactor renames/reshapes seams (including two
  released in 1.1.0) in a minor release, with a CHANGELOG note. User-facing surface
  (annotations, handler contexts, wire behavior, properties) is unchanged.
- **Supersedes/amends:** parts of ADR-0034 (descriptor customizers) and ADR-0038 (dispatch
  hook, invoker placement) — recorded in a new ADR-0039.

## 1. Summary

Seven coordinated changes that make the extension surface one coherent design language:

1. **`McpDispatchInterceptor<H, P>`** — a generic, `proceed`-based, ordered per-request
   interception seam on all three MRTR methods, replacing the claim-only, tools-only
   `ToolCallDispatchCustomizer`.
2. **`ToolInvocationCore`** — the detached tool-execution core extracted out of
   `McpToolsService`; it (not the service) becomes the `ToolCallReplayInvoker` bean,
   eliminating the `ObjectProvider` bean-cycle workaround.
3. **`ReplayOutcome<R, Q>` unification** — one generic sealed outcome type; the duplicate
   `ToolCallReplayInvoker.Outcome` hierarchy is deleted.
4. **Descriptor-customizer fold** — `descriptor` becomes settable on all four
   `*HandlerConfig`s; `ToolDescriptorCustomizer`/`ResourceDescriptorCustomizer` are
   deleted; mocapi-apps migrates to the per-kind handler customizers; prompts and
   resource templates gain descriptor customization for free.
5. **Pre-guard parity for tasks** — `TaskToolCallDispatcher` evaluates the handler's
   guards before minting a task, so authorization semantics are identical in sync and
   task mode.
6. **Uniform collision semantics** — routed-param duplicates fail the boot;
   a user-supplied `ServerCapabilities` bean that discards registered customizers logs a
   prominent WARN; interceptor ordering is `@Order`, documented.
7. **Naming taxonomy + docs** — five seam words with fixed contracts, a single
   "Extending mocapi" guide, corrected `extension-spi.md`, ADR-0039.

## 2. The seam taxonomy (the language this spec enforces)

| Word | Contract | Members after this refactor |
|---|---|---|
| `*Contributor` | Startup; returns values; framework unions them; **collisions fail the boot** | `ResourceContributor`, `RoutedParamContributor` |
| `*Customizer` | Startup; mutates/folds a framework-owned object; **never per-request** | `CallToolHandlerCustomizer` + 3 siblings (now descriptor-capable), `ServerCapabilitiesCustomizer`, oauth2 customizers |
| `*Interceptor` | Per-request; ordered (`@Order`); receives `proceed`; may short-circuit | `McpDispatchInterceptor`, the six-strata `MethodInterceptor`s |
| `*Store` / `*Source` / `*Strategy` | Deployment-supplied strategy bean, `@ConditionalOnMissingBean` default | `TaskStore`, `McpPrincipalSource`, `McpTokenStrategy` |
| `*Sink` | Runtime one-way delivery callback supplied at construction | `ProgressSink` |
| *(no seam suffix)* | API you **call** (documented as such, excluded from the SPI table) | `ToolCallReplayInvoker`, `McpProgressSource`, `McpElicitor` |

Prefix rule, stated in the guide: `Mcp` prefix for `mocapi-api` (author-facing) types and
for cross-cutting server concepts; `McpPrincipalSource` stays as-is (grandfathered);
`McpRoutedParamContributor` → **`RoutedParamContributor`** (package unchanged:
`com.callibrity.mocapi.server.routing`).

## 3. `McpDispatchInterceptor<H, P>` (replaces `ToolCallDispatchCustomizer`)

```java
// com.callibrity.mocapi.server.dispatch
@FunctionalInterface
public interface McpDispatchInterceptor<H, P> {
  /**
   * Intercepts one dispatch of an MRTR-capable method. Return proceed.get() (optionally
   * decorated) to continue the chain; return a different response Object to own the call;
   * throw to abort with a JSON-RPC error. Interceptors are ordered by @Order/Ordered;
   * lower values run outermost. Runs BEFORE the handler chain — and therefore before
   * guards and schema validation; an interceptor that does not call proceed() owns those
   * responsibilities itself (see the Extending-mocapi guide).
   */
  Object intercept(H handler, P params, Supplier<Object> proceed);
}
```

- The three services collect their own generic instantiation via Spring's
  generics-aware injection: `List<McpDispatchInterceptor<CallToolHandler,
  CallToolRequestParams>>` in `McpToolsService`,
  `<GetPromptHandler, GetPromptRequestParams>` in `McpPromptsService`,
  `<ReadResourceHandler, ResourceRequestParams>` in `McpResourcesService`
  (verify the exact prompt/resource handler + params type names in code; resources may
  need the lookup restructured so the handler is resolved before the chain runs, matching
  tools).
- Chain assembly per dispatch: sort by `@Order` once at construction, then fold:
  innermost `proceed` = the existing default path (the `elicitationEngine.execute(...)`
  call). The zero-interceptor path must be byte-for-byte today's behavior.
- `ToolCallDispatchCustomizer` is **deleted** (unreleased). `TaskToolCallDispatcher`
  becomes `McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>`: capable +
  `@McpTask` → return `CreateTaskResult` (never calls `proceed`); `required` +
  non-capable → throw as today; otherwise → `return proceed.get()`.
- Javadoc + guide document the three outcomes (proceed / own / abort) and that declining
  interceptors must be side-effect-free.

## 4. `ToolInvocationCore` + `ReplayOutcome<R, Q>` unification

- New `com.callibrity.mocapi.server.tools.ToolInvocationCore` (name final): owns handler
  lookup (takes the registry via `McpToolsService`'s handler list — constructor takes
  `List<CallToolHandler>` or a lookup function), `DefaultMcpToolContext` construction,
  `invokeWithContext` (moves here verbatim, including the exception cascade), the
  `McpExchange` ScopedValue binding for detached invocation, and result mapping.
- `ToolInvocationCore implements ToolCallReplayInvoker`; `McpToolsService` delegates its
  sync path to the same core. The autoconfigure bean graph becomes acyclic:
  `ToolInvocationCore` ← (`McpToolsService`, `TaskExecutionEngine`); the
  `ObjectProvider` workaround in `MocapiTasksAutoConfiguration` is deleted.
- `ReplayOutcome` becomes generic: `ReplayOutcome<R, Q>` sealed with
  `Completed<R, Q>(R result)` and `InputRequired<R, Q>(String key, Q request,
  List<ResponseLedgerEntry> ledger)`. `ReplayExecutor.execute` returns
  `ReplayOutcome<Object, ElicitRequestFormParams>`; `ToolCallReplayInvoker.invoke`
  returns `ReplayOutcome<CallToolResult, ElicitRequest>`. The nested
  `ToolCallReplayInvoker.Outcome` hierarchy is deleted; `TaskExecutionEngine` switches on
  the generic type. If the double type parameter proves unreadable in practice, the
  implementer escalates rather than silently reverting to two hierarchies.
- `ToolCallReplayInvoker` keeps its name but is reclassified in docs/ADR as an API you
  call; `progressOverride` parameter renamed `progress`.
- `MrtrElicitationEngine.replayExecutor()` accessor is removed if the core can receive
  the `ReplayExecutor` directly (constructor-injected); `ReplayExecutor` becomes a
  constructor argument of the engine rather than `new`'d inside it.

## 5. Descriptor-customizer fold (amends ADR-0034)

- All four `*HandlerConfig`s gain a descriptor mutator (`void descriptor(Tool)` etc.);
  `descriptor()` keeps returning the current value. Customizers now run where descriptor
  customizers ran (post-generation, pre-chain-assembly) — one customizer pass per
  handler, ordering unchanged otherwise.
- `ToolDescriptorCustomizer` and `ResourceDescriptorCustomizer` are **deleted** (1.1.0
  types; allowed per the semver policy — CHANGELOG migration note required).
  `AppsToolDescriptorCustomizer` / `AppsResourceDescriptorCustomizer` become
  `CallToolHandlerCustomizer` / `ReadResourceHandlerCustomizer` beans with identical
  behavior.
- Prompts and resource templates: descriptor mutators exist for parity. `_meta` is added
  to the `Prompt` / `ResourceTemplate` model records **iff** the 2026-07-28 core schema
  declares `_meta` on those types (I7 — verify in `docs/plans/2026-07-28-schema.ts`;
  if absent, skip the model change and note it).
- `Tool.withMeta` / `Resource.withMeta` `deepCopy()` their `ObjectNode` so published
  descriptors are immutable; `meta()` accessors return a defensive copy or are
  documented read-only (pick `deepCopy` on write + document; do not return copies on
  every `meta()` read — list serialization hot path).

## 6. Task-mode authorization parity

`TaskToolCallDispatcher` evaluates `Guards.evaluate(handler.guards())` before creating
the task record; a `Deny` throws the same `-32010 Forbidden` `JsonRpcException` the sync
path produces. E2E test: guarded `@McpTask` tool + unauthorized capable client →
synchronous `-32010`, no task record created (assert store emptiness via a second
`tasks/get` being impossible — assert no CreateTaskResult was returned). Input-schema
validation intentionally still runs inside the handler chain per execution (documented).

## 7. Collision semantics + misc hardening

- `RoutedParamContributor` merge (autoconfigure): duplicate method key across
  contributors, or a contribution colliding with a built-in, **fails the boot** with a
  message naming both parties (mirrors `ResourceContributor`'s duplicate-URI treatment).
- New `ServerCapabilitiesOverrideAuditor` (autoconfigure, `SmartInitializingSingleton`):
  when `ServerCapabilitiesCustomizer` beans exist but the `ServerCapabilities` bean was
  user-supplied (our `@ConditionalOnMissingBean` factory backed off), log one WARN naming
  the discarded customizer bean names.
- `ClientCapabilities.hasExtension(String id)` added to mocapi-model (null-safe);
  `TaskToolCallDispatcher.isTaskCapable` and the tasks service gate delegate to it.
- `McpResourcesService` constructors consolidated: the contributor-based constructor is
  the primary; extra overloads reduced to what tests genuinely need (target ≤2 public).

## 8. Documentation + governance

- **ADR-0039 — extension-seam taxonomy and dispatch interception**: records the
  taxonomy table, the interceptor redesign (supersedes ADR-0038's dispatch-hook and
  invoker-placement decisions; ADR-0038's ReplayExecutor/ProgressSink/routing decisions
  stand), the descriptor fold (amends ADR-0034), the semver-scope policy, and the
  deliberately deferred items (guards/audit for extension-owned methods; o11y method-set
  openness; `McpExtension` façade — each with a sentence of rationale).
- **New guide `docs/guides/extending-mocapi.md`**: the one-stop "writing a mocapi
  extension" page — the taxonomy table with lifecycle + merge-rule columns, the eight
  seams with one worked example each (drawn from apps/tasks), the interceptor contract
  (proceed/own/abort, ordering, guard responsibility), client-capability detection via
  `hasExtension`, error-code guidance for extensions (use spec codes; never allocate in
  `-32000..-32019` — I9), and the API-vs-SPI classification.
- `docs/design/extension-spi.md` rewritten to match (fix the two audit-caught errors:
  `JsonRpcMethodHandlerCustomizer` is ripcurl's, consumed by core and o11y; the
  "see-and-attach at startup" claim no longer covers per-request seams — the interceptor
  gets its own lifecycle-labeled section). `docs/design/tasks.md`, `apps.md`,
  `handlers.md` updated where they name renamed/deleted types. Constitution I2 citation
  list gains ADR-0038/0039 (routing contribution acknowledged) — wording touch-up from
  the earlier follow-up list rides along.
- `CHANGELOG.md`: "Internal extension SPIs reshaped (unreleased + 1.1.0 seam types);
  see ADR-0039" with a rename/migration table.

## 9. Non-goals (deferred with names attached)

- Guard/audit/strata participation for **extension-owned JSON-RPC methods** (`tasks/*`
  et al.) — next design cycle; `McpTasksService` keeps its hand-rolled checks for now.
- `mocapi-o11y` openness to extension methods/attributes (hardcoded method switch).
- `McpExtension` aggregate façade — revisit at the third extension.
- Renaming 1.1.0 seams that are individually well-named (`ResourceContributor`,
  `ServerCapabilitiesCustomizer`, the four `*HandlerCustomizer`s keep their names).

## 10. Acceptance

- Full reactor green; existing MRTR/tools/prompts/resources/apps/tasks suites pass with
  only the mechanical renames/migrations this spec mandates (no behavioral test edits
  except the new ones specified).
- Zero-interceptor dispatch path proven byte-identical (existing service tests unedited).
- Conformance suite unchanged: base 79 pass/13 waived; tasks scenarios 33 pass/2 waived.
- The bean graph contains no `ObjectProvider` cycle workaround.
- `git grep -l "ToolCallDispatchCustomizer\|ToolDescriptorCustomizer\|ResourceDescriptorCustomizer\|McpRoutedParamContributor"`
  returns only CHANGELOG/ADR history mentions.
