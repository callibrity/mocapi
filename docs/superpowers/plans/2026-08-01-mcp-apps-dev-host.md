# MCP Apps Dev Host Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A minimal in-repo dev host that connects to a mocapi (MCP 2026-07-28) server, lists its UI-bearing tools as cards, and renders a clicked tool's `ui://` app end-to-end in a sandboxed iframe — proving mocapi Apps output, since no shipping host speaks 2026-07-28.

**Architecture:** A Vite + React + TypeScript SPA at `examples/dev-host/`. **Half A** is a browser MCP client that speaks 2026-07-28 (the `_meta` envelope + `MCP-Protocol-Version`/`Mcp-Method`/`Mcp-Name` headers) — the only genuinely new code. **Half B** is the host↔iframe bridge, built on the official `@modelcontextprotocol/ext-apps/app-bridge` `AppBridge` in **no-client mode** (`new AppBridge(null, …)`), with the app's `tools/call` requests proxied to Half A. The rendered app is isolated with the reference's **two-origin sandbox proxy** (`sandbox.html` served from a different port/origin, double-iframe relay). Run model mirrors `ext-apps/examples/basic-host`: `vite build` → an express `serve.ts` on two ports.

**Tech Stack:** Vite 6, React 19, TypeScript 5.9, Vitest 2 (unit tests), express 5 (two-origin serve), `@modelcontextprotocol/ext-apps` `^1.7.0`, `@modelcontextprotocol/sdk` `^1.29.0`.

## Global Constraints

- Location: `examples/dev-host/`. It is **NOT a Maven module** — never add it to any `pom.xml` `<modules>`; the root reactor must not build it.
- Pin `@modelcontextprotocol/ext-apps` to `^1.7.0` and `@modelcontextprotocol/sdk` to `^1.29.0` — the versions `examples/apps/src/main/frontend` already uses (avoids app/host bridge-protocol skew).
- Transport is **MCP 2026-07-28 only**: every request carries headers `Content-Type: application/json`, `Accept: application/json, text/event-stream`, `MCP-Protocol-Version: 2026-07-28`, `Mcp-Method: <method>`, and `Mcp-Name: <name>` for `tools/call` (tool name) and `resources/read` (resource URI); body `params._meta` carries `io.modelcontextprotocol/clientCapabilities` (declaring the `ui` extension), `io.modelcontextprotocol/protocolVersion: "2026-07-28"`, and `io.modelcontextprotocol/clientInfo`.
- Sandbox: the host origin and the sandbox origin **MUST differ** (spec requirement; enforced by two express ports). Never load the `ui://` bundle directly on the host origin.
- No new Sonar/coverage surface for the Java build: confirm `examples/**` stays excluded from Sonar analysis (it already is for coverage).
- Non-goals (do NOT build): `sendMessage`/`sendLog`/`openLink`, display-mode negotiation, host-context/safe-area, OAuth2 servers, connection persistence, rendering non-UI tools.
- Reference source (fetch raw when adapting): `https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/examples/basic-host/<path>` — `serve.ts`, `sandbox.html`, `src/sandbox.ts`, `vite.config.ts`, `src/index.tsx`.

---

## File Structure

- `examples/dev-host/package.json` — deps, scripts (`build`, `serve`, `dev`, `test`).
- `examples/dev-host/tsconfig.json`, `vite.config.ts`, `.gitignore` — TS/Vite config; `node_modules/` + `dist/` ignored.
- `examples/dev-host/index.html` — host SPA entry (origin A).
- `examples/dev-host/sandbox.html` — sandbox proxy entry (origin B). Adapted from reference.
- `examples/dev-host/serve.ts` — express: host server (port A) + sandbox server (port B, CSP headers). Adapted from reference.
- `examples/dev-host/src/mcp/envelope.ts` — header + `_meta` envelope builders and constants (pure).
- `examples/dev-host/src/mcp/transport.ts` — `DevHostMcpClient` (Half A): `discover`/`listTools`/`callTool`/`readResource`.
- `examples/dev-host/src/mcp/transport.test.ts` — Vitest unit tests for envelope + parsing.
- `examples/dev-host/src/host/bridge.ts` — `mountApp(...)` (Half B): build `AppBridge(null,…)`, connect to the sandbox iframe, deliver the tool result, proxy `tools/call`.
- `examples/dev-host/src/host/sandbox.ts` — the double-iframe relay (origin B). Adapted from reference.
- `examples/dev-host/src/ui/App.tsx` — SPA: URL box, connect, tool-card grid, active-app panel.
- `examples/dev-host/src/ui/ToolCard.tsx` — one clickable card.
- `examples/dev-host/src/ui/main.tsx`, `src/ui/styles.css` — React mount + minimal styles.
- `examples/dev-host/README.md` — how to run against `examples/apps`.

