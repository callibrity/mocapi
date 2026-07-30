# ADR-0029 — Authorization SHOULD-level challenges: `required-scopes` for resource-level step-up, decline per-tool

- **Status:** Accepted
- **Date:** 2026-07-30

## Context

[ADR-0022](0022-2026-07-28-features-not-implemented.md) recorded two
SHOULD-level authorization items as "deferred" without deciding them: the
`scope` parameter and RFC 9728 `resource_metadata` on the 401
`WWW-Authenticate: Bearer` challenge, and `403 insufficient_scope` for
step-up authorization. The 1.0 release sweep reopened both against the
project's posture of adhering to SHOULD-level spec requirements by
default rather than treating them as optional.

**`resource_metadata` turned out to be already emitted.** mocapi runs on
Spring Security 7.0.5 (Spring Boot 4.0.5), whose
`BearerTokenAuthenticationEntryPoint.commence` adds `resource_metadata`
unconditionally, computing the absolute
`/.well-known/oauth-protected-resource` URL from the request. That entry
point is the default on the chain `McpFilterChains.createMcpFilterChain`
builds, and `MocapiOAuth2AutoConfigurationTest` already asserts the
parameter is present. Planning notes described this as a gap; that was a
Spring Security 6.x fact.

**`scope` on a bare 401 has no well-defined value.** Spring emits it only
for a `BearerTokenError` that carries one — i.e. `insufficient_scope`. A
401 from a missing or malformed token happens before any handler is
selected, so the server does not yet know which scopes the request would
have needed. The only set available is `mocapi.oauth2.scopes`, the
*advertised supported* set, already published as `scopes_supported` in the
RFC 9728 document that the challenge's `resource_metadata` points at.

**Resource-level step-up was not actually reachable.** The original plan
assumed a deployment could add a coarse scope check with an
`McpFilterChainCustomizer` calling
`auth.anyRequest().hasAuthority("SCOPE_…")`, making this "configuration,
not code." Verification against the Spring sources showed that is false in
two distinct ways. `HttpSecurity.authorizeHttpRequests` resolves to a
single shared rule registry via `getOrApply`, and
`AbstractRequestMatcherRegistry.anyRequest()` asserts
`!this.anyRequestConfigured` — mocapi already called it, so a customizer
calling it again fails the context at startup with "Can't configure
anyRequest after itself." Rewriting the customizer to use
`requestMatchers(...)` is worse: `RequestMatcherDelegatingAuthorizationManager`
returns on the first matching mapping, and mocapi's `anyRequest()` rule is
registered before customizers run, so the customizer's rule is never
consulted — no error, no enforcement. Documentation recommending that
pattern (which `docs/guides/authorization.md` did) was actively harmful:
a deployment could follow it and believe a scope was enforced when it was
not.

**Per-tool step-up conflicts with `visibility ≡ invocation`**
([ADR-0012](0012-guard-spi.md)). mocapi serves every method and tool from
one endpoint, so per-tool scope checks live in the Guard SPI inside
JSON-RPC dispatch, below the filter chain, which cannot see which tool is
targeted. A scope `Deny` both hides the tool from `tools/list` and rejects
the call. Step-up assumes the opposite shape — "you can see this tool but
need more scope." A compliant client never sees a scope-gated tool, so
never calls it, so never needs the prompt; and a 403 naming the required
scope would leak the existence and the entitlement of a deliberately
hidden handler.

## Decision

Adopt the SHOULDs at the granularity where they are meaningful; decline
the rest explicitly.

1. **401 `resource_metadata` — adopted**, inherited from Spring Security's
   default entry point. No mocapi code; the existing autoconfiguration
   test is the regression guard.
2. **`scope` on the 401 challenge — declined.** It would carry the
   advertised-supported set, duplicate information one hop away through
   `resource_metadata` → `scopes_supported`, on a less authoritative
   channel. Revisit only if a real client is shown to read the challenge
   parameter and not the metadata document.
