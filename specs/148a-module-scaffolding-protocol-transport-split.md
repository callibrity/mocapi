# Module scaffolding: split mocapi-core into mocapi-protocol + mocapi-transport-streamable-http

## What to build

Create two new Maven modules and move existing code from
`mocapi-core` into the appropriate module. **This is a mechanical
file-move spec — no refactoring, no new classes, no behavior
changes.** The goal is to get the module structure in place so
subsequent specs can build on it.

After this spec:
- `mocapi-protocol` contains all transport-agnostic code
- `mocapi-transport-streamable-http` contains all HTTP/SSE/Odyssey code
- `mocapi-core` is deleted
- `mocapi-spring-boot-starter` depends on both new modules
- `mvn verify` passes, conformance suite still 39/39

### Module layout

```
mocapi-protocol/
├── pom.xml
│   depends on: mocapi-model, ripcurl-core, ripcurl-autoconfigure,
│               substrate-core, codec-jackson, methodical-jackson3,
│               spring-boot-autoconfigure (for @AutoConfiguration)
└── src/main/java/com/callibrity/mocapi/
    ├── McpProtocol.java
    ├── McpTransport.java
    ├── McpContext.java
    ├── McpLifecycleEvent.java
    ├── MocapiProperties.java
    ├── MocapiCodecAutoConfiguration.java
    ├── MocapiAutoConfiguration.java  (session store bean, session service, etc.)
    ├── session/        (McpSessionStore, SubstrateAtomMcpSessionStore,
    │                    McpSessionService, McpSession, McpSessionMethods,
    │                    McpLoggingMethods, DefaultMcpSessionStream,
    │                    McpSessionStream)
    ├── tools/          (ToolsRegistry, McpTool, McpToolMethods,
    │                    AnnotationMcpTool, ToolServiceMcpToolProvider,
    │                    MocapiToolsAutoConfiguration, schema/, annotation/)
    ├── resources/      (ResourcesRegistry, McpResourceMethods,
    │                    MocapiResourcesAutoConfiguration)
    ├── prompts/        (PromptsRegistry, McpPromptMethods,
    │                    MocapiPromptsAutoConfiguration)
    ├── stream/         (McpStreamContext, DefaultMcpStreamContext,
    │                    McpSamplingTimeoutException, elicitation/)
    ├── server/         (McpCompletionMethods)
    ├── security/       (Ciphers)
    └── util/           (Cursors)

mocapi-transport-streamable-http/
├── pom.xml
│   depends on: mocapi-protocol, spring-boot-starter-webmvc, odyssey
└── src/main/java/com/callibrity/mocapi/http/
    ├── StreamableHttpController.java
    ├── McpRequestValidator.java
    └── McpRequestId.java
```

### What moves where

**To `mocapi-protocol`** — everything that doesn't import
`org.springframework.web`, `SseEmitter`, or Odyssey:
- All interfaces: `McpProtocol`, `McpTransport`, `McpContext`,
  `McpLifecycleEvent`
- Session: `McpSessionStore`, `SubstrateAtomMcpSessionStore`,
  `McpSessionService`, `McpSession`, `McpSessionMethods`,
  `McpLoggingMethods`, `DefaultMcpSessionStream`,
  `McpSessionStream`
- Tools: entire `tools/` package
- Resources: entire `resources/` package
- Prompts: entire `prompts/` package
- Stream: entire `stream/` package
- Server: `McpCompletionMethods`
- Security: `Ciphers`
- Util: `Cursors`
- Config: `MocapiProperties`, `MocapiAutoConfiguration`,
  `MocapiCodecAutoConfiguration`
- Resources: `mocapi-defaults.properties`,
  `AutoConfiguration.imports`

**To `mocapi-transport-streamable-http`** — everything that
imports Spring Web or Odyssey:
- `StreamableHttpController`
- `McpRequestValidator`
- `McpRequestId`

