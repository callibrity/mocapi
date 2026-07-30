# ADR-0030 — Align observability with the OpenTelemetry MCP semantic conventions

- **Status:** Accepted
- **Date:** 2026-07-30

## Context

[ADR-0017](0017-observability-stack.md) established the Micrometer
observation stack and stated that GenAI / MCP attributes are populated
"for cross-tool consistency". A 1.0 review against the actual
conventions found that claim overstated, and found the conventions had
moved.

**The conventions say "instead of", explicitly.** The MCP semconv
document ([`docs/gen-ai/mcp.md`](https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/))
resolves the layering question in prose: "When instrumenting MCP calls,
it's RECOMMENDED to follow MCP conventions *instead of* RPC semantic
conventions since MCP spans and metrics provide domain-specific context
and record details that are not covered by the RPC conventions." The
JSON-RPC semconv does define client/server spans (as specializations of
the generic RPC span types in `model/rpc/spans.yaml` — an earlier draft
of this ADR wrongly said no JSON-RPC span exists); those conventions
govern plain JSON-RPC calls, which is exactly what ripcurl's default
still emits for non-MCP services. For MCP calls, the MCP span *is* the
one span representing the RPC — it carries `jsonrpc.request.id`,
`jsonrpc.protocol.version`, and `rpc.response.status_code` in its own
attribute list. Same relationship as gRPC's conventions to the generic
RPC ones: the most specific convention owns the span.

**The conventions relocated.** `mcp.method.name`, `mcp.protocol.version`,
`mcp.resource.uri`, and `mcp.session.id` are now marked *Deprecated* in
`open-telemetry/semantic-conventions` and live in
`open-telemetry/semantic-conventions-genai` (`model/mcp/`). That
repository is the authority for this ADR. Every MCP attribute is
`development` stability — this is a moving target, and the alignment
here is to a snapshot, not a frozen contract.

**What mocapi already had right.** The three registry attributes we emit
(`mcp.method.name`, `mcp.protocol.version`, `mcp.resource.uri`) are
correct, as are `gen_ai.operation.name=execute_tool`,
`gen_ai.tool.name`, and `gen_ai.prompt.name`. `mcp.session.id` is
correctly absent — mocapi is stateless ([ADR-0020](0020-stateless-request-model.md)).

**What ripcurl already had right.** The `jsonrpc.server` observation from
`ripcurl-o11y` emits `jsonrpc.request.id`, `jsonrpc.protocol.version`,
`rpc.system.name`, `rpc.response.status_code`, and `error.type` as the
JSON-RPC error code string. Each of those was checked against the current
`model/jsonrpc/registry.yaml` and `model/rpc/registry.yaml` during this
review and all five are current — `rpc.response.status_code` is even
`release_candidate`, a firmer stability tier than any MCP attribute.
MCP semconv inherits the JSON-RPC conventions for exactly these, so that
half needed no work.

Consequently **ripcurl is not modified and no ripcurl release is
required**. This was checked rather than assumed, because the option of
changing ripcurl was on the table; recording it here so the question
isn't reopened without new evidence.

**What was wrong.** The observation name `mcp.handler.execution` yields
the metric `mcp.handler.execution.duration`, where semconv defines
`mcp.server.operation.duration`. The span name was the bare target name
(`echo`) rather than the prescribed `{mcp.method.name} {target}`
(`tools/call echo`). `error.type` on mocapi's own observation was
`e.getClass().getSimpleName()` rather than the JSON-RPC error code, and
nothing emitted `tool_error` for a `CallToolResult` with `isError=true`
as the conventions require. `network.transport` was absent.
`mcp.client.name` is not a convention at all — it has no counterpart in
the registry.

## Decision

Emit the semconv MCP server operation from a `MethodInterceptor`
registered at the **JSON-RPC method level**, through ripcurl's
`JsonRpcMethodHandlerCustomizer` SPI — not from a server decorator, not
from new convention machinery in ripcurl, and not from mocapi's
per-handler OBSERVATION stratum.

The placement is load-bearing, because mocapi has two distinct invoker
chains and they bracket different intervals:

