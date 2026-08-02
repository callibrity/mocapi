# MCP Apps Extension — Design Spec

- **Date:** 2026-07-31
- **Status:** Approved design (pre-implementation)
- **Extension:** `io.modelcontextprotocol/ui` (SEP-1865, `modelcontextprotocol/ext-apps`)
- **Baseline spec:** the **Stable** snapshot `specification/2026-01-26/apps.mdx` (forward-compatible with `draft`)
- **Supersedes stance:** the "decline apps" line item in ADR-0022 (to be flipped during implementation)
- **Relationship to Tasks:** shares the `ServerCapabilitiesCustomizer` core seam introduced by the
  [MCP Tasks design](2026-08-02-mcp-tasks-extension-design.md); otherwise independent.

## 1. Summary

Add support for the MCP **Apps** extension as a new, optional module `mocapi-apps`. Apps lets a
server ship an interactive HTML UI that the host renders (in a sandboxed iframe) in place of
plain tool output. The **server's** entire role is declarative metadata: serve `ui://`-scheme
HTML resources (`text/html;profile=mcp-app`), stamp `_meta.ui` on tools and resources to link
them to their UI, and declare the `io.modelcontextprotocol/ui` capability. No state, no new
JSON-RPC methods, no runtime execution. The whole interactive layer — the sandbox handshake,
the `ui/initialize`/`postMessage` bridge, display modes, app-registered tools — is **host and
in-iframe JavaScript**, which mocapi does not implement.

## 2. Scope boundary (decided)

mocapi implements the **server** surface only, with authoring ergonomics, and stops at the
language boundary:

- **In scope:** `ui://` resource declaration + serving; tool↔UI linkage `_meta.ui`; resource
  `_meta.ui` (CSP/sandbox); capability declaration; ergonomic annotations.
- **Out of scope (host / in-iframe JS — the "JS bridge"):** the `postMessage` JSON-RPC
  protocol, `ui/initialize`, `ui/notifications/sandbox-proxy-ready` /
  `sandbox-resource-ready`, display modes, host/app capability negotiation,
  app-registered tools, `sampling/createMessage` over postMessage. App authors supply the
  in-iframe JS themselves (via the official JS SDK); mocapi serves their HTML bytes
  content-agnostically. When an app calls a server tool, it arrives at mocapi as an
  **ordinary `tools/call`** — no new server code.

## 3. What the extension requires (server side, grounded in the spec)

- **UI resources:** URI **MUST** use the `ui://` scheme; `mimeType` **MUST** be
  `text/html;profile=mcp-app` (other types reserved). Served via existing
  `resources/list` / `resources/read`.
- **Resource `_meta.ui`** (`UIResourceMeta`): `csp` (`McpUiResourceCsp`:
  `connectDomains`, `resourceDomains`, `frameDomains`, `baseUriDomains`) + `sandbox`
  (requested browser permissions). The server **declares** what the UI needs; the host
  enforces it on the iframe. May appear on the `resources/list` entry (static default) and,
  in the draft, on the `resources/read` content item (per-response override, content wins).
  **v1 targets the listing/static form** (the per-response override is draft-only — deferred).
- **Tool `_meta.ui`** (`McpUiToolMeta`): `resourceUri` (the linked `ui://` resource) +
  `visibility: Array<"model"|"app">` (default `["model","app"]`). Note `visibility` here is a
  **UI access axis** (model vs app), distinct from mocapi's auth-Guard visibility (ADR-0012);
  it is emitted as metadata and enforced host-side, not by mocapi.
- **Capability:** `capabilities.extensions["io.modelcontextprotocol/ui"] = { mimeTypes:
  ["text/html;profile=mcp-app"] }`.
