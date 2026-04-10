# Unify content types into single ContentBlock hierarchy

## What to build

The MCP schema.ts defines one `ContentBlock` union shared by prompts, tools,
and sampling. We have separate hierarchies for each. Unify them.

### Create shared ContentBlock sealed interface

In a new package `com.callibrity.mocapi.content` (or at the protocol level):

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio"),
    @JsonSubTypes.Type(value = ResourceLink.class, name = "resource_link"),
    @JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource")
})
public sealed interface ContentBlock
    permits TextContent, ImageContent, AudioContent, ResourceLink, EmbeddedResource {}
```

### Content types (matching schema.ts names exactly)

```java
public record TextContent(String text) implements ContentBlock {}
public record ImageContent(String data, String mimeType) implements ContentBlock {}
public record AudioContent(String data, String mimeType) implements ContentBlock {}
public record ResourceLink(String uri, String mimeType) implements ContentBlock {}
public record EmbeddedResource(ResourceContents resource) implements ContentBlock {}
```

### ResourceContents types (for EmbeddedResource and resources/read)

```java
@JsonTypeInfo(...)  // discriminate by presence of text vs blob
public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {}

public record TextResourceContents(String uri, String mimeType, String text)
    implements ResourceContents {}
public record BlobResourceContents(String uri, String mimeType, String blob)
    implements ResourceContents {}
```

These are used both inside `EmbeddedResource.resource` and in
`ReadResourceResponse.contents`.

### Fix ResourceLink type discriminator

We currently use `"resource"` for ResourceLink — the spec says `"resource_link"`.
`"resource"` is for `EmbeddedResource` only.

### Update PromptMessage

```java
public record PromptMessage(String role, List<ContentBlock> content) {}
```

### Update CallToolResponse

```java
public record CallToolResponse(List<ContentBlock> content, Boolean isError, ObjectNode structuredContent) {}
```

### Delete duplicate types

Delete all of these — replaced by the shared types:
- `TextPromptContent`, `ImagePromptContent`, `AudioPromptContent`
- `ResourcePromptContent`, `ResourceLinkContent`
- `EmbeddedPromptResource`
- `PromptContent` (sealed interface)
- `ToolsRegistry.TextContent`, `ToolsRegistry.ImageContent`, etc. (inner classes)
- `TextResourceContent`, `BlobResourceContent` from resources package
  (replaced by `TextResourceContents`, `BlobResourceContents`)

### Update all references

Tools, prompts, resources, conformance tools, tests — everything uses the
shared `ContentBlock` types.

## Acceptance criteria

- [ ] `ContentBlock` sealed interface with 5 subtypes matching schema.ts names
- [ ] `ResourceContents` sealed interface with Text and Blob
- [ ] `ResourceLink` uses type discriminator `"resource_link"` (not `"resource"`)
- [ ] `EmbeddedResource` uses type discriminator `"resource"`
- [ ] `PromptMessage.content` is `List<ContentBlock>`
- [ ] `CallToolResponse.content` is `List<ContentBlock>`
- [ ] `ReadResourceResponse.contents` is `List<ResourceContents>`
- [ ] `EmbeddedResource.resource` is `ResourceContents`
- [ ] All duplicate type classes deleted
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes

## Implementation notes

- The tool content types are currently inner classes of `ToolsRegistry` — they
  need to move out to the shared package
- `ResourceLink` vs `EmbeddedResource` discrimination may need a custom
  deserializer since both could appear where `ContentBlock` is expected
- Annotations support (`audience`, `priority`, `lastModified`) can be added
  to the `ContentBlock` types if spec 082 hasn't already done it
