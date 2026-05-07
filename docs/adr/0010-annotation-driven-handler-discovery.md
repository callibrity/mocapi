# ADR-0010 — Annotation-driven handler discovery on Spring beans

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

An MCP server exposes four kinds of handlers — tools, prompts, resources, and
resource templates. Mocapi needs a way for users to register handlers without
reaching into the framework's internals: no manual registry, no factory
boilerplate, no DSL the IDE can't resolve. The mechanism also has to play
nicely with whatever bean wiring the host application already uses (Spring
`@Component`, `@Bean` factories, `@Service`, hand-built configuration), and
support multiple handlers per bean class so a single domain object can host
several related operations.

Earlier iterations of mocapi required class-level marker annotations
(`@ToolService`, `@PromptService`, `@ResourceService`) to narrow the
bean-method scan. That was always a startup-time micro-optimization — and the
measured cost of scanning every bean for handler annotations is sub-100ms
even on Spring apps with thousands of beans. The marker added ceremony per
handler class with no payoff: a developer adding a new tool had to remember
both the method-level annotation and the class-level marker, and the
annotation pair drifted in name (`@ToolMethod` on the method, `@ToolService`
on the class) for no reason that survived scrutiny.

Reflective dispatch on annotated methods is provided by the `methodical`
library (Callibrity OSS). Methodical 0.6+ delivers a stateless
`MethodInvokerFactory`, an interceptor chain, and parameter resolvers — the
exact primitives mocapi needs to invoke a `Method` reference with
JSON-derived arguments and to layer cross-cutting behavior on top.

## Decision

Handlers are discovered by scanning every Spring bean's methods for one of
four mocapi method-level annotations. No class-level marker is required; the
method annotation is the opt-in.

The four annotations carry a consistent `@Mcp` prefix:

| Handler kind | Annotation | Package |
|---|---|---|
| Tool | `@McpTool` | `com.callibrity.mocapi.api.tools` |
| Prompt | `@McpPrompt` | `com.callibrity.mocapi.api.prompts` |
| Resource | `@McpResource` | `com.callibrity.mocapi.api.resources` |
| Resource template | `@McpResourceTemplate` | `com.callibrity.mocapi.api.resources` |

A fifth annotation, `@McpToolParams`, opts an `@McpTool` parameter into
record-based binding: the entire `tools/call` arguments object deserializes
into a single typed record parameter, replacing per-name positional binding
for tools that would otherwise carry many parameters.

Discovery runs once during the `@PostConstruct` of each provider
autoconfiguration (tools provider, prompts provider, resources provider). A
single pass walks every bean in the `ApplicationContext`, groups
`(bean, Method)` pairs by which mocapi annotation they carry, and exposes
the result as a cache the per-kind builders consume. No second pass, no
duplicated reflection. Each registered handler is logged at `INFO` so
startup output is the source of truth for what got wired (see
[architecture.md — Startup Logging](../design/architecture-overview.md#startup-logging)).

Reflective dispatch goes through Methodical 0.6+. Each handler is built as a
`MethodInvoker` with the appropriate `ParameterResolver` set (JSON-node
resolver for tools, argument-map resolver for prompts and resource
templates, scoped-value resolvers for `McpSession` / `McpTransport` /
`McpToolContext`) and the appropriate interceptor chain
(see [ADR-0011](0011-customizer-spi-and-strata.md)).

## Consequences

**What this buys us.** Adding a handler is one annotation on a method on a
bean — nothing else. Existing Spring wiring patterns (`@Component`,
`@Bean`, `@Service`, hand-built configuration) all work unchanged. Multiple
handlers per bean class are natural. The discovery pass is centralized,
which means the customizer SPI ([ADR-0011](0011-customizer-spi-and-strata.md))
and the actuator inventory endpoint ([ADR-0017](0017-observability-stack.md))
both consume the same single source of truth.

**Costs.** Every bean's methods are reflected at startup; for very large
contexts that's a one-time cost in the tens of milliseconds, dwarfed by
Spring's own bean instantiation. Methodical is a hard dependency of
`mocapi-server`.

**Non-goals.** Mocapi does not support runtime registration of handlers
discovered after `@PostConstruct`. Dynamic registration would require
`listChanged: true` on the relevant capability and a notification path; both
are explicitly out of scope (see
[ADR-0018](0018-mcp-spec-features-not-implemented.md)).

**Code anchors:** `mocapi-api/.../tools/McpTool.java` (and sibling `McpPrompt`, `McpResource`, `McpResourceTemplate`, `McpToolParams` annotations); `mocapi-autoconfigure/.../MocapiServerToolsAutoConfiguration.java`.
