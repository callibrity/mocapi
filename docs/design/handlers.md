# Handlers

Each MCP capability in mocapi is dispatched through a concrete "handler"
class — one per request kind — built at startup from annotated Spring
beans. Handlers are internal to mocapi-server; application code doesn't
see them directly.

## CallToolHandler — `tools/call`

Every `@McpTool`-annotated method on a Spring bean produces
one `CallToolHandler`. The handler bundles the generated `Tool`
descriptor (name, title, description, input/output schemas) with a
`MethodInvoker<JsonNode>` that adapts `tools/call` arguments to the
method signature. `McpToolsService` holds a `Map<String,
CallToolHandler>` keyed by tool name and looks up the handler on every
call before dispatching.

Handlers are built by mapping each `(bean, method)` pair from the
central `HandlerMethodsCache` through `CallToolHandlers.build(...)` in
`MocapiServerToolsAutoConfiguration`. There is no separate SPI
interface for tool registration — the annotation scan is the only
supported path.

## GetPromptHandler — `prompts/get`

Every `@McpPrompt`-annotated method on a Spring bean
produces one `GetPromptHandler`. The handler bundles the generated
`Prompt` descriptor (name, title, description, argument list) with a
`MethodInvoker<Map<String, String>>` that converts the client-supplied
string arguments to the declared parameter types, plus the list of
`CompletionCandidate`s derived from enum-typed or
`@Schema(allowableValues=...)` parameters. `McpPromptsService` holds a
`Map<String, GetPromptHandler>` keyed by prompt name and looks up the
handler on every `prompts/get` call before dispatching.

Handlers are built by mapping each `(bean, method)` pair from the
central `HandlerMethodsCache` through `GetPromptHandlers.build(...)` in
`MocapiServerPromptsAutoConfiguration`. The same bean method walks
every handler's `completionCandidates()` and registers them with
`McpCompletionsService`, so `completion/complete` keeps working for
prompt arguments.

## ReadResourceHandler — `resources/read` (fixed URIs)

Every `@McpResource`-annotated method on a Spring bean
produces one `ReadResourceHandler`. The handler bundles the generated
`Resource` descriptor (URI, name, description, MIME type) with a
`ResourceReader` (a `Supplier<ReadResourceResult>`) that produces the
result returned by `resources/read` (ADR-0035). For an annotation-scanned
method the reader wraps the method's `MethodInvoker<Object>` and adapts
its return value: a `ReadResourceResult` passes through, while a `String`/
`CharSequence`, `byte[]`/`ByteBuffer`, or Spring `Resource` is wrapped
against the descriptor's URI and MIME type by `ResourceResults` (the
`@McpResource(content=…)` enum disambiguates a `Resource` as text or
blob). `McpResourcesService` holds a `Map<String, ReadResourceHandler>`
keyed by URI and looks up the handler on every fixed-URI `resources/read`
call before dispatching.

The reader indirection means a handler need not be method-backed at all:
a reader-only constructor takes `(descriptor, guards, reader)` with no
`Method`/`MethodInvoker`, which is how contributed (non-scanned) resources
are served — see *Resource contributors* below.

Handlers are built by mapping each `(bean, method)` pair from the
central `HandlerMethodsCache` through `ReadResourceHandlers.build(...)`
in the `AnnotationScanResourceContributor`.

## ReadResourceTemplateHandler — `resources/read` (templated URIs)

Every `@McpResourceTemplate`-annotated method on a `@ResourceService`
bean produces one `ReadResourceTemplateHandler`. The handler bundles
the generated `ResourceTemplate` descriptor (URI template, name,
description, MIME type) with a `MethodInvoker<Map<String, String>>`
that converts the URI's resolved path variables to the declared
parameter types, plus the list of `CompletionCandidate`s derived from
enum-typed or `@Schema(allowableValues=...)` variables.
`McpResourcesService` holds a `Map<UriTemplate,
ReadResourceTemplateHandler>` and matches an incoming `resources/read`
URI against the templates after the fixed-URI map lookup misses.

Handlers are built by mapping each `(bean, method)` pair from the
central `HandlerMethodsCache` through
`ReadResourceTemplateHandlers.build(...)` in the
`AnnotationScanResourceContributor`. Template methods keep returning
`ReadResourceResult` (they resolve their own concrete URI, so the
convenience return types are a fixed-URI `@McpResource` feature). The
resources autoconfiguration walks every registered template handler's
`completionCandidates()` and registers them with `McpCompletionsService`,
so `completion/complete` keeps working for resource-template variables.

## Resource contributors — merging registrations (ADR-0035)

`McpResourcesService` is built once, at construction, by merging the
handlers from every `ResourceContributor` bean:

```java
public interface ResourceContributor {
  default List<ReadResourceHandler> resources()                { return List.of(); }
  default List<ReadResourceTemplateHandler> resourceTemplates() { return List.of(); }
}
```

The `@McpResource`/`@McpResourceTemplate` annotation scan is itself the
primary, built-in contributor (`AnnotationScanResourceContributor`) — not
a privileged path, just one contributor among peers. An extension supplies
another: MCP Apps' serve-mode (`AppUiResourceContributor`, ADR-0036)
contributes reader-only handlers that serve `ui://` bundles from a fixed
location. The service stays immutable; registration is construction-time
only, with no runtime mutation. Duplicate URIs across contributors fail
fast at construction.

Guards and observability are *not* generalized onto contributed readers.
A method-backed reader keeps its baked-in `MethodInvoker` strata
(correlation/observation/audit + guard enforcement); a contributed
reader-only handler carries an empty `guards` list (so it is public and
visible in `listResources`) and a bare reader with no interceptors. A
resource that needs guards, observability, or logic is declared as a
`@McpResource`/`@McpAppResource` **method** and reached by reference — the
deliberate escape hatch.

