# Interactive Features

Tools can communicate with the client mid-execution using `McpToolContext`. Add it as a parameter to your tool method -- the framework injects it automatically.

```java
import com.callibrity.mocapi.api.tools.McpToolContext;

@ToolMethod(name = "process", description = "Processes data with progress")
public ProcessResult process(String data, McpToolContext ctx) {
    ctx.sendProgress(1, 3);
    // ... step 1 ...
    ctx.sendProgress(2, 3);
    // ... step 2 ...
    ctx.sendProgress(3, 3);
    return new ProcessResult("Done");
}
```

The `McpToolContext` parameter does not appear in the tool's input schema -- it is resolved by the framework, not provided by the client.

## Progress Notifications

Send progress updates to let the client show a progress indicator:

```java
ctx.sendProgress(current, total);
```

Both arguments are `long`. Progress notifications are sent as `notifications/progress` SSE events. They are only sent if the client included a `progressToken` in the request's `_meta` field.

## Logging

Send structured log messages to the client via an SLF4J-shaped logger obtained
from the context. Messages use `{}` placeholders for parameter interpolation.

```java
var log = ctx.logger("my-tool");
log.info("Processing started");
log.debug("Found {} items", count);
log.error("Failed to connect to {}: {}", host, cause.getMessage());
log.warn("Rate limit approaching ({} req/s)", rate);
```

`ctx.logger()` (no argument) returns a logger named after the currently
executing handler — the tool's `@ToolMethod` name — so you rarely need to pass
a name explicitly:

```java
ctx.logger().info("Processing started");
```

For rare cases you can still call the general `log` method with an explicit level:

```java
import com.callibrity.mocapi.model.LoggingLevel;

ctx.log(LoggingLevel.NOTICE, "my-tool", "Something noteworthy");
```

Log messages are filtered by the session's log level. The client sets this via `logging/setLevel`. Messages below the session's level are silently dropped.

Available levels (in ascending order): `DEBUG`, `INFO`, `NOTICE`, `WARNING`, `ERROR`, `CRITICAL`, `ALERT`, `EMERGENCY`.

## Elicitation

Tools can prompt the user for input during execution. The server sends an `elicitation/create` request to the client, which presents a form to the user and returns their response.

```java
@ToolMethod(name = "onboard", description = "Onboards a new user")
public OnboardResult onboard(McpToolContext ctx) {
    ElicitResult result = ctx.elicit("Please enter your details", schema -> schema
        .string("name", "Your name")
        .string("email", "Email address", s -> s.email())
    );

    if (result.isAccepted()) {
        String name = result.getString("name");
        return new OnboardResult("Welcome, " + name + "!");
    }
    return new OnboardResult("Onboarding cancelled.");
}
```

### Response Actions

The `ElicitResult` contains an `action` field:

- `ACCEPT` -- the user submitted the form. Access data via `result.content()`.
- `DECLINE` -- the user explicitly declined (clicked "No" or "Reject").
- `CANCEL` -- the user dismissed without choosing (closed the dialog, pressed Escape).

### Elicitation and Client Capabilities

The client must declare elicitation support during initialization. If the client does not support elicitation, calling `ctx.elicit()` will time out.

## Sampling

Tools can request LLM completions from the client:

```java
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;

@ToolMethod(name = "summarize", description = "Summarizes text using an LLM")
public SummaryResult summarize(String text, McpToolContext ctx) {
    var params = new CreateMessageRequestParams(
        List.of(new SamplingMessage(Role.USER, new TextContent("Summarize: " + text, null))),
        null, null, "Summarize the text concisely", null, 200, null, null);

    CreateMessageResult result = ctx.sample(params);
    return new SummaryResult(result.content().text());
}
```

Like elicitation, the client must declare sampling support during initialization.

## Timeouts

Both `elicit()` and `sample()` block until the client responds or a timeout elapses. Timeouts are configurable:

```properties
mocapi.elicitation.timeout=PT5M
mocapi.sampling.timeout=PT30S
```

If a timeout occurs, the server sends a `notifications/cancelled` to the client and the tool receives an exception, which the framework catches and returns as a tool error (`isError=true`).

## Three Tool Patterns

Mocapi supports three patterns for tool methods. All are invoked identically -- there is no `isInteractive` flag or streaming mode switch.

### 1. Simple Return

The tool takes parameters, does work, returns a result:

```java
@ToolMethod(name = "hello", description = "Greets someone")
public HelloResponse hello(String name) {
    return new HelloResponse("Hello, " + name + "!");
}
```

### 2. Void Return

The tool performs a side effect and returns nothing:

```java
@ToolMethod(name = "notify", description = "Sends a push notification")
public void notify(String message) {
    pushService.send(message);
}
```

### 3. Interactive

The tool declares `McpToolContext` and uses it for mid-execution communication:

```java
@ToolMethod(name = "wizard", description = "Multi-step wizard")
public WizardResult wizard(McpToolContext ctx) {
    ctx.sendProgress(1, 3);
    ElicitResult step1 = ctx.elicit(step1Params);
    ctx.sendProgress(2, 3);
    ElicitResult step2 = ctx.elicit(step2Params);
    ctx.sendProgress(3, 3);
    return new WizardResult(step1, step2);
}
```

In all three patterns, the tool returns its result (or void). The framework wraps it in a `CallToolResult` and sends it as the JSON-RPC response. Tools never send their own result on the transport.
