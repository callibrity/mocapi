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

Prompts, resources, and resource templates will follow the same shape
(`GetPromptHandler`, `ReadResourceHandler`,
`ReadResourceTemplateHandler`) as the 170-series handler cleanup
proceeds.
