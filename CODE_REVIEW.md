# Mocapi — Comprehensive Code Review

**Reviewer:** Claude (claude-sonnet-4-6)
**Date:** 2026-04-04
**Revision:** `0457a5c` (branch `claude/modest-colden`)
**Protocol Target:** MCP 2025-11-25
**Scope:** All modules — core, tools, prompts, autoconfigure, starter, example; Maven configuration; tests; documentation

---

## Executive Summary

Mocapi is a well-conceived Spring Boot framework for building MCP servers with a clean annotation-driven API. The module structure is logical, the developer experience is pleasant, and the core abstractions are sound. The JSON schema generation, input validation, SSE infrastructure, and Spring auto-configuration are all thoughtfully designed.

However, there is **one critical bug that makes the primary `@PromptService`/`@Prompt` annotation path completely non-functional** — all prompts are silently discarded at startup. Beyond that, the HTTP layer (`McpStreamingController`) has a cluster of security and correctness issues: bypassable origin validation, an unchecked cast that causes `NullPointerException`, unvalidated JSON-RPC envelopes, and error messages that leak exception details to clients. The GET endpoint for server-initiated SSE notifications is a stub returning 405. Test coverage for the SSE infrastructure is entirely absent.

---

## Severity Legend

| Label | Meaning |
|-------|---------|
| **CRITICAL** | Breaks functionality or creates serious security risk |
| **MAJOR** | Significant correctness, security, or design problem |
| **MINOR** | Code quality, subtle correctness issue, or missed best practice |
| **SUGGESTION** | Improvement worth considering |

---

## CRITICAL Issues

### CRITICAL-1: Double `flatMap` Silently Drops All Registered Prompts

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/PromptServiceMcpPromptProvider.java:51-62`

```java
@PostConstruct
public void initialize() {
    this.prompts = context.getBeansWithAnnotation(PromptService.class).entrySet().stream()
            .flatMap(entry -> {
                var beanName = entry.getKey();
                var bean = entry.getValue();
                log.info("Registering MCP prompts for @{} bean \"{}\"...", ...);
                var list = AnnotationMcpPrompt.createPrompts(bean);  // correct
                list.forEach(prompt -> log.info("\tRegistered MCP prompt: \"{}\".", prompt.name()));
                return list.stream();  // produces Stream<AnnotationMcpPrompt>
            })
            // *** BUG: second flatMap receives AnnotationMcpPrompt objects, not beans ***
            .flatMap(targetObject -> AnnotationMcpPrompt.createPrompts(targetObject).stream())
            .toList();
}
```

The first `flatMap` correctly produces `Stream<AnnotationMcpPrompt>`. The second `flatMap` on line 61 receives each `AnnotationMcpPrompt` instance as `targetObject` and calls `AnnotationMcpPrompt.createPrompts(targetObject)`. Since `AnnotationMcpPrompt` itself has no `@Prompt`-annotated methods, `createPrompts()` returns an empty list for every element — every element flatMaps to an empty stream — and `this.prompts` is always `[]`.

The log messages print correctly (because they run inside the first `flatMap`), giving the false impression that prompts were registered. Every `prompts/get` call then returns "Prompt not found."

Compare with the correct implementation in `ToolServiceMcpToolProvider.java:55-67`, which terminates after a single `flatMap`.

**Fix:** Remove line 61 entirely.

**Test gap:** The `CodeReviewPromptsIT` integration test exercises this path and should be failing against current code. A dedicated unit test for `PromptServiceMcpPromptProvider` with a real `@PromptService` bean should also be added.

---

## MAJOR Issues

### MAJOR-1: Origin Validation Is Bypassable and Non-Configurable

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:229-236`

```java
private boolean isValidOrigin(String origin) {
    if (origin == null) {
        return true;
    }
    return origin.contains("localhost") ||
            origin.contains("127.0.0.1") ||
            origin.contains("[::1]");
}
```

**Problem 1 — bypassable:** `origin.contains("localhost")` matches any string containing that substring, including `https://evil.localhost.attacker.com`. A proper check must parse the URI and compare the host component.

