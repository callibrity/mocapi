# Mocapi Upgrade: Java 25 + Spring Boot + RipCurl Removal + Code Review Fixes

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade mocapi to Java 25 and latest Spring Boot, remove the RipCurl dependency entirely, and fix all issues from CODE_REVIEW.md.

**Architecture:** RipCurl provided JSON-RPC annotations, exception types, a lazy initializer utility, and a method invoker. Since McpStreamingController already handles JSON-RPC routing via a switch statement, the annotations are purely decorative. We'll create lightweight replacements in mocapi-core for the exception hierarchy, lazy initializer, and method invoker. The annotations (`@JsonRpc`, `@JsonRpcService`) can be kept as documentation-only markers.

**Tech Stack:** Java 25, Spring Boot 3.5.9 (latest 3.x), Jackson, VicTools JSON Schema, Spring Web (SSE via SseEmitter)

**Decision: Spring Boot version.** Latest stable is 4.0.1 (Spring Framework 7, Nov 2025). However, 4.0 is a major version with breaking changes and this PR already has significant scope. Recommend 3.5.9 (latest 3.x patch) for this upgrade. Spring Boot 4.0 can be a follow-up. If you want 4.0.1 instead, adjust the `spring-boot.version` property accordingly.

---

## Phase 1: RipCurl Replacement Classes in mocapi-core

These must land first since every other module depends on them.

### Task 1: Create JSON-RPC Exception Hierarchy

**Files:**
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/jsonrpc/JsonRpcException.java`
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/jsonrpc/JsonRpcInvalidParamsException.java`
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/jsonrpc/JsonRpcInternalErrorException.java`

**Step 1: Create the base exception**

```java
package com.callibrity.mocapi.server.jsonrpc;

public class JsonRpcException extends RuntimeException {
    private final int code;

