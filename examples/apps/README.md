# Mocapi Example — MCP Apps (Get Time)

A runnable [MCP Apps](https://github.com/modelcontextprotocol/ext-apps) example built with mocapi.
It serves an interactive `ui://` HTML app linked to a `get-time` tool: in an Apps-capable host, the
app renders in a sandboxed iframe with a **Get Server Time** button that calls the tool and displays
the result.

It demonstrates the [`mocapi-apps`](../../mocapi-apps) module — `@McpUi(resource=…)` serve-mode
(the bundle is served straight from the classpath, no resource method) and the
`io.modelcontextprotocol/ui` capability. The whole server surface is a single `@McpTool` +
`@McpUi` method. See the [Apps guide](../../docs/guides/apps.md) and
[design doc](../../docs/design/apps.md).

## Two halves, one boundary

| Half | Who | In this example |
|------|-----|-----------------|
| Serve the `ui://` HTML + `_meta.ui` + capability | **mocapi (server)** | [`GetTimeApp.java`](src/main/java/com/callibrity/mocapi/examples/apps/GetTimeApp.java) — one tool method |
| Render the iframe, run the `postMessage` / `ui-initialize` bridge | **host + in-iframe JS** | the vendored app bundle (below) |

mocapi is only the server. The interactive layer — the handshake, the button wiring, calling back to
the `get-time` tool — lives in the in-iframe JavaScript, which the **host** runs. mocapi does not ship
that JavaScript; this example vendors it (see Provenance).

## Run it

```bash
# from the repo root — build the runnable jar (and its mocapi dependencies)
mvn -pl examples/apps -am package -DskipTests

# run it (default port 8080; pass --server.port to move it)
java -jar examples/apps/target/mocapi-example-apps-*.jar
```

The server listens on `http://localhost:8080/mcp` (Streamable HTTP). To move it to
another port (e.g. so the ext-apps `basic-host` can keep 8080), pass
`--server.port=3001`.

> `spring-boot:run` works too, but only from *inside* the module — the plugin prefix
> doesn't resolve through the aggregator pom. After `mvn -pl examples/apps -am install
> -DskipTests`, run `cd examples/apps && mvn spring-boot:run`. The `java -jar` route
> above avoids that.

## Try it in an MCP Apps host

Point an Apps-capable MCP host (a desktop client that implements the `io.modelcontextprotocol/ui`
extension) at the Streamable HTTP endpoint:

```json
{
  "mcpServers": {
    "mocapi-get-time": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Invoke the **Get Time** tool. Because its descriptor carries `_meta.ui.resourceUri`, an Apps host
fetches `ui://get-time/mcp-app.html` and renders it; the app's **Get Server Time** button then calls
`get-time` and shows the ISO timestamp. A non-Apps host simply shows the tool's text result — the
`_meta.ui` is ignored (graceful fallback).

## Render it locally with the ext-apps `basic-host`

The most reliable way to *see it render* is the official
[`basic-host`](https://github.com/modelcontextprotocol/ext-apps/tree/main/examples/basic-host)
reference host (a browser app) — independent of any production client:

```bash
# Terminal 1 — run this server on 3001 (basic-host uses 8080/8081 itself)
java -jar examples/apps/target/mocapi-example-apps-*.jar --server.port=3001

# Terminal 2 — the reference host (defaults to http://localhost:3001/mcp)
git clone https://github.com/modelcontextprotocol/ext-apps.git
cd ext-apps && npm install && cd examples/basic-host && npm start
# open http://localhost:8080 → select the server → select "get-time" → Call Tool
```

Because `basic-host` is a **browser** app on `http://localhost:8080`, this server enables CORS for
that origin (see [`CorsConfig.java`](src/main/java/com/callibrity/mocapi/examples/apps/CorsConfig.java)).
Point a different browser host at it with `--mocapi.example.cors-origins=http://host:port`. (This is
a dev convenience; it's separate from — and doesn't weaken — mocapi's Origin allowlist.)

> A `GET /mcp` returning `405 Method Not Allowed` in the network log is expected and harmless: mocapi
> is stateless (ADR-0020) and offers no standalone server-push SSE stream, so clients use POST only.

## See the server side on the wire

No Apps host needed to inspect mocapi's half — use [`mcp-example-requests.http`](mcp-example-requests.http)
(IntelliJ HTTP client) or curl:

```bash
# tools/list — get-time carries _meta.ui.resourceUri
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' -H 'Mcp-Method: tools/list' \
  -d '{"id":1,"jsonrpc":"2.0","method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}'

# resources/read — the ui:// app bundle the host renders
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' -H 'Mcp-Method: resources/read' -H 'Mcp-Name: ui://get-time/mcp-app.html' \
  -d '{"id":1,"jsonrpc":"2.0","method":"resources/read","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"uri":"ui://get-time/mcp-app.html"}}'
```

`server/discover` advertises `capabilities.extensions."io.modelcontextprotocol/ui"` whenever
`mocapi-apps` is on the classpath.

## Provenance of the UI bundle

`src/main/resources/ui/get-time/mcp-app.html` is the **official** MCP Apps
["Get Time" vanilla-JS example](https://github.com/modelcontextprotocol/ext-apps/tree/main/examples/basic-server-vanillajs),
vendored verbatim as a self-contained single-file bundle (HTML + inlined JS/CSS, no external network
loads). It is licensed **MIT** by the Model Context Protocol authors — see the ext-apps repository for
the full license.

It was extracted from the published npm package (which ships the prebuilt bundle):

```bash
npm pack @modelcontextprotocol/server-basic-vanillajs
# then copy package/dist/mcp-app.html into src/main/resources/ui/get-time/
```

To adopt a different or updated app, rebuild/repack from ext-apps and replace that one file; the Java
side is unchanged as long as the app calls a tool named `get-time` that returns `{ "time": "..." }`.
Because the bundle makes no external requests, no CSP (`@Csp`) origins are declared on the resource.
