# ADR-0037 — `mocapi-tasks`: the MCP Tasks extension and its execution model

- **Status:** Amended by ADR-0040
- **Date:** 2026-08-02

## Context

MCP Tasks (SEP-2663, `modelcontextprotocol/ext-tasks`) was declared
declined in [ADR-0022](0022-2026-07-28-features-not-implemented.md):
extensions are opt-in, and the task lifecycle looked like a substantial
state machine mocapi's synchronous handler model didn't need. A
2026-07-31 design (commit `309d01fa`, unmerged branch
`feat/mcp-tasks-extension-design`) revisited that and proposed an
**explicit** task API: a task-eligible tool declared a `TaskContext`
parameter, returned `CreateTaskResult`, and called `task.elicit(...)`,
which **blocked the background thread** parked on the store. Task tools
were task-only — `-32003` for non-capable clients, no synchronous
degrade. That reintroduced exactly the parked-continuation liabilities
[ADR-0021](0021-mrtr-elicitation-replay.md) rejected for wire
elicitation: a thread that dies with its node, no idempotency contract,
and a bespoke elicit API duplicating what MRTR replay already does.

Meanwhile [ADR-0031](0031-server-capabilities-customizer.md)
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
   unless `required = true`, which rejects instead of degrading.
3. **Resume is MRTR replay through the store, not a parked thread.**
   `tasks/update` merges `inputResponses` into the ledger and, iff the
   mutation itself observed `input_required` with outstanding keys
   consumed, flips to `working` and spawns the next execution — a
   duplicate concurrent `tasks/update` observes `working` and spawns
   nothing. Single-resume falls out of the store's atomicity, not a
   check-then-act race. The [ADR-0021](0021-mrtr-elicitation-replay.md)
   idempotency contract applies unchanged; only the ledger's carrier
   differs — see [ADR-0038](0038-server-seams-for-extensions.md) for the
   shared execution core this depends on.