## No public handler SPI

After the 170–174 cleanup series, mocapi has no public handler-SPI
interfaces at all. Tools, prompts, resources, and resource templates
are all annotation-driven; each internal representation is a single
concrete class built once at startup. There is no SPI users
implement — only annotations.

## Meta-annotation composition (ADR-0032)

Discovery is meta-annotation aware. `HandlerMethodsCache` detects
handler methods with `MergedAnnotations`, and every attribute read
(`HandlerKind`, the `*Handlers.build` factories) uses
`AnnotatedElementUtils.findMergedAnnotation`, so a **composed**
annotation that is itself meta-annotated with `@McpTool` / `@McpPrompt`
/ `@McpResource` / `@McpResourceTemplate` is discovered under its
meta-annotation, with `@AliasFor` attribute overrides resolved. The four
handler annotations carry `@Target({METHOD, ANNOTATION_TYPE})` so they
may be used as meta-annotations. This lets optional modules ship an
ergonomic single annotation — e.g. `@McpAppResource` (mocapi-apps),
meta-annotated `@McpResource` with the `ui://` MIME type defaulted and
`uri` aliased through — that registers through the existing scan with no
bespoke registration SPI. Directly-annotated handlers are unaffected
(merged detection is a strict superset of raw detection).

## Descriptor customizers (ADR-0034)

Each handler's generated `Tool`/`Resource` descriptor is passed through
a post-processing pass after it is otherwise built: `CallToolHandlers.build`
applies every registered `List<ToolDescriptorCustomizer>` to the `Tool`,
and `ReadResourceHandlers.build` applies every registered
`List<ResourceDescriptorCustomizer>` to the `Resource`. Each customizer
receives the handler's source `Method` alongside the descriptor, so it
can read annotations off it to decide what to write. This lets an
optional module enrich a descriptor's `_meta` — e.g. `mocapi-apps`
reads `@McpUi`/`@McpAppResource` and writes `_meta.ui` — without core
knowing what the enrichment means; core only offers "run every
registered customizer over this descriptor before it's published."
Descriptors with no customizers registered are unaffected (`_meta` stays
absent, `NON_NULL`), so the change is wire-additive.

## Handler context injection

Tool, prompt, and resource handler methods may declare a context parameter
for mid-execution communication with the client. The contexts form one
hierarchy (ADR-0025): `MrtrContext` (`McpElicitor` + `McpProgressSource` +
`handlerName()`) is the shared base of the three leaf types
`McpToolContext`, `McpPromptContext`, and `McpResourceContext`. The leaves
exist only for the three MRTR-capable methods (`tools/call`, `prompts/get`,
`resources/read`), making the spec's "interaction only on these three"
boundary a compile-time fact.

Each of the three services builds a context per request
(`AbstractMrtrContext` subclass, capturing the request's transport,
`_meta` progress token, and exchange) and binds it to both its leaf
`CURRENT` `ScopedValue` and `McpElicitor.CURRENT` around the handler
invocation. The handler factories register the matching structural
resolvers (the `ScopedValueResolver` pattern): the leaf-context resolver
first, then `McpElicitorResolver`, so a handler can declare its full leaf
context or a bare `McpElicitor` for elicitation alone. Progress flows
through the captured transport as `notifications/progress`; elicitation
routes through the MRTR engine (see
[elicitation-mrtr.md](elicitation-mrtr.md)).

## Interceptor chain

Since spec 175 (Methodical 0.6), every handler's reflective invocation
runs through a `MethodInterceptor` chain. Interceptors attach
per-handler via the `*HandlerCustomizer` SPI (spec 180) — a customizer
bean receives each handler's `*HandlerConfig` at build time and calls
one of the per-stratum mutators (`correlationInterceptor`,
`observationInterceptor`, `auditInterceptor`, `validationInterceptor`,
`invocationInterceptor`) to contribute an interceptor to the kind of
concern it represents. The builder assembles the chain in a fixed
outer-to-inner order; see [extension-spi.md](extension-spi.md#the-six-strata) for
the full stratum story. The customizer path gives per-handler
metadata (descriptor, method, bean, annotations) and supports
conditional attachment, which is why mocapi no longer autowires bare
`MethodInterceptor<? super T>` beans at the handler layer.

Tools get one built-in interceptor: `InputSchemaValidatingInterceptor`
is appended innermost per `CallToolHandler`, validating the incoming
`JsonNode` against the compiled input schema and throwing
`JsonRpcException(-32602)` on a mismatch. Because that exception
propagates out of the invoker and into `McpToolsService.invokeTool`'s
generic `catch (Exception)`, it surfaces to the client as
`CallToolResult { isError: true }` — matching the MCP spec's "input
validation errors belong in the result body so the LLM can
self-correct" guidance.

A minimal timing interceptor wired via a customizer looks like:

```java
public class ToolTimingInterceptor implements MethodInterceptor<JsonNode> {
  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    long start = System.nanoTime();
    try {
      return invocation.proceed();
    } finally {
      long elapsedMicros = (System.nanoTime() - start) / 1_000;
      log.info("tool {} took {}µs", invocation.method().getName(), elapsedMicros);
    }
  }
}

@Bean
CallToolHandlerCustomizer toolTimingCustomizer() {
  return config -> config.observationInterceptor(new ToolTimingInterceptor());
}
```

Returning such a customizer bean is enough to have the interceptor
wrap every `CallToolHandler`. Output-schema validation is *not* wired
in — the output schema is descriptive metadata only, and mocapi trusts
handlers to produce conformant results.
