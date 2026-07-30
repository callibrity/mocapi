# ADR-0022 — MCP 2026-07-28 features mocapi deliberately does not implement

- **Status:** Accepted — supersedes [ADR-0018](0018-mcp-spec-features-not-implemented.md)
- **Date:** 2026-06-11

## Context

[ADR-0018](0018-mcp-spec-features-not-implemented.md) was the canonical
list of MCP spec features mocapi declared not supported, written against
the 2025-11-25 revision. The clean break to 2026-07-28
([ADR-0019](0019-clean-break-2026-07-28.md)) changes the list in both
directions: the new revision deprecates features mocapi previously
implemented (Roots handling, Sampling, MCP Logging), removes mechanisms
some old entries referenced (`listChanged` capabilities, the stateful
session model), and introduces new optional surface
(`subscriptions/listen`, official extensions, custom parameter headers)
that needs an explicit stance.

As before: a user evaluating mocapi needs a single place to look up
"does mocapi do X?" with the rationale, and conformance tooling needs a
stable declared-not-supported list. This ADR is the canonical record
for the 2026-07-28 revision. Spec references point at the
[draft specification](https://modelcontextprotocol.io/specification/draft/changelog)
(release candidate locked 2026-05-21) and the SEPs that shaped it.

## Decision

Mocapi declares the following 2026-07-28 features unimplemented (or, in
one case, partially implemented) and documents the rationale for each.

### Roots, Sampling, and MCP Logging

**Spec reference:** SEP-2577 ([draft changelog](https://modelcontextprotocol.io/specification/draft/changelog))

Not implemented. SEP-2577 deprecates all three; the spec says new
implementations should not adopt them. `ctx.sample(...)` and
`ctx.log(...)` are removed from the mocapi API, and the server neither
reads root information nor emits `notifications/message`.

**Rationale:** a framework rewritten as if designed against 2026-07-28
from the start ([ADR-0019](0019-clean-break-2026-07-28.md)) would not
adopt features the same revision deprecates. The spec's suggested
migrations apply directly:

- **Roots** → pass directories/files via tool parameters, resource
  URIs, or server configuration.
- **Sampling** → integrate directly with LLM provider APIs.
- **Logging** → stderr on stdio, or OpenTelemetry — which `mocapi-otel`
  ([ADR-0017](0017-observability-stack.md)) already covers.

Note: `notifications/progress` is **not** deprecated and remains
supported, flowing on the request's own response stream.

### `subscriptions/listen`

**Spec reference:** [draft changelog](https://modelcontextprotocol.io/specification/draft/changelog)

Not implemented.

**Rationale:** mocapi discovers tools, prompts, and resources
statically at application startup
([ADR-0010](0010-annotation-driven-handler-discovery.md)), so it has no
list-change or resource-update notifications to push. This carries
forward ADR-0018's resource-subscription and `listChanged` rationale
into the new mechanism: change detection and subscriber tracking add
significant complexity for a capability the framework's static
discovery model cannot feed. If dynamic registration ever lands,
`subscriptions/listen` is the place this decision gets reopened.

### Tasks extension (`io.modelcontextprotocol/tasks`)

**Spec reference:** SEP-2663 ([draft changelog](https://modelcontextprotocol.io/specification/draft/changelog))

Not implemented. SEP-2663 moved tasks out of the core protocol into an
official extension; mocapi implements neither the extension nor any
task semantics.

**Rationale:** extensions are opt-in by design; mocapi starts from the
core protocol only. The task lifecycle (`tasks/get`, `tasks/update`,
`tasks/cancel`, server-directed task creation) is a substantial state
machine for long-running operations that mocapi's synchronous handler
model does not need yet. The extension's independent versioning means
it can be adopted later without a protocol bump.

### MCP Apps extension

**Spec reference:** SEP-1865 ([2026-07-28 release candidate announcement](https://blog.modelcontextprotocol.io/posts/2026-07-28-release-candidate/))

Not implemented.

**Rationale:** MCP Apps lets servers ship server-rendered HTML UIs
displayed in sandboxed iframes — a presentation-layer concern far
outside mocapi's tool/prompt/resource handler model, and an optional
extension besides. Nothing in the core protocol requires it, and no
mocapi use case has asked for it.

### URL-Mode Elicitation

**Spec reference:** [draft changelog](https://modelcontextprotocol.io/specification/draft/changelog)

Mocapi supports form-mode elicitation via MRTR replay
([ADR-0021](0021-mrtr-elicitation-replay.md)). URL-mode elicitation is
not implemented.

**Rationale:** carried forward from ADR-0018. URL mode involves
out-of-band browser interactions and OAuth flows that are significantly
more complex than form mode, and its design remains subject to change
across protocol revisions. Mocapi will revisit when it stabilizes.

### JSON-RPC Batching

**Spec reference:** [Transports / Streamable HTTP](https://modelcontextprotocol.io/specification/draft/basic/transports/streamable-http)

Not supported. The spec continues to require that a POST body be a
single JSON-RPC message; batching (arrays of messages) remains
**prohibited** by the spec. Carried forward from ADR-0018: mocapi
follows the spec.

### Cancellation Processing (partial)

**Spec reference:** [Transports / Streamable HTTP](https://modelcontextprotocol.io/specification/draft/basic/transports/streamable-http)

Partially implemented — this stance is recorded explicitly. The
2026-07-28 transport rules state that the client closing the SSE
response stream MUST be treated as cancellation of the in-flight
request. Mocapi honors the MUST: once the stream is closed, no further
messages are sent for that request (automatic on disconnect). Mocapi
does **not** interrupt the in-flight handler's execution.

**Rationale:** carried forward from ADR-0018. Handlers run on virtual
threads without cooperative cancellation
([ADR-0006](0006-virtual-thread-per-call.md)); interrupting them safely
is not feasible without handler-author cooperation. The "SHOULD stop
processing" half of the rule is best-effort: the work completes, but
its output goes nowhere.

**Ratified for 1.0.0 (2026-07-29).** Re-reviewed during the 1.0 release
sweep against the project's "adhere to SHOULD-level requirements by
default" posture, which this entry is a deliberate exception to. The
MUST half is honored unconditionally; the SHOULD half stays best-effort
for 1.0. Interrupting a handler mid-execution would mean
`Thread.interrupt()` on the per-call virtual thread, which is only
observable at interruptible blocking points — a handler doing CPU work,
a non-interruptible I/O call, or a `synchronized` section would ignore
it, while one holding a lock or mid-write to an external system could be
torn down at an unsafe point. Correct cancellation therefore requires a
handler-visible cancellation token that authors opt into and check;
that is a public API addition, not a 1.0 bug fix. The cost is bounded
and non-protocol-visible: the client already sees nothing after
disconnect, so the only consequence is wasted server-side work.
Revisitable post-1.0 by threading a cancellation signal through
`MrtrContext` ([ADR-0025](0025-progress-emitters-and-mrtr-context.md)),
which is the natural carrier now that it is the shared handler-context
base.

### Custom Parameter Headers (`x-mcp-header`)

**Spec reference:** SEP-2243 ([Transports / Streamable HTTP](https://modelcontextprotocol.io/specification/draft/basic/transports/streamable-http))

Mocapi designates no custom parameter headers — declined on principle in
[ADR-0028](0028-decline-sep-2243-custom-parameter-headers.md) (an HTTP-only
feature that would breach the transport-agnostic server contract). SEP-2243 makes
`x-mcp-header` support mandatory for clients but optional for servers —
a server MAY designate none, and mocapi designates none. Unrecognized
`Mcp-Param-*` headers on incoming requests are ignored, per RFC 9110's
treatment of unrecognized header fields.

**Possible future:** an `@McpTool` parameter annotation that designates
a tool parameter as header-supplied. Not currently planned.

### Extensions

**Spec reference:** [draft changelog](https://modelcontextprotocol.io/specification/draft/changelog)

The `extensions` capability map is advertised empty. No extensions are
implemented (see the Tasks and MCP Apps entries above for the two
official ones explicitly declined).

### 2026-07-28 authorization review (record of verification)

**Spec references:** SEP-2468 (RFC 9207 `iss` validation), SEP-837
(`application_type` at DCR), SEP-2352 (issuer-bound credentials),
SEP-2207 (refresh tokens), SEP-2350 (scope accumulation), SEP-2351
(`.well-known` suffix), and the RFC 7591 DCR deprecation in favor of
Client ID Metadata Documents.

Reviewed 2026-06-12 against the draft authorization spec. All seven
changes are client- or authorization-server-side; none impose new
resource-server MUSTs. Mocapi's resource-server obligations remain met
without code changes: bearer validation with mandatory audience
enforcement (`MocapiOAuth2Compliance`,
`AudienceCheckingOpaqueTokenIntrospector`) in both JWT and opaque-token
modes; RFC 9728 Protected Resource Metadata at
`/.well-known/oauth-protected-resource` with the standard field set
(shape unchanged in the draft); 401 on missing/invalid tokens; no
`offline_access` in `scopes_supported`.

**Deferred SHOULD-level enhancements** (no protocol impact): the
`scope` parameter on 401 `WWW-Authenticate` challenges, and 403
`insufficient_scope` challenges for step-up authorization flows.

## Consequences

**What this buys us.** A single, citable list of declared-not-supported
features for the 2026-07-28 revision. Conformance tooling can assert
against this list and trust it — conformance failures should map 1:1 to
entries here. Users evaluating mocapi can compare needs to omissions in
one read, and each omission has a stated reason that's reviewable when
the constraint changes.

**Costs.** Use cases needing tasks, MCP Apps, URL-mode elicitation, or
true mid-execution cancellation require a different framework today.
Each omission is revisitable — the rationale is recorded so future
revisions can reopen the decision.

**Non-goals.** This ADR does not list every minor protocol feature; it
lists the ones we have explicitly evaluated and declined. Any feature
not mentioned here and not present in the code is a gap that has not
been formally decided either way — file an issue.

This ADR supersedes
[ADR-0018](0018-mcp-spec-features-not-implemented.md), which recorded
the equivalent stances for the 2025-11-25 revision.

**Code anchors:** `mocapi-server/.../server/discover/DiscoverHandler.java`
(builds the `server/discover` result advertising supported capabilities)
and `mocapi-model/.../model/ServerCapabilities.java` (the advertised
capability set, whose `extensions` map is empty — no extension
implemented).
