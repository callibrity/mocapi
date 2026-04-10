# PRD — Mocapi

---

## What this project is

Mocapi is a modular Spring Boot framework for building Model Context Protocol (MCP) servers
with a clean, annotation-driven API. Developers create MCP tools via `@ToolService`/`@Tool`
and prompts via `@PromptService`/`@Prompt` as Spring components. The framework handles
JSON-RPC protocol dispatch, JSON schema generation/validation, SSE streaming transport,
and Spring Boot auto-configuration. It targets the MCP 2025-11-25 spec.

Currently undergoing a major upgrade: Java 25, Spring Boot 4.0.5, removal of the internal
RipCurl JSON-RPC library, and fixes from a comprehensive code review (see `CODE_REVIEW.md`).
The implementation plan is at `docs/plans/2026-04-05-java25-spring-boot-ripcurl-removal.md`.

---

## Tech stack

- Language: Java 25 (Liberica JDK 25.0.2)
- Framework: Spring Boot 4.0.5 (upgrading from 3.5.3)
- Build tool: Maven 3.9.12
- Testing: JUnit 5 + Mockito + AssertJ (unit tests), Spring Boot Test with failsafe (integration tests)
- Formatting: Spotless with Google Java Format
- License: license-maven-plugin (Apache 2.0 headers)
- Additional libraries: Jackson, Lombok, VicTools JSON Schema, Everit JSON Schema, commons-lang3, Swagger Annotations

---

## How to run the project

```bash
# Build the project
mvn clean install

# Run the example app
mvn spring-boot:run -pl mocapi-example
```

---

## How to run tests

```bash
# Run all tests (unit + integration) and all checks
mvn verify

# Run unit tests only
mvn test

# Run a single test class
mvn test -pl mocapi-core -Dtest=McpServerTest

# Run integration tests only
mvn failsafe:integration-test failsafe:verify -pl mocapi-example
```

Expected output when all tests pass:
```
[INFO] BUILD SUCCESS
```

---

## How to lint / type-check

```bash
# Check formatting (Spotless) and license headers
mvn spotless:check license:check

# Auto-fix formatting
mvn spotless:apply
```

Both `spotless:check` and `license:check` are bound to the `validate` phase, so `mvn verify`
catches them automatically before compilation.

---

## Coding conventions

- Lombok for boilerplate reduction (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`, `@Getter`)
- Records for immutable data types (DTOs, responses, descriptors)
- Google Java Format enforced via Spotless
- Never use `@SuppressWarnings` — fix the underlying issue. **One
  narrow exception**: `@SuppressWarnings("deprecation")` is allowed
  (and required by Sonar rule S1874) when a deprecated API is being
  used legitimately — specifically, when the MCP specification
  itself mandates support for a deprecated type (e.g.,
  `LegacyTitledEnumSchema` which exists for backward compatibility
  with pre-2025-11-25 clients) or when a test deliberately exercises
  a deprecated code path. Every such suppression must be accompanied
  by a short comment explaining why the deprecated usage is
  intentional.
- Apache 2.0 license headers on all source files (managed by license-maven-plugin)
- Test files mirror source structure: `src/test/java/...` matching `src/main/java/...`
- Integration tests use `*IT.java` suffix (Maven failsafe convention)

---

## Repository structure

```
mocapi-parent (pom.xml)          Multi-module Maven parent
├── mocapi-core/                 Core MCP protocol abstractions, JSON-RPC types, utilities
│   └── src/main/java/com/callibrity/mocapi/
│       ├── server/              McpServer, McpServerCapability, ServerInfo
│       │   ├── jsonrpc/         JSON-RPC annotations and exception hierarchy
│       │   ├── invoke/          JsonMethodInvoker for reflective method dispatch
│       │   └── util/            Names, Parameters, LazyInitializer utilities
│       └── client/              ClientCapabilities, ClientInfo records
├── mocapi-tools/                MCP tools capability (@ToolService/@Tool)
│   └── src/main/java/com/callibrity/mocapi/tools/
│       ├── annotation/          @Tool, @ToolService, AnnotationMcpTool
│       └── schema/              JSON schema generation (VicTools)
├── mocapi-prompts/              MCP prompts capability (@PromptService/@Prompt)
│   └── src/main/java/com/callibrity/mocapi/prompts/
│       └── annotation/          @Prompt, @PromptService, AnnotationMcpPrompt
├── mocapi-autoconfigure/        Spring Boot auto-configuration
│   └── src/main/java/com/callibrity/mocapi/autoconfigure/
│       ├── sse/                 McpStreamingController, McpSessionManager, McpStreamEmitter
│       ├── tools/               MocapiToolsAutoConfiguration
│       └── prompts/             MocapiPromptsAutoConfiguration
├── mocapi-spring-boot-starter/  Convenience starter (bundles core + autoconfigure)
├── mocapi-example/              Example app with HelloTool, Rot13Tool, CodeReviewPrompts
├── mocapi-resources/            Shared resources
└── mocapi-coverage/             JaCoCo aggregate coverage
```

---

## Definition of "done" for a spec

A spec is done when ALL of the following are true:

- [ ] The feature described in the spec is implemented
- [ ] All existing tests pass (`mvn verify`)
- [ ] New tests cover the new behavior (unless the spec says otherwise)
- [ ] Spotless and license checks pass (bound to `validate`, caught by `mvn verify`)
- [ ] No debug code left in
- [ ] progress.txt is updated with verification results

---

## Constraints and guardrails

- Never use `@SuppressWarnings` — fix the underlying issue instead.
  **Narrow exception**: `@SuppressWarnings("deprecation")` is
  allowed for deprecated MCP-spec types that must be supported for
  backward compatibility (e.g., `LegacyTitledEnumSchema`) and for
  tests that deliberately exercise deprecated code paths. Every
  such suppression must have a comment explaining the intent.
- Never commit secrets or credentials
- Never modify the public API shape of existing annotations (`@Tool`, `@ToolService`, `@Prompt`, `@PromptService`) without an explicit spec
- Never remove or weaken input validation (JSON schema validation on tool calls)
- License headers must be present on all source files
- Read `CLAUDE.md` for additional project-level instructions

---

## Environment

- No runtime environment variables required for development or testing
- `SONAR_TOKEN` — used in CI only (GitHub Actions) for SonarCloud analysis
- Java 25 and Maven 3.9+ must be installed locally
