# Fix critical and major issues from code review

## What to build

Fix all critical and major issues identified in `CODE_REVIEW.md`. These are bugs, security
issues, and correctness problems that must be resolved.

### CRITICAL-1: Double flatMap drops all prompts

**File:** `PromptServiceMcpPromptProvider.java`

The `initialize()` method has two `.flatMap()` calls. The first correctly produces
`Stream<AnnotationMcpPrompt>`. The second calls `createPrompts()` on each
`AnnotationMcpPrompt` object (which has no `@Prompt` methods), producing empty streams
and silently discarding all prompts. Remove the second `flatMap`.

### MAJOR-1: Origin validation is bypassable and non-configurable

**File:** `McpStreamingController.java`

`origin.contains("localhost")` matches `evil.localhost.attacker.com`. Fix by parsing the
Origin header as a URI and comparing the host component. Make allowed origins configurable
via `mocapi.allowed-origins` property in `MocapiProperties` (default: `localhost`,
`127.0.0.1`, `[::1]`).

### MAJOR-2: Unchecked cast and null dereference on arguments

**File:** `McpStreamingController.java`

In `tools/call`: `(ObjectNode) params.get("arguments")` fails on null, NullNode, or
non-object types. Use `params.path("arguments")` and check `isObject()` before casting,
defaulting to an empty `ObjectNode`.

In `prompts/get`: `objectMapper.convertValue(params.get("arguments"), Map.class)` uses
raw type and fails on null. Use `TypeReference<Map<String, String>>` with a null guard.

### MAJOR-3 + MINOR-6: @EnableScheduling and @Component on McpSessionManager

**File:** `MocapiAutoConfiguration.java`, `McpSessionManager.java`

`@EnableScheduling` globally activates scheduling in all consumer applications. Remove it.
Instead, have `McpSessionManager` create its own internal `ScheduledExecutorService` with
a daemon thread for session cleanup. Add a `shutdown()` method and register it as the
bean's `destroyMethod`. Also remove `@Component` from `McpSessionManager` since it's
created via `@Bean`.

### MAJOR-5: Race condition in cleanupInactiveSessions

**File:** `McpSessionManager.java`

Mutating `ConcurrentHashMap` during `entrySet()` iteration is weakly consistent. Replace
the manual for-loop with `sessions.entrySet().removeIf(e -> e.getValue().isInactive(sessionTimeoutSeconds))`,
which is atomic per entry on `ConcurrentHashMap`.

### MAJOR-6: No JSON-RPC request envelope validation

**File:** `McpStreamingController.java`

Validate the request body before processing:
1. `jsonrpc` field must equal `"2.0"`
2. `method` field must be present and non-empty
3. `id` must be a string, number, or null (not object or array)

Return appropriate JSON-RPC error responses for invalid envelopes.

### MAJOR-7: InvocationTargetException swallowed in AnnotationMcpPrompt

**File:** `AnnotationMcpPrompt.java`

The catch block catches `ReflectiveOperationException` which includes
`InvocationTargetException`. If a prompt method throws `McpInvalidParamsException`, it
gets wrapped as an internal error (-32603) instead of propagating with its original code
(-32602). Fix by catching `InvocationTargetException` separately, unwrapping the cause,
and re-throwing if it's a `RuntimeException`.

### MAJOR-8: Type-safe describe() return

**Files:** `McpServerCapability.java`, `McpToolsCapability.java`, `McpPromptsCapability.java`

`McpServerCapability.describe()` returns raw `Object`. Create a marker interface
`CapabilityDescriptor` and change the return type. Update both `ToolsCapabilityDescriptor`
and `PromptsCapabilityDescriptor` records to implement it.

## Acceptance criteria

- [ ] `PromptServiceMcpPromptProvider.initialize()` has exactly one `flatMap` call
- [ ] Origin validation parses URI and compares host; `mocapi.allowed-origins` property exists
- [ ] `tools/call` handles null/missing/non-object arguments without throwing ClassCastException or NullPointerException
- [ ] `prompts/get` uses `TypeReference<Map<String, String>>` and handles null arguments
- [ ] `@EnableScheduling` is not present anywhere in the project
- [ ] `@Component` is not present on `McpSessionManager`
- [ ] `McpSessionManager` manages its own cleanup executor with a `shutdown()` method
- [ ] `cleanupInactiveSessions` uses `removeIf` instead of manual iteration+removal
- [ ] POST handler validates `jsonrpc: "2.0"`, `method` presence, and `id` type
- [ ] `AnnotationMcpPrompt.getPrompt()` unwraps `InvocationTargetException` and re-throws `RuntimeException` causes directly
- [ ] `McpServerCapability.describe()` returns `CapabilityDescriptor` instead of `Object`
- [ ] `mvn verify` passes

## Implementation notes

- This spec depends on specs 001-003 being complete (MCP exception types exist, RipCurl
  removed, Spring Boot 4.0.5 in place).
- The `McpStreamingController` catch blocks may reference the old exception types — use
  `McpException` from spec 001.
- For MAJOR-1, the `MocapiProperties` class already exists — just add the
  `allowedOrigins` field with a default list. Wire it through to the controller.
- For MAJOR-3, use `Executors.newSingleThreadScheduledExecutor` with a daemon thread
  factory. Register `destroyMethod = "shutdown"` on the `@Bean` definition in
  `MocapiAutoConfiguration`.
- For MAJOR-8, put `CapabilityDescriptor` in `com.callibrity.mocapi.server` alongside
  `McpServerCapability`.
