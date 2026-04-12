# Delete AnnotationMcpToolProviderFactory

## What to build

Delete the unnecessary `AnnotationMcpToolProviderFactory` interface
and `DefaultAnnotationMcpToolProviderFactory` implementation.
Inline the tool creation logic directly where it's called.

### What to delete

- `AnnotationMcpToolProviderFactory` interface
- `DefaultAnnotationMcpToolProviderFactory` class
- Any auto-config `@Bean` that registers the factory
- Any test that tests the factory in isolation

### What to change

Wherever the factory is called (likely the auto-config or
`ToolServiceMcpToolProvider`), replace:

```java
// Before — pointless indirection
McpToolProvider provider = factory.create(targetBean);
```

With a direct call:

```java
// After — just create the tools directly
List<AnnotationMcpTool> tools = AnnotationMcpTool.createTools(generator, invokerFactory, targetBean);
```

Or whatever the equivalent direct creation call is in the
current codebase.

## Acceptance criteria

- [ ] `AnnotationMcpToolProviderFactory` interface is deleted.
- [ ] `DefaultAnnotationMcpToolProviderFactory` is deleted.
- [ ] No references to either class remain.
- [ ] Tool creation still works — tools register correctly.
- [ ] `mvn verify` passes.
