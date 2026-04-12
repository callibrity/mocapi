# Create mocapi-protocol module with core interfaces

## What to build

Create a new Maven module `mocapi-protocol` with the four core
interfaces and nothing else. **Do NOT move any code from
mocapi-core. Do NOT modify mocapi-core. Do NOT delete anything.**
This spec is purely additive — a new module with new files.

### Module

```
mocapi-protocol/
├── pom.xml
└── src/main/java/com/callibrity/mocapi/protocol/
    ├── McpProtocol.java
    ├── McpTransport.java
    ├── McpContext.java
    └── McpEvent.java
```

### pom.xml

```xml
<parent>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-parent</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>mocapi-protocol</artifactId>
<name>Mocapi - Protocol</name>
<description>
  Transport-agnostic MCP protocol interfaces and implementation.
</description>

<dependencies>
  <dependency>
    <groupId>com.callibrity.ripcurl</groupId>
    <artifactId>ripcurl-core</artifactId>
    <version>${ripcurl.version}</version>
  </dependency>
</dependencies>
```

The only dependency is ripcurl-core (for `JsonRpcMessage`). More
deps will be added in later specs as the protocol implementation
grows. Start minimal.

### Package

Use `com.callibrity.mocapi.protocol` — a NEW package, not the
existing `com.callibrity.mocapi`. This avoids split-package
problems between the two modules.

### Interfaces

**`McpProtocol.java`**:
```java
package com.callibrity.mocapi.protocol;

import com.callibrity.ripcurl.core.JsonRpcMessage;

public interface McpProtocol {
  void handle(McpContext context, JsonRpcMessage message, McpTransport transport);
}
```

**`McpTransport.java`**:
```java
package com.callibrity.mocapi.protocol;

import com.callibrity.ripcurl.core.JsonRpcMessage;

public interface McpTransport {
  void emit(McpEvent event);
  void send(JsonRpcMessage message);
}
```

**`McpContext.java`**:
```java
package com.callibrity.mocapi.protocol;

public interface McpContext {
  String sessionId();
  String protocolVersion();
}
```

**`McpEvent.java`**:
```java
package com.callibrity.mocapi.protocol;

public sealed interface McpEvent {
  record SessionInitialized(String sessionId, String protocolVersion)
      implements McpEvent {}
}
```

### Parent pom

Add `<module>mocapi-protocol</module>` to the parent pom's
`<modules>` list. Place it before `mocapi-core` in the list so
it builds first (other modules will depend on it in later specs).

### Delete the old interfaces from mocapi-core

The interfaces `McpProtocol`, `McpTransport`, `McpContext`, and
`McpLifecycleEvent` currently exist in
`com.callibrity.mocapi` inside `mocapi-core`. **Delete them from
mocapi-core** — they are being replaced by the ones in the new
`mocapi-protocol` module under `com.callibrity.mocapi.protocol`.
Nothing in mocapi-core uses them yet, so this is safe.

## Acceptance criteria

- [ ] `mocapi-protocol` module exists in the reactor.
- [ ] `pom.xml` depends only on `ripcurl-core`.
- [ ] Package is `com.callibrity.mocapi.protocol`.
- [ ] All four interfaces exist with the exact signatures above.
- [ ] `McpEvent` is sealed with `SessionInitialized(sessionId, protocolVersion)`.
- [ ] Old interfaces deleted from `mocapi-core`.
- [ ] `mvn verify` passes (the new module compiles; existing
      tests in other modules are unaffected).
- [ ] **No code was moved from mocapi-core.**
- [ ] **mocapi-core was not modified except to delete the four
      old interface files.**

## Implementation notes

- This is deliberately tiny — one module, four interfaces, one
  pom. Ralph should complete this in a single iteration.
- Do NOT add `spring-boot-autoconfigure`, substrate, Odyssey, or
  any other dependency. Those come in later specs.
- Do NOT add tests yet — there's nothing to test. Tests come
  when the implementation is added in later specs.
- Do NOT register anything in AutoConfiguration.imports.
