# mocapi-metrics-spring-boot-starter (no-code aggregator)

## What to build

The conventional Spring Boot starter that pairs with `mocapi-metrics` —
no Java code, just a pom aggregating the dependencies users need to wire
metrics into their mocapi-powered app.

### Module layout

```
mocapi-metrics-spring-boot-starter/
  pom.xml
  (no src/)
```

### pom.xml

- `artifactId`: `mocapi-metrics-spring-boot-starter`
- Parent: `mocapi-parent`
- Dependencies (compile scope):
    - `com.callibrity.mocapi:mocapi-metrics`
    - `com.callibrity.mocapi:mocapi-streamable-http-spring-boot-starter`
      (so users pulling this starter don't need to manage two starters
      for basic tool-server + metrics setups — follow whatever
      convention the existing `mocapi-oauth2-spring-boot-starter` uses).
    - `org.springframework.boot:spring-boot-starter-actuator` —
      optional, commented in the pom with "add this if you want
      `/actuator/metrics` to expose the mcp.* meters; most apps already
      have it". Or leave out entirely and have users add it themselves.

No `spring-boot-starter-micrometer-*` — users pick their registry
(Prometheus / OTLP / CloudWatch / etc.) in their own pom.

### Add to parent pom

- `<modules>` entry.
- `mocapi-bom` `<dependencyManagement>` entry.

### Tests

No code = no tests. The starter's pom gets verified by the parent's
`mvn verify` running successfully and by `mocapi-metrics`'s integration
test (which can depend on the starter rather than on `mocapi-metrics`
directly, to exercise the full user-facing dependency graph).

## Acceptance criteria

- [ ] New Maven module `mocapi-metrics-spring-boot-starter`, no `src/`.
- [ ] Pom depends on `mocapi-metrics` and the streamable-http starter.
- [ ] Parent pom `<modules>` and `mocapi-bom`
      `<dependencyManagement>` updated.
- [ ] `mvn verify` green.
- [ ] `mocapi-metrics` integration test migrated to depend on the
      starter instead of `mocapi-metrics` directly (optional but
      cleaner).

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: one-line mention
      of the new starter module.
- [ ] README "Modules" table gains a row for the starter.

## Commit

Suggested commit message:

```
Add mocapi-metrics-spring-boot-starter

Pom-only aggregator so users can enable Micrometer metrics on their
mocapi server with a single dependency.
```
