# MCP Apps

How mocapi implements the MCP Apps extension (`io.modelcontextprotocol/ui`,
SEP-1865, `modelcontextprotocol/ext-apps`) — the `mocapi-apps` module, the
`_meta.ui` shapes it writes, and the explicit boundary where mocapi stops.

For decisions, see:

- [ADR-0033](../adr/0033-mcp-apps-module-and-ui-capability.md) — the
  `mocapi-apps` module and the `io.modelcontextprotocol/ui` capability
- [ADR-0034](../adr/0034-descriptor-meta-and-customizer-seams.md) —
  `_meta` on `Tool`/`Resource` and the original descriptor-customizer
  seams Apps was built on *(amended by ADR-0039 — see below)*
- [ADR-0039](../adr/0039-extension-seam-taxonomy-and-dispatch-interception.md) —
  folds the descriptor-customizer seam into the four `*HandlerCustomizer`
  SPIs; Apps' two customizers are renamed accordingly
- [ADR-0032](../adr/0032-meta-annotation-aware-handler-discovery.md) —
  meta-annotation-aware handler discovery, which lets `@McpAppResource`
  register as a resource with no bespoke SPI
- [ADR-0031](../adr/0031-server-capabilities-customizer.md) —
  `ServerCapabilitiesCustomizer`, which `UiCapabilityCustomizer` uses to
  declare the `ui` extension capability
- [ADR-0035](../adr/0035-resource-readers-and-contributor-seam.md) —
  function-backed resource readers and the `ResourceContributor` seam
  that serve-mode plugs into
- [ADR-0036](../adr/0036-mcpui-serve-mode.md) — `@McpUi(resource=…)`
  serve-mode: serving a `ui://` bundle from a fixed location with no
  resource method

Design history: the original design spec is
[`docs/superpowers/specs/2026-07-31-mcp-apps-extension-design.md`](../superpowers/specs/2026-07-31-mcp-apps-extension-design.md).
That spec proposed splitting the module into `mocapi-apps-api` +
`mocapi-apps`; the implementation collapsed that into a single
`mocapi-apps` module (annotations, `_meta.ui` records, customizers, and
`MocapiAppsAutoConfiguration` all together) — this doc describes what
shipped, not the original proposal.

## Scope boundary

MCP Apps splits into two halves. mocapi implements only the first:

- **Server surface (in scope):** declare `ui://` HTML resources, stamp
  `_meta.ui` on tool and resource descriptors to link them, declare the
  `io.modelcontextprotocol/ui` capability. All of this is static,
  descriptor-time metadata — no new JSON-RPC methods, no runtime state.
- **Host / in-iframe JS surface (out of scope):** the sandbox handshake
  (`ui/initialize`), the `postMessage` JSON-RPC bridge,
  `ui/notifications/sandbox-proxy-ready` / `sandbox-resource-ready`,
  display-mode negotiation, app-registered tools, and
  `sampling/createMessage` over `postMessage`. mocapi serves the
  author's HTML bytes content-agnostically; when the in-iframe app calls
  a server tool, it arrives at mocapi as an ordinary `tools/call` — no
  Apps-specific server code is involved. **mocapi ships no `postMessage`
  / iframe / JS-bridge code of any kind, and never will under this
  design** — see ADR-0033's non-goals.

This mirrors the split in `docs/adr/0022-2026-07-28-features-not-implemented.md`:
ADR-0033 flips that entry from declined to accepted-and-implemented,
specifically for the server half.

## Module layout

`mocapi-apps` depends on `mocapi-api` (for the `@McpResource`/`@McpTool`
meta-annotation targets) and `mocapi-server` (for the descriptor and
capability customizer seams):

