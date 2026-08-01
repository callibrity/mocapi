# ADR-0036 — `@McpUi(resource=…)` serve-mode for UI bundles

- **Status:** Accepted
- **Date:** 2026-08-01

## Context

MCP Apps ([ADR-0033](0033-mcp-apps-module-and-ui-capability.md)) links a
tool to a `ui://` HTML bundle: the tool carries `@McpUi("ui://…")`, and
the bundle is served by a resource declared with the same URI. Today that
resource must be a hand-written `@McpAppResource` method that loads the
bundle and returns it — the exact boilerplate ADR-0035 set out to remove:

```java
@McpAppResource(uri = "ui://get-time/mcp-app.html")
public ReadResourceResult bundle() throws IOException {
  var html = new ClassPathResource("ui/get-time/mcp-app.html")
      .getContentAsString(UTF_8);
  return ReadResourceResult.ofText("ui://get-time/mcp-app.html",
      "text/html;profile=mcp-app", html);
}
```

Two declarations (the `@McpUi` link and the `@McpAppResource` method) must
agree on the URI by hand, and every app author re-writes the same loader.
[ADR-0035](0035-resource-readers-and-contributor-seam.md) built exactly the
seam needed to collapse this — reader-backed handlers plus a
`ResourceContributor` — and explicitly deferred this serve-mode as "a
separate decision built on this."

The hard constraint carried over from ADR-0035 is that the `ui://` URI is
**logical and author-controlled**; the served location is a **fixed,
author-specified** classpath/filesystem path, never derived from client
input. A client-supplied path resolving to a file is path-traversal / local
file inclusion and stays rejected.

## Decision

Add an optional `resource` attribute to `@McpUi`. When set, mocapi serves
that bundle for the annotation's `ui://` URI; the author writes no resource
method.

```java
@McpTool(name = "get-time")
@McpUi(value = "ui://get-time/mcp-app.html",
       resource = "classpath:/ui/get-time/mcp-app.html")
public TimeResult getTime() { … }
```

Concrete rules:

1. **Two modes, by presence of `resource`.**
   - `resource` **blank** (unchanged): the author declares the bundle
     elsewhere (`@McpAppResource` / `@McpResource`) with the same URI;
     `McpUiReferenceValidator` fails fast at boot if none matches.
   - `resource` **set**: an Apps `ResourceContributor` contributes a
     reader-only, **public** `ReadResourceHandler` at the `value()` URI that
     serves the bundle, resolved once via Spring `ResourceLoader` at startup.
     A missing/unreadable location fails the boot with a clear error.
2. **Logical URI, fixed location.** `value()` is the wire identity; the
   served bytes come only from the literal `resource` location. Neither is
   ever built from request input. No `ui://{path}` catch-all.
3. **MIME + `_meta.ui`.** The contributed resource is
   `text/html;profile=mcp-app` and carries a default `_meta.ui` (default
   CSP, no extra sandbox) — parity with a default `@McpAppResource`.
4. **Reuse by many tools is fine; conflicts fail fast.** Several tools may
   `@McpUi` the same `value()`; the contributor registers **one** handler
   per URI. Two tools naming the same `value()` with **different** `resource`
   locations is a configuration error and fails the boot. A serve-mode URI
   that also collides with a hand-declared resource URI fails fast via the
   service's existing duplicate-URI check.
5. **Lightweight, public path — escape hatch unchanged.** Serve-mode
   resources carry no guards and no observability (ADR-0035's model:
   contributed readers are the bare, public path). An author who needs
   guards, o11y, custom CSP/sandbox, or generated content declares an
   `@McpAppResource` method instead and leaves `resource` blank.

Layering: `@McpUi.resource` lives in `mocapi-apps`; the Apps
`ResourceContributor` (reusing `HandlerMethodsCache` and `ResourceLoader`)
lives in the apps autoconfiguration and contributes through the generic
ADR-0035 seam. No Apps concept crosses the core resources line.

## Consequences

App authors get a working UI bundle from two lines on the tool — one
annotation, no loader method, no duplicated URI. The `@McpAppResource`
method remains the full-control form and the sole path for guarded, observed,
or dynamically-generated UI resources, keeping serve-mode deliberately thin.
Because the contributor resolves the bundle at startup, a typo'd path or a
missing file is a boot failure, not a blank iframe at render time — the same
fail-fast posture `McpUiReferenceValidator` already gives dangling links.

Costs: one public attribute on `@McpUi`, one new contributor, and a small
amount of conflict-detection logic. The `@McpUi` docs and the Apps design
doc change with the code.

Non-goals: custom CSP/sandbox on serve-mode (use `@McpAppResource`);
templated UI URIs; serving from a client-influenced location (rejected as
LFI); runtime registration (ADR-0035 rules it out).

**Code anchors:**

- `mocapi-apps/.../McpUi.java` (`resource()` attribute)
- `mocapi-autoconfigure/.../apps/AppUiResourceContributor.java` (scans
  `@McpUi(resource=…)` tools, contributes reader-only handlers) + its
  registration in `MocapiAppsAutoConfiguration`
- `mocapi-server/.../resources/ResourceResults.java` (bundle wrapping) and
  `ResourceContributor` (the ADR-0035 seam it plugs into)
