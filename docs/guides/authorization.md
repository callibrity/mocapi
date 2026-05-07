# Authorization

Mocapi ships an OAuth2 resource-server module for the Streamable HTTP transport. The MCP 2025-11-25 authorization specification requires servers to:

1. Validate bearer JWTs on every request (signature, expiry, audience).
2. Respond with `401 WWW-Authenticate: Bearer ... resource_metadata="..."` when a token is missing or invalid.
3. Publish an RFC 9728 protected-resource metadata document at `/.well-known/oauth-protected-resource` advertising the accepted authorization servers.

`mocapi-oauth2` wires all three, leaning on Spring Boot's `oauth2-resource-server` starter for the heavy lifting and filling in the MCP-specific gaps Spring doesn't address.

## Getting Started

Add the starter to your build — it pulls `mocapi-streamable-http-spring-boot-starter` and `spring-boot-starter-oauth2-resource-server` transitively.

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-oauth2</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

Configure your identity provider and the resource you're protecting:

**Minimum configuration** — for the common single-audience case, two standard Spring Boot properties are enough:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.com
          audiences:
            - https://mcp.example.com
```

When `spring.security.oauth2.resourceserver.jwt.audiences` has exactly one element and `mocapi.oauth2.resource` is unset, mocapi auto-derives the protected-resource metadata's `resource` field from that single audience. For Auth0, Okta, Keycloak, and most other IdPs this is the normal case — clients get tokens for one logical resource, and there's no need to duplicate the identifier across two properties.

**Full configuration** — set `mocapi.oauth2.*` when you want to enrich the metadata document or work around the auto-derivation:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.com
          audiences:
            - https://mcp.example.com

mocapi:
  server-title: My MCP Server                          # already used by MCP initialize;
                                                       # reused as OAuth2 resource_name
  oauth2:
    resource: https://mcp.example.com                  # optional — defaults to audiences[0]
                                                       # when audiences has exactly one entry.
                                                       # Must be a member of audiences.
    scopes:                                            # optional — advertised in metadata
      - mcp.read
      - mcp.write
    resource-documentation: https://docs.example.com   # optional — developer docs URL
    resource-policy-uri: https://example.com/policy    # optional — policy doc (token handling)
    resource-tos-uri: https://example.com/tos          # optional — terms of service
```

The metadata's `resource_name` field is sourced from `mocapi.server-title` (falling back to `mocapi.server-name`) — the same human-readable label the MCP `initialize` response advertises. Having one property feed both avoids a configuration drift where the OAuth2 metadata names a different server than the MCP handshake.

`spring.security.oauth2.resourceserver.jwt.issuer-uri` and `.audiences` are the standard Spring Boot properties; mocapi does not duplicate them. The `mocapi.oauth2.*` properties cover the MCP-specific metadata document.

### Recommended: set `jwk-set-uri` directly

If you only set `issuer-uri`, Spring Boot performs an HTTP call to the IdP's `/.well-known/openid-configuration` at startup (or on first request, via `SupplierJwtDecoder`) to discover the signing keys URL. Setting `jwk-set-uri` explicitly skips that discovery hop:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://example.auth0.com/
          jwk-set-uri: https://example.auth0.com/.well-known/jwks.json   # recommended
          audiences:
            - https://demo-api.example.com
