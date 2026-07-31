# MCP Apps Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional MCP Apps support (`io.modelcontextprotocol/ui`) to mocapi as a new `mocapi-apps` module: serve `ui://` HTML resources, stamp `_meta.ui` on tool/resource descriptors, and declare the capability — server-side metadata only, no state, no new JSON-RPC methods.

**Architecture:** Two small core additions (an optional `_meta` `ObjectNode` on the `Tool`/`Resource` model records; `ToolDescriptorCustomizer`/`ResourceDescriptorCustomizer` seams applied in the handler build paths) plus a self-contained `mocapi-apps` module holding the `@McpUi`/`@McpAppResource` annotations, the `_meta.ui` types, the two Apps descriptor customizers, and a `UiCapabilityCustomizer`. The two shared foundation seams it depends on — `ServerCapabilitiesCustomizer` (ADR-0031) and meta-annotation-aware discovery (ADR-0032) — are already implemented and verified on this branch. The host/iframe `postMessage` bridge is out of scope by design.

**Tech Stack:** Java 21+ (records, sealed types, switch), Spring Boot 4 autoconfiguration, Jackson 3 (`tools.jackson`) for JSON with `com.fasterxml.jackson.annotation.*` annotations, JUnit 5 + AssertJ + Spring Boot `ApplicationContextRunner`, methodical + ripcurl (already on the classpath).

**Design spec:** [`docs/superpowers/specs/2026-07-31-mcp-apps-extension-design.md`](../specs/2026-07-31-mcp-apps-extension-design.md) — read §3 (wire requirements), §5–7 (architecture/API), §9 (wire contract).

## Global Constraints

- **Branch:** `feat/mcp-apps-extension` (already checked out, based on `feat/extension-foundation-seams`). Do not merge to main.
- **Version:** all modules are `1.1.0-SNAPSHOT`. New module inherits from `mocapi-parent`.
- **No star imports** (Java or static). Explicit single-symbol imports only.
- **No `@SuppressWarnings`** of any kind in new code (the `LegacyTitledEnumSchema` deprecation exception does not apply here).
- **Jackson:** `ObjectNode` / `ObjectMapper` are `tools.jackson.databind.*`; annotations (`@JsonProperty`, `@JsonInclude`) are `com.fasterxml.jackson.annotation.*`.
- **Formatting/headers:** run `mvn spotless:apply -pl <module>` before every commit; every new `.java` file needs the Apache license header (spotless adds it via `mvn spotless:apply`). Verify with `mvn spotless:check`.
- **Tests are mandatory** ("we do not release uncovered code"). Every new class ships with tests in the same task.
- **Build via Maven, not the IDE.** If a test throws `java.lang.Error: Unresolved compilation problem` at runtime, that is a stale IDE-compiled (`ecj`) class stub — re-run with `mvn ... clean` to force a javac rebuild.
- **Baseline spec:** stable `2026-01-26` of ext-apps. MIME type is exactly `text/html;profile=mcp-app`. Capability id is exactly `io.modelcontextprotocol/ui`.
- **ADR-before-code:** this plan is architecturally significant (new module, new declared capability, new SPI seams). Task 1 writes the ADRs + design-doc updates first, per the project rule in `CLAUDE.md`.

---

## File Structure

**Core (existing modules):**
- `mocapi-model/.../Tool.java` — add optional `_meta` component + `withMeta`.
- `mocapi-model/.../Resource.java` — add optional `_meta` component + `withMeta`.
- `mocapi-server/.../tools/ToolDescriptorCustomizer.java` — new seam.
- `mocapi-server/.../resources/ResourceDescriptorCustomizer.java` — new seam.
- `mocapi-server/.../tools/CallToolHandlers.java` — apply tool descriptor customizers.
- `mocapi-server/.../resources/ReadResourceHandlers.java` — apply resource descriptor customizers.
- `mocapi-autoconfigure/.../MocapiServerToolsAutoConfiguration.java` — collect + pass tool descriptor customizers.
- `mocapi-autoconfigure/.../MocapiServerResourcesAutoConfiguration.java` — collect + pass resource descriptor customizers.
- `mocapi-autoconfigure/.../apps/MocapiAppsAutoConfiguration.java` — new; register Apps customizer beans.
- `mocapi-autoconfigure/src/main/resources/META-INF/spring/...AutoConfiguration.imports` — register the new autoconfig.
- `mocapi-autoconfigure/pom.xml` — add optional `mocapi-apps` dependency.

**New module `mocapi-apps`:**
- `pom.xml`
- `.../apps/McpUi.java` — `@McpUi` tool→UI link annotation.
- `.../apps/McpAppResource.java` — `@McpAppResource` composed `ui://` resource annotation.
- `.../apps/Csp.java` — `@Csp` nested annotation for CSP declaration.
- `.../apps/McpUiToolMeta.java` — the `_meta.ui` shape for tools.
- `.../apps/UiResourceMeta.java` + `.../apps/McpUiResourceCsp.java` — the `_meta.ui` shape for resources.
- `.../apps/AppsToolDescriptorCustomizer.java` — reads `@McpUi` → tool `_meta.ui`.
- `.../apps/AppsResourceDescriptorCustomizer.java` — reads `@McpAppResource` → resource `_meta.ui`.
- `.../apps/UiCapabilityCustomizer.java` — declares the `ui` capability.

**Root:**
- `pom.xml` — add `<module>mocapi-apps</module>`.
- `mocapi-bom/pom.xml` — add the `mocapi-apps` artifact.

**Docs:**
- `docs/adr/0033-*.md`, `docs/adr/0034-*.md`, `docs/adr/0022-*.md` (update), `docs/adr/README.md`.
- `docs/design/handlers.md` (descriptor customizers), new `docs/design/apps.md`, `docs/guides/apps.md`.

---

## Task 1: ADRs and design-doc scaffolding (before code)

