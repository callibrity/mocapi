# MCP Tasks

How mocapi implements the MCP Tasks extension
(`io.modelcontextprotocol/tasks`, SEP-2663,
`modelcontextprotocol/ext-tasks`) — the `mocapi-tasks` module, the
`@McpTask` annotation, and the replay-through-store execution model.

For decisions, see:

- [ADR-0037](../adr/0037-mcp-tasks-extension.md) — the `mocapi-tasks`
  module, the `@McpTask` surface, the execution model, and the scoped
  amendment to [I1](../constitution.md#i1--stateless-request-model)
- [ADR-0038](../adr/0038-server-seams-for-extensions.md) — the three
  `mocapi-server` seams (`ReplayExecutor` extraction,
  `ToolCallDispatchCustomizer`, `McpRoutedParamContributor`) plus
  `ProgressSink`, which this module is built on
- [ADR-0021](../adr/0021-mrtr-elicitation-replay.md) — the MRTR replay
  decision this module's resume model reuses
- [ADR-0031](../adr/0031-server-capabilities-customizer.md) —
  `ServerCapabilitiesCustomizer`, which `TasksCapabilityCustomizer` uses
  to declare the `tasks` extension capability

## The `@McpTask` surface

A tool author opts in with one annotation on an otherwise-ordinary
`@McpTool` method:

```java
@McpTool(description = "Re-encode a video")
@McpTask                                       // the entire task-enabling surface
public EncodeResult encode(String uri, McpToolContext ctx) { ... }
```

```java
public @interface McpTask {
  String ttl();           // ISO-8601 Duration; "" → mocapi.tasks.default-ttl (PT1H)
  String pollInterval();  // ISO-8601 Duration; "" → mocapi.tasks.default-poll-interval (PT2S)
  boolean required();     // true → non-capable clients get -32021 instead of sync execution
}
```

`ttl` and `pollInterval` are resolved through the same `${...}`
property-placeholder mechanism (`mcpAnnotationValueResolver`) as
`@McpTool`'s `name`/`title`/`description` before being parsed as ISO-8601
durations.

The tool body is written exactly as a normal tool and never knows
whether it is running as a task: `ctx.elicit(...)`, progress emitters,
guards, validation, parameter resolution, and `McpToolException`
semantics behave identically in both modes.

## The decision rule

| Tool | Client declared `tasks` capability? | Result |
|---|---|---|
| no `@McpTask` | either | normal synchronous tool (never a task) |
| `@McpTask` | yes | `CreateTaskResult`; body runs as a task |
| `@McpTask` | no | normal synchronous execution (progressive enhancement) |
| `@McpTask(required = true)` | no | `-32021` `MissingRequiredClientCapabilityError` |

`TaskToolCallDispatcher.isTaskCapable` reads the per-request
`_meta["io.modelcontextprotocol/clientCapabilities"].extensions["io.modelcontextprotocol/tasks"]`
entry; its mere presence (even `{}`) counts as capable, matching the
elicitation capability's gating rule
([elicitation-mrtr.md](elicitation-mrtr.md#capability-gating)).

`@McpTask(required = true)` against a non-capable client throws
`McpTaskRequiredException`, translated by `TaskRequiredExceptionTranslator`
to the spec's `MissingRequiredClientCapabilityError`: JSON-RPC **`-32021`**
with `data.requiredCapabilities = {"extensions": {"io.modelcontextprotocol/tasks": {}}}`.
The extension's own draft text still says `-32003` (stale — that code
sits in mocapi's own implementation-defined sub-range,
[I9](../constitution.md#i9--error-code-allocation)); mocapi follows the
core 2026-07-28 registry's `MissingRequiredClientCapabilityErrorData.CODE`,
the same translator elicitation already uses. See
[ADR-0037](../adr/0037-mcp-tasks-extension.md#error-code--32021-not-the-extension-drafts--32003)
for the full rationale and the conformance-verification follow-up.

## Replay-through-store: one core, two carriers

Mid-task elicitation is MRTR through an intermediary. `ctx.elicit(...)`
unwinds via the same `InputRequiredException` as wire MRTR
([elicitation-mrtr.md](elicitation-mrtr.md)), but the response ledger
lives in a `TaskStore` record keyed by `taskId` instead of an encrypted
`requestState` token. `tasks/update` re-executes the handler from the
top against the merged ledger.

This works because [ADR-0038](../adr/0038-server-seams-for-extensions.md)
extracted the replay mechanics out of `MrtrElicitationEngine` into
`ReplayExecutor` (ordinal cursor, fingerprint enforcement,
`InputRequiredException` conversion) and out of `McpToolsService` into
the public `ToolCallReplayInvoker` (handler lookup, context construction,
ScopedValue binding, the six-stratum chain, result/exception mapping).
`McpToolsService` itself implements `ToolCallReplayInvoker` — the wire
path and the task path both terminate in the identical execution core;
only the ledger's carrier differs:

| | Wire carrier (`tools/call` retry) | Task carrier (`tasks/update`) |
|---|---|---|
| Ledger lives in | encrypted `requestState` token | `TaskRecord.ledger()` in the `TaskStore` |
| Resume trigger | client retries the same `tools/call` | client calls `tasks/update` |
| Principal/target check | `RequestStateCodec` decrypt + verify | `McpTasksService.requireOwned` |

Ordinals, fingerprints, context binding, strata, and error mapping exist
exactly once, so the two carriers cannot drift semantically. The
[idempotency contract](elicitation-mrtr.md#the-idempotency-contract)
("code before your last `elicit()` re-executes once per round trip")
applies identically in both modes.

## `TaskStore` SPI

```java
public interface TaskStore {
  void create(TaskRecord record);            // MUST NOT return before get() would find it
  Optional<TaskRecord> get(String taskId);   // empty if unknown or expired
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);
                                             // atomic; mutation MUST be deterministic/side-effect-free
  void delete(String taskId);                // idempotent
}
```

**The contract is the atomicity of `update`.** The mutation function is
applied against the current record as one atomic step; implementations
use whatever the backend offers (`ConcurrentHashMap.compute`,
version-column optimistic locking with retry, conditional writes). Every
engine invariant — single-resume, terminal finality, no resurrection —
derives from decisions made *inside* mutations
(`TaskRecord.completed`/`inputRequired`/`failed`/`cancelled`/`withStatusMessage`
all no-op once the record is already terminal) and from what the
returned record shows actually happened. Because `update` may retry the
mutation optimistically, callers must not read wall-clock time or other
non-deterministic state from inside the lambda — `McpTasksService` and
`TaskExecutionEngine` both hoist `clock.instant()` before calling
`update`.

`TaskRecord` carries `taskId`, `toolName`, `arguments`, `principal`,
`clientCapabilities` (a snapshot of the triggering request's
declaration), `status`, `statusMessage`, `createdAt`, `lastUpdatedAt`,
`ttl`, `pollInterval`, `ledger` (the same `ResponseLedgerEntry` type MRTR
uses), `inputRequests`, `result`, `error`, and a monotonic `version`
field for stores that need an optimistic-locking handle.

### `InMemoryTaskStore` and the WARN

`InMemoryTaskStore` (a `ConcurrentHashMap<String, TaskRecord>`) is the
shipped default, wired `@ConditionalOnMissingBean(TaskStore.class)` in
`MocapiTasksAutoConfiguration`. Expired records are removed lazily on
`get`/`update` and proactively by a background virtual-thread sweeper
(`mocapi.tasks.sweep-interval`, default `PT1M`). Whenever this default
activates, mocapi logs a prominent `WARN` (mirroring the
`mocapi.mrtr.secret` ephemeral-key warning in
[elicitation-mrtr.md](elicitation-mrtr.md)):

> Using the in-memory TaskStore: task state is process-local — NOT
> multi-node safe, and in-flight tasks are lost on restart. Provide a
> shared TaskStore bean for clustered or durable deployments.

A production, multi-node deployment supplies its own `TaskStore` bean
(user-written, or a future Substrate-side adapter — outside mocapi's
dependency graph either way; see
[ADR-0037](../adr/0037-mcp-tasks-extension.md#rejected-alternatives)).

### The contract TCK

`TaskStoreContractTest` (in `mocapi-tasks`'s test-jar) is an abstract
test class asserting create-durability/collision, atomic-mutation
semantics under contention, terminal finality, TTL expiry, and version
monotonicity. `InMemoryTaskStore` is tested against it; any external
implementation extends it to prove the same bar. See the
[Tasks guide](../guides/tasks.md#writing-a-custom-taskstore) for the
how-to.

## Execution model

### Task creation (`tools/call`)

`TaskToolCallDispatcher` (a `ToolCallDispatchCustomizer`,
[ADR-0038](../adr/0038-server-seams-for-extensions.md)) claims the call
when the handler carries `@McpTask` and the client declared the `tasks`
capability. It mints a `taskId` (`TaskIds.newTaskId()`, spec-strength
entropy), builds a `TaskRecord` (status `WORKING`, the bound principal
from `McpPrincipalSource`, a snapshot of the request's declared client
capabilities, the arguments, resolved `ttl`/`pollInterval`, an empty
ledger), and hands it to `TaskExecutionEngine.createAndStart`, which
calls `store.create(record)` — durable before returning, per the spec's
MUST — spawns execution #1, and returns the `CreateTaskResult`.

### Executions: one routine, two trigger sites

Every execution is identical: run the tool from the top through
`ToolCallReplayInvoker.invoke` with the store-loaded ledger bound.
Execution #1 is triggered by `tools/call`; executions #2+ by
`tasks/update`. There is no parked thread — between executions the task
exists only as its `TaskRecord`.

Each execution runs on its own virtual thread, wrapped via
`ContextSnapshotFactory.captureAll()` — the same context-propagation
mechanism `StreamableHttpController.handleCall` uses for the synchronous
dispatch path ([transports.md](transports.md#thread-local-context-propagation)).
Both trigger sites are live, authenticated requests from the bound
principal, so the full six-stratum chain — **guards included** — re-runs
under a legitimate security context every time; there is no
authorization special case, and it works cross-node because auth arrives
with each triggering request rather than being stored.

Outcome handling, all via atomic store mutation:

- `Completed(result)` → `working → completed` with `result` (including
  `isError: true` tool failures — the spec's rule that tool-level errors
  are `completed`, not `failed`).
- `InputRequired(key, request, ledger)` → write the ledger, record the
  pending `inputRequests[key]`, `working → input_required`.
- `ElicitationLedgerMismatchException` (the handler violated the replay
  idempotency contract mid-task) → `failed` with a `-32602` JSON-RPC
  error detail.
- Any other exception → `failed` with a `-32603` internal error detail.
- Any outcome arriving after the record already went terminal (e.g.
  cancel won the race) is discarded — `TaskRecord`'s transition helpers
  no-op on a terminal record.

### Resume (`tasks/update`)

`McpTasksService.updateTask` looks up the task bound to the requesting
principal (`-32602` "Unknown task" on mismatch, unknown, or expired — no
existence leak), then atomically: merges `inputResponses` into the
ledger (answering only outstanding keys present in the request; unknown
or already-answered keys are ignored per the spec's SHOULD), and iff the
mutation itself observed `input_required` with keys consumed, flips to
`working`. Only the mutation that performed the flip spawns the next
execution (`engine.resume`) — a duplicate concurrent `tasks/update`
observes `working`, consumes nothing, spawns nothing. Single-resume
derives entirely from the store's atomicity, not a check-then-act race.
The ack (`{}`) returns immediately regardless (spec: eventually
consistent).

### Cancel (`tasks/cancel`)

`McpTasksService.cancelTask` atomically flips any non-terminal status to
`cancelled` and acks `{}`. Terminal states are final: an in-flight
execution is not interrupted — the work completes, its output is
discarded by the terminal no-op in `TaskRecord`'s transition helpers
(consistent with mocapi's general cancellation stance,
[ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)). A
cancel on an already-terminal task acks without effect.

### Progress → `statusMessage`

`TaskProgressSource.forTask` builds an `McpProgressSource` whose emits
write a formatted `statusMessage` via store mutation: `"<progress>/<total>:
<message>"` when a total is known, `"<progress>: <message>"` otherwise.
`notifications/progress` and `notifications/message` are not sent for
tasks — the spec routes status only through `tasks/get` /
`notifications/tasks` (the latter unimplemented, see
[ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)).
Monotonicity validation ([ADR-0025](../adr/0025-progress-emitters-and-mrtr-context.md))
runs identically to the wire path; a straggler progress write after
cancellation is a no-op against the terminal record.

### `tasks/get`

`McpTasksService.getTask` is a principal-checked read mapping the
record to the status-appropriate shape: `input_required` includes all
outstanding `inputRequests`; `completed` includes `result`; `failed`
includes `error`. Unknown, expired, or foreign-principal → the same
`-32602` "Unknown task".

## Deployment topology

- **Single node, in-memory store:** works out of the box; tasks die with
  the process (the WARN above explains the trade-off).
- **Multi-node:** requires a **shared `TaskStore`**. Plain
  hash-on-`Mcp-Name` load balancing does not provide create→poll
  affinity — the creating `tools/call` hashes on the tool name, the
  follow-up `tasks/get`/`tasks/update`/`tasks/cancel` hash on `taskId`,
  a different value. The extension spec is explicitly silent on how
  intermediaries learn taskId→instance affinity. A response-aware
  routing tier or an instance-hint `taskId` prefix are possible without
  protocol changes (taskIds are opaque to clients), but the supported v1
  answer is a shared store reachable from every node.
- **Node death:** with a shared store, an `input_required` task is
  resumable by any node — the parked state lives entirely in the
  `TaskRecord`. A task whose execution was mid-flight (`working`) when
  its node died is orphaned until TTL expiry marks it unusable;
  arbitrary Java compute is not checkpointable. This is a documented
  limitation, not a defect.

## Error table

| Condition | Code | Notes |
|---|---|---|
| `@McpTask(required = true)`, client lacks `tasks` capability | `-32021` | `MissingRequiredClientCapabilityError`; see the decision-rule section above |
| Unknown / expired / foreign-principal `taskId` (`tasks/get`, `tasks/update`, `tasks/cancel`) | `-32602` | identical message for all three causes — no existence leak |
| Replay ledger fingerprint mismatch mid-task | `-32602` | same idempotency-contract violation the wire carrier rejects with `-32602` |
| Other engine-internal failure during a task execution | `-32603` | `failed` status, diagnostic `statusMessage` |
| Tool-level error (`CallToolResult.isError = true`) | *(not a JSON-RPC error)* | surfaces as `completed` with the error in `result`, per spec |

## Module layout

```
mocapi-tasks    @McpTask, TaskStore SPI + TaskRecord, InMemoryTaskStore,
                task model types (Task, CreateTaskResult, GetTask*/UpdateTask*/CancelTask*),
                McpTasksService (3 @JsonRpcMethods), TaskExecutionEngine,
                TaskToolCallDispatcher, TasksCapabilityCustomizer,
                TasksRoutedParamContributor, TaskRequiredExceptionTranslator,
                TaskStoreContractTest (test-jar TCK)
     └─ depends on ─▶ mocapi-api, mocapi-server (the seams above)
```

`MocapiTasksAutoConfiguration` (in `mocapi-autoconfigure`, gated
`@ConditionalOnClass(TaskExecutionEngine.class)`, running `after`
`MocapiServerToolsAutoConfiguration`) registers every bean above,
`@ConditionalOnMissingBean` throughout so a deployment can override any
one of them (most commonly `TaskStore`). Adding `mocapi-tasks` to the
classpath is the only integration step; omitting it leaves core inert —
byte-for-byte the stateless server, all four seams unused.

## Testing

- `TaskStoreContractTest` (test-jar) — the `TaskStore` atomicity/
  terminal-finality TCK, run against `InMemoryTaskStore`; TTL expiry is
  covered here (`expired_record_is_purged_on_get`/`…_on_update`), not at
  the integration level.
- Engine unit tests — the outcome/mutation matrix (single-resume under
  duplicate updates, cancel-vs-complete discard, progress-after-cancel
  no-op, ledger fingerprint mismatch → `failed`, `JsonRpcException` →
  `failed` with its own error code preserved). Cancel mid-`working` is
  covered here too, via a latch race
  (`cancel_wins_race_discards_completed_output`): the invoker blocks on a
  `CountDownLatch` until the test cancels the record, proving the
  terminal write is discarded rather than by driving an actual
  concurrent HTTP request.
- Decision-rule and `-32021` translation unit tests.
- End-to-end tests run at the `JsonRpcDispatcher` level
  (`WebEnvironment.NONE`, no servlet container): create → poll →
  complete; the `input_required` round trip via `tasks/update`;
  synchronous degrade for non-capable clients; `required = true` →
  `-32021`; cross-principal `tasks/get` → `-32602`. These do not exercise
  Streamable HTTP itself — the transport surface (headers, SSE framing)
  is exercised externally by the `@modelcontextprotocol/conformance`
  suite's tasks scenarios (`mocapi-conformance`), not by an in-repo
  integration test.
