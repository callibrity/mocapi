# Migrate resources to use mocapi-model types

## What to build

Replace the resource types in `mocapi-core` with the canonical types from
`mocapi-model`. This is the first migration — we'll do prompts and tools
in follow-up specs.

### Add dependency

`mocapi-core` depends on `mocapi-model`.

### Replace resource content types

Delete from `mocapi-core`:
- `com.callibrity.mocapi.resources.ResourceContent` (sealed interface)
- `com.callibrity.mocapi.resources.TextResourceContent`
- `com.callibrity.mocapi.resources.BlobResourceContent`

Use from `mocapi-model`:
- `com.callibrity.mocapi.model.TextResourceContents`
- `com.callibrity.mocapi.model.BlobResourceContents`
- `com.callibrity.mocapi.model.ResourceContents` (sealed interface)

### Update ReadResourceResponse

```java
public record ReadResourceResponse(List<ResourceContents> contents) {}
```

Or delete `ReadResourceResponse` entirely and use the model's
`ReadResourceResult` directly.

### Update McpResource and McpResourceTemplate

The `read()` methods return `ReadResourceResponse` (or `ReadResourceResult`
from model). The `Descriptor` records can stay on the interfaces — they're
Mocapi-specific metadata, not protocol types. Or replace them with the model's
`Resource` and `ResourceTemplate` records if the shapes match.

Check if `McpResource.Descriptor` matches `com.callibrity.mocapi.model.Resource`:
- Model: `Resource(uri, name, description, mimeType)`
- Current: `McpResource.Descriptor(uri, name, description, mimeType)`

Same shape — use the model type. Delete the nested `Descriptor` record.

Same for `McpResourceTemplate.Descriptor` → model `ResourceTemplate`.

### Update ResourcesRegistry

- `sortedResourceDescriptors` becomes `List<Resource>` (from model)
- `sortedTemplateDescriptors` becomes `List<ResourceTemplate>` (from model)
- `ListResourcesResponse` → model `ListResourcesResult`
- `ListResourceTemplatesResponse` → model `ListResourceTemplatesResult`

### Update McpResourceMethods

Use model types for return values. The JSON-RPC method handlers return
model types directly — Jackson serializes them.

### Update conformance tools and tests

All references to the old types need updating.

### Delete replaced types from mocapi-core

- `ResourceContent`, `TextResourceContent`, `BlobResourceContent`
- `ReadResourceResponse` (if using model's `ReadResourceResult`)
- `ListResourcesResponse` (if using model's `ListResourcesResult`)
- `ListResourceTemplatesResponse` (if using model's `ListResourceTemplatesResult`)
- `McpResource.Descriptor` (if using model's `Resource`)
- `McpResourceTemplate.Descriptor` (if using model's `ResourceTemplate`)

## Acceptance criteria

- [ ] `mocapi-core` depends on `mocapi-model`
- [ ] Resource content types from model used everywhere
- [ ] Resource/ResourceTemplate from model replace Descriptor records
- [ ] Response types from model replace custom response records
- [ ] Old types deleted from mocapi-core
- [ ] No duplicate type definitions remain
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