**Files:**
- Create: `docs/adr/0033-mcp-apps-module-and-ui-capability.md`
- Create: `docs/adr/0034-descriptor-meta-and-customizer-seams.md`
- Modify: `docs/adr/0022-declined-features.md` (flip the "apps declined" line item)
- Modify: `docs/adr/README.md` (index the two new ADRs)
- Modify: `docs/design/handlers.md` (add a "Descriptor customizers" subsection)

**Interfaces:**
- Produces: nothing code-level; establishes the decisions the code tasks implement.

- [ ] **Step 1: Write ADR-0033** using the template at `docs/adr/_template.md`. Status `Accepted`, date `2026-07-31`. Context: MCP Apps declined at 1.0 (ADR-0022); it is a shallow, server-side, additive extension. Decision: introduce optional `mocapi-apps`; serve `ui://` resources; declare `io.modelcontextprotocol/ui` = `{ "mimeTypes": ["text/html;profile=mcp-app"] }` via `UiCapabilityCustomizer`; the postMessage/JS-bridge layer is explicitly out of scope. Consequences: authors get `@McpAppResource`/`@McpUi`; non-Apps hosts ignore the metadata (text-only fallback); no state, no new methods. Code anchors point at `mocapi-apps/.../UiCapabilityCustomizer.java` and the annotations.

- [ ] **Step 2: Write ADR-0034.** Context: `Tool`/`Resource` descriptors carried no `_meta`; extensions need to annotate descriptors without core knowing the semantics. Decision: add an optional `_meta` `ObjectNode` to `Tool` and `Resource` (SEP/ADR-0014 base-protocol fidelity); introduce `ToolDescriptorCustomizer` (`Tool customize(Method, Tool)`) and `ResourceDescriptorCustomizer` (`Resource customize(Method, Resource)`), applied in `CallToolHandlers.build` / `ReadResourceHandlers.build` after the descriptor is created; core stays ignorant of `ui`. Consequences: additive/backward-compatible wire change (`NON_NULL`); reusable by any future descriptor-annotating extension. Code anchors: the two interfaces + the two model records.

- [ ] **Step 3: Update ADR-0022** — change the apps line item from declined to "Accepted — implemented in ADR-0033 (mocapi-apps); the postMessage/JS-bridge layer remains out of scope." Keep a back-link.

- [ ] **Step 4: Update `docs/adr/README.md`** — append:
```markdown
- [ADR-0033 — MCP Apps module and the `io.modelcontextprotocol/ui` capability](0033-mcp-apps-module-and-ui-capability.md)
- [ADR-0034 — Descriptor `_meta` and descriptor-customizer seams](0034-descriptor-meta-and-customizer-seams.md)
```

- [ ] **Step 5: Update `docs/design/handlers.md`** — after the "Meta-annotation composition" section, add a short "Descriptor customizers (ADR-0034)" subsection: each handler's `Tool`/`Resource` descriptor is passed through `List<ToolDescriptorCustomizer>` / `List<ResourceDescriptorCustomizer>` at build time, letting modules enrich `_meta` (e.g. `mocapi-apps` writes `_meta.ui`). Core never interprets the enrichment.

- [ ] **Step 6: Commit**
```bash
git add docs/adr docs/design/handlers.md
git commit -m "docs(adr): ADR-0033/0034 for MCP Apps module + descriptor _meta seams"
```

---

## Task 2: `_meta` on the `Tool` model

**Files:**
- Modify: `mocapi-model/src/main/java/com/callibrity/mocapi/model/Tool.java`
- Test: `mocapi-model/src/test/java/com/callibrity/mocapi/model/ToolTest.java`

**Interfaces:**
- Produces: `Tool` gains a 6th component `@JsonProperty("_meta") ObjectNode meta`, a 5-arg convenience constructor (existing call sites unchanged), and `Tool withMeta(ObjectNode)`.

- [ ] **Step 1: Write the failing test** at `ToolTest.java`:
```java
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ToolTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omits_meta_when_absent() {
    Tool tool = new Tool("t", "T", "desc", mapper.createObjectNode(), null);
    assertThat(mapper.valueToTree(tool).has("_meta")).isFalse();
  }

  @Test
  void serializes_meta_under_underscore_meta_key() {
    ObjectNode meta = mapper.createObjectNode();
    meta.putObject("ui").put("resourceUri", "ui://x");
    Tool tool = new Tool("t", "T", "desc", mapper.createObjectNode(), null).withMeta(meta);
    assertThat(mapper.valueToTree(tool).path("_meta").path("ui").path("resourceUri").asString())
        .isEqualTo("ui://x");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-model test -Dtest=ToolTest`
Expected: compile failure (`withMeta` undefined / 6-arg constructor missing).

- [ ] **Step 3: Modify `Tool.java`**:
```java
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(
    String name,
    String title,
    String description,
    ObjectNode inputSchema,
    ObjectNode outputSchema,
    @JsonProperty("_meta") ObjectNode meta) {

  /** Backward-compatible constructor for descriptors without extension metadata. */
  public Tool(
      String name, String title, String description, ObjectNode inputSchema, ObjectNode outputSchema) {
    this(name, title, description, inputSchema, outputSchema, null);
  }

  /** Returns a copy of this descriptor carrying the given {@code _meta} object. */
  public Tool withMeta(ObjectNode meta) {
    return new Tool(name, title, description, inputSchema, outputSchema, meta);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**
Run: `mvn -pl mocapi-model test -Dtest=ToolTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Format + commit**
```bash
mvn spotless:apply -pl mocapi-model
git add mocapi-model/src/main/java/com/callibrity/mocapi/model/Tool.java \
        mocapi-model/src/test/java/com/callibrity/mocapi/model/ToolTest.java
git commit -m "feat(model): optional _meta on Tool descriptor"
```

---

## Task 3: `_meta` on the `Resource` model

**Files:**
- Modify: `mocapi-model/src/main/java/com/callibrity/mocapi/model/Resource.java`
- Test: `mocapi-model/src/test/java/com/callibrity/mocapi/model/ResourceTest.java`

**Interfaces:**
- Produces: `Resource` gains `@JsonProperty("_meta") ObjectNode meta`, a 4-arg convenience constructor, and `Resource withMeta(ObjectNode)`.

