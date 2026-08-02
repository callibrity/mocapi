# MCP Tasks Extension — Design Spec

- **Date:** 2026-08-02
- **Status:** Approved design (pre-implementation)
- **Extension:** `io.modelcontextprotocol/tasks` (SEP-2663, `modelcontextprotocol/ext-tasks`,
  spec `tasks.extensions.modelcontextprotocol.io/specification/draft/tasks`)
- **Supersedes:** the 2026-07-31 tasks design spec (commit `309d01fa`, unmerged branch
  `feat/mcp-tasks-extension-design`) — see §2 for what changed and why.
- **Supersedes stance:** the "decline tasks" line item in ADR-0022 (to be flipped during
  implementation).
- **Relationship to Apps:** the `ServerCapabilitiesCustomizer` seam the 07-31 spec proposed
  has since shipped with MCP Apps (ADR-0031/0033); this design reuses it as-is. The Apps
  spec's link to `2026-07-31-mcp-tasks-extension-design.md` is dangling on main and should
  be repointed here during implementation.

## 1. Summary

Add support for the MCP **Tasks** extension as a new, optional module `mocapi-tasks`. A tool
author opts in by adding **one annotation** to an ordinary `@McpTool` method:

```java
@McpTool(description = "Re-encode a video")
@McpTask                                       // ← the entire task-enabling surface
public EncodeResult encode(String uri, McpToolContext ctx) { ... }
```

The tool body is written exactly as a normal tool and **never knows whether it is running as
a task**. When a task-capable client calls it, the server returns a `CreateTaskResult`
handle and runs the tool on a background virtual thread; the client polls `tasks/get`,
answers elicitations via `tasks/update`, and may `tasks/cancel`. When a non-capable client
calls it, the same tool runs synchronously as today.

Mid-task elicitation is **MRTR through an intermediary**: `ctx.elicit(...)` unwinds via the
existing `InputRequiredException` exactly as in wire MRTR, but the response ledger lives in
a `TaskStore` keyed by `taskId` instead of riding the wire as an encrypted `requestState`
token. Resume (`tasks/update`) re-executes the handler from the top against the merged
ledger — one replay engine, one idempotency contract, two ledger carriers.

Core mocapi stays stateless (constitution I1 amended with a scoped exception): all task
state is confined behind the `TaskStore` SPI inside the opt-in module. **No new
dependencies** — the in-memory store is the only shipped implementation; cluster stores are
user-supplied beans (a Substrate-side adapter is anticipated but lives outside mocapi).

## 2. What changed vs. the 2026-07-31 spec

The 07-31 design made tasks an **explicit API**: a task-eligible tool declared a
`TaskContext` parameter, returned `CreateTaskResult`, wrapped its body in `task.submit(...)`,
and called `task.elicit(...)`, which **blocked the background thread** parked on the store.
Task tools were task-only (`-32003` for non-capable clients, no sync degrade).

This design replaces that surface entirely:

| | 07-31 spec | This spec |
|---|---|---|
| Opt-in | `TaskContext` param + `CreateTaskResult` return + `submit(...)` | `@McpTask` annotation on an unmodified tool |
| Tool awareness | Tool code is task-shaped | Tool code is task-agnostic |
| Elicitation | Blocks the parked thread; no idempotency contract | MRTR replay from the top; ADR-0021 contract applies unchanged |
| Non-capable client | Always `-32003` | Runs synchronously; `-32003` only with `required = true` |
| Progress | `task.updateStatus(String)` (task-only API) | Standard ADR-0025 emitters, routed to `statusMessage` |
| Resume after node loss | Parked thread dies with node | `input_required` state is fully in the store; any node can resume |

Rationale: the replay pattern already exists (ADR-0021), already carries the idempotency
contract, and already unifies tools/prompts/resources; parking threads reintroduced exactly
the parked-continuation liabilities ADR-0021 rejected. The transparent annotation means the
same tool serves both capable and non-capable clients from one body, and handler authors
learn nothing new. Carried over from 07-31: module name, polling-only v1 scope, the wire
contract, TTL/reaping model, `-32003` shape, and the conformance strategy.