```

Cuts one network dependency from the boot path and makes startup more robust in restricted network environments. Most IdPs publish their JWKS at `<issuer>.well-known/jwks.json` — Auth0, Okta, Keycloak, Entra ID all follow that pattern.

### Startup-time invariant

Mocapi validates at startup that `mocapi.oauth2.resource` (whether explicitly set or auto-derived) is a member of `spring.security.oauth2.resourceserver.jwt.audiences`. If they don't agree, the app refuses to start with a descriptive error. The rationale: clients following the RFC 9728 metadata document request tokens bound to the advertised `resource` identifier; if that identifier isn't in the server's accepted audiences, every token the client obtains would be rejected during validation. Catching that at startup is much cheaper than a silently-broken deployment where every MCP request returns 401.

That's the whole setup. Starting your Spring Boot application brings up:

- Bearer token validation on `${mocapi.endpoint:/mcp}/**` (signature, expiry, audience) — Spring Boot auto-wires this from the `jwt.*` properties.
- A `401 WWW-Authenticate: Bearer ... resource_metadata="..."` challenge on missing or invalid tokens — Spring Security 7.0's built-in entry point handles this natively.
- A `GET /.well-known/oauth-protected-resource` endpoint serving the RFC 9728 metadata document — mocapi wires Spring's filter with your configured fields.

## What mocapi adds vs. what Spring provides

| Capability | Provided by |
|---|---|
| `JwtDecoder` (JWKS fetch, signature verify) | Spring Boot auto-config (`jwt.issuer-uri`) |
| `aud` claim validation | Spring Boot auto-config (`jwt.audiences`) |
| `JwtAuthenticationConverter` (claims → authorities) | Spring Boot auto-config |
| Default `SecurityFilterChain` with `oauth2ResourceServer()` | Spring Boot auto-config |
| `401 WWW-Authenticate: Bearer ... resource_metadata="..."` | Spring Security 7.0 `BearerTokenAuthenticationEntryPoint` |
| `/.well-known/oauth-protected-resource` endpoint | mocapi wires Spring's `OAuth2ProtectedResourceMetadataFilter` |
| Metadata document content (`resource`, `authorization_servers`, `scopes_supported`, etc.) | mocapi, from `mocapi.oauth2.*` with fallback to `jwt.issuer-uri` |
| `SecurityFilterChain` scoped to the MCP endpoint + metadata path | mocapi |

## Filter chain architecture

`mocapi-oauth2` registers **two** `SecurityFilterChain` beans, not one.
Each chain owns a distinct URL space, has its own authorization policy,
and exposes its own customizer SPI.

| Bean name | `@Order` | Matches | Policy | Customizer SPI |
|---|---|---|---|---|
| `mcpMetadataFilterChain` | `HIGHEST_PRECEDENCE` | `/.well-known/oauth-protected-resource` | `permitAll` (RFC 9728 §3) | `McpMetadataFilterChainCustomizer` |
| `mcpFilterChain` | `HIGHEST_PRECEDENCE + 10` | `${mocapi.endpoint:/mcp}` and `${mocapi.endpoint:/mcp}/**` | `authenticated` | `McpFilterChainCustomizer` |

CSRF is disabled on both (MCP is stateless bearer-token, not cookie auth).
Both chains wire the same `McpTokenStrategy` into Spring's
`oauth2ResourceServer` DSL — the metadata chain uses it only to satisfy
the DSL (which refuses to build without a bearer-token format declared);
the MCP chain uses it to actually validate incoming tokens.

The chains are split because they have genuinely different
responsibilities. The metadata chain serves a public discovery document
— clients fetch it **before** they have a token to find out which
authorization server to use. Requiring auth there would be a chicken-
and-egg violation of RFC 9728. Keeping it on its own chain with its own
customizer surface means tweaks to the MCP auth policy (like "require
scope `mcp.write`") can't accidentally lock the metadata document.

## Customizing the MCP chain

`McpFilterChainCustomizer` mutates the `HttpSecurity` for `mcpFilterChain`
after mocapi has applied its defaults (securityMatcher, authenticated,
CSRF disabled, `oauth2ResourceServer`). Use it to require specific scopes
on top of authentication, add CORS to the MCP endpoint, install a
rate-limit filter, etc.

```java
@Bean
McpFilterChainCustomizer requireScope() {
    return http -> http.authorizeHttpRequests(auth ->
        auth.anyRequest().hasAuthority("SCOPE_mcp.read"));
}
```

Multiple `McpFilterChainCustomizer` beans compose in Spring's natural
order. They run **after** mocapi's defaults, so user rules layer on top
of (and can override) the built-ins.

## Customizing the metadata chain

`McpMetadataFilterChainCustomizer` targets the metadata chain. Typical
uses: permit CORS so browser-based MCP clients can fetch the metadata
document, add security headers, or front the endpoint with a rate
limiter.

```java
@Bean
McpMetadataFilterChainCustomizer metadataCors() {
    return http -> http.cors(cors -> cors.configurationSource(request -> {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));
        config.setAllowedMethods(List.of("GET", "HEAD"));
        return config;
    }));
}
```

**Don't touch `authorizeHttpRequests` on this chain.** RFC 9728 §3
requires the metadata document to be fetchable without authentication,
and mocapi freezes the policy at `permitAll` for that reason. This
customizer is for HTTP-layer concerns (CORS, headers, logging, rate
limiting) — not for auth policy.

## Swapping the token strategy

`McpTokenStrategy` is the SPI that configures Spring's
`oauth2ResourceServer` DSL with a bearer-token format. Mocapi ships two
implementations, selected automatically by `@ConditionalOnBean`:

- `JwtMcpTokenStrategy` — activates when Spring Boot wired a `JwtDecoder`
  (i.e. `spring.security.oauth2.resourceserver.jwt.*` is set).
- `OpaqueTokenMcpTokenStrategy` — activates when Spring Boot wired an
  `OpaqueTokenIntrospector` (i.e. `spring.security.oauth2.resourceserver.opaquetoken.*`
  is set). Internally wraps the introspector in an audience-checking
  delegate so the MCP-mandated `aud` validation still runs.

To replace both with a custom strategy — for a hypothetical future
token format, or to plug in a mock for testing — register a `@Primary`
bean:

```java
@Bean
@Primary
McpTokenStrategy customStrategy() {
    return rs -> rs.jwt(jwt -> jwt.decoder(myCustomDecoder()));
}
```

The same instance is applied to both the metadata and MCP chains, so
there's exactly one place to swap validation behavior.

## Customizing the metadata document

The RFC 9728 metadata document served at
`/.well-known/oauth-protected-resource` is assembled by a list of
`McpMetadataCustomizer` beans. Mocapi ships five baseline customizers,
each responsible for one facet:

| Customizer | Field(s) it sets | Source |
|---|---|---|
| `ResourceMetadataCustomizer` | `resource` | `mocapi.oauth2.resource`, or the single `jwt.audiences` entry when unset |
| `AuthorizationServersMetadataCustomizer` | `authorization_servers` | `mocapi.oauth2.authorization-servers`, or `jwt.issuer-uri` fallback |
| `ScopesSupportedMetadataCustomizer` | `scopes_supported` | `mocapi.oauth2.scopes` |
| `ResourceNameMetadataCustomizer` | `resource_name` | `Implementation.title()`, falling back to `Implementation.name()` (i.e. `mocapi.server-title` / `mocapi.server-name`) |
| `ClaimsMetadataCustomizer` | `resource_documentation`, `resource_policy_uri`, `resource_tos_uri` | the matching `mocapi.oauth2.*` properties; only emits a claim when the property is set |

### Adding a custom claim

Register a `@Bean McpMetadataCustomizer`. Default `@Order` runs it after
the five baseline customizers, so you see (and can overwrite) whatever
they set.

```java
@Bean
McpMetadataCustomizer tlsBoundTokenAdvertisement() {
    return builder -> builder.tlsClientCertificateBoundAccessTokens(true);
}
```

### Overriding a baseline facet

Two approaches, pick whichever fits:

1. **Register a later-`@Order` `McpMetadataCustomizer`** that mutates
   the field you want to change. Mocapi's baselines use
   `@Order(HIGHEST_PRECEDENCE)`, so any customizer with a later order
   (including the default) runs after them and wins:

   ```java
   @Bean
   @Order(0)
   McpMetadataCustomizer overrideResourceName() {
       return builder -> builder.resourceName("My Custom Name");
   }
   ```

2. **Register a `@Primary` replacement** for the specific baseline bean
   type (e.g. `@Primary ResourceNameMetadataCustomizer`). The autoconfig
   uses `@ConditionalOnMissingBean` on each baseline, so your bean
   replaces it entirely and mocapi's default never registers:

   ```java
   @Bean
   @Primary
   ResourceNameMetadataCustomizer myResourceName() {
       return new ResourceNameMetadataCustomizer(/* ... */) {
           @Override
           public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
               builder.resourceName("My Custom Name");
           }
       };
   }
   ```

   Use this when you want to take over a facet completely, including its
   property-reading constructor logic.

## Opaque tokens

Some IdPs issue opaque (non-JWT) access tokens and validate them via RFC 7662 introspection. Mocapi auto-detects the mode from which Spring Boot resource-server properties are configured: set `spring.security.oauth2.resourceserver.opaquetoken.*` instead of `jwt.*`.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: https://idp.example.com/introspect
          client-id: mcp-client
          client-secret: ${INTROSPECTION_SECRET}
        jwt:
          audiences:
            - mcp.example.com                          # still required — enforced via introspection response
```

