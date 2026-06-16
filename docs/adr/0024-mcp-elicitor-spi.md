# ADR-0024 — `McpElicitor`: elicitation from prompt and resource handlers

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

The MRTR replay engine ([ADR-0021](0021-mrtr-elicitation-replay.md))
deliberately wraps all three MRTR-capable RPC seams — `tools/call`,
`prompts/get`, and `resources/read` — because the 2026-07-28 spec
permits any of them to return `InputRequiredResult`. But the only
user-facing way to *trigger* an elicitation was
`McpToolContext.elicit(...)`, and `McpToolContext` is bound only
during tool dispatch. Prompt and resource handlers had engine support
with no API: the official conformance suite's
`input-required-result-non-tool-request` scenario (a prompt that
elicits) was unimplementable and sat in the expected-failures
baseline.

Adding per-kind context types (`McpPromptContext`,
`McpResourceContext`) would mint two new interfaces whose only member
is `elicit`, and would leave three near-identical APIs to keep in
sync.

## Decision

Introduce one new user-facing interface,
`com.callibrity.mocapi.api.elicitation.McpElicitor`, owning the
elicitation surface:

```java
public interface McpElicitor {
    ScopedValue<McpElicitor> CURRENT = ScopedValue.newInstance();
    ElicitResult elicit(ElicitRequestFormParams params);
    default ElicitResult elicit(String message, Consumer<RequestedSchemaBuilder> schema) { … }
}
```

**Rules:**

1. `McpToolContext extends McpElicitor`. Tool code is unchanged —
   `ctx.elicit(...)` is now inherited.
2. The server binds `McpElicitor.CURRENT` during dispatch of all
   three MRTR-capable methods: tools bind their `McpToolContext`
   (which is an `McpElicitor`); prompts and resources bind a
   `DefaultMcpElicitor` (capability pre-check + the
   `ElicitationDispatcher` seam, identical semantics to the tool
   path: absent capability → `McpElicitationNotSupportedException`
   → `-32003` on the wire).
3. Prompt, resource, and tool handler methods may declare an
   `McpElicitor` parameter; a structural `McpElicitorResolver`
   (the `ScopedValueResolver` pattern) resolves it. The flat-schema
   `RequestedSchemaBuilder` (ADR-0015) is the shared schema surface.
4. The replay semantics and idempotency contract of ADR-0021 apply
   unchanged to prompts and resources: code before the handler's
   last `elicit(...)` re-executes once per round trip.

**Code anchors:** `mocapi-api/.../elicitation/McpElicitor.java`,
`mocapi-server/.../elicitation/DefaultMcpElicitor.java`,
`mocapi-server/.../elicitation/McpElicitorResolver.java`,
`McpPromptsService.java`, `McpResourcesService.java`.

## Consequences

**What this buys us.** Prompts and resources gain the elicitation
capability the engine already supported, through one interface
instead of three. The conformance suite's
`input-required-result-non-tool-request` scenario moves out of the
expected-failures baseline. Tool authors see no change; the API
surface grows by exactly one interface.

**Costs.** A second way to reach `elicit` from tools (an
`McpElicitor` parameter resolves there too) — harmless, but two
spellings for the same thing. The idempotency contract now applies
to prompt/resource authors, who must read the interactive-tools
guide's warnings.

**Non-goals.** No sampling/roots equivalents (deprecated features,
[ADR-0022](0022-2026-07-28-features-not-implemented.md)); no
URL-mode elicitation.

> **Update ([ADR-0025](0025-progress-emitters-and-mrtr-context.md),
> 2026-06-16):** the original non-goals also declined a progress API
> for prompts/resources and per-kind context types. ADR-0025 reverses
> both: it adds typed progress emitters, gives prompts and resources a
> progress surface, and introduces `MrtrContext extends McpElicitor,
> McpProgressSource` with `McpToolContext` / `McpPromptContext` /
> `McpResourceContext` as leaves. The elicitation decision above stands
> unchanged; `McpElicitor` simply becomes a super-interface of
> `MrtrContext`.