**Note**: `McpSessionService` currently imports Odyssey
(`org.jwcarman.odyssey.core.Odyssey`). This creates a temporary
dependency from `mocapi-protocol` on Odyssey. That's OK for this
scaffolding spec — spec 148d will break this dependency when
`McpSessionService` is refactored to use `McpTransport` instead
of Odyssey directly. For now, `mocapi-protocol` declares Odyssey
as a dependency. It will be removed in a later spec.

Similarly, `DefaultMcpSessionStream` imports
`OdysseyPublisher` and `SseEmitter` — it straddles the boundary.
For this scaffolding spec, keep it in `mocapi-protocol` with the
Odyssey dependency. Spec 148d deletes it entirely (replaced by
`OdysseyTransport` in the HTTP module).

### What moves in tests

Tests follow their source:
- All `session/`, `tools/`, `resources/`, `prompts/`, `stream/`,
  `server/` tests → `mocapi-protocol/src/test/`
- `MocapiAutoConfigurationTest`, `MocapiToolsAutoConfigurationTest`
  → `mocapi-protocol/src/test/`
- `StreamableHttpControllerTest`,
  `StreamableHttpControllerComplianceTest`,
  `StreamableHttpControllerGetTest` → `mocapi-transport-streamable-http/src/test/`
- `TestAtomSessionStore` → `mocapi-protocol/src/test/`

### Starter update

`mocapi-spring-boot-starter/pom.xml` currently depends on
`mocapi-core`. Change to depend on both:

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
```

### Parent pom update

Replace `<module>mocapi-core</module>` with:
```xml
<module>mocapi-protocol</module>
<module>mocapi-transport-streamable-http</module>
```

### AutoConfiguration.imports

Split the auto-config imports file:

**`mocapi-protocol/.../AutoConfiguration.imports`**:
```
com.callibrity.mocapi.MocapiCodecAutoConfiguration
com.callibrity.mocapi.tools.MocapiToolsAutoConfiguration
com.callibrity.mocapi.resources.MocapiResourcesAutoConfiguration
com.callibrity.mocapi.prompts.MocapiPromptsAutoConfiguration
com.callibrity.mocapi.MocapiAutoConfiguration
```

**`mocapi-transport-streamable-http/.../AutoConfiguration.imports`**:
(empty for now — the controller is component-scanned via
`@RestController`, not auto-configured)

## Acceptance criteria

- [ ] `mocapi-core` module is deleted from the filesystem and
      parent pom.
- [ ] `mocapi-protocol` module exists with all protocol code.
- [ ] `mocapi-transport-streamable-http` module exists with all
      HTTP/controller code.
- [ ] `mocapi-spring-boot-starter` depends on both new modules.
- [ ] Parent pom `<modules>` lists both new modules.
- [ ] All packages retain their original names (`com.callibrity.mocapi.*`).
- [ ] No source files are modified — only moved between modules.
      (Exception: import changes if any are needed due to module
      boundaries, but there shouldn't be any since package names
      don't change.)
- [ ] `mvn verify` passes across the full reactor.
- [ ] MCP conformance suite: 39/39 passing.

## Implementation notes

- **Use `git mv`** to preserve history.
- **Build order matters**: `mocapi-protocol` must build before
  `mocapi-transport-streamable-http` (the transport depends on
  the protocol).
- **The temporary Odyssey dependency in `mocapi-protocol`** is
  intentional. It will be removed in spec 148d when
  `McpSessionService` and `DefaultMcpSessionStream` are refactored.
  Don't try to remove it now — that's refactoring, not scaffolding.
- **Example apps** depend on `mocapi-spring-boot-starter` which
  pulls both modules transitively. No example pom changes needed.
- **`mocapi-compat`** depends on `mocapi-spring-boot-starter`.
  Same — no changes needed.
- **Commit as one big commit** — module splits are atomic. A
  half-done split breaks the build.
