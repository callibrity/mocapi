# Substrate-Backed TaskStore (`mocapi-tasks-substrate`) — Design

**Date:** 2026-08-03
**Status:** Approved (brainstorming), pending spec review
**Topic:** A new `mocapi-tasks-substrate` module providing a distributed,
durable `TaskStore` implementation backed by Substrate 0.8.0 Atoms, closing
the "Substrate TaskStore adapter" follow-up deferred from the 1.2.0 MCP
Tasks work.

## Goal

Give clustered / durable mocapi deployments a drop-in `TaskStore` that works
across all nine Substrate backends (Redis, PostgreSQL, MongoDB, Cassandra,
DynamoDB, etcd, Hazelcast, NATS, RabbitMQ), using Substrate 0.8.0's new
token-based `Atom.compareAndSet` as the atomicity primitive. Adding the
module plus any Substrate backend to an app makes task state multi-node
safe and restart-durable with zero code.

## Context (current state)

- `mocapi-tasks` (shipped 1.2.0) defines the `TaskStore` SPI
  (`create` / `get` / `update(taskId, UnaryOperator<TaskRecord>)` /
  `delete`), an `InMemoryTaskStore` default, and a contract TCK
  (`TaskStoreContractTest`) published in the test-jar for external
  implementations.
- The 1.2.0 design deferred the Substrate adapter until Substrate grew CAS
  on Atoms. Substrate 0.8.0 (released) delivers exactly that:
  `Atom.compareAndSet(Snapshot expected, T data, Duration ttl)` with a
  documented retryable-`false` (lost race) vs. terminal-
  `AtomExpiredException` distinction, across all nine backends, plus TTL
  correctness fixes.
- `AtomFactory.create` throws `AtomAlreadyExistsException` on name
  collision — a 1:1 match for `TaskStore.create`'s
  `TaskAlreadyExistsException` contract. `AtomFactory.connect` returns a
  handle without I/O; `Atom.delete()` is probe-free and retry-safe.
- Substrate serializes atom values through the `org.jwcarman.codec`
  `CodecFactory` SPI; `codec-jackson` is Jackson 3 (`tools.jackson`), the
  same generation mocapi uses, and autoconfigures from the app's
  `ObjectMapper`. `TaskRecord` is explicitly documented as serializable for
  external stores.
- Substrate's `substrate-core` autoconfigures `AtomFactory` when an
  `AtomSpi` (from a backend module) and a `CodecFactory` are present.

## Decision summary (from brainstorming dialogue)

1. **Home repo: mocapi.** The adapter lives in the mocapi reactor as
   `mocapi-tasks-substrate`, superseding the 1.2.0-era "adapter is
   Substrate-side" note. Rationale: `TaskRecord` and the SPI evolve with
   mocapi releases, so the adapter stays in lockstep with what it
   implements; it depends only on the stable `substrate-api` artifact;
   it reuses the TCK test-jar at the reactor version; and it ships via the
   existing BOM + release train (mocapi-prompts-mustache precedent).
2. **Substrate 0.8.0 as-is.** No new Substrate API is required. The
   read → mutate → `compareAndSet` retry loop lives in the adapter
   (~10 lines, the exact pattern the Atom javadoc prescribes).
3. **Test depth: TCK + one real backend.** Contract TCK against the
   in-memory `AtomSpi` (no Docker), plus one Testcontainers integration
   run against Redis. The other backends are Substrate's conformance
   responsibility.

## Rejected alternatives

- **`substrate-mocapi-tasks` module in the Substrate repo** — puts a
  mocapi-specific artifact in a general-purpose library and couples
  Substrate's release cadence to `TaskRecord`'s shape.
- **Standalone bridge repo** — cleanest dependency story, but a third
  release train and CI setup for one class plus autoconfig.
- **Generic `Atom.update(UnaryOperator, Duration)` helper in Substrate
  first** — blocks mocapi on a Substrate 0.9.0 release for one call site;
  the CAS loop is shorter than its own javadoc.
- **Split-record layout** (status in one atom, ledger elsewhere) — tasks
  are mostly single-writer with small ledgers; splitting breaks the "one
  record, one atomic unit" simplicity the SPI assumes.

## Design

### Module layout