4. **Guards re-run every execution under a fresh `ContextSnapshot`**
   captured from the triggering dispatch thread (`tools/call` for
   execution #1, `tasks/update` for #2+). Both are live, authenticated
   requests from the bound principal, so the six-stratum chain — guards
   included — evaluates under a legitimate security context every time.
   No authorization special case, and resume works cross-node because
   auth arrives with each triggering request rather than being stored.
5. **Progress routes to `statusMessage`**, not
   `notifications/progress`/`notifications/message` — a task's
   `McpProgressSource` writes a formatted string via store mutation.
   Monotonicity validation ([ADR-0025](0025-progress-emitters-and-mrtr-context.md))
   runs identically in both modes.
6. **Cancel sticks; terminal states are final.** `tasks/cancel`
   atomically flips any non-terminal status to `cancelled`; an in-flight
   execution is not interrupted (consistent with the
   [ADR-0022](0022-2026-07-28-features-not-implemented.md) cancellation
   stance) — its output is discarded by construction once the record is
   terminal (`TaskRecord`'s transition helpers no-op).
7. **Errors:** `failed` is reserved for JSON-RPC-level errors (engine
   failure, e.g. a replay ledger fingerprint mismatch → `-32602`, the
   same code the wire carrier uses for the identical idempotency
   violation). A tool-level error (`isError = true`) surfaces as
   `completed` with the error in `result`, per spec. Unknown, expired, or
   foreign-principal `taskId` all report the identical `-32602` "Unknown
   task" — no existence leak.
8. **`TaskStore` SPI: atomic-mutation contract, in-memory default, no
   new dependencies.** All engine semantics (single-resume, terminal
   finality, no-resurrection) derive from decisions made inside
   `update`'s mutation and from what the returned record shows actually
   happened. `InMemoryTaskStore` is the shipped default
   (`@ConditionalOnMissingBean`) and logs a prominent WARN when it
   activates (mirroring the `mocapi.mrtr.secret` warning): process-local,
   not multi-node safe, dies on restart. `TaskStoreContractTest` ships in
   a test-jar so any external implementation proves itself against the
   same bar.
9. **v1 scope:** polling only (no `notifications/tasks`, no
   `subscriptions/listen` — declined globally per
   [ADR-0022](0022-2026-07-28-features-not-implemented.md)); `tools/call`
   only; elicitation form-mode only; finite TTLs (default `PT1H`, poll
   default `PT2S`, overridable per-tool or via `mocapi.tasks.default-*`).
10. **Extension model types live in `mocapi-tasks`**, not
    `mocapi-model` — [I7](../constitution.md#i7--model-is-11-with-the-mcp-schema)
    scopes the model to the core `schema.ts`, and ADR-0033 already
    established module-local extension types.
11. **Multi-node deployment requires a shared `TaskStore`.** Plain
    hash-on-`Mcp-Name` load balancing does not give create→poll affinity
    (creating `tools/call` hashes on tool name; the follow-up `tasks/get`
    hashes on `taskId`), and the spec is silent on taskId→instance
    affinity. See [`docs/design/tasks.md`](../design/tasks.md#deployment-topology).
12. **Constitution I1 gains a scoped exception:** core's request model
    stays stateless; `mocapi-tasks` confines all task state behind the
    `TaskStore` SPI (updated in [`constitution.md`](../constitution.md)).

### Error code: `-32021`, not the extension draft's `-32003`

The extension's draft text still says `-32003` for a server that cannot
serve a request without a task. That's stale: `-32003` sits in mocapi's
own implementation-defined sub-range
([I9](../constitution.md#i9--error-code-allocation)), while the core
2026-07-28 registry defines `MissingRequiredClientCapabilityErrorData.CODE`
as `-32021` for exactly this case — the same translator elicitation
already uses. mocapi follows the core registry:
`TaskRequiredExceptionTranslator` emits `-32021`.

**Conformance verification (2026-08-02):** exercised against
`@modelcontextprotocol/conformance@0.2.0-alpha.10`'s `tasks-required-task-error`
and `tasks-capability-negotiation` scenarios (run individually with
`--scenario <name> --force` — the suite's 10 tasks scenarios are tagged
`[extension]` and excluded from `--suite all` regardless of
`--spec-version`; see `mocapi-conformance/README.md`). The suite confirms
`-32021` is correct: both scenarios assert `code === -32021` explicitly and
both pass. This closes the open question — mocapi's choice to follow the
core registry over the extension draft's stale `-32003` stands.
Reconciling the full tasks-scenario run surfaced two real bugs, both fixed
in `mocapi-tasks`: (1) `tasks/get`/`update`/`cancel` had no capability
check at all, so a non-capable caller got `-32602` "Unknown task" instead
of `-32021`, fixed in `McpTasksService.requireTaskCapable`; (2) a
malformed `tasks/update` `inputResponses` entry failed Jackson's
deduction-based `InputResponse` typing and errored the *whole* request
instead of being ignored per SEP-2322's SHOULD — fixed by typing
`UpdateTaskParams.inputResponses` as `Map<String, JsonNode>` and
converting each outstanding entry leniently in `McpTasksService`, with no
`mocapi-model` change. Two architectural v1-scope limitations remain
waived in `conformance-expected-failures.yaml` — simultaneous multi-key
`inputRequests` (`tasks-mrtr-input:tasks-mrtr-partial-fulfillment`; see
the non-goals below) and `tasks-mrtr-composition`'s
MRTR-then-escalate-to-task shape — see the conformance README for detail.

## Rejected alternatives

- **The 07-31 blocking-`TaskContext` design** — parked threads
  reintroduce exactly the liabilities MRTR replay was adopted to avoid.
- **Substrate dependency in mocapi** — a `TaskStore` adapter belongs on
  the Substrate side; mocapi adds zero new dependencies for tasks.

  > **Amended ([ADR-0040](0040-substrate-taskstore-adapter.md),
  > 2026-08-03):** Reversed. Substrate 0.8.0 shipped token
  > `compareAndSet` across all nine backends, which is exactly the
  > primitive `TaskStore.update`'s atomicity contract needs; with that
  > primitive available, mocapi ships the adapter itself
  > (`mocapi-tasks-substrate`) rather than asking Substrate to depend on
  > mocapi's `TaskRecord`/`TaskStore` types instead.
- **Hand-rolled JDBC/JPA/Redis stores in-tree** — rebuilding a backend
  fleet mocapi has no business maintaining; users bring their own bean.
- **Event-sourced store** — deterministic conflict resolution, but the
  fold/dedup/retention machinery is overkill for a five-state lifecycle;
  recorded as the fallback if tasks ever need real history.
- **Client-echoed version numbers on `tasks/update`/`cancel`** — not
  spec-viable (params are fixed) and wouldn't help the worst race anyway
  (cancel vs. the task's own executing thread).

## Consequences

**What this buys us.** A tool author adds one annotation and gets
dual-mode execution for free. The replay core, idempotency contract, and
context-injection model are shared with wire MRTR, so there is exactly
one place ordinals, fingerprints, and error mapping can drift. Core
mocapi is unaffected when the module is absent — see
[ADR-0038](0038-server-seams-for-extensions.md) for the seams it sits on.

**Costs.** Multi-node deployments must supply a shared `TaskStore`. A
`working` execution whose node dies is orphaned until TTL expiry —
arbitrary Java compute is not checkpointable, a documented limitation,
not a bug. `-32021` is confirmed correct against the conformance suite
(above); `-32003` was never emitted.

**Non-goals.** `notifications/tasks` push, task-augmenting
`prompts/get`/`resources/read`, sampling/roots `inputRequests`, URL-mode
elicitation, `pollIntervalMs`-based rate limiting, shipped cluster-store
adapters, and mid-execution thread interruption on cancel are out of
scope for v1. So is more than one simultaneously-pending `inputRequests`
key per task: the replay engine's single-pending-key-per-round model
(decision 3, ADR-0021) surfaces at most one outstanding input-required
exception per execution by construction — confirmed against the
conformance suite's `tasks-mrtr-input:tasks-mrtr-partial-fulfillment`
check (waived in `conformance-expected-failures.yaml`), not a bug.

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