---

## Task 1: Scaffold the non-Maven Vite/React/TS project

**Files:**
- Create: `examples/dev-host/package.json`, `tsconfig.json`, `vite.config.ts`, `.gitignore`, `index.html`, `src/ui/main.tsx`, `src/ui/App.tsx` (placeholder), `src/ui/styles.css`

**Interfaces:**
- Produces: an installable, buildable project. `npm run test` runs Vitest; `npm run build` runs vite; `npm run serve` runs `serve.ts`; `npm run dev` = build watch + serve.

- [ ] **Step 1: Write `package.json`**

```json
{
  "name": "mocapi-apps-dev-host",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "description": "Dev host that renders mocapi MCP Apps (ui://) end-to-end over MCP 2026-07-28",
  "scripts": {
    "build": "cross-env INPUT=index.html vite build && cross-env INPUT=sandbox.html vite build",
    "serve": "tsx serve.ts",
    "dev": "concurrently -k \"cross-env NODE_ENV=development INPUT=index.html vite build --watch\" \"cross-env NODE_ENV=development INPUT=sandbox.html vite build --watch\" \"tsx serve.ts\"",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@modelcontextprotocol/ext-apps": "^1.7.0",
    "@modelcontextprotocol/sdk": "^1.29.0",
    "cors": "^2.8.5",
    "express": "^5.1.0",
    "react": "^19.2.0",
    "react-dom": "^19.2.0"
  },
  "devDependencies": {
    "@types/cors": "^2.8.19",
    "@types/express": "^5.0.0",
    "@types/node": "^22.10.0",
    "@types/react": "^19.2.2",
    "@types/react-dom": "^19.2.2",
    "@vitejs/plugin-react": "^4.3.4",
    "concurrently": "^9.2.1",
    "cross-env": "^10.1.0",
    "tsx": "^4.19.0",
    "typescript": "^5.9.3",
    "vite": "^6.0.0",
    "vite-plugin-singlefile": "^2.3.0",
    "vitest": "^2.1.0"
  }
}
```

- [ ] **Step 2: Write `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ESNext",
    "lib": ["ESNext", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "verbatimModuleSyntax": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "skipLibCheck": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true
  },
  "include": ["src", "serve.ts", "vite.config.ts"]
}
```

- [ ] **Step 3: Write `vite.config.ts`** (two inputs via `INPUT` env, single-file output, so both `index.html` and `sandbox.html` build to `dist/`)

```ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

const INPUT = process.env.INPUT;
if (!INPUT) throw new Error("INPUT environment variable is not set (index.html or sandbox.html)");
const isDev = process.env.NODE_ENV === "development";

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    sourcemap: isDev ? "inline" : undefined,
    cssMinify: !isDev,
    minify: !isDev,
    rollupOptions: { input: INPUT },
    outDir: "dist",
    emptyOutDir: false,
  },
});
```

- [ ] **Step 4: Write `.gitignore`**

```
node_modules/
dist/
```

- [ ] **Step 5: Write `index.html`, `src/ui/main.tsx`, `src/ui/styles.css`, and a placeholder `src/ui/App.tsx`**

`index.html`:
```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="color-scheme" content="light dark" />
    <title>mocapi MCP Apps — Dev Host</title>
    <link rel="stylesheet" href="/src/ui/styles.css" />
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/ui/main.tsx"></script>
  </body>
</html>
```

