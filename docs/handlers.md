# Handlers

Each MCP capability in mocapi is dispatched through a concrete "handler"
class — one per request kind — built at startup from annotated Spring
beans. Handlers are internal to mocapi-server; application code doesn't
see them directly.

## CallToolHandler — `tools/call`

Every `@ToolMethod`-annotated method on a `@ToolService` bean produces
one `CallToolHandler`. The handler bundles the generated `Tool`
descriptor (name, title, description, input/output schemas) with a
`MethodInvoker<JsonNode>` that adapts `tools/call` arguments to the
method signature. `McpToolsService` holds a `Map<String,
CallToolHandler>` keyed by tool name and looks up the handler on every
call before dispatching.

Discovery lives in `CallToolHandlers#discover`, which is invoked once
during `McpToolsService` bean creation. There is no separate SPI
interface for tool registration — the annotation scan is the only
supported path.

## GetPromptHandler — `prompts/get`

Every `@PromptMethod`-annotated method on a `@PromptService` bean
produces one `GetPromptHandler`. The handler bundles the generated
`Prompt` descriptor (name, title, description, argument list) with a
`MethodInvoker<Map<String, String>>` that converts the client-supplied
string arguments to the declared parameter types, plus the list of
`CompletionCandidate`s derived from enum-typed or
`@Schema(allowableValues=...)` parameters. `McpPromptsService` holds a
`Map<String, GetPromptHandler>` keyed by prompt name and looks up the
handler on every `prompts/get` call before dispatching.

Discovery lives in `GetPromptHandlers#discover`, invoked once during
`McpPromptsService` bean creation by
`MocapiServerPromptsAutoConfiguration`. The same bean method walks
every handler's `completionCandidates()` and registers them with
`McpCompletionsService`, so `completion/complete` keeps working for
prompt arguments.

## ReadResourceHandler — `resources/read` (fixed URIs)

Every `@ResourceMethod`-annotated method on a `@ResourceService` bean
produces one `ReadResourceHandler`. The handler bundles the generated
`Resource` descriptor (URI, name, description, MIME type) with a
`MethodInvoker<Object>` that produces the `ReadResourceResult` returned
by `resources/read`. `McpResourcesService` holds a `Map<String,
ReadResourceHandler>` keyed by URI and looks up the handler on every
fixed-URI `resources/read` call before dispatching.

Discovery lives in `ReadResourceHandlers#discover`, invoked once during
`McpResourcesService` bean creation by
`MocapiServerResourcesAutoConfiguration`.

## ReadResourceTemplateHandler — `resources/read` (templated URIs)

Every `@ResourceTemplateMethod`-annotated method on a `@ResourceService`
bean produces one `ReadResourceTemplateHandler`. The handler bundles
the generated `ResourceTemplate` descriptor (URI template, name,
description, MIME type) with a `MethodInvoker<Map<String, String>>`
that converts the URI's resolved path variables to the declared
parameter types, plus the list of `CompletionCandidate`s derived from
enum-typed or `@Schema(allowableValues=...)` variables.
`McpResourcesService` holds a `Map<UriTemplate,
ReadResourceTemplateHandler>` and matches an incoming `resources/read`
URI against the templates after the fixed-URI map lookup misses.

Discovery lives in `ReadResourceTemplateHandlers#discover`, invoked
once during `McpResourcesService` bean creation by
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
runs through a `MethodInterceptor` chain. Each of the four handler
autoconfigs autowires an optional `List<MethodInterceptor<? super T>>`
for its kind (`JsonNode` for tools, `Map<String, String>` for prompts
and resource templates, `Object` for fixed-URI resources) and threads
it through the `discover(...)` helper. Interceptors are applied in
list order — first-added is outermost — so a downstream starter can
ship an interceptor as a plain `@Bean` and it will wrap every
matching handler automatically without any mocapi API additions.

Tools get one built-in interceptor: `InputSchemaValidatingInterceptor`
is appended innermost per `CallToolHandler`, validating the incoming
`JsonNode` against the compiled input schema and throwing
`JsonRpcException(-32602)` on a mismatch. Because that exception
propagates out of the invoker and into `McpToolsService.invokeTool`'s
generic `catch (Exception)`, it surfaces to the client as
`CallToolResult { isError: true }` — matching the MCP spec's "input
validation errors belong in the result body so the LLM can
self-correct" guidance.

A minimal timing interceptor looks like:

```java
@Component
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
```

Returning such a bean from anywhere on the classpath is enough to
have it wrap every `CallToolHandler`. Output-schema validation is
*not* wired in — the output schema is descriptive metadata only, and
mocapi trusts handlers to produce conformant results.
