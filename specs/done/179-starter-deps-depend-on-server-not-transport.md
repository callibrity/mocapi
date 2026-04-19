# Starter cleanup: depend on mocapi-server, not a transport starter

## What to build

Fix two feature-starter pom dependencies that currently pull in the
streamable-HTTP transport by accident. The starters in question are
transport-agnostic — they work with any mocapi transport — so they
should depend on `mocapi-server` directly and leave transport choice
to the user.

### Affected starters

| Starter | Drop | Add |
|---|---|---|
| `mocapi-jakarta-validation-spring-boot-starter` | `mocapi-streamable-http-spring-boot-starter` | `mocapi-server` |
| `mocapi-logging-spring-boot-starter` | `mocapi-streamable-http-spring-boot-starter` | `mocapi-server` |

### Not affected

- **`mocapi-oauth2-spring-boot-starter`** stays on
  `mocapi-streamable-http-spring-boot-starter`. OAuth2 validates
  Bearer tokens on HTTP request headers — it's genuinely HTTP-only.
- **`mocapi-streamable-http-spring-boot-starter`** and
  **`mocapi-stdio-spring-boot-starter`** are the transport starters
  themselves; their direct dependency on the transport module is
  correct.

### Why

A stdio-only consumer who wants MDC logs (or Jakarta Validation on
their tool parameters) should not be dragged into the full HTTP
transport stack — that's ~a dozen transitive deps they don't need,
plus potential servlet / embedded-container confusion in a CLI-only
deployment.

The transport starters themselves depend on `mocapi-server`; feature
starters depending on `mocapi-server` stack cleanly with whichever
transport starter the user picks.

---

## File-level changes

### `mocapi-jakarta-validation-spring-boot-starter/pom.xml`

Replace:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-streamable-http-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

with:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-server</artifactId>
    <version>${project.version}</version>
</dependency>
```

### `mocapi-logging-spring-boot-starter/pom.xml`

Same replacement.

### Also pull in `spring-boot-starter` for autoconfig ergonomics

Both affected starters should ensure `spring-boot-starter`
(non-specific-to-any-web-stack) is present transitively so the
autoconfig in the corresponding code module is picked up. Verify by
reading the current transitive graph of `mocapi-server`:

- If `mocapi-server` already pulls in `spring-boot-starter`, nothing
  to add.
- If not, add `spring-boot-starter` to each feature starter alongside
  the `mocapi-server` dependency. (Unlikely to be needed — the
  server module transitively pulls Spring Boot via
  `spring-boot-autoconfigure`.)

---

## Acceptance criteria

- [ ] `mocapi-jakarta-validation-spring-boot-starter`'s pom declares
      `mocapi-server` and no longer declares any
      `mocapi-*-spring-boot-starter`.
- [ ] `mocapi-logging-spring-boot-starter`'s pom declares
      `mocapi-server` and no longer declares any
      `mocapi-*-spring-boot-starter`.
- [ ] `mvn dependency:tree` on
      `mocapi-jakarta-validation-spring-boot-starter` does **not**
      list `mocapi-streamable-http-transport` anywhere.
- [ ] `mvn dependency:tree` on `mocapi-logging-spring-boot-starter`
      does **not** list `mocapi-streamable-http-transport` anywhere.
- [ ] Build an integration test or verify via inspection that adding
      `mocapi-stdio-spring-boot-starter` + `mocapi-logging-spring-boot-starter`
      to a consumer gives a working stdio server with MDC wired up —
      no HTTP stack pulled in.
- [ ] `mocapi-oauth2-spring-boot-starter` is untouched.
- [ ] Existing integration tests that rely on the feature starters
      still pass (they'll also pull in a transport starter where
      needed, so the feature starter not bringing one is fine).
- [ ] `mvn verify` + `mvn spotless:check` green.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Changed`: entry noting
      that `mocapi-jakarta-validation-spring-boot-starter` and
      `mocapi-logging-spring-boot-starter` no longer transitively
      pull in the streamable-HTTP transport. Migration note: users
      who depended on these starters implicitly bringing HTTP should
      add `mocapi-streamable-http-spring-boot-starter` explicitly.
- [ ] README "Modules" section already names each starter's
      purpose; no copy change needed unless the transport
      implication was called out (it isn't).

## Commit

Suggested commit message:

```
Decouple feature starters from the streamable-HTTP transport

mocapi-jakarta-validation-spring-boot-starter and
mocapi-logging-spring-boot-starter now depend on mocapi-server
directly instead of the streamable-HTTP transport starter. A
stdio-only (or future-transport-only) consumer pulling in validation
or MDC no longer drags the HTTP stack along for the ride.

mocapi-oauth2-spring-boot-starter still depends on the HTTP starter
— OAuth2 resource-server validation is HTTP-specific (Bearer tokens
on request headers) so the coupling is honest there.

BREAKING (sort of): consumers who depended on validation /
logging starters pulling in HTTP transitively need to add
mocapi-streamable-http-spring-boot-starter explicitly. In practice
most consumers already declare a transport starter, so this is a
no-op for them.
```

## Implementation notes

- Upcoming starters in the observability roadmap (`mocapi-o11y`,
  `mocapi-actuator`) should adopt the same "depend on
  `mocapi-server`" pattern from day one. That rule is baked into
  their specs (180, 181) so this cleanup is a one-time retrofit of
  previously-shipped starters.
- No code changes — pure pom edits plus CHANGELOG.
