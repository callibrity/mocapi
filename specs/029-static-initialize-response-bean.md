# Static InitializeResponse as a bean

## What to build

Replace the dynamic `McpServer.initialize()` method with a static `InitializeResponse`
bean built once at startup in auto-configuration. The server's capabilities are fixed
at startup — there's no reason to compute the response per-request.

### Create typed response records

In `mocapi-core`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolsCapabilityDescriptor(boolean listChanged) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
    ToolsCapabilityDescriptor tools
    // Future: LoggingCapabilityDescriptor logging,
    //         ResourcesCapabilityDescriptor resources, etc.
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeResponse(
    String protocolVersion,
    ServerCapabilities capabilities,
    ServerInfo serverInfo,
    String instructions
) {}
```

`@JsonInclude(NON_NULL)` ensures unsupported capabilities are omitted from the JSON,
not serialized as `null`.

### Create the bean in auto-configuration

```java
@Bean
public InitializeResponse initializeResponse(
        MocapiProperties props,
        @Autowired(required = false) BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    return new InitializeResponse(
        "2025-11-25",
        new ServerCapabilities(new ToolsCapabilityDescriptor(false)),
        new ServerInfo(props.getServerName(), props.getServerTitle(), version),
        props.getInstructions()
    );
}
```

`BuildProperties` is provided by Spring Boot's `build-info` goal. If not configured,
falls back to `"unknown"`. Server name and title come from `MocapiProperties`.

### Simplify the initialize handler

The `@JsonRpc("initialize")` handler injects the bean and returns it:

```java
@JsonRpc("initialize")
public InitializeResponse initialize() {
    return initializeResponse;
}
```

No parameters, no computation, no `McpServer` involvement. The controller still
handles session creation from the request params separately.

### Remove dynamic capability building from `McpServer`

Delete the `initialize()` method on `McpServer` that builds the response dynamically
and checks client capabilities. Delete `ElicitationCapabilityDescriptor` and any
per-request capability negotiation logic.

`McpServer` may become unnecessary entirely if its only remaining role was building
the initialize response. Evaluate whether it should be deleted or repurposed.

### Update `MocapiProperties`

Ensure properties exist for:

```yaml
mocapi:
  server-name: mocapi-example
  server-title: Mocapi Example Server
  instructions: Optional instructions for the client
```

## Acceptance criteria

- [ ] `ToolsCapabilityDescriptor` record exists
- [ ] `ServerCapabilities` record exists with `@JsonInclude(NON_NULL)`
- [ ] `InitializeResponse` record exists with `@JsonInclude(NON_NULL)`
- [ ] `InitializeResponse` is a Spring bean built once at startup
- [ ] Server version comes from `BuildProperties` when available
- [ ] `@JsonRpc("initialize")` handler returns the cached bean, takes no parameters
- [ ] Dynamic capability building in `McpServer` is removed
- [ ] JSON output matches spec: `protocolVersion`, `capabilities.tools.listChanged`,
      `serverInfo.name`, optional `instructions`
- [ ] Null capabilities are omitted from JSON (not serialized as null)
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- The `InitializeResponse` record here is for the server's response only. It replaces
  `McpServer.InitializeResponse`. The name might conflict — consider the package
  location to avoid ambiguity.
- The existing `CapabilityDescriptor` marker interface and `McpServerCapability`
  interface may be dead code after this change. Delete if nothing references them.
- When we add `logging` support later, add a `LoggingCapabilityDescriptor` field to
  `ServerCapabilities`. The `@JsonInclude(NON_NULL)` ensures it's omitted until then.
- Spring Boot's `build-info` goal needs to be configured in the example app's
  `pom.xml` for `BuildProperties` to be available:
  ```xml
  <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <executions>
          <execution>
              <goals><goal>build-info</goal></goals>
          </execution>
      </executions>
  </plugin>
  ```
