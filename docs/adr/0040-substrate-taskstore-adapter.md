# ADR-0040 — Substrate-backed TaskStore lives in mocapi as mocapi-tasks-substrate

- **Status:** Accepted
- **Date:** 2026-08-03

## Context

[ADR-0037](0037-mcp-tasks-extension.md) shipped `mocapi-tasks` in 1.2.0
with a `TaskStore` SPI and an `InMemoryTaskStore` default, and explicitly
deferred a distributed store: its rejected-alternatives list ruled out a
Substrate dependency in mocapi, reasoning that "a `TaskStore` adapter
belongs on the Substrate side; mocapi adds zero new dependencies for
tasks" — Substrate had no token-conditioned compare-and-set at the time,
so an adapter built against it couldn't satisfy `TaskStore.update`'s
atomicity contract without a race between the read and the write.

Substrate 0.8.0 closed that gap: every one of its nine backends now
implements token `compareAndSet` on `Atom<T>` (`Atom.get()` returns a
`Snapshot<T>` carrying an opaque token; `compareAndSet(snapshot, next,
ttl)` succeeds only if the backend's current token still matches). That
removes the reason the adapter was pushed to Substrate's side of the
fence: `TaskStore.update`'s single atomic-mutation-step contract now maps
directly onto Substrate's own primitive, with the retry loop mocapi
already writes for exactly this shape of contract elsewhere.

Building the adapter in mocapi rather than in Substrate keeps
`mocapi-tasks`' `TaskRecord`/`TaskStore` types as the single source of
truth for the mutation contract (terminal finality, ledger merge,
version monotonicity) instead of asking Substrate to depend on mocapi
model types it has no other reason to know about.

## Decision

The adapter is a mocapi reactor module, `mocapi-tasks-substrate`,
depending on `mocapi-tasks` and `substrate-api` only — no
`substrate-core`, no backend module, no codec module on its main
classpath (all three are test-scoped, used to prove the contract against
real backends).

**Layout: one `Atom<TaskRecord>` per task**, keyed
`<key-prefix><taskId>` (default prefix `mocapi:tasks:`, configurable via
`mocapi.tasks.substrate.key-prefix`). `SubstrateTaskStore.create` calls
`AtomFactory.create`; `get`/`update`/`delete` connect via
`AtomFactory.connect` and operate on the returned `Atom` handle.

**`update` is an optimistic CAS loop**: read the current `Snapshot`,
apply the mutation, `compareAndSet(snapshot, mutated, ttl)`; a lost race
re-reads and retries — permitted by `TaskStore`'s contract, which
requires the mutation be deterministic and side-effect-free for exactly
this reason.

**Remaining-TTL mapping, with the adapter-side `isExpired` check as the
sole correctness gate.** A `TaskRecord`'s deadline is absolute
(`createdAt + ttl`); a Substrate Atom's TTL is a lease that resets on
every write. Every write computes `remaining = createdAt + ttl - now` and
passes that to the backend as its lease, so the backend lease never
outlives the record's real deadline; backend expiry is garbage
collection only, never the liveness decision. `remaining` is clamped to
a 1ms floor when it has reached or passed zero — Substrate requires a
positive TTL, and the record has already failed the `isExpired` check by
that point regardless of what lease value is written. **Correctness
lives entirely in `TaskRecord.isExpired(clock.instant())`, checked on
every `get`/`update`/`create`-retry** — this is a correction from this
task's original spec, which called for skipping backend-TTL writes once
remaining time reached zero; that reading conflicted with `TaskStore`'s
own durability contract at the exact-deadline boundary (a record must
still be readable up to and including the instant it expires), so
`isExpired` alone — not a "remaining ≤ 0" shortcut — governs. Eager
purges (on `get`, `update`, and a `create` collision retry) are
best-effort: the Atom SPI has no token-conditioned delete, so a purge can
race a concurrent re-create of the same key. This is safe because
`isExpired` remains the sole authority — a purge is cleanup, not a
correctness mechanism.

**Self-registered autoconfiguration, centralized in
`mocapi-autoconfigure`.** `MocapiTasksSubstrateAutoConfiguration` and its
companion `MocapiTasksSubstrateProperties` live in
`mocapi-autoconfigure` under package `com.callibrity.mocapi.tasks` (not
in `mocapi-tasks-substrate` itself), guarded
`@ConditionalOnClass({AtomFactory.class, TaskExecutionEngine.class,
SubstrateTaskStore.class})` and, on the bean itself,
`@ConditionalOnBean(AtomFactory.class)` +
`@ConditionalOnMissingBean(TaskStore.class)`, and declared `before =
MocapiTasksAutoConfiguration.class` so it wins the `TaskStore` slot
before the in-memory default gets a chance to register — while a
user-supplied `TaskStore` bean still beats both.
`mocapi-tasks-substrate` itself is left a thin, focused leaf: it owns
only `SubstrateTaskStore` and its native hints
(`SubstrateTaskStoreRuntimeHints`), and depends on nothing beyond
`mocapi-tasks` + `substrate-api`. `mocapi-autoconfigure` in turn declares
optional Maven dependencies on `substrate-api` and
`mocapi-tasks-substrate`, matching the existing pattern for every other
`@ConditionalOnClass`-gated integration in that module.

**`SubstrateOrderingAutoConfiguration`: an ordering shim, resurrected.**
Substrate 0.8.0's factory beans (`AtomFactory`, `Notifier`, …) are
`@ConditionalOnBean(CodecFactory.class)`, but Substrate declares no
`@AutoConfigureAfter`/`@AutoConfigureBefore` against the codec
auto-configurations (`codec-jackson`'s `JacksonCodecAutoConfiguration`,
`codec-gson`'s `GsonCodecAutoConfiguration`, `codec-protobuf`'s
`ProtobufCodecAutoConfiguration`). Left alone, Spring Boot's default
auto-configuration ordering can evaluate Substrate's conditional beans
before the codec auto-configuration in use registers its `CodecFactory`
bean, silently backing off the entire Substrate chain — and with it,
`MocapiTasksSubstrateAutoConfiguration`'s `AtomFactory` condition, which
would silently fall back to the in-memory store with no error. This is
the identical failure mode the pre-clean-break codebase hit and fixed
with an empty, ordering-only `@AutoConfiguration` class wedging itself
between the two (`SubstrateOrderingAutoConfiguration`, commit
`770d00fd`, deleted along with the rest of the old `mocapi-protocol`
module in the 2026-07-28 clean break, ADR-0019). This ADR resurrects
that identical class in `mocapi-autoconfigure`
(`beforeName = "...SubstrateAutoConfiguration"`, `afterName` listing all
three codec auto-configurations, all referenced by name so neither
`substrate-core` nor any `codec-*` module is a compile dependency of
`mocapi-autoconfigure`). The proper fix landed upstream in Substrate
0.8.1 (`SubstrateAutoConfiguration` now declares `@AutoConfigureAfter`
on the codec auto-configurations itself), which is mocapi's baseline;
the shim is retained as harmless defense-in-depth for applications that
pin Substrate 0.8.0 (an ordering declaration between two classes that no
longer need one is a no-op).

## Consequences

mocapi gains one optional third-party compile dependency —
`substrate-api` — confined to one leaf module (`mocapi-tasks-substrate`)
and to `mocapi-autoconfigure`'s optional dependency set; nothing in
`mocapi-tasks` or `mocapi-server` depends on Substrate.

A deployment enabling this store must configure its chosen Substrate
backend's `TtlBounds` maximum to be at least as large as the largest
`@McpTask` TTL in use; a task whose remaining time exceeds the backend's
allowed maximum TTL will fail to write its lease. This is a Substrate
backend concern, not something `mocapi-tasks-substrate` can enforce
itself — the adapter only computes and passes the remaining duration.

**Non-goals.** No `Atom` subscription/push integration — the Tasks
extension has no push seam for mocapi to bridge into (see
[ADR-0022](0022-2026-07-28-features-not-implemented.md) on
`notifications/tasks`); this store is poll-only, same as the in-memory
default. No integration tests beyond Redis — the adapter is verified
against Substrate's Atom SPI contract (TCK on the in-memory Atom SPI,
full TCK against real Redis) rather than against every one of
Substrate's nine backends individually; the SPI contract, not the
backend, is what this module depends on.

