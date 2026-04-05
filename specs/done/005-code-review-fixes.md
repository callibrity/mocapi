# Fix code review issues (prompts module already removed)

## What to build

Fix all remaining issues from `CODE_REVIEW.md` that still apply after the prompts module
has been removed. This combines the critical, major, and minor fixes into a single spec
since removing prompts eliminated about half the issues.

### Critical / Major fixes

**MAJOR-1: Origin validation is bypassable and non-configurable**

**File:** `McpStreamingController.java`, `MocapiProperties.java`

`origin.contains("localhost")` matches `evil.localhost.attacker.com`. Fix by parsing the
Origin header as a URI and comparing the host component. Make allowed origins configurable
via `mocapi.allowed-origins` property in `MocapiProperties` (default: `localhost`,
`127.0.0.1`, `[::1]`). Wire the list through to the controller.

**MAJOR-2: Unchecked cast and null dereference on arguments**

**File:** `McpStreamingController.java`

In `tools/call`: `(ObjectNode) params.get("arguments")` fails on null, NullNode, or
non-object types. Use `params.path("arguments")` and check `isObject()` before casting,
defaulting to an empty `ObjectNode`.

**MAJOR-3 + MINOR-6: @EnableScheduling and @Component on McpSessionManager**

**Files:** `MocapiAutoConfiguration.java`, `McpSessionManager.java`

Remove `@EnableScheduling` from `MocapiAutoConfiguration`. Have `McpSessionManager` create
its own internal `ScheduledExecutorService` with a daemon thread for session cleanup.
Add a `shutdown()` method and register it as the bean's `destroyMethod`. Remove
`@Component` from `McpSessionManager`.

**MAJOR-5: Race condition in cleanupInactiveSessions**

**File:** `McpSessionManager.java`

Replace the manual for-loop with
`sessions.entrySet().removeIf(e -> e.getValue().isInactive(sessionTimeoutSeconds))`.

**MAJOR-6: No JSON-RPC request envelope validation**

**File:** `McpStreamingController.java`

Validate the request body before processing:
1. `jsonrpc` field must equal `"2.0"`
2. `method` field must be present and non-empty
3. `id` must be a string, number, or null (not object or array)

**MAJOR-8: Type-safe describe() return**

**Files:** `McpServerCapability.java`, `McpToolsCapability.java`

Create a `CapabilityDescriptor` marker interface. Change `McpServerCapability.describe()`
return type from `Object` to `CapabilityDescriptor`. Update `ToolsCapabilityDescriptor`
to implement it.

### Minor fixes

**MINOR-1: Default protocol version**

**File:** `McpStreamingController.java`

Change `DEFAULT_PROTOCOL_VERSION` to reference `McpServer.PROTOCOL_VERSION`.

**MINOR-4: Error message leaks internal details**

**File:** `McpStreamingController.java`

Change `"Internal error: " + e.getMessage()` to `"Internal error"`.

**MINOR-5: Missing @ConditionalOnMissingBean**

**File:** `MocapiToolsAutoConfiguration.java`

Add `@ConditionalOnMissingBean` to `methodSchemaGenerator` and
`annotationMcpToolProviderFactory` beans.

**MINOR-9: @Inherited on @Tool**

**File:** `Tool.java`

Remove `@Inherited` from `@Tool` (method-level, no-op per Java spec).

**MINOR-12: Test uses literal protocol version**

**File:** `McpServerTest.java`

Replace literal string with `McpServer.PROTOCOL_VERSION`.

**SUGGESTION-1: NON_NULL Jackson customizer is global**

**File:** `MocapiAutoConfiguration.java`

Replace the global Jackson customizer with a dedicated `mcpObjectMapper` bean scoped to
MCP serialization. Update `McpStreamingController` to use it via `@Qualifier`.

## Acceptance criteria

- [ ] Origin validation parses URI host; `mocapi.allowed-origins` property exists with sensible defaults
- [ ] `tools/call` handles null/missing/non-object arguments gracefully
- [ ] `@EnableScheduling` is not present anywhere
- [ ] `@Component` is not on `McpSessionManager`
- [ ] `McpSessionManager` manages its own cleanup executor with `shutdown()` destroy method
- [ ] `cleanupInactiveSessions` uses `removeIf`
- [ ] POST handler validates `jsonrpc: "2.0"`, `method` presence, and `id` type
- [ ] `McpServerCapability.describe()` returns `CapabilityDescriptor` not `Object`
- [ ] `DEFAULT_PROTOCOL_VERSION` references `McpServer.PROTOCOL_VERSION`
- [ ] No `e.getMessage()` in client-facing JSON-RPC error responses
- [ ] `methodSchemaGenerator` and `annotationMcpToolProviderFactory` have `@ConditionalOnMissingBean`
- [ ] `@Inherited` is not on `@Tool`
- [ ] `McpServerTest` uses `McpServer.PROTOCOL_VERSION` constant
- [ ] MCP-specific `mcpObjectMapper` bean exists; no global Jackson customizer
- [ ] `mvn verify` passes

## Implementation notes

- Specs 001-004 must be complete (RipCurl removed, Spring Boot 4.0.5, prompts removed).
- Use `McpException` from spec 001 in `McpStreamingController` catch blocks.
- For MAJOR-8, put `CapabilityDescriptor` in `com.callibrity.mocapi.server`.
- For SUGGESTION-1, the `mcpObjectMapper` should be
  `@ConditionalOnMissingBean(name = "mcpObjectMapper")`.
