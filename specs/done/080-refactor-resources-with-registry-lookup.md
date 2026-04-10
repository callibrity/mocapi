# Refactor resources to interface-based registry lookup

## What to build

Replace `McpResourceProvider` iteration with direct registry lookup.
`McpResource` and `McpResourceTemplate` become interfaces (not records).
The registry owns URI routing.

### McpResource interface

```java
public interface McpResource {
    String uri();
    String name();
    String description();
    String mimeType();
    ReadResourceResponse read();
}
```

Each static resource is a bean implementing this interface.

### McpResourceTemplate interface

```java
public interface McpResourceTemplate {
    String uriTemplate();
    String name();
    String description();
    String mimeType();
    ReadResourceResponse read(Map<String, String> pathVariables);
}
```

Each template resource is a bean implementing this interface.

### ResourcesRegistry

Collects all `McpResource` and `McpResourceTemplate` beans. Owns routing:

```java
public class ResourcesRegistry {
    private final Map<String, McpResource> resources;           // exact URI match
    private final Map<UriTemplate, McpResourceTemplate> templates; // pattern match

    public ResourcesRegistry(List<McpResource> resources, 
                             List<McpResourceTemplate> templates, 
                             int pageSize) {
        this.resources = resources.stream()
            .collect(Collectors.toMap(McpResource::uri, r -> r));
        this.templates = templates.stream()
            .collect(Collectors.toMap(
                t -> new UriTemplate(t.uriTemplate()), t -> t));
    }

    public ReadResourceResponse read(String uri) {
        // 1. Exact match (O(1))
        McpResource exact = resources.get(uri);
        if (exact != null) return exact.read();

        // 2. Template match (iterate)
        for (var entry : templates.entrySet()) {
            if (entry.getKey().matches(uri)) {
                Map<String, String> vars = entry.getKey().match(uri);
                return entry.getValue().read(vars);
            }
        }
        throw new JsonRpcException(INVALID_PARAMS, "Resource not found: " + uri);
    }

    public boolean isEmpty() {
        return resources.isEmpty() && templates.isEmpty();
    }
}
```

Uses Spring's `UriTemplate` for pattern matching and variable extraction.

### Delete McpResourceProvider

Remove the `McpResourceProvider` interface entirely. Resources and templates
are registered as individual beans, not grouped by provider.

### Update auto-configuration

`MocapiResourcesAutoConfiguration` injects `List<McpResource>` and
`List<McpResourceTemplate>` instead of `List<McpResourceProvider>`.

### Update McpResourceMethods

`resources/list` returns resources from registry.
`resources/templates/list` returns templates from registry.
`resources/read` calls `registry.read(uri)`.

No more provider iteration.

### Update conformance tools

The conformance resource tools currently implement `McpResourceProvider`.
Refactor them into individual `McpResource` and `McpResourceTemplate` beans.

### Pagination

`listResources` and `listResourceTemplates` paginate from the registry's
collections. Same cursor-based pagination as before.

## Acceptance criteria

- [ ] `McpResource` is an interface with `uri()`, `name()`, `description()`, `mimeType()`, `read()`
- [ ] `McpResourceTemplate` is an interface with `uriTemplate()`, `name()`, `description()`, `mimeType()`, `read(Map<String, String>)`
- [ ] Registry uses `Map<String, McpResource>` for exact match
- [ ] Registry uses `Map<UriTemplate, McpResourceTemplate>` for pattern match
- [ ] Exact match checked first, template iteration as fallback
- [ ] `UriTemplate.match()` extracts path variables passed to template's `read()`
- [ ] `McpResourceProvider` deleted
- [ ] Auto-config injects `List<McpResource>` and `List<McpResourceTemplate>`
- [ ] Conformance tools refactored to individual beans
- [ ] All conformance tests pass
- [ ] `mvn verify` passes