**Problem 2 — non-configurable:** Any non-localhost deployment (Docker containers, CI environments, staging, LAN) will have all requests rejected with no escape hatch. The MCP 2025-11-25 spec mandates Origin validation to prevent DNS rebinding attacks — it does not mandate localhost-only. The allowed origins should be configurable via a property (e.g., `mocapi.allowed-origins`).

### MAJOR-2: Unchecked Cast and Null Dereference on `arguments` in `tools/call`

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:188-193`

```java
case "tools/call" -> Optional.ofNullable(toolsCapability)
        .map(cap -> cap.callTool(
                params.path("name").asText(),
                (ObjectNode) params.get("arguments")  // unchecked cast
        ))
```

If a client sends `"arguments": null`, `params.get("arguments")` returns a `NullNode`, and `(ObjectNode) nullNode` is a valid Java cast (yields the `NullNode` object), which is then passed to `McpToolsCapability.callTool()`. Inside `validateInput()`, `new JSONObject(arguments.toString())` is called with a `NullNode` whose `toString()` is `"null"` — this produces an invalid JSON object and throws an unexpected exception.

If `"arguments"` is absent from params, `params.get("arguments")` returns `null`, and `(ObjectNode) null` is valid Java, but passing `null` to `callTool(String name, ObjectNode arguments)` causes `new JSONObject(null.toString())` to throw `NullPointerException`.

If `"arguments"` is a JSON array or string, the cast throws `ClassCastException` at the call site.

All three failure modes bypass the clean JSON-RPC error handling and surface as internal errors with unhelpful messages.

### MAJOR-3: `@EnableScheduling` Silently Activates Scheduling in All Consumer Applications

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiAutoConfiguration.java:38`

```java
@AutoConfiguration
@EnableScheduling  // ← activates scheduling globally
public class MocapiAutoConfiguration {
```

`@EnableScheduling` is a global switch. Any application using the mocapi starter will have Spring scheduling activated — this starts scheduler threads, registers `ScheduledAnnotationBeanPostProcessor`, and processes any `@Scheduled` annotations anywhere in the application context.

Applications that have intentionally not enabled scheduling, or that have their own scheduling configuration, are silently affected. The correct pattern is to programmatically schedule cleanup via a `ScheduledExecutorService` owned by `McpSessionManager`, without touching the application's scheduling infrastructure.

### MAJOR-4: GET Endpoint Returns 405 Instead of SSE Notification Stream

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:160-168`

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<?> handleGet(...) {
    log.debug("GET request received, but server-initiated notifications not yet implemented");
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(Map.of("error", "Server-initiated notifications not yet supported"));
}
```

The MCP 2025-11-25 Streamable HTTP transport requires the GET endpoint to establish a persistent SSE connection for server-initiated messages (notifications, progress updates, log messages). Returning 405 means any MCP client relying on server-push notifications fails. This should be documented prominently in the README as an architectural limitation, not just a debug log.

### MAJOR-5: Race Condition in `cleanupInactiveSessions`

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpSessionManager.java:129-142`

```java
@Scheduled(fixedRate = 300000)
public void cleanupInactiveSessions() {
    for (Map.Entry<String, McpSession> entry : sessions.entrySet()) {
        if (entry.getValue().isInactive(sessionTimeoutSeconds)) {
            sessions.remove(entry.getKey());  // mutating while iterating
            removedCount++;
        }
    }
}
```

Mutating a `ConcurrentHashMap` during `entrySet()` iteration produces weakly-consistent results: entries added during iteration may be missed or double-counted, and the count is unreliable. The correct idiom is:

```java
long removed = sessions.entrySet().removeIf(e -> e.getValue().isInactive(sessionTimeoutSeconds));
```

`removeIf` on `ConcurrentHashMap` is safe to call concurrently and is atomic per entry.

### MAJOR-6: No JSON-RPC Request Envelope Validation

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:79-82`

```java
JsonNode idNode = requestBody.get("id");
String method = requestBody.path("method").asText();
JsonNode params = requestBody.get("params");
```

