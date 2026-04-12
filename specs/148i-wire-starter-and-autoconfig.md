# Wire starter and auto-configuration

## What to build

Update `mocapi-spring-boot-starter` to depend on both
`mocapi-protocol` and `mocapi-transport-streamable-http`. Add
auto-configuration for the protocol beans (`DefaultMcpProtocol`,
`McpSessionService`, registries) in `mocapi-protocol` and for
the controller in `mocapi-transport-streamable-http`.

### Starter pom

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-protocol</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-transport-streamable-http</artifactId>
  <version>${project.version}</version>
</dependency>
<!-- Keep mocapi-core for now — it still provides the old
     controller path. Will be removed in spec 148j. -->
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-core</artifactId>
  <version>${project.version}</version>
</dependency>
```

### Auto-configuration

Register the new protocol and transport beans in
`AutoConfiguration.imports` files in each module. Ensure the
new protocol beans are `@ConditionalOnMissingBean` so they
don't conflict with the old beans in mocapi-core during the
transition.

### Verification

Boot mocapi-compat against the NEW protocol + transport path
and run the MCP conformance suite. All 39 tests must pass.

## Acceptance criteria

- [ ] Starter depends on both new modules + mocapi-core.
- [ ] Auto-configuration registers protocol beans.
- [ ] Auto-configuration registers transport beans.
- [ ] `mvn verify` passes.
- [ ] MCP conformance suite: 39/39 against the new path.
- [ ] **mocapi-core is not modified.**
