# Extension-Seam Elegance Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the approved seam-elegance spec: generic `proceed`-based dispatch interception, `ToolInvocationCore` extraction, `ReplayOutcome` unification, descriptor-customizer fold, task-mode guard parity, uniform collision semantics, taxonomy docs + ADR-0039.

**Architecture:** Every change is internal-SPI reshaping per the semver-scope policy; user-facing annotations/contexts/wire behavior are untouched. The authoritative shapes live in the spec — `docs/superpowers/specs/2026-08-02-extension-seam-elegance-design.md` (§ references below). Zero-interceptor and zero-change paths must stay byte-identical; existing behavioral tests may be edited ONLY for the mechanical renames/migrations the spec mandates.

**Tech Stack:** Java 25, Spring Boot 4.0.5 (generics-aware `List<T<A,B>>` injection), JUnit 5 + AssertJ.

## Global Constraints

- Apache-2 license header on new files; `mvn -q spotless:apply` before committing; no star imports; no `@SuppressWarnings`; Jackson annotations from `com.fasterxml.jackson.annotation`, databind from `tools.jackson.databind`.
- The spec (§ numbers below) is the requirements document — read the referenced section before the task; its signatures are verbatim.
- Deleted types must leave no references: each deleting task ends with a `git grep` proving it.
- Existing test files may be edited only for renames/migrations that the spec mandates; assertions must not be weakened.
- Full module suite green before every commit; `mvn -q verify` from root at each task's end.
- Conventional commits; reference ADR-0039 where applicable.

---

### Task 1: `McpDispatchInterceptor<H, P>` + chain, wired into all three services; migrate tasks; delete the old hook (spec §3)

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/dispatch/McpDispatchInterceptor.java` (spec §3 javadoc + signature verbatim)
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/dispatch/DispatchChains.java` — `static Object run(List<McpDispatchInterceptor<H,P>> sorted, H handler, P params, Supplier<Object> terminal)` folding right-to-left so list order = outermost-first; plus `static <H,P> List<...> sort(List<...>)` using `AnnotationAwareOrderComparator`
- Modify: `McpToolsService` (replace the `ToolCallDispatchCustomizer` loop + constructor param), `McpPromptsService`, `McpResourcesService` (new constructor param + chain around their `elicitationEngine.execute(...)` call; resources: resolve the handler before the chain so the interceptor sees it — restructure `readResource`/`doReadResource` minimally)
- Modify: `MocapiServerToolsAutoConfiguration`, `MocapiServerPromptsAutoConfiguration`, `MocapiServerResourcesAutoConfiguration` (inject the generics-typed lists, null-coalesced)
- Modify: `mocapi-tasks/.../TaskToolCallDispatcher.java` → `implements McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>`; non-claiming branches `return proceed.get()`
- Delete: `mocapi-server/.../tools/ToolCallDispatchCustomizer.java` + its test (superseded by new tests)
- Tests: `mocapi-server/.../dispatch/McpDispatchInterceptorTest.java` (chain order via @Order, proceed-decoration, short-circuit, zero-interceptor identity on all three services), migrate `TaskToolCallDispatcherTest` mechanically

**Steps:** (TDD) write the chain/order/decoration tests first → RED → implement → full `mvn -q -pl mocapi-server,mocapi-autoconfigure,mocapi-tasks test` → root `mvn -q verify` → grep gate `git grep ToolCallDispatchCustomizer -- '*.java'` = empty → commit `refactor(server)!: McpDispatchInterceptor replaces ToolCallDispatchCustomizer across the MRTR methods (ADR-0039)`.

---

### Task 2: Task-mode guard parity (spec §6)

