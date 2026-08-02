# ADR-0037 — `mocapi-tasks`: the MCP Tasks extension and its execution model

- **Status:** Accepted
- **Date:** 2026-08-02

## Context

MCP Tasks (SEP-2663, `modelcontextprotocol/ext-tasks`) was declared
declined in [ADR-0022](0022-2026-07-28-features-not-implemented.md):
extensions are opt-in, and the task lifecycle looked like a substantial
state machine mocapi's synchronous handler model didn't need. A
2026-07-31 design (commit `309d01fa`, unmerged branch
`feat/mcp-tasks-extension-design`) revisited that and proposed an
**explicit** task API: a task-eligible tool declared a `TaskContext`
parameter, returned `CreateTaskResult`, wrapped its body in
`task.submit(...)`, and called `task.elicit(...)`, which **blocked the
background thread** parked on the store. Task tools were task-only —
`-32003` for non-capable clients, no synchronous degrade.

That design reintroduced exactly the parked-continuation liabilities
[ADR-0021](0021-mrtr-elicitation-replay.md) rejected for wire
elicitation: a blocked thread that dies with its node, no idempotency
contract, and a bespoke `task.elicit(...)` API duplicating what MRTR
replay already does. Meanwhile [ADR-0031](0031-server-capabilities-customizer.md)
(`ServerCapabilitiesCustomizer`) and the MCP Apps precedent
([ADR-0033](0033-mcp-apps-module-and-ui-capability.md)) established that
an optional module can add capability and behavior with zero changes to
a stateless core — the seam the 07-31 design anticipated has since
shipped. This ADR supersedes the 07-31 design's surface and the Tasks
entry in ADR-0022.

## Decision

Add an optional module, `mocapi-tasks`, implementing the extension's
polling model over `tools/call` only.

1. **Opt-in is one annotation.** `@McpTask` on an unmodified `@McpTool`
   method is the entire task-enabling surface; the tool body never knows
   whether it is running as a task (transparency contract).
2. **Decision rule:** no `@McpTask` → always synchronous. `@McpTask` +
   client declares the `tasks` capability → `CreateTaskResult`, body runs
   as a task. `@McpTask` + non-capable client → synchronous execution
   (progressive enhancement) unless `required = true`, which instead
   rejects with the spec's `MissingRequiredClientCapabilityError`.
3. **Resume is MRTR replay through the store, not a parked thread.**
   `tasks/update` merges `inputResponses` into the ledger and, iff the
   mutation itself observed `input_required` with outstanding keys
   consumed, flips to `working` and spawns the next execution. A
   duplicate concurrent `tasks/update` observes `working`, consumes
   nothing, spawns nothing — single-resume falls out of the store's
   atomicity instead of a check-then-act race. The
   [ADR-0021](0021-mrtr-elicitation-replay.md) idempotency contract
   ("code before your last `elicit()` re-executes once per round trip")
   applies unchanged; only the ledger's carrier differs (store vs. wire
   token) — see [ADR-0038](0038-server-seams-for-extensions.md) for the
   shared execution core this depends on.
