# ADR-0033 — MCP Apps module and the `io.modelcontextprotocol/ui` capability

- **Status:** Accepted
- **Date:** 2026-07-31

## Context

MCP Apps (SEP-1865, `modelcontextprotocol/ext-apps`) was declared
unimplemented at the 2026-07-28 revision boundary
([ADR-0022](0022-2026-07-28-features-not-implemented.md)), on the
grounds that it looked like a presentation-layer concern — sandboxed
iframes, a `postMessage` JS bridge — far outside mocapi's tool/prompt/
resource handler model. Revisiting the spec shows that assessment was
too broad: the extension splits cleanly into a **server** surface
(declarative metadata — serve `ui://` HTML resources, link tools to
their UI via `_meta`, declare the capability) and a **host/in-iframe
JavaScript** surface (the sandbox handshake, `ui/initialize`,
`postMessage` JSON-RPC, display-mode negotiation, app-registered
tools). Only the second half is out of mocapi's reach; the first half
is a shallow, additive, server-side extension that fits the existing
annotation-driven handler model exactly.

Two foundation seams landed ahead of this decision on
`feat/extension-foundation-seams`: meta-annotation-aware handler
discovery ([ADR-0032](0032-meta-annotation-aware-handler-discovery.md))
lets a composed annotation register a handler without a bespoke
registration SPI, and `ServerCapabilitiesCustomizer`
([ADR-0031](0031-server-capabilities-customizer.md)) lets an optional
module declare an `extensions` capability entry without core
enumerating it. Both were built with Apps (and Tasks) as the driving
use case, and the `@McpAppResource` pattern they enable is proven by a
passing end-to-end test, not merely assumed. What remains is deciding
whether to build the module at all, and on what surface.

## Decision

Introduce an optional module, `mocapi-apps`, that implements the MCP
Apps **server** surface only, stopping at the language boundary
described above.

- **`ui://` resources.** Authors declare an HTML resource with
  `@McpAppResource` (meta-annotated `@McpResource`, mimeType defaulted
  to `text/html;profile=mcp-app`, `uri` aliased through via
  `@AliasFor`). It is served through the existing
  `resources/list`/`resources/read` path — no new handler kind.
- **Tool↔UI linkage.** A companion `@McpUi(resourceUri, visibility)`
  annotation on an `@McpTool` method links the tool to its UI resource.
  `visibility` (`model`/`app`) is emitted as metadata only; mocapi does
  not enforce it (host-side concern, distinct from the auth-Guard
  `visibility ≡ invocation` model of [ADR-0012](0012-guard-spi.md)).
- **Capability declaration.** `UiCapabilityCustomizer` (a
  `ServerCapabilitiesCustomizer`) unconditionally declares
  `capabilities.extensions["io.modelcontextprotocol/ui"] = {
  "mimeTypes": ["text/html;profile=mcp-app"] }` whenever `mocapi-apps`
  is on the classpath. mocapi is stateless and has no `initialize`
  handshake to gate on, so the capability and `_meta.ui` are always
  emitted; a non-Apps host simply ignores metadata it doesn't
  recognize (the spec's text-only fallback).
- **Out of scope, explicitly.** The `postMessage` JSON-RPC protocol,
  `ui/initialize`, `ui/notifications/sandbox-proxy-ready` /
  `sandbox-resource-ready`, display-mode negotiation, app-registered
  tools, and `sampling/createMessage` over `postMessage` are host/
  in-iframe JavaScript. mocapi serves the author's HTML bytes
  content-agnostically; when the in-iframe app calls a server tool, it
  arrives at mocapi as an ordinary `tools/call` with no Apps-specific
  server code involved.

## Consequences

Authors get two ergonomic annotations, `@McpAppResource` and `@McpUi`,
and nothing else changes about how they write tools or resources — the
per-call data the UI renders rides the normal `CallToolResult`. Adding
`mocapi-apps` to the classpath is the only integration step; omitting
it leaves the core inert (no descriptor gains `_meta`, discovery
behaves identically for direct annotations). A non-Apps host is
unaffected: it sees an unrecognized `extensions` entry and unrecognized
`_meta.ui` fields, both of which the spec requires it to ignore.

Cost: mocapi now ships and maintains a module whose HTML-serving
surface it cannot validate beyond "well-formed resource" — CSP/sandbox
correctness and rendering are entirely host- and author-responsibility.
This ADR does not attempt to police that.

Non-goals: no `postMessage`/iframe/JS-bridge code of any kind; no
call-time `UiContext` (the tool↔UI link is static descriptor metadata,
resolved before any invocation — see
[ADR-0034](0034-descriptor-meta-and-customizer-seams.md) for the
mechanism); no app-registered tools or display-mode negotiation. If a
real use case for a per-response `_meta.ui` override appears (the
draft-only dynamic case), it is a follow-up, not an extension of this
decision.

This ADR flips the MCP Apps entry in
[ADR-0022](0022-2026-07-28-features-not-implemented.md) from declined
to accepted-and-implemented.

**Code anchors:**

- `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/UiCapabilityCustomizer.java`
- `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/McpAppResource.java`
- `mocapi-apps/src/main/java/com/callibrity/mocapi/apps/McpUi.java`
