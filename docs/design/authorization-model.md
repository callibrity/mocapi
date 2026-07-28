# Authorization model

Authorization in mocapi is split across two layers that compose at runtime.
Neither layer knows about the other; both are present in any production
deployment.

1. **Transport-layer authentication** (`mocapi-oauth2`). Validates a bearer
   token on every HTTP request, populates Spring Security's
   `SecurityContextHolder` with an `Authentication`, and serves the RFC 9728
   protected-resource metadata document.
2. **Handler-layer authorization** (Guard SPI in `mocapi-server`, plus the
   `mocapi-spring-security-guards` reference implementation). Decides, per
   handler invocation, whether the current `Authentication` is allowed to
   see and call the handler. Visibility and invocation are unified — if a
   guard denies, the handler is hidden from `tools/list` *and* rejected by
   `tools/call`.

See [ADR-0012](../adr/0012-guard-spi.md) for the Guard SPI decision and
[ADR-0013](../adr/0013-oauth2-and-reference-guards.md) for the OAuth2
module and reference guards.

## Layer 1: transport-layer authentication (`mocapi-oauth2`)

`mocapi-oauth2` registers **two** `SecurityFilterChain` beans, scoped to
disjoint URL spaces. One serves the public discovery document; the other
authenticates MCP traffic.

| Bean | `@Order` | URL pattern | Policy | Customizer SPI |
|---|---|---|---|---|
| `mcpMetadataFilterChain` | `HIGHEST_PRECEDENCE` | `/.well-known/oauth-protected-resource` | `permitAll` | `McpMetadataFilterChainCustomizer` |
| `mcpFilterChain` | `HIGHEST_PRECEDENCE + 10` | `${mocapi.endpoint:/mcp}` and below | `authenticated` | `McpFilterChainCustomizer` |

Both chains disable CSRF (MCP is stateless bearer-token, not cookie auth)
and wire the same `McpTokenStrategy` into Spring's `oauth2ResourceServer`
DSL.

The chains are split because their responsibilities genuinely differ. The
metadata document must be fetchable without a token — clients read it
*to learn which authorization server to ask for a token*. Forcing
authentication on metadata would be a chicken-and-egg violation of RFC
9728 §3. Keeping policy on its own chain prevents an MCP-chain edit
(say, `requireScope("mcp.write")`) from accidentally locking metadata.

### Token strategy

`McpTokenStrategy` is the SPI that tells Spring how to validate bearer
tokens. Mocapi auto-selects the implementation based on which Spring Boot
properties were configured:

- `JwtMcpTokenStrategy` — when `spring.security.oauth2.resourceserver.jwt.*`
  is set. JWKS fetch, signature verify, audience validation are all
  Spring's own; mocapi only wires them onto both chains.
- `OpaqueTokenMcpTokenStrategy` — when `spring.security.oauth2.resourceserver.opaquetoken.*`
  is set. Wraps Spring's `OpaqueTokenIntrospector` to enforce the
  `aud` check on the introspection response (Spring's opaque path doesn't
  ship one out of the box; the MCP spec requires it).

A user can replace both with a `@Primary` bean for testing or a
hypothetical future format.

### Metadata customizers

The RFC 9728 document at `/.well-known/oauth-protected-resource` is
assembled by a list of `McpMetadataCustomizer` beans, one per facet
(`resource`, `authorization_servers`, `scopes_supported`, `resource_name`,
documentation/policy/ToS URIs). Mocapi ships five baselines, each
`@ConditionalOnMissingBean` — users can replace any of them outright with
`@Primary`, or add a later-`@Order` customizer to mutate or extend the
output (e.g. advertise mTLS-bound tokens).

## Layer 2: handler-layer authorization (Guard SPI)

The Guard SPI lives in `com.callibrity.mocapi.server.guards`. Three
types, no framework coupling:

```java
@FunctionalInterface
public interface Guard { GuardDecision check(); }

public sealed interface GuardDecision {
  record Allow() implements GuardDecision {}
  record Deny(String reason) implements GuardDecision {}
}
```

Guards attach via the per-handler customizer SPI. At handler-build time, a
customizer inspects the method (typically for an annotation) and calls
`config.guard(...)` to register the runtime check. The guard closure
captures whatever annotation state it needs; the runtime call is a single
method invocation with no reflection.

Multiple guards on the same handler evaluate with **AND** semantics, in
`@Order` of the contributing customizers. The first `Deny` short-circuits.

### Visibility ≡ invocation

The same guard list is consulted in two places:

- **`*/list`** — `tools/list`, `prompts/list`, `resources/list`,
  `resources/templates/list`. Denied handlers are filtered out *before*
  pagination and never appear in the response. Deny reasons are not
  surfaced — list time is a discovery surface and should not leak why a
  handler was hidden.