`src/ui/main.tsx`:
```tsx
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

`src/ui/App.tsx` (placeholder, replaced in Task 5):
```tsx
export function App() {
  return <main>mocapi dev host — scaffolding</main>;
}
```

`src/ui/styles.css`:
```css
:root { color-scheme: light dark; --gap: 1rem; }
* { box-sizing: border-box; }
body { margin: 0; font-family: ui-sans-serif, system-ui, sans-serif; }
```

- [ ] **Step 6: Install and verify build**

Run: `cd examples/dev-host && npm install && npm run build`
Expected: `dist/index.html` and `dist/sandbox.html`... (only `index.html` yet — `sandbox.html` comes in Task 3; for now the `INPUT=sandbox.html` build will fail). To keep Step 6 green, temporarily run only the host build: `cross-env INPUT=index.html npx vite build`. Expected: `dist/index.html` exists.

- [ ] **Step 7: Commit**

```bash
git add examples/dev-host
git commit -m "feat(dev-host): scaffold non-Maven Vite/React/TS project"
```

---

## Task 2: The 2026-07-28 transport client (Half A) + unit tests

**Files:**
- Create: `examples/dev-host/src/mcp/envelope.ts`, `src/mcp/transport.ts`, `src/mcp/transport.test.ts`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `envelope.ts`: `PROTOCOL_VERSION = "2026-07-28"`; `buildHeaders(method: string, mcpName?: string): Record<string,string>`; `buildMeta(): Record<string, unknown>` (the `params._meta` object).
  - `transport.ts`: `class DevHostMcpClient { constructor(endpoint: string); discover(): Promise<DiscoverResult>; listTools(): Promise<Tool[]>; callTool(name: string, args: Record<string,unknown>): Promise<CallToolResult>; readResource(uri: string): Promise<ReadResourceResult>; }` where `Tool`, `CallToolResult`, `ReadResourceResult` are imported from `@modelcontextprotocol/sdk/types.js`, and `DiscoverResult = { supportedVersions: string[]; capabilities: { extensions?: Record<string, unknown> } }`. Throws `McpError { code: number; message: string }` on JSON-RPC error.

- [ ] **Step 1: Write the failing test** — `src/mcp/transport.test.ts`

```ts
import { describe, expect, it, vi } from "vitest";
import { buildHeaders, buildMeta, PROTOCOL_VERSION } from "./envelope";
import { DevHostMcpClient, McpError } from "./transport";

describe("envelope", () => {
  it("sets the 2026-07-28 routing headers, with Mcp-Name only when given", () => {
    expect(buildHeaders("tools/list")).toMatchObject({
      "Content-Type": "application/json",
      "Accept": "application/json, text/event-stream",
      "MCP-Protocol-Version": PROTOCOL_VERSION,
      "Mcp-Method": "tools/list",
    });
    expect(buildHeaders("tools/list")["Mcp-Name"]).toBeUndefined();
    expect(buildHeaders("tools/call", "get-time")["Mcp-Name"]).toBe("get-time");
  });

  it("declares protocolVersion + the ui extension in clientCapabilities", () => {
    const meta = buildMeta() as Record<string, any>;
    expect(meta["io.modelcontextprotocol/protocolVersion"]).toBe(PROTOCOL_VERSION);
    expect(
      meta["io.modelcontextprotocol/clientCapabilities"].extensions["io.modelcontextprotocol/ui"],
    ).toBeTruthy();
  });
});