Three omissions:

1. **`jsonrpc` field not checked.** The JSON-RPC 2.0 spec requires `"jsonrpc": "2.0"`. Non-compliant or misrouted requests are silently accepted.
2. **`method` field not validated.** `requestBody.path("method").asText()` returns `""` on a missing field; an empty method falls to the `default` case with `"Unknown method: "` — technically handled but nonsensical.
3. **`id` type not validated.** Per JSON-RPC 2.0, `id` must be a string, number, or null. An object or array `id` passes through and produces a malformed response.

### MAJOR-7: `AnnotationMcpPrompt.getPrompt()` Swallows JSON-RPC Exceptions from Prompt Methods

**File:** `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/annotation/AnnotationMcpPrompt.java:119-127`

```java
try {
    var result = method.invoke(targetObject, params);
    ...
} catch (ReflectiveOperationException e) {
    throw new JsonRpcInternalErrorException(String.format("Unable to get prompt \"%s\".", name), e);
}
```

`Method.invoke()` wraps user-thrown exceptions in `InvocationTargetException` (a subtype of `ReflectiveOperationException`). If a prompt method deliberately throws `JsonRpcInvalidParamsException` for a business validation error, it gets wrapped in `InvocationTargetException` → wrapped again in `JsonRpcInternalErrorException` → client receives error code -32603 (Internal Error) instead of -32602 (Invalid Params).

The fix is to unwrap `InvocationTargetException.getCause()` and re-throw it if it's already a `RuntimeException` (or specifically a `JsonRpcException`). The ripcurl `JsonMethodInvoker` used by tools likely handles this correctly — that's why tools don't exhibit this problem.

### MAJOR-8: `describe()` Returns Raw `Object`, Losing Type Safety

**File:** `mocapi-core/src/main/java/com/callibrity/mocapi/server/McpServerCapability.java`

```java
public interface McpServerCapability {
    String name();
    Object describe();  // raw Object
}
```

Both implementations return typed records (`ToolsCapabilityDescriptor`, `PromptsCapabilityDescriptor`) but the interface contract is `Object`. This loses compile-time safety at the interface boundary and makes the contract non-discoverable. A marker interface `CapabilityDescriptor` would make the contract explicit and allow for future reflection-based introspection.

---

## MINOR Issues

### MINOR-1: Default Protocol Version Lags Behind Current Version

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:67`

```java
private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";
```

When the `MCP-Protocol-Version` header is absent, the controller defaults to `"2025-03-26"` — an older protocol revision. `McpServer.PROTOCOL_VERSION` is `"2025-11-25"`. The default should at minimum be the current supported version, or the request should be rejected if the header is absent.

### MINOR-2: Raw `Map.class` in `convertValue` Loses Generic Type

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:199-200`

```java
objectMapper.convertValue(params.get("arguments"), Map.class)
```

Using raw `Map.class` produces an unchecked cast. The method signature `getPrompt(String, Map<String,String>)` expects `Map<String,String>`, but Jackson produces `Map<Object,Object>` from the raw type. Also, `params.get("arguments")` can return `null` (if the key is absent), and `convertValue(null, Map.class)` returns `null` — which then causes `NullPointerException` inside `AnnotationMcpPrompt.getPrompt()` when `arguments.get(argument.name())` is called.

Fix: use `objectMapper.convertValue(params.path("arguments"), new TypeReference<Map<String, String>>() {})` with a null/missing-key guard.

### MINOR-3: Unbounded SSE Event Queue in `McpSession`

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpSession.java:84-87`

```java
public void storeEvent(String streamId, SseEvent event) {
    updateActivity();
    streamEvents.computeIfAbsent(streamId, k -> new ConcurrentLinkedQueue<>()).add(event);
}
```

Events are stored for resumability but never evicted until `clearStream()` is called (on stream completion). For streams that never complete cleanly (client disconnects before `onCompletion` fires), the queue grows without bound. Under sustained load this is an OOM vector. A bounded queue (keep last N events, or events within a TTL) is safer for production.

### MINOR-4: Error Messages Leak Internal Exception Details to Clients

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:146`

