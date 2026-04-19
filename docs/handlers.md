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

Resources and resource templates will follow the same shape
(`ReadResourceHandler`, `ReadResourceTemplateHandler`) as the 170-series
handler cleanup proceeds.
