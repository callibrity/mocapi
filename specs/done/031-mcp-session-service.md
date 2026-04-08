# Introduce McpSessionService

## What to build

Create `McpSessionService` as the single public API for all session concerns —
lifecycle, encryption, and lookup. The controller and `McpStreamContext` interact
with sessions through this service only. The `McpSessionStore` (persistence SPI)
and `McpEventIdCodec` (encryption) become internal implementation details.

### `McpSessionService` in `mocapi-core`

```java
public class McpSessionService {
    private final McpSessionStore store;
    private final byte[] masterKey;
    private final Duration ttl;

    // Lifecycle
    String create(McpSession session);
    Optional<McpSession> find(String sessionId);
    void delete(String sessionId);

    // Session mutations
    void setLogLevel(String sessionId, LogLevel level);

    // Encryption (session-bound)
    String encrypt(String sessionId, String plaintext);
    String decrypt(String sessionId, String ciphertext);
}
```

**`create()`** — saves session to store with TTL, returns the generated session ID.

**`find()`** — looks up by session ID. If found, touches (extends TTL) and returns.
If expired or missing, returns empty.

**`delete()`** — removes session from store.

**`encrypt()`** — delegates to `Ciphers.encryptAesGcm(masterKey, sessionId, plaintext)`,
Base64-encodes the result. Used for SSE event IDs and elicitation JSON-RPC request IDs.

**`decrypt()`** — Base64-decodes, delegates to
`Ciphers.decryptAesGcm(masterKey, sessionId, ciphertext)`. Throws on
tampered/wrong-session tokens. The controller maps this to 400 Bad Request.

**`setLogLevel()`** — reads current session, creates updated copy with new level,
saves back to store.

### Update controller

Replace direct usage of `McpSessionStore` and `McpEventIdCodec` with
`McpSessionService`:

```java
// Initialize
String sessionId = sessionService.create(session);

// Every request
McpSession session = sessionService.find(sessionId)
    .orElse(null); // → 404

// Delete
sessionService.delete(sessionId);

// SSE event ID encryption (in SseEventMapper)
String eventId = sessionService.encrypt(sessionId, streamKey + "/" + journalEntryId);

// GET resumption
String plaintext = sessionService.decrypt(sessionId, lastEventId);

// Elicitation request ID
String requestId = sessionService.encrypt(sessionId, "elicit:" + uuid);

// Elicitation response routing
String mailboxKey = sessionService.decrypt(sessionId, responseId);
```

### Update `DefaultMcpStreamContext`

Replace direct `McpEventIdCodec` usage with `McpSessionService`. The stream context
needs the service for encrypting elicitation request IDs and for checking session
capabilities (log level, elicitation support).

### Update auto-configuration

Create `McpSessionService` bean:

```java
@Bean
public McpSessionService mcpSessionService(
        McpSessionStore store,
        MocapiProperties props) {
    byte[] masterKey = Base64.getDecoder().decode(props.getSessionEncryptionMasterKey());
    return new McpSessionService(store, masterKey, props.getSessionTimeout());
}
```

Remove direct beans for `McpEventIdCodec` and `McpSessionStore` from public API.
`McpEventIdCodec` becomes a stateless utility with static methods — the service
owns the master key and passes it per call. The store is still created as a bean
(for `@ConditionalOnMissingBean` pluggability) but consumers interact through
the service only.

### Delete direct controller dependencies

The controller no longer injects `McpSessionStore`, `McpEventIdCodec`, or
`Duration sessionTimeout` separately. It injects `McpSessionService` only.

## Acceptance criteria

- [ ] `McpSessionService` exists in `mocapi-core`
- [ ] `create()`, `find()`, `delete()` delegate to `McpSessionStore`
- [ ] `find()` calls `touch()` to extend TTL on successful lookup
- [ ] `encrypt()` and `decrypt()` delegate to `McpEventIdCodec`
- [ ] Controller uses `McpSessionService` for all session operations
- [ ] Controller no longer directly depends on `McpSessionStore` or `McpEventIdCodec`
- [ ] `DefaultMcpStreamContext` uses `McpSessionService` for encryption
- [ ] `McpSessionService` bean is created in auto-configuration
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- `McpSessionService` lives in `mocapi-core` (`session` package). It depends on
  `McpSessionStore` (SPI) and `Ciphers` (static utility), both also in core.
- The service owns the master key (`byte[]`). The auto-configuration decodes the
  Base64 config property and passes the bytes to the service constructor.
- The service validates the key at construction via `Ciphers.validateAesGcmKey()`.
  Fails fast with a clear error if the key is wrong size or null.
- `Ciphers` is a general-purpose crypto utility in a `security` or `util` package.
  Stateless, static methods. Knows nothing about MCP, sessions, or event IDs.
- `McpEventIdCodec` is deleted — replaced by `Ciphers` + `McpSessionService`.
- The service is the boundary — nothing outside the `session` package should
  reference `McpSessionStore` or `Ciphers` directly for session encryption.
- Property: `mocapi.session-encryption-master-key` (Base64-encoded 32-byte secret).

### `Ciphers` utility class

In `mocapi-core` (`security` or `util` package):

```java
public final class Ciphers {
    public static void validateAesGcmKey(byte[] key);
    public static byte[] encryptAesGcm(byte[] masterKey, String keyContext, byte[] plaintext);
    public static byte[] decryptAesGcm(byte[] masterKey, String keyContext, byte[] ciphertext);
}
```

**`validateAesGcmKey()`** — checks key is non-null and 32 bytes (AES-256). Throws
`IllegalArgumentException` with a clear message if invalid. Called by
`McpSessionService` constructor to fail fast at startup.

**`encryptAesGcm()`** — derives per-context AES key via `HMAC-SHA256(masterKey, keyContext)`,
generates 12-byte random nonce, encrypts with AES-256-GCM, returns
`nonce + ciphertext + auth tag` as bytes.

**`decryptAesGcm()`** — derives same key, extracts nonce, decrypts. Throws on
tampered data or wrong key context (`AEADBadTagException`).

`keyContext` is any string that scopes the derived key — for MCP it's the session ID,
but `Ciphers` doesn't care. Pure crypto, no domain knowledge.
- `find()` combines lookup + touch in one call so the caller can't forget to
  extend the TTL.
- The `ttl` is injected at construction so every `create()` and `find()` uses
  the same configured timeout.
- `setLogLevel()` reads the current session, creates a new record with the updated
  level, and saves it back. The `@JsonRpc("logging/setLevel")` handler calls
  `sessionService.setLogLevel(sessionId, level)` — no direct store access needed.
- `McpStreamContext.log()` calls `sessionService.find()` to get the current level
  for filtering. Since the context is created per-request and the level is set on
  a different request, the level is always current.
