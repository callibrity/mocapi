# ADR-0016 — Victools `jsonschema-generator` for tool input/output schemas

- **Status:** Accepted
- **Date:** 2025-07-06

## Context

A tool's `tools/list` entry includes an `inputSchema` (and optionally an
`outputSchema`) that describes the JSON shape of the call's `arguments`
object. Clients — including LLM clients — read that schema to decide
how to construct calls; the schema is not optional dressing.

Mocapi discovers tool methods reflectively
([ADR-0010](0010-annotation-driven-handler-discovery.md)). The natural
source of truth for the input schema is the method signature itself: the
parameter types, names, generic type arguments, and any annotations
already carry enough information to generate a JSON Schema. Hand-written
schemas alongside Java method signatures double the maintenance burden
and drift the moment a parameter is added or renamed.

Unlike elicitation ([ADR-0015](0015-constrained-elicitation-schema-builder.md)),
the tool input-schema vocabulary in MCP is the full JSON Schema draft —
nesting, references, polymorphism, all of it. Building a schema generator
from scratch to cover that surface is unjustifiable when a mature
library exists.

The Victools `jsonschema-generator` library is the standard Java
solution: it walks types, supports records and Jackson annotations
out of the box, has well-defined extension points (modules, custom
definition providers), and produces draft-2020-12 output (matching
what MCP clients expect).

## Decision

Tool input and output schemas are generated at startup from the method
signature by the Victools `jsonschema-generator` library
(`com.github.victools:jsonschema-generator`).

**Generation runs once per tool at handler-build time** during
`@PostConstruct` — alongside annotation discovery and customizer
attachment. The resulting schema is closed over by the handler; the
hot path does not invoke the generator.

**Record components are required-by-default.** When a tool parameter
(or an `@McpToolParams` record's component) is a non-nullable record
field, the generated schema marks it `required` without the user having
to opt in via Jackson `@JsonProperty(required = true)`. Java records
have non-null component semantics by construction; the schema reflects
that. Nullable components (`Optional<T>` or types annotated to indicate
nullability) remain non-required.

**Configurable via the customizer SPI.** Users who need to override
generator behavior — additional modules, custom definition providers,
schema-version override — register a `SchemaGeneratorConfigBuilder`
customization through the existing customizer pattern; the autoconfig
exposes the builder under `@ConditionalOnMissingBean` so a user `@Bean`
replaces the default.

**Output schemas are opt-in.** Tools with a non-trivial return shape
can register an `outputSchema` for clients that validate responses;
the framework generates it from the return type the same way. Output
schema validation at runtime is also opt-in (it has a real cost, and
not every deployment wants to validate the server's own outputs).

**Structured output may be any JSON value (MCP 2026-07-28).** The spec
widened `structuredContent` from a JSON object to any JSON value
(object, array, string, number, boolean, or null), so any tool return
type that is not `void`, `CallToolResult`, or a `CharSequence` is mapped
to `structuredContent` of whatever shape it serializes to — a record or
`Map` becomes an object, a `List`/array becomes an array, a primitive
becomes a scalar. The derived schema is advertised as the `outputSchema`
when it carries a concrete `type`; an untyped empty schema (e.g. raw
`Object`) is mapped structurally but advertises no schema. `Optional<T>`
is the one rejected case: its element type is erased on the return
signature, so no schema can be derived — return the value directly or a
`CallToolResult`. (Earlier mocapi enforced the 2025-11-25 object-only
rule; this relaxation finished a migration the model already reflected,
`structuredContent` being typed `JsonNode`.)

## Consequences

**What this buys us.** Adding a tool parameter automatically updates
the wire schema — no hand-edited JSON Schema sitting next to a Java
signature drifting out of sync. Records get sensible required-field
semantics for free. Generator behavior is overridable for the rare
deployment that needs custom type mappings (e.g., a project-specific
`Money` type that should serialize as a string with a known pattern).
Schema generation cost is paid once at startup, not per call.

**Costs.** Victools is a transitive dependency of `mocapi-server`. The
generator's default behavior — particularly around polymorphism and
generic type arguments — sometimes needs nudging via custom modules
for advanced types; users with exotic signatures may need to write a
small customizer. Schema generation runs at startup for every
discovered tool, adding tens to low-hundreds of milliseconds for tool-
heavy applications (still small relative to the rest of Spring Boot
startup).

**Non-goals.** Mocapi does not auto-generate schemas for prompts or
resources — prompts use argument descriptors, resources use URI
templates with separate variable metadata. The generator is scoped to
the tool path, where the JSON-Schema-shaped `inputSchema` /
`outputSchema` fields live. Elicitation does not use Victools at all
([ADR-0015](0015-constrained-elicitation-schema-builder.md)).

**Code anchors:** `mocapi-server/.../tools/DefaultMethodSchemaGenerator.java`;
return-type classification and schema advertisement in
`mocapi-server/.../tools/CallToolHandlers.java` (`createResultMapper`);
structured mapping in `mocapi-server/.../tools/StructuredResultMapper.java`.
Required-by-default for record components landed in commit `fe420b43` and
shipped in 0.17.0.