- **`tools/call`** (and prompt/resource equivalents). After lookup,
  guards are evaluated. A `Deny` throws `JsonRpcException` with code
  `-32010 Forbidden` and message `"Forbidden: <reason>"` — the call never
  reaches the invoker chain, so interceptors after `AUTHORIZATION` (input
  validation, the reflective method call) don't run.

A denial does *not* return `CallToolResult.isError=true`. That shape is
for tool-level errors the model can reasonably recover from; auth failures
are infrastructure-level and the protocol-correct shape is JSON-RPC
`-32010` (ADR-0023).

### Reference implementation: `mocapi-spring-security-guards`

This module reads two annotations off handler methods at startup:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("admin:write")           // AND across listed scopes
@RequiresRole({"TENANT_ADMIN", "OPS"})  // OR across listed roles
public void tenantAdminOp(...) { ... }
```

Two customizers attach a `ScopeGuard` and/or `RoleGuard` when those
annotations are present. Each guard reads
`SecurityContextHolder.getContext().getAuthentication()` at call time —
so the same `Authentication` populated by `mcpFilterChain` is what the
guard inspects. Both annotations may coexist; the combined effect is the
SPI's natural AND semantics.

`@RequiresScope` matches granted authorities with the `SCOPE_` prefix
Spring Security's JWT and opaque-token converters produce.
`@RequiresRole` accepts bare or `ROLE_`-prefixed values.

Other entitlement models — tenant checks, rate limits, mTLS subject
matching — are user or third-party concerns. Mocapi does not bake any of
them into core; the SPI is the seam.

## Initialize bypasses guards

The `initialize` JSON-RPC method does not flow through the per-handler
invoker chain — it is dispatched directly by the protocol layer. Guards
are not consulted. A client can always handshake. This is necessary for
the metadata + capability negotiation to work even on locked-down servers.

Per-handler guards take effect only on `tools/call`, `prompts/get`,
`resources/read`, `resources/templates/read`, and the matching `*/list`
operations.

## End-to-end flow

An authenticated `tools/call` on a Streamable HTTP deployment with
`mocapi-oauth2` and `mocapi-spring-security-guards` both present:

```
Client ──POST /mcp + Authorization: Bearer eyJ…──▶ Servlet container
                                                   │
                            mcpMetadataFilterChain │ (URL doesn't match — skipped)
                                  mcpFilterChain   ▼
                              ┌─ BearerTokenAuthenticationFilter
                              │   ├─ JwtDecoder: signature, exp, aud
                              │   ├─ JwtAuthenticationConverter: claims → authorities
                              │   └─ SecurityContextHolder.set(Authentication)
                              │  (on failure: 401 WWW-Authenticate + resource_metadata)
                              ▼
                       StreamableHttpController.handleCall
                              │
                              │  spawns virtual thread; context-propagation
                              │  carries SecurityContext, Observation, MDC across
                              ▼
                       JSON-RPC dispatch → tools/call
                              │
                              ▼
                       Handler lookup by tool name
                              │
                              ▼
                       AUTHORIZATION stratum
                              │  Guards.evaluate(handler.guards())
                              │   ├─ ScopeGuard   reads SecurityContextHolder
                              │   └─ RoleGuard    reads SecurityContextHolder
                              │  Allow ───► continue
                              │  Deny  ───► throw JsonRpcException(-32010, reason)
                              ▼
                       VALIDATION stratum (input schema, Jakarta)
                              │
                              ▼
                       INVOCATION (reflective call into user code)
```

Every stratum outside `AUTHORIZATION` (CORRELATION/MDC, OBSERVATION/Micrometer,
AUDIT) wraps the guard evaluation, so a denial is logged, observed, and
audited as `outcome=forbidden` even though the user method never ran. See
[`observability-stack.md`](observability-stack.md) for the stratum order.

## Why split into two layers

Authentication answers "who is this caller?" and is uniformly applied
across every request that reaches the MCP endpoint. Authorization answers
"may this caller do this thing?" and is per-handler. Conflating them
into Spring Security's `authorizeHttpRequests` would force every per-tool
rule to be expressed as a URL pattern, which doesn't work — `tools/call`
is one URL serving N tools.

The split also matches the deployment story. `mocapi-oauth2` is mandatory
for HTTP-bearer deployments; `mocapi-spring-security-guards` is optional
and can be replaced or augmented by user-supplied Guard implementations
without touching the OAuth2 wiring.

## Related

- [Authorization guide](../guides/authorization.md) — user-facing
  configuration recipes for OAuth2 + guards.
- [Guards guide](../guides/authorization.md#per-handler-authorization) —
  annotation usage.
- [`docs/guards.md`](../guides/guards.md) — Guard SPI details.
- [ADR-0012](../adr/0012-guard-spi.md) / [ADR-0013](../adr/0013-oauth2-and-reference-guards.md).
- [`observability-stack.md`](observability-stack.md) — where AUTHORIZATION
  sits in the interceptor strata.
