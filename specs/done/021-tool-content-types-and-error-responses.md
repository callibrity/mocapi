# Support all MCP tool content types and error responses

## What to build

The MCP conformance suite tests multiple tool response content types and error
handling patterns that Mocapi doesn't currently support. Update the tool response
model to handle all content types defined in the spec.

### Expand `CallToolResponse` content types

Currently `CallToolResponse` has `List<TextContent> content`. The MCP spec defines
multiple content types that a tool can return:

- **Text**: `{ "type": "text", "text": "..." }`
- **Image**: `{ "type": "image", "data": "<base64>", "mimeType": "image/png" }`
- **Audio**: `{ "type": "audio", "data": "<base64>", "mimeType": "audio/wav" }`
- **Resource**: `{ "type": "resource", "resource": { "uri": "...", "mimeType": "...", "text": "..." } }`

Replace `TextContent` with a polymorphic `Content` type hierarchy:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio"),
    @JsonSubTypes.Type(value = ResourceContent.class, name = "resource")
})
public sealed interface Content permits TextContent, ImageContent, AudioContent, ResourceContent {}

public record TextContent(String text) implements Content {}
public record ImageContent(String data, String mimeType) implements Content {}
public record AudioContent(String data, String mimeType) implements Content {}
public record ResourceContent(EmbeddedResource resource) implements Content {}
public record EmbeddedResource(String uri, String mimeType, String text, String blob) {}
```

### Update `CallToolResponse`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolResponse(
    List<Content> content,
    Boolean isError,
    ObjectNode structuredContent
) {}
```

The `isError` field (conformance test `tools-call-error`) indicates the tool returned
an error result. When `true`, the `content` should contain error details.

### Update tool infrastructure

`McpTool` interface and `McpToolsCapability` need to work with the new `Content`
types. Tool authors should be able to return any content type:

```java
// Simple text tool (most common)
return new CallToolResponse(List.of(new TextContent("Hello!")), null, structured);

// Error tool
return new CallToolResponse(
    List.of(new TextContent("Something went wrong")), true, null);

// Image tool
return new CallToolResponse(
    List.of(new ImageContent(base64Data, "image/png")), null, null);

// Mixed content
return new CallToolResponse(List.of(
    new TextContent("Here's an image:"),
    new ImageContent(base64Data, "image/png")
), null, null);
```

## Acceptance criteria

- [ ] `Content` sealed interface with `TextContent`, `ImageContent`, `AudioContent`,
      `ResourceContent` implementations
- [ ] `EmbeddedResource` record for resource content
- [ ] `CallToolResponse` uses `List<Content>` instead of `List<TextContent>`
- [ ] `CallToolResponse` has optional `isError` field
- [ ] Jackson polymorphic serialization works (type discriminator in JSON output)
- [ ] Existing tools that return `TextContent` continue to work
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- Content types live in `mocapi-core` or `mocapi-tools` alongside the existing tool
  types.
- Jackson's `@JsonTypeInfo` and `@JsonSubTypes` handle polymorphic serialization.
  The `type` field is the discriminator.
- `EmbeddedResource` has both `text` and `blob` fields — one is used for text content,
  the other for binary (base64). Both should be `@JsonInclude(NON_NULL)`.
- The conformance test `tools-call-mixed-content` expects text, image, AND resource
  content in a single response.
- The conformance test `tools-call-error` expects `isError: true` with text content
  describing the error. This is different from a JSON-RPC error — it's a successful
  JSON-RPC response where the tool itself reports an error.
