# Mocapi HTTP Example

A single runnable mocapi app over the stateless streamable-HTTP transport
(MCP protocol `2026-07-28`). No external infrastructure required — just Java
and Maven.

## Prerequisites

- JDK 25
- Maven 3.6+

## Run

```bash
cd examples/http
mvn spring-boot:run
```

The server starts on `http://localhost:8080` with the MCP endpoint at `/mcp`.

## Talk to it

The `2026-07-28` transport is **stateless and POST-only**: there is no
`initialize` handshake and no `Mcp-Session-Id` header. Each request is a
self-contained JSON-RPC POST to `/mcp`; per-call context (protocol version,
client info, etc.) travels in the request's `_meta` envelope.

For the exact request envelope shape, see the streamable-HTTP transport guide
under [`docs/guides`](../../docs/guides) rather than copying a payload from
here — the envelope is versioned and the guides are kept current.

A `tools/call` request looks roughly like:

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": { "name": "hello", "arguments": { "name": "World" } }
  }'
```

## What's included

Tools:

- **hello** — returns a greeting for the given name
- **hello-elicitation** — greets after eliciting first/last name from the client
- **rot13** — applies ROT13 encoding to a string
- **countdown** — streams a countdown with progress notifications
- **greet** — Jakarta Bean Validation on a tool parameter (`name` must be
  2–60 non-blank chars; violations come back as a tool error result)

Resources:

- **docs** resources — static example resources
- **config://{env}/app** resource template — Jakarta Bean Validation on a URI
  template variable (`env` must be lowercase letters; violations come back as
  JSON-RPC `-32602`)

Prompts:

- **summarize** — a template-backed prompt

## Notes

This example generates an **ephemeral** session-encryption master key on
startup (see `EphemeralMasterKeyEnvironmentPostProcessor`). It is for local
demos only. Set `MOCAPI_SESSION_ENCRYPTION_MASTER_KEY` to a secure 32+ byte
value in any non-demo deployment.

**Not for production use.**
