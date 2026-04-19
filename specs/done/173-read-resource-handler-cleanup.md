# Collapse McpResource SPI into a concrete ReadResourceHandler

## What to build

Same cleanup as specs 170 / 171, applied to fixed (non-templated)
resources: delete `McpResource` and `McpResourceProvider`, merge
`AnnotationMcpResource` into a concrete `ReadResourceHandler` built
directly from `@ResourceService` / `@ResourceMethod` scanning.
`McpResourcesService` holds a `Map<String, ReadResourceHandler>`
keyed by resource URI.

Resource templates (URIs with path variables) are a separate kind;
they're covered in spec 173 as `ReadResourceTemplateHandler`.

## File-level changes

### Delete

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/McpResource.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/McpResourceProvider.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/annotation/AnnotationMcpResource.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/ResourceServiceMcpResourceProvider.java`
- Their associated tests.

### Create

`mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ReadResourceHandler.java`:

```java
public final class ReadResourceHandler {
  private final Resource descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<Object> invoker;

  ReadResourceHandler(Resource descriptor, Method method, Object bean, MethodInvoker<Object> invoker) { ... }

  public Resource descriptor() { return descriptor; }
  public String uri() { return descriptor.uri(); }
  public Method method() { return method; }
  public Object bean() { return bean; }

  public ReadResourceResult read() {
    return (ReadResourceResult) invoker.invoke(null);
  }
}
```

`mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ReadResourceHandlers.java`:
static `discover(...)` factory that walks every `@ResourceService`
bean for `@ResourceMethod` methods and returns
`List<ReadResourceHandler>`.

### Modify

- `McpResourcesService`: constructor takes
  `List<ReadResourceHandler>`. Internally keeps a
  `Map<String, ReadResourceHandler>` keyed by URI. `readResource(uri)`
  looks up the handler and calls `read()`.
- `MocapiServerResourcesAutoConfiguration`: drop the
  `ResourceServiceMcpResourceProvider` bean; add a
  `List<ReadResourceHandler>` bean sourced from
  `ReadResourceHandlers.discover(...)`.

### Tests

- `AnnotationResourceTest` → `ReadResourceHandlerTest`.
- `McpResourcesServiceTest` update constructor.
- `ResourceServiceMcpResourceProviderTest` deleted or repurposed.
- Compliance tests updated to new type names.

## Acceptance criteria

- [ ] `McpResource` and `McpResourceProvider` removed.
- [ ] `AnnotationMcpResource` and `ResourceServiceMcpResourceProvider`
      removed.
- [ ] `ReadResourceHandler` + `ReadResourceHandlers` in
      `com.callibrity.mocapi.server.resources`.
- [ ] `McpResourcesService` dispatches through the new handler map.
- [ ] Resource templates (spec 173) remain entirely untouched by
      this change.
- [ ] All resource-related tests pass under new type names.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] No external behavior change for `resources/list` or
      `resources/read` against fixed URIs.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Breaking changes`:
      entry mirroring 170 / 171.
- [ ] `docs/handlers.md` gains a resources section.

## Commit

Suggested commit message:

```
Collapse McpResource SPI into concrete ReadResourceHandler

Continues the 170–173 handler-cleanup series for fixed resources.
McpResource + McpResourceProvider interfaces removed;
AnnotationMcpResource and ResourceServiceMcpResourceProvider folded
into ReadResourceHandler + ReadResourceHandlers#discover.
McpResourcesService dispatches through a Map<String, ReadResourceHandler>
keyed by URI. Resource templates are handled separately in spec 173.

BREAKING: McpResource and McpResourceProvider interfaces removed.
```