- [ ] **Step 1: Write the failing test** at `ResourceTest.java`:
```java
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ResourceTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omits_meta_when_absent() {
    Resource r = new Resource("ui://x", "X", "desc", "text/html;profile=mcp-app");
    assertThat(mapper.valueToTree(r).has("_meta")).isFalse();
  }

  @Test
  void serializes_meta_under_underscore_meta_key() {
    ObjectNode meta = mapper.createObjectNode();
    meta.putObject("ui").putObject("csp").putArray("connectDomains").add("https://api.example.com");
    Resource r =
        new Resource("ui://x", "X", "desc", "text/html;profile=mcp-app").withMeta(meta);
    assertThat(
            mapper
                .valueToTree(r)
                .path("_meta")
                .path("ui")
                .path("csp")
                .path("connectDomains")
                .get(0)
                .asString())
        .isEqualTo("https://api.example.com");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-model test -Dtest=ResourceTest`
Expected: compile failure (`withMeta` undefined).

- [ ] **Step 3: Modify `Resource.java`**:
```java
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Resource(
    String uri,
    String name,
    String description,
    String mimeType,
    @JsonProperty("_meta") ObjectNode meta) {

  public Resource(String uri, String name, String description, String mimeType) {
    this(uri, name, description, mimeType, null);
  }

  public Resource withMeta(ObjectNode meta) {
    return new Resource(uri, name, description, mimeType, meta);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**
Run: `mvn -pl mocapi-model test -Dtest=ResourceTest`
Expected: PASS.

- [ ] **Step 5: Format + commit**
```bash
mvn spotless:apply -pl mocapi-model
git add mocapi-model/src/main/java/com/callibrity/mocapi/model/Resource.java \
        mocapi-model/src/test/java/com/callibrity/mocapi/model/ResourceTest.java
git commit -m "feat(model): optional _meta on Resource descriptor"
```

---

## Task 4: `ToolDescriptorCustomizer` seam

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolDescriptorCustomizer.java`
- Modify: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlers.java` (build signature + apply)
- Modify: `mocapi-autoconfigure/.../MocapiServerToolsAutoConfiguration.java` (collect + pass)
- Test: `mocapi-autoconfigure/.../server/tools/CallToolHandlersTest.java` (add a case)

**Interfaces:**
- Produces: `@FunctionalInterface ToolDescriptorCustomizer { Tool customize(Method method, Tool descriptor); }`. `CallToolHandlers.build` gains a `List<ToolDescriptorCustomizer> descriptorCustomizers` parameter (added **after** `customizers`).
- Consumes: `Tool.withMeta` (Task 2).

- [ ] **Step 1: Write the failing test.** In `CallToolHandlersTest.java`, add:
```java
@Test
void applies_tool_descriptor_customizers() {
  ToolDescriptorCustomizer stamp =
      (method, descriptor) -> descriptor.withMeta(new ObjectMapper().createObjectNode().put("k", "v"));
  CallToolHandler handler =
      CallToolHandlers.build(
          new SampleTools(), method(SampleTools.class, "greet"),
          generator, objectMapper, List.of(), List.of(stamp), valueResolver, false);
  assertThat(handler.descriptor().meta().path("k").asString()).isEqualTo("v");
}
```
(Use the existing test's helpers for `generator`, `objectMapper`, `valueResolver`, `method(...)`, and a sample `@McpTool` bean; match the arg list to the current `build` call in that test file, inserting `List.of()` for the new `descriptorCustomizers` parameter in the other existing call sites too.)

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=CallToolHandlersTest`
Expected: compile failure (`ToolDescriptorCustomizer` missing / arity mismatch).

- [ ] **Step 3: Create the interface** `ToolDescriptorCustomizer.java` (with license header — `mvn spotless:apply` adds it):
```java
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.Tool;
import java.lang.reflect.Method;

/**
 * Enriches a tool's {@link Tool} descriptor at build time (ADR-0034). Applied after the descriptor
 * is generated and before the handler is assembled; implementations return the same descriptor or a
 * copy carrying additional {@code _meta} (e.g. {@code mocapi-apps} writing {@code _meta.ui}). Core
 * does not interpret the enrichment.
 */
@FunctionalInterface
public interface ToolDescriptorCustomizer {
  Tool customize(Method method, Tool descriptor);
}
```

- [ ] **Step 4: Apply it in `CallToolHandlers.build`.** Add `List<ToolDescriptorCustomizer> descriptorCustomizers` to the parameter list (immediately after `List<CallToolHandlerCustomizer> customizers`). Immediately after `Tool descriptor = new Tool(name, title, description, inputSchema, outputSchema);` insert:
```java
    for (ToolDescriptorCustomizer descriptorCustomizer : descriptorCustomizers) {
      descriptor = descriptorCustomizer.customize(method, descriptor);
    }
```
(Change `Tool descriptor` from effectively-final usage is fine — it is reassigned before `new MutableConfig(descriptor, ...)`.)

- [ ] **Step 5: Wire the autoconfig.** In `MocapiServerToolsAutoConfiguration`, add a parameter `@Autowired(required = false) List<ToolDescriptorCustomizer> toolDescriptorCustomizers` to the `McpToolsService` bean method, normalize null → `List.of()`, and pass it into the `CallToolHandlers.build(...)` call in the new position.

- [ ] **Step 6: Run the test to verify it passes**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=CallToolHandlersTest`
Expected: PASS.

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-server,mocapi-autoconfigure
git add mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolDescriptorCustomizer.java \
        mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlers.java \
        mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerToolsAutoConfiguration.java \
        mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/server/tools/CallToolHandlersTest.java
git commit -m "feat(server): ToolDescriptorCustomizer seam for descriptor _meta"
```

---

## Task 5: `ResourceDescriptorCustomizer` seam

