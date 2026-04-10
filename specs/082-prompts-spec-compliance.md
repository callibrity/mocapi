# Prompts full spec compliance

## What to build

Audit and fix the prompts implementation against the MCP 2025-11-25 spec.
Multiple gaps exist.

### Fix PromptMessage.content — must be an array

The spec defines `content` as an **array** of `ContentBlock`, not a single
content object.

```java
// Current (wrong):
public record PromptMessage(String role, PromptContent content) {}

// Correct:
public record PromptMessage(String role, List<PromptContent> content) {}
```

Update all conformance tools and tests that construct `PromptMessage`.

### Add missing content types

**AudioPromptContent:**
```java
public record AudioPromptContent(String data, String mimeType) implements PromptContent {}
```

**ResourceLinkContent** — references a resource by URI without embedding it:
```java
public record ResourceLinkContent(String uri, String mimeType) implements PromptContent {}
```

Both `ResourcePromptContent` (embedded) and `ResourceLinkContent` (link)
use `"type": "resource"`. They're distinguished by the presence of `resource`
(embedded) vs `uri` (link) fields. Handle this in the `@JsonSubTypes` or
with a custom deserializer.

### Add Annotations support

All content types can have optional annotations:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Annotations(
    List<String> audience,    // "user" and/or "assistant"
    Double priority,          // 0.0 to 1.0
    String lastModified       // ISO 8601 timestamp
) {}
```

Add `Annotations annotations` field to all content type records.

### Add missing Prompt fields

The spec defines `Prompt` with:
- `name` (required) ✓ have it
- `title` (optional) ✗ missing
- `description` (optional) ✓ have it
- `arguments` (optional) ✓ have it
- `icons` (optional) ✗ missing

Add `title` and `icons` to `McpPrompt` interface and `McpPromptDescriptor`.

```java
public record Icon(String src, String mimeType, List<String> sizes, String theme) {}
```

### Add _meta support

The spec allows `_meta` on prompts, arguments, content, and responses.
For now, add `@JsonInclude(NON_NULL) ObjectNode _meta` fields where the
spec requires them. We don't need to actively use them — just don't reject
them if present.

### notifications/prompts/list_changed

If `listChanged: true` in capabilities, the server should send
`notifications/prompts/list_changed` when the prompt list changes. For now,
document this as a known limitation if we don't support dynamic prompt
registration. The capability should report `listChanged: false`.

### Update conformance tools

Update all prompt conformance tools to use the array content format.

## Acceptance criteria

- [ ] `PromptMessage.content` is `List<PromptContent>`
- [ ] `AudioPromptContent` added
- [ ] `ResourceLinkContent` added (distinguished from embedded resource)
- [ ] `Annotations` record with audience, priority, lastModified
- [ ] All content types accept optional annotations
- [ ] `McpPrompt` and `McpPromptDescriptor` have `title` and `icons`
- [ ] `Icon` record
- [ ] Prompt capability reports `listChanged: false`
- [ ] Conformance tools updated for array content
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