**Verification.** `TaskStoreContractTest` (the same TCK
`InMemoryTaskStore` proves itself against) runs against
`SubstrateTaskStore` twice: once on Substrate's in-memory `AtomSpi`
(`SubstrateTaskStoreTest`, still round-tripping through
`codec-jackson` bytes so serialization is genuinely exercised) and once
against a real Redis via Testcontainers
(`RedisSubstrateTaskStoreIT`, Failsafe `*IT` naming). Lease-clamping
behavior at and around the exact-deadline boundary is covered by a
dedicated unit test (`SubstrateTaskStoreLeaseTest`).
`MocapiTasksSubstrateAutoConfigurationTest` proves the full
autoconfiguration chain — `JacksonCodecAutoConfiguration` →
`SubstrateOrderingAutoConfiguration` → `SubstrateAutoConfiguration` →
`MocapiTasksSubstrateAutoConfiguration` — activates
`SubstrateTaskStore`, plus the `@ConditionalOnMissingBean`/
`@ConditionalOnBean` back-off matrix and the key-prefix property.
`examples/tasks`' `substrate` Maven profile swaps `InMemoryTaskStore`
for `SubstrateTaskStore` with zero application-code changes, verified
end-to-end for both a plain task (`batch_resize`) and an
elicitation-bearing task (`confirmed_report`, exercising
`working → input_required → tasks/update → completed` through the
store) on a live JVM and on a genuine GraalVM native image (Cloud
Native Buildpacks) — the activation log line present, the in-memory
WARN absent, in both.

