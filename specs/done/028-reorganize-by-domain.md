# Reorganize codebase by domain

## What to build

Reorganize Mocapi's package structure from technical layers to domain-oriented
subpackages. Each package is a cohesive unit around a concept, with `@JsonRpc`
handlers living next to their domain types.

### Target structure for `mocapi-core`

```
com.callibrity.mocapi
├── session/
│   ├── McpSession                    (record: protocolVersion, capabilities, clientInfo)
│   ├── McpSessionStore               (SPI interface)
│   └── ClientCapabilities            (record)
│   ├── ClientInfo                    (record)
│   ├── ElicitationCapability         (record with FormCapability, UrlCapability)
│   ├── SamplingCapability            (record)
│   ├── RootsCapability               (record)
│   ├── TasksCapability               (record)
│   └── Icon                          (record, shared with ServerInfo)
│
├── tools/
│   ├── ToolsRegistry                 (NEW: lookup, list with pagination)
│   ├── McpTool                       (interface)
│   ├── McpToolProvider               (functional interface)
│   ├── Content                       (sealed interface: TextContent, ImageContent, etc.)
│   ├── CallToolResponse              (record)
│   ├── ListToolsResponse             (record)
│   ├── annotation/
│   │   ├── Tool                      (method annotation)
│   │   ├── ToolService               (stereotype annotation)
│   │   ├── AnnotationMcpTool         (implementation)
│   │   ├── AnnotationMcpToolProviderFactory (interface)
│   │   └── DefaultAnnotationMcpToolProviderFactory (implementation)
│   └── schema/
│       ├── MethodSchemaGenerator     (interface)
│       └── DefaultMethodSchemaGenerator (implementation)
│
├── stream/
│   ├── McpStreamContext              (interface: elicitForm, sendProgress, log)
│   ├── ElicitationResult<T>         (record)
│   ├── ElicitationAction            (enum)
│   ├── McpElicitationException      (exception)
│   ├── McpElicitationTimeoutException (exception)
│   └── McpElicitationNotSupportedException (exception)
│
├── server/
│   ├── McpServer                     (builds InitializeResponse, holds ServerInfo)
│   ├── ServerInfo                    (record)
│   ├── McpServerCapability           (interface)
│   └── CapabilityDescriptor          (marker interface)
│
└── security/
    └── McpEventIdCodec               (AES-256-GCM encrypted event IDs)
    └── McpEventIdCodecException      (exception)
```

### Target structure for `mocapi-autoconfigure`

```
com.callibrity.mocapi.autoconfigure
├── MocapiAutoConfiguration           (wires core beans)
├── MocapiProperties                  (top-level config)
│
├── session/
│   ├── InMemoryMcpSessionStore       (default in-memory implementation)
│   └── McpSessionMethods             (NEW: @JsonRpcService for initialize,
│                                      notifications/initialized, ping)
│
├── tools/
│   ├── McpToolMethods                (@JsonRpcService for tools/list, tools/call)
│   ├── MocapiToolsAutoConfiguration  (tool provider/factory beans)
│   ├── MocapiToolsProperties         (tools config)
│   └── ToolServiceMcpToolProvider    (discovers @ToolService beans)
│
├── stream/
│   ├── DefaultMcpStreamContext       (Odyssey + Substrate implementation)
│   └── McpStreamContextParamResolver (ScopedValue-based resolver)
│
└── http/
    └── StreamableHttpController      (thin HTTP adapter, transport only)
```

### Create `ToolsRegistry`

New class in `mocapi-core/tools/`:

```java
public class ToolsRegistry {
    private final Map<String, McpTool> tools;
    private final List<McpTool> sortedTools;

    public ToolsRegistry(List<McpToolProvider> providers) { ... }

    public McpTool lookup(String name);  // throws if not found
    public ListToolsResponse listTools(String cursor, int pageSize);
}
```

Built from all `McpToolProvider` beans. Owns the sorted list, pagination logic
(cursor encoding/decoding), and name-based lookup. The `tools/list` and `tools/call`
`@JsonRpc` handlers delegate to it.

Move pagination logic (cursor encode/decode) from `McpToolsCapability` into
`ToolsRegistry`. Delete `McpToolsCapability` — its responsibilities are split between
`ToolsRegistry` (lookup, list, pagination) and `McpToolMethods` (JSON-RPC handlers).

