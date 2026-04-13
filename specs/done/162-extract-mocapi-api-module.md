# Extract mocapi-api module for user-facing types

## What to build

Extract the user-facing API — annotations, interfaces, and SPIs that tool/prompt/resource
authors depend on — into a new `mocapi-api` module. This enables multiple server
implementations (stateful SSE, stateless, etc.) to share the same tool authoring API.

Tool authors depend on `mocapi-api`. They never import `mocapi-server` directly.

### New module: mocapi-api

**Maven coordinates:** `com.callibrity.mocapi:mocapi-api`

**Dependencies:** `mocapi-model` only (for MCP types like `ElicitRequestFormParams`,
`ElicitResult`, `CreateMessageResult`, `LoggingLevel`, etc.)

### Types to move from mocapi-server to mocapi-api

**Annotations** (from `com.callibrity.mocapi.server.tools.annotation`):
- `@ToolService` — marks a class as MCP tool container
- `@ToolMethod` — marks a method as an MCP tool
- `@McpToolParams` — marks a record parameter for schema generation

**Tool interfaces** (from `com.callibrity.mocapi.server.tools`):
- `McpToolContext` — user-facing context (sendProgress, log, elicit, sample).
  Move the interface only. The `CURRENT` ScopedValue stays here too since tools
  read it. `DefaultMcpToolContext` stays in mocapi-server.
- `McpTool` — tool SPI (descriptor + call)
- `McpToolProvider` — provides a list of McpTool instances

**Prompt interfaces** (from `com.callibrity.mocapi.server.prompts`):
- `McpPrompt` — prompt SPI
- `McpPromptProvider` — provides McpPrompt instances

**Resource interfaces** (from `com.callibrity.mocapi.server.resources`):
- `McpResource` — resource SPI
- `McpResourceTemplate` — resource template SPI
- `McpResourceProvider` — provides McpResource instances
- `McpResourceTemplateProvider` — provides McpResourceTemplate instances

### Package structure in mocapi-api

Keep the same package names so imports don't change for existing users:
- `com.callibrity.mocapi.server.tools.annotation` — ToolService, ToolMethod, McpToolParams
- `com.callibrity.mocapi.server.tools` — McpToolContext, McpTool, McpToolProvider
- `com.callibrity.mocapi.server.prompts` — McpPrompt, McpPromptProvider
- `com.callibrity.mocapi.server.resources` — McpResource, McpResourceTemplate,
  McpResourceProvider, McpResourceTemplateProvider

Wait — using `com.callibrity.mocapi.server.*` packages in a module called `mocapi-api`
is confusing. Consider renaming to `com.callibrity.mocapi.api.*`:
- `com.callibrity.mocapi.api.tools` — ToolService, ToolMethod, McpToolParams, McpToolContext,
  McpTool, McpToolProvider
- `com.callibrity.mocapi.api.prompts` — McpPrompt, McpPromptProvider
- `com.callibrity.mocapi.api.resources` — McpResource, McpResourceTemplate,
  McpResourceProvider, McpResourceTemplateProvider

Use new `com.callibrity.mocapi.api` packages. This is a pre-1.0 project.

### Types that stay in mocapi-server

- `McpToolsService`, `McpPromptsService`, `McpResourcesService` — framework dispatch
- `DefaultMcpToolContext` — implementation of McpToolContext
- `McpToolContextResolver` — ScopedValue resolver
- `AnnotationMcpTool`, `Names` — annotation processing
- `McpToolParamsResolver` — parameter resolution
- `MethodSchemaGenerator`, `DefaultMethodSchemaGenerator`, `Parameters` — schema gen
- `MocapiServerToolsAutoConfiguration` — auto-config for tools
- All session, server, transport, lifecycle code

### Update mocapi-server dependency

`mocapi-server` depends on `mocapi-api` (and `mocapi-model`). It imports the
interfaces from `mocapi-api` and provides implementations.

### Update starters

The starter POMs should pull in both `mocapi-api` and `mocapi-server` transitively.
Users who only write tools (e.g., in a shared library) can depend on `mocapi-api`
alone.

### Update examples

Examples depend on the starter, which transitively includes `mocapi-api`. No import
changes needed if we keep existing package names. If we use new packages, update
all examples.

## Acceptance criteria

- [ ] `mocapi-api` module exists with its own POM
- [ ] All user-facing annotations and interfaces are in mocapi-api
- [ ] `mocapi-server` depends on `mocapi-api`
- [ ] `mocapi-api` depends only on `mocapi-model`
- [ ] No circular dependencies
- [ ] All tests pass across all modules
- [ ] All compat tests pass
- [ ] npx conformance suite passes
- [ ] Examples compile and run

## Implementation notes

- This is a module extraction, not a rewrite. Move files, update imports, fix POMs.
- If keeping existing package names: this is a split-package situation. Make sure
  no two modules have the same package (Java module system won't allow it).
  Actually, split packages are fine without JPMS modules (which we don't use).
- If using new packages: a find-and-replace across the codebase for the old imports.
  Update all test files, examples, and compat tests.
- The `McpToolContext.CURRENT` ScopedValue field means the interface has a dependency
  on the ScopedValue API — that's fine, it's a JDK API.
- **`@ToolService` is a plain marker annotation — NOT a `@Component` stereotype.**
  It has no Spring dependency. This means `mocapi-api` has zero Spring dependencies.
  The annotation is just:
  ```java
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ToolService {}
  ```
  Users can register tool services however they want:
  - Component scan: annotate with both `@ToolService` and `@Component` (or `@Service`)
  - `@Bean` method: return a `@ToolService`-annotated class from a `@Bean` factory
  - Programmatic: register directly in the application context
  The server's auto-config discovers `@ToolService` beans using
  `context.getBeansWithAnnotation(ToolService.class)` — it doesn't care how they
  got into the context.
- Schema generation (`MethodSchemaGenerator`) stays in mocapi-server — it's a
  framework concern, not a user API concern.
- Do NOT create a stateless server module yet — just extract the API.
