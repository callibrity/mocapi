# Merge autoconfigure into core

## What to build

Mocapi is a Spring Boot framework — there's no use case for core without Spring Boot.
Merge `mocapi-autoconfigure` into `mocapi-core` so there's one module with everything.
Organize by domain, not by technical layer.

### Move all autoconfigure classes into core

Move every class from `mocapi-autoconfigure` into `mocapi-core`, placing each in its
domain package:

**`session/`**
- `InMemoryMcpSessionStore` (was autoconfigure/session or http)
- `McpSessionMethods` (@JsonRpcService for initialize, initialized, ping)

**`tools/`**
- `McpToolMethods` (@JsonRpcService for tools/list, tools/call)
- `ToolServiceMcpToolProvider` (discovers @ToolService beans)
- `MocapiToolsProperties` (tools config)

**`stream/`**
- `DefaultMcpStreamContext` (Odyssey + Substrate implementation)
- `McpStreamContextParamResolver` (ScopedValue-based resolver)

**`transport/http/`**
- `StreamableHttpController` (thin HTTP adapter)

**Root package:**
- `MocapiAutoConfiguration` (wires all beans)
- `MocapiProperties` (top-level config)

### Move autoconfigure dependencies into core pom

Merge all dependencies from `mocapi-autoconfigure/pom.xml` into
`mocapi-core/pom.xml`:
- `spring-boot-autoconfigure`
- `spring-boot-starter-webmvc`
- `spring-boot-configuration-processor` (optional)
- `ripcurl-autoconfigure`
- `odyssey-core`
- Any test dependencies

### Move autoconfigure resources into core

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `mocapi-defaults.properties`
- Any other resource files

### Move all autoconfigure tests into core

Move test classes into appropriate test packages in `mocapi-core`, matching the
domain structure.

### Delete `mocapi-autoconfigure` module

Remove the module directory and remove it from the parent pom's `<modules>` list.

### Update `mocapi-spring-boot-starter`

The starter now just pulls in `mocapi-core`. No `mocapi-autoconfigure` dependency.

### Update parent pom

Remove `mocapi-autoconfigure` from `<modules>`.

### Update example app

Update `mocapi-example` to depend on the starter (which pulls in core). Should
work without changes since the packages and class names don't change — just the
module they live in.

## Acceptance criteria

- [ ] `mocapi-autoconfigure` module is deleted
- [ ] All classes from autoconfigure are in `mocapi-core` in their domain packages
- [ ] All autoconfigure resources are in `mocapi-core`
- [ ] All autoconfigure test classes are in `mocapi-core`
- [ ] `MocapiAutoConfiguration` is in `mocapi-core` and registered in
      `AutoConfiguration.imports`
- [ ] `mocapi-core/pom.xml` has all required dependencies
- [ ] Parent pom no longer references `mocapi-autoconfigure`
- [ ] Starter depends on `mocapi-core` only
- [ ] Example app works without changes
- [ ] `mvn verify` passes

## Implementation notes

- This is a structural merge — no behavior changes. Classes move, imports update,
  but functionality is identical.
- The auto-configuration registration file
  (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)
  must reference the full class name of `MocapiAutoConfiguration` in its new location.
- If any classes had package-private visibility assumptions based on being in the
  same module, those are now naturally satisfied since everything is in one module.
- Do this in one commit to avoid a broken intermediate state.