## 3. What the extension requires (grounded in the spec)

- **Methods:** `tasks/get` (poll), `tasks/update` (deliver `inputResponses`), `tasks/cancel`
  (cooperative). There is deliberately **no `tasks/list`** — task IDs are bearer handles and
  MUST be generated "with sufficient entropy that a third party cannot enumerate or guess
  them."
- **Task creation is server-directed.** The client signals support per request via
  `_meta["io.modelcontextprotocol/clientCapabilities"].extensions["io.modelcontextprotocol/tasks"]`;
  the server alone decides whether to return `CreateTaskResult` (`resultType: "task"`).
  A server that cannot serve a request without a task MUST return the spec's
  `MissingRequiredClientCapabilityError` with `data.requiredCapabilities` — code **`-32021`**
  per the core 2026-07-28 registry (`MissingRequiredClientCapabilityErrorData.CODE`, the
  translator mocapi already ships for elicitation). The extension site's draft text still
  says `-32003` — stale from before tasks moved out of core (`-32003` sits in the
  implementation-defined sub-range I9 reserves); verify against the conformance suite during
  implementation and record the outcome in ADR-0037.
- **Durability MUST:** "the task is durably created before the response is sent" — a
  `tasks/get` arriving immediately after the `CreateTaskResult` must find the task.
- **`Task` fields:** `taskId`, `status`, `statusMessage?`, `createdAt`, `lastUpdatedAt`
  (ISO 8601), `ttlMs` (null = unlimited), `pollIntervalMs?`. Statuses: `working`,
  `input_required`, `completed`, `failed`, `cancelled` (last three terminal).
- **`tasks/get`** returns the status-appropriate shape: `input_required` MUST include **all**
  outstanding `inputRequests`; `completed` includes `result`; `failed` includes `error`.
  Each `inputRequests` key MUST be unique over the task's lifetime.
- **`tasks/update`** `{taskId, inputResponses}` → empty ack (eventually consistent); keys not
  currently outstanding SHOULD be ignored; partial sets MAY be accepted.
- **`tasks/cancel`** `{taskId}` → empty ack; cancellation is cooperative and the transition
  to `cancelled` is not guaranteed.
- **Error mapping rule:** `failed` is reserved for JSON-RPC-level errors. A tool-level error
  (`CallToolResult.isError = true`) MUST surface as `completed` with the error in `result`.
- **Progress:** `notifications/progress` and `notifications/message` are **not supported**
  on tasks; status is conveyed only via `tasks/get` / `notifications/tasks`.
- **Routing header:** over Streamable HTTP, `tasks/*` requests MUST carry
  `Mcp-Name: <taskId>` (the core 2026-07-28 routing-header pair `Mcp-Method`/`Mcp-Name`,
  with the taskId as the target name). The spec is explicitly silent on how intermediaries
  learn taskId→instance affinity (see §10).
- **Scope:** only `tools/call` is task-augmentable today; designs SHOULD accommodate more
  request types later.
- **Errors:** `-32602` for invalid/unknown `taskId` (MUST on `tasks/get`, SHOULD on
  update/cancel); `-32021` for missing capability (see above); `-32603` internal.

## 4. Goals / non-goals

**Goals**

- `@McpTask` on an unmodified `@McpTool` method; transparent dual-mode execution.
- `tasks/get` / `tasks/update` / `tasks/cancel`, polling model, `tools/call` only.
- MRTR-replay resume with the ledger in the store; ADR-0021 semantics unchanged.
- Progress emitters routed to `statusMessage`.
- `TaskStore` SPI (atomic-mutation contract) + in-memory default + contract test kit;
  **zero new dependencies**.
- Principal-bound task access; spec-strength taskId entropy.
- Core stateless; three small, generic seams added to `mocapi-server`.

**Non-goals (v1, YAGNI)**