### Move `McpSessionMethods` into `session` package

The `initialize`, `notifications/initialized`, and `ping` handlers belong with
session types. Currently `McpServerMethods` in the autoconfigure root. Rename to
`McpSessionMethods` and move to `autoconfigure/session/`.

The initialize handler uses the cached `InitializeResponse` from `McpServer` and
doesn't need method parameters (RipCurl doesn't invoke it with params since it's
cached). The controller handles session creation from the request params separately.

### Delete dead code

- **`JsonMethodInvoker`** in `mocapi-core/server/invoke/` — rename to
  `ToolMethodInvoker` and move to the `tools` package where it's used. It can
  wrap RipCurl's `JsonMethodInvoker` internally for JSON parameter resolution
  while adding tool-specific concerns (input schema validation, result wrapping).
  Delete the `server/invoke/` package.
- **`McpRequestValidator`** — check if still needed. JSON-RPC envelope validation is
  now handled by RipCurl. Protocol version, origin, and Accept header validation may
  have moved to the controller. If it's just a few static methods, inline them or
  move to `http` package.
- **`Names`** and **`Parameters`** utilities in `server/util/` — check if still used.
  Move to appropriate domain package if so, delete if not.
- **`McpToolsCapability`** — replaced by `ToolsRegistry` + `McpToolMethods`. Delete
  it and the `McpServerCapability` interface if nothing else implements it.

### Update autoconfiguration

`MocapiAutoConfiguration` wires the core beans:
- `McpServer` (with cached `InitializeResponse`)
- `ToolsRegistry` (from all `McpToolProvider` beans)
- `McpSessionStore` (default in-memory)
- `McpEventIdCodec`

`MocapiToolsAutoConfiguration` wires tools beans:
- `ToolServiceMcpToolProvider`
- `DefaultAnnotationMcpToolProviderFactory`
- `McpToolMethods` (injected with `ToolsRegistry`)

The controller gets `McpSessionStore`, `OdysseyStreamRegistry`,
`McpEventIdCodec`, and session timeout from config.

## Acceptance criteria

- [ ] `session` package in core contains `McpSession`, `McpSessionStore`, and all
      client capability types
- [ ] `tools` package in core contains `ToolsRegistry`, `McpTool`, content types,
      response records
- [ ] `stream` package in core contains `McpStreamContext`, `ElicitationResult`, and
      related types
- [ ] `server` package in core contains `McpServer`, `ServerInfo`, capability interfaces
- [ ] `security` package in core contains `McpEventIdCodec`
- [ ] `ToolsRegistry` exists with `lookup()` and `listTools()` methods
- [ ] `McpToolsCapability` is deleted, replaced by `ToolsRegistry` + `McpToolMethods`
- [ ] `JsonMethodInvoker` in mocapi-core is deleted (uses RipCurl's)
- [ ] `McpSessionMethods` in autoconfigure/session handles initialize, initialized, ping
- [ ] `McpToolMethods` in autoconfigure/tools handles tools/list, tools/call
- [ ] `StreamableHttpController` in autoconfigure/http is transport-only
- [ ] Dead code removed (unused validators, utilities, capability interfaces)
- [ ] No circular dependencies between packages
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- Package moves don't change behavior — just imports. But they'll touch a lot of
  files, so do it in one commit to avoid partial states.
- `ToolsRegistry` replaces `McpToolsCapability` entirely. The capability's role as a
  `McpServerCapability` (providing a descriptor for the initialize response) can be
  handled by `McpServer` directly — it knows tools exist if `ToolsRegistry` is
  present.
- The `client` package (`ClientCapabilities`, `ClientInfo`, etc.) moves to `session`
  because that's where client data is used — it's session initialization data.
- `Icon` is shared between `ClientInfo` and `ServerInfo`. It can live in `session`
  (where most usage is) and `server` imports it, or in a shared `common` package.
  Simpler to just put it in `session`.
- `AnnotationMcpTool` currently uses mocapi-core's `JsonMethodInvoker`. After
  switching to RipCurl's, `AnnotationMcpTool` would depend on `ripcurl-core`. This
  is fine since `mocapi-core` already depends on `ripcurl-core`.
