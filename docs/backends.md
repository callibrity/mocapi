# Backend Integration

Mocapi uses [Substrate](https://github.com/jwcarman/substrate) as its storage abstraction. Substrate provides four SPIs that Mocapi depends on:

| SPI | Mocapi Usage |
|-----|-------------|
| `AtomFactory` | Session storage (`AtomMcpSessionStore`) |
| `MailboxFactory` | Request/response correlation for elicitation and sampling |
| `JournalFactory` | SSE event journaling for stream resumption |
| `NotifierFactory` | Cross-node event notification |

## Choosing a Backend

By default, Substrate uses in-memory implementations. These work for development and single-node deployments but do not survive restarts or support multiple nodes.

For production, add a Substrate backend module to your classpath. Substrate auto-configures the appropriate SPI implementations.

| Backend | Module | Supports Clustering | Persistence |
|---------|--------|:------------------:|:-----------:|
| In-memory | (default) | No | No |
| Redis | `substrate-redis` | Yes | Optional |
| PostgreSQL | `substrate-jdbc` | Yes | Yes |
| Hazelcast | `substrate-hazelcast` | Yes | Optional |
| DynamoDB | `substrate-dynamodb` | Yes | Yes |

See [Configuration Reference](configuration.md#backend-configuration) for dependency coordinates and setup.

## How It Works

When a client initializes a session, Mocapi creates an Atom in the session store containing the `McpSession` record (session ID, protocol version, client capabilities, log level, initialized flag). Each request refreshes the session's TTL.

When a tool calls `elicit()` or `sample()`, Mocapi creates a Mailbox for the outbound request. The tool's virtual thread blocks on the mailbox. When the client responds, the server delivers the response to the mailbox and the tool unblocks.

SSE events are published to Odyssey (which uses Substrate's Journal). Clients can reconnect using the `Last-Event-ID` header to resume from where they left off.

## Multi-Node Considerations

With a clustered backend (Redis, PostgreSQL, etc.):

- Sessions are shared across nodes -- a client can hit any node
- SSE reconnection works across nodes via Journal replay
- Elicitation/sampling correlation works across nodes via Mailbox
- The `mocapi.session-encryption-master-key` must be the same on all nodes

The in-memory backend does not support multi-node. If you see Substrate's warning about "in-memory fallback (single-node only)", add a backend module to your classpath.