```
mocapi-apps
  McpAppResource     — annotation: declares a ui:// resource
  McpUi              — annotation: links a tool to its ui:// resource
  Csp                — annotation: CSP origins for a ui:// resource
  UiResourceMeta      — record: resource _meta.ui shape (csp, sandbox)
  McpUiResourceCsp    — record: connect/resource/frame/baseUri domain lists
  McpUiToolMeta        — record: tool _meta.ui shape (resourceUri, visibility)
  AppsResourceUiMetaCustomizer — reads @McpAppResource → writes Resource._meta.ui (ReadResourceHandlerCustomizer)
  AppsToolUiMetaCustomizer     — reads @McpUi → writes Tool._meta.ui (CallToolHandlerCustomizer)
  UiCapabilityCustomizer       — declares capabilities.extensions["io.modelcontextprotocol/ui"]
```

`MocapiAppsAutoConfiguration` (in `mocapi-autoconfigure`, gated
`@ConditionalOnClass(UiCapabilityCustomizer.class)`) registers the three
customizers as `@ConditionalOnMissingBean` beans whenever `mocapi-apps`
is on the classpath, plus the serve-mode `AppUiResourceContributor` (also
in `mocapi-autoconfigure` — it needs `HandlerMethodsCache` and
`ResourceLoader`, both Spring-side). Omitting the module leaves the core
inert: no descriptor gains `_meta`, and handler discovery behaves
identically for plain `@McpResource`/`@McpTool` methods.

## `_meta.ui` shapes

Apps is the first consumer of the generic descriptor `_meta` seam
(ADR-0034). `Tool` and `Resource` carry an optional `_meta`
(`ObjectNode`, `NON_NULL` — omitted from the wire when no customizer
touches it); Apps writes a `ui` key into it.

### Resource `_meta.ui` — `UiResourceMeta`

Written by `AppsResourceUiMetaCustomizer` when a `@McpResource`
method (or, via meta-annotation, `@McpAppResource`) carries the merged
`@McpAppResource` annotation:

```json
{ "ui": { "csp": { "connectDomains": ["https://api.weather.com"] },
          "sandbox": ["allow-scripts"] } }
```

- `csp` (`McpUiResourceCsp`): `connectDomains`, `resourceDomains`,
  `frameDomains`, `baseUriDomains` — the CSP origins the UI needs. The
  server *declares* what's needed; the host enforces it on the iframe.
  `null` (customizer omits the whole `csp` object) when every list on
  `@Csp` is empty.