```java
ObjectNode errorResponse = createErrorResponse(idNode, -32603, "Internal error: " + e.getMessage());
```

Including `e.getMessage()` in the JSON-RPC error response can leak class names, internal paths, or configuration values. The full exception is already logged on line 145. The client response should be just `"Internal error"` with no detail.

### MINOR-5: `@ConditionalOnMissingBean` Missing from Framework Beans

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/tools/MocapiToolsAutoConfiguration.java:62-69`

```java
@Bean
MethodSchemaGenerator methodSchemaGenerator(ObjectMapper mapper) { ... }

@Bean
public AnnotationMcpToolProviderFactory annotationMcpToolProviderFactory(...) { ... }
```

Neither bean is guarded with `@ConditionalOnMissingBean`. Spring Boot auto-configuration convention is that framework beans yield to user-defined ones. Users wanting a custom schema generator must exclude the entire auto-configuration rather than simply defining their own `MethodSchemaGenerator` bean. Adding `@ConditionalOnMissingBean` to both allows drop-in replacement.

### MINOR-6: `@Component` on `McpSessionManager` Is Misleading

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpSessionManager.java:39`

```java
@Component
public class McpSessionManager {
```

`McpSessionManager` is instantiated by a `@Bean` method in `MocapiAutoConfiguration`, not by component scanning. Auto-configuration classes don't scan components — the `@Component` annotation is silently ignored. It misleads readers into thinking the class self-registers. Remove `@Component`.

### MINOR-7: `Pagination cursor` Is Silently Ignored

**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/McpToolsCapability.java:94-100`
**File:** `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java:56-59`

Both `listTools(String cursor)` and `listPrompts(String cursor)` accept a cursor parameter per the MCP protocol but ignore it completely, always returning all items with `nextCursor = null`. For large deployments this becomes a correctness issue. At minimum, document this limitation or throw if a non-null cursor is received (so clients aren't silently misled into thinking they've received all results when they pass a cursor and get back everything).

### MINOR-8: `listPrompts` Returns Indeterminate Order; `listTools` Sorts Alphabetically

**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/McpToolsCapability.java:95-99`
**File:** `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java:56-59`

`listTools` sorts by name:
```java
.sorted(Comparator.comparing(McpToolDescriptor::name))
```

`listPrompts` iterates a `HashMap` in indeterminate order. Clients receive a different prompt ordering on every JVM startup. Both should be consistent — sort alphabetically or sort neither.

### MINOR-9: `@Inherited` on Method-Level Annotations Has No Effect

**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/annotation/Tool.java:28`
**File:** `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/annotation/Prompt.java:28`

```java
@Target(ElementType.METHOD)
@Inherited  // ← no-op on method annotations
public @interface Tool { ... }
```

Per the Java specification, `@Inherited` only affects class-level annotations. On method-level annotations it has no effect. Its presence is misleading — it implies subclasses inherit the annotation from overridden methods, which they don't. Remove it from both `@Tool` and `@Prompt`. (`@ToolService` and `@PromptService` are class-level — `@Inherited` is meaningful there and correct.)

### MINOR-10: Unused Import `JsonRpcService` in Both Provider Classes

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/tools/ToolServiceMcpToolProvider.java:23`
**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/PromptServiceMcpPromptProvider.java:22`

Both files import `com.callibrity.ripcurl.core.annotation.JsonRpcService` (or similar) but never use it. Remove these dead imports.

### MINOR-11: `MocapiPromptsProperties` Is an Empty Configuration Properties Class

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/MocapiPromptsProperties.java`

```java
@ConfigurationProperties("mocapi.prompts")
@Data
public class MocapiPromptsProperties {
    // No fields
}
```

This class is `@EnableConfigurationProperties`-wired and triggers annotation processing metadata generation for nothing. Either remove it (with its `@PropertySource` reference) or add a comment explaining what properties are forthcoming.

### MINOR-12: Test Uses Literal Protocol Version String

