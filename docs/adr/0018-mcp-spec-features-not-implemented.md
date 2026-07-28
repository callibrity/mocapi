# ADR-0018 — MCP spec features mocapi deliberately does not implement

- **Status:** Superseded by [ADR-0022](0022-2026-07-28-features-not-implemented.md)
- **Date:** 2026-04-19

## Context

The MCP specification defines a number of features that mocapi
intentionally does not implement. Some are explicitly marked as
unstable in the spec; others add significant complexity for a feature
most use cases don't need; others are incompatible with mocapi's
stateful, multi-node deployment model.

A user evaluating mocapi needs a single place to look up "does mocapi
do X?" with the rationale, rather than discovering omissions by
reading the spec and noticing what isn't there. Conformance tooling
also needs a stable list of declared-not-supported features so test
assertions match what the framework actually claims to do.

This ADR is the canonical record for what mocapi declares not
supported and why.

## Decision

Mocapi declares the following MCP spec features unimplemented and
documents the rationale for each.

### Resource Subscriptions (`resources/subscribe`, `resources/unsubscribe`)

**Spec reference:** [Server / Resources](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)

Mocapi declares `resources` capability with `subscribe: false`. The
`resources/subscribe` and `resources/unsubscribe` methods are not
registered.

**Rationale:** Resource subscriptions require the server to push
notifications when resource content changes. This adds significant
complexity (change detection, subscriber tracking) for a feature
that most MCP use cases don't need. Resources are available for
listing and reading.

### URL-Mode Elicitation

**Spec reference:** [Client / Elicitation (URL Mode)](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)

Mocapi supports form-mode elicitation
(`elicitation/create` with `mode: "form"`). URL-mode elicitation
(`mode: "url"`) and the `URLElicitationRequiredError` (code -32042)
are not implemented.

**Rationale:** URL-mode elicitation is marked as a new feature in the
2025-11-25 spec with the caveat "its design and implementation may
change in future protocol revisions." It involves out-of-band browser
interactions and OAuth flows that are significantly more complex than
form-mode. Mocapi will revisit when the spec stabilizes.

### JSON-RPC Batching

**Spec reference:** [Transports / Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports/streamable-http)

The MCP spec explicitly states the POST body "MUST be a single
JSON-RPC request, notification, or response." JSON-RPC batching
(arrays of messages) is **prohibited** by the spec.

Note: the official TypeScript SDK supports batching despite the
spec prohibition. Mocapi follows the spec.

### Cancellation Processing (`notifications/cancelled`)

**Spec reference:** [Utilities / Cancellation](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/cancellation)

Mocapi accepts `notifications/cancelled` and logs it, but does not
attempt to interrupt in-flight tool execution. The spec says
receivers SHOULD stop processing but MAY ignore cancellation if "the
request cannot be cancelled." Since tool methods run on virtual
threads without cooperative cancellation, interrupting them safely is
not feasible without tool author cooperation.

The server does send `notifications/cancelled` to the client when a
`sendAndAwait` call (elicitation or sampling) times out.

### List Change Notifications

**Spec reference:** [Server / Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools), [Server / Resources](https://modelcontextprotocol.io/specification/2025-11-25/server/resources), [Server / Prompts](https://modelcontextprotocol.io/specification/2025-11-25/server/prompts)

Mocapi declares `listChanged: false` for tools, prompts, and
resources. The server does not send
`notifications/tools/list_changed`,
`notifications/resources/list_changed`, or
`notifications/prompts/list_changed`.

**Rationale:** Mocapi discovers tools, prompts, and resources at
application startup ([ADR-0010](0010-annotation-driven-handler-discovery.md)).
Dynamic registration at runtime is not currently supported. When it is,
`listChanged` will be enabled.

### Roots (`roots/list`, `notifications/roots/list_changed`)

**Spec reference:** [Client / Roots](https://modelcontextprotocol.io/specification/2025-11-25/client/roots)

Mocapi accepts the `notifications/roots/list_changed` notification
(logs and ignores it) but does not call `roots/list` or use root
information.

**Rationale:** Roots provide filesystem context hints to the server.
Mocapi's tool-oriented architecture does not currently use filesystem
context.

### Stateless / Serverless Mode

The TypeScript SDK supports a stateless mode (no session IDs, no
session store) for serverless deployments. Mocapi always creates
sessions and requires a session store. *(Superseded — see
[ADR-0020](0020-stateless-request-model.md): mocapi is now stateless,
creates no sessions, and requires no session store. This line reflects
the 2025-11-25-era design only and is kept as the frozen historical
record.)*

**Rationale:** Mocapi is designed for stateful, multi-node deployments
where sessions, elicitation, and sampling require durable state. A
stateless server variant may be added in the future.

## Consequences

**What this buys us.** A single, citable list of declared-not-supported
features. Conformance tooling can assert against this list and trust
it. Users evaluating mocapi can compare needs to omissions in one
read. Each omission has a stated reason that's reviewable when the
constraint changes.

**Costs.** Some legitimate use cases (file-system-aware servers
needing roots; subscription-driven UIs needing resource
subscriptions) require a different framework today. Each omission
is revisitable — the rationale is recorded so future revisions can
reopen the decision.

**Non-goals.** This ADR does not list every minor protocol feature;
it lists the ones we have explicitly evaluated and declined. Any
feature not mentioned here and not present in the code is a gap
that has not been formally decided either way — file an issue.