- `sandbox`: requested iframe sandbox permissions (raw strings, no enum
  — the spec doesn't fix a closed set). `null` when `@McpAppResource`
  declares no `sandbox` values.

v1 targets the **listing/static form only** — a per-response override
on the `resources/read` content item exists in the draft spec but is
explicitly deferred (§6.3 of the design spec); mocapi has no call-time
`UiContext` to produce one.

### Tool `_meta.ui` — `McpUiToolMeta`

Written by `AppsToolUiMetaCustomizer` when a `@McpTool` method
carries `@McpUi`:

```json
{ "ui": { "resourceUri": "ui://weather/dashboard",
          "visibility": ["model", "app"] } }
```

- `resourceUri`: the linked `ui://` resource.
- `visibility`: `["model", "app"]` by default. This is the MCP Apps UI
  **access axis** (should the model see this tool, should the app UI
  see it, or both) — a *different* concept from the auth-Guard
  `visibility ≡ invocation` model in
  [`authorization-model.md`](authorization-model.md) /
  [ADR-0012](../adr/0012-guard-spi.md). mocapi emits it as metadata
  only; it is not enforced server-side. A host is responsible for
  acting on it.

### Capability declaration

`UiCapabilityCustomizer` implements `ServerCapabilitiesCustomizer`
([ADR-0031](../adr/0031-server-capabilities-customizer.md)) and
unconditionally adds:

```json
{ "capabilities": { "extensions": {
    "io.modelcontextprotocol/ui": { "mimeTypes": ["text/html;profile=mcp-app"] } } } }
```

mocapi is stateless (`server/discover`, no `initialize` handshake), so
there's nothing to gate registration on — the capability and `_meta.ui`
are always emitted. A non-Apps host sees an unrecognized `extensions`
entry and unrecognized `_meta.ui` fields and ignores both, per the
spec's text-only fallback.

## Author-facing API

### `@McpAppResource` — declare a `ui://` resource

`@McpAppResource` is a meta-annotation over `@McpResource`
(`mimeType` defaulted to `text/html;profile=mcp-app`; `uri`/`name`
aliased through via `@AliasFor`). Meta-annotation-aware discovery
(ADR-0032) means the method registers exactly like a hand-written
`@McpResource` — there is no separate Apps registration path:

```java
@McpAppResource(
    uri = "ui://weather/dashboard",
    name = "Weather",
    csp = @Csp(connect = "https://api.weather.com"))
public ReadResourceResult dashboard() {
  return ReadResourceResult.ofText(
      "ui://weather/dashboard", "text/html;profile=mcp-app", html);
}
```

Like every `@McpResource` handler, the method returns a
`ReadResourceResult` or one of the convenience return types
(`String`/`CharSequence`, `byte[]`/`ByteBuffer`, Spring `Resource`;
ADR-0035) — `ReadResourceHandlers.validateReturnType` accepts any of
these at startup regardless of which annotation registered the method.

`AppsResourceUiMetaCustomizer` runs after the `Resource` descriptor
is otherwise built and reads `csp()`/`sandbox()` off the merged
annotation (via `AnnotatedElementUtils.findMergedAnnotation`) to write
`_meta.ui`.

### `@McpUi` — link a tool to its UI resource

A companion annotation on an existing `@McpTool` method — it changes no
discovery behavior, only descriptor metadata:

```java
@McpTool(name = "get_weather", description = "Get weather")
@McpUi("ui://weather/dashboard")
public WeatherResult getWeather(Args a) { … }
```

`AppsToolUiMetaCustomizer` reads `@McpUi` and writes
`_meta.ui.resourceUri` (+ `visibility`). The per-call data the UI
renders rides the **normal** `CallToolResult` (typically
`structuredContent`) — no special context object is involved.

By default the linked `value()` URI must be declared elsewhere on the
server; `McpUiReferenceValidator` (a `SmartInitializingSingleton`) fails
the boot if a `@McpUi` points at a URI no handler declares — turning a
fat-fingered link into a clear startup error instead of a blank iframe.

### `@McpUi(resource=…)` — serve-mode (ADR-0036)

Setting `@McpUi.resource` to a fixed location makes the resource method
optional: `AppUiResourceContributor` scans every `@McpUi(resource=…)`
tool and contributes, through the generic ADR-0035 `ResourceContributor`
seam, a **public, reader-only** `text/html;profile=mcp-app` resource at
`value()` serving the bundle resolved once at startup via Spring
`ResourceLoader`:

```java
@McpTool(name = "get_weather", description = "Get weather")
@McpUi(value = "ui://weather/dashboard",
       resource = "classpath:/ui/weather-dashboard.html")
public WeatherResult getWeather(Args a) { … }
```

The URI is logical and author-controlled; the served bytes come only
from the literal `resource` string — never request input, so there is no
path-traversal / LFI surface (a `ui://{path}` catch-all was rejected for
exactly this reason). Several tools may reuse one `value()`; the
contributor registers one resource per URI, and the same `value()` with
two different locations — or a missing bundle — fails the boot. Serve-mode
resources carry no guards, no observability, and a default `_meta.ui`
(default CSP, no extra sandbox); anything needing policy, custom
`@Csp`/sandbox, or generated content uses an `@McpAppResource` method and
leaves `resource` blank. Because the contributed URI is now declared,
`McpUiReferenceValidator` is satisfied without a hand-written resource.

### What was deliberately not added

No call-time `UiContext` injectable. The tool↔UI link is *static
descriptor metadata* surfaced in `tools/list` before any invocation,
which a call-time context structurally cannot supply. The only dynamic
case in the spec — a per-response `_meta.ui` override on
`resources/read` content — is draft-only and out of scope for v1; see
ADR-0033's "Consequences" section for the forward-compatibility note.

## Descriptor-customizer seam recap

Apps was the first consumer of ADR-0034's generic descriptor-`_meta`
seam. ADR-0039 folded that seam's originally-standalone descriptor
customizer interfaces into the existing per-handler-kind customizers
(see [ADR-0034](../adr/0034-descriptor-meta-and-customizer-seams.md)'s
amendment note for the prior shape), so `AppsToolUiMetaCustomizer` and
`AppsResourceUiMetaCustomizer` are ordinary `CallToolHandlerCustomizer`
/ `ReadResourceHandlerCustomizer` beans that happen to call the
`descriptor(T)` mutator:

```java
public class AppsToolUiMetaCustomizer implements CallToolHandlerCustomizer {
  @Override
  public void customize(CallToolHandlerConfig config) {
    // ... build McpUiToolMeta from @McpUi ...
    Tool descriptor = config.descriptor();
    config.descriptor(descriptor.withMeta(meta));
  }
}
```

`CallToolHandlers.build` and `ReadResourceHandlers.build` run every
registered `*HandlerCustomizer` over the generated descriptor at the same
build-pipeline point the old descriptor-only customizers ran; each
customizer reads `config.method()` for its annotations and calls
`config.descriptor(T)` to fold in `_meta`. Core has no knowledge of what
"ui" means — it only offers "run every registered customizer over this
handler before it's published." See [Extension SPI](extension-spi.md),
[Handlers](handlers.md), and the [Extending mocapi
guide](../guides/extending-mocapi.md#3-calltoolhandlercustomizer--3-siblings--mutate-a-handlers-descriptor-or-strata)
for the customizer pattern in general.

## Flows

1. **Discovery.** Host calls `server/discover`, sees the
   `io.modelcontextprotocol/ui` capability. `resources/list` includes
   `ui://` resources with their `_meta.ui` (CSP/sandbox). `tools/list`
   includes tools carrying `_meta.ui.resourceUri`.
2. **Render.** Model calls a tool → mocapi returns a normal
   `CallToolResult`. Because the tool descriptor carried
   `_meta.ui.resourceUri`, the host fetches that `ui://` resource via
   `resources/read` and renders it in a sandboxed iframe per the
   resource's declared CSP/sandbox. Everything from here on is
   host/iframe — mocapi's involvement ends at serving the HTML bytes.
3. **App calls a server tool.** An action inside the rendered app
   (e.g. "Refresh") goes *App → Host → Server*, arriving at mocapi as
   an ordinary `tools/call`. No Apps-specific server code runs.

## Testing

- `AppsUiMetaCustomizerTest` — unit coverage of
  `AppsToolUiMetaCustomizer` / `AppsResourceUiMetaCustomizer`
  producing correct `_meta.ui`.
- `AnnotationContractTest` — the `@AliasFor`/meta-annotation contract
  on `@McpAppResource` (uri/mimeType merge correctly).
- `UiMetaSerializationTest` — wire-shape serialization of
  `UiResourceMeta` / `McpUiToolMeta` / `McpUiResourceCsp`.
- `AppsEndToEndTest` (in `mocapi-autoconfigure`) — a real Spring context
  wiring `MocapiServerToolsAutoConfiguration`,
  `MocapiServerResourcesAutoConfiguration`, `MocapiServerAutoConfiguration`,
  and `MocapiAppsAutoConfiguration`, asserting `tools/list`,
  `resources/list`, and `server/discover` all carry the expected Apps
  metadata.
- `AppUiServeModeTest` (in `mocapi-autoconfigure`) — serve-mode: a
  `@McpUi(resource=classpath:…)` tool boots with the `ui://` resource
  contributed and served from the classpath, and the same URI from two
  locations fails the boot.

There is no Apps-specific conformance-suite coverage: the
`ext-apps` Playwright/e2e suite tests the host/iframe handshake, which
is out of scope for mocapi's server.