**File:** `mocapi-core/src/test/java/com/callibrity/mocapi/server/McpServerTest.java`

If any test asserts `assertThat(response.protocolVersion()).isEqualTo("2025-11-25")` with a literal string rather than `McpServer.PROTOCOL_VERSION`, the test silently diverges from the production constant when the version changes. Reference the constant.

### MINOR-13: `LazyInitializer` Construction Inconsistency

**File:** `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java:40`
**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/McpToolsCapability.java:48`

```java
// McpPromptsCapability:
this.prompts = new LazyInitializer<>(() -> ...);

// McpToolsCapability:
this.tools = LazyInitializer.of(() -> ...);
```

Minor inconsistency in construction style. Standardize on one idiom.

### MINOR-14: No `AnnotationMcpPromptProviderFactory` in Prompts Module

The tools module exposes `AnnotationMcpToolProviderFactory` / `DefaultAnnotationMcpToolProviderFactory` as a public API for programmatically registering tools from arbitrary objects. The prompts module has no equivalent. If prompts and tools are intended to have symmetric APIs, add the factory.

### MINOR-15: Java 24 (Non-LTS) Required

**File:** `pom.xml:66`

```xml
<java.version>24</java.version>
```

Java 24 is a non-LTS release with a 6-month support window. The codebase uses no Java 24-specific features — `var`, records, pattern matching in `instanceof`, text blocks, and sealed classes are all available in Java 21 LTS. Requiring Java 24 significantly limits adoption in organizations running LTS releases. Consider dropping to Java 21.

---

## SUGGESTIONS

### SUGGESTION-1: NON_NULL Jackson Customizer Is Global

**File:** `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiAutoConfiguration.java:55-58`

```java
@Bean
Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
}
```

This applies `NON_NULL` serialization to the entire application's `ObjectMapper`, not just the MCP endpoint. Applications that need null fields serialized in other APIs will be silently broken. Document this prominently in the README or find a way to scope it to the MCP-specific mapper.

### SUGGESTION-2: `Names.kebab()` Produces Unexpected Output for Acronyms

**File:** `mocapi-core/src/main/java/com/callibrity/mocapi/server/util/Names.java:49-53`

`StringUtils.splitByCharacterTypeCamelCase("MyMCPTool")` yields `["My", "M", "C", "P", "Tool"]`, so `kebab()` produces `my-m-c-p-tool` rather than `my-mcp-tool`. Worth documenting or addressing with a custom split if acronym-heavy class names are expected.

### SUGGESTION-3: `Parameters.isRequired()` Treats `@Schema(requiredMode=AUTO)` as Required

**File:** `mocapi-core/src/main/java/com/callibrity/mocapi/server/util/Parameters.java:29-34`

```java
return !parameter.isAnnotationPresent(Schema.class)
    || parameter.getAnnotation(Schema.class).requiredMode() != Schema.RequiredMode.NOT_REQUIRED;
```

`RequiredMode.AUTO` is not `NOT_REQUIRED`, so parameters annotated with `@Schema(description = "...")` alone (without specifying `requiredMode`) will be treated as required. This is a potential surprise. Document this behavior or treat `AUTO` with non-primitive types as optional by default.

### SUGGESTION-4: `mcpServer.clientInitialized()` Is a No-Op with No Extension Point

**File:** `mocapi-core/src/main/java/com/callibrity/mocapi/server/McpServer.java:52-54`

```java
public void clientInitialized() {
    // Do nothing!
}
```

`notifications/initialized` is architecturally significant — it signals the client is ready. Providing a hook (e.g., publishing a Spring `ApplicationEvent`, or accepting a `List<McpServerLifecycleListener>`) would allow applications to react to client initialization without modifying the framework.

### SUGGESTION-5: SonarCloud Step Fails on Forks Without `SONAR_TOKEN`

**File:** `.github/workflows/maven.yml`

If `SONAR_TOKEN` is not set (e.g., a fork PR from an external contributor), the Sonar step fails with an unhelpful error. Add `if: env.SONAR_TOKEN != ''` to make the step conditional, or use a dedicated `sonar` Maven profile.

### SUGGESTION-6: Empty `mocapi-prompts-defaults.properties` Loaded Unnecessarily

**File:** `mocapi-autoconfigure/src/main/resources/mocapi-prompts-defaults.properties`

The file is loaded via `@PropertySource` but contains no properties — a no-op resource load on every startup. Remove the file and the `@PropertySource` reference if no prompt-specific defaults are planned.

### SUGGESTION-7: `AnnotationMcpTool` Default Description Equals Default Title

**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/annotation/AnnotationMcpTool.java:80-83`