**Files:**
- Create: `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ResourceDescriptorCustomizer.java`
- Modify: `mocapi-server/.../resources/ReadResourceHandlers.java` (build signature + apply)
- Modify: `mocapi-autoconfigure/.../MocapiServerResourcesAutoConfiguration.java` (collect + pass)
- Test: `mocapi-autoconfigure/.../server/autoconfigure/ResourceServiceAutoConfigurationTest.java` (add a case)

**Interfaces:**
- Produces: `@FunctionalInterface ResourceDescriptorCustomizer { Resource customize(Method method, Resource descriptor); }`. `ReadResourceHandlers.build` gains a `List<ResourceDescriptorCustomizer> descriptorCustomizers` parameter after `customizers`.
- Consumes: `Resource.withMeta` (Task 3).

- [ ] **Step 1: Write the failing test.** In `ResourceServiceAutoConfigurationTest.java`, add a bean and assertion:
```java
@Test
void applies_resource_descriptor_customizers() {
  ResourceDescriptorCustomizer stamp =
      (method, descriptor) ->
          descriptor.withMeta(new ObjectMapper().createObjectNode().put("k", "v"));
  contextRunner
      .withBean(SampleResourceService.class, SampleResourceService::new)
      .withBean(ResourceDescriptorCustomizer.class, () -> stamp)
      .run(
          context -> {
            var service = context.getBean(McpResourcesService.class);
            var resource = service.listResources(null).resources().getFirst();
            assertThat(resource.meta().path("k").asString()).isEqualTo("v");
          });
}
```
(Add imports: `ResourceDescriptorCustomizer`, `tools.jackson.databind.ObjectMapper`.)

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=ResourceServiceAutoConfigurationTest`
Expected: compile failure (`ResourceDescriptorCustomizer` missing).

- [ ] **Step 3: Create the interface** `ResourceDescriptorCustomizer.java`:
```java
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.model.Resource;
import java.lang.reflect.Method;

/**
 * Enriches a resource's {@link Resource} descriptor at build time (ADR-0034). Applied after the
 * descriptor is generated; implementations return the same descriptor or a copy carrying additional
 * {@code _meta} (e.g. {@code mocapi-apps} writing {@code _meta.ui} CSP/sandbox). Core does not
 * interpret the enrichment.
 */
@FunctionalInterface
public interface ResourceDescriptorCustomizer {
  Resource customize(Method method, Resource descriptor);
}
```

- [ ] **Step 4: Apply it in `ReadResourceHandlers.build`.** Add `List<ResourceDescriptorCustomizer> descriptorCustomizers` after `customizers`. Immediately after `Resource descriptor = new Resource(uri, name, description, mimeType);` insert:
```java
    for (ResourceDescriptorCustomizer descriptorCustomizer : descriptorCustomizers) {
      descriptor = descriptorCustomizer.customize(method, descriptor);
    }
```

- [ ] **Step 5: Wire the autoconfig.** In `MocapiServerResourcesAutoConfiguration`, add `@Autowired(required = false) List<ResourceDescriptorCustomizer> resourceDescriptorCustomizers` to the `McpResourcesService` bean method, normalize null → `List.of()`, and pass it into the `ReadResourceHandlers.build(...)` call in the new position. (Resource **templates** are out of scope — the Apps extension only links `ui://` fixed resources; leave `ReadResourceTemplateHandlers.build` unchanged.)

- [ ] **Step 6: Run the test to verify it passes**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=ResourceServiceAutoConfigurationTest`
Expected: PASS (existing cases still green).

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-server,mocapi-autoconfigure
git add mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ResourceDescriptorCustomizer.java \
        mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ReadResourceHandlers.java \
        mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerResourcesAutoConfiguration.java \
        mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/server/autoconfigure/ResourceServiceAutoConfigurationTest.java
git commit -m "feat(server): ResourceDescriptorCustomizer seam for descriptor _meta"
```

---

## Task 6: `mocapi-apps` module scaffold + `_meta.ui` types

**Files:**
- Create: `mocapi-apps/pom.xml`
- Modify: `pom.xml` (add `<module>mocapi-apps</module>` after `mocapi-audit`)
- Modify: `mocapi-bom/pom.xml` (add the `mocapi-apps` artifact to `<dependencyManagement>`)
- Create: `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/McpUiToolMeta.java`
- Create: `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/McpUiResourceCsp.java`
- Create: `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/UiResourceMeta.java`
- Test: `mocapi-apps/src/test/java/com/callibrity/mocapi/apps/UiMetaSerializationTest.java`

**Interfaces:**
- Produces: records `McpUiToolMeta(String resourceUri, List<String> visibility)`; `McpUiResourceCsp(List<String> connectDomains, List<String> resourceDomains, List<String> frameDomains, List<String> baseUriDomains)`; `UiResourceMeta(McpUiResourceCsp csp, List<String> sandbox)`. All `@JsonInclude(NON_NULL)`.

- [ ] **Step 1: Create `mocapi-apps/pom.xml`** mirroring `mocapi-audit/pom.xml` (copy the license header comment). `artifactId` = `mocapi-apps`, name `Mocapi - Apps`, description one line ("MCP Apps extension (io.modelcontextprotocol/ui): declare ui:// HTML resources and link them to tools via _meta.ui, server-side only."). Dependencies: `mocapi-server` (`${project.version}`), `mocapi-api` (`${project.version}`, needed for `@McpResource` meta-annotation), `tools.jackson.core:jackson-databind`, `org.springframework.boot:spring-boot-autoconfigure` (optional), `spring-boot-starter-test` (test). Drop the `spring-security-core` dependency (not needed).

- [ ] **Step 2: Register the module.** In root `pom.xml`, add `<module>mocapi-apps</module>` immediately after `<module>mocapi-audit</module>`. In `mocapi-bom/pom.xml`, add a `<dependency>` entry for `com.callibrity.mocapi:mocapi-apps:${project.version}` matching the style of the other mocapi entries there.

