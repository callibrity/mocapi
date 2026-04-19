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

## Advanced: Customizers

Two extension points are available.

### `OAuth2ProtectedResourceMetadataCustomizer`

Add arbitrary claims to the metadata document:

```java
@Bean
OAuth2ProtectedResourceMetadataCustomizer tlsBoundTokenAdvertisement() {
    return builder -> builder.tlsClientCertificateBoundAccessTokens(true);
}
```

Multiple customizers compose. They run after mocapi's defaults are populated from properties, so they can override field values or add new ones.

### `MocapiOAuth2SecurityFilterChainCustomizer`

Layer additional authorization rules on top of mocapi's default chain:

```java
@Bean
MocapiOAuth2SecurityFilterChainCustomizer requireScope() {
    return http -> http.authorizeHttpRequests(auth ->
        auth.requestMatchers("/mcp/**").hasAuthority("SCOPE_mcp.read"));
}
```

Mocapi's chain already has `.authenticated()` + `oauth2ResourceServer(rs -> rs.jwt(...))` applied. Customizers run last, so you can layer scope checks, `hasAuthority(...)` rules, CORS, etc., without redeclaring the whole chain.

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

## Stdio transport

OAuth2 is transport-bearer-token-specific and applies only to the Streamable HTTP transport. Stdio (subprocess-launched) MCP servers authenticate the subprocess via its launch context; there are no bearer tokens to validate. `mocapi-oauth2` depends on `mocapi-streamable-http-spring-boot-starter` and is not compatible with stdio-only deployments.

## What's not yet supported

Covered by a future minor release:

- **DPoP (RFC 9449) and mTLS token binding** — out of scope for 0.9.0.
- **Signed metadata** (`application/resource-metadata+jwt`) — Spring serves JSON only.
- **Multiple issuer-uris (federation)** — `mocapi.oauth2.authorization-servers` accepts a list and is advertised in the metadata, but Spring's auto-wired `JwtDecoder` assumes a single issuer. Federation requires a custom `JwtDecoder` bean today.