New reactor module `mocapi-tasks-substrate`, added to the parent POM and
`mocapi-bom`.

- **Compile deps:** `mocapi-tasks`, `substrate-api`,
  `spring-boot-autoconfigure` (optional, per existing module conventions).
- **Test deps:** `mocapi-tasks` test-jar (TCK), `substrate-core`
  (in-memory `AtomSpi` + `DefaultAtomFactory`), `codec-jackson`,
  Testcontainers + Redis backend module for the integration leg.
- The consuming app picks its backend module (`substrate-redis`,
  `substrate-postgresql`, …) and gets `AtomFactory` from Substrate's own
  autoconfiguration; mocapi never depends on a concrete backend.

### `SubstrateTaskStore` semantics

One `Atom<TaskRecord>` per task, keyed `<prefix><taskId>`. Prefix is
configurable (`mocapi.tasks.substrate.key-prefix`, default
`mocapi:tasks:`).

- **`create(rec)`** — compute `remaining = createdAt + ttl − now`. If
  `remaining ≤ 0` the record is dead on arrival: skip the backend write
  entirely (any subsequent `get` would return empty regardless, satisfying
  the SPI's durability clause vacuously). Otherwise
  `atomFactory.create(key, TaskRecord.class, rec, remaining)`. On
  `AtomAlreadyExistsException`: connect and read the incumbent — if it is
  expired by record semantics, purge (`delete`) and retry the create
  **once**; otherwise throw `TaskAlreadyExistsException`.
- **`get(taskId)`** — `connect` → `get()` → if
  `isExpired(clock.instant())`, delete the atom and return empty →
  otherwise return the value. `AtomNotFoundException` /
  `AtomExpiredException` → `Optional.empty()`.
- **`update(taskId, mutation)`** — `connect` → `get()` snapshot → expired
  check (purge + empty, as in `get`) → apply the mutation →
  `compareAndSet(snapshot, mutated, remaining)`. On `false` (lost race):
  re-read, re-check expiry, re-apply, retry — an unbounded optimistic
  loop; the `TaskStore` contract already licenses invoking the mutation
  more than once. Terminal exceptions map to `Optional.empty()`.
- **`delete(taskId)`** — `connect(key).delete()`; probe-free and
  retry-safe per Substrate's contract, matching the SPI's idempotency
  requirement.

### TTL model

`TaskRecord` expiry is **absolute** (`createdAt + ttl`, fixed at
creation); an Atom's TTL is a **lease that resets on every write**. The
adapter reconciles the two by passing *remaining* time
(`createdAt + ttl − now`, by the adapter's injected `Clock`) on every
write, so the backend lease never outlives the original absolute
deadline.

- Backend TTL expiry is **garbage collection**; the adapter's
  `isExpired` check on every read/update is the **correctness gate**.
  This also makes the TCK's fake-clock expiry tests pass on any backend,
  including ones whose server-side clocks the test cannot advance.
- Substrate's configurable `TtlBounds` maximum must be ≥ the largest
  `@McpTask` ttl in the app; documented in the user guide.

### Serialization

Values ride through Substrate's `CodecFactory`. With `codec-jackson`
that is `tools.jackson` — mocapi's Jackson generation — and
`DefaultAtomFactory` encodes to `byte[]` even on the in-memory `AtomSpi`,
so the unit-test path genuinely exercises the byte round-trip. A
dedicated test round-trips a maximally populated `TaskRecord` (ledger
entries, input requests, a result with content blocks, error detail,
`JsonNode` arguments). If any wire type turns out to require mocapi's
mapper customizations, the guide documents wiring the app's
`ObjectMapper` into `codec-jackson`.

### Autoconfiguration + native image

> **Amended 2026-08-03 (James, during implementation):** the autoconfiguration
> lives in `mocapi-autoconfigure` — the repo's centralized wiring module —
> NOT in `mocapi-tasks-substrate` as originally written below. This matches
> the established pattern (`MocapiTasksAutoConfiguration` precedent: hints
> are extension-owned, autoconfiguration is centralized), and reverses the
> awkward `mocapi-tasks-substrate → mocapi-autoconfigure` optional edge into
> the standard `mocapi-autoconfigure --optional--> mocapi-tasks-substrate`
> direction. `mocapi-autoconfigure` gains optional `substrate-api` +
> `mocapi-tasks-substrate` deps; the entry joins the central
> `AutoConfiguration.imports`; `before = MocapiTasksAutoConfiguration.class`
> becomes a same-package typed reference. The class-level guard widens to
> `@ConditionalOnClass({AtomFactory, TaskExecutionEngine, SubstrateTaskStore})`
> so the autoconfig backs off unless BOTH substrate-api and the adapter
> module are on the classpath.
>
> **Second amendment (same day, after live verification):** Substrate 0.8.0's
> `SubstrateAutoConfiguration` evaluates its `@ConditionalOnBean(CodecFactory)`
> factory-bean conditions before `JacksonCodecAutoConfiguration` registers its
> bean (no upstream `@AutoConfigureAfter`; confirmed via condition-evaluation
> report in the live example). mocapi resurrects its pre-clean-break fix — an
> ordering-only, zero-bean `SubstrateOrderingAutoConfiguration`
> (`beforeName` substrate, `afterName` codec-jackson, both string-referenced) —
> in `mocapi-autoconfigure`, plus a full-chain activation test. The proper fix
> (substrate declaring the ordering itself) is filed for a future substrate
> 0.8.1 and the shim stays harmless after it.

`MocapiTasksSubstrateAutoConfiguration`:

- `@ConditionalOnClass(AtomFactory.class)`,
  `@ConditionalOnBean(AtomFactory.class)`,
  `@ConditionalOnMissingBean(TaskStore.class)`, ordered **before**
  `MocapiTasksAutoConfiguration` so the in-memory default backs off.
- A user-supplied `TaskStore` bean still wins over both.
- Logs which store is active at startup (mirroring the existing
  in-memory warning log).
- Properties: `mocapi.tasks.substrate.key-prefix` (default
  `mocapi:tasks:`).
- The module registers its **own** native-image hints for everything it
  serializes (`TaskRecord`, `ResponseLedgerEntry`, `InputRequest`, and
  reachable wire types), per the extension-modules-own-their-hints
  pattern. The plan includes an **empirical** native-image verification
  run — reasoned "no hints needed" has been wrong twice before.

### Testing

1. **Contract TCK** subclass against `DefaultAtomFactory` +
   `InMemoryAtomSpi` + `JacksonCodecFactory` — fast, no Docker; covers
   atomicity (800 contended increments), expiry purge, terminal-status
   finality, version monotonicity, idempotent delete.
2. **Round-trip richness test** — the maximally populated `TaskRecord`
   from the serialization section.
3. **Autoconfig tests** — activates with an `AtomFactory` bean; backs off
   to a user-supplied `TaskStore`; without Substrate on the classpath the
   `mocapi-tasks` in-memory default is untouched.
4. **Integration** — the full TCK via Testcontainers against Redis
   (fast startup, native TTL semantics).

### Governance

- New module ⇒ **ADR** under `docs/adr/NNNN-substrate-taskstore.md` +
  index entry, per the architecturally-significant-change rule.
- `docs/design/tasks.md` gains the substrate-store section;
  `docs/guides/tasks.md` gains an enablement how-to.
- CHANGELOG headline for **1.3.0** (minor — new feature, no breaking
  surface).
- Closes the "Substrate TaskStore adapter" follow-up from the 1.2.0
  notes.

## Out of scope

- Atom **subscriptions** (watch/push) — the tasks extension currently
  polls; no push seam exists in mocapi-tasks. Revisit if/when the spec
  grows task-status notifications.
- Any Substrate or codec library changes.
- Additional backend integration suites beyond Redis.
- Multi-node create→poll affinity (unchanged limitation, documented in
  the 1.2.0 design: multi-node = shared TaskStore).

## Success criteria

- `mocapi-tasks-substrate` passes the full `TaskStoreContractTest` TCK on
  the in-memory SPI **and** on Redis via Testcontainers.
- A maximally populated `TaskRecord` round-trips byte-identical in
  semantics (field-by-field equality) through `codec-jackson`.
- Adding the module + a Substrate backend to `examples/tasks` swaps the
  active store with zero app-code changes (autoconfig log line proves it).
- `mvn verify` green across the reactor; zero new SonarCloud issues;
  native-image verification run passes.