- **ripcurl's chain** wraps each `@JsonRpcMethod` handler — `tools/call`
  dispatching into `McpToolsService.callTool`. This brackets the whole
  MCP method: envelope handling, validation, guards, the handler, and
  result serialization. It is the interval the conventions mean by
  "received until the result or ack is sent".
- **mocapi's chain** (`MutableHandlerState`: correlation, observation,
  audit, validation, invocation) wraps the *user's* `@McpTool` method
  only. It is strictly narrower.

`mcp.server.operation.duration` must measure the former. Attaching the
compliant observation to mocapi's OBSERVATION stratum would silently
under-report it by excluding everything the framework does around the
user handler — a metric that is wrong by construction, which defeats the
purpose of adopting the convention at all.

The existing `mcp.handler.execution` observation stays where it is, on
mocapi's chain, as a deliberate non-standard child. It measures user
handler time in isolation, which is genuinely useful for separating
application cost from framework overhead, and it is documented as a
mocapi extension rather than as a convention. It is slimmed to handler
kind + target name: the semconv attributes it used to carry move to the
server span, where the conventions place them.

Two consolidations follow from the one-span model:

- **ripcurl's `jsonrpc.server` observation backs off** — not by
  suppression, but through a seam added to ripcurl for the purpose
  (2.12.0): its observation customizer is now the named, replaceable
  `JsonRpcObservationCustomizer` bean, registered
  `@ConditionalOnMissingBean`. mocapi's `McpServerOperationCustomizer`
  implements that interface, so ripcurl's default backs off exactly the
  way any Spring Boot default bean does when the application provides its
  own. ripcurl is not "wrong" to emit a JSON-RPC observation standing
  alone; the conventions simply put the JSON-RPC attributes *on* the MCP
  server span when a more specific protocol layers over it — one span,
  owned by the most specific convention (the same layering rule that has
  Spring MVC emit `http.server` with no separate servlet span beneath
  it). Nothing is lost: the server-operation observation emits every
  attribute ripcurl's did, with identical semantics. mocapi's o11y
  autoconfiguration is ordered `beforeName` ripcurl's so the back-off
  condition sees the bean; a context-runner test pins that ordering,
  because the failure mode of getting it wrong is silent
  double-observation.
- **Remote trace-parenting moves to the server span.** The
  `McpRequestReceiverContext` (W3C extraction from `_meta`, `Kind.SERVER`)
  previously hung off the inner handler observation, which produced an
  odd topology: the client's trace linked to the handler span while the
  outer span parented to the local HTTP span. The server-operation
  observation now owns the receiver context, so the client trace links to
  the span the conventions say should carry it — and span kind `SERVER`
  comes with it, closing what ripcurl had documented as its own gap. The
  handler child uses a plain context and parents locally.

Concretely:

1. **Observation name** is `mcp.server.operation`, producing the
   prescribed `mcp.server.operation.duration` histogram. The JSON-RPC
   method name is closed over at customizer time, so the hot path does no
   reflection — the same shape ripcurl uses for its own interceptor.
2. **Span name** becomes `{mcp.method.name} {target}`, where target is
   `gen_ai.tool.name` or `gen_ai.prompt.name`. Where no low-cardinality
   target exists, the span name is `{mcp.method.name}` alone. The
   resource URI is **not** used as a target by default — the conventions
   warn it produces high-cardinality span names.
3. **`mcp.method.name`** is emitted as a low-cardinality attribute. It is
   `required` for this span and metric, and was previously only on the
   outer observation.
4. **`error.type`** is the string form of the JSON-RPC error code,
   obtained through ripcurl's `JsonRpcExceptionTranslatorRegistry` so it
   always matches the code the client actually receives; and
   `tool_error` when a tool returns `CallToolResult.isError=true`, which
   the conventions call out specifically. `rpc.response.status_code`
   carries the same code on failure.
5. **`network.transport`** is `tcp` for Streamable HTTP and `pipe` for
   stdio, resolved by the autoconfiguration from the transport module on
   the classpath rather than sniffed per call.
