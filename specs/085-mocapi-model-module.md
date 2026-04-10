# Create mocapi-model module from MCP schema.ts

## What to build

Create a new `mocapi-model` module that contains Java record types translated
directly from the MCP 2025-11-25 `schema.ts`. Same names, same shapes. Pure
data types — no Spring, no business logic. Only dependency is Jackson annotations
for serialization.

### Module setup

- New Maven module `mocapi-model`
- `<maven.deploy.skip>false</maven.deploy.skip>` — this IS published
- Dependencies: Jackson annotations only (both `tools.jackson` and
  `com.fasterxml.jackson` annotations for compatibility)
- Package: `com.callibrity.mocapi.model`
- Add to parent pom `<modules>`
- `mocapi-core` depends on `mocapi-model`

### Translation rules

Every TypeScript interface → Java record.
Every union type → sealed interface with `@JsonSubTypes`.
Same names as schema.ts. Optional fields are nullable with `@JsonInclude(NON_NULL)`.

### Types to translate (organized by schema.ts sections)

**Content types (ContentBlock union):**
- `TextContent(String type, String text, Annotations annotations)`
- `ImageContent(String type, String data, String mimeType, Annotations annotations)`
- `AudioContent(String type, String data, String mimeType, Annotations annotations)`
- `ResourceLink(String type, String uri, String mimeType, Annotations annotations)`
- `EmbeddedResource(String type, ResourceContents resource, Annotations annotations)`

`type` is the discriminator — handled by `@JsonTypeInfo`, not stored as a field.
So records omit `type` and Jackson handles it:

```java
public sealed interface ContentBlock
    permits TextContent, ImageContent, AudioContent, ResourceLink, EmbeddedResource {}

public record TextContent(String text, Annotations annotations) implements ContentBlock {}
public record ImageContent(String data, String mimeType, Annotations annotations) implements ContentBlock {}
public record AudioContent(String data, String mimeType, Annotations annotations) implements ContentBlock {}
public record ResourceLink(String uri, String mimeType, Annotations annotations) implements ContentBlock {}
public record EmbeddedResource(ResourceContents resource, Annotations annotations) implements ContentBlock {}
```

**Annotations:**
```java
public record Annotations(List<Role> audience, Double priority, String lastModified) {}
```

**Role:**
```java
public enum Role { user, assistant }
```

**Resource contents:**
```java
public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {}
public record TextResourceContents(String uri, String mimeType, String text) implements ResourceContents {}
public record BlobResourceContents(String uri, String mimeType, String blob) implements ResourceContents {}
```

**Prompt types:**
```java
public record PromptMessage(Role role, List<ContentBlock> content) {}
public record PromptArgument(String name, String description, Boolean required) {}
public record GetPromptResult(String description, List<PromptMessage> messages) {}
public record Prompt(String name, String title, String description, List<Icon> icons, List<PromptArgument> arguments) {}
public record ListPromptsResult(List<Prompt> prompts, String nextCursor) {}
```

**Tool types:**
```java
public record Tool(String name, String title, String description, ObjectNode inputSchema, ObjectNode outputSchema) {}
public record CallToolResult(List<ContentBlock> content, Boolean isError, ObjectNode structuredContent) {}
public record ListToolsResult(List<Tool> tools, String nextCursor) {}
```

**Resource types:**
```java
public record Resource(String uri, String name, String description, String mimeType) {}
public record ResourceTemplate(String uriTemplate, String name, String description, String mimeType) {}
public record ReadResourceResult(List<ResourceContents> contents) {}
public record ListResourcesResult(List<Resource> resources, String nextCursor) {}
public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, String nextCursor) {}
```

**Icon:**
```java
public record Icon(String src, String mimeType, List<String> sizes, String theme) {}
```

**Initialize types:**
```java
public record InitializeResult(String protocolVersion, ServerCapabilities capabilities, Implementation serverInfo, String instructions) {}
public record Implementation(String name, String title, String version) {}
public record ClientCapabilities(RootsCapability roots, SamplingCapability sampling, ElicitationCapability elicitation) {}
public record ServerCapabilities(ToolsCapability tools, LoggingCapability logging, CompletionsCapability completions, ResourcesCapability resources, PromptsCapability prompts) {}
```

**Capability descriptors:**
```java
public record ToolsCapability(Boolean listChanged) {}
public record ResourcesCapability(Boolean subscribe, Boolean listChanged) {}
public record PromptsCapability(Boolean listChanged) {}
public record LoggingCapability() {}
public record CompletionsCapability() {}
public record RootsCapability(Boolean listChanged) {}
public record SamplingCapability() {}
public record ElicitationCapability() {}
```

**Elicitation types:**
```java
public record ElicitResult(String action, ObjectNode content) {}
// ElicitationSchema types stay in mocapi-core (they're builder/behavior, not protocol)
```

**Sampling types:**
```java
public record CreateMessageResult(Role role, ContentBlock content, String model, String stopReason) {}
public record SamplingMessage(Role role, ContentBlock content) {}
```

**Completion types:**
```java
public record CompleteResult(Completion completion) {}
public record Completion(List<String> values, Integer total, Boolean hasMore) {}
```

**Logging types:**
```java
public enum LoggingLevel { debug, info, notice, warning, error, critical, alert, emergency }
```

**Error types:**
```java
public record JsonRpcError(int code, String message, Object data) {}
```

### What stays in mocapi-core

- `McpTool`, `McpPrompt`, `McpResource`, `McpResourceTemplate` interfaces (behavior)
- Registries, JSON-RPC method handlers
- `ElicitationSchema` builder and property builders
- Stream context, session management
- Auto-configuration
- All Spring dependencies

### Migration

After creating the model module, update `mocapi-core` to use the model types
instead of its own duplicates. Delete the duplicates. This is a follow-up
spec — this spec only creates the module and the types.

## Acceptance criteria

- [ ] `mocapi-model` module exists
- [ ] All protocol types from schema.ts translated to Java records
- [ ] Names match schema.ts exactly
- [ ] `@JsonInclude(NON_NULL)` on all types with optional fields
- [ ] `@JsonTypeInfo`/`@JsonSubTypes` for union types
- [ ] Type discriminators match spec (`"text"`, `"image"`, `"audio"`, `"resource_link"`, `"resource"`)
- [ ] No Spring dependencies — Jackson annotations only
- [ ] Unit tests verifying serialization round-trips for key types
- [ ] Module added to parent pom
- [ ] `mvn verify` passes
