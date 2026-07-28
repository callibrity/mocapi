# Configuration Reference

All Mocapi properties use the `mocapi.*` prefix. Configure them in `application.properties` or `application.yml`.

Mocapi is stateless under MCP 2026-07-28: there are no sessions, no session store, and no session-related configuration. Every request is self-contained.

## Server Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.server-name` | `${spring.application.name:mocapi}` | Server name reported in the `server/discover` result. Defaults to your Spring application name. |
| `mocapi.server-title` | `Callibrity Mocapi MCP Server` | Human-readable server title. |
| `mocapi.server-version` | `unknown` | Server version. Overridden automatically if Spring Boot's `BuildProperties` are available (via `spring-boot-maven-plugin` build-info goal). |
| `mocapi.instructions` | (none) | Optional instructions string included in the `server/discover` result. Provides guidance to the LLM about how to use this server's tools. |
| `mocapi.emit-server-info` | `true` | Emit `io.modelcontextprotocol/serverInfo` in every response's `_meta` (MCP 2026-07-28 SHOULD). Set `false` to disable. |

## Transport Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.endpoint` | `/mcp` | The HTTP endpoint path for the MCP Streamable HTTP transport. POST-only — `GET` and `DELETE` return `405 Method Not Allowed`. |
| `mocapi.allowed-origins` | `localhost,127.0.0.1,[::1]` | Comma-separated list of allowed Origin header hostnames (DNS-rebinding protection). Requests with an `Origin` header whose hostname is not in this list are rejected with HTTP 403. Requests without an `Origin` header are accepted. |
| `mocapi.stdio.enabled` | `false` | Enables the stdio transport. Set to `true` when an MCP client launches the server as a subprocess (Claude Desktop, Cursor, MCP Inspector). |

## MRTR Elicitation Properties

Elicitation in MCP 2026-07-28 is a multi round-trip request (MRTR): the server returns an `input_required` result carrying an opaque `requestState` token, and the client retries the call with the answers attached. The token is encrypted and signed; these properties control it (see [Interactive Tools](interactive-tools.md) and ADR-0021).

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.mrtr.secret` | (empty) | Base64-encoded 256-bit key used to encrypt and sign `requestState` tokens. **Required for production and multi-instance deployments.** When empty, an ephemeral key is generated at startup — in-flight elicitations will not survive a restart, and other instances cannot decode the tokens. |
| `mocapi.mrtr.ttl` | `PT5M` | How long a client has to complete one elicitation round trip before the `requestState` token expires (ISO 8601 duration). An expired or tampered token is rejected with `-32602`. |

## Cache Properties

The 2026-07-28 spec requires cache directives (`ttlMs` / `cacheScope`) on the six cacheable results. These properties control what mocapi stamps onto them. The defaults (`PT0S` + `private`) mean "don't cache" — the conservative choice.

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.cache.list-ttl` | `PT0S` | TTL stamped onto the four list results (`tools/list`, `prompts/list`, `resources/list`, `resources/templates/list`) and `server/discover` (ISO 8601 duration). |
| `mocapi.cache.read-ttl` | `PT0S` | TTL stamped onto `resources/read` results (ISO 8601 duration). |
| `mocapi.cache.scope` | `private` | Cache scope for all cacheable results: `private` (per-client caches only) or `public` (shared caches allowed). |

## Pagination Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.pagination.page-size` | `50` | Number of items per page for `tools/list`, `prompts/list`, `resources/list`, and `resources/templates/list`. |

## Example Configuration

```properties
# Server identity
mocapi.server-name=my-mcp-server
mocapi.server-title=My MCP Server
mocapi.instructions=This server provides weather and calendar tools.

# Transport
mocapi.endpoint=/api/mcp
mocapi.allowed-origins=myapp.example.com,localhost

# MRTR elicitation (see "Generating the MRTR secret" below)
mocapi.mrtr.secret=BASE64_ENCODED_32_BYTE_KEY
mocapi.mrtr.ttl=PT2M

# Cache directives
mocapi.cache.list-ttl=PT1M
mocapi.cache.read-ttl=PT30S
mocapi.cache.scope=private

# Pagination
mocapi.pagination.page-size=25
```

## Generating the MRTR Secret

`mocapi.mrtr.secret` must be a base64-encoded 32-byte (256-bit) key. Any of the following produces a suitable value:

**OpenSSL (macOS/Linux):**
```bash
openssl rand -base64 32
```

**Python (any platform with Python 3):**
```bash
python3 -c "import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

**Java (one-liner from a JShell session):**
```java
jshell -s -
var k = new byte[32]; new java.security.SecureRandom().nextBytes(k);
System.out.println(java.util.Base64.getEncoder().encodeToString(k));
/exit
```

Generate the key once per environment and store it in your secret manager (Vault, AWS Secrets Manager, Kubernetes Secret, etc.) -- not in source control. Rotating the key invalidates outstanding `requestState` tokens, so in-flight elicitations at rotation time fail with `-32602` and the client starts the call over -- annoying but harmless, given the short TTL.

## Module-Specific Properties

Some optional modules carry their own property prefixes, documented in their guides:

- `mocapi.oauth2.*` -- OAuth2 resource-server settings; see [Authorization](authorization.md).
- `mocapi.audit.*` -- audit-log options such as `mocapi.audit.hash-arguments`; see [Audit](audit.md).