- [ ] **Step 3: Write the failing test** `UiMetaSerializationTest.java`:
```java
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UiMetaSerializationTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void tool_meta_serializes_resource_uri_and_visibility() {
    var meta = new McpUiToolMeta("ui://x", List.of("model", "app"));
    var node = mapper.valueToTree(meta);
    assertThat(node.path("resourceUri").asString()).isEqualTo("ui://x");
    assertThat(node.path("visibility").get(0).asString()).isEqualTo("model");
  }

  @Test
  void resource_meta_omits_empty_csp_fields() {
    var meta = new UiResourceMeta(new McpUiResourceCsp(List.of("https://api.example.com"), null, null, null), null);
    var node = mapper.valueToTree(meta);
    assertThat(node.path("csp").path("connectDomains").get(0).asString())
        .isEqualTo("https://api.example.com");
    assertThat(node.path("csp").has("resourceDomains")).isFalse();
    assertThat(node.has("sandbox")).isFalse();
  }
}
```

- [ ] **Step 4: Run test to verify it fails**
Run: `mvn -pl mocapi-apps -am test -Dtest=UiMetaSerializationTest`
Expected: compile failure (types missing).

- [ ] **Step 5: Create the three record types.** `McpUiToolMeta.java`:
```java
package com.callibrity.mocapi.apps;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The {@code _meta.ui} shape on a tool descriptor (MCP Apps). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpUiToolMeta(String resourceUri, List<String> visibility) {}
```
`McpUiResourceCsp.java`:
```java
package com.callibrity.mocapi.apps;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** CSP origins a UI resource declares (MCP Apps); hosts enforce them on the iframe. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpUiResourceCsp(
    List<String> connectDomains,
    List<String> resourceDomains,
    List<String> frameDomains,
    List<String> baseUriDomains) {}
```
`UiResourceMeta.java`:
```java
package com.callibrity.mocapi.apps;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The {@code _meta.ui} shape on a UI resource descriptor (MCP Apps). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiResourceMeta(McpUiResourceCsp csp, List<String> sandbox) {}
```

- [ ] **Step 6: Run tests to verify they pass**
Run: `mvn -pl mocapi-apps -am test -Dtest=UiMetaSerializationTest`
Expected: PASS.

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-apps
git add pom.xml mocapi-bom/pom.xml mocapi-apps/pom.xml mocapi-apps/src
git commit -m "feat(apps): mocapi-apps module scaffold + _meta.ui types"
```

---

## Task 7: `@McpUi` + `@McpAppResource` + `@Csp` annotations

**Files:**
- Create: `mocapi-apps/.../apps/McpUi.java`
- Create: `mocapi-apps/.../apps/Csp.java`
- Create: `mocapi-apps/.../apps/McpAppResource.java`
- Test: `mocapi-apps/.../apps/AnnotationContractTest.java`

**Interfaces:**
- Produces: `@McpUi` (target METHOD) with `String value()` (the `ui://` resourceUri) and `String[] visibility() default {"model","app"}`. `@Csp` with `String[] connect() default {}; String[] resource() default {}; String[] frame() default {}; String[] baseUri() default {};`. `@McpAppResource` (target METHOD) meta-annotated `@McpResource(uri = "", mimeType = "text/html;profile=mcp-app")` with `@AliasFor` `uri`/`name` and `Csp csp() default @Csp; String[] sandbox() default {};`.

- [ ] **Step 1: Write the failing test** `AnnotationContractTest.java` — assert the annotations are discoverable/mergeable exactly as the foundation test proved:
```java
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.resources.McpResource;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

class AnnotationContractTest {

  static class Fixture {
    @McpAppResource(uri = "ui://dash", name = "Dash", csp = @Csp(connect = "https://api.example.com"))
    public void ui() {}

    @McpUi(value = "ui://dash", visibility = {"app"})
    public void tool() {}
  }

  @Test
  void app_resource_merges_mcp_resource_with_ui_mime_and_aliased_uri() throws Exception {
    Method m = Fixture.class.getMethod("ui");
    McpResource merged = AnnotatedElementUtils.findMergedAnnotation(m, McpResource.class);
    assertThat(merged).isNotNull();
    assertThat(merged.uri()).isEqualTo("ui://dash");
    assertThat(merged.mimeType()).isEqualTo("text/html;profile=mcp-app");
  }

  @Test
  void app_resource_exposes_csp() throws Exception {
    Method m = Fixture.class.getMethod("ui");
    McpAppResource app = m.getAnnotation(McpAppResource.class);
    assertThat(app.csp().connect()).containsExactly("https://api.example.com");
  }

  @Test
  void mcp_ui_carries_resource_uri_and_visibility() throws Exception {
    Method m = Fixture.class.getMethod("tool");
    McpUi ui = m.getAnnotation(McpUi.class);
    assertThat(ui.value()).isEqualTo("ui://dash");
    assertThat(ui.visibility()).containsExactly("app");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-apps -am test -Dtest=AnnotationContractTest`
Expected: compile failure (annotations missing).

- [ ] **Step 3: Create `McpUi.java`**:
```java
package com.callibrity.mocapi.apps;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Links a tool's results to a {@code ui://} UI resource (MCP Apps) via the tool descriptor's {@code _meta.ui}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpUi {
  /** The linked UI resource URI (must start with {@code ui://}). */
  String value();

  /** UI access axis: {@code "model"} and/or {@code "app"}. Default: both. */
  String[] visibility() default {"model", "app"};
}
```

- [ ] **Step 4: Create `Csp.java`**:
```java
package com.callibrity.mocapi.apps;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the CSP origins a UI resource needs (MCP Apps). Empty arrays mean "no external access". */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Csp {
  String[] connect() default {};

  String[] resource() default {};

  String[] frame() default {};

  String[] baseUri() default {};
}
```

