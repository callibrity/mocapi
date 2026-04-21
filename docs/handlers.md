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
`MethodInvoker<Object>` that produces the `ReadResourceResult` returned
by `resources/read`. `McpResourcesService` holds a `Map<String,
ReadResourceHandler>` keyed by URI and looks up the handler on every
fixed-URI `resources/read` call before dispatching.

Handlers are built by mapping each `(bean, method)` pair from the
central `HandlerMethodsCache` through `ReadResourceHandlers.build(...)`
in `MocapiServerResourcesAutoConfiguration`.

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
`ReadResourceTemplateHandlers.build(...)` in
`MocapiServerResourcesAutoConfiguration`. The same bean method walks
every handler's `completionCandidates()` and registers them with
`McpCompletionsService`, so `completion/complete` keeps working for
resource-template variables.

## No public handler SPI

After the 170–174 cleanup series, mocapi has no public handler-SPI
interfaces at all. Tools, prompts, resources, and resource templates
are all annotation-driven; each internal representation is a single
concrete class built once at startup. There is no SPI users
implement — only annotations.

## Interceptor chain

Since spec 175 (Methodical 0.6), every handler's reflective invocation
runs through a `MethodInterceptor` chain. Interceptors attach
per-handler via the `*HandlerCustomizer` SPI (spec 180) — a customizer
bean receives each handler's `*HandlerConfig` at build time and calls
one of the per-stratum mutators (`correlationInterceptor`,
`observationInterceptor`, `auditInterceptor`, `validationInterceptor`,
`invocationInterceptor`) to contribute an interceptor to the kind of
concern it represents. The builder assembles the chain in a fixed
outer-to-inner order; see [customizers.md](customizers.md#strata) for
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