    public JsonRpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public JsonRpcException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

**Step 2: Create InvalidParams exception**

```java
package com.callibrity.mocapi.server.jsonrpc;

public class JsonRpcInvalidParamsException extends JsonRpcException {
    public JsonRpcInvalidParamsException(String message) {
        super(-32602, message);
    }

    public JsonRpcInvalidParamsException(String message, Throwable cause) {
        super(-32602, message, cause);
    }
}
```

**Step 3: Create InternalError exception**

```java
package com.callibrity.mocapi.server.jsonrpc;

public class JsonRpcInternalErrorException extends JsonRpcException {
    public JsonRpcInternalErrorException(String message) {
        super(-32603, message);
    }

    public JsonRpcInternalErrorException(String message, Throwable cause) {
        super(-32603, message, cause);
    }
}
```

**Step 4: Verify compilation**

Run: `mvn compile -pl mocapi-core -am -q`

**Step 5: Commit**

```
feat: add JSON-RPC exception hierarchy to mocapi-core

Replaces ripcurl exception classes with native equivalents.
```

---

### Task 2: Create `@JsonRpcService` and `@JsonRpc` Annotations

These already exist as imports in the working tree (`com.callibrity.mocapi.server.jsonrpc.*`) but the files haven't been created yet.

**Files:**
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/jsonrpc/JsonRpcService.java`
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/jsonrpc/JsonRpc.java`

**Step 1: Create `@JsonRpcService` annotation**

```java
package com.callibrity.mocapi.server.jsonrpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonRpcService {
}
```

**Step 2: Create `@JsonRpc` annotation**

```java
package com.callibrity.mocapi.server.jsonrpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonRpc {
    String value();
}
```

**Step 3: Verify compilation**

Run: `mvn compile -pl mocapi-core -am -q`

**Step 4: Commit**

```
feat: add @JsonRpcService and @JsonRpc marker annotations

Documentation-only annotations replacing ripcurl equivalents.
Routing is handled by McpStreamingController's switch statement.
```

---

### Task 3: Create `LazyInitializer` Utility

**Files:**
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/util/LazyInitializer.java`
- Create: `mocapi-core/src/test/java/com/callibrity/mocapi/server/util/LazyInitializerTest.java`

**Step 1: Write the test**

```java
package com.callibrity.mocapi.server.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class LazyInitializerTest {

    @Test
    void initializesOnFirstGet() {
        var counter = new AtomicInteger(0);
        var lazy = LazyInitializer.of(() -> counter.incrementAndGet());
        assertThat(lazy.get()).isEqualTo(1);
    }

    @Test
    void returnsSameInstanceOnSubsequentGets() {
        var counter = new AtomicInteger(0);
        var lazy = LazyInitializer.of(() -> counter.incrementAndGet());
        lazy.get();
        lazy.get();
        assertThat(counter.get()).isEqualTo(1);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl mocapi-core -Dtest=LazyInitializerTest -q`
Expected: FAIL — class not found

**Step 3: Write the implementation**

```java
package com.callibrity.mocapi.server.util;

import java.util.function.Supplier;

public final class LazyInitializer<T> {
    private final Supplier<T> supplier;
    private volatile T value;
    private volatile boolean initialized;

    private LazyInitializer(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyInitializer<T> of(Supplier<T> supplier) {
        return new LazyInitializer<>(supplier);
    }

    public T get() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    value = supplier.get();
                    initialized = true;
                }
            }
        }
        return value;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl mocapi-core -Dtest=LazyInitializerTest -q`
Expected: PASS

**Step 5: Commit**

```
feat: add thread-safe LazyInitializer utility

Replaces ripcurl LazyInitializer with a minimal double-checked locking implementation.
```

---

### Task 4: Create `JsonMethodInvoker` Replacement

**Files:**
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/invoke/JsonMethodInvoker.java`
- Create: `mocapi-core/src/test/java/com/callibrity/mocapi/server/invoke/JsonMethodInvokerTest.java`

**Step 1: Write the test**

```java
package com.callibrity.mocapi.server.invoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JsonMethodInvokerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    public record Greeting(String message) {}

    public static class Target {
        public Greeting greet(String name) {
            return new Greeting("Hello, " + name + "!");
        }
    }

    @Test
    void invokesMethodWithJsonArguments() throws Exception {
        var target = new Target();
        var method = Target.class.getMethod("greet", String.class);
        var invoker = new JsonMethodInvoker(mapper, target, method);

        ObjectNode args = mapper.createObjectNode();
        args.put("name", "World");

        var result = invoker.invoke(args);
        assertThat(result.isObject()).isTrue();
        assertThat(result.get("message").asText()).isEqualTo("Hello, World!");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl mocapi-core -Dtest=JsonMethodInvokerTest -q`
Expected: FAIL

**Step 3: Write the implementation**

The invoker takes an ObjectNode of arguments, maps each method parameter by name to its value from the JSON, deserializes them to the parameter types using Jackson, invokes the method, and returns the result as a JsonNode.

```java
package com.callibrity.mocapi.server.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class JsonMethodInvoker {

    private final ObjectMapper mapper;
    private final Object target;
    private final Method method;

    public JsonMethodInvoker(ObjectMapper mapper, Object target, Method method) {
        this.mapper = mapper;
        this.target = target;
        this.method = method;
    }

    public JsonNode invoke(ObjectNode arguments) {
        try {
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                JsonNode value = arguments.get(parameters[i].getName());
                args[i] = mapper.treeToValue(value, parameters[i].getType());
            }
            Object result = method.invoke(target, args);
            return mapper.valueToTree(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
```

Note: This implementation correctly unwraps `InvocationTargetException` (unlike the ripcurl version's behavior in prompts).

**Step 4: Run test to verify it passes**

Run: `mvn test -pl mocapi-core -Dtest=JsonMethodInvokerTest -q`
Expected: PASS

**Step 5: Commit**

```
feat: add JsonMethodInvoker for reflective JSON-RPC method dispatch

Replaces ripcurl JsonMethodInvoker. Correctly unwraps InvocationTargetException
to propagate business exceptions (e.g., JsonRpcInvalidParamsException) with their
original error codes.
```

---

## Phase 2: Remove RipCurl Dependencies from All Modules

### Task 5: Update mocapi-core POM — Remove ripcurl-core Dependency

**Files:**
- Modify: `mocapi-core/pom.xml`

**Step 1: Remove the ripcurl-core dependency**

Remove this block from `mocapi-core/pom.xml`:
```xml
<dependency>
    <groupId>com.callibrity.ripcurl</groupId>
    <artifactId>ripcurl-core</artifactId>
</dependency>
```

Also add `com.fasterxml.jackson.core:jackson-databind` dependency (needed by JsonMethodInvoker, was transitively pulled via ripcurl):
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

**Step 2: Verify compilation**

Run: `mvn compile -pl mocapi-core -am -q`

**Step 3: Run tests**

Run: `mvn test -pl mocapi-core -q`

**Step 4: Commit**

```
refactor: remove ripcurl-core dependency from mocapi-core
```

---

### Task 6: Update mocapi-tools — Replace RipCurl Imports

**Files:**
- Modify: `mocapi-tools/pom.xml` — remove ripcurl-core dependency
- Modify: `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/McpToolsCapability.java` — replace exception + LazyInitializer imports
- Modify: `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/annotation/AnnotationMcpTool.java` — replace JsonMethodInvoker + exception imports

**Step 1: Remove ripcurl-core from mocapi-tools/pom.xml**

Remove:
```xml
<dependency>
    <groupId>com.callibrity.ripcurl</groupId>
    <artifactId>ripcurl-core</artifactId>
</dependency>
```

**Step 2: Update imports in McpToolsCapability.java**

Replace:
```java
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.callibrity.ripcurl.core.util.LazyInitializer;
```
With:
```java
import com.callibrity.mocapi.server.jsonrpc.JsonRpcInvalidParamsException;
import com.callibrity.mocapi.server.util.LazyInitializer;
```

Also fix MINOR-13 (construction inconsistency): the `McpPromptsCapability` uses `new LazyInitializer<>()` while `McpToolsCapability` uses `LazyInitializer.of()`. Keep `LazyInitializer.of()` everywhere since that's our API.

**Step 3: Update imports in AnnotationMcpTool.java**

Replace:
```java
import com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException;
import com.callibrity.ripcurl.core.invoke.JsonMethodInvoker;
```
With:
```java
import com.callibrity.mocapi.server.jsonrpc.JsonRpcInternalErrorException;
import com.callibrity.mocapi.server.invoke.JsonMethodInvoker;
```

**Step 4: Verify compilation and tests**

Run: `mvn test -pl mocapi-tools -am -q`

**Step 5: Commit**

```
refactor: remove ripcurl-core dependency from mocapi-tools
```

---

### Task 7: Update mocapi-prompts — Replace RipCurl Imports

**Files:**
- Modify: `mocapi-prompts/pom.xml` — remove ripcurl-core dependency
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java` — replace imports
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/annotation/AnnotationMcpPrompt.java` — replace imports

**Step 1: Remove ripcurl-core from mocapi-prompts/pom.xml**

Remove:
```xml
<dependency>
    <groupId>com.callibrity.ripcurl</groupId>
    <artifactId>ripcurl-core</artifactId>
</dependency>
```

**Step 2: Update imports in McpPromptsCapability.java**

Replace:
```java
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.callibrity.ripcurl.core.util.LazyInitializer;
```
With:
```java
import com.callibrity.mocapi.server.jsonrpc.JsonRpcInvalidParamsException;
import com.callibrity.mocapi.server.util.LazyInitializer;
```

**Step 3: Update imports in AnnotationMcpPrompt.java**

Replace:
```java
import com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
```
With:
```java
import com.callibrity.mocapi.server.jsonrpc.JsonRpcInternalErrorException;
import com.callibrity.mocapi.server.jsonrpc.JsonRpcInvalidParamsException;
```

**Step 4: Verify compilation and tests**

Run: `mvn test -pl mocapi-prompts -am -q`

**Step 5: Commit**

```
refactor: remove ripcurl-core dependency from mocapi-prompts
```

---

### Task 8: Update mocapi-autoconfigure — Remove RipCurl

**Files:**
- Modify: `mocapi-autoconfigure/pom.xml` — remove ripcurl-autoconfigure dependency
- Modify: `mocapi-autoconfigure/src/main/resources/mocapi-defaults.properties` — rename `ripcurl.endpoint` to `mocapi.endpoint`
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:50` — update `@RequestMapping` property reference
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiProperties.java` — add `endpoint` property
- Remove unused `JsonRpcService` import from `PromptServiceMcpPromptProvider.java` (MINOR-10)
- Remove unused `JsonRpcService` import from `ToolServiceMcpToolProvider.java` (MINOR-10)
- Modify: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/MocapiAutoConfigurationTest.java` — remove RipCurlAutoConfiguration
- Modify: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/tools/MocapiToolsAutoConfigurationTest.java` — remove RipCurlAutoConfiguration
- Modify: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/prompts/MocapiPromptsAutoConfigurationTest.java` — remove RipCurlAutoConfiguration

**Step 1: Remove ripcurl-autoconfigure from pom.xml**

Remove:
```xml
<dependency>
    <groupId>com.callibrity.ripcurl</groupId>
    <artifactId>ripcurl-autoconfigure</artifactId>
    <version>${ripcurl.version}</version>
</dependency>
```

Add spring-boot-starter-web (needed for @RestController and SseEmitter, was transitively pulled via ripcurl-autoconfigure):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**Step 2: Rename ripcurl.endpoint property**

In `mocapi-defaults.properties`, change:
```
ripcurl.endpoint=/mcp
```
To:
```
mocapi.endpoint=/mcp
```

In `McpStreamingController.java`, change:
```java
@RequestMapping("${ripcurl.endpoint:/mcp}")
```
To:
```java
@RequestMapping("${mocapi.endpoint:/mcp}")
```

Add to `MocapiProperties.java`:
```java
private String endpoint = "/mcp";
```

**Step 3: Remove unused JsonRpcService imports (MINOR-10)**

Remove `import com.callibrity.mocapi.server.jsonrpc.JsonRpcService;` from both:
- `PromptServiceMcpPromptProvider.java`
- `ToolServiceMcpToolProvider.java`

**Step 4: Update test classes — remove RipCurlAutoConfiguration**

In all three test files, remove the `RipCurlAutoConfiguration` import and remove it from the `AutoConfigurations.of(...)` call. Replace with `JacksonAutoConfiguration` if not already present.

**Step 5: Verify compilation and tests**

Run: `mvn test -pl mocapi-autoconfigure -am -q`

**Step 6: Commit**

```
refactor: remove ripcurl dependency from mocapi-autoconfigure

Renames ripcurl.endpoint property to mocapi.endpoint.
```

---

## Phase 3: Version Upgrades

### Task 9: Upgrade Java Version to 25

**Files:**
- Modify: `pom.xml:66` — change `<java.version>24</java.version>` to `<java.version>25</java.version>`

**Step 1: Update the property**

Change:
```xml
<java.version>24</java.version>
```
To:
```xml
<java.version>25</java.version>
```

**Step 2: Verify build**

Run: `mvn compile -q`
Expected: PASS (no Java 25-specific features needed, just bumping the target)

Note: Ensure Java 25 is installed. Run `java -version` first to confirm.

**Step 3: Commit**

```
chore: upgrade Java version from 24 to 25
```

---

### Task 10: Upgrade Spring Boot to 3.5.9

**Files:**
- Modify: `pom.xml` — change `<spring-boot.version>3.5.3</spring-boot.version>` to `<spring-boot.version>3.5.9</spring-boot.version>`

**Step 1: Update the property**

Change:
```xml
<spring-boot.version>3.5.3</spring-boot.version>
```
To:
```xml
<spring-boot.version>3.5.9</spring-boot.version>
```

**Step 2: Verify full build + tests**

Run: `mvn verify -q`

**Step 3: Commit**

```
chore: upgrade Spring Boot from 3.5.3 to 3.5.9
```

---

## Phase 4: CODE_REVIEW.md Critical and Major Fixes

### Task 11: Fix CRITICAL-1 — Double flatMap in PromptServiceMcpPromptProvider

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/PromptServiceMcpPromptProvider.java:61`

**Step 1: Remove the second flatMap**

In `PromptServiceMcpPromptProvider.java`, the `initialize()` method has two `.flatMap()` calls. Remove line 61:
```java
.flatMap(targetObject -> AnnotationMcpPrompt.createPrompts(targetObject).stream())
```

The corrected stream should terminate after the first `flatMap`, going directly to `.toList()`:
```java
this.prompts = context.getBeansWithAnnotation(PromptService.class).entrySet().stream()
        .flatMap(entry -> {
            var beanName = entry.getKey();
            var bean = entry.getValue();
            log.info("Registering MCP prompts for @{} bean \"{}\"...", PromptService.class.getSimpleName(), beanName);
            var list = AnnotationMcpPrompt.createPrompts(bean);
            list.forEach(prompt -> log.info("\tRegistered MCP prompt: \"{}\".", prompt.name()));
            return list.stream();
        })
        .toList();
```

**Step 2: Run tests**

Run: `mvn verify -q`

**Step 3: Commit**

```
fix: remove erroneous second flatMap that silently dropped all prompts

CRITICAL-1: The second flatMap called createPrompts() on AnnotationMcpPrompt
objects (which have no @Prompt methods), producing empty streams for every
element and resulting in an always-empty prompt list.
```

---

### Task 12: Fix MAJOR-1 — Origin Validation

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:229-236`
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiProperties.java` — add `allowedOrigins` property

**Step 1: Add allowed origins to MocapiProperties**

```java
private List<String> allowedOrigins = List.of("localhost", "127.0.0.1", "[::1]");
```

**Step 2: Pass allowed origins to McpStreamingController**

Add a constructor parameter or inject the property. Since the controller uses `@RequiredArgsConstructor`, add a field:
```java
private final List<String> allowedOrigins;
```

Update `MocapiAutoConfiguration.mcpStreamingController()` to pass `props.getAllowedOrigins()`.

**Step 3: Fix the origin validation to use URI parsing**

Replace:
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

With:
```java
private boolean isValidOrigin(String origin) {
    if (origin == null) {
        return true;
    }
    try {
        var uri = java.net.URI.create(origin);
        var host = uri.getHost();
        if (host == null) {
            return false;
        }
        return allowedOrigins.stream().anyMatch(host::equals);
    } catch (IllegalArgumentException e) {
        return false;
    }
}
```

**Step 4: Run tests**

Run: `mvn verify -q`

**Step 5: Commit**

```
fix: use URI parsing for origin validation, make origins configurable

MAJOR-1: String.contains() origin check was bypassable (e.g.,
evil.localhost.attacker.com). Now parses URI host component.
Configurable via mocapi.allowed-origins property.
```

---

### Task 13: Fix MAJOR-2 — Unchecked Cast on arguments in tools/call

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:188-193`

**Step 1: Add null/type checking for arguments**

Replace:
```java
case "tools/call" -> Optional.ofNullable(toolsCapability)
        .map(cap -> cap.callTool(
                params.path("name").asText(),
                (ObjectNode) params.get("arguments")
        ))
```

With:
```java
case "tools/call" -> Optional.ofNullable(toolsCapability)
        .map(cap -> {
            JsonNode argsNode = params.path("arguments");
            ObjectNode arguments = argsNode.isObject() ? (ObjectNode) argsNode : objectMapper.createObjectNode();
            return cap.callTool(params.path("name").asText(), arguments);
        })
```

**Step 2: Commit**

```
fix: null-check and type-check arguments in tools/call dispatch

MAJOR-2: Prevents NullPointerException when arguments is null/absent
and ClassCastException when arguments is a non-object JSON type.
```

---

### Task 14: Fix MAJOR-2 (MINOR-2) — prompts/get arguments handling

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:197-201`

**Step 1: Fix the raw Map.class and null handling**

Replace:
```java
case "prompts/get" -> Optional.ofNullable(promptsCapability)
        .map(cap -> cap.getPrompt(
                params.path("name").asText(),
                objectMapper.convertValue(params.get("arguments"), Map.class)
        ))
```

With:
```java
case "prompts/get" -> Optional.ofNullable(promptsCapability)
        .map(cap -> {
            JsonNode argsNode = params.path("arguments");
            Map<String, String> arguments = argsNode.isObject()
                    ? objectMapper.convertValue(argsNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {})
                    : Map.of();
            return cap.getPrompt(params.path("name").asText(), arguments);
        })
```

**Step 2: Commit**

```
fix: use TypeReference for type-safe prompts/get argument conversion

MAJOR-2/MINOR-2: Fixes raw Map.class cast and null argument handling.
```

---

### Task 15: Fix MAJOR-3 — Remove @EnableScheduling, Use Internal Executor

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiAutoConfiguration.java` — remove `@EnableScheduling`
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpSessionManager.java` — replace `@Scheduled` with internal `ScheduledExecutorService`

**Step 1: Remove @EnableScheduling from MocapiAutoConfiguration**

Remove the `@EnableScheduling` annotation and its import.

**Step 2: Update McpSessionManager to use internal scheduling**

Remove `@Component` (MINOR-6), remove `@Scheduled`, add internal executor:

```java
@Slf4j
public class McpSessionManager {
    private static final long DEFAULT_SESSION_TIMEOUT_SECONDS = 3600L;
    private static final long CLEANUP_INTERVAL_SECONDS = 300L;

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final long sessionTimeoutSeconds;
    private final ScheduledExecutorService cleanupExecutor;

    public McpSessionManager() {
        this(DEFAULT_SESSION_TIMEOUT_SECONDS);
    }

    public McpSessionManager(long sessionTimeoutSeconds) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupInactiveSessions,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    // ... existing methods ...

    public void shutdown() {
        cleanupExecutor.shutdown();
    }
}
```

Also fix MAJOR-5 (race condition) in `cleanupInactiveSessions`:

Replace:
```java
for (Map.Entry<String, McpSession> entry : sessions.entrySet()) {
    if (entry.getValue().isInactive(sessionTimeoutSeconds)) {
        sessions.remove(entry.getKey());
        removedCount++;
    }
}
```

With:
```java
int removedCount = 0;
var iter = sessions.entrySet().iterator();
while (iter.hasNext()) {
    if (iter.next().getValue().isInactive(sessionTimeoutSeconds)) {
        iter.remove();
        removedCount++;
    }
}
```

Or even simpler using `removeIf` on ConcurrentHashMap (thread-safe):
```java
// ConcurrentHashMap.entrySet().removeIf is atomic per-entry
long removedCount = sessions.values().stream()
        .filter(s -> s.isInactive(sessionTimeoutSeconds))
        .count();
sessions.entrySet().removeIf(e -> e.getValue().isInactive(sessionTimeoutSeconds));
```

**Step 3: Update MocapiAutoConfiguration to handle shutdown**

Add `@PreDestroy` or implement `DisposableBean` to call `mcpSessionManager.shutdown()`. Or register a destroy method on the bean:

```java
@Bean(destroyMethod = "shutdown")
@ConditionalOnMissingBean
public McpSessionManager mcpSessionManager() {
    return new McpSessionManager();
}
```

**Step 4: Run tests**

Run: `mvn verify -q`

**Step 5: Commit**

```
fix: remove @EnableScheduling, use internal executor for session cleanup

MAJOR-3: @EnableScheduling globally activates scheduling in consumer apps.
MAJOR-5: Use removeIf for thread-safe cleanup iteration.
MINOR-6: Remove @Component from McpSessionManager (created via @Bean).
```

---

### Task 16: Fix MAJOR-6 — JSON-RPC Request Envelope Validation

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:79-82`

**Step 1: Add validation after parsing the request body**

After `JsonNode idNode = requestBody.get("id");` add:

```java
// Validate JSON-RPC 2.0 envelope
String jsonrpc = requestBody.path("jsonrpc").asText();
if (!"2.0".equals(jsonrpc)) {
    return ResponseEntity.badRequest()
            .body(createErrorResponse(idNode, -32600, "Invalid JSON-RPC: missing or invalid 'jsonrpc' field"));
}

String method = requestBody.path("method").asText();
if (method.isEmpty()) {
    return ResponseEntity.badRequest()
            .body(createErrorResponse(idNode, -32600, "Invalid JSON-RPC: missing 'method' field"));
}

// Validate id type (must be string, number, or null per JSON-RPC 2.0)
if (idNode != null && !idNode.isTextual() && !idNode.isNumber() && !idNode.isNull()) {
    return ResponseEntity.badRequest()
            .body(createErrorResponse(idNode, -32600, "Invalid JSON-RPC: 'id' must be a string, number, or null"));
}

JsonNode params = requestBody.get("params");
```

**Step 2: Commit**

```
fix: validate JSON-RPC 2.0 request envelope

MAJOR-6: Validates jsonrpc field, method presence, and id type per spec.
```

---

### Task 17: Fix MAJOR-7 — Unwrap InvocationTargetException in AnnotationMcpPrompt

**Files:**
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/annotation/AnnotationMcpPrompt.java:119-127`

**Step 1: Fix exception handling**

Replace:
```java
try {
    var result = method.invoke(targetObject, params);
    if (result instanceof GetPromptResult r) {
        return r;
    }
    throw new JsonRpcInternalErrorException(String.format("Prompt \"%s\" did not return a GetPromptResult.", name));
} catch (ReflectiveOperationException e) {
    throw new JsonRpcInternalErrorException(String.format("Unable to get prompt \"%s\".", name), e);
}
```

With:
```java
try {
    var result = method.invoke(targetObject, params);
    if (result instanceof GetPromptResult r) {
        return r;
    }
    throw new JsonRpcInternalErrorException(String.format("Prompt \"%s\" did not return a GetPromptResult.", name));
} catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause instanceof RuntimeException re) {
        throw re;
    }
    throw new JsonRpcInternalErrorException(String.format("Unable to get prompt \"%s\".", name), cause);
} catch (IllegalAccessException e) {
    throw new JsonRpcInternalErrorException(String.format("Unable to get prompt \"%s\".", name), e);
}
```

Add `import java.lang.reflect.InvocationTargetException;` if not already present.

**Step 2: Run tests**

Run: `mvn test -pl mocapi-prompts -am -q`

**Step 3: Commit**

```
fix: unwrap InvocationTargetException in AnnotationMcpPrompt.getPrompt()

MAJOR-7: Business exceptions (e.g., JsonRpcInvalidParamsException) were being
wrapped in InvocationTargetException then re-wrapped as InternalError, masking
the original -32602 error code with -32603.
```

---

### Task 18: Fix MAJOR-8 — Type-safe describe() Return

**Files:**
- Create: `mocapi-core/src/main/java/com/callibrity/mocapi/server/CapabilityDescriptor.java`
- Modify: `mocapi-core/src/main/java/com/callibrity/mocapi/server/McpServerCapability.java`
- Modify: `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/McpToolsCapability.java` — `ToolsCapabilityDescriptor implements CapabilityDescriptor`
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java` — `PromptsCapabilityDescriptor implements CapabilityDescriptor`

**Step 1: Create marker interface**

```java
package com.callibrity.mocapi.server;

public interface CapabilityDescriptor {
}
```

**Step 2: Update McpServerCapability**

Change:
```java
Object describe();
```
To:
```java
CapabilityDescriptor describe();
```

**Step 3: Update both capability descriptor records**

Add `implements CapabilityDescriptor` to:
- `ToolsCapabilityDescriptor`
- `PromptsCapabilityDescriptor`

**Step 4: Run tests**

Run: `mvn verify -q`

**Step 5: Commit**

```
refactor: type-safe CapabilityDescriptor interface for describe()

MAJOR-8: Replaces raw Object return type with a marker interface.
```

---

## Phase 5: CODE_REVIEW.md Minor Fixes

### Task 19: Fix MINOR-1 — Default Protocol Version

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:67`

**Step 1:** Change:
```java
private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";
```
To:
```java
private static final String DEFAULT_PROTOCOL_VERSION = McpServer.PROTOCOL_VERSION;
```

**Step 2: Commit**

```
fix: default protocol version now references McpServer.PROTOCOL_VERSION

MINOR-1: Was hardcoded to outdated "2025-03-26".
```

---

### Task 20: Fix MINOR-4 — Error Message Leaks Internal Details

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingController.java:146`

**Step 1:** Change:
```java
ObjectNode errorResponse = createErrorResponse(idNode, -32603, "Internal error: " + e.getMessage());
```
To:
```java
ObjectNode errorResponse = createErrorResponse(idNode, -32603, "Internal error");
```

The full exception is already logged on the preceding line.

**Step 2: Commit**

```
fix: sanitize error responses to prevent internal detail leakage

MINOR-4: e.getMessage() was included in JSON-RPC error response.
```

---

### Task 21: Fix MINOR-5 — Add @ConditionalOnMissingBean to Framework Beans

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/tools/MocapiToolsAutoConfiguration.java`

**Step 1:** Add `@ConditionalOnMissingBean` to:
- `methodSchemaGenerator` bean
- `annotationMcpToolProviderFactory` bean

**Step 2: Commit**

```
fix: add @ConditionalOnMissingBean to allow user bean overrides

MINOR-5: Users can now provide custom MethodSchemaGenerator or
AnnotationMcpToolProviderFactory without excluding auto-configuration.
```

---

### Task 22: Fix MINOR-8 — Sort Prompts Alphabetically

**Files:**
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/McpPromptsCapability.java:56-59`

**Step 1:** Add sorting to `listPrompts`:

```java
public ListPromptsResponse listPrompts(String cursor) {
    return new ListPromptsResponse(prompts.get().values().stream()
            .map(p -> new McpPromptDescriptor(p.name(), p.description(), p.arguments()))
            .sorted(Comparator.comparing(McpPromptDescriptor::name))
            .toList(), null);
}
```

Add `import java.util.Comparator;` if not present.

**Step 2: Commit**

```
fix: sort prompts alphabetically in listPrompts for deterministic ordering

MINOR-8: Matches the sorting behavior already present in listTools.
```

---

### Task 23: Fix MINOR-9 — Remove @Inherited from Method-Level Annotations

**Files:**
- Modify: `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/annotation/Tool.java:28`
- Modify: `mocapi-prompts/src/main/java/com/callibrity/mocapi/prompts/annotation/Prompt.java:28`

**Step 1:** Remove `@Inherited` from both `@Tool` and `@Prompt` annotations (method-level annotations cannot be inherited per Java spec).

**Step 2: Commit**

```
fix: remove no-op @Inherited from method-level @Tool and @Prompt

MINOR-9: @Inherited has no effect on method-level annotations per Java spec.
```

---

### Task 24: Fix MINOR-12 — Test Uses Literal Protocol Version

**Files:**
- Modify: `mocapi-core/src/test/java/com/callibrity/mocapi/server/McpServerTest.java`

**Step 1:** Replace any remaining literal `"2025-11-25"` assertion with `McpServer.PROTOCOL_VERSION`:

```java
assertThat(response.protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
```

**Step 2: Commit**

```
fix: use McpServer.PROTOCOL_VERSION constant in test assertion

MINOR-12: Prevents test/production divergence on version updates.
```

---

### Task 25: Clean Up SUGGESTION-1 — Scoped Jackson Customizer

**Files:**
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/MocapiAutoConfiguration.java`

**Step 1:** Remove the global Jackson customizer and instead create a dedicated ObjectMapper bean for MCP:

Replace the `jacksonCustomizer` bean with a `@ConditionalOnMissingBean`-guarded `mcpObjectMapper`:

```java
@Bean
@ConditionalOnMissingBean(name = "mcpObjectMapper")
public ObjectMapper mcpObjectMapper(ObjectMapper objectMapper) {
    return objectMapper.copy()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
}
```

Update `McpStreamingController` constructor to use `@Qualifier("mcpObjectMapper") ObjectMapper objectMapper` and update the bean wiring in `MocapiAutoConfiguration.mcpStreamingController()`.

**Step 2: Commit**

```
refactor: scope NON_NULL Jackson config to MCP-specific ObjectMapper

SUGGESTION-1: Global customizer was overriding application-wide serialization.
```

---

### Task 26: Remove Empty MocapiPromptsProperties

**Files:**
- Delete: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/MocapiPromptsProperties.java`
- Delete: `mocapi-autoconfigure/src/main/resources/mocapi-prompts-defaults.properties`
- Modify: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/MocapiPromptsAutoConfiguration.java` — remove `@EnableConfigurationProperties`, `@PropertySource`, and the `props` field

**Step 1:** Remove the empty properties class, the empty properties file, and the corresponding annotations/field from the auto-configuration.

**Step 2: Commit**

```
refactor: remove empty MocapiPromptsProperties and unused property source

MINOR-11/SUGGESTION-6: No prompt-specific properties exist.
```

---

## Phase 6: Final Verification

### Task 27: Full Build Verification

**Step 1:** Run full build with tests:

Run: `mvn clean verify`

**Step 2:** Run integration tests:

Run: `mvn verify -pl mocapi-example -am`

**Step 3:** Verify no ripcurl references remain:

Run: `grep -r "ripcurl" --include="*.java" --include="*.xml" --include="*.properties" .`

Expected: No matches (excluding docs/plans and CODE_REVIEW.md)

**Step 4:** Commit any final adjustments, then delete CODE_REVIEW.md (issues resolved).

---

## Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| 1 | 1-4 | Create RipCurl replacement classes in mocapi-core |
| 2 | 5-8 | Remove RipCurl dependencies from all modules |
| 3 | 9-10 | Java 25 + Spring Boot 3.5.9 upgrade |
| 4 | 11-18 | Critical and Major CODE_REVIEW fixes |
| 5 | 19-26 | Minor CODE_REVIEW fixes and suggestions |
| 6 | 27 | Final verification |

**Total: 27 tasks**