- [ ] **Step 5: Create `McpAppResource.java`** (meta-annotated composed annotation — mirrors the proven foundation-test fixture):
```java
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.api.resources.McpResource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Declares a {@code ui://} MCP Apps UI resource. Composes {@link McpResource} (MIME defaulted to
 * {@code text/html;profile=mcp-app}) via meta-annotation, so the method registers as a resource
 * through the standard scan (ADR-0032); {@code csp}/{@code sandbox} drive the resource's
 * {@code _meta.ui}. The annotated method returns the HTML (a {@code String} or a
 * {@code ReadResourceResult}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@McpResource(uri = "", mimeType = "text/html;profile=mcp-app")
public @interface McpAppResource {

  @AliasFor(annotation = McpResource.class, attribute = "uri")
  String uri();

  @AliasFor(annotation = McpResource.class, attribute = "name")
  String name() default "";

  Csp csp() default @Csp;

  String[] sandbox() default {};
}
```
Note: `org.springframework.core.annotation.AliasFor` requires `spring-core` — already transitively present via `mocapi-server`. If the compiler cannot resolve it, add `org.springframework:spring-core` explicitly to `mocapi-apps/pom.xml`.

- [ ] **Step 6: Run tests to verify they pass**
Run: `mvn -pl mocapi-apps -am test -Dtest=AnnotationContractTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-apps
git add mocapi-apps/src
git commit -m "feat(apps): @McpUi, @McpAppResource, @Csp annotations"
```

---

## Task 8: Apps descriptor customizers + capability customizer

**Files:**
- Create: `mocapi-apps/.../apps/AppsToolDescriptorCustomizer.java`
- Create: `mocapi-apps/.../apps/AppsResourceDescriptorCustomizer.java`
- Create: `mocapi-apps/.../apps/UiCapabilityCustomizer.java`
- Test: `mocapi-apps/.../apps/AppsDescriptorCustomizerTest.java`

**Interfaces:**
- Consumes: `ToolDescriptorCustomizer`/`ResourceDescriptorCustomizer` (Tasks 4–5), `Tool.withMeta`/`Resource.withMeta` (Tasks 2–3), `ServerCapabilitiesCustomizer` (ADR-0031, already on branch), `McpUiToolMeta`/`UiResourceMeta`/`McpUiResourceCsp` (Task 6), `@McpUi`/`@McpAppResource`/`@Csp` (Task 7).
- Produces: three `@Component`-able beans; each customizer takes an `ObjectMapper` (constructor injection) to build the `_meta` `ObjectNode`.

- [ ] **Step 1: Write the failing test** `AppsDescriptorCustomizerTest.java`:
```java
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.Tool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AppsDescriptorCustomizerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  static class Fixture {
    @McpUi(value = "ui://dash", visibility = {"app"})
    public void tool() {}

    @McpAppResource(uri = "ui://dash", csp = @Csp(connect = "https://api.example.com"))
    public void ui() {}

    public void plain() {}
  }

  private Method method(String name) throws Exception {
    return Fixture.class.getMethod(name);
  }

  @Test
  void tool_customizer_stamps_ui_meta_when_McpUi_present() throws Exception {
    var customizer = new AppsToolDescriptorCustomizer(mapper);
    Tool out = customizer.customize(method("tool"), new Tool("t", "T", "d", mapper.createObjectNode(), null));
    assertThat(out.meta().path("ui").path("resourceUri").asString()).isEqualTo("ui://dash");
    assertThat(out.meta().path("ui").path("visibility").get(0).asString()).isEqualTo("app");
  }

  @Test
  void tool_customizer_is_a_noop_without_McpUi() throws Exception {
    var customizer = new AppsToolDescriptorCustomizer(mapper);
    Tool in = new Tool("t", "T", "d", mapper.createObjectNode(), null);
    assertThat(customizer.customize(method("plain"), in)).isSameAs(in);
  }

  @Test
  void resource_customizer_stamps_ui_csp_when_McpAppResource_present() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    Resource out =
        customizer.customize(method("ui"), new Resource("ui://dash", "Dash", "d", "text/html;profile=mcp-app"));
    assertThat(out.meta().path("ui").path("csp").path("connectDomains").get(0).asString())
        .isEqualTo("https://api.example.com");
  }

  @Test
  void resource_customizer_is_a_noop_without_McpAppResource() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    Resource in = new Resource("res://x", "X", "d", "text/plain");
    assertThat(customizer.customize(method("plain"), in)).isSameAs(in);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `mvn -pl mocapi-apps -am test -Dtest=AppsDescriptorCustomizerTest`
Expected: compile failure (customizers missing).

- [ ] **Step 3: Create `AppsToolDescriptorCustomizer.java`**:
```java
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.ToolDescriptorCustomizer;
import java.lang.reflect.Method;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes a tool's {@code _meta.ui} from an {@link McpUi} annotation, when present. */
public class AppsToolDescriptorCustomizer implements ToolDescriptorCustomizer {

  private final ObjectMapper mapper;

  public AppsToolDescriptorCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Tool customize(Method method, Tool descriptor) {
    McpUi ui = method.getAnnotation(McpUi.class);
    if (ui == null) {
      return descriptor;
    }
    McpUiToolMeta uiMeta = new McpUiToolMeta(ui.value(), List.of(ui.visibility()));
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    return descriptor.withMeta(meta);
  }
}
```

- [ ] **Step 4: Create `AppsResourceDescriptorCustomizer.java`**:
```java
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.resources.ResourceDescriptorCustomizer;
import java.lang.reflect.Method;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes a UI resource's {@code _meta.ui} (CSP/sandbox) from an {@link McpAppResource}, when present. */
public class AppsResourceDescriptorCustomizer implements ResourceDescriptorCustomizer {

  private final ObjectMapper mapper;

  public AppsResourceDescriptorCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Resource customize(Method method, Resource descriptor) {
    McpAppResource app = AnnotatedElementUtils.findMergedAnnotation(method, McpAppResource.class);
    if (app == null) {
      return descriptor;
    }
    UiResourceMeta uiMeta = new UiResourceMeta(csp(app.csp()), listOrNull(app.sandbox()));
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    return descriptor.withMeta(meta);
  }

  private McpUiResourceCsp csp(Csp csp) {
    McpUiResourceCsp result =
        new McpUiResourceCsp(
            listOrNull(csp.connect()),
            listOrNull(csp.resource()),
            listOrNull(csp.frame()),
            listOrNull(csp.baseUri()));
    boolean empty =
        result.connectDomains() == null
            && result.resourceDomains() == null
            && result.frameDomains() == null
            && result.baseUriDomains() == null;
    return empty ? null : result;
  }

