# Add @McpToolParams annotation for record-based tool parameter binding

## What to build

Introduce a `@McpToolParams` annotation (and a corresponding
Methodical parameter resolver) that binds the entire
`tools/call` arguments JSON to a single typed record parameter
on an `@ToolMethod`-annotated tool method. This is the tool-
layer parallel to RipCurl's existing `@JsonRpcParams`
annotation for JSON-RPC request-handler methods (spec 095 and
the ripcurl 2.1.0 release).

### Why

Today, tool methods bind their parameters individually by
name:

```java
@ToolMethod(name = "register", description = "Registers a user")
public void register(
    String username,
    String email,
    Integer age,
    Boolean acceptTerms,
    String referralCode,
    String locale,
    String timezone,
    McpStreamContext<RegisterResult> ctx) {
  // ... 7 parameters, and we're one shy of S107's 7-param limit
}
```

With `@McpToolParams`, the same tool becomes:

```java
public record RegisterRequest(
    String username,
    String email,
    Integer age,
    Boolean acceptTerms,
    String referralCode,
    String locale,
    String timezone) {}

@ToolMethod(name = "register", description = "Registers a user")
public void register(
    @McpToolParams RegisterRequest request,
    McpStreamContext<RegisterResult> ctx) {
  // Clean. Record validates shape. Schema generation uses the record's components.
}
```

**Benefits:**

1. **Cleaner signatures for tools with many parameters** —
   the record collapses an N-parameter method into a
   two-parameter one (the params record + the optional
   stream context). This is strictly better for tools with
   more than ~3 parameters, and it avoids Sonar's S107
   (max 7 parameters) warning.
2. **Record-level validation** — the record's compact
   constructor can enforce invariants at binding time,
   before the tool method runs.
3. **Easier evolution** — adding a new field to the record
   doesn't change the method signature, which means
   downstream code that reflects on the method doesn't
   break.
4. **Consistency with `@JsonRpcParams`** — mocapi already
   uses the "bind whole params object to a record" pattern
   at the JSON-RPC handler layer (spec 095). Extending it
   to the tool layer is a natural symmetry.
5. **Input schema generation stays clean** — the schema
   generator introspects the record's components and
   produces a JSON schema with `username`, `email`, `age`,
   etc. as top-level fields (same shape as the
   per-parameter approach), so the wire format is
   unchanged from the client's perspective.

### Scope

**What this spec adds:**

1. **`@McpToolParams` annotation** in
   `com.callibrity.mocapi.tools.annotation` package.
2. **`McpToolParamsResolver`** — a Methodical
   `ParameterResolver<JsonNode>` (or equivalent) that detects
   the annotation and deserializes the entire `arguments`
   JsonNode into the parameter's declared type via the
   framework's `ObjectMapper`.
3. **Resolver registration** — a Spring Boot
   auto-configuration (or extension to the existing
   `MocapiAutoConfiguration`) that registers the resolver
   with `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs
   before the generic Jackson parameter resolver.
4. **`DefaultMethodSchemaGenerator` updates** — when the tool
   method has exactly one `@McpToolParams`-annotated
   parameter (plus an optional `McpStreamContext`), the
   generator derives the input schema from the record's
   components, not from the method's parameter list.
5. **Tests**:
   - Unit tests for `McpToolParamsResolver` (directly)
   - Unit tests for `DefaultMethodSchemaGenerator` verifying
     the record-based schema shape
   - Integration test: end-to-end `tools/call` with a tool
     method that uses `@McpToolParams` — verify the
     arguments JSON is correctly bound to the record and
     the tool executes

**What this spec does NOT change:**

1. The existing per-parameter binding pattern still works.
   Tools with 2-3 parameters can keep writing
   `public void myTool(String a, int b)` and the framework
   binds each parameter by name. **Both patterns coexist.**
2. No changes to `McpStreamContext`, `@ToolMethod`, or any
   other tool framework surface.
3. No changes to the `tools/call` wire format — the
   `arguments` object on the wire is unchanged; the
   annotation only affects how the framework routes the
   parsed JSON to the Java method.

### Annotation definition

```java
package com.callibrity.mocapi.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool method parameter to receive the entire
 * {@code tools/call} arguments object deserialized into a
 * typed record.
 *
 * <p>This is the tool-layer parallel to RipCurl's
 * {@code @JsonRpcParams} annotation. It lets tool authors
 * define a record for their tool's parameters and have the
 * framework bind the whole argument object to it in one
 * step:
 *
 * <pre>
 * public record GreetRequest(String name, int volume) {}
 *
 * &#64;ToolMethod(name = "greet", description = "Greets the user")
 * public String greet(&#64;McpToolParams GreetRequest request) {
 *   return "Hello, " + request.name().repeat(request.volume());
 * }
 * </pre>
 *
 * <p>The framework's input schema generation derives the
 * tool's JSON schema from the record's components. The
 * client still sends the arguments as a flat JSON object
 * matching the record's shape ({@code {"name": "...",
 * "volume": ...}}) — the annotation only affects how the
 * framework binds the parsed JSON to the Java method.
 *
 * <p>At most one parameter per tool method may be annotated
 * with {@code @McpToolParams}. The parameter's type must be
 * a Jackson-deserializable type (typically a record). The
 * tool method may additionally declare an
 * {@link com.callibrity.mocapi.stream.McpStreamContext}
 * parameter for streaming.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolParams {}
