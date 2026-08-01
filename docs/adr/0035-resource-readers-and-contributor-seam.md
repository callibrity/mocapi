# ADR-0035 — Function-backed resource readers and a `ResourceContributor` seam

- **Status:** Accepted
- **Date:** 2026-08-01

## Context

Resource registration is entirely annotation-scan-based
([ADR-0010](0010-annotation-driven-handler-discovery.md)): every
`@McpResource` / `@McpResourceTemplate` method becomes a
`ReadResourceHandler` / `ReadResourceTemplateHandler`, and those are
`final` classes whose `read()` is a reflective `MethodInvoker.invoke`
over the annotated method. `McpResourcesService` is built once from that
fixed list. There is no way to register a resource that is *not* a
scanned method — which blocks serving a file with minimal boilerplate,
letting an optional module contribute resources, or generating a
resource programmatically.

The motivating driver is MCP Apps: an author wants to serve a `ui://`
HTML bundle from a file and have a tool's `@McpUi` link to it, ideally
without hand-writing a `ReadResourceResult`-returning method. Working
that through surfaced hard constraints:

- **A catch-all `ui://{path}` template is rejected** — it turns the
  client-supplied request URI into a filesystem locator (path traversal /
  local-file inclusion). The template variable is untrusted input, not a
  resource identifier.
- **Core must stay ignorant of the extension.** `mocapi-server` and the
  core resources autoconfiguration must know nothing about `@McpUi`,
  `ui://`, or "Apps".
- **No runtime registration.** All resources are known at startup;
  post-startup mutation (and its concurrency machinery) is unwanted.
- Authors want file/text/binary returns without building a
  `ReadResourceResult` by hand.

## Decision

Invert the resource handler around a **reader function**, and make
registration flow through a single generic contributor seam.

**Function-backed handlers.** Introduce strongly-typed functional
interfaces in `mocapi-server`:

```java
@FunctionalInterface public interface ResourceReader { ReadResourceResult read(); }
@FunctionalInterface public interface ResourceTemplateReader { ReadResourceResult read(Map<String,String> vars); }
```

`ReadResourceHandler` / `ReadResourceTemplateHandler` carry
`(descriptor, guards, reader)`. The reflective form is **one** reader
adapter — `() -> (ReadResourceResult) invoker.invoke(...)` — that keeps
the method's `MethodInvoker` (and thus its interceptor/guard chain).
New reader kinds are just other implementations; no reflection required.

**One registration mechanism — the `ResourceContributor` seam.**

```java
public interface ResourceContributor {
  default List<ReadResourceHandler> resources()                { return List.of(); }
  default List<ReadResourceTemplateHandler> resourceTemplates() { return List.of(); }
}
```

`McpResourcesService` is built **once, at construction**, by merging the
handlers from *every* `ResourceContributor` bean. The **annotation scan
is itself the primary, built-in contributor** — not a privileged path;
it is one contributor among peers. The service stays **immutable**;
there is **no runtime registration** (see Consequences).

**Author-friendly return types**, implemented as readers. A resource
method may return `ReadResourceResult` / `String` / `CharSequence` /
`byte[]` / `ByteBuffer` / `Resource`:

- `String` / `CharSequence` → text; `byte[]` / `ByteBuffer` → blob
  (`ByteBuffer` read via `duplicate()`, non-destructive).
- `Resource` → text or blob via a **`content` enum on `@McpResource`** —
  `AUTO` (default) uses the declared `mimeType` (base type `text/*`, or
  subtype `json`/`xml`/`javascript`/`ecmascript` incl. `+json`/`+xml` →
  text; else blob; blank/unknown → blob), else `TEXT`/`BLOB` forced.
  Text charset is UTF-8 (honoring a mime `charset` param if present); a
  malformed mime degrades to blob rather than failing the read.
- `ReadResourceResult` remains the full-control escape hatch.

**Layering — core is extension-blind.** `mocapi-server` defines the
readers, the `ResourceContributor` SPI, and the return-type conversion;
`MocapiServerResourcesAutoConfiguration` collects
`List<ResourceContributor>` generically. An extension (e.g. MCP Apps'
`@McpUi(resource=…)` serve mode — a **separate** decision built on this)
contributes from its own layer, reusing the existing scan to find its
declarations. No Apps/UI concept crosses the core line.

**Guards and o11y stay with the method form.** `guards` remains a plain
field on the handler, general to all readers, and the service keeps
filtering `listResources` by it (empty guards → visible). But
enforcement and o11y are *not* generalized: method-backed readers keep
their baked-in `MethodInvoker` strata (correlation / observation / audit
+ guard enforcement); contributed readers carry an empty `guards` list
(public) and a bare reader with no interceptors. Nothing is lifted into
the service, and no `AnnotatedElement` guard-source abstraction is
introduced — both would solve problems we do not have (guarded/observed
*non-method* resources). The escape hatch is deliberate and already
exists: a resource that needs guards, o11y, or logic is declared as a
**method** (`@McpResource` / `@McpAppResource`) and reached by reference,
which routes it through the full chain. Serve-mode contributed resources
are the lightweight, public path by design.

## Consequences

Registering any new resource — a file, a module-contributed one, a
generated one — becomes "hand the service a reader through a
contributor." The annotation scanner is demystified into one contributor
among peers, which is a cleaner mental model. Authors get file/text/
binary returns with near-zero boilerplate, and the return-type mapping is
general (not Apps-specific). The core remains ignorant of every
extension; the Apps boundary sits entirely above the core line. Because
everything is construction-time, the service stays immutable with no
concurrency surface.

Costs: `ReadResourceHandler` / `ReadResourceTemplateHandler` change from
method-bound to reader-backed (touching the build path and the o11y/guard
integration of the method form); a new `ResourceContributor` SPI and its
collection; a return-type converter. Bounded, but a real change to a
constitution-guarded subsystem.

Rejected / deferred: the catch-all `ui://{path}` template (LFI risk);
runtime `register(...)` with copy-on-write registries (no current use
case — the design keeps the maps private so it *could* be added later
without API churn, but it is not built). Non-goal: per-request dynamic
content beyond what a reader already gives (a reader is invoked per read,
so dynamic content is inherent to the model).

**Code anchors:**

- `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ResourceReader.java`, `ResourceTemplateReader.java`, `ResourceContributor.java`
- `mocapi-server/.../resources/ReadResourceHandler.java`, `ReadResourceTemplateHandler.java` (reader-backed), `ReadResourceHandlers.java` / `ReadResourceTemplateHandlers.java` (method → reader adapter + return-type conversion)
- `mocapi-server/.../resources/McpResourcesService.java` (construction-time merge of contributors)
- `mocapi-autoconfigure/.../MocapiServerResourcesAutoConfiguration.java` (scan-as-contributor + `List<ResourceContributor>` collection)
- `mocapi-api/.../resources/McpResource.java` (`content` enum) + the enum type
