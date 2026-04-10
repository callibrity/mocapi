# Fix initialize capability detection

## What to build

The `initializeResponse` bean uses `@Nullable` registries to decide which
capabilities to advertise. The registries are always created — they're never
null. Instead, ask the registry if it has content.

### Add isEmpty() to registries

Add `isEmpty()` to `ToolsRegistry`, `ResourcesRegistry`, and `PromptsRegistry`.

### Fix initializeResponse bean

Remove `@Nullable` on registry parameters. Use `isEmpty()` instead of null checks:

```java
@Bean
@ConditionalOnMissingBean
public InitializeResponse initializeResponse(
    ToolsRegistry toolsRegistry,
    ResourcesRegistry resourcesRegistry,
    PromptsRegistry promptsRegistry,
    @Nullable BuildProperties buildProperties) {
  String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
  ToolsCapabilityDescriptor tools =
      toolsRegistry.isEmpty() ? null : new ToolsCapabilityDescriptor(false);
  ResourcesCapabilityDescriptor resources =
      resourcesRegistry.isEmpty() ? null : new ResourcesCapabilityDescriptor(true, false);
  PromptsCapabilityDescriptor prompts =
      promptsRegistry.isEmpty() ? null : new PromptsCapabilityDescriptor(false);
  // ...
}
```

`BuildProperties` stays `@Nullable` — it's genuinely optional (only present
with spring-boot-info plugin).

### Remove Nullable import if unused

If `@Nullable` is no longer used elsewhere in the file, remove the import.

## Acceptance criteria

- [ ] `isEmpty()` on ToolsRegistry, ResourcesRegistry, PromptsRegistry
- [ ] `@Nullable` removed from registry parameters
- [ ] Capabilities only advertised when registry has content
- [ ] `BuildProperties` stays `@Nullable`
- [ ] All tests pass
- [ ] `mvn verify` passes