describe("DevHostMcpClient", () => {
  function mockFetch(body: unknown) {
    return vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }),
    );
  }

  it("posts a well-formed request and returns result.tools", async () => {
    const fetchMock = mockFetch({ jsonrpc: "2.0", id: 1, result: { tools: [{ name: "get-time" }] } });
    vi.stubGlobal("fetch", fetchMock);
    const tools = await new DevHostMcpClient("http://x/mcp").listTools();
    expect(tools).toEqual([{ name: "get-time" }]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://x/mcp");
    expect((init.headers as Record<string, string>)["Mcp-Method"]).toBe("tools/list");
    expect(JSON.parse(init.body).method).toBe("tools/list");
    vi.unstubAllGlobals();
  });

  it("throws McpError on a JSON-RPC error response", async () => {
    vi.stubGlobal("fetch", mockFetch({ jsonrpc: "2.0", id: 1, error: { code: -32602, message: "Missing required _meta key" } }));
    await expect(new DevHostMcpClient("http://x/mcp").listTools()).rejects.toMatchObject({
      code: -32602,
    });
    vi.unstubAllGlobals();
  });

  it("callTool/readResource pass Mcp-Name", async () => {
    const fetchMock = mockFetch({ jsonrpc: "2.0", id: 1, result: { content: [] } });
    vi.stubGlobal("fetch", fetchMock);
    await new DevHostMcpClient("http://x/mcp").callTool("get-time", {});
    expect((fetchMock.mock.calls[0][1].headers as Record<string, string>)["Mcp-Name"]).toBe("get-time");
    vi.unstubAllGlobals();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd examples/dev-host && npm test`
Expected: FAIL — `./envelope` and `./transport` do not exist.

- [ ] **Step 3: Write `src/mcp/envelope.ts`**

```ts
export const PROTOCOL_VERSION = "2026-07-28";

const APP_MIME = "text/html;profile=mcp-app";

export function buildHeaders(method: string, mcpName?: string): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Accept": "application/json, text/event-stream",
    "MCP-Protocol-Version": PROTOCOL_VERSION,
    "Mcp-Method": method,
  };
  if (mcpName) headers["Mcp-Name"] = mcpName;
  return headers;
}

export function buildMeta(): Record<string, unknown> {
  return {
    "io.modelcontextprotocol/protocolVersion": PROTOCOL_VERSION,
    "io.modelcontextprotocol/clientInfo": { name: "mocapi-dev-host", version: "0.0.0" },
    "io.modelcontextprotocol/clientCapabilities": {
      extensions: { "io.modelcontextprotocol/ui": { mimeTypes: [APP_MIME] } },
    },
  };
}
```

- [ ] **Step 4: Write `src/mcp/transport.ts`**

```ts
import type { CallToolResult, ReadResourceResult, Tool } from "@modelcontextprotocol/sdk/types.js";
import { buildHeaders, buildMeta } from "./envelope";

export interface DiscoverResult {
  supportedVersions: string[];
  capabilities: { extensions?: Record<string, unknown> };
}

export class McpError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = "McpError";
  }
}

let nextId = 1;

async function rpc<T>(endpoint: string, method: string, params: Record<string, unknown>, mcpName?: string): Promise<T> {
  const res = await fetch(endpoint, {
    method: "POST",
    headers: buildHeaders(method, mcpName),
    body: JSON.stringify({ jsonrpc: "2.0", id: nextId++, method, params: { ...params, _meta: buildMeta() } }),
  });
  if (!res.ok && res.status !== 200) throw new McpError(res.status, `HTTP ${res.status} from ${endpoint}`);
  const json = (await res.json()) as { result?: T; error?: { code: number; message: string } };
  if (json.error) throw new McpError(json.error.code, json.error.message);
  return json.result as T;
}

export class DevHostMcpClient {
  constructor(private readonly endpoint: string) {}