3. **Resource-level `403 insufficient_scope` — adopted, via a new
   property.** `mocapi.oauth2.required-scopes` is an optional list, empty
   by default. Empty means the endpoint's rule stays `authenticated()`,
   identical to the behavior before the property existed. Non-empty means
   *all* listed scopes are required (AND, matching `@RequiresScope` at the
   handler layer), and Spring's `BearerTokenAccessDeniedHandler` emits the
   RFC 6750 §3.1 challenge. Enforcement is expressed inside
   `McpFilterChains.authorizeMcpEndpoint`, which is now the **single
   owner** of the endpoint's `anyRequest()` rule — the only arrangement
   that works given the registry and ordering constraints above.
4. **Per-tool `insufficient_scope` step-up — declined on posture
   grounds.** Scope-gated handlers stay hidden, and a call to one returns
   `-32010 Forbidden` (ADR-0023) without naming the missing scope. No
   Guard-denial → HTTP-status bridge, and no `McpErrorHandler` seam: that
   may be a reasonable extension point later, but it should not be
   justified by a feature that fights the guard model.

Two hazards are handled explicitly rather than left to chance.
`AuthorizationManagers.allOf` **grants** when handed zero managers, so the
empty-list case is branched rather than composed — composing it would turn
"no required scopes" into "permit everyone" and drop authentication
entirely. And a scope enforced but absent from `mocapi.oauth2.scopes` is
undiscoverable to clients, who would see only a 403 with no way to learn
what to request; mocapi logs a startup warning for that rather than
failing, since the advertised set may legitimately be supplied by a
replacement `ScopesSupportedMetadataCustomizer`.

## Consequences

**What this buys us.** Both SHOULDs are satisfied where they apply. The
resource-level path is now genuinely reachable and verified — previously
the documented recipe could not work at all. The strong per-tool story is
preserved: no code path can leak a hidden handler's name or entitlement
through an HTTP challenge. `mocapi-server` and the Guard SPI are
untouched, so no OAuth2 or HTTP concept leaks into the transport-agnostic
core ([ADR-0002](0002-protocol-transport-contract.md), the
[constitution](../constitution.md)).

**Costs.** A new public configuration property on the 1.0 surface, and a
fourth component on the `McpFilterChainConfig` record. There is no
per-tool step-up UX, deliberately: a client calling a scope-denied tool
gets `-32010` with no machine-readable hint about which scope would
unlock it. Resource-level enforcement is opt-in, so a deployment that
never sets `required-scopes` emits `insufficient_scope` nowhere — a
stated position, not a gap. `McpFilterChainCustomizer` is now explicitly
not the place for authorization rules, which narrows what that SPI is
for; the guide says so directly.

**Non-goals.** This does not change the Guard SPI, the guard denial code,
or what `*/list` filters. It adds no challenge to any JSON-RPC-level
error. It says nothing about stdio, which has no bearer tokens and no HTTP
challenge to carry — `required-scopes` is inert there.

**Verification.** `McpRequiredScopesTest` covers: a token with the
required scope reaching the endpoint; a token without it getting 403; the
challenge actually containing `error="insufficient_scope"`; AND rather
than OR across multiple scopes; an unauthenticated request still getting
401 rather than 403; and — for the empty-list default — an authenticated
token passing while an unauthenticated one is still rejected. The tests
drive a real `Authorization: Bearer` header rather than the `jwt()`
post-processor, because Spring scopes `BearerTokenAccessDeniedHandler` to
a `BearerTokenRequestMatcher`: without the header the request falls
through to the plain `AccessDeniedHandlerImpl` and returns a bare 403,
which would pass a status-code assertion while testing none of the
challenge behavior. The suite was confirmed to fail against a deliberately
broken implementation before being accepted.

**Code anchors:**
`mocapi-oauth2/src/main/java/com/callibrity/mocapi/oauth2/McpFilterChains.java`
(`authorizeMcpEndpoint` / `allOfScopes` — the single owner of the endpoint
authorization rule),
`mocapi-oauth2/src/main/java/com/callibrity/mocapi/oauth2/MocapiOAuth2Properties.java`
(`requiredScopes`), and
`mocapi-spring-security-guards/src/main/java/com/callibrity/mocapi/security/spring/ScopeGuard.java`
(per-tool scope checks, which stay `Deny`-and-hide).
