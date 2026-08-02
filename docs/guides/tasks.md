# MCP Tasks

MCP Tasks (`io.modelcontextprotocol/tasks`) lets a long-running tool
return a handle immediately instead of blocking the client's request:
the client polls `tasks/get` for completion, answers any mid-task
questions via `tasks/update`, and may `tasks/cancel`. mocapi implements
this as a single annotation, `@McpTask`, on an otherwise-ordinary
`@McpTool` method — the tool body never knows whether it's running as a
task.

For the architecture behind this guide, see the
[MCP Tasks design doc](../design/tasks.md), and
[ADR-0037](../adr/0037-mcp-tasks-extension.md) /
[ADR-0038](../adr/0038-server-seams-for-extensions.md) for the
decisions.

## Add the dependency

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-tasks</artifactId>
</dependency>
```

`MocapiTasksAutoConfiguration` activates automatically once
`mocapi-tasks` is on the classpath (`@ConditionalOnClass`) — no explicit
`@Enable...` needed. Omitting the dependency leaves core unaffected: no
`@McpTask` annotation is recognized, and every tool runs synchronously
as it does today.

## Annotate a tool

```java
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.tasks.McpTask;
import org.springframework.stereotype.Component;

@Component
public class VideoTools {

    @McpTool(description = "Re-encode a video")
    @McpTask                                       // the entire task-enabling surface
    public EncodeResult encode(String uri, McpToolContext ctx) {
        var progress = ctx.countingProgress(100L);
        // ... do the work, calling progress.emit(...) as you go ...
        return new EncodeResult(uri, "done");
    }
}
```

That's the whole surface. `ctx.elicit(...)`, the progress emitters,
guards, validation, and `McpToolException` handling behave exactly as
they do for a plain tool — see
[Interactive Tools](interactive-tools.md) for that API. The only
observable differences, and only for a task-capable client, are where
the spec forces them: elicitation answers arrive via `tasks/update`
instead of a wire retry, and progress lands in a task's `statusMessage`
instead of `notifications/progress`.

### The decision rule

| Tool | Client declared the `tasks` capability? | What happens |
|---|---|---|
| no `@McpTask` | either | normal synchronous tool — never a task |
| `@McpTask` | yes | `CreateTaskResult`; the client polls `tasks/get` |
| `@McpTask` | no | normal synchronous execution (progressive enhancement) |
| `@McpTask(required = true)` | no | rejected with `-32021` instead of running synchronously |

Most tools should leave `required` at its default `false`: the same
handler then serves task-capable and plain clients alike, with zero
client-side branching required on your part. Reach for
`required = true` only when synchronous execution would be actively
wrong for the tool (e.g. work that's guaranteed to exceed a client's
request timeout).

### Configuring TTL and poll interval

```java
@McpTool(description = "Re-encode a video")
@McpTask(ttl = "PT2H", pollInterval = "PT5S")
public EncodeResult encode(String uri, McpToolContext ctx) { ... }
```

Both attributes take ISO-8601 durations and default to the empty string,
which defers to server-wide properties:

```properties
mocapi.tasks.default-ttl=PT1H
mocapi.tasks.default-poll-interval=PT2S
```

Like `@McpTool`'s `name`/`title`/`description`, `ttl` and `pollInterval` are
resolved through the same `${...}` property-placeholder mechanism before
parsing, so `@McpTask(ttl = "${my.app.encode-ttl}")` works.

## The idempotency contract (restated)

Tasks resume by **re-executing your handler from the top** against a
ledger of prior elicitation answers — the identical MRTR replay
mechanism wire elicitation uses, just with the ledger in a `TaskStore`
record instead of an encrypted token. That means the same rule from
[Interactive Tools](interactive-tools.md#the-idempotency-contract-read-this-one-paragraph)
applies to task tools without modification:

**Code before your last `elicit()` call re-executes once per round
trip — put side effects after the final elicitation, or make them
idempotent.**

A task tool that charges a card and *then* elicits a confirmation
charges the card once per `tasks/update` round trip, exactly like its
synchronous counterpart. There is nothing task-specific to learn here —
if you've written an interactive tool before, you already know the
rule.

## Deployment topology

- **Single node, default configuration:** works out of the box. mocapi
  logs a prominent `WARN` at startup when it falls back to the built-in
  `InMemoryTaskStore`:

  > Using the in-memory TaskStore: task state is process-local — NOT
  > multi-node safe, and in-flight tasks are lost on restart. Provide a
  > shared TaskStore bean for clustered or durable deployments.

  Take that WARN literally: it means exactly what it says. A restart
  loses every task that hasn't reached a terminal status, and a
  multi-instance deployment behind a load balancer will route a
  `tasks/get` to an instance that never saw the `tasks/create` call.

- **Multi-node:** supply your own `TaskStore` bean (see below) reachable
  from every node. Load balancing on `Mcp-Name` alone does not give you
  create→poll affinity — the creating `tools/call` hashes on the tool
  name, the follow-up `tasks/get` hashes on the `taskId`, a different
  value — so a shared store is the supported answer, not routing tricks.
  See [the design doc's deployment-topology section](../design/tasks.md#deployment-topology)
  for the full picture, including the documented limitation around a
  node dying mid-execution.

## Writing a custom `TaskStore`

Supply a `TaskStore` bean and `MocapiTasksAutoConfiguration` backs off
its `InMemoryTaskStore` default (`@ConditionalOnMissingBean`):

```java
public interface TaskStore {
  void create(TaskRecord record);
  Optional<TaskRecord> get(String taskId);
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);
  void delete(String taskId);
}
```

The one contract to get right is **`update`'s atomicity**: the mutation
function must be applied against the current record as a single atomic
step (a compare-and-swap loop, an optimistic-locking retry against a
version column, a conditional write — whatever your backend offers).
Every engine guarantee — single-resume under duplicate `tasks/update`
calls, a cancelled task staying cancelled, a straggler progress update
not resurrecting a terminal task — derives from decisions made *inside*
your mutation function and from what your returned record shows
actually happened. The mutation function itself must be deterministic
and side-effect-free: your backend may legitimately invoke it more than
once per logical call (optimistic retry), and only the last invocation's
result is kept.

### Proving it against the TCK

Add the `mocapi-tasks` test-jar and extend `TaskStoreContractTest`:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-tasks</artifactId>
    <classifier>test-jar</classifier>
    <scope>test</scope>
</dependency>
```

```java
class MyTaskStoreContractTest extends TaskStoreContractTest {
    @Override
    protected TaskStore newStore(Clock clock) {
        return new MyTaskStore(clock, /* your backend config */);
    }
}
```

The abstract test class exercises create-durability and taskId
collision, atomic-mutation semantics under contention, terminal-status
finality, TTL expiry, and version monotonicity — the same bar
`InMemoryTaskStore` is held to. A passing run is your evidence the store
is safe to run in production; a failing one tells you exactly which
guarantee your backend doesn't yet provide.

## See also

- [MCP Tasks (design)](../design/tasks.md) — the execution model,
  `TaskStore` SPI, error table, and deployment topology in full.
- [Interactive Tools](interactive-tools.md) — `ctx.elicit(...)` and
  progress emitters, unchanged for task tools.
- [ADR-0037](../adr/0037-mcp-tasks-extension.md) — the extension and
  execution-model decision.
