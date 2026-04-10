# Migrate core session/server types to mocapi-model

## What to build

Replace session and server-level data records in `mocapi-core` with the
canonical types from `mocapi-model`. This covers initialize, capabilities,
client/server info, and related types.

### Replace types

| mocapi-core (delete) | mocapi-model (use) |
|---|---|
| `com.callibrity.mocapi.server.InitializeResponse` | `com.callibrity.mocapi.model.InitializeResult` |
| `com.callibrity.mocapi.server.ServerInfo` | `com.callibrity.mocapi.model.Implementation` |
| `com.callibrity.mocapi.server.ServerCapabilities` | `com.callibrity.mocapi.model.ServerCapabilities` |
| `com.callibrity.mocapi.server.ToolsCapabilityDescriptor` | `com.callibrity.mocapi.model.ToolsCapability` |
| `com.callibrity.mocapi.server.ResourcesCapabilityDescriptor` | `com.callibrity.mocapi.model.ResourcesCapability` |
| `com.callibrity.mocapi.server.PromptsCapabilityDescriptor` | `com.callibrity.mocapi.model.PromptsCapability` |
| `com.callibrity.mocapi.server.LoggingCapabilityDescriptor` | `com.callibrity.mocapi.model.LoggingCapability` |
| `com.callibrity.mocapi.server.CompletionsCapabilityDescriptor` | `com.callibrity.mocapi.model.CompletionsCapability` |
| `com.callibrity.mocapi.server.PingResponse` | (empty response — use `Map.of()` or model equivalent) |
| `com.callibrity.mocapi.session.ClientInfo` | `com.callibrity.mocapi.model.Implementation` |
| `com.callibrity.mocapi.session.ClientCapabilities` | `com.callibrity.mocapi.model.ClientCapabilities` |
| `com.callibrity.mocapi.session.RootsCapability` | `com.callibrity.mocapi.model.RootsCapability` |
| `com.callibrity.mocapi.session.SamplingCapability` | `com.callibrity.mocapi.model.SamplingCapability` |
| `com.callibrity.mocapi.session.ElicitationCapability` | `com.callibrity.mocapi.model.ElicitationCapability` |
| `com.callibrity.mocapi.session.Icon` | `com.callibrity.mocapi.model.Icon` (if already in model) |
| `com.callibrity.mocapi.session.LogLevel` | `com.callibrity.mocapi.model.LoggingLevel` |
| `com.callibrity.mocapi.session.TasksCapability` | (if still present and in model) |

### Update McpSession

`McpSession` stores client info and capabilities. Update its fields to use
model types:

```java
public record McpSession(
    String protocolVersion,
    ClientCapabilities capabilities,  // from model
    Implementation clientInfo,          // from model (instead of ClientInfo)
    LoggingLevel logLevel,              // from model
    String sessionId) {}
```

### Update MocapiAutoConfiguration

The `initializeResponse` bean returns `InitializeResult` from model:

```java
@Bean
public InitializeResult initializeResponse(
    ToolsRegistry toolsRegistry,
    ResourcesRegistry resourcesRegistry,
    PromptsRegistry promptsRegistry,
    @Nullable BuildProperties buildProperties) {
  ...
  return new InitializeResult(
      PROTOCOL_VERSION,
      new ServerCapabilities(tools, logging, completions, resources, prompts),
      new Implementation(props.getServerName(), props.getServerTitle(), version),
      props.getInstructions());
}
```

### Update McpSessionMethods

- `initialize` returns `InitializeResult`
- `ping` returns empty object (Map.of() or a simple empty record)

### Update McpLoggingMethods

`logging/setLevel` takes a level string, converts to `LoggingLevel` from model.
Update `LogLevel` references to `LoggingLevel`.

### Update StreamableHttpController

`createSessionFromParams` parses `InitializeRequest.params` into model types:
- `ClientCapabilities` from model
- `Implementation` from model (was `ClientInfo`)

### Update DefaultMcpStreamContext

Uses `LoggingLevel` from model instead of `LogLevel` from session package.

### Delete replaced types

After migration, delete everything from `mocapi-core` that's now duplicated
in `mocapi-model`.

## Acceptance criteria

- [ ] All server capability descriptors deleted — using model types
- [ ] All client capability types deleted — using model types
- [ ] `InitializeResponse` deleted — using `InitializeResult`
- [ ] `ServerInfo` and `ClientInfo` deleted — using `Implementation`
- [ ] `LogLevel` deleted — using `LoggingLevel`
- [ ] `McpSession` uses model types for capabilities, client info, log level
- [ ] All JSON-RPC method signatures updated to return model types
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