  private static List<String> listOrNull(String[] values) {
    return values.length == 0 ? null : List.of(values);
  }
}
```

- [ ] **Step 5: Create `UiCapabilityCustomizer.java`**:
```java
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.discover.ServerCapabilitiesCustomizer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Declares the {@code io.modelcontextprotocol/ui} extension capability (MCP Apps). */
public class UiCapabilityCustomizer implements ServerCapabilitiesCustomizer {

  private static final String UI_EXTENSION_ID = "io.modelcontextprotocol/ui";
  private static final String RESOURCE_MIME_TYPE = "text/html;profile=mcp-app";

  private final ObjectMapper mapper;

  public UiCapabilityCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void customize(ServerCapabilities.Builder capabilities) {
    ObjectNode config = mapper.createObjectNode();
    config.putArray("mimeTypes").add(RESOURCE_MIME_TYPE);
    capabilities.extension(UI_EXTENSION_ID, config);
  }
}
```

- [ ] **Step 6: Run tests to verify they pass**
Run: `mvn -pl mocapi-apps -am test -Dtest=AppsDescriptorCustomizerTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-apps
git add mocapi-apps/src
git commit -m "feat(apps): descriptor customizers + ui capability customizer"
```

---

## Task 9: `MocapiAppsAutoConfiguration` + registration

**Files:**
- Create: `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/apps/MocapiAppsAutoConfiguration.java`
- Modify: `mocapi-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mocapi-autoconfigure/pom.xml` (optional `mocapi-apps` dependency)
- Test: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/apps/MocapiAppsAutoConfigurationTest.java`

**Interfaces:**
- Consumes: the three customizer classes (Task 8); `ObjectMapper` bean.
- Produces: three beans (`@ConditionalOnMissingBean` each): `AppsToolDescriptorCustomizer`, `AppsResourceDescriptorCustomizer`, `UiCapabilityCustomizer`, gated on the Apps classes being present.

- [ ] **Step 1: Add the optional dependency** in `mocapi-autoconfigure/pom.xml`: `com.callibrity.mocapi:mocapi-apps:${project.version}` with `<optional>true</optional>` (mirror how `mocapi-audit` / `mocapi-o11y` are declared there).

- [ ] **Step 2: Write the failing test** `MocapiAppsAutoConfigurationTest.java`:
```java
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class MocapiAppsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MocapiAppsAutoConfiguration.class))
          .withUserConfiguration(Infra.class);

  @Configuration(proxyBeanMethods = false)
  static class Infra {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Test
  void registers_apps_customizers() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(AppsToolDescriptorCustomizer.class);
          assertThat(context).hasSingleBean(AppsResourceDescriptorCustomizer.class);
          assertThat(context).hasSingleBean(UiCapabilityCustomizer.class);
        });
  }
}
```

