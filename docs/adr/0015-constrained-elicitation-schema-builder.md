# ADR-0015 — Constrained elicitation schema builder; no escape hatch

- **Status:** Accepted
- **Date:** 2026-04-13

## Context

MCP elicitation lets a server pause an in-flight tool call to ask the
client for additional input. The protocol constrains the elicitation
schema severely: it must be a flat object whose properties are
primitives (string, integer, number, boolean) or enum-typed select /
multi-select. No nested objects. No arbitrary JSON Schema. The
constraint is in the spec for a reason — clients render elicitation as
forms, and forms can't usefully render arbitrary nesting.

A naive Java API for elicitation would accept "any JSON Schema" — a
`JsonNode` or a Jackson tree — and let the user assemble whatever they
want. That always produces the same bug: a tool author writes a nested
schema, the client rejects it (or worse, the spec-conformant client
silently flattens or errors), and the failure surfaces deep into
testing.

A second tempting shape is bean-mode elicitation — the user passes a
Java type (`MyForm.class` or a `TypeReference<MyForm>`), the framework
generates a schema from the bean, sends elicitation, and deserializes
the response back into the bean. That's convenient until you remember:
the spec restricts the schema. Bean-mode generation has to either
faithfully reproduce that restriction (in which case the bean has to be
flat, with carefully chosen field types — at which point a builder is
clearer) or generate over-rich schemas the protocol forbids.

Sampling — the dual of elicitation, where the server asks the client to
generate text — wants typed responses too, and the same constrained
schema shape covers it.

## Decision

Mocapi ships a single elicitation entry point: a constrained,
type-safe builder that mirrors MCP's flat-object schema restriction
exactly. There is no escape hatch.

The entry point on `McpStreamContext`:

```java
ElicitationResult elicit(String message, Consumer<ElicitationSchemaBuilder> schema);
```

`ElicitationSchemaBuilder` exposes only methods that produce
spec-compliant schemas:

- **Primitives:** `string`, `integer`, `number`, `bool` (each with and
  without a default value).
- **Single-select enums:**
  `choose(name, Class<? extends Enum<?>> enumType[, defaultValue])`. The
  enum constants generate the wire-level `enum` array;
  `Enum.values()` ordinal order; `toString()` for display names. If
  `toString()` differs from `name()`, generates a titled enum schema
  via `oneOf` with `const` / `title`.
- **Multi-select enums:** `chooseMany(...)` — the same enum-to-schema
  logic wrapped in `"type": "array", "items": {...}`.
- **Required:** `required(String... names)`.

The builder produces the appropriate sealed-variant schema record from
`mocapi-model` ([ADR-0014](0014-mocapi-model-from-schema-ts.md)) — there
is no untyped `JsonNode` path through the builder.

**Bean-mode elicitation is removed.** The two earlier overloads
(`elicitForm(String, Class<T>)` and `elicitForm(String, TypeReference<T>)`)
are deleted, along with the bean-result type and the schema generation
that backed them. The constrained builder covers every elicitation use
case the spec supports without dragging schema generation into the
elicitation path.

**Sampling reuses the same builder** for typed sampling responses. One
schema-shape vocabulary spans both server-to-client request kinds.

## Consequences

**What this buys us.** A tool author cannot accidentally produce a
non-spec-compliant elicitation schema — the type system rejects it at
compile time. Test surface shrinks: there's exactly one elicitation API
to test, not three. The elicitation path doesn't depend on a JSON
Schema generator, which keeps the dependency graph of `mocapi-server`
slimmer ([ADR-0016](0016-victools-tool-schema-generation.md) confines
schema generation to the tool path, where the spec is permissive
enough to need it). Sampling and elicitation share one mental model.

**Costs.** Tool authors with a complex form previously expressible as a
nested bean must redesign for the flat-property constraint. That
constraint is in the spec — we'd have to enforce it eventually anyway
— but it is a real ergonomic step relative to "pass me a class with
fields."

**Non-goals.** No `elicit(String, JsonNode customSchema)` overload. No
`elicit(String, Class<?>)` bean overload. No "advanced builder" with
arbitrary-JSON-Schema methods. If a future MCP spec revision relaxes
the elicitation schema constraints, the builder grows to match — at
that point, and not before.

**Code anchors:** `mocapi-server/.../elicitation/RequestedSchemaBuilder.java`. Landed in commit `842be533` (2026-04-13).