When `@Tool(description = "")`, both `title()` and `description()` fall back to `humanReadableName()`, producing identical strings for both fields. MCP clients display title and description separately; identical values are redundant. Consider defaulting `description` to `null` when unspecified — the `NON_NULL` Jackson customizer will suppress it from serialization automatically.

---

## Architecture Assessment

### What Works Well

**Module separation is clean.** `mocapi-core` → `mocapi-tools`/`mocapi-prompts` → `mocapi-autoconfigure` → `mocapi-spring-boot-starter` — each has a well-defined responsibility. Dependencies flow in one direction. The optional capability model (`@ConditionalOnClass(McpToolsCapability.class)`) means a project can include only prompts, only tools, or both.

**The annotation API is intuitive.** `@ToolService`/`@Tool` and `@PromptService`/`@Prompt` mirror Spring's own idioms (`@Service`, `@RequestMapping`). Developers familiar with Spring will adopt it immediately. Auto-generating kebab-case identifiers and capitalized titles from class/method names is a thoughtful ergonomic touch.

**JSON Schema generation is sophisticated.** `DefaultMethodSchemaGenerator` integrates VicTools with Jackson, Jakarta Validation, and Swagger2 modules — `@NotNull`, `@Size`, `@Schema(description=...)` annotations on method parameters are automatically reflected in the generated schema. Input validation against the schema via Everit before tool invocation is the right separation of concerns.

**SSE streaming is carefully engineered.** `McpStreamEmitter` has idempotent completion via `AtomicBoolean`, explicit timeout and error callbacks, and detailed lifecycle logging. `McpSession` tracks event IDs for resumability. The priming event per spec is correctly implemented.

**The provider/capability pattern enables extensibility.** Custom `McpToolProvider` or `McpPromptProvider` beans can augment or replace annotation-based scanning without touching the framework.

**Build configuration is professional.** Maven enforcer, JaCoCo, SonarCloud, GPG signing, Central Publishing, and Apache license headers — this is production-quality open-source infrastructure.

### Architectural Concerns

**`McpStreamingController` is doing too much.** Session management, protocol version validation, origin validation, JSON-RPC routing, async dispatch, and SSE response building live in one class. The `invokeMethod` switch is particularly brittle: adding a new capability (resources, sampling) requires modifying this central switch, which should be an extension point. Extracting a `McpRequestRouter` would make each concern independently testable.

**The ripcurl dependency is opaque.** `@JsonRpcService`, `@JsonRpc`, `LazyInitializer`, `JsonMethodInvoker`, and the `JsonRpc*Exception` hierarchy all come from `com.callibrity.ripcurl`, which does not appear to be published on Maven Central. This is the most significant barrier to adoption — users of the starter cannot resolve its transitive dependencies without access to Callibrity's artifact repository. This should be documented clearly.

---

## Security Assessment

| Concern | Status | Notes |
|---------|--------|-------|
| Tool input schema validation | ✅ Good | Everit JSON Schema validation before invocation |
| Injection via tool arguments | ✅ Good | Arguments through `ObjectMapper`, not concatenated |
| Origin validation | ⚠️ Weak | Substring match, bypassable, not configurable — see MAJOR-1 |
| Error detail leakage | ⚠️ Present | `e.getMessage()` in JSON-RPC error response — see MINOR-4 |
| Session ID guessing | ✅ Good | `UUID.randomUUID()` is cryptographically random |
| DoS via unbounded queue | ⚠️ Possible | Event queue has no size limit — see MINOR-3 |
| DoS via unconstrained session creation | ⚠️ Unmitigated | Sessions created freely at line 114 for any request; no rate limiting |
| Reflection invocation scope | ✅ Bounded | Confined to `@ToolService`/`@PromptService` beans |

