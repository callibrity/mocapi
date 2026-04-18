# Mocapi Stdio Example

A minimal mocapi server that speaks MCP over **stdio** — newline-delimited
JSON-RPC on stdin/stdout, with logging on stderr. Exposes a single `echo`
tool and is ready to be launched as a subprocess by any MCP stdio client
(Claude Desktop, Cursor, the reference `@modelcontextprotocol/inspector`, etc.).

## How stdio works in MCP

You don't start the server yourself and connect to it. The **client owns
the lifecycle**: it reads its config, spawns the server as a subprocess,
writes JSON-RPC requests to the subprocess's stdin, and reads responses
from the subprocess's stdout. When the client shuts down, it closes stdin;
the server sees EOF and exits.

```
┌─────────────┐    stdin  (JSON-RPC requests)    ┌──────────────┐
│             │──────────────────────────────────>│              │
│  MCP Client │                                   │  This server │
│  (Claude    │<──────────────────────────────────│  (subprocess)│
│  Desktop…)  │    stdout (JSON-RPC responses)    │              │
└─────────────┘                                   └──────────────┘
                                stderr (logs) ────────>  terminal
```

## Build

```bash
mvn -pl examples/stdio -am package
```

That produces a runnable Spring Boot jar at
`examples/stdio/target/mocapi-example-stdio-0.6.0-SNAPSHOT.jar`.

## Try it by hand (curl-style)

Pipe a single JSON-RPC line on stdin and watch stdout. First initialize
the session; subsequent requests need to run in the same process, which
is why the one-liner below sends both lines before stdin closes:

```bash
(
  printf '%s\n' '{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"shell","version":"1"}}}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"tools/list","id":2}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"tools/call","id":3,"params":{"name":"echo","arguments":{"message":"hello"}}}'
) | java -jar examples/stdio/target/mocapi-example-stdio-0.6.0-SNAPSHOT.jar
```

You'll get four JSON responses on stdout, one per line. Logs appear on
stderr and are not interleaved with the protocol.

## Wire up a client

### Claude Desktop

Edit `~/Library/Application Support/Claude/claude_desktop_config.json`
(macOS) / `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "mocapi-echo": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mocapi/examples/stdio/target/mocapi-example-stdio-0.6.0-SNAPSHOT.jar"
      ]
    }
  }
}
```

Restart Claude Desktop. Ask it to call the `echo` tool and it will spawn
this server as a subprocess and use it.

### MCP Inspector

The official `@modelcontextprotocol/inspector` tool can launch a stdio
server for interactive poking:

```bash
npx @modelcontextprotocol/inspector java -jar /absolute/path/to/mocapi-example-stdio-0.6.0-SNAPSHOT.jar
```

## Adding your own tools

Drop a `@ToolService` class next to `EchoTool` and register any Spring
bean it needs. `McpTransport.CURRENT` is bound inside handler threads,
so elicitation and sampling work over stdio just like they do over HTTP.

## Why stderr-only logging?

Anything written to stdout on this server is interpreted by the client as
MCP protocol traffic. Stray `System.out.println` calls — or a console
logger pointed at stdout — will corrupt the JSON stream. The supplied
`logback-spring.xml` routes every log event to stderr and the YAML config
disables the Spring banner.

## Building a native binary (optional)

The project supports GraalVM native-image. From the repo root:

```bash
mvn -pl examples/stdio -Pnative -am native:compile
```

The resulting native executable starts in tens of milliseconds and can
be used as `"command"` in the Claude Desktop config directly, with no
`java -jar` wrapper.
