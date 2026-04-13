# Configuration Reference

All Mocapi properties use the `mocapi.*` prefix. Configure them in `application.properties` or `application.yml`.

## Server Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.server-name` | `${spring.application.name:mocapi}` | Server name reported in the `InitializeResult`. Defaults to your Spring application name. |
| `mocapi.server-title` | `Callibrity Mocapi MCP Server` | Human-readable server title. |
| `mocapi.server-version` | `unknown` | Server version. Overridden automatically if Spring Boot's `BuildProperties` are available (via `spring-boot-maven-plugin` build-info goal). |
| `mocapi.instructions` | (none) | Optional instructions string included in the `InitializeResult`. Provides guidance to the LLM about how to use this server's tools. |

## Session Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.session-timeout` | `PT1H` | Session TTL as an ISO 8601 duration. Sessions are refreshed on each access. Expired sessions are removed from the store. |
| `mocapi.session-encryption-master-key` | (required) | Base64-encoded 32-byte AES-256 key for encrypting SSE event IDs. Must be set explicitly for production deployments. The example applications generate an ephemeral key on startup. |

## Transport Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.endpoint` | `/mcp` | The HTTP endpoint path for the MCP Streamable HTTP transport. |
| `mocapi.allowed-origins` | `localhost,127.0.0.1,[::1]` | Comma-separated list of allowed Origin header hostnames. Requests with an `Origin` header whose hostname is not in this list are rejected with HTTP 403. Requests without an `Origin` header are accepted. |

## Elicitation Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.elicitation.timeout` | `PT5M` | Maximum time to wait for a client response to an `elicitation/create` request. If exceeded, the server sends `notifications/cancelled` and the tool receives an error. |

## Sampling Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mocapi.sampling.timeout` | `PT30S` | Maximum time to wait for a client response to a `sampling/createMessage` request. If exceeded, the server sends `notifications/cancelled` and the tool receives an error. |

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

# Session management
mocapi.session-timeout=PT30M
mocapi.session-encryption-master-key=BASE64_ENCODED_32_BYTE_KEY

# Transport
mocapi.endpoint=/api/mcp
mocapi.allowed-origins=myapp.example.com,localhost

# Timeouts
mocapi.elicitation.timeout=PT2M
mocapi.sampling.timeout=PT1M

# Pagination
mocapi.pagination.page-size=25
```

## Backend Configuration

Mocapi uses [Substrate](https://github.com/jwcarman/substrate) for session storage, mailbox-based request/response correlation, and SSE event journaling. By default, Substrate uses in-memory implementations suitable for single-node development.

For production multi-node deployments, add a Substrate backend to your classpath:

### Redis

```xml
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Configure Redis as you normally would for Spring Boot:

```properties
spring.data.redis.host=redis.example.com
spring.data.redis.port=6379
```

### PostgreSQL

```xml
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

### Hazelcast

```xml
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-hazelcast</artifactId>
</dependency>
```

Substrate's auto-configuration detects the backend on the classpath and configures the appropriate implementations of `AtomFactory`, `MailboxFactory`, `JournalFactory`, and `NotifierFactory`. No Mocapi-specific backend configuration is needed.

See the [examples](../examples/) directory for working configurations with each backend.
