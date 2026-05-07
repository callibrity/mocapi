# ADR-0014 — `mocapi-model` translated 1:1 from MCP `schema.ts`

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

The Model Context Protocol publishes its wire format as a TypeScript file
(`schema/2025-11-25/schema.ts`) that defines every JSON-RPC method's
request and response shape, every resource/tool/prompt descriptor, every
notification, every union type. That file is the spec; everything else
(server SDKs, client SDKs, conformance suites) is downstream.

A Java MCP framework needs a model module — a set of types covering every
shape on the wire. The choice is whether that model is:

1. A 1:1 translation of `schema.ts`, with the same names, the same union
   shapes, the same nullability, and the same JSON keys; or
2. An idiomatic Java rewrite that reorganizes for "Java taste" —
   collapsing unions, renaming fields, swapping naming conventions.

Option 2 looks attractive at first. Option 2 also drifts. Each MCP spec
revision becomes a translation exercise; each translation introduces
an opportunity to misread a `?` (optional) as required, to flatten a
sealed hierarchy that mattered, to rename a field that conformance tools
expect verbatim. Six months in, the model is its own thing, and bringing
it back in line costs more than just translating in the first place.

The model module also sits at the bottom of the dependency graph: every
other mocapi module depends on it. Pulling Spring, Jackson Databind
modules, validation libraries, or anything else heavy into model would
force every consumer to inherit those dependencies.

## Decision

`mocapi-model` is a 1:1 translation of MCP `schema.ts`. Same type names,
same field names, same nullability, same union structure. Jackson
annotations are the only annotations on the types; Jackson Databind is
the only non-JDK dependency.

**Rules:**

- Every `schema.ts` `interface` becomes a Java `record` (immutable, value
  semantics, equals/hashCode for free). Field names match the
  TypeScript names with no Java-style renaming.
- Every `schema.ts` discriminated union becomes a Java `sealed interface`
  with one `record` permitted variant per union arm. The sealed
  hierarchy mirrors `schema.ts` exactly, including intermediate
  unions. For example, the spec defines:
  ```typescript
  type PrimitiveSchemaDefinition = StringSchema | NumberSchema | BooleanSchema | EnumSchema;
  type EnumSchema = SingleSelectEnumSchema | MultiSelectEnumSchema | LegacyTitledEnumSchema;
  ```
  and `mocapi-model` carries the same two-level sealed hierarchy. Flattening
  the union on the Java side is forbidden — exhaustive `switch`
  expressions on `PrimitiveSchemaDefinition` and on `EnumSchema` both
  need to compile.
- **Enum constants are uppercase Java idiom**
  (`Role.USER`, `Role.ASSISTANT`); JSON serialization uses the
  spec's lowercase form via `@JsonValue` on a per-enum `value()`
  accessor. The spec dictates the wire form; Java idiom dictates the
  source form.
- Optional fields use Java's `Optional<T>` for primitives where the spec
  marks them optional; nullable JSON fields tolerate missing input via
  Jackson's standard handling.
- Backward-compatibility variants the spec keeps (notably
  `LegacyTitledEnumSchema`) are kept here too, marked
  `@Deprecated`. Code that instantiates them or tests that exercise
  them carries a narrow `@SuppressWarnings("deprecation")` with a
  comment naming the spec section that requires the deprecated form.

**Dependencies:** `mocapi-model` depends only on `jackson-databind`
(and the JDK). No Spring, no validation, no schema generators, no other
mocapi modules.

## Consequences

**What this buys us.** Every MCP spec revision is a mechanical
translation — open `schema.ts`, open `mocapi-model`, diff. Conformance
suites that send canonical request payloads round-trip through the model
without name mapping. The sealed hierarchies give exhaustive `switch`
in user code (e.g., handling each `PrimitiveSchemaDefinition` variant)
with compile-time completeness checking. Consumers — `mocapi-server`,
`mocapi-streamable-http-transport`, conformance tooling, third-party
clients — depend on a small Jackson-only jar.

**Costs.** The model is not "Java-idiomatic" by some definitions —
field names follow JSON conventions, sealed hierarchies have more
levels than a Java-first design would. We accept the small ergonomic
cost for the spec-fidelity payoff. Spec revisions that change a field
name break every consumer; that is also the point — the framework
fails loudly when the wire format changes, which is what we want.

**Non-goals.** `mocapi-model` does not validate, generate schemas,
serialize over the wire, or know about transports. It is the type
layer. Anything richer (constrained builders for elicitation schemas,
tool input-schema generation) lives in higher modules — see
[ADR-0015](0015-constrained-elicitation-schema-builder.md) and
[ADR-0016](0016-victools-tool-schema-generation.md).

**Code anchor:** `mocapi-model/` (every record type maps 1:1 to a `schema.ts` shape). `LegacyTitledEnumSchema` carries `(since = "0.0.1", forRemoval = false)`.