```

### Resolver implementation sketch

```java
package com.callibrity.mocapi.tools.annotation;

import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.ParameterResolver;
import org.jwcarman.methodical.param.ParameterInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class McpToolParamsResolver implements ParameterResolver<JsonNode> {

  private final ObjectMapper objectMapper;

  public McpToolParamsResolver(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ParameterInfo info) {
    return info.parameter().isAnnotationPresent(McpToolParams.class);
  }

  @Override
  public Object resolve(ParameterInfo info, JsonNode arguments) {
    if (arguments == null || arguments.isNull()) {
      return null;
    }
    try {
      return objectMapper.readerFor(info.resolvedType())
          .readValue(objectMapper.treeAsTokens(arguments));
    } catch (JacksonException e) {
      throw new ParameterResolutionException(
          "Failed to deserialize @McpToolParams parameter "
              + info.parameter().getName()
              + " of type "
              + info.resolvedType().getTypeName()
              + ": "
              + e.getOriginalMessage(),
          e);
    }
  }
}
```

### Registration

```java
@Configuration
public class McpToolParamsResolverConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public McpToolParamsResolver mcpToolParamsResolver(ObjectMapper objectMapper) {
    return new McpToolParamsResolver(objectMapper);
  }
}
```

Add this configuration to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
so it's picked up automatically when `mocapi-spring-boot-starter`
is on the classpath. Or integrate into the existing
`MocapiAutoConfiguration` as a nested `@Bean`.

### `DefaultMethodSchemaGenerator` change

Today, the schema generator iterates over method parameters
and produces a JSON schema with one property per parameter.
With `@McpToolParams`, the behavior must change:

```java
// Pseudo-code
if (hasSingleMcpToolParamsParameter(method)) {
  Parameter recordParam = findMcpToolParamsParameter(method);
  return schemaGenerator.generateSchema(recordParam.getParameterizedType());
}
// ... existing per-parameter schema generation
```

The record's components become the schema's top-level
properties. Victools can generate this directly — it knows
how to reflect on record types.

### Documentation updates

- **README** — add a section showing both the per-parameter
  pattern and the `@McpToolParams` pattern side by side.
  Recommend `@McpToolParams` for tools with more than ~3
  parameters or where a record's invariant enforcement adds
  value.
- **Tool method javadoc** (`@ToolMethod` annotation) — add a
  mention of `@McpToolParams` with a cross-reference.

## Acceptance criteria

### Annotation and resolver

- [ ] `@McpToolParams` annotation exists at
      `com.callibrity.mocapi.tools.annotation.McpToolParams`
      with `@Target(PARAMETER)` and `@Retention(RUNTIME)`.
- [ ] `McpToolParamsResolver` implements Methodical's
      `ParameterResolver` SPI. Its `supports` method checks
      for the annotation; its `resolve` method deserializes
      the `JsonNode` arguments into the parameter's declared
      type via the framework's `ObjectMapper`.
- [ ] Deserialization failures throw
      `ParameterResolutionException` with a message that
      identifies the parameter and the underlying Jackson
      error.

### Auto-configuration

- [ ] The resolver is registered as a Spring bean at
      `HIGHEST_PRECEDENCE` order so it runs before the
      generic Jackson3 parameter resolver that binds
      parameters by name.
- [ ] The auto-configuration activates by default whenever
      `mocapi-spring-boot-starter` is on the classpath.
- [ ] No changes are required in consumer applications —
      the annotation works out of the box.

### Schema generation

- [ ] `DefaultMethodSchemaGenerator` detects tool methods
      where exactly one parameter is annotated
      `@McpToolParams` (optionally alongside an
      `McpStreamContext` parameter) and generates the input
      schema from the record's components rather than from
      the method's parameter list.
- [ ] The generated schema matches the per-parameter approach
      byte-for-byte for equivalent records, so clients can't
      tell the difference. If `GreetRequest(String name, int
      volume)` is used with `@McpToolParams`, the generated
      schema must be identical to the schema generated for
      `public void greet(String name, int volume)`.
- [ ] Tool methods that mix `@McpToolParams` with other
      non-`McpStreamContext` parameters are rejected at
      registration time with a clear error message
      ("`@McpToolParams` must be the only non-context
      parameter on the tool method").

### Tests

- [ ] `McpToolParamsResolverTest` unit tests (mirroring
      ripcurl's `JsonRpcParamsResolverTest` pattern):
  - `supports(info)` returns true for annotated parameters
    and false for unannotated ones
  - `resolve(info, arguments)` deserializes a valid JSON
    object into the expected record
  - `resolve(info, null)` and `resolve(info, NullNode)` both
    return `null`
  - Invalid JSON shape (e.g., wrong field types) throws
    `ParameterResolutionException` with an informative
    message
- [ ] `DefaultMethodSchemaGeneratorTest` new cases:
  - `@McpToolParams` on a record parameter produces the
    correct input schema
  - `@McpToolParams` on a record parameter + an
    `McpStreamContext` parameter still produces the correct
    input schema (stream context is ignored for input
    schema purposes, as it is today)
  - Tool method with `@McpToolParams` plus another
    non-context parameter throws at registration time
- [ ] Integration test in `mocapi-core` (or a new test in
      `mocapi-example` / `mocapi-compat`) exercises an
      end-to-end `tools/call`:
  - Define a tool class with a record-based input
  - Register it as a `ToolMethodProvider`
  - POST `tools/call` with matching arguments JSON
  - Assert the tool method receives the correctly
    deserialized record
  - Assert the response content matches the tool's output

### Build

- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes
      39/39.

## Implementation notes

- **Parallel to `@JsonRpcParams`**: read
  `ripcurl-core/src/main/java/com/callibrity/ripcurl/core/annotation/JsonRpcParamsResolver.java`
  for the shape. The mocapi-side resolver should be
  structurally identical, just in a different package and
  keying on a different annotation.
- **`ParameterResolver<JsonNode>` vs `ParameterResolver<?>`**:
  mocapi's tool invocation path passes a `JsonNode`
  (the `arguments` field) to the tool's invoker.
  Methodical's resolver generic parameter should be
  `JsonNode` to match. Verify by looking at how
  `AnnotationMcpTool` constructs the `MethodInvoker` and
  what type it passes to `invoke(...)`.
- **Precedence over Jackson3ParameterResolver**: both
  resolvers might claim to "support" an annotated parameter
  (Jackson3 would try to bind it by parameter name).
  `HIGHEST_PRECEDENCE` on `McpToolParamsResolver` ensures
  it wins. Ripcurl does the same thing for
  `JsonRpcParamsResolver`.
- **Record validation**: the compact constructor of a
  user-defined record can throw to reject invalid inputs:
  ```java
  public record AgeRequest(String name, int age) {
    public AgeRequest {
      if (age < 0) throw new IllegalArgumentException("age must be non-negative");
    }
  }
  ```
  These exceptions propagate up through the resolver as
  `ParameterResolutionException` (wrapping the IAE), which
  the framework maps to a JSON-RPC error. Good default
  behavior — tool authors get validation for free.
- **Methodical parameter resolver SPI**: the interface to
  implement is
  `org.jwcarman.methodical.ParameterResolver<A>` (check the
  ripcurl implementation for the exact fully-qualified
  name; it may have moved between methodical versions).
- **No `@ConfigurationProperties` needed**: the resolver
  doesn't have any configuration — it just uses the
  application's `ObjectMapper`. No new properties.
- **Don't deprecate the per-parameter pattern**. Both
  patterns should coexist. Simple tools
  (`String sayHello(String name)`) shouldn't be forced to
  define a wrapper record.
- **Commit granularity**:
  1. Add the annotation + resolver + auto-config.
  2. Update `DefaultMethodSchemaGenerator`.
  3. Add unit tests for the resolver and schema generator.
  4. Add the integration test.
  5. Update README + `@ToolMethod` javadoc.
  Each commit leaves the tree green.
- **Dependency on spec 132** (enforce void return for
  streaming tools): not strictly a prerequisite, but the
  validation logic for "at most one `@McpToolParams` + at
  most one `McpStreamContext`" fits naturally in the same
  registration-time check that 132 introduces. If 132 has
  landed, extend its validation; if not, add a standalone
  check here and flag for consolidation later.