- **Stateless fit:** the SDK examples *conditionally register* UI tools after an `initialize`
  handshake. mocapi has no handshake (stateless, `server/discover`); it **always** emits
  `_meta.ui`, and non-Apps hosts ignore it (spec's text-only fallback). Simpler and correct.

## 4. Goals / non-goals

**Goals**
- Declare a `ui://` HTML resource and link a tool to it with **static annotations**.
- Emit correct `_meta.ui` on tool and resource descriptors; declare the `ui` capability.
- Typed, ergonomic CSP/sandbox declaration on UI resources.
- Zero new server methods, zero state.

**Non-goals (v1, YAGNI)**
- Any `postMessage`/iframe/JS-bridge code (§2).
- Per-response `_meta.ui` override and a call-time `UiContext` (draft-only; deferred — §6.3).
- App-registered tools, display modes, sampling-over-postMessage (host/iframe concerns).
- `prefersBorder` and other draft-only visual fields (may be added forward-compatibly later).

## 5. Architecture

### 5.1 Module layout

```
mocapi-apps-api    annotations + types: @McpAppResource, @McpUi, @Csp,
      ▲            McpUiToolMeta, UIResourceMeta, McpUiResourceCsp
      │
mocapi-apps        impl: ToolDescriptorCustomizer + ResourceDescriptorCustomizer (read the
      │            annotations → _meta.ui), UiCapabilityCustomizer, MocapiAppsAutoConfiguration
      │
      └─ depends on ─▶ mocapi-api, mocapi-model, mocapi-server (existing seams + the core changes below)

core changes:  mocapi-model   (+ _meta ObjectNode on Tool, Resource, resource content items)
               mocapi-server  (+ ToolDescriptorCustomizer / ResourceDescriptorCustomizer seams;
                               + meta-annotation-aware handler discovery;
                               + ServerCapabilitiesCustomizer — shared with Tasks)
```

Add `mocapi-apps` → Apps work. Omit it → no customizer beans, no `@McpUi`/`@McpAppResource`
usage; the core changes are inert (no descriptor gains `_meta`, discovery behaves identically
for direct annotations).

### 5.2 Core touches (honest inventory)

> **Foundation status (2026-07-31):** items 3 and 4 below shipped ahead of this module on
> `feat/extension-foundation-seams` — meta-annotation-aware discovery ([ADR-0032](../../adr/0032-meta-annotation-aware-handler-discovery.md),
> including the `@Target({METHOD, ANNOTATION_TYPE})` widening a test surfaced) and
> `ServerCapabilitiesCustomizer` ([ADR-0031](../../adr/0031-server-capabilities-customizer.md)).
> The `@McpAppResource` pattern (meta-annotated `@McpResource`, `uri` via `@AliasFor` with the
> `uri = ""` meta override) is **proven** by a passing end-to-end test, not assumed. Only items 1
> and 2 remain as new core work for this module.

1. **`_meta` on descriptors.** *(remaining)* `Tool` and `Resource` model records (and resource content items
   — `TextResourceContents`/`BlobResourceContents`) gain an optional `_meta` (`ObjectNode`,
   `NON_NULL`). Additive/backward-compatible on the wire, and a latent base-protocol fidelity
   gap regardless (ADR-0014). Descriptor build populates it.

2. **Descriptor `_meta` customizer seams.** *(remaining)* New `ToolDescriptorCustomizer` and
   `ResourceDescriptorCustomizer` post-process each generated descriptor:

   ```java
   @FunctionalInterface
   public interface ToolDescriptorCustomizer { Tool customize(Method method, Tool descriptor); }
   ```

   Applied in the tool/resource build path (`CallToolHandlers.build`,
   `ReadResourceHandlers.build`). `mocapi-apps` contributes customizers that read `@McpUi` /
   `@McpAppResource` and write `_meta.ui`. **Core never learns what "ui" means** — it only
   offers "enrich a descriptor's `_meta`." Same philosophy as the Tasks seams.

3. **Meta-annotation-aware handler discovery.** ✅ **Shipped** ([ADR-0032](../../adr/0032-meta-annotation-aware-handler-discovery.md)).
   A module's `@McpAppResource` (meta-annotated `@McpResource`) is discovered *as a resource* with
   no bespoke registration SPI. `HandlerMethodsCache` + `HandlerKind` detect via `MergedAnnotations`;
   the four `*Handlers.build` factories read via `AnnotatedElementUtils.findMergedAnnotation` (so
   `@AliasFor` on `uri` surfaces); the four handler annotations gained
   `@Target({METHOD, ANNOTATION_TYPE})`. Verified backward-compatible by the full suite.

4. **`ServerCapabilitiesCustomizer`** ✅ **Shipped** ([ADR-0031](../../adr/0031-server-capabilities-customizer.md)).
   `UiCapabilityCustomizer` declares `io.modelcontextprotocol/ui` via this seam; no further core
   change needed here.

Everything else is additive on existing seams (`@McpResource` registration, `resources/read`,
ripcurl dispatch, `@ConditionalOnMissingBean` defaults).

## 6. Author-facing API

### 6.1 UI resource (meta-annotation registers it)

```java
// in mocapi-apps-api
@McpResource(mimeType = "text/html;profile=mcp-app")   // meta-annotation
@Retention(RUNTIME) @Target(METHOD)
public @interface McpAppResource {
  @AliasFor(annotation = McpResource.class, attribute = "uri")  String uri();
  @AliasFor(annotation = McpResource.class, attribute = "name") String name() default "";
  Csp csp() default @Csp;
  String[] sandbox() default {};
}

// author code
@McpAppResource(uri = "ui://weather/dashboard",
    csp = @Csp(connect = "https://api.weather.com"))
public String dashboard() { return html; }   // returns the HTML; classpath-asset helper optional
```

Core discovers the method **as a `@McpResource`** (mimeType defaulted, `uri` aliased through);
the `ResourceDescriptorCustomizer` reads `csp`/`sandbox` and writes the resource's `_meta.ui`.

### 6.2 Tool ↔ UI link (companion annotation, no discovery change)

```java
@McpTool(name = "get_weather", description = "…")
@McpUi("ui://weather/dashboard")                      // optional: visibility = {MODEL, APP}
public WeatherResult getWeather(Args a) { … }
```

The tool is already discovered via `@McpTool`; the `ToolDescriptorCustomizer` reads `@McpUi`
and writes `_meta.ui.resourceUri` (+ `visibility`). The per-call data the UI renders rides the
**normal `CallToolResult`** (`structuredContent`) — no context object needed.

### 6.3 What we deliberately did **not** add

A call-time `UiContext` injectable. The tool↔UI link is *static descriptor metadata* surfaced
in `tools/list` before any invocation, which a call-time context structurally cannot supply.
The only dynamic case — a per-response `_meta.ui` override — is draft-only and deferred; if a
real use case appears, a `UiContext` (or result-`_meta` support) can be added later without
disturbing this design.

## 7. Capability declaration

`UiCapabilityCustomizer` (a `ServerCapabilitiesCustomizer`) adds:

```json
"extensions": { "io.modelcontextprotocol/ui": { "mimeTypes": ["text/html;profile=mcp-app"] } }
```

Always emitted when `mocapi-apps` is present; non-Apps hosts ignore it.

## 8. Flows

### 8.1 Discovery
Host calls `server/discover` → sees the `io.modelcontextprotocol/ui` capability. `resources/list`
includes the `ui://` resources with their `_meta.ui` (CSP/sandbox). `tools/list` includes tools
carrying `_meta.ui.resourceUri`.

### 8.2 Render
Model calls `get_weather` → mocapi returns a normal `CallToolResult`. Because the tool
descriptor carried `_meta.ui.resourceUri`, the host fetches that `ui://` resource via
`resources/read`, renders it in a sandboxed iframe (CSP/sandbox per the resource's `_meta.ui`),
and hands it the result data. All post-`resources/read` steps are host/iframe.

### 8.3 App calls a server tool
"Refresh" in the app → *App → Host → Server* → arrives at mocapi as an ordinary `tools/call`.
No new server code.

## 9. Wire contract

```jsonc
// tools/list entry
{ "name":"get_weather", "description":"…", "inputSchema":{…},
  "_meta": { "ui": { "resourceUri":"ui://weather/dashboard", "visibility":["model","app"] } } }

// resources/list entry (static default)
{ "uri":"ui://weather/dashboard", "name":"Weather Dashboard",
  "mimeType":"text/html;profile=mcp-app",
  "_meta": { "ui": { "csp": { "connectDomains":["https://api.weather.com"] } } } }

// resources/read content
{ "uri":"ui://weather/dashboard", "mimeType":"text/html;profile=mcp-app",
  "text":"<!doctype html>…" }

// server/discover capabilities (excerpt)
{ "capabilities": { "extensions": {
    "io.modelcontextprotocol/ui": { "mimeTypes":["text/html;profile=mcp-app"] } } } }
```

## 10. ADR & design-doc obligations (during implementation)

Architecturally significant (new module, new declared capability, new SPI seams, a
handler-discovery change). Implementation must produce:

- **ADR (module + capability):** introduce `mocapi-apps`; Apps support; the `ui` capability.
- **ADR (descriptor `_meta`):** `_meta` on `Tool`/`Resource`/content items; the
  `ToolDescriptorCustomizer` / `ResourceDescriptorCustomizer` seams.
- ~~ADR (meta-annotation discovery)~~ — **done: [ADR-0032](../../adr/0032-meta-annotation-aware-handler-discovery.md).**
- ~~ADR (`ServerCapabilitiesCustomizer`)~~ — **done: [ADR-0031](../../adr/0031-server-capabilities-customizer.md).**
- **ADR-0022 update:** flip the "apps declined" line item; note the JS-bridge / postMessage
  layer remains out of scope by design.
- **Design docs:** update `docs/design/handlers.md` (meta-annotation discovery, descriptor
  customizers, `@McpUi`/`@McpAppResource`) and the resources design doc; add an Apps design
  doc; index in `docs/adr/README.md`.
- **Guide:** an Apps user guide (declare a UI, link a tool, CSP/sandbox), explicitly pointing
  authors at the official JS SDK for the in-iframe side.

## 11. Testing strategy

- Unit: `ToolDescriptorCustomizer`/`ResourceDescriptorCustomizer` (correct `_meta.ui`);
  meta-annotation discovery (`@McpAppResource` discovered as a resource, `uri`/`mimeType`
  merged correctly); `UiCapabilityCustomizer`.
- Integration (full context): a `@McpAppResource` + `@McpUi` app → assert `tools/list` carries
  `_meta.ui.resourceUri`, `resources/list`/`resources/read` serve the `ui://` HTML with
  `_meta.ui.csp`, and `server/discover` advertises the `ui` capability.
- Conformance: add to `mocapi-conformance` if/when the ext-apps suite has server-assertable
  scenarios; the ext-apps Playwright/e2e tests are host-side and out of scope for mocapi's
  server tests.

## 12. Decisions locked

1. Scope: server metadata + `ui://` serving ergonomics; **no** JS bridge / postMessage (§2).
2. Baseline: stable `2026-01-26`; forward-compatible with draft.
3. Static annotation-driven linkage: `@McpAppResource` (meta-annotated `@McpResource`) +
   `@McpUi` companion. No `UiContext` in v1 (§6.3).
4. Core touches: `_meta` on descriptors + descriptor `_meta` customizer seams (remaining);
   meta-annotation-aware discovery (ADR-0032, done) + `ServerCapabilitiesCustomizer` (ADR-0031,
   done).
5. `visibility` (`model`/`app`) emitted as metadata only — not enforced by mocapi.
6. Always emit `_meta.ui` + capability (no conditional registration); non-Apps hosts ignore.
