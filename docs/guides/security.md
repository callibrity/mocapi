# Securing your MCP server

An MCP server executes tools on behalf of a remote caller. That makes it a
privileged surface: whatever your handlers can do — read data, call
downstream APIs, mutate state — a caller who reaches an unprotected endpoint
can do too. This guide is the pre-production checklist for closing the gap
between *"it runs"* and *"it's safe to expose."*

mocapi's design goal is that the framework owns the security primitives you
can't be expected to get right (token cryptography, audience enforcement,
replay prevention), while you own the deployment decisions only you can make
(is this endpoint public? which scopes gate it? where does TLS terminate?).
This guide draws that line explicitly.

> **The single most important thing on this page:** the Streamable HTTP
> endpoint is **unauthenticated by default**. Read [§1](#1-authentication-is-opt-in--this-is-the-big-one)
> before you deploy anything reachable from a network you don't control.

## The security contract at a glance

| Concern | Who owns it | Default posture |
|---|---|---|
| `requestState` token crypto (AES-256-GCM, principal-bound, TTL) | **mocapi** | Secure; you only supply the key |
| Bearer-token validation, audience enforcement, RFC 9728 discovery | **mocapi** (`mocapi-oauth2`) | Correct *once the module is present* |
| DNS-rebinding `Origin` allowlist (Streamable HTTP) | **mocapi** | On — localhost family; widen for real browser origins |
| Whether the endpoint requires authentication at all | **you** | **Open** until you add `mocapi-oauth2` |
| Which scopes/roles gate which handlers | **you** | None until you configure them |
| Validating tool *arguments* against your contract | **you** (`mocapi-jakarta-validation`) | Not validated unless you opt in |
| TLS, CORS, network exposure, rate limiting | **you** (deployment) | Not mocapi's layer |

Everything mocapi owns fails **closed**: a missing key rejects tokens, a
missing scope denies the call, an unverified principal is treated as nobody.
Everything you own is a decision mocapi cannot make for you — which is why the
rest of this page is a checklist.

## 1. Authentication is opt-in — this is the big one

The Streamable HTTP transport ships **no** Spring Security configuration.
Add `mocapi-streamable-http-spring-boot-starter`, write some tools, deploy,
and you have published an **unauthenticated tool-execution endpoint** at
`/mcp`. Nothing in the framework will warn you, because a public MCP server
is a legitimate thing to run — mocapi cannot tell "intentionally public" from
"forgot to add auth."

**If your endpoint is reachable by anyone you would not hand a shell to, add
OAuth2:**

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-oauth2-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-idp.example.com
          audiences: https://mcp.example.com   # MUST identify THIS server
mocapi:
  oauth2:
    resource: https://mcp.example.com          # must be a member of audiences
```

With the module present, `/mcp` requires a valid bearer token and the
`/.well-known/oauth-protected-resource` discovery document is served
automatically. Full setup — issuer/JWKS options, opaque tokens, the
audience-must-match-resource startup invariant — is in the
[Authorization guide](authorization.md).

The **stdio** transport is different: there are no bearer tokens because
there is no network. The client launches the server as a subprocess and the
OS process boundary is the trust boundary. `mocapi-oauth2` does not apply to
stdio, and that is correct — but it also means an stdio server trusts whoever
can launch it.

## 2. Set the MRTR secret in production

If any of your tools, prompts, or resources use
[elicitation](interactive-tools.md), the framework mints an encrypted
`requestState` token to carry round-trip state statelessly. It is
AES-256-GCM, bound to the authenticated principal, and TTL-checked — you
cannot weaken that. What you must supply is the **key**:

```yaml
mocapi:
  mrtr:
    secret: <base64-encoded 256-bit key>   # openssl rand -base64 32
    ttl: PT5M
```

If `mocapi.mrtr.secret` is unset, mocapi generates an ephemeral key at startup
and logs a prominent warning. This is **not insecure** — the crypto is
identical and a tampered or foreign token is still rejected — but:

- in-flight elicitations do not survive a restart, and
- **a multi-instance deployment cannot validate a peer's token**, so
  elicitation breaks behind a load balancer.

The failure mode is closed (rejection), not open (forgery), so this is a
correctness footgun rather than a hole — but every clustered deployment must
set it. See the [Configuration guide](configuration.md) for key-generation
one-liners.

## 3. Validate tool arguments

mocapi validates the *protocol* for you — the `_meta` envelope, routing
headers, protocol version, and the JSON shape of `params` — before your
handler runs. It does **not** validate that the `arguments` a caller sent
satisfy your tool's semantic contract. "Assuming your handler code is safe"
quietly assumes you checked your inputs.

Add Jakarta Bean Validation and annotate your parameters:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-jakarta-validation</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

```java
@McpTool(name = "transfer")
public Receipt transfer(@NotBlank String toAccount, @Positive @Max(10_000) long cents) { ... }
```

Constraint violations become a JSON-RPC `-32602` before your method body
executes. The mechanics are in the [Validation guide](validation.md). This
does not replace your own authorization logic inside the handler — it ensures
the values reaching that logic are well-formed.

## 4. Authorize per handler, not just at the door

Authentication answers *"who is this caller?"* Authorization answers *"may
this caller run **this** tool?"* — and those are different questions. A valid
token is not entitlement to every handler.

Two complementary layers:

- **Resource-level scope** — require a scope to reach the endpoint at all:
  ```yaml
  mocapi:
    oauth2:
      required-scopes: [mcp.use]
  ```
  A token missing it gets `403 insufficient_scope`. See
  [Authorization §resource-level](authorization.md#requiring-a-scope-to-reach-the-server-resource-level).

- **Per-handler scope/role** — gate individual tools, with the mocapi
  property that an unentitled caller never even *sees* the tool
  (visibility ≡ invocation — it is hidden from `tools/list`, not just
  rejected on call):
  ```java
  @McpTool(name = "tenant_admin_op")
  @RequiresScope("admin:write")
  @RequiresRole({"TENANT_ADMIN", "OPS"})
  public void adminOp(...) { ... }
  ```
  Requires `mocapi-spring-security-guards`; see the [Guards guide](guards.md).

Design tools so the *sensitive* ones carry an explicit `@RequiresScope`.
An un-annotated tool is reachable by any authenticated caller.

## 5. Deployment-layer controls mocapi does not provide

These are outside the framework by design — mocapi is transport-agnostic and
cannot make network decisions for you. They are still your responsibility:

- **TLS.** Bearer tokens are credentials; never accept them over plaintext
  HTTP. Terminate TLS at the server or a trusted proxy in front of it.
- **CORS.** If browser-based MCP clients call your endpoint, configure CORS
  deliberately via an `McpFilterChainCustomizer` (see
  [Authorization §Customizing the MCP chain](authorization.md#customizing-the-mcp-chain)) —
  do not reflexively allow all origins. CORS is separate from the built-in
  `Origin` allowlist ([above](#dns-rebinding-protection--what-it-is-and-isnt));
  browser clients from a non-default origin need *both* that origin in
  `mocapi.allowed-origins` and a matching CORS rule.
- **Network exposure & rate limiting.** Put the endpoint only where it needs
  to be reachable, and apply rate limiting at your gateway/proxy. mocapi runs
  each call on a virtual thread with no built-in per-caller quota.

## 6. What the framework already does for you

So the checklist above is the whole job — you are not also re-implementing
these:

- **Token cryptography** — AES-256-GCM `requestState`, per-caller principal
  binding so a token minted for one caller is rejected if replayed by
  another, TTL checked *after* authentication ([ADR-0021](../adr/0021-mrtr-elicitation-replay.md)).
- **Audience enforcement** — a token whose `aud` does not name this server is
  rejected, in both JWT and opaque-token modes; enforced at startup that the
  advertised `resource` is a member of the accepted audiences.
- **Fail-closed authorization** — empty required-scopes means
  *authentication only*, never "permit everyone"; scope checks are AND, not
  OR; an unverified or anonymous principal is nobody ([ADR-0029](../adr/0029-authorization-should-level-challenges.md)).
- **Protocol-boundary validation** — malformed envelopes, wrong routing
  headers, and unsupported protocol versions are rejected before your handler
  is reached.
- **No sensitive data on telemetry** — bearer tokens, `requestState`, and
  principal identity are never placed on spans or metrics
  ([ADR-0030](../adr/0030-otel-mcp-semconv-alignment.md)).
- **Errors don't leak internals** — failures surface as JSON-RPC error codes,
  not stack traces.
- **DNS-rebinding protection, on by default.** The Streamable HTTP transport
  validates the `Origin` header against `mocapi.allowed-origins` (default
  `localhost,127.0.0.1,[::1]`) and rejects a mismatch with `403 Forbidden:
  Invalid Origin`. This defends the common local-MCP-server case, where a
  malicious web page in the user's browser tries to reach a server bound to
  `localhost` — see below for the important scope of this control.

### DNS-rebinding protection — what it is and isn't

The `Origin` check ([`mocapi.allowed-origins`](configuration.md)) exists for
one specific threat: a browser, tricked by a malicious page, making requests to
an MCP server the *victim* can reach (typically `localhost`). Browsers attach
an `Origin` header they cannot forge across sites, so allowlisting origins
blocks that vector.

Two properties follow, and both matter:

- **A request with no `Origin` header is accepted.** This is deliberate —
  non-browser clients (curl, CLI tools, server-to-server) don't send `Origin`,
  and they aren't the rebinding threat. It also means the `Origin` check is
  **not an access-control mechanism**: anyone who can omit the header passes
  it. Authentication is [§1](#1-authentication-is-opt-in--this-is-the-big-one)'s
  job, not this one.
- **If browser clients call your server from a real origin**, add that origin's
  host to `mocapi.allowed-origins` — otherwise legitimate browser traffic gets
  a 403. Conversely, do **not** widen it to `*` or to hosts you don't control,
  or you reopen the rebinding vector.

This is distinct from **CORS** ([§5](#5-deployment-layer-controls-mocapi-does-not-provide)):
the `Origin` allowlist decides whether the *server processes* a cross-origin
request at all; CORS decides whether a *browser lets its own script read the
response*. You may need both.

## Pre-production checklist

- [ ] `/mcp` requires authentication (`mocapi-oauth2` present) — **unless the
      server is deliberately public**.
- [ ] `spring.security.oauth2.resourceserver.jwt.audiences` names *this*
      server, and `mocapi.oauth2.resource` is one of them.
- [ ] `mocapi.mrtr.secret` set to a real 256-bit key (required for any
      multi-instance deployment using elicitation).
- [ ] Sensitive tools carry `@RequiresScope` / `@RequiresRole`; a
      resource-level `required-scopes` is set if the whole server is gated.
- [ ] Tool parameters are validated (`mocapi-jakarta-validation` +
      constraints) or validated by hand in the handler.
- [ ] TLS terminates in front of the endpoint; bearer tokens never traverse
      plaintext.
- [ ] CORS is configured deliberately if browser clients are in scope, and any
      non-default browser origin is added to `mocapi.allowed-origins` (which
      defaults to the localhost family for DNS-rebinding protection).
- [ ] The endpoint is exposed only on the networks that need it, with rate
      limiting at the gateway.

## What is deliberately not implemented

mocapi's security scope has explicit edges, recorded so they are decisions
rather than surprises: DPoP / mTLS token binding, signed metadata, and
per-tool `insufficient_scope` step-up are not implemented — see
[ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md) and
[ADR-0029](../adr/0029-authorization-should-level-challenges.md) for the
reasoning. If your threat model needs one of these, that ADR is the place to
start the conversation.

## Related

- [Authorization](authorization.md) — OAuth2 resource-server setup in full
- [Guards](guards.md) — per-handler `@RequiresScope` / `@RequiresRole`
- [Validation](validation.md) — Jakarta Bean Validation on parameters
- [Interactive Tools](interactive-tools.md) — elicitation and the
  `requestState` token
- [Configuration](configuration.md) — every `mocapi.*` property, including key
  generation