  discover(): Promise<DiscoverResult> {
    return rpc<DiscoverResult>(this.endpoint, "server/discover", {});
  }
  async listTools(): Promise<Tool[]> {
    const { tools } = await rpc<{ tools: Tool[] }>(this.endpoint, "tools/list", {});
    return tools;
  }
  callTool(name: string, args: Record<string, unknown>): Promise<CallToolResult> {
    return rpc<CallToolResult>(this.endpoint, "tools/call", { name, arguments: args }, name);
  }
  readResource(uri: string): Promise<ReadResourceResult> {
    return rpc<ReadResourceResult>(this.endpoint, "resources/read", { uri }, uri);
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd examples/dev-host && npm test`
Expected: PASS (all envelope + client tests green).

- [ ] **Step 6: Commit**

```bash
git add examples/dev-host/src/mcp
git commit -m "feat(dev-host): 2026-07-28 MCP transport client + envelope (Vitest)"
```

---

## Task 3: Two-origin sandbox proxy (serve.ts + sandbox.html + sandbox.ts)

**Files:**
- Create: `examples/dev-host/serve.ts`, `sandbox.html`, `src/host/sandbox.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `npm run serve` serves the built host on `HOST_PORT` (default `5173`) and `sandbox.html` on `SANDBOX_PORT` (default `5174`), different origins. The host page reads the sandbox origin from `GET /api/sandbox-origin` → `{ origin: "http://localhost:5174" }`, and the default target mocapi server from `GET /api/servers` → `["http://localhost:8888/mcp"]` (override via `SERVERS` env).

- [ ] **Step 1: Port `serve.ts` from the reference, adapted**

Fetch `https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/examples/basic-host/serve.ts` and adapt:
- Ports: `HOST_PORT` default `5173`, `SANDBOX_PORT` default `5174`.
- Keep the two express apps, `cors()`, the static `dist/` serving, the `/sandbox.html` 404 on the host server, and the CSP-header logic on the sandbox server (keep `sanitizeCspDomains` / `buildCspHeader` verbatim — they are security-relevant).
- `SERVERS` default: `["http://localhost:8888/mcp"]` (our `examples/apps` port). Keep `GET /api/servers`.
- Add `GET /api/sandbox-origin` on the host app returning `{ origin: \`http://localhost:${SANDBOX_PORT}\` }`.
- Start both servers and `console.log` both URLs.

Verify by reading the fetched file first; do not invent the CSP logic.

- [ ] **Step 2: Port `sandbox.html` from the reference, verbatim**

Fetch `https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/examples/basic-host/sandbox.html` and copy it into `examples/dev-host/sandbox.html` unchanged (it only loads `/src/sandbox.ts` and sets transparent styles).

- [ ] **Step 3: Port `src/sandbox.ts` from the reference, verbatim (adjust referrer allowlist only)**

Fetch `https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/examples/basic-host/src/sandbox.ts` and copy it to `examples/dev-host/src/host/sandbox.ts`. Keep the referrer/origin validation, the `window.top` isolation self-test, the double-iframe creation (`sandbox="allow-scripts allow-same-origin allow-forms"`), the `sandbox-proxy-ready` interception, and the bidirectional message relay **unchanged** — this is the security core. The default `ALLOWED_REFERRER_PATTERN` already allows `localhost`/`127.0.0.1`, which covers `HOST_PORT`.

- [ ] **Step 4: Build both entries and serve; verify origins differ**

Run: `cd examples/dev-host && npm run build && npm run serve`
Expected: console prints host on `http://localhost:5173` and sandbox on `http://localhost:5174`. `curl -s localhost:5173/api/sandbox-origin` → `{"origin":"http://localhost:5174"}`; `curl -s localhost:5173/api/servers` → `["http://localhost:8888/mcp"]`; `curl -s -o /dev/null -w "%{http_code}" localhost:5173/sandbox.html` → `404`; `curl -s -o /dev/null -w "%{http_code}" localhost:5174/sandbox.html` → `200`.

- [ ] **Step 5: Commit**

```bash
git add examples/dev-host/serve.ts examples/dev-host/sandbox.html examples/dev-host/src/host/sandbox.ts
git commit -m "feat(dev-host): two-origin sandbox proxy (serve.ts + sandbox relay), adapted from ext-apps basic-host"
```

---

## Task 4: Host bridge in no-client mode (Half B)

**Files:**
- Create: `examples/dev-host/src/host/bridge.ts`

**Interfaces:**
- Consumes: `DevHostMcpClient` from `src/mcp/transport.ts`; `CallToolResult`, `ReadResourceResult` from `@modelcontextprotocol/sdk/types.js`; `AppBridge`, `PostMessageTransport` from `@modelcontextprotocol/ext-apps/app-bridge`.
- Produces: `async function mountApp(opts: { iframe: HTMLIFrameElement; sandboxOrigin: string; bundleUri: string; bundleHtml: string; toolName: string; toolArgs: Record<string, unknown>; toolResult: CallToolResult; client: DevHostMcpClient }): Promise<{ dispose(): void }>`. It points the iframe at the sandbox origin, connects an `AppBridge(null, …)` over `PostMessageTransport(iframe.contentWindow)`, hands the sandbox the bundle HTML, delivers `toolResult` to the app, and proxies the app's `tools/call` requests through `client`.

- [ ] **Step 1: Write `src/host/bridge.ts`**

Confirm the exact no-client request-handler wiring against the SDK's `app-bridge` types (`node_modules/@modelcontextprotocol/ext-apps/dist/src/app-bridge.d.ts`) and the reference `basic-host/src/index.tsx` (`https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/examples/basic-host/src/index.tsx`) before finalizing. The confirmed shape:

```ts
import { AppBridge, PostMessageTransport } from "@modelcontextprotocol/ext-apps/app-bridge";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import type { DevHostMcpClient } from "../mcp/transport";

export interface MountOptions {
  iframe: HTMLIFrameElement;
  sandboxOrigin: string;
  bundleUri: string;
  bundleHtml: string;
  toolName: string;
  toolArgs: Record<string, unknown>;
  toolResult: CallToolResult;
  client: DevHostMcpClient;
}

export async function mountApp(opts: MountOptions): Promise<{ dispose(): void }> {
  const { iframe, sandboxOrigin, bundleUri, bundleHtml, toolName, toolArgs, toolResult, client } = opts;

  // 1. Point the iframe at the sandbox proxy (origin B). The sandbox reads the
  //    bundle via the proxy-ready handshake below.
  iframe.src = `${sandboxOrigin}/sandbox.html`;
  await new Promise<void>((resolve) => iframe.addEventListener("load", () => resolve(), { once: true }));

  // 2. Host-side bridge in NO-CLIENT mode: we proxy server calls ourselves,
  //    because an SDK Client would try to `initialize` against mocapi (2026-07-28
  //    has no initialize) and fail.
  const bridge = new AppBridge(
    null,
    { name: "mocapi-dev-host", version: "0.0.0" },
    { /* McpUiHostCapabilities — none of the optional host features in v1 */ },
  );

  // 3. Proxy the app's server tool calls to our 2026-07-28 client.
  bridge.setRequestHandler(CallToolRequestSchema, async (req) =>
    client.callTool(req.params.name, (req.params.arguments ?? {}) as Record<string, unknown>),
  );

  const transport = new PostMessageTransport(iframe.contentWindow!, iframe.contentWindow!);
  await bridge.connect(transport);

  // 4. Hand the sandbox the bundle HTML + deliver the initiating tool result.
  //    (Adapt from basic-host/src/index.tsx: it sends the resource-ready with the
  //    bundle text, then sendToolInput + sendToolResult.)
  await bridge.sendSandboxResourceReady({ resource: { uri: bundleUri, mimeType: "text/html;profile=mcp-app", text: bundleHtml } } as any);
  await bridge.sendToolInput({ toolName, params: toolArgs } as any);
  await bridge.sendToolResult(toolResult as any);

  return { dispose: () => bridge.close() };
}
```

Note: the exact `params` shapes for `sendSandboxResourceReady` / `sendToolInput` / `sendToolResult` come from the SDK's `McpUi*Notification["params"]` types and the reference — replace the `as any` casts with the real typed shapes once confirmed against `app-bridge.d.ts` and `index.tsx`. Do not ship `as any`.

- [ ] **Step 2: Typecheck**

Run: `cd examples/dev-host && npm run typecheck`
Expected: PASS (no `as any` remaining; the notification params typecheck against the SDK).

- [ ] **Step 3: Commit**

```bash
git add examples/dev-host/src/host/bridge.ts
git commit -m "feat(dev-host): no-client AppBridge host wiring, proxying tools/call to the 2026-07-28 client"
```

---

## Task 5: The SPA — connect, discover UI tools, cards, render

**Files:**
- Create: `examples/dev-host/src/ui/ToolCard.tsx`
- Modify: `examples/dev-host/src/ui/App.tsx` (replace placeholder), `src/ui/styles.css` (add card + panel styles)

**Interfaces:**
- Consumes: `DevHostMcpClient` (`src/mcp/transport.ts`), `mountApp` (`src/host/bridge.ts`), `getToolUiResourceUri` from `@modelcontextprotocol/ext-apps/app-bridge`, `Tool` from `@modelcontextprotocol/sdk/types.js`.
- Produces: the full host UI.

- [ ] **Step 1: Write `src/ui/ToolCard.tsx`**

```tsx
import type { Tool } from "@modelcontextprotocol/sdk/types.js";

export function ToolCard({ tool, uiUri, onRun }: { tool: Tool; uiUri: string; onRun: () => void }) {
  return (
    <button className="card" onClick={onRun}>
      <h3>{tool.title ?? tool.name}</h3>
      {tool.description && <p>{tool.description}</p>}
      <code>{uiUri}</code>
    </button>
  );
}
```

- [ ] **Step 2: Write `src/ui/App.tsx`** (replace placeholder)

```tsx
import { useCallback, useRef, useState } from "react";
import { getToolUiResourceUri } from "@modelcontextprotocol/ext-apps/app-bridge";
import type { Tool } from "@modelcontextprotocol/sdk/types.js";
import { DevHostMcpClient, McpError } from "../mcp/transport";
import { mountApp } from "../host/bridge";
import { ToolCard } from "./ToolCard";

const UI_CAP = "io.modelcontextprotocol/ui";

export function App() {
  const [url, setUrl] = useState("http://localhost:8888/mcp");
  const [error, setError] = useState<string | null>(null);
  const [uiTools, setUiTools] = useState<{ tool: Tool; uiUri: string }[]>([]);
  const clientRef = useRef<DevHostMcpClient | null>(null);
  const [sandboxOrigin, setSandboxOrigin] = useState("http://localhost:5174");
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const [active, setActive] = useState<string | null>(null);

  const connect = useCallback(async () => {
    setError(null);
    setUiTools([]);
    setActive(null);
    try {
      // sandbox origin is provided by serve.ts
      setSandboxOrigin((await (await fetch("/api/sandbox-origin")).json()).origin);
      const client = new DevHostMcpClient(url);
      clientRef.current = client;
      const discover = await client.discover();
      if (!discover.capabilities.extensions?.[UI_CAP]) {
        setError("This server does not advertise the MCP Apps (io.modelcontextprotocol/ui) capability.");
        return;
      }
      const tools = await client.listTools();
      const withUi = tools
        .map((tool) => ({ tool, uiUri: getToolUiResourceUri(tool) }))
        .filter((t): t is { tool: Tool; uiUri: string } => Boolean(t.uiUri));
      if (withUi.length === 0) setError("Connected, but no tools declare a ui:// resource.");
      setUiTools(withUi);
    } catch (e) {
      setError(e instanceof McpError ? `MCP error ${e.code}: ${e.message}` : String(e));
    }
  }, [url]);

  const run = useCallback(
    async ({ tool, uiUri }: { tool: Tool; uiUri: string }) => {
      setError(null);
      setActive(tool.name);
      const client = clientRef.current!;
      try {
        const toolResult = await client.callTool(tool.name, {});
        const read = await client.readResource(uiUri);
        const bundleHtml = (read.contents[0] as { text?: string }).text ?? "";
        await mountApp({
          iframe: iframeRef.current!,
          sandboxOrigin,
          bundleUri: uiUri,
          bundleHtml,
          toolName: tool.name,
          toolArgs: {},
          toolResult,
          client,
        });
      } catch (e) {
        setError(e instanceof McpError ? `MCP error ${e.code}: ${e.message}` : String(e));
      }
    },
    [sandboxOrigin],
  );

  return (
    <main className="host">
      <header>
        <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="http://localhost:8888/mcp" />
        <button onClick={connect}>Connect</button>
      </header>
      {error && <p className="error">{error}</p>}
      <section className="cards">
        {uiTools.map((t) => (
          <ToolCard key={t.tool.name} tool={t.tool} uiUri={t.uiUri} onRun={() => run(t)} />
        ))}
      </section>
      <section className="stage" hidden={!active}>
        <iframe ref={iframeRef} title="mcp-app" className="app-frame" />
      </section>
    </main>
  );
}
```

- [ ] **Step 3: Add styles** to `src/ui/styles.css`

```css
.host { display: flex; flex-direction: column; gap: var(--gap); padding: var(--gap); }
.host header { display: flex; gap: .5rem; }
.host header input { flex: 1; padding: .5rem; }
.error { color: #b00020; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--gap); }
.card { text-align: left; padding: var(--gap); border: 1px solid #8884; border-radius: 8px; cursor: pointer; background: transparent; }
.card h3 { margin: 0 0 .25rem; }
.stage { min-height: 400px; }
.app-frame { width: 100%; height: 480px; border: 1px solid #8884; border-radius: 8px; }
```

- [ ] **Step 4: Typecheck + build**

Run: `cd examples/dev-host && npm run typecheck && npm run build`
Expected: PASS; `dist/index.html` and `dist/sandbox.html` both produced.

- [ ] **Step 5: Commit**

```bash
git add examples/dev-host/src/ui
git commit -m "feat(dev-host): SPA — connect, discover ui:// tools, cards, render in sandbox"
```

---

## Task 6: End-to-end verification against examples/apps + README

**Files:**
- Create: `examples/dev-host/README.md`

**Interfaces:**
- Consumes: everything above; the running `examples/apps` server.

- [ ] **Step 1: Ensure the mocapi server allows the host origin (CORS)**

`examples/apps` `CorsConfig` defaults to allowing `http://localhost:8080` / `:8080`. The dev host runs on `:5173`. Start the server with the host origin allowed:

Run:
```bash
cd /Users/jcarman/IdeaProjects/mocapi
mvn -pl examples/apps -am package -DskipTests
java -jar examples/apps/target/mocapi-example-apps-*.jar \
  --server.port=8888 --mocapi.example.cors-origins=http://localhost:5173
```
Expected: server up on 8888, logs "Registered MCP Apps UI resource ... get-time".

- [ ] **Step 2: Run the dev host and render get-time end-to-end**

Run (second terminal): `cd examples/dev-host && npm run build && npm run serve`
Then in a browser open `http://localhost:5173`, confirm the target URL is `http://localhost:8888/mcp`, click **Connect**.
Expected: one **Get Time** card appears (title "Get Time App - Get Time", `ui://get-time/mcp-app.html`). Click it.
Expected: the iframe renders the React UI showing the server time; clicking **Get Server Time** updates the time (round-trips `tools/call get-time` through the host). No console errors from the sandbox isolation self-test.

If the app fails to connect, re-check the `sendSandboxResourceReady`/`sendToolResult` param shapes in `bridge.ts` against `basic-host/src/index.tsx` — that is the most likely defect site.

- [ ] **Step 3: Write `README.md`**

Document: what it is (a 2026-07-28 dev host to render mocapi Apps), the two-origin sandbox note, the exact two commands above (server with `--mocapi.example.cors-origins`, then `npm run build && npm run serve`), the default ports (5173 host / 5174 sandbox / 8888 server), and the "graduate to its own repo if it becomes a product" note from the spec. State clearly this is a dev/demo host, not a production MCP host.

- [ ] **Step 4: Commit**

```bash
git add examples/dev-host/README.md
git commit -m "docs(dev-host): README — run against examples/apps, ports, scope"
```

---

## Self-Review

**Spec coverage:** connect+discover (Task 5), UI-tool cards (Task 5), tools/call+resources/read (Tasks 2,5), two-origin sandbox (Task 3), bridge + callServerTool proxy (Task 4), transport client unit-tested (Task 2), success criterion get-time e2e (Task 6), non-Maven + pinned deps + Sonar exclusion (Task 1 + Global Constraints). All covered.

**Placeholder scan:** the only deferred specifics are the exact `McpUi*Notification["params"]` shapes in Task 4, which are explicitly resolved by reading the SDK `.d.ts` + `basic-host/src/index.tsx` (named files, not "figure it out"), with an explicit "do not ship `as any`" gate. The sandbox/serve files are ported from named reference URLs with enumerated deltas — not hand-waving.

**Type consistency:** `DevHostMcpClient` methods (`discover`/`listTools`/`callTool`/`readResource`), `mountApp(MountOptions)`, `getToolUiResourceUri`, and `buildHeaders`/`buildMeta` are used with identical signatures across Tasks 2/4/5.
