# Rename mocapi-protocol → mocapi-server, McpProtocol → McpServer

## What to build

Rename the module and its primary interface to reflect that this
is a server implementation, not a protocol definition.

### Renames

| Before | After |
|---|---|
| Module: `mocapi-protocol` | `mocapi-server` |
| Interface: `McpProtocol` | `McpServer` |
| Implementation: `DefaultMcpProtocol` | `DefaultMcpServer` |
| Package root: `com.callibrity.mocapi.protocol` | `com.callibrity.mocapi.server` |
| All sub-packages: `...protocol.session`, etc. | `...server.session`, etc. |
| Auto-config: `MocapiProtocolAutoConfiguration` | `MocapiServerAutoConfiguration` |

### Steps

1. `git mv mocapi-protocol mocapi-server`
2. Update `pom.xml` artifactId to `mocapi-server`.
3. Rename all packages from `protocol` to `server` in source and
   test trees.
4. Rename `McpProtocol.java` → `McpServer.java`, rename the
   interface.
5. Rename `DefaultMcpProtocol` → `DefaultMcpServer`.
6. Rename auto-config class.
7. Update parent pom `<modules>`.
8. Update `mocapi-spring-boot-starter` dependency.
9. Update `mocapi-transport-streamable-http` dependency and all
   imports.
10. Update `examples/example-autoconfigure` imports.
11. Update `mocapi-compat` imports.
12. Update `AutoConfiguration.imports` files.

### Also rename

- `McpLifecycleEvent` → `McpEvent` (if it hasn't been renamed
  already).
- `SessionInitialized` should carry `(String sessionId, String protocolVersion)`.

### McpServer interface (final shape)

After this rename, `McpServer` should have this exact interface:

```java
package com.callibrity.mocapi.server;

import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;

public interface McpServer {
  void handleCall(McpContext context, JsonRpcCall call, McpTransport transport);
  void handleNotification(McpContext context, JsonRpcNotification notification);
  void handleResponse(McpContext context, JsonRpcResponse response);
  void terminate(String sessionId);
}
```

Only `handleCall` takes an `McpTransport` — calls produce output.
Notifications and responses do not.

## Acceptance criteria

- [ ] Module is `mocapi-server` with artifactId `mocapi-server`.
- [ ] Interface is `McpServer` in `com.callibrity.mocapi.server`.
- [ ] All sub-packages are `com.callibrity.mocapi.server.*`.
- [ ] `McpServer` has exactly `handleCall`, `handleNotification`,
      `handleResponse`, `terminate`.
- [ ] No references to `mocapi-protocol` or `McpProtocol` remain
      (except `specs/done/`).
- [ ] `mvn compile` passes across the reactor.
- [ ] **mocapi-core is not modified.**
- [ ] **Do NOT change any behavior** — pure rename.
