# `Hashes` utility helper in `mocapi-server`

## What to build

Extract the SHA-256-plus-hex-encoding boilerplate currently
inlined in `McpActuatorSnapshots.schemaDigest` into a shared
static helper at `com.callibrity.mocapi.server.util.Hashes`.
Future SHA-256 call sites (mocapi-audit from spec 196,
potential cursor opaque-id rolls, event-id fingerprints, etc.)
use the helper instead of re-implementing.

### Why

The current implementation:

```java
static String schemaDigest(ObjectNode schema) {
    byte[] bytes = schema.toString().getBytes(StandardCharsets.UTF_8);
    try {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        return "sha256:" + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 not available", e);
    }
}
```

…is being duplicated in spec 196 for `arguments_hash` in the
audit interceptor. It'll duplicate again for anything that
wants a fingerprint. All the variants will:

- Use SHA-256.
- Encode as lowercase hex.
- Prefix `sha256:` (self-describing — lets consumers tell at a
  glance what hash algo produced the value).
- Wrap the checked `NoSuchAlgorithmException` as unchecked
  (SHA-256 is mandated by the JDK — the exception is
  theoretically impossible).

Extract once, reuse.

### API

```java
// mocapi-server — com.callibrity.mocapi.server.util
public final class Hashes {

    private Hashes() {}

    /** SHA-256 of the UTF-8 bytes of {@code content}, hex-encoded with {@code sha256:} prefix. */
    public static String sha256Of(String content) { ... }

    /** SHA-256 of {@code bytes}, hex-encoded with {@code sha256:} prefix. */
    public static String sha256Of(byte[] bytes) { ... }

    /**
     * SHA-256 of the concatenation of the given chunks' UTF-8 bytes, hex-encoded with
     * {@code sha256:} prefix. Useful for hashing composite inputs (handler-name + arguments,
     * session-id + cursor position, etc.) without an intermediate String concat.
     */
    public static String sha256Of(String... chunks) { ... }
}
```

All three variants produce the same `"sha256:<64 hex chars>"`
output format. Internally they delegate to one private
`digest(byte[])` implementation.

**Design choices:**

- **Hex (not base64).** Hex is the near-universal expectation
  for content-addressable hashes (`git`, `sha256sum`, Docker
  image digests). Base64 would be shorter but compare poorly
  against external tooling output.
- **Lowercase.** Matches `git` / `sha256sum` convention.
- **`sha256:` prefix always.** Self-describing. Costs 7
  characters per value; buys the ability to version the
  algorithm later ("sha512:..." / "blake3:...") without
  ambiguity. Matches Docker image digest syntax.
- **UTF-8 for String overloads.** Unambiguous; mocapi is
  UTF-8 throughout.
- **Varargs concatenation variant** (`sha256Of(String...)`)
  so callers who want to hash `handler-name|session-id|args`
  don't have to build an intermediate delimiter-joined
  String. Helper handles the bytes directly.
- **IllegalStateException on `NoSuchAlgorithmException`.**
  SHA-256 is mandated available per the Java spec; the
  exception is genuinely impossible. Wrapping as unchecked
  lets call sites be clean one-liners.

Deliberately **not** adding `MessageDigest`-reuse (thread-
locals, object pools, etc.). `MessageDigest.getInstance("SHA-256")`
is cheap enough on modern JVMs for the call rates mocapi's
use cases generate. Premature optimization.

Deliberately **not** adding other algorithms. If a future
spec needs SHA-512 or Blake3, add a second method then. No
`Hashes.of(Algorithm algo, ...)` matrix.

### Migration — existing call sites

`McpActuatorSnapshots.schemaDigest` becomes:

```java
static String schemaDigest(ObjectNode schema) {
    return schema == null ? null : Hashes.sha256Of(schema.toString());
}
```

That's the one existing call site.

### Downstream consumers

Spec 196 (mocapi-audit) will consume `Hashes.sha256Of(...)`
for the `arguments_hash` field when that spec's optional
`hash-arguments` property is enabled.

Any future cursor opaque-id / event-id work gets the helper for
free.

## Acceptance criteria

- [ ] New class
      `com.callibrity.mocapi.server.util.Hashes` in
      `mocapi-server/src/main/java/...` with the three
      `sha256Of(...)` overloads described above.
- [ ] All three variants return
      `"sha256:" + <64 lowercase hex characters>`.
- [ ] `sha256Of(String)` hashes UTF-8 bytes.
- [ ] `sha256Of(String...)` hashes the UTF-8-byte
      concatenation of its arguments, with no delimiter
      between them. Empty varargs / null args handling:
      null chunk throws `NullPointerException` with a clear
      message; empty varargs hashes the empty string.
- [ ] `McpActuatorSnapshots.schemaDigest` reduced to a
      one-liner delegating to `Hashes.sha256Of(...)`; existing
      behavior / output identical.
- [ ] `NoSuchAlgorithmException` wrapped as
      `IllegalStateException` with message including the
      algorithm name; exception is effectively unreachable on
      a conformant JVM, but cover it in a javadoc
      `@throws`-style comment rather than leaking checked
      exceptions to callers.
- [ ] Unit tests:
    - `sha256Of("hello")` produces the known vector
      `"sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"`.
    - Varargs variant: `sha256Of("a", "b")` equals
      `sha256Of("ab")`.
    - Empty String hashes to the known empty-string SHA-256.
    - Null chunk in varargs throws `NullPointerException`.
    - Byte-array variant produces the same hash as the String
      variant for the same UTF-8 bytes.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Supporting algorithms beyond SHA-256.** Add when a spec
  needs them.
- **Pooling `MessageDigest` instances.** Unneeded at mocapi's
  call rates.
- **Streaming / incremental hashing.** Varargs covers the
  "several chunks" case without pulling the API toward
  `DigestInputStream`-shape complexity.
- **Base64 variants.** Same principle — add when a real
  consumer wants one.

## Implementation notes

- `HexFormat.of()` returns a shared instance — no need to
  cache.
- Private single `byte[] sha256Bytes(byte[])` helper avoids
  duplicating the try/catch block across the three overloads.
- Package location `com.callibrity.mocapi.server.util` matches
  existing siblings (`Cursors`, `ScopedValueResolver`, etc.).
- This spec is a prereq for spec 196 (`mocapi-audit`) using
  the helper cleanly. Ralph runs in order, so 195 lands
  first and 196 references it without special instruction.