**Files:**
- Modify: `TaskToolCallDispatcher` — before minting the record: `if (Guards.evaluate(handler.guards()) instanceof GuardDecision.Deny deny) throw new JsonRpcException(JsonRpcErrorCodes.FORBIDDEN, "Forbidden: " + deny.reason());` (match the sync path's message format — read `GuardEvaluationInterceptor` first and reuse its exact wording/mechanism)
- Tests: unit — guarded handler + capable client → `-32010`, store untouched; e2e in `TasksEndToEndTest` — guarded `@McpTask` tool, unauthorized capable client → synchronous `-32010`, and no `CreateTaskResult` shape in the response

**Steps:** TDD as usual; suites + verify; commit `fix(tasks): evaluate guards before task creation — sync/task authorization parity`.

---

### Task 3: `ToolInvocationCore` + `ReplayOutcome<R, Q>` unification (spec §4)

**Files:**
- Create: `mocapi-server/.../tools/ToolInvocationCore.java` — handler lookup (constructor takes the handler list or lookup function shared with `McpToolsService`), context construction, `invokeWithContext` moved verbatim, `McpExchange` binding, result mapping; `implements ToolCallReplayInvoker`
- Modify: `ReplayOutcome` → generic `ReplayOutcome<R, Q>` per spec §4; `ReplayExecutor.execute` returns `ReplayOutcome<Object, ElicitRequestFormParams>`
- Modify: `ToolCallReplayInvoker` — returns `ReplayOutcome<CallToolResult, ElicitRequest>`; delete nested `Outcome`; rename param `progressOverride` → `progress`
- Modify: `McpToolsService` — delegates to the core; drops `implements ToolCallReplayInvoker`; constructor takes the core
- Modify: `MrtrElicitationEngine` — `ReplayExecutor` constructor-injected; `replayExecutor()` accessor removed if no longer needed
- Modify: `TaskExecutionEngine` (switch on generic outcome), `MocapiTasksAutoConfiguration` (inject `ToolInvocationCore` directly; DELETE the `ObjectProvider` workaround + its comment), `MocapiServerToolsAutoConfiguration`/`MocapiServerAutoConfiguration` (bean wiring for core + executor)
- Tests: migrate `ToolCallReplayInvokerTest` + `ReplayExecutorTest` mechanically; add a bean-graph test asserting no cycle (context starts with tasks + tools autoconfig without `ObjectProvider`)

**Escalation clause (from spec):** if `ReplayOutcome<R, Q>` reads badly at call sites, STOP and report DONE_WITH_CONCERNS with the ugliest call site quoted — do not silently keep two hierarchies.

**Steps:** TDD; suites (`mocapi-server,mocapi-autoconfigure,mocapi-tasks`) + verify; grep gate for `ToolCallReplayInvoker.Outcome` and `ObjectProvider<McpToolsService>` = empty; commit `refactor(server)!: ToolInvocationCore owns detached execution; ReplayOutcome unified (ADR-0039)`.

---

### Task 4: Descriptor-customizer fold (spec §5)

**Files:**
- Modify: all four `*HandlerConfig`s (settable descriptor), `CallToolHandlers`/`ReadResourceHandlers`/prompt + resource-template builders (single customizer pass, post-generation)
- Delete: `ToolDescriptorCustomizer`, `ResourceDescriptorCustomizer` (+ direct tests, folded into customizer tests)
- Modify: `mocapi-apps` — `AppsToolDescriptorCustomizer` → `CallToolHandlerCustomizer`, `AppsResourceDescriptorCustomizer` → `ReadResourceHandlerCustomizer` (same behavior; read then `config.descriptor(updated)`), `MocapiAppsAutoConfiguration` bean types updated
- Modify: `Tool.withMeta`/`Resource.withMeta` → `deepCopy()` (spec §5 bullet 4)
- Conditional: `_meta` on `Prompt`/`ResourceTemplate` iff `docs/plans/2026-07-28-schema.ts` declares it (check first; skip + note otherwise)
- Tests: migrate apps tests; add prompt-descriptor customization test (parity proof); withMeta immutability test (mutate the input node after build → served descriptor unchanged)

**Steps:** TDD; suites (`mocapi-server,mocapi-autoconfigure,mocapi-apps`) + verify; grep gate; commit `refactor(server,apps)!: fold descriptor customization into the per-kind handler customizers (ADR-0039)`.

---

### Task 5: Collision semantics, rename, hardening (spec §2, §7)

**Files:**
- Rename: `McpRoutedParamContributor` → `RoutedParamContributor` (same package); update `TasksRoutedParamContributor`, `StreamableHttpAutoConfiguration`, `McpHeaderValidatorTest` additions
- Modify: the autoconfigure merge — duplicate key across contributors OR contribution colliding with a built-in → `IllegalStateException` at boot naming both parties
- Create: `ServerCapabilitiesOverrideAuditor` (autoconfigure, `SmartInitializingSingleton`) — WARN naming discarded customizer beans when the user supplied `ServerCapabilities`
- Modify: `ClientCapabilities` — `public boolean hasExtension(String id)` (null-safe); `TaskToolCallDispatcher.isTaskCapable` + `McpTasksService.requireTaskCapable` delegate to it
- Modify: `McpResourcesService` — constructor consolidation (contributor ctor primary, ≤2 public)
- Tests: collision boot-failure (both flavors), auditor WARN present/absent (CapturedOutput), `hasExtension` truth table, ctor-consolidation compile fallout

**Steps:** TDD; suites (`mocapi-server,mocapi-streamable-http-transport,mocapi-autoconfigure,mocapi-tasks,mocapi-model`) + verify; grep gate `McpRoutedParamContributor` = empty; commit `refactor(server,http)!: contributor collision hardening, RoutedParamContributor rename, hasExtension (ADR-0039)`.

---

### Task 6: ADR-0039, guides, design docs, CHANGELOG (spec §8)

**Files:**
- Create: `docs/adr/0039-extension-seam-taxonomy-and-dispatch-interception.md` (template; Accepted; supersedes ADR-0038's dispatch-hook + invoker-placement decisions, amends ADR-0034; records semver-scope policy + deferred items; **Code anchors**)
- Modify: `docs/adr/0034-*.md`, `docs/adr/0038-*.md` (amended-by back-links), `docs/adr/README.md`
- Create: `docs/guides/extending-mocapi.md` (spec §8 content list — taxonomy table with lifecycle/merge columns, eight seams with worked examples from apps/tasks, interceptor contract, `hasExtension`, error-code guidance, API-vs-SPI)
- Modify: `docs/design/extension-spi.md` (rewrite per spec §8 incl. the two corrections), `docs/design/tasks.md`, `docs/design/apps.md`, `docs/design/handlers.md` (renamed/deleted types), `docs/constitution.md` (I2 citation touch-up), `docs/guides/README.md`, `README.md` (guides list), `CHANGELOG.md` (rename/migration table under Unreleased)
- Verify every code anchor and cross-link resolves.

**Steps:** write → link-check → `mvn -q verify` → commit `docs: ADR-0039, Extending mocapi guide, seam-taxonomy doc sweep`.

---

### Task 7: Acceptance sweep (spec §10)

- [ ] `mvn -q verify` from root → green
- [ ] Grep gate: `git grep -l "ToolCallDispatchCustomizer\|ToolDescriptorCustomizer\|ResourceDescriptorCustomizer\|McpRoutedParamContributor" -- '*.java'` → empty; `-- '*.md'` hits only CHANGELOG/ADR history
- [ ] Conformance: start `mocapi-conformance`, run base suite + the tasks scenarios → 79/13 and 33/2 unchanged; kill server
- [ ] Bean-graph: confirm no `ObjectProvider` cycle workaround remains (`git grep "ObjectProvider<McpToolsService>"` → empty)
- [ ] Commit anything the sweep shakes out; report results verbatim
