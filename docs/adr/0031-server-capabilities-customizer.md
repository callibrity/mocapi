# ADR-0031 — Contribute declared capabilities via `ServerCapabilitiesCustomizer`

- **Status:** Accepted
- **Date:** 2026-07-31

## Context

mocapi advertises its `ServerCapabilities` in the `server/discover`
response ([ADR-0019](0019-clean-break-2026-07-28.md)/[ADR-0020](0020-stateless-request-model.md)).
Until now the capabilities object was built by a single autoconfigure
bean (`mcpServerCapabilities`) that hardcoded every member, including
an empty `extensions` map (SEP-2133 reverse-DNS extension identifiers).

The planned optional extension modules — MCP Tasks
(`io.modelcontextprotocol/tasks`) and MCP Apps
(`io.modelcontextprotocol/ui`) — each need to declare their capability
when present, without knowing about one another and without forcing the
core to enumerate them. The only prior escape hatch was replacing the
entire `ServerCapabilities` bean via `@ConditionalOnMissingBean`, which
is all-or-nothing: a module cannot add one extension entry without
owning the whole object, and two modules cannot both contribute.

## Decision

Introduce a `ServerCapabilitiesCustomizer` seam.

- `ServerCapabilities` gains a `Builder` (in `mocapi-model`) seeded with
  the historical hardcoded defaults; building with no customizations
  reproduces the previous object exactly.
- `ServerCapabilitiesCustomizer` (in `mocapi-server`, `discover`
  package) is a `@FunctionalInterface` with
  `void customize(ServerCapabilities.Builder)`.
- The default `mcpServerCapabilities` bean collects
  `List<ServerCapabilitiesCustomizer>`, applies each in bean order to a
  fresh builder, then builds. It remains `@ConditionalOnMissingBean`, so
  a deployment supplying its own `ServerCapabilities` bean still wins
  outright (customizers are not applied in that case — the override is
  authoritative).
- The canonical use is `caps.extension("io.modelcontextprotocol/…",
  configNode)`, but the builder also exposes the other members
  (`tools`, `resources`, `prompts`, `completions`, `experimental`,
  `logging`) for completeness.

## Consequences

Extension modules declare their capability with a one-line bean and no
core change per module — mocapi core never enumerates the extensions.
Both Tasks and Apps reuse this identical seam. The composition is
order-dependent only for same-id collisions (last write wins), which is
not a concern for distinct extension identifiers.

Cost: `ServerCapabilities` now carries builder machinery, and the
autoconfigure bean took a new parameter. The behavior for a stock server
(no customizers) is byte-for-byte identical to before, covered by a
regression test asserting the historical defaults.

Non-goal: this seam does not gate a capability on runtime/client state —
mocapi is stateless and declares its capabilities statically at startup
(a non-Apps/Tasks host simply ignores an extension it does not
understand).

**Code anchors:**

- `mocapi-model/src/main/java/com/callibrity/mocapi/model/ServerCapabilities.java` (`Builder`)
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/discover/ServerCapabilitiesCustomizer.java`
- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerAutoConfiguration.java` (`mcpServerCapabilities`)
- `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerAutoConfigurationTest.java`