6. **`mcp.client.name` is dropped.** It is not in the registry and has no
   standard counterpart. Client identity remains available through MDC
   for logging; it does not belong on a span claiming semconv compliance.

`mcp.handler.kind` is kept as a deliberate mocapi extension — it has no
registry counterpart but is useful for filtering, and custom attributes
alongside the standard set do not breach compliance.

Not adopted, and recorded so the gap is a decision rather than an
oversight: `client.address` / `client.port` (recommended "if applicable";
they require servlet-level access the observation stratum does not have),
`gen_ai.tool.call.arguments` / `gen_ai.tool.call.result` /
`gen_ai.prompt.variable` (all `opt_in`, and all capable of carrying
sensitive payloads), `network.protocol.version` (per-request servlet
information unavailable at this stratum), and
`mcp.client.session.duration` / `mcp.server.session.duration` (sessions
do not exist in a stateless server). One partial adoption: on
`tool_error` the attribute is set but span status stays unset — there is
no throwable to record on that path, and the attribute is the queryable
signal the conventions emphasize. And one looseness accepted knowingly:
the registry's `mcp.method.name` enum predates the 2026-07-28 revision
(it still lists `initialize`, roots, and sampling methods), so values
like `server/discover` are emitted as legal open-enum extensions.

## Consequences

**What this buys us.** Telemetry that off-the-shelf OTel tooling
recognizes without per-deployment mapping: the metric has the name
dashboards expect, spans read as `tools/call echo`, and failures carry
the error taxonomy the conventions define, including the tool-level
`tool_error` distinction that a generic exception name cannot express.

**Costs.** Metric and span names change, which breaks any existing
dashboard or alert built on `mcp.handler.execution.duration`. This is
deliberately taken pre-1.0, while mocapi has no install base to disrupt —
the same change after 1.0 would be a breaking telemetry migration. One
attribute (`mcp.client.name`) is removed outright.

**Stability risk, stated plainly.** Every MCP attribute in the registry
is `development` stability and the conventions were relocated to a new
repository recently enough that the old location still carries the
deprecated copies. Alignment will need revisiting; this ADR pins the
snapshot it was written against so a future reviewer can diff rather than
guess.

**Non-goals.** This does not change the audit or MDC subsystems, the
interceptor strata order, or ripcurl. It does not add opt-in payload
attributes, and it does not introduce session metrics that a stateless
server cannot produce.

**Code anchors:**
`mocapi-o11y/src/main/java/com/callibrity/mocapi/o11y/McpServerOperationInterceptor.java`
(the compliant `mcp.server.operation` observation, registered via ripcurl's
`JsonRpcMethodHandlerCustomizer`; carries the `McpRequestReceiverContext`, so
remote trace-parenting and `SERVER` span kind live on the server span),
`mocapi-o11y/src/main/java/com/callibrity/mocapi/o11y/McpServerOperationCustomizer.java`
(implements ripcurl's `JsonRpcObservationCustomizer` seam, displacing the
default `jsonrpc.server` observation),
`ripcurl-o11y`'s `JsonRpcObservationCustomizer` /
`DefaultJsonRpcObservationCustomizer` (ripcurl ≥ 2.12.0 — the replaceable
observation-owner seam),
`mocapi-o11y/src/main/java/com/callibrity/mocapi/o11y/McpHandlerObservationInterceptor.java`
(the narrower, non-standard `mcp.handler.execution` child — kind + target
only), and
`mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/o11y/MocapiO11yAutoConfiguration.java`
(customizer registration, autoconfig ordering, and `network.transport`
resolution). `McpObservationFilter` is deleted — its two convention
attributes moved onto the server-operation observation, and
`mcp.client.name` is dropped (client identity stays in MDC via
`mocapi-logging`).

**Dependency note:** this decision raises mocapi's ripcurl floor to
2.12.0 (the release that introduces the observation-owner seam). ripcurl
2.12.0 must be released to Maven Central before mocapi 1.0.0 is tagged —
Central rejects SNAPSHOT dependencies.
