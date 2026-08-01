# MCP Apps Dev Host — Design

- **Date:** 2026-08-01
- **Status:** Approved (brainstorm)

## Context

mocapi implements the MCP Apps server surface (ADR-0033…0036): it serves
`ui://` HTML resources and links tools to them. But mocapi speaks **MCP
2026-07-28** (`server/discover`, the `_meta` envelope, the
`MCP-Protocol-Version`/`Mcp-Method`/`Mcp-Name` headers), and **no shipping
host does** — Claude Desktop and the official `ext-apps/examples/basic-host`
both speak the `initialize`/2025-11-25 transport. So mocapi's Apps output can
be verified on the wire (we did, via httpie) but has never been rendered
end-to-end.

This project is a **minimal dev host** that closes that gap: point it at a
mocapi server, see its UI-bearing tools, click one, and watch the linked
`ui://` app render in a sandboxed iframe and round-trip a tool call. It exists
to demo mocapi Apps and to give mocapi Apps authors a way to test their UIs
locally.

## Scope

**In scope (v1):**

- Connect to a mocapi server by URL; run `server/discover` and confirm the
  `io.modelcontextprotocol/ui` capability.
- `tools/list`, filtered to tools that carry `_meta.ui.resourceUri`
  (SDK helper `getToolUiResourceUri`), rendered as **clickable cards**.
- Click a card → `tools/call` the tool → `resources/read` its linked `ui://`
  bundle → render it in a sandboxed iframe.
- The rendered app talks to the host over the ext-apps postMessage bridge:
  the host delivers the initiating tool result, and proxies the app's
  `callServerTool` back to the server as a `tools/call`.

**Non-goals (v1):** `sendMessage` / `sendLog` / `openLink`, display-mode
negotiation, host-context/safe-area insets, OAuth2/authenticated servers,
persisting connections, and rendering non-UI tools. These are additive later
if wanted; leaving them out keeps the host a focused demo.

## Location & tech

- Lives at **`examples/dev-host/`** in the mocapi repo — a Vite + React +
  TypeScript SPA. It is **not a Maven module** (never in the reactor / `mvn
  verify`), mirroring the existing in-repo Node project at
  `examples/apps/src/main/frontend`. Run with `npm install && npm run dev`.
- **Graduation note:** if this grows from a demo into a published, standalone
  dev tool, extract it to its own repo (`callibrity/mocapi-apps-devhost`) with
  `git filter-repo` to keep history. Starting in-repo keeps it iterating
  tightly against `examples/apps`; that's the whole value early on.
- **Sonar:** it is JavaScript under `examples/**` (already coverage-excluded);
  confirm the JS is not newly picked up by Sonar analysis so it can't affect
  the gate.

## Architecture

Two halves. One is new; one adapts a proven reference.

### Half A — the 2026-07-28 transport client (the new part)

A tiny browser MCP client. No existing host has this, because it's what the
new revision changed. It POSTs to the server's `/mcp` endpoint with:

- Headers: `Content-Type: application/json`, `Accept: application/json,
  text/event-stream`, `MCP-Protocol-Version: 2026-07-28`, `Mcp-Method:
  <method>`, and `Mcp-Name: <tool-or-resource-name>` for `tools/call` /
  `resources/read`.
- Body: JSON-RPC with a `params._meta` envelope carrying
  `io.modelcontextprotocol/clientCapabilities` (declaring the `ui` extension),
  `io.modelcontextprotocol/protocolVersion: "2026-07-28"`, and
  `io.modelcontextprotocol/clientInfo`.

Methods used: `server/discover`, `tools/list`, `tools/call`,
`resources/read`. It parses the JSON (or the single-message SSE stream) into a
result, surfacing JSON-RPC errors (e.g. `-32602` on a missing `_meta` key)
as typed failures.

This client is the one piece worth unit-testing in isolation: envelope/header
construction and result/error parsing are pure and deterministic.

### Half B — the iframe bridge (adapt the reference)

Built on the official **`@modelcontextprotocol/ext-apps/app-bridge`**
(`PostMessageTransport`, `getToolUiResourceUri`, the `McpUi*` request /
notification/result types), adapting `ext-apps/examples/basic-host`'s host
logic rather than hand-rolling postMessage. Responsibilities:

- Complete the `ui/initialize` handshake with the app.
- Deliver the initiating tool's result to the app
  (`McpUiToolResultNotification`).
- Handle the app's `callServerTool` request by proxying it through Half A's
  transport client to the server and returning the `CallToolResult`.
- Everything else the bridge can receive in v1 is acknowledged/ignored per the
  non-goals.

### Sandbox model — two-origin proxy

The rendered app is isolated exactly as the reference host does it:

```
host page            (dev origin A, e.g. :5173)
  └─ iframe ─▶ sandbox.html   (dev origin B, e.g. :5174 — a DIFFERENT origin)
                └─ loads the ui:// bundle
app ⇄ sandbox proxy ⇄ host    (postMessage, cross-origin, isolated)
```

A different origin for `sandbox.html` means the app cannot reach the host's
DOM or globals. This matches the spec's `sandbox-proxy-ready` handshake and
the SDK app side's expectations, so an app built with the SDK's `useApp`
connects without special-casing. The second origin is provided by a small dev
server / Vite setup (a `serve.ts`-style bit, per the reference).

## Data flow

```
1. user enters server URL, clicks Connect
2. host ─ server/discover ─▶ server        (assert io.modelcontextprotocol/ui;
                                             else show "not an MCP Apps server")
3. host ─ tools/list ──────▶ server
4. host filters tools to those with _meta.ui.resourceUri → renders one card each
5. user clicks a card
6. host ─ tools/call ──────▶ server         → CallToolResult
7. host ─ resources/read ──▶ server         → ui:// bundle HTML (Mcp-Name = uri)
8. host mounts sandbox.html (origin B) in an iframe; sandbox loads the bundle
9. app connects over the bridge; host delivers the CallToolResult (step 6)
10. app renders; on a button, app.callServerTool ─▶ host ─ tools/call ─▶ server
    → result flows back to the app
```

## Components (files, each one responsibility)

- `src/mcp/transport.ts` — the 2026-07-28 client (Half A): build request,
  send, parse result/error. Pure and unit-tested.
- `src/mcp/envelope.ts` — the `_meta` envelope + header constants/builders
  (kept separate so tests pin the exact wire shape).
- `src/host/bridge.ts` — the ext-apps host bridge (Half B): wire
  `PostMessageTransport` to the app, handle `initialize` / tool-result /
  `callServerTool`.
- `src/host/sandbox.html` + `src/host/sandbox.ts` — the origin-B proxy document
  that hosts the app bundle.
- `src/ui/App.tsx` — the SPA: URL box, connect state, the tool-card grid, and
  the active-app iframe panel.
- `src/ui/ToolCard.tsx` — one card (tool name/title/description + Run).
- `serve.ts` / `vite.config.ts` — dev server(s): the host origin, the sandbox
  origin, and a proxy for `/mcp` to a configurable target (default
  `http://localhost:8888`) so localhost demos have no CORS friction; a typed
  cross-origin URL relies on that server's CORS.

## Error handling

- Server unreachable / non-2xx → inline error on the connect panel.
- `server/discover` without the `ui` capability → "this server doesn't
  advertise MCP Apps" (still list tools? No — v1 requires the capability).
- JSON-RPC error (e.g. `-32602`) → surface code + message.
- `tools/call` returning `isError: true` → still render the app but show the
  error result (the app decides what to display).
- `resources/read` failure / missing bundle → card-level error, don't mount
  the iframe.

## Testing & success criteria

- **Unit:** `transport.ts` / `envelope.ts` — assert exact headers, `_meta`
  envelope, and JSON-RPC result/error parsing against fixed inputs (Vitest).
- **Manual success criterion:** start `examples/apps` (`--server.port=8888`),
  `npm run dev` the host, point it at `http://localhost:8888/mcp`, see a
  **Get Time** card, click it, watch the React UI render the server time and
  the "Get Server Time" button round-trip through the host to the `get-time`
  tool.
- The bridge/sandbox path is validated manually (browser), like the reference
  host — no headless browser test in v1.

## Open risks

- The exact `sandbox-proxy-ready` sequence must match what the SDK app side
  waits for; mitigated by adapting the working reference host rather than
  inventing it. If the reference's flow changed across SDK versions, pin the
  same `@modelcontextprotocol/ext-apps` version the `examples/apps` bundle uses.
