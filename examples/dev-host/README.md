# mocapi MCP Apps — Dev Host

A minimal **MCP Apps host** for local development, built to render mocapi's `ui://`
apps end-to-end. It speaks **MCP `2026-07-28`** — `server/discover`, the `_meta`
envelope, and the `MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name` headers — which
is why it exists: mocapi is the *server* half of MCP Apps, but no shipping host
(Claude Desktop, the ext-apps `basic-host`) speaks `2026-07-28`, so there is nothing
else that can render mocapi's Apps output. Point this at a mocapi server, see its
UI-bearing tools as cards, click one, and watch the linked `ui://` app render in a
sandboxed iframe and round-trip a tool call.

> **This is a dev/demo host, not a production MCP host.** It implements just enough
> of the host surface to render and exercise a mocapi App locally.

## What it does

1. You enter a mocapi server URL and **Connect**. The host runs `server/discover`
   (and checks for the `io.modelcontextprotocol/ui` capability), then `tools/list`.
2. Tools that declare a UI (`_meta.ui.resourceUri`) are shown as **clickable cards**.
3. Click a card → the host `tools/call`s the tool, `resources/read`s its `ui://`
   bundle, and renders it in a sandboxed iframe.
4. The rendered app talks back over the ext-apps `postMessage` bridge; the host
   delivers the tool result and proxies the app's `callServerTool` back to the
   server.

The **Wire** panel shows every MCP exchange as it happens — `server/discover`,
`tools/list`, `tools/call`, `resources/read`, and the app's proxied
`callServerTool` — with the routing headers and the request/response JSON
(large values like the `ui://` bundle are truncated).

## Architecture

Two halves (see [`docs/superpowers/specs/2026-08-01-mcp-apps-dev-host-design.md`](../../docs/superpowers/specs/2026-08-01-mcp-apps-dev-host-design.md)):

- **`src/mcp/`** — a small **2026-07-28 transport client** (the piece no existing
  host has): `server/discover`, `tools/list`, `tools/call`, `resources/read` with
  the required headers and `_meta` envelope.
- **`src/host/`** — the **iframe bridge**, built on the official
  [`@modelcontextprotocol/ext-apps`](https://www.npmjs.com/package/@modelcontextprotocol/ext-apps)
  `AppBridge` (no-client mode) and adapted from `ext-apps/examples/basic-host`. The
  app is isolated with a **two-origin sandbox proxy**: `sandbox.html` is served from
  a *different* origin (port) than the host page, so the app cannot reach the host —
  the model the SDK's sandbox handshake expects.

Not a Maven module — a standalone Vite/React/TypeScript project.

## Run it against the Apps example

**Terminal 1 — the mocapi server** (the [`examples/apps`](../apps) Get Time app).
The dev host runs on `http://localhost:5173`, so allow that origin via CORS:

```bash
# from the repo root
mvn -pl examples/apps -am package -DskipTests
# examples/apps defaults to :8080 (application.properties); just allow the host origin
java -jar examples/apps/target/mocapi-example-apps-*.jar \
    --mocapi.example.cors-origins=http://localhost:5173
```

**Terminal 2 — the dev host:**

```bash
cd examples/dev-host
npm install
npm run build   # builds the host page + the sandbox page (two Vite entries)
npm run serve   # host on :5173, sandbox on :5174
```

Open **http://localhost:5173**, leave the URL as `http://localhost:8080/mcp`, click
**Connect** → a **Get Time** card appears → click it. The app renders the server
time, and the **Get Server Time** button round-trips through the host to the
`get-time` tool.

`npm run dev` runs the same thing with Vite in watch mode (rebuilds on source
changes) alongside the server.

## Ports

| Port | What |
|------|------|
| `5173` | host page (origin A) |
| `5174` | sandbox proxy (origin B — MUST differ from the host) |
| `8080` | the mocapi server (any mocapi `2026-07-28` server; must allow the host origin via CORS) |

## Scope (v1)

Renders a UI-bearing tool and proxies its `callServerTool`. **Out of scope:**
`sendMessage` / `sendLog` / `openLink`, display-mode negotiation, host-context,
OAuth2-protected servers, and connection persistence — this is a demo host, not a
full one.

## If this grows up

It starts in-repo (colocated with the `examples/apps` bundle it renders) for tight
iteration. If it becomes a published, standalone dev tool, extract it to its own
repo (e.g. `callibrity/mocapi-apps-devhost`) with `git filter-repo` to keep history.