**A pre-existing bug surfaced during that verification, fixed alongside
this module (not scoped to Substrate specifically).**
`PrimitiveSchemaDefinition` (`mocapi-model`) had no deserialization
routing at all — every serializing `TaskStore` (this one included) broke
`tasks/get` with `-32603` for any task whose `inputRequests` held a
pending elicitation's `requestedSchema`. Fixed with
`PrimitiveSchemaDefinitionDeserializer`, a
shape-sniffing router covering all eight sealed-hierarchy leaves per
`docs/plans/2026-07-28-schema.ts` (including the documented
`LegacyTitledEnumSchema`-without-`enumNames`-vs-`UntitledSingleSelectEnumSchema`
wire ambiguity, resolved toward the non-deprecated variant, since the two
are genuinely indistinguishable on the wire). Wire serialization is
unchanged; the released 1.2.0 wire path was never affected, because
`RequestStateCodec` only ever carries response ledgers, not schema
definitions. Discovering this only after `mocapi-tasks-substrate` forced
every store to serialize is itself evidence for `InMemoryTaskStore`'s
companion fix below.

**Amends.** [ADR-0037](0037-mcp-tasks-extension.md)'s rejected
alternative "a `TaskStore` adapter belongs on the Substrate side" is
reversed by this ADR now that Substrate 0.8.0 supplies the CAS
primitive that alternative was waiting on; see the amendment note at
that site in ADR-0037 itself. Every other decision in ADR-0037 stands
unchanged.

**`InMemoryTaskStore` now serializes too — parity with every external
store.** It previously held live `TaskRecord` object graphs in its
`ConcurrentHashMap`, which meant a wire-representation bug like the one
above could hide behind the shipped default indefinitely, only surfacing
against a real backing store. `InMemoryTaskStore` now round-trips every
record through JSON strings on write and read, closing that blind spot
and incidentally eliminating an aliasing hazard (a caller could
previously mutate a `JsonNode` inside a stored or returned record and
corrupt shared state). Its two public constructors are unchanged.

## Code anchors

- `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/SubstrateTaskStore.java`
- `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/aot/SubstrateTaskStoreRuntimeHints.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/MocapiTasksSubstrateAutoConfiguration.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/MocapiTasksSubstrateProperties.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/tasks/SubstrateOrderingAutoConfiguration.java`
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/PrimitiveSchemaDefinitionDeserializer.java`
- `mocapi-tasks/src/main/java/com/callibrity/mocapi/tasks/store/InMemoryTaskStore.java`
