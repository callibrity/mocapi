# ServerCapabilitiesContributor interface

## What to build

Create a `ServerCapabilitiesContributor` interface that services
implement to declare their own capabilities. The server collects
all contributors and builds `ServerCapabilities` from them at
initialize time. No service needs to know about any other
service's capabilities.

### Interface

```java
package com.callibrity.mocapi.server;

public interface ServerCapabilitiesContributor {
  void contribute(ServerCapabilitiesBuilder builder);
}
```

### Builder

```java
package com.callibrity.mocapi.server;

import com.callibrity.mocapi.model.*;

public class ServerCapabilitiesBuilder {
  private ToolsCapability tools;
  private ResourcesCapability resources;
  private PromptsCapability prompts;
  private LoggingCapability logging;
  private CompletionsCapability completions;

  public ServerCapabilitiesBuilder tools(ToolsCapability tools) {
    this.tools = tools; return this;
  }
  public ServerCapabilitiesBuilder resources(ResourcesCapability resources) {
    this.resources = resources; return this;
  }
  public ServerCapabilitiesBuilder prompts(PromptsCapability prompts) {
    this.prompts = prompts; return this;
  }
  public ServerCapabilitiesBuilder logging(LoggingCapability logging) {
    this.logging = logging; return this;
  }
  public ServerCapabilitiesBuilder completions(CompletionsCapability completions) {
    this.completions = completions; return this;
  }

  public ServerCapabilities build() {
    return new ServerCapabilities(tools, logging, completions, resources, prompts);
  }
}
```

### Implement on services

Each service implements `ServerCapabilitiesContributor`:

**`McpToolsService`**:
```java
@Override
public void contribute(ServerCapabilitiesBuilder builder) {
  if (!isEmpty()) {
    builder.tools(new ToolsCapability(false));
  }
}
```

**`McpResourcesService`**:
```java
@Override
public void contribute(ServerCapabilitiesBuilder builder) {
  if (!isEmpty()) {
    builder.resources(new ResourcesCapability(false, false));
  }
}
```

**`McpPromptsService`**:
```java
@Override
public void contribute(ServerCapabilitiesBuilder builder) {
  if (!isEmpty()) {
    builder.prompts(new PromptsCapability(false));
  }
}
```

**`McpLoggingService`**:
```java
@Override
public void contribute(ServerCapabilitiesBuilder builder) {
  builder.logging(new LoggingCapability());
}
```

**`McpCompletionsService`**:
```java
@Override
public void contribute(ServerCapabilitiesBuilder builder) {
  builder.completions(new CompletionsCapability());
}
```

### What NOT to do

- Do NOT wire the contributors into `DefaultMcpServer` yet —
  that happens in spec 150c when initialize moves to
  `McpSessionService`.
- Do NOT modify the auto-config yet.

## Acceptance criteria

- [ ] `ServerCapabilitiesContributor` interface exists.
- [ ] `ServerCapabilitiesBuilder` exists.
- [ ] `McpToolsService` implements `ServerCapabilitiesContributor`.
- [ ] `McpResourcesService` implements `ServerCapabilitiesContributor`.
- [ ] `McpPromptsService` implements `ServerCapabilitiesContributor`.
- [ ] `McpLoggingService` implements `ServerCapabilitiesContributor`.
- [ ] `McpCompletionsService` implements `ServerCapabilitiesContributor`.
- [ ] `mvn verify` passes.
