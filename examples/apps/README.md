# Mocapi Example — MCP Apps (Get Time)

A runnable [MCP Apps](https://github.com/modelcontextprotocol/ext-apps) example built with mocapi.
It serves an interactive `ui://` HTML app linked to a `get-time` tool: in an Apps-capable host, the
app renders in a sandboxed iframe with a **Get Server Time** button that calls the tool and displays
the result.

It demonstrates the [`mocapi-apps`](../../mocapi-apps) module — `@McpAppResource`, `@McpUi`, and the
`io.modelcontextprotocol/ui` capability. See the [Apps guide](../../docs/guides/apps.md) and
[design doc](../../docs/design/apps.md).

## Two halves, one boundary

| Half | Who | In this example |
|------|-----|-----------------|
| Serve the `ui://` HTML + `_meta.ui` + capability | **mocapi (server)** | [`GetTimeApp.java`](src/main/java/com/callibrity/mocapi/examples/apps/GetTimeApp.java) — ~30 lines |
| Render the iframe, run the `postMessage` / `ui-initialize` bridge | **host + in-iframe JS** | the vendored app bundle (below) |

mocapi is only the server. The interactive layer — the handshake, the button wiring, calling back to
the `get-time` tool — lives in the in-iframe JavaScript, which the **host** runs. mocapi does not ship
that JavaScript; this example vendors it (see Provenance).

## Run it

```bash
# from the repo root
mvn -pl examples/apps -am spring-boot:run
# or build a jar:
mvn -pl examples/apps -am package -DskipTests
java -jar examples/apps/target/mocapi-example-apps-*.jar
```

The server listens on `http://localhost:8080/mcp` (Streamable HTTP).

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