- [ ] **Step 3: Run test to verify it fails**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=MocapiAppsAutoConfigurationTest`
Expected: compile failure (autoconfig missing).

- [ ] **Step 4: Create `MocapiAppsAutoConfiguration.java`**:
```java
package com.callibrity.mocapi.apps;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Registers the MCP Apps descriptor customizers and the {@code ui} capability when mocapi-apps is present. */
@AutoConfiguration
@ConditionalOnClass(UiCapabilityCustomizer.class)
public class MocapiAppsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AppsToolDescriptorCustomizer appsToolDescriptorCustomizer(ObjectMapper objectMapper) {
    return new AppsToolDescriptorCustomizer(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AppsResourceDescriptorCustomizer appsResourceDescriptorCustomizer(ObjectMapper objectMapper) {
    return new AppsResourceDescriptorCustomizer(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public UiCapabilityCustomizer uiCapabilityCustomizer(ObjectMapper objectMapper) {
    return new UiCapabilityCustomizer(objectMapper);
  }
}
```

- [ ] **Step 5: Register the autoconfig.** Append `com.callibrity.mocapi.apps.MocapiAppsAutoConfiguration` to the `AutoConfiguration.imports` file (after `MocapiAuditAutoConfiguration`).

- [ ] **Step 6: Run tests to verify they pass**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=MocapiAppsAutoConfigurationTest`
Expected: PASS.

- [ ] **Step 7: Format + commit**
```bash
mvn spotless:apply -pl mocapi-autoconfigure
git add mocapi-autoconfigure/pom.xml \
        mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/apps/MocapiAppsAutoConfiguration.java \
        mocapi-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/apps/MocapiAppsAutoConfigurationTest.java
git commit -m "feat(apps): MocapiAppsAutoConfiguration wiring"
```

---

## Task 10: End-to-end integration test

**Files:**
- Test: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/apps/AppsEndToEndTest.java`

**Interfaces:**
- Consumes: everything above, wired through the real autoconfig (tools, resources, discover, apps).

- [ ] **Step 1: Write the integration test.** Register a bean that declares a `@McpAppResource` UI resource and an `@McpUi`-linked `@McpTool`, boot the server autoconfigs, and assert the wire output:
```java
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.discover.DiscoverHandler;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class AppsEndToEndTest {

  static class WeatherApp {
    @McpAppResource(uri = "ui://weather/dashboard", name = "Weather",
        csp = @Csp(connect = "https://api.weather.com"))
    public String dashboard() {
      return "<html></html>";
    }

    @McpTool(name = "get_weather", description = "Get weather")
    @McpUi("ui://weather/dashboard")
    public String getWeather() {
      return "sunny";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Infra {
    @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    @Bean JsonRpcDispatcher jsonRpcDispatcher() { return mock(JsonRpcDispatcher.class); }
    @Bean WeatherApp weatherApp() { return new WeatherApp(); }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerResourcesAutoConfiguration.class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration.class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration.class,
                  MocapiAppsAutoConfiguration.class))
          .withUserConfiguration(Infra.class);

  @Test
  void tool_list_carries_ui_resource_uri() {
    runner.run(context -> {
      var tools = context.getBean(McpToolsService.class).listTools(null).tools();
      var tool = tools.stream().filter(t -> t.name().equals("get_weather")).findFirst().orElseThrow();
      assertThat(tool.meta().path("ui").path("resourceUri").asString())
          .isEqualTo("ui://weather/dashboard");
    });
  }

  @Test
  void resource_list_carries_ui_csp_and_html_mime() {
    runner.run(context -> {
      var resource = context.getBean(McpResourcesService.class).listResources(null).resources().getFirst();
      assertThat(resource.uri()).isEqualTo("ui://weather/dashboard");
      assertThat(resource.mimeType()).isEqualTo("text/html;profile=mcp-app");
      assertThat(resource.meta().path("ui").path("csp").path("connectDomains").get(0).asString())
          .isEqualTo("https://api.weather.com");
    });
  }

  @Test
  void discover_advertises_ui_capability() {
    runner.run(context -> {
      ServerCapabilities caps = context.getBean(DiscoverHandler.class).discover().capabilities();
      assertThat(caps.extensions()).containsKey("io.modelcontextprotocol/ui");
    });
  }
}
```
Note: confirm the accessor names against the real API when writing — `ListToolsResult.tools()`, `ListResourcesResult.resources()`, `DiscoverResult.capabilities()`. If `DiscoverHandler.discover()` returns a `DiscoverResult`, read `.capabilities()` from it (adjust if the accessor differs).

- [ ] **Step 2: Run the test**
Run: `mvn -pl mocapi-autoconfigure -am test -Dtest=AppsEndToEndTest`
Expected: PASS (3 tests). If it fails on an accessor name, fix the accessor (do not change production behavior).

- [ ] **Step 3: Commit**
```bash
git add mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/apps/AppsEndToEndTest.java
git commit -m "test(apps): end-to-end _meta.ui + ui capability through the real autoconfig"
```

---

## Task 11: Design doc + user guide

**Files:**
- Create: `docs/design/apps.md`
- Create: `docs/guides/apps.md`
- Modify: `docs/adr/README.md` (already done in Task 1 — skip if present)

**Interfaces:** documentation only.

- [ ] **Step 1: Write `docs/design/apps.md`** — the current architecture: the `mocapi-apps` module, the `_meta.ui` shapes, the descriptor customizers, `UiCapabilityCustomizer`, the `@McpAppResource`/`@McpUi` author API, and the explicit out-of-scope boundary (no postMessage/JS bridge). Cross-link ADR-0033/0034 and the design spec.

- [ ] **Step 2: Write `docs/guides/apps.md`** — a task-oriented how-to: add the `mocapi-apps` dependency; declare a `ui://` resource with `@McpAppResource` (including `@Csp`); link a tool with `@McpUi`; what the host does with it; and a prominent pointer to the official ext-apps JS SDK for the in-iframe side (which mocapi does not provide). Include the wire examples from spec §9.

- [ ] **Step 3: Commit**
```bash
git add docs/design/apps.md docs/guides/apps.md
git commit -m "docs: MCP Apps design doc + user guide"
```

---

## Task 12: Full verification

- [ ] **Step 1: Full clean build.**
Run: `mvn clean verify`
Expected: `BUILD SUCCESS`, 0 failures/errors across all modules (including the new `mocapi-apps`, the transports, oauth2, conformance, and examples). Do not run it with `-q` piped to `grep` — read Maven's real exit and the reactor summary.

- [ ] **Step 2: Release-profile javadoc check** (catches doclint errors `verify` misses; the new public annotations/types must have clean javadoc).
Run: `mvn -P release javadoc:jar -DskipTests`
Expected: success.

- [ ] **Step 3: Confirm no stray formatting/license drift.**
Run: `mvn spotless:check`
Expected: success.

---

## Self-Review

**Spec coverage** (design spec §-by-§):
- §2 scope boundary (no JS bridge) → Tasks 7–11 implement only server metadata; guide (Task 11) points to the JS SDK. ✅
- §3 wire requirements (`ui://`, `text/html;profile=mcp-app`, tool `_meta.ui.resourceUri`+`visibility`, resource `_meta.ui.csp`/`sandbox`, `ui` capability, `mimeTypes`) → Tasks 6–10. ✅
- §5.2 core touches: items 1–2 (`_meta` on descriptors + customizer seams) → Tasks 2–5; items 3–4 already shipped (foundation). ✅
- §6 author API (`@McpAppResource` meta-annotated, `@McpUi` companion, no `UiContext`) → Task 7; no `UiContext` anywhere. ✅
- §7 capability declaration → Task 8 `UiCapabilityCustomizer`. ✅
- §10 ADR/design-doc obligations → Tasks 1, 11; ADR-0022 flip in Task 1. ✅
- §11 testing strategy → unit (Tasks 6–8), integration (Task 10). Conformance suite intentionally deferred (host-side e2e out of scope) — matches spec §11. ✅

**Placeholder scan:** no "TBD"/"handle edge cases"/"similar to Task N" — every code step has concrete code. The two "confirm accessor names" notes (Task 10) are verification instructions, not placeholders (the code is written; the note guards a name mismatch).

**Type consistency:** `Tool.withMeta`/`Resource.withMeta` (Tasks 2–3) used by Tasks 4–5 and 8; `ToolDescriptorCustomizer.customize(Method, Tool)` / `ResourceDescriptorCustomizer.customize(Method, Resource)` signatures identical across Tasks 4–5 (definition) and 8 (implementation); `McpUiToolMeta(resourceUri, visibility)` / `UiResourceMeta(csp, sandbox)` / `McpUiResourceCsp(connect/resource/frame/baseUri Domains)` consistent across Tasks 6 and 8; `UI_EXTENSION_ID = "io.modelcontextprotocol/ui"` matches the Task 10 discover assertion and spec §7.

**Deferred deliberately (documented, not gaps):** content-item `_meta` (per-response override — spec §4/§6.3 non-goal); resource-template `_meta.ui` (Apps links fixed `ui://` resources only); conformance suite (host-side).