- `notifications/tasks` push and `subscriptions/listen` (optional per spec; mocapi declines
  `subscriptions/listen` per I5/ADR-0022 — polling is the mandatory path and mocapi's only path).
- Task-augmenting `prompts/get` / `resources/read` (spec doesn't allow it yet; the shared
  replay core keeps the door open).
- Sampling/roots `inputRequests` (removed features, ADR-0022; elicitation form-mode only) and
  URL-mode elicitation.
- `pollIntervalMs`-based rate limiting (spec MAY).
- Shipped cluster-store adapters (Substrate adapter is a Substrate-side follow-up; token-CAS
  in Substrate is an internal detail of that work, not a mocapi prerequisite).
- Mid-execution thread interruption on cancel (consistent with the ratified ADR-0022 stance).
- Crash-resumable *compute* — a `working` execution dies with its node (§10); `input_required`
  tasks are resumable anywhere by construction.

## 5. Architecture

### 5.1 Module layout

```
mocapi-tasks     @McpTask, TaskStore SPI + TaskRecord, InMemoryTaskStore,
                 task model types (Task, CreateTaskResult, GetTask*/UpdateTask*/CancelTask* params/results),
                 McpTasksService (@JsonRpcMethod × 3), TaskExecutionEngine,
                 TasksCapabilityCustomizer, TaskToolCallDispatcher (dispatch-hook impl),
                 tasks Mcp-Name validation contribution, MocapiTasksAutoConfiguration,
                 TaskStoreContractTest (test-jar: the store TCK)
      └─ depends on ─▶ mocapi-api, mocapi-model, mocapi-server (seams below)
```

Single module (no api/impl split): the public surface is one annotation plus one small SPI;
custom-store authors already run the module. Extension model types live **in the module**,
not `mocapi-model` — I7 scopes the model to the *core* `schema.ts`, and the Apps precedent
(ADR-0033) already established module-local extension types. Add the module to the classpath
→ tasks work (capability advertised, dispatcher active). Omit it → core is byte-for-byte the
stateless server; all three seams sit inert.

### 5.2 The three `mocapi-server` seams

All three are generic (extension-agnostic) and land as their own small refactors; the
existing MRTR test suite passing unchanged is the acceptance gate for #1.

1. **`ToolCallReplayInvoker` — the extracted execution core.** The ledger-replay mechanics
   currently private to `MrtrElicitationEngine` (`ReplayExecution`: ordinal cursor,
   fingerprint enforcement, `InputRequiredException` raising) and to
   `McpToolsService.invokeTool` (handler lookup, `DefaultMcpToolContext` construction,
   ScopedValue binding, six-stratum chain via `handler.call(...)`, result/exception →
   `CallToolResult` mapping) are extracted into one public component:

   ```java
   public interface ToolCallReplayInvoker {
     sealed interface Outcome {
       record Completed(CallToolResult result) implements Outcome {}
       record InputRequired(String key, ElicitRequest request,
                            List<ResponseLedgerEntry> ledger) implements Outcome {}
     }
     Outcome invoke(String toolName, JsonNode arguments,
                    List<ResponseLedgerEntry> ledger,
                    McpProgressSource progressOverride);  // null → default wire progress
   }
   ```

   After the extraction the wire path is a *caller* of the core: `callTool` keeps only the
   token carrier (decode `requestState`, the `-32602` validation table, ledger from token;
   on `InputRequired` outcome, encode a fresh token into `InputRequiredResult`). The task
   path is the second carrier (ledger from store; outcome written to store). Ordinals,
   fingerprints, context binding, strata, and error mapping exist exactly once, so wire and
   task execution cannot drift semantically. Prompts/resources keep using the same core
   internally; only the tool invoker is public in v1.

2. **A `tools/call` dispatch hook.** `McpToolsService.callTool` consults an ordered list of
   `ToolCallDispatchCustomizer` beans before its default path; the first to claim the call
   produces the response `Object` (the spec's response union). `mocapi-tasks` registers the
   sole v1 implementation: it claims the call iff the handler's descriptor meta carries
   `@McpTask` (the ADR-0034 descriptor-meta mechanism) *and* the request's declared client
   capabilities include the tasks extension — then materializes the task (§7). Unclaimed
   calls fall through to today's behavior, byte-for-byte.

3. **Routing-header validation contribution.** The transport's `Mcp-Name` validation table
   (`-32020 HeaderMismatch`) is keyed by method with a fixed name-extractor set
   (`tools/call` → `params.name`, etc.). It gains a contribution seam (method →
   expected-name extractor); `mocapi-tasks` registers `tasks/get|update|cancel` →
   `params.taskId`. Transports keep owning wire validation (I2) without hardcoding
   extension knowledge.

The capability entry `extensions["io.modelcontextprotocol/tasks"] = {}` is contributed via
the **existing** `ServerCapabilitiesCustomizer` (ADR-0031), mirroring Apps.

## 6. User surface

### 6.1 `@McpTask`

```java
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface McpTask {
  String ttl() default "";           // ISO-8601 Duration; "" → mocapi.tasks.default-ttl (default PT1H)
  String pollInterval() default "";  // ISO-8601 Duration; "" → mocapi.tasks.default-poll-interval (default PT2S)
  boolean required() default false;  // true → non-capable clients get -32021 instead of sync execution
}
```

Config properties `mocapi.tasks.default-ttl` / `mocapi.tasks.default-poll-interval` supply
the defaults; the annotation overrides per tool. `ttlMs` is always finite in v1 (no
`null`/unlimited tasks; the spec permits finite TTLs and the in-memory store needs a bound).

### 6.2 The decision rule

| Tool | Client declared tasks extension? | Result |
|---|---|---|
| no `@McpTask` | either | normal synchronous tool (never a task) |
| `@McpTask` | yes | `CreateTaskResult`; body runs as a task |
| `@McpTask` | no | normal synchronous execution (progressive enhancement) |
| `@McpTask(required = true)` | no | `-32021` + `data.requiredCapabilities.extensions` |

### 6.3 Transparency contract

Inside the body, `ctx.elicit(...)`, the progress emitters, guards, validation, parameter
resolution, and `McpToolException` semantics behave identically in both modes. The observable
differences are only where the spec forces them: elicitation answers arrive via
`tasks/update` instead of a wire retry, and progress lands in `statusMessage` instead of
`notifications/progress`. The ADR-0021 idempotency contract ("code before your last
`elicit()` re-executes once per round trip") applies identically — it is the same contract,
restated in the tasks guide.

## 7. Execution model

### 7.1 Task creation (`tools/call`)

The dispatch hook claims the call, then:

1. Mints `taskId`: 256-bit `SecureRandom`, Base64URL (spec entropy MUST; format is opaque to
   clients, so a routing prefix can be added later compatibly).
2. Builds the `TaskRecord` (status `working`, bound principal from `McpPrincipalSource`,
   the request's declared client capabilities snapshot, arguments, ttl, pollInterval, empty
   ledger) and calls `store.create(record)` — which MUST NOT return until the record is
   durably readable (the spec's durability MUST; trivially true in-memory, the contract for
   external stores).
3. Spawns execution #1 on a virtual thread (ADR-0006), then returns `CreateTaskResult`
   (`resultType:"task"`).

### 7.2 Executions — one spin-off routine, two trigger sites

Every execution is identical: run the tool from the top through `ToolCallReplayInvoker`
with the store-loaded ledger bound. Execution #1 is triggered by `tools/call`; executions
#2+ by `tasks/update`. There is **no parked thread** — between executions the task exists
only as its `TaskRecord`.

Each execution runs inside a **`ContextSnapshot` captured from the triggering dispatch
thread** (`ContextSnapshotFactory.captureAll()`, the mechanism transports already use). Both
trigger sites are live, authenticated requests from the bound principal, so the full
six-stratum chain — **guards included** — re-runs under a legitimate security context every
time. No authorization special case, and it works cross-node because auth arrives with each
triggering request rather than being stored.

Outcome handling (all via atomic store mutation, §8):

- **`Completed(result)`** → `working → completed` with `result`. This includes
  `isError: true` tool failures (spec rule: tool-level errors are `completed`).
- **`InputRequired(key, request, ledger)`** → write ledger + pending `inputRequests[key]`
  (`elicit-<ordinal>` keys are unique over the task lifetime since ordinals only grow),
  `working → input_required`.
- **Engine-internal failure** (ledger mismatch, invoker crash) → `failed` with a JSON-RPC
  error object and diagnostic `statusMessage`.
- Any outcome arriving after the record went terminal (e.g. cancel won) is **discarded**.

### 7.3 Resume (`tasks/update`)

Validate principal + taskId (`-32602` on mismatch/unknown/expired, no existence leak), then
atomically: merge `inputResponses` into the ledger (fingerprint/key rules shared with the
wire carrier; keys not outstanding ignored per spec SHOULD; partial sets accepted), and iff
the mutation itself observed `input_required` with keys consumed, flip to `working`. Ack
`{}` immediately (spec: eventually consistent). **Only the mutation that performed the flip
spawns the next execution** — a duplicate concurrent `tasks/update` observes `working`,
consumes nothing, spawns nothing. This derives single-resume from the store's atomicity
instead of racing check-then-act.

### 7.4 Cancel (`tasks/cancel`)

Atomically flip any non-terminal status → `cancelled`; ack `{}`. Terminal states are final:
an in-flight execution is *not* interrupted (ADR-0022's ratified stance — the work
completes, its output goes nowhere; §7.2 discards it). A cancel on an already-terminal task
acks without effect.

### 7.5 Progress

The task carrier passes a `McpProgressSource` whose emitters write
`statusMessage` (formatted value + optional message, e.g. `"42/100: resizing…"`) and bump
`lastUpdatedAt` via store mutation. Monotonicity validation (ADR-0025) runs identically in
both modes. Mutations refuse to touch terminal records, so a straggler progress write cannot
resurrect a cancelled task.

### 7.6 `tasks/get`

Principal-checked read; map the record to the status-appropriate `DetailedTask` shape
(`input_required` → all outstanding `inputRequests`; `completed` → `result`; `failed` →
`error`); `resultType:"complete"`. Unknown, expired, or foreign-principal → `-32602` with
the same "unknown task" message (no existence leak).

## 8. `TaskStore` SPI

```java
public interface TaskStore {
  void create(TaskRecord record);            // throws if taskId exists; durable before returning
  Optional<TaskRecord> get(String taskId);   // empty if unknown or expired
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);
                                             // atomic; returns the post-mutation record
  void delete(String taskId);                // idempotent
}
```

- **The contract is the atomicity of `update`.** The mutation function is applied against
  the current record as one atomic step; implementations use whatever the backend offers
  (`ConcurrentHashMap.compute`, version-column optimistic locking with retry, conditional
  writes). All engine semantics — single-resume, terminal-finality, no-resurrection — derive
  from decisions made *inside* mutations and from what the returned record shows actually
  happened.
- **`TaskRecord`** (immutable record): `taskId`, `toolName`, `arguments`, `principal`,
  `clientCapabilities`, `status`, `statusMessage`, `createdAt`, `lastUpdatedAt`, `ttl`,
  `pollInterval`, `ledger` (`List<ResponseLedgerEntry>` — the MRTR type, reused),
  `inputRequests`, `result`, `error`, plus a monotonic `version` (the optimistic-locking
  handle for stores that need one).
- **Principal enforcement lives in `McpTasksService`** (single enforcement point; the store
  stays a dumb keyed blob). taskId entropy + principal binding together make tasks
  invisible cross-caller; there is no enumeration surface (no `tasks/list`).
- **TTL:** a record older than `createdAt + ttl` is expired — `get` returns empty and the
  store may physically discard it (in-memory: lazy check on access + periodic sweep;
  external stores: native TTL). Expired == unknown on the wire (`-32602`).
- **`InMemoryTaskStore`** is the shipped default (`@ConditionalOnMissingBean(TaskStore.class)`),
  fully satisfying the atomicity contract. When auto-configuration falls back to it, a
  prominent WARN is logged (mirroring the `mocapi.mrtr.secret` ephemeral-key warning):
  task state is process-local — not multi-node safe, and tasks die on restart; production
  clusters should provide a shared `TaskStore` bean.
- **`TaskStoreContractTest`** ships as a test-jar TCK: an abstract test class asserting
  create-durability/collision, atomic-mutation semantics under contention, terminal
  finality, TTL expiry, and version monotonicity — so any external implementation
  (user-written or the future Substrate adapter) can prove itself against the same bar the
  in-memory store passes.

**Considered and rejected:**

- *Substrate dependency in mocapi* — the adapter belongs on the Substrate side; mocapi adds
  zero dependencies. (Substrate's `AtomSpi` is already storage-only — the watch machinery is
  layered above and pay-per-use — so an adapter is straightforward there; adding a
  token-conditional write to `AtomSpi` is that project's internal hardening, not mocapi's
  concern.)
- *Hand-rolled JDBC/JPA/Redis stores in mocapi* — rebuilding Substrate's backend fleet
  in-tree; unbounded maintenance surface.
- *Event-sourced store* (append events, fold state) — deterministic conflict resolution,
  but fold/dedup/retention machinery is overkill for a five-state lifecycle; recorded as
  the fallback if tasks ever need real history (audit, forensics).
- *Client-echoed version numbers on `tasks/update`/`cancel`* — not spec-viable: the params
  are fixed, conforming clients send exactly them, and nothing obliges `_meta` echo; also
  wouldn't help the worst race (cancel vs. our own executing thread).

## 9. Wire contract

```jsonc
// tools/call (capable client, @McpTask tool) → CreateTaskResult
{ "resultType": "task", "taskId": "3J9…", "status": "working",
  "createdAt": "2026-08-02T14:00:00Z", "lastUpdatedAt": "2026-08-02T14:00:00Z",
  "ttlMs": 3600000, "pollIntervalMs": 2000 }

// tasks/get → working (statusMessage carries progress)
{ "resultType": "complete", "taskId": "3J9…", "status": "working",
  "statusMessage": "42/100: resizing…", "createdAt": "…", "lastUpdatedAt": "…",
  "ttlMs": 3600000, "pollIntervalMs": 2000 }

// tasks/get → input_required
{ "resultType": "complete", "taskId": "3J9…", "status": "input_required",
  "inputRequests": { "elicit-1": { "method": "elicitation/create",
                                   "params": { "message": "…", "requestedSchema": { … } } } },
  "createdAt": "…", "lastUpdatedAt": "…", "ttlMs": 3600000 }

// tasks/update params → {} ; tasks/cancel params {taskId} → {}   (both resultType:"complete")
{ "taskId": "3J9…", "inputResponses": { "elicit-1": { "action": "accept", "content": { … } } } }

// tasks/get → completed (isError tool failures land here too, per spec)
{ "resultType": "complete", "taskId": "3J9…", "status": "completed",
  "result": { "content": [ … ], "isError": false, "resultType": "complete" }, … }

// @McpTask(required = true) called by a non-capable client
{ "code": -32021, "message": "Missing required client capability",
  "data": { "requiredCapabilities": { "extensions": { "io.modelcontextprotocol/tasks": {} } } } }
```

HTTP: `tasks/*` requests carry `Mcp-Method: tasks/<op>` and `Mcp-Name: <taskId>`, validated
against the body (`-32020 HeaderMismatch` on mismatch), via seam #3.

## 10. Deployment topology (documented honestly)

- **Single node, in-memory store:** works out of the box; tasks die with the process (TTL
  guidance applies).
- **Multi-node:** requires a **shared `TaskStore`**. Plain hash-on-`Mcp-Name` load balancing
  does *not* provide create→poll affinity (the creating `tools/call` carries the tool name,
  the follow-up `tasks/get` carries the taskId — different hash keys), and the spec is
  explicitly silent on how intermediaries learn taskId→instance affinity. A response-aware
  routing tier or an instance-hint taskId prefix are possible without protocol changes
  (taskIds are opaque), but the supported v1 answer is the shared store.
- **Node death:** with a shared store, an `input_required` task is resumable by any node
  (the parked state is entirely in the record — an improvement over the 07-31 design). A
  task whose execution was mid-flight (`working`) is orphaned until TTL expiry marks it
  unusable; arbitrary Java compute is not checkpointable. Documented limitation, carried
  over from 07-31.

## 11. Governance artifacts (per the ADR rule)

This change hits four trigger buckets (new module; new SPI; capability change; I1 impact).
Implementation must produce, in the same PR stream:

- **ADR-0037 — mocapi-tasks and the task execution model:** the `@McpTask` surface, the
  decision rule, replay-through-store as the second MRTR carrier, the `TaskStore`
  atomic-mutation SPI, cancelled-sticks semantics, snapshot-carried guard re-evaluation,
  and the **scoped amendment to constitution I1** (core request model stateless; task state
  confined behind `TaskStore` in the opt-in module). Records the rejected alternatives from
  §8 plus the 07-31 blocking-`TaskContext` design.
- **ADR-0038 — the three `mocapi-server` seams:** `ToolCallReplayInvoker`
  extraction, `ToolCallDispatchCustomizer`, routing-header validation contribution.
- **Constitution:** I1 entry updated with the scoped exception + ADR link.
- **ADR-0022:** flip the Tasks entry to "Accepted — implemented in ADR-0037" (mirroring the
  Apps flip); update the Extensions entry (`extensions` map now enumerates tasks when the
  module is present); note `notifications/tasks` remains declined.
- **Design docs:** new `docs/design/tasks.md`; update `docs/design/elicitation-mrtr.md`
  (second carrier), `docs/design/handlers.md` (`@McpTask`), `docs/design/transports.md`
  (header-validation seam), `docs/design/extension-spi.md` (dispatch customizer);
  `docs/adr/README.md` index.
- **Guides:** a Tasks guide (annotation, idempotency contract restated, deployment topology,
  custom-store how-to + TCK usage); repoint the Apps spec's dangling tasks-spec link here.

## 12. Testing strategy

- **Refactor gate:** the `ToolCallReplayInvoker` extraction lands first; the existing MRTR
  unit/integration suites must pass unchanged before any task code builds on it.
- **Unit:** `InMemoryTaskStore` via the TCK; the engine's outcome/mutation matrix
  (single-resume under duplicate updates, cancel-vs-complete discard, progress-after-cancel
  no-op, ledger fingerprint mismatch → `failed`); decision-rule table (§6.2);
  `-32602`/`-32021` translation; header-validation contribution.
- **Integration (full Spring context, Streamable HTTP):** create → poll → complete; the
  `input_required` round trip via `tasks/update` (including a two-elicit tool = three
  executions); cancel mid-`working` and mid-`input_required`; sync degrade for non-capable
  clients; `required = true` → `-32003`; cross-principal `tasks/get` → `-32602`; TTL expiry.
- **Conformance:** wire the extension's scenarios into `mocapi-conformance`
  (`@modelcontextprotocol/conformance`), baseline anything covered by declared non-goals
  (notifications) in `conformance-expected-failures.yaml`.

## 13. Decisions locked

1. Opt-in: `@McpTask` on an unmodified tool; tool body is task-agnostic (transparency
   contract, §6.3).
2. Decision rule: capability → task; else sync; `required = true` → `-32003`.
3. Resume: MRTR replay through the store — no parked threads, ADR-0021 idempotency contract
   unchanged; `tasks/update` = merge + flip + spawn-iff-flipped.
4. One execution core (`ToolCallReplayInvoker`), two carriers (wire token, task store);
   extraction is behavior-preserving for the wire path.
5. Guards re-run every execution under a `ContextSnapshot` from the triggering request; no
   authorization asymmetry.
6. Progress emitters → `statusMessage`; monotonic validation unchanged.
7. Cancel: cancelled sticks; terminal states final; in-flight output discarded; no
   interruption.
8. `TaskStore`: atomic-mutation SPI, in-memory default, contract TCK; **no new
   dependencies**; Substrate adapter + token-CAS are Substrate-side follow-ups; JDBC/JPA
   in-tree stores, event sourcing, and client version echo rejected.
9. Errors: `failed` = JSON-RPC errors only; `isError` tools → `completed`; unknown/expired/
   foreign taskId → `-32602` indistinguishably.
10. v1 scope: polling only; `tools/call` only; elicitation-form-only inputRequests; finite
    TTLs (default PT1H, poll default PT2S, per-tool annotation overrides).
11. Extension model types live in `mocapi-tasks` (Apps precedent), not `mocapi-model`.
12. Multi-node = shared store; `Mcp-Name` affinity limits documented (§10).
