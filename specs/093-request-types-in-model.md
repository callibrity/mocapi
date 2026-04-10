# Add request and notification types to mocapi-model

## What to build

Add all request param types, notification types, and base types from schema.ts
to `mocapi-model`. Just the data types — no migration of mocapi-core yet.

### Base types

**RequestParams** (base for all request params):
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestParams(@JsonProperty("_meta") RequestMeta meta) {}
```

**RequestMeta**:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMeta(Object progressToken) {}  // string | number
```

**PaginatedRequestParams** (for list methods):
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedRequestParams(String cursor, @JsonProperty("_meta") RequestMeta meta) {}
```

**NotificationParams** (base for notifications):
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationParams(@JsonProperty("_meta") ObjectNode meta) {}
```

**TaskMetadata** (if present in schema — placeholder for now):
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskMetadata(String taskId) {}  // check schema for exact shape
```

### Initialization

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeRequestParams(
    String protocolVersion,
    ClientCapabilities capabilities,
    Implementation clientInfo,
    @JsonProperty("_meta") RequestMeta meta
) {}
```

### Tools

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolRequestParams(
    String name,
    JsonNode arguments,  // JsonNode for flexibility
    TaskMetadata task,
    @JsonProperty("_meta") RequestMeta meta
) {}
```

`ListToolsRequest` uses `PaginatedRequestParams` — no dedicated param type.

### Prompts

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetPromptRequestParams(
    String name,
    Map<String, String> arguments,  // strict string map per spec
    @JsonProperty("_meta") RequestMeta meta
) {}
```

`ListPromptsRequest` uses `PaginatedRequestParams`.

### Resources

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceRequestParams(String uri, @JsonProperty("_meta") RequestMeta meta) {}
```

Used by `read`, `subscribe`, `unsubscribe`. `ListResourcesRequest` and
`ListResourceTemplatesRequest` use `PaginatedRequestParams`.

### Logging

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetLevelRequestParams(LoggingLevel level, @JsonProperty("_meta") RequestMeta meta) {}
```

### Sampling

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateMessageRequestParams(
    List<SamplingMessage> messages,
    ModelPreferences modelPreferences,
    String systemPrompt,
    String includeContext,  // "none" | "thisServer" | "allServers"
    Double temperature,
    int maxTokens,
    List<String> stopSequences,
    ObjectNode metadata,
    List<Tool> tools,
    Object toolChoice,
    TaskMetadata task,
    @JsonProperty("_meta") RequestMeta meta
) {}

public record SamplingMessage(Role role, ContentBlock content) {}
public record ModelPreferences(
    List<ModelHint> hints,
    Double costPriority,
    Double speedPriority,
    Double intelligencePriority
) {}
public record ModelHint(String name) {}
```

### Completion

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompleteRequestParams(
    CompletionRef ref,
    CompletionArgument argument,
    CompletionContext context,
    @JsonProperty("_meta") RequestMeta meta
) {}

public sealed interface CompletionRef permits PromptReference, ResourceTemplateReference {}
public record PromptReference(String type, String name) implements CompletionRef {}
public record ResourceTemplateReference(String type, String uri) implements CompletionRef {}
public record CompletionArgument(String name, String value) {}
public record CompletionContext(Map<String, String> arguments) {}
```

### Elicitation (new params types to add — may overlap spec 091)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitRequestFormParams(
    String mode,  // "form" or null
    String message,
    RequestedSchema requestedSchema,
    TaskMetadata task,
    @JsonProperty("_meta") RequestMeta meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitRequestURLParams(
    String mode,  // "url"
    String message,
    String elicitationId,
    String url,
    TaskMetadata task,
    @JsonProperty("_meta") RequestMeta meta
) {}
```

### Notifications

```java
public record InitializedNotificationParams(@JsonProperty("_meta") ObjectNode meta) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelledNotificationParams(
    Object requestId,  // string | number
    String reason,
    @JsonProperty("_meta") ObjectNode meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressNotificationParams(
    Object progressToken,
    double progress,
    Double total,
    String message,
    @JsonProperty("_meta") ObjectNode meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoggingMessageNotificationParams(
    LoggingLevel level,
    String logger,
    Object data,
    @JsonProperty("_meta") ObjectNode meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceUpdatedNotificationParams(
    String uri,
    @JsonProperty("_meta") ObjectNode meta
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitationCompleteNotificationParams(
    String elicitationId,
    @JsonProperty("_meta") ObjectNode meta
) {}
```

### Method name constants

Add a class with all the MCP method name constants for compile-time safety:

```java
public final class McpMethods {
    public static final String INITIALIZE = "initialize";
    public static final String PING = "ping";
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    public static final String RESOURCES_READ = "resources/read";
    public static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    public static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    public static final String LOGGING_SET_LEVEL = "logging/setLevel";
    public static final String COMPLETION_COMPLETE = "completion/complete";
    public static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
    public static final String ELICITATION_CREATE = "elicitation/create";
    public static final String ROOTS_LIST = "roots/list";
    
    // Notifications
    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    public static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
    public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    public static final String NOTIFICATIONS_MESSAGE = "notifications/message";
    public static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    public static final String NOTIFICATIONS_RESOURCES_UPDATED = "notifications/resources/updated";
    public static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    public static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    public static final String NOTIFICATIONS_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    public static final String NOTIFICATIONS_ELICITATION_COMPLETE = "notifications/elicitation/complete";
    
    private McpMethods() {}
}
```

### Skip for now

- `Request`, `JSONRPCRequest`, `JSONRPCNotification` — these are the envelope
  types (jsonrpc, id, method, params). We already have RipCurl's `JsonRpcCall`
  and `JsonRpcNotification` for that. No need to duplicate.

### Tests

Unit tests verifying serialization round-trips for each param type.

## Acceptance criteria

- [ ] Base types: `RequestParams`, `RequestMeta`, `PaginatedRequestParams`, `NotificationParams`
- [ ] Initialize: `InitializeRequestParams`
- [ ] Tools: `CallToolRequestParams`
- [ ] Prompts: `GetPromptRequestParams`
- [ ] Resources: `ResourceRequestParams`
- [ ] Logging: `SetLevelRequestParams`
- [ ] Sampling: `CreateMessageRequestParams`, `SamplingMessage`, `ModelPreferences`, `ModelHint`
- [ ] Completion: `CompleteRequestParams`, `CompletionRef`, `PromptReference`, `ResourceTemplateReference`, `CompletionArgument`, `CompletionContext`
- [ ] Elicitation: `ElicitRequestFormParams`, `ElicitRequestURLParams`
- [ ] All notification param types
- [ ] `McpMethods` constants class
- [ ] `arguments` fields use `JsonNode` for flexibility (not `ObjectNode`)
- [ ] Unit tests for serialization
- [ ] NO changes to mocapi-core — migration is a follow-up spec
- [ ] `mvn verify` passes