4. **Guards re-run every execution under a fresh `ContextSnapshot`**
   captured from the triggering dispatch thread (`tools/call` for
   execution #1, `tasks/update` for #2+). Both trigger sites are live,
   authenticated requests from the bound principal, so the full
   six-stratum chain — guards included — evaluates under a legitimate
   security context every time. No authorization special case, and
   resume works cross-node because auth arrives with each triggering
   request rather than being stored.
5. **Progress routes to `statusMessage`.** `notifications/progress` and
   `notifications/message` are not supported on tasks; a task's
   `McpProgressSource` writes a formatted `statusMessage` (`"42/100:
   resizing…"`) via store mutation instead. Monotonicity validation
   ([ADR-0025](0025-progress-emitters-and-mrtr-context.md)) runs
   identically in both modes.
6. **Cancel sticks; terminal states are final.** `tasks/cancel`
   atomically flips any non-terminal status to `cancelled`; an in-flight
   execution is not interrupted (consistent with the [ADR-0022](0022-2026-07-28-features-not-implemented.md)
   cancellation stance) — its output lands after the record is already
   terminal and is discarded by construction (`TaskRecord`'s transition
   helpers no-op once terminal).
7. **Errors:** `failed` is reserved for JSON-RPC-level errors
   (engine-internal failure, e.g. a replay ledger fingerprint mismatch —
   mapped to `-32602`, not `-32603`, since it's the same idempotency
   violation the wire carrier rejects with `-32602`). A tool-level error
   (`CallToolResult.isError = true`) surfaces as `completed` with the
   error in `result`, per the spec. Unknown, expired, or foreign-principal
   `taskId` all report the identical `-32602` "Unknown task" — no
   existence leak.
8. **`TaskStore` SPI: atomic-mutation contract, in-memory default, no
   new dependencies.** `create`/`get`/`update` (atomic
   `UnaryOperator<TaskRecord>` mutation)/`delete`; all engine semantics
   (single-resume, terminal finality, no-resurrection) derive from
   decisions made inside mutations and from what the returned record
   shows actually happened. `InMemoryTaskStore` (`ConcurrentHashMap`,
   lazy + swept TTL expiry) is the shipped default,
   `@ConditionalOnMissingBean(TaskStore.class)`, and logs a prominent WARN
   when it activates (mirroring the `mocapi.mrtr.secret` ephemeral-key
   warning): task state is then process-local, not multi-node safe, and
   dies on restart. `TaskStoreContractTest` ships in a test-jar so any
   external implementation — user-written or a future Substrate adapter —
   proves itself against the same bar.
9. **v1 scope:** polling only (no `notifications/tasks`, no
   `subscriptions/listen` — mocapi declines the latter globally per
   [ADR-0022](0022-2026-07-28-features-not-implemented.md)); `tools/call`
   only; elicitation form-mode `inputRequests` only; finite TTLs (default
   `PT1H`, poll default `PT2S`, both overridable per-tool via the
   annotation or globally via `mocapi.tasks.default-ttl` /
   `mocapi.tasks.default-poll-interval`).
10. **Extension model types live in `mocapi-tasks`**, not
    `mocapi-model` — [I7](../constitution.md#i7--model-is-11-with-the-mcp-schema)
    scopes the model to the core `schema.ts`, and the Apps precedent
    (ADR-0033) already established module-local extension types.
11. **Multi-node deployment requires a shared `TaskStore`.** Plain
    hash-on-`Mcp-Name` load balancing does not provide create→poll
    affinity (the creating `tools/call` hashes on tool name, the
    follow-up `tasks/get` hashes on `taskId`), and the spec is silent on
    how intermediaries learn taskId→instance affinity. The supported v1
    answer is a shared store; see [`docs/design/tasks.md`](../design/tasks.md#deployment-topology)
    for the full documented limitation.
12. **Constitution I1 gains a scoped exception:** core's request model
    stays stateless; `mocapi-tasks` confines all task state behind the
    `TaskStore` SPI inside the opt-in module (updated in
    [`constitution.md`](../constitution.md) in this change).

### Error code: `-32021`, not the extension draft's `-32003`

The extension's draft spec text still says a server that cannot serve a
request without a task returns `-32003`. That is stale: `-32003` sits in
mocapi's own implementation-defined sub-range
([I9](../constitution.md#i9--error-code-allocation)), and the core
2026-07-28 registry defines `MissingRequiredClientCapabilityErrorData.CODE`
as `-32021` for exactly this "client lacks a required capability" case —
the same translator mocapi already ships for elicitation
([ADR-0021](0021-mrtr-elicitation-replay.md)). mocapi follows the core
registry: `@McpTask(required = true)` against a non-capable client is
translated to `-32021` by `TaskRequiredExceptionTranslator`. This choice
is to be exercised against the `@modelcontextprotocol/conformance` suite
in the follow-up conformance-wiring task; if the suite disagrees, that
task reopens this call, not this ADR.

## Rejected alternatives

- **The 07-31 blocking-`TaskContext` design** (see Context) — parked
  threads reintroduce exactly the liabilities MRTR replay was adopted to
  avoid: no cross-node resume, no idempotency contract, and a bespoke
  elicitation API duplicating `ctx.elicit(...)`.
- **Substrate dependency in mocapi** — a `TaskStore` adapter belongs on
  the Substrate side (its `AtomSpi` is already storage-only); mocapi adds
  zero new dependencies for tasks.
- **Hand-rolled JDBC/JPA/Redis stores in-tree** — rebuilding a backend
  fleet mocapi has no business maintaining; users bring their own bean.
- **Event-sourced store** (append events, fold state) — deterministic
  conflict resolution, but the fold/dedup/retention machinery is
  overkill for a five-state lifecycle. Recorded as the fallback if tasks
  ever need real history (audit, forensics).
- **Client-echoed version numbers on `tasks/update`/`cancel`** — not
  spec-viable (the params are fixed; nothing obliges an echo) and
  wouldn't help the worst race anyway (cancel vs. the task's own
  executing thread).

## Consequences

**What this buys us.** A tool author adds one annotation and gets
dual-mode execution for free — the same handler body serves capable and
non-capable clients. The replay core, idempotency contract, and
context-injection model are shared with wire MRTR, so there is exactly
one place ordinals, fingerprints, and error mapping can drift. Core
mocapi is unaffected when the module is absent: no descriptor gains
task metadata, and the three seams in
[ADR-0038](0038-server-seams-for-extensions.md) sit inert.

**Costs.** Multi-node deployments must supply a shared `TaskStore`;
the in-memory default is single-node-only and calls that out loudly. A
`working` execution whose node dies is orphaned until TTL expiry —
arbitrary Java compute is not checkpointable, so this is a documented
limitation, not a bug. `-32003` vs. `-32021` needs conformance
verification (above).

**Non-goals.** `notifications/tasks` push, task-augmenting `prompts/get`
/ `resources/read`, sampling/roots `inputRequests`, URL-mode
elicitation, `pollIntervalMs`-based rate limiting, shipped cluster-store
adapters, and mid-execution thread interruption on cancel are all out of
scope for v1.

This ADR flips the Tasks entry in
[ADR-0022](0022-2026-07-28-features-not-implemented.md) from declined to
accepted-and-implemented.

**Code anchors:**

- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/McpTask.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/McpTasksService.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TaskToolCallDispatcher.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/TaskStore.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/InMemoryTaskStore.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/engine/TaskExecutionEngine.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/TaskRequiredExceptionTranslator.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/MocapiTasksAutoConfiguration.java`
