# MCP Resources support

## What to build

Add resource support to Mocapi, following the same patterns as tools:
annotation-based providers, a registry, JSON-RPC methods, and auto-configuration.

### Resource model

```java
public record McpResource(String uri, String name, String description, String mimeType) {}
public record McpResourceTemplate(String uriTemplate, String name, String description, String mimeType) {}
public record ResourceContent(String uri, String mimeType, String text, String blob) {}
public record ReadResourceResponse(List<ResourceContent> contents) {}
public record ListResourcesResponse(List<McpResource> resources, String nextCursor) {}
public record ListResourceTemplatesResponse(List<McpResourceTemplate> resourceTemplates, String nextCursor) {}
```

### McpResourceProvider interface

```java
public interface McpResourceProvider {
    List<McpResource> getResources();
    List<McpResourceTemplate> getResourceTemplates();
    ReadResourceResponse read(String uri);
}
```

### McpResourceMethods — JSON-RPC service

A `@JsonRpcService` that handles:

- `resources/list` — returns all direct resources (not templates)
- `resources/templates/list` — returns resource templates
- `resources/read` — reads a resource by URI, params: `{ "uri": "..." }`
- `resources/subscribe` — accepts URI subscription, returns `{}`
- `resources/unsubscribe` — removes URI subscription, returns `{}`

Subscription tracking can be in-memory for now — track subscribed URIs per
session.

### ResourcesRegistry

Aggregates resources from all `McpResourceProvider` beans. Handles URI matching
for templates (simple string substitution in `{param}` patterns).

### Auto-configuration

- Register `McpResourceMethods` when `McpResourceProvider` beans exist
- Add `resources` capability to `InitializeResponse` when resources are available

### Conformance tools (in mocapi-compat)

Add these to the compat module's conformance server:

**Static text resource:** `test://static-text`
```json
{"contents": [{"uri": "test://static-text", "mimeType": "text/plain", "text": "This is the content of the static text resource."}]}
```

**Static binary resource:** `test://static-binary`
```json
{"contents": [{"uri": "test://static-binary", "mimeType": "image/png", "blob": "<base64 1x1 red PNG>"}]}
```

**Resource template:** `test://template/{id}/data`
Substitutes `{id}` parameter:
```json
{"contents": [{"uri": "test://template/123/data", "mimeType": "application/json", "text": "{\"id\":\"123\",\"templateTest\":true,\"data\":\"Data for ID: 123\"}"}]}
```

**Watched resource:** `test://watched-resource`
For subscribe/unsubscribe testing. Returns simple text content.

## Acceptance criteria

- [ ] `resources/list` returns registered resources
- [ ] `resources/templates/list` returns registered templates
- [ ] `resources/read` returns content for known URIs
- [ ] `resources/read` with template URI substitutes parameters
- [ ] `resources/subscribe` accepts URI and returns `{}`
- [ ] `resources/unsubscribe` removes subscription and returns `{}`
- [ ] `resources` capability in initialize response when resources exist
- [ ] Conformance scenarios pass: `resources-list`, `resources-read-text`,
  `resources-read-binary`, `resources-templates-read`, `resources-subscribe`,
  `resources-unsubscribe`
- [ ] All tests pass
- [ ] `mvn verify` passes
