# ADR-0013 — OAuth2 module and Spring Security reference Guard

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

The MCP authorization specification (2025-11-25 revision) requires the
server to act as an OAuth 2.1 protected resource and to expose RFC 9728
protected-resource metadata at
`/.well-known/oauth-protected-resource`. That implies two coupled but
distinct HTTP concerns:

1. A `SecurityFilterChain` that serves the metadata document with
   `permitAll` access (per RFC 9728 the metadata endpoint cannot
   require authentication).
2. A `SecurityFilterChain` that protects `/mcp/**` with
   `oauth2ResourceServer`, validating bearer tokens (JWT or opaque)
   against the configured authorization server.

These are HTTP-layer concerns. They live in front of the JSON-RPC
dispatcher and the MCP handler chain — by the time a request reaches an
`@McpTool` method, authentication has already happened and the
`SecurityContextHolder` is populated.

Authorization is a separate layer: once authenticated, *which* handlers
can this caller see and call? That decision lives at the handler level
([ADR-0012](0012-guard-spi.md)). Mocapi's core must not couple to any
specific auth model — Spring Security is one choice among many —
but a reference Guard implementation backed by Spring Security
covers the common OAuth2-scope and role cases out of the box and
demonstrates how to wire custom auth models against the SPI.

Earlier iterations bundled OAuth2 setup directly into
`mocapi-autoconfigure`. That worked for autowiring but made the OAuth2
logic untestable without `@SpringBootTest` and forced every transport
starter consumer to drag in `spring-security` even when running stdio.
Splitting the responsibilities into two dedicated modules aligns with
the per-feature module pattern used elsewhere
([ADR-0017](0017-observability-stack.md)).

## Decision

**OAuth2 lives in `mocapi-oauth2`.** This module ships:

- Two `SecurityFilterChain` beans — one for the RFC 9728 metadata
  endpoint (`permitAll`) and one for `/mcp/**`
  (`oauth2ResourceServer`).
- An `McpTokenStrategy` SPI with two built-in implementations,
  `JwtMcpTokenStrategy` and `OpaqueTokenMcpTokenStrategy`. The
  strategy controls how the bearer token is validated. Spring Boot's
  `OAuth2ResourceServerProperties` decides which built-in is wired;
  users can register a `@Primary McpTokenStrategy` bean to replace it.
- An `McpMetadataCustomizer` SPI plus five built-in customizers
  (`ResourceMetadataCustomizer`, `AuthorizationServersMetadataCustomizer`,
  `ScopesSupportedMetadataCustomizer`, `ResourceNameMetadataCustomizer`,
  `ClaimsMetadataCustomizer`) that populate the metadata document.
- Two filter-chain customizer SPIs, `McpFilterChainCustomizer` and
  `McpMetadataFilterChainCustomizer`, for HTTP-layer tweaks (CORS,
  rate limiting, additional security headers) on each chain.

`mocapi-autoconfigure` retains only thin `@AutoConfiguration` declarations
that delegate into `mocapi-oauth2`. The autoconfig class targets under
100 lines; all real logic — compliance validation, resource resolution,
metadata-customizer construction, filter-chain assembly — lives in
testable classes inside the feature module.

**`mocapi-spring-security-guards` is the reference Guard implementation.**
This module reads two method-level annotations on user handler methods
at startup and attaches matching `Guard` instances via the customizer
SPI:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("admin:write")          // all listed scopes required (AND)
@RequiresRole({"TENANT_ADMIN", "OPS"}) // any listed role grants access (OR)
public void tenantAdminOp(...) { ... }
```

The `ScopeGuard` and `RoleGuard` implementations read
`SecurityContextHolder.getContext().getAuthentication()` at call time —
no reflection on the hot path, since the required scopes/roles are
captured by the Guard at startup. Denial of either hides the handler at
list time and returns JSON-RPC `-32003` with the deny reason at call
time, exactly per [ADR-0012](0012-guard-spi.md).

The two modules are independent. A deployment can use `mocapi-oauth2` with
custom guards, or `mocapi-spring-security-guards` with a hand-rolled
filter chain, or both together for the canonical enterprise shape.

## Consequences

**What this buys us.** Authentication (OAuth2 + RFC 9728 metadata) and
authorization (per-handler guards) are split along their natural seam.
Each module has a focused dependency footprint — stdio deployments don't
pull in `spring-security-oauth2-resource-server`. The reference Guard
implementation demonstrates the customizer/Guard pattern end-to-end so
third-party auth modules have a worked example to follow. OAuth2 logic
is unit-testable without an `ApplicationContextRunner`.

**Costs.** Two modules to remember. Enterprise deployments typically
need both, plus a transport starter — the dependency graph is wider
than a single "mocapi-security" jar would be. Mitigation: the
documentation lists the canonical bundle in
[authorization.md](../guides/authorization.md).

**Non-goals.** Mocapi does not ship custom token-introspection logic
beyond what Spring Security already provides; the `McpTokenStrategy` SPI
is a thin pass-through to `oauth2ResourceServer` configuration. Mocapi
does not own a tenant model, a rate-limit model, or any RBAC scheme
beyond the two reference annotations — those live in user code or
third-party modules built on the Guard SPI.

**Code anchors:** `mocapi-autoconfigure/.../oauth2/MocapiOAuth2AutoConfiguration.java` (filter chains); the `McpTokenStrategy` and `McpMetadataCustomizer` implementations live in `mocapi-oauth2/`; `mocapi-spring-security-guards/`. See also
[authorization.md](../guides/authorization.md).
