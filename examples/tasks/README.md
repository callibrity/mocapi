# Mocapi Example — MCP Tasks

A runnable [MCP Tasks](https://github.com/modelcontextprotocol/ext-tasks) (`io.modelcontextprotocol/tasks`)
example built with mocapi. It demonstrates the [`mocapi-tasks`](../../mocapi-tasks) module: a single
annotation, `@McpTask`, that turns an ordinary `@McpTool` method into one that returns a task handle
for capable clients — progress, mid-task elicitation, and the required-task degrade, all without the
tool body ever knowing it's running as a task. See the [Tasks guide](../../docs/guides/tasks.md) and
[design doc](../../docs/design/tasks.md).

## What's included

All three tools live in [`TaskDemoTools.java`](src/main/java/com/callibrity/mocapi/examples/tasks/TaskDemoTools.java):

- **batch_resize** (`@McpTask(ttl = "PT10M", pollInterval = "PT1S")`) — loops over `items`,
  sleeping briefly and emitting counting progress each iteration. For a task-capable client, that
  progress shows up as a moving `statusMessage` across successive `tasks/get` polls. For a plain
  client it's silent and the call just blocks until done — the progressive-enhancement decision
  rule in action.
- **confirmed_report** (`@McpTask`) — "compiles" a report, then calls `ctx.elicit(...)` to confirm
  publishing. For a task-capable + elicitation-capable client, that elicit call surfaces as the
  task reaching `input_required`; the client answers via `tasks/update` (not a wire retry) and the
  task resumes and completes.
- **must_run_as_task** (`@McpTask(required = true)`) — opts out of the synchronous fallback: a
  client that hasn't declared the `tasks` capability gets JSON-RPC `-32021` instead of a result.

## Run it

```bash
# from the repo root — build the runnable jar (and its mocapi dependencies)
mvn -pl examples/tasks -am package -DskipTests

# run it (port 8082 — see application.properties)
java -jar examples/tasks/target/mocapi-example-tasks-*.jar
```

`spring-boot:run` also works from *inside* the module (the plugin prefix doesn't resolve through
the aggregator pom):

```bash
mvn -pl examples/tasks -am install -DskipTests
cd examples/tasks && mvn spring-boot:run
```

The server listens on `http://localhost:8082/mcp` (Streamable HTTP, stateless — POST only, no
`initialize` handshake, no `Mcp-Session-Id`; see the streamable-HTTP transport guide under
[`docs/guides`](../../docs/guides) for the request envelope shape).

## Walk through the conversation

[`mcp-example-requests.http`](mcp-example-requests.http) (IntelliJ HTTP client) is a numbered,
nine-step conversation that exercises the full lifecycle:

1. `tools/call batch_resize` with the `tasks` extension declared in `_meta` clientCapabilities →
   an immediate `CreateTaskResult` (a task handle, not a result).
2. `tasks/get` poll (`Mcp-Name` is the `taskId` from step 1) → watch `statusMessage` move
   (`"item 1"`, `"item 2"`, …) until `status` flips to `completed`.
3. `tools/call confirmed_report` with `tasks` + `elicitation` capabilities declared → another
   `CreateTaskResult`.
4. `tasks/get` on that task → `status: "input_required"` with a pending `inputRequests` entry.
5. `tasks/update` answering the elicitation (`{"action":"accept","content":{"publish":true}}`) →
   the task resumes.
6. `tasks/get` again → `status: "completed"` with the published `ReportResult`.
7. `tasks/cancel` on the batch_resize task (re-run step 1 first if it already finished).
8. `tools/call batch_resize` with **no** `tasks` capability declared → ordinary synchronous
   result, no task handle — the progressive-enhancement fallback.
9. `tools/call must_run_as_task` with **no** `tasks` capability declared → JSON-RPC `-32021`,
   because that tool is `@McpTask(required = true)`.

Each request in the file is commented with the response shape to expect. Run them in order — the
`.http` client's response-handler scripts (`client.global.set(...)`) capture `taskId` /
`reportTaskId` / `elicitKey` from earlier responses into later requests automatically.

## Notes

**Not for production use.** The server falls back to `InMemoryTaskStore` (process-local, not
multi-node safe — see the startup `WARN` and the [Tasks guide](../../docs/guides/tasks.md#deployment-topology)).
