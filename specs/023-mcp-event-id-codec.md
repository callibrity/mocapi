# Encrypted event ID codec for SSE and JSON-RPC correlation

## What to build

Implement `McpEventIdCodec` — a utility that produces opaque, tamper-proof,
session-bound IDs for two purposes:

1. **SSE event IDs** — encode stream key + journal entry ID so the server can resume
   the correct stream when a client reconnects with `Last-Event-ID`
2. **JSON-RPC request IDs for server-to-client requests** — encode a mailbox key so
   the server can route the client's response to the correct Substrate Mailbox
   (elicitation, sampling, etc.)

Clients store and echo these IDs — they cannot read, forge, or reuse them across
sessions.

### `McpEventIdCodec` class

Location: `mocapi-core` (no transport dependencies).

```java
public class McpEventIdCodec {
    public String encode(String sessionId, String plaintext);
    public String decode(String sessionId, String encoded);
}
```

- `encode()` produces a Base64 string safe for HTTP headers (visible ASCII only)
- `decode()` returns the original plaintext or throws if tampered/wrong session
- The codec is stateless per call — all session binding is via key derivation

### Algorithm

**Encryption**: AES-256-GCM (authenticated encryption — confidentiality + integrity)

**Key derivation**: `HMAC-SHA256(masterKey, sessionId)` → 32 bytes → AES key.
Each session gets a unique derived key. A token from session A cannot be decoded
with session B's key — GCM authentication fails.

**Nonce**: 12 bytes from `SecureRandom` per encryption call.

**Ciphertext layout** (before Base64):
```
[12 bytes nonce][N bytes AES-GCM ciphertext + 16 byte auth tag]
```

**Encoding**: Standard Base64. Output is ~112 chars for typical plaintext.

### Encryption flow

```java
// Derive per-session AES key
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(masterKeyBytes, "HmacSHA256"));
byte[] derived = mac.doFinal(sessionId.getBytes(UTF_8));
SecretKey aesKey = new SecretKeySpec(derived, "AES");

// Encrypt
byte[] nonce = new byte[12];
new SecureRandom().nextBytes(nonce);
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, nonce));
byte[] ciphertext = cipher.doFinal(plaintext.getBytes(UTF_8));

// Combine nonce + ciphertext, Base64 encode
byte[] combined = new byte[nonce.length + ciphertext.length];
System.arraycopy(nonce, 0, combined, 0, nonce.length);
System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
return Base64.getEncoder().encodeToString(combined);
```

### Decryption flow

```java
byte[] combined = Base64.getDecoder().decode(encoded);
byte[] nonce = Arrays.copyOfRange(combined, 0, 12);
byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);

Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, nonce));
byte[] plaintext = cipher.doFinal(ciphertext);
// Throws AEADBadTagException if wrong session or tampered
return new String(plaintext, UTF_8);
```

### Usage — SSE event IDs

The `SseEventMapper` provided to Odyssey's subscriber builder encrypts event IDs:

```java
SseEventMapper mcpMapper = event -> {
    String plaintext = event.streamKey() + "/" + event.id();
    String encrypted = codec.encode(sessionId, plaintext);
    SseEmitter.SseEventBuilder builder = SseEmitter.event()
        .id(encrypted)
        .data(event.payload());
    if (event.eventType() != null) {
        builder.name(event.eventType());
    }
    return builder;
};

stream.subscriber().mapper(mcpMapper).subscribe();
```

On GET reconnect with `Last-Event-ID`:

```java
String plaintext = codec.decode(sessionId, lastEventId);
// plaintext = "streamKey/journalEntryId"
String[] parts = plaintext.split("/", 2);
String streamKey = parts[0];
String journalEntryId = parts[1];
OdysseyStream stream = registry.stream(streamKey);
stream.subscriber().mapper(mcpMapper).resumeAfter(journalEntryId);
```

If decryption fails (wrong session, tampered), return 400.

### Usage — elicitation/sampling JSON-RPC request IDs

When sending an `elicitation/create` request to the client:

```java
String mailboxKey = "elicit:" + UUID.randomUUID();
String encryptedId = codec.encode(sessionId, mailboxKey);
// Build JSON-RPC request with "id": encryptedId
// Publish on SSE stream
// Create mailbox with key = mailboxKey
// Block on mailbox.poll(timeout)
```

When the client POSTs back the response:

```java
String encryptedId = responseBody.get("id").asString();
String mailboxKey = codec.decode(sessionId, encryptedId);
// mailboxKey = "elicit:uuid-here"
Mailbox<JsonNode> mailbox = mailboxFactory.create(mailboxKey, JsonNode.class);
mailbox.deliver(responseBody.get("result"));
```

If decryption fails, return 400.

### Configuration

Add to `MocapiProperties`:

```java
private String eventIdMasterKey; // Base64-encoded 32-byte secret
```

Property: `mocapi.event-id.master-key`

Generate with `SecureRandom`, store in secrets manager or environment variable.
Never hard-code.

**Key rotation**: Accept an array of keys. Try decryption with each (most recent
first). Encrypt new IDs with only the current key.

### Error handling

- `AEADBadTagException` (wrong session or tampered) → 400 Bad Request
- `IllegalArgumentException` (malformed Base64) → 400 Bad Request
- Missing master key at startup → fail fast with clear error message

## Acceptance criteria

- [ ] `McpEventIdCodec` exists in `mocapi-core`
- [ ] `encode(sessionId, plaintext)` produces Base64 output
- [ ] `decode(sessionId, encoded)` returns original plaintext
- [ ] Decoding with wrong session ID throws
- [ ] Decoding tampered ciphertext throws
- [ ] Different sessions produce different ciphertexts for same plaintext
- [ ] Same session + same plaintext produces different ciphertexts (random nonce)
- [ ] Master key is configurable via `mocapi.event-id.master-key`
- [ ] Missing master key fails fast at startup
- [ ] SSE events use encrypted IDs via `SseEventMapper`
- [ ] GET `Last-Event-ID` is decrypted to resolve stream key and journal entry ID
- [ ] Elicitation JSON-RPC request IDs are encrypted
- [ ] Elicitation response IDs are decrypted to route to correct mailbox
- [ ] Invalid/tampered IDs return 400
- [ ] All new behavior has unit tests
- [ ] `mvn verify` passes

## Implementation notes

- The codec uses only `java.security` and `javax.crypto` — no external dependencies.
- The codec is stateless per call. Construct it once with the master key bytes and
  reuse across requests. Thread-safe.
- The `SseEventMapper` that uses the codec is per-subscription (captures the session
  ID). Create it in the controller when setting up a subscription.
- The `/` delimiter in SSE event ID plaintext (`streamKey/journalEntryId`) works
  because stream keys don't contain `/` (they use `:` as separator in Odyssey/Substrate).
- For elicitation, the mailbox key prefix (`elicit:`) lets the POST handler distinguish
  elicitation responses from other JSON-RPC responses if needed.
- Key rotation support is a nice-to-have for initial implementation. A single key
  is sufficient for v1.
- This spec depends on 018 (session store, for session ID) and 019 (RipCurl
  integration, for controller structure). The codec itself can be implemented
  independently.