The `jwt.audiences` property is reused in opaque mode — mocapi wraps Spring's `OpaqueTokenIntrospector` to enforce the `aud` claim on the introspection response, since Spring's opaque path does not include an audience validator out of the box. The MCP spec still requires audience checking, so this wrapper is not optional.

JWT and opaque modes are mutually exclusive; configure one or the other. If both are somehow configured, JWT wins (matching Spring Boot's own precedence).

## Per-handler authorization

`mocapi-oauth2` validates tokens on the way in. Gating individual handlers
on the resulting `Authentication` is a second concern, covered by the
`mocapi-spring-security-guards` module. Add it alongside `mocapi-oauth2`:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-security-guards</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

Then annotate handler methods:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("admin:write")           // AND — all listed scopes required
@RequiresRole({"TENANT_ADMIN", "OPS"})  // OR — any listed role grants access
public void tenantAdminOp(...) { ... }
```

`@RequiresScope` values match granted authorities with the `SCOPE_` prefix
Spring Security's JWT / opaque-token converters produce (so `admin:write`
matches `SCOPE_admin:write`). `@RequiresRole` accepts bare (`ADMIN`) or
prefixed (`ROLE_ADMIN`) values; both normalize to the same granted
authority. Both annotations may coexist on the same method — the Guard
SPI's AND evaluation means every attached guard must allow.

Denied calls do not reach the handler: `tools/list` (and the matching
`prompts/list`, `resources/list`, `resources/templates/list`) hides the
handler entirely, and `tools/call` returns JSON-RPC `-32003` with
`Forbidden: <reason>` where the reason comes from the first denying
guard (`unauthenticated`, `missing scope(s): ...`, or
`insufficient role`). Deny reasons are only returned at call time —
list time simply omits the handler so the decision doesn't leak.

Unlike Spring Security's `@PreAuthorize`, guards gate list operations as
well as call operations — clients doing `tools/list` never see handlers
they aren't entitled to invoke. And guard denials surface as the
protocol-right `-32003 Forbidden` shape instead of the generic
`-32603 Internal error` an AOP-thrown `AccessDeniedException` would
produce. See [docs/guards.md](guards.md) for the underlying Guard SPI,
and the `mocapi-spring-security-guards` module for the annotation
sources if you want to write your own Guard implementation.

## Stdio transport

OAuth2 is transport-bearer-token-specific and applies only to the Streamable HTTP transport. Stdio (subprocess-launched) MCP servers authenticate the subprocess via its launch context; there are no bearer tokens to validate. `mocapi-oauth2` depends on `mocapi-streamable-http-spring-boot-starter` and is not compatible with stdio-only deployments.

## What's not yet supported

Covered by a future minor release:

- **DPoP (RFC 9449) and mTLS token binding** — out of scope for 0.9.0.
- **Signed metadata** (`application/resource-metadata+jwt`) — Spring serves JSON only.
- **Multiple issuer-uris (federation)** — `mocapi.oauth2.authorization-servers` accepts a list and is advertised in the metadata, but Spring's auto-wired `JwtDecoder` assumes a single issuer. Federation requires a custom `JwtDecoder` bean today.
