# Consolidate modules and rename controller

## What to build

Mocapi's multi-module structure was designed for mix-and-match capabilities, but in
practice we always need tools and there's no benefit to the separation. Consolidate
`mocapi-tools` into `mocapi-core` and rename the controller to reflect the transport
it implements.

### Merge `mocapi-tools` into `mocapi-core`

Move all classes from `mocapi-tools` into `mocapi-core` under a `tools` subpackage:

- `com.callibrity.mocapi.tools.*` → `com.callibrity.mocapi.core.tools.*` (or keep
  `com.callibrity.mocapi.tools.*` in core — package doesn't need to change if the
  module does)

Move dependencies from `mocapi-tools/pom.xml` into `mocapi-core/pom.xml`:
- `jsonschema-generator`
- `jsonschema-module-swagger-2`
- `jsonschema-module-jackson`
- `jsonschema-module-jakarta-validation`
- `json-sKema`
- `jakarta.validation-api`

Delete the `mocapi-tools` module directory and remove it from the parent pom's
`<modules>` list.

Update `mocapi-autoconfigure/pom.xml` to remove the optional `mocapi-tools` dependency
(it's now in core).

### Merge `mocapi-tools-test` if it exists

Check if `mocapi-tools-test` exists and merge any test utilities into
`mocapi-core`'s test scope.

### Rename controller

Rename `McpStreamingController` to `StreamableHttpController` (or `McpHttpController`).
The class implements the MCP Streamable HTTP transport — the name should reflect that.

Move it from `com.callibrity.mocapi.autoconfigure.sse` to
`com.callibrity.mocapi.autoconfigure.http`. The `sse` package name is misleading —
the controller handles both JSON and SSE responses. It's an HTTP transport, not just
SSE.

Rename any related classes in the `sse` package:
- `McpStreamContextParamResolver` → stays, but moves to `http` package
- `DefaultMcpStreamContext` → stays, but moves to `http` package
- `InMemoryMcpSessionStore` → could stay in `autoconfigure` root since it's not
  transport-specific

### Update `MocapiAutoConfiguration`

Update bean references to use new class names and packages.

### Update `mocapi-spring-boot-starter`

Verify the starter still pulls in the right modules. With tools merged into core,
the starter just needs core + autoconfigure.

### Clean up parent pom

Remove `mocapi-tools` and `mocapi-tools-test` from `<modules>`.

## Acceptance criteria

- [ ] All `mocapi-tools` classes are in `mocapi-core`
- [ ] All `mocapi-tools` dependencies are in `mocapi-core/pom.xml`
- [ ] `mocapi-tools` module is deleted
- [ ] `mocapi-tools-test` module is deleted (if it exists)
- [ ] Parent pom no longer references deleted modules
- [ ] Controller is renamed (e.g., `StreamableHttpController`)
- [ ] Controller package is `com.callibrity.mocapi.autoconfigure.http`
- [ ] Related classes moved from `sse` to `http` package
- [ ] `MocapiAutoConfiguration` updated for new names/packages
- [ ] Starter module works correctly
- [ ] No broken imports or references
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- This is a structural refactoring — no behavior changes.
- The tools package can keep its original package name
  (`com.callibrity.mocapi.tools`) even though it's now in `mocapi-core`. This avoids
  changing every import in every file that references tools classes.
- The `sse` package should be empty after the move. Delete it.
- The example module (`mocapi-example`) may need import updates.
- Consider whether `McpRequestValidator` should also move — it's in `mocapi-core`
  under `server` package, which is fine.
