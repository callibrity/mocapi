# ADR-0032 — Handler discovery recognizes meta-annotations

- **Status:** Accepted
- **Date:** 2026-07-31

## Context

mocapi discovers handlers by scanning Spring beans for methods carrying
`@McpTool` / `@McpPrompt` / `@McpResource` / `@McpResourceTemplate`
([handlers design](../design/handlers.md); [ADR-0010](0010-annotation-driven-handler-discovery.md)/[ADR-0011](0011-customizer-spi-and-strata.md)).
The scan (`HandlerMethodsCache`) and every attribute read
(`HandlerKind`, the `*Handlers.build` methods) used *raw* reflection —
`Method.getAnnotation`, `Method.isAnnotationPresent`,
`MethodUtils.getMethodsListWithAnnotation` — which only sees an
annotation when it is placed **directly** on the method.

The planned MCP Apps module (`io.modelcontextprotocol/ui`) wants an
ergonomic single annotation, `@McpAppResource`, that both *registers* a
`ui://` resource and defaults its MIME type — i.e. a composed annotation
**meta-annotated** with `@McpResource`. Raw reflection cannot see
through a meta-annotation, so such a composed annotation would be
invisible to discovery. The alternatives were worse: a bespoke,
per-extension resource-registration SPI, or forcing authors to stack two
annotations. Making discovery meta-annotation aware is a single, general
capability that any future extension can reuse.

## Decision

Detect and read handler annotations through Spring's merged-annotation
model, and permit the handler annotations to be used as meta-annotations.

- **Detection** uses `MergedAnnotations.from(method).isPresent(type)` in
  `HandlerMethodsCache` (both the bean prefilter and the per-method
  grouping) and in `HandlerKind.of`. Merged detection is a strict
  superset of raw detection, so directly-annotated methods are still
  found.
- **Attribute reads** use
  `AnnotatedElementUtils.findMergedAnnotation(method, type)` in
  `HandlerKind.nameOf`, `CallToolHandlers.build`,
  `GetPromptHandlers.build`, `ReadResourceHandlers.build`, and
  `ReadResourceTemplateHandlers.build`. This resolves `@AliasFor`
  attribute overrides, so a composed annotation can alias, e.g., the
  resource `uri`.
- The four handler annotations gain `ElementType.ANNOTATION_TYPE` in
  their `@Target` (in addition to `METHOD`) so they may legally be used
  as meta-annotations. This is additive — method placement is unchanged.

The scanned annotation-type list is unchanged: a composed annotation is
discovered *under its meta-annotation* (e.g. `@McpAppResource` is found
when scanning for `@McpResource`), so no registration SPI is required.

## Consequences

Extensions can ship ergonomic composed annotations that register through
the existing scan with zero further core change — the Apps module needs
no resource-registration SPI. The capability is general (works for tool,
prompt, resource, and resource-template annotations alike) and is proven
end-to-end by a test that discovers a meta-annotated resource and
resolves an `@AliasFor` on `uri`.

Cost: discovery now depends on `spring-core`'s annotation model (already
on the classpath) rather than Commons Lang `MethodUtils`, and the scan
iterates `Class.getMethods()` filtering by merged presence. This is
startup-only work with no hot-path impact. The change is behavior-
preserving for all existing directly-annotated handlers (the full
handler/discovery/observability suites pass unchanged).

Non-goal: this does not change *which* annotations are handler
annotations, nor how handlers are built once discovered — only how the
existing annotations are detected and read.

**Code anchors:**

- `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/HandlerMethodsCache.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/handler/HandlerKind.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/{tools/CallToolHandlers,prompts/GetPromptHandlers,resources/ReadResourceHandlers,resources/ReadResourceTemplateHandlers}.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/{tools/McpTool,prompts/McpPrompt,resources/McpResource,resources/McpResourceTemplate}.java` (`@Target`)
- `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/server/autoconfigure/ResourceServiceAutoConfigurationTest.java` (`discovers_meta_annotated_resource_…`)
