# ADR-0034 — Descriptor `_meta` and descriptor-customizer seams

- **Status:** Amended by ADR-0039
- **Date:** 2026-07-31

## Context

`Tool` and `Resource` — the wire descriptors returned in `tools/list`
and `resources/list`/`resources/read` — carry no `_meta` field. The MCP
base protocol defines `_meta` as generic, extension-owned metadata on
every major object; mocapi's translation of `schema.ts`
([ADR-0014](0014-mocapi-model-from-schema-ts.md)) omitted it on these
two records, which is a latent base-protocol fidelity gap independent
of any specific extension.

MCP Apps ([ADR-0033](0033-mcp-apps-module-and-ui-capability.md)) is the
forcing case: it needs to stamp `_meta.ui` on a tool descriptor (linking
it to a `ui://` resource) and on a resource descriptor (CSP/sandbox),
without core learning what "ui" means. The alternative — teaching
`Tool`/`Resource` about Apps-specific fields, or giving every extension
its own descriptor subtype — would couple core to every optional
module and doesn't generalize to whatever the next extension needs to
attach. What's missing is a generic seam: a place in the descriptor
build path where a post-processing step can write arbitrary
extension-owned data into `_meta`, without core interpreting it.

## Decision

Add an optional `_meta` `ObjectNode` to the `Tool` and `Resource` model
records, and introduce two customizer interfaces that populate it after
the descriptor is otherwise built.

- **`_meta` on descriptors.** `Tool` and `Resource` gain an optional
  `_meta` field (`ObjectNode`, `NON_NULL` — omitted from the wire when
  absent), matching the base-protocol shape used elsewhere for `_meta`.
  This is a pure additive change to `mocapi-model`; existing descriptors
  without a customizer touching them serialize identically to before.
- **`ToolDescriptorCustomizer`.**
  ```java
  @FunctionalInterface
  public interface ToolDescriptorCustomizer {
    Tool customize(Method method, Tool descriptor);
  }
  ```
- **`ResourceDescriptorCustomizer`.**
  ```java
  @FunctionalInterface
  public interface ResourceDescriptorCustomizer {
    Resource customize(Method method, Resource descriptor);
  }
  ```
- **Application point.** `CallToolHandlers.build` applies every
  registered `ToolDescriptorCustomizer` to the generated `Tool` after
  the descriptor is otherwise complete; `ReadResourceHandlers.build`
  does the same with `ResourceDescriptorCustomizer` and `Resource`.
  Both receive the handler's source `Method`, so a customizer can read
  annotations off it (e.g. `mocapi-apps` reading `@McpUi`/
  `@McpAppResource`) to decide what to write. Core has no knowledge of
  what any customizer writes into `_meta` — it only offers "enrich this
  descriptor before it's published."

## Consequences

Any optional module can annotate a tool or resource descriptor without
a core code change per module — the same "customizer contributes,
core stays ignorant" philosophy as
[ADR-0031](0031-server-capabilities-customizer.md)'s
`ServerCapabilitiesCustomizer`. `mocapi-apps` is the first consumer
(writing `_meta.ui`); a future extension needing descriptor-level
metadata reuses this seam instead of inventing another one.

The wire change is additive and backward-compatible: `_meta` is
`NON_NULL`, so a `Tool`/`Resource` built with no customizers serializes
byte-for-byte as before. Cost: `CallToolHandlers.build` and
`ReadResourceHandlers.build` each take a new `List<...Customizer>`
parameter and an extra pass over the descriptor; this is startup-only
work with no hot-path impact, since descriptors are built once and
cached in the handler maps.

Non-goal: this does not add `_meta` customization to prompts or
resource templates — no current use case needs it there, and it can be
added the same way later if one appears. (Superseded on this point by
[ADR-0039](0039-extension-seam-taxonomy-and-dispatch-interception.md),
which adds `_meta` to both.) It also does not give customizers access
to runtime call state; they run once at descriptor-build time, not
per-request, matching mocapi's static-discovery model
([ADR-0010](0010-annotation-driven-handler-discovery.md)).

**Code anchors:**

- ~~`mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolDescriptorCustomizer.java`~~ (deleted, see amendment below)
- ~~`mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ResourceDescriptorCustomizer.java`~~ (deleted, see amendment below)
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/Tool.java` (`_meta`)
- `mocapi-model/src/main/java/com/callibrity/mocapi/model/Resource.java` (`_meta`)

> **Amended ([ADR-0039](0039-extension-seam-taxonomy-and-dispatch-interception.md),
> 2026-08-02):** `ToolDescriptorCustomizer` and `ResourceDescriptorCustomizer` are
> deleted. Descriptor mutation folds into the four existing `*HandlerCustomizer` SPIs
> (ADR-0011) instead of a second, descriptor-only SPI: every `*HandlerConfig` gains a
> `void descriptor(T)` mutator alongside its `T descriptor()` accessor, applied at the
> same build-pipeline point descriptor customizers used to run. `mocapi-apps`'s
> `AppsToolDescriptorCustomizer` / `AppsResourceDescriptorCustomizer` become
> `AppsToolUiMetaCustomizer` / `AppsResourceUiMetaCustomizer`. The `_meta`-on-descriptors
> decision above stands unchanged for `Tool`/`Resource`; ADR-0039 separately extends
> `_meta` to `Prompt`/`ResourceTemplate`, reversing this ADR's non-goal on that point
> (see ADR-0039's own decision record for the rationale).