---

## Testing Assessment

### Coverage by Module

| Module | Unit Tests | Integration Tests | Notable Gaps |
|--------|-----------|-------------------|--------------|
| `mocapi-core` | `McpServerTest`, `NamesTest`, `ParametersTest` | — | Adequate for the surface area |
| `mocapi-tools` | `AnnotationMcpToolTest`, schema generator tests, capability tests | — | Duplicate-name collision, provider aggregation |
| `mocapi-prompts` | `AnnotationMcpPromptTest`, content type tests, capability tests | — | `InvocationTargetException` unwrapping |
| `mocapi-autoconfigure` | Config wiring tests (bean presence only) | `HelloToolIT`, `Rot13ToolIT`, `CodeReviewPromptsIT` | **Entire SSE layer untested at unit level** |
| `mocapi-example` | `HelloToolTest`, `Rot13ToolTest`, `CodeReviewPromptsTest` | Same ITs as above | — |

### Critical Testing Gaps

1. **`McpStreamingController` has no unit tests.** JSON-RPC dispatch, session creation, protocol version validation, and origin validation are all untested. This is the class with the highest bug density.

2. **`McpStreamEmitter` has no tests.** Idempotent completion, priming event, error-handling paths — all untested.

3. **`McpSession` has no tests.** Event queue behavior, resumption logic (`getEventsAfter`), and inactivity detection are untested.

4. **`McpSessionManager` has no tests.** Session creation, expiry, cleanup, and the concurrent-modification issue in `cleanupInactiveSessions` are untested.

5. **`PromptServiceMcpPromptProvider` has no unit test.** CRITICAL-1 would have been caught immediately by a test that asserts `getMcpPrompts()` returns non-empty results for a `@PromptService` bean.

6. **`ToolServiceMcpToolProvider` has no unit test.** The analogous (correct) tools provider is also untested.

7. **No negative-path tests for the HTTP layer.** Invalid origin, missing session ID on non-initialize, invalid protocol version, malformed JSON-RPC envelope — none are tested.

---

## Maven / Build Assessment

**Strengths:**
- Spring Boot BOM and VicTools BOM used correctly for dependency version management.
- `maven-compiler-plugin` has `<parameters>true</parameters>` — essential for reflection-based parameter name resolution.
- Mockito javaagent correctly configured for both `surefire` and `failsafe`.
- JaCoCo with aggregated coverage via `mocapi-coverage`.
- `release` profile correctly attaches sources, Javadoc, and GPG signatures.
- License headers and enforcer plugin present.

**Issues:**
- **`ripcurl` version not in `dependencyManagement`.** The ripcurl artifacts appear in module POMs with no version property or BOM entry in the parent. Version resolution is opaque.
- **`required.maven.version=3.6` is very old** (2018). Consider 3.8+ for security and reproducibility features.
- **`mocapi-spring-boot-starter` doesn't transitively include `mocapi-tools` or `mocapi-prompts`.** Users must add these separately to get any MCP functionality, which is counterintuitive for a starter.

---

## Documentation Assessment

**README:** Clear and concise. Quick-start code examples are accurate for the happy path. Two gaps:
1. The GET endpoint for server-initiated notifications is unimplemented (returns 405) — not mentioned anywhere.
2. The `mocapi.*` configuration properties (`mocapi.serverInfo`, `mocapi.instructions`, `mocapi.tools.schemaVersion`) are undocumented.

**Javadoc:** Inconsistent. `McpSession`, `McpSessionManager`, and `McpStreamEmitter` have good Javadoc. Core interfaces (`McpTool`, `McpPrompt`, `McpServerCapability`) and the primary annotation types (`@Tool`, `@ToolService`, `@Prompt`, `@PromptService`) have none. `@Prompt` has no attribute-level documentation while `@Tool` does — asymmetric.

---

## MCP Protocol Compliance (2025-11-25)

| Spec Requirement | Status | Notes |
|-----------------|--------|-------|
| Streamable HTTP transport (POST + SSE) | ✅ Implemented | |
| SSE with event IDs | ✅ Implemented | `sessionId:counter` format |
| Priming event on connection | ✅ Implemented | `sendPrimingEvent()` |
| `MCP-Session-Id` response header | ✅ Implemented | Set on `initialize` |
| Session management | ✅ Implemented | 1-hour TTL |
| Stream resumability via `Last-Event-ID` | ⚠️ Partial | Events stored but GET endpoint not implemented |
| Server-initiated notifications (GET SSE) | ❌ Not implemented | Returns 405 |
| `tools/list`, `tools/call` | ✅ Implemented | |
| `prompts/list`, `prompts/get` | ⚠️ Broken | Due to CRITICAL-1 |
| `ping` | ✅ Implemented | |
| `initialize` / `notifications/initialized` | ✅ Implemented | |
| Structured tool output (`structuredContent`) | ✅ Implemented | |
| Pagination (`cursor` / `nextCursor`) | ⚠️ Stub | Accepted but always returns all results |
| `jsonrpc: "2.0"` validation | ❌ Not validated | |
| Protocol version negotiation | ✅ Implemented | Accepts 4 versions |
| Origin validation (DNS rebinding protection) | ⚠️ Weak | Substring match; not configurable |

---

## Prioritized Action Plan

### Must Fix Before Release

1. **[CRITICAL-1]** Remove the erroneous second `.flatMap()` at `PromptServiceMcpPromptProvider.java:61`.
2. **[MAJOR-1]** Replace `String.contains()` origin check with URI parsing; make allowed origins configurable.
3. **[MAJOR-2]** Null-check and type-check `params.get("arguments")` before casting in `tools/call`.
4. **[MINOR-2]** Use `TypeReference<Map<String, String>>` and null-check in `prompts/get`.
5. **[MAJOR-6]** Validate `jsonrpc: "2.0"`, presence of `method`, and `id` type in the POST handler.
6. **[MINOR-4]** Replace `"Internal error: " + e.getMessage()` with a sanitized `"Internal error"` message.
7. **[MAJOR-7]** Unwrap `InvocationTargetException` in `AnnotationMcpPrompt.getPrompt()` before re-wrapping.

### Should Fix

8. **[MAJOR-3]** Remove `@EnableScheduling`; manage cleanup executor internally.
9. **[MAJOR-5]** Replace iteration+remove with `removeIf` in `cleanupInactiveSessions`.
10. **[MINOR-6]** Remove `@Component` from `McpSessionManager`.
11. **[MINOR-1]** Change `DEFAULT_PROTOCOL_VERSION` to `McpServer.PROTOCOL_VERSION`.
12. **[MINOR-5]** Add `@ConditionalOnMissingBean` to `methodSchemaGenerator` and `annotationMcpToolProviderFactory`.
13. **[MINOR-8]** Sort prompts alphabetically in `listPrompts`, consistent with `listTools`.
14. **[MINOR-9]** Remove `@Inherited` from `@Tool` and `@Prompt`.
15. Add unit tests for `McpStreamingController`, `McpStreamEmitter`, `McpSession`, `McpSessionManager`, `ToolServiceMcpToolProvider`, `PromptServiceMcpPromptProvider`.

### Consider

16. **[MINOR-3]** Bound the event queue in `McpSession` to prevent OOM under sustained load.
17. **[MAJOR-8]** Introduce a `CapabilityDescriptor` marker interface to type `describe()`.
18. **[MINOR-15]** Drop minimum Java version to 21 LTS.
19. **[MINOR-10]** Remove unused `JsonRpcService` imports from both provider classes.
20. Document the `mocapi.*` configuration properties in the README.
21. Document that GET SSE notifications are not yet implemented.
22. Add `@Prompt` attribute-level Javadoc.
23. Publish `ripcurl` to Maven Central or document resolution instructions.
