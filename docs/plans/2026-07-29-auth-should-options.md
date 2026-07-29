# Authorization SHOULDs — two plans for review

**Status:** for decision. Pick Option A or Option B; the chosen one becomes
ADR-0029 + implementation. Written 2026-07-29.

## What we're deciding

ADR-0022 deferred two SHOULD-level authorization items; we're reopening them for
1.0 against the "adhere to SHOULD by default" posture:

1. **`scope` + RFC 9728 `resource_metadata` on the 401 `WWW-Authenticate` challenge.**
2. **`403 insufficient_scope` step-up** when a token is valid but lacks a required scope.

### Facts that shape the choice

- **OAuth2 in mocapi is HTTP-only** (ADR-0013) — it lives at the Spring Security
  filter layer, in front of JSON-RPC dispatch. stdio has no bearer tokens.
- **Spring Security emits `403 insufficient_scope` natively** — via
  `BearerTokenAccessDeniedHandler` — but only for authorization decided at the
  **HTTP filter chain** (per-URL, e.g. `.requestMatchers("/mcp").hasAuthority("SCOPE_x")`).
  It does **not** emit RFC 9728 `resource_metadata` on the 401 (newer than Spring's
  built-in), so item (1) needs a small entry-point customization either way.
- **mocapi has one endpoint (`/mcp`) for every method/tool.** Per-tool scope checks
  happen in the **Guard SPI, inside JSON-RPC dispatch** — *below* Spring's filter,
  which can't see which tool is being called.
- **Guards are visibility ≡ invocation** (ADR-0012): a scope `Deny` both **hides**
  the tool from `tools/list` *and* rejects the call. `ScopeGuard` today reads
  `SCOPE_` authorities off the `Authentication`, AND-checks the required set,
  computes the missing scopes, and returns `Deny("missing scope(s): …")` → `-32010`.

### The crux

A per-tool `insufficient_scope` **step-up** assumes "you can see the tool but need
more scope for this operation." mocapi's fine-grained model is "you lack the scope
→ you can't even see it." So per-tool step-up is in tension with hiding:

- A compliant client never sees a scope-gated tool, so never calls it, so never
  needs a step-up prompt.
- Emitting `403 insufficient_scope: need "admin" for "wipe"` to a caller who could
  not see `wipe` **leaks the existence and scope requirement of a hidden tool** — a
  posture mocapi's hiding model deliberately avoids. (Today a call to a
  denied/hidden tool returns `-32010`: reveals existence, not the scope.)

---

## Shared by both options: item (1) — 401 challenge enrichment

Low-risk, uncontroversial, in **`mocapi-oauth2`** only.

- Wrap/replace the OAuth2 `AuthenticationEntryPoint` so the 401 `WWW-Authenticate:
  Bearer` challenge carries `resource_metadata="<RFC 9728 URL>"` (the
  `/.well-known/oauth-protected-resource` absolute URL) and `scope="<advertised
  scopes>"` (from `mocapi.oauth2.scopes`).
- **Files:** a `AuthenticationEntryPoint` bean/customizer in `mocapi-oauth2`
  (compose with Spring's `BearerTokenAuthenticationEntryPoint`), wired via the
  existing `McpFilterChainCustomizer`/metadata config; unit + integration test
  asserting the header contents on a 401.
- **Effort:** small (~half a day incl. tests).

---

## Option A — Full per-tool `insufficient_scope` bridge

Build the seam so per-tool `ScopeGuard` denials surface as HTTP `403
insufficient_scope`. **`mocapi-server` stays untouched.**

### Design
1. **`McpErrorHandler` SPI** (new) in `mocapi-streamable-http-transport`: given a
   JSON-RPC error (code + `data`), optionally return an HTTP status + headers. The
   transport composes registered handlers, falling back to the existing static
   `HttpStatusMapping` defaults (`-32601→404`, `-32020→400`). General-purpose
   error→HTTP extension point, reusable beyond this feature.
2. **`RequiredScopeMissingException(Set<String> requiredScopes)`** in
   **`mocapi-oauth2`**, plus a ripcurl `JsonRpcExceptionTranslator` for it (mirrors
   the existing `ElicitationNotSupportedExceptionTranslator`) → JSON-RPC error
   carrying the missing scopes in `data`; oauth2 owns the code (reserved `-32011`).
3. **oauth2 `McpErrorHandler`** maps that error → `403` + the challenge built with
   Spring's **`BearerTokenError` / `BearerTokenErrors.insufficientScope(...)`**
   (byte-aligned with Spring's format), enriched with `resource_metadata`.
4. stdio has no `McpErrorHandler`, so it degrades to the in-band `-32011` error.

### The wrinkle to resolve (important)
`ScopeGuard.check()` is called for **both** visibility (hide) and invocation
(reject) — it is context-blind. It cannot simply "throw on invocation, hide on
listing." So Option A must either:
- (i) keep `ScopeGuard` returning `Deny` (so it still hides), and have the
  **invocation path** — not the guard — translate a scope-flavoured denial into the
  exception. That requires the `Deny` to carry the missing scopes structurally
  (enrich `GuardDecision.Deny`, or a `ScopeDeny` subtype) — which re-touches the
  core Guard SPI; or
- (ii) accept that scope-denied tools are hidden **and**, if called anyway, return
  `403 insufficient_scope` with the scope hint — i.e. **accept the info-leak** of
  revealing a hidden tool's existence and required scope to an unauthorized caller.

Neither is free: (i) adds surface to the core Guard model; (ii) weakens the hiding
posture.

### Files touched
- `mocapi-streamable-http-transport`: `McpErrorHandler` SPI, wire it into
  `DirectMessageWriter`/the controller's error path, keep `HttpStatusMapping` as the
  default handler; tests.
- `mocapi-oauth2`: `RequiredScopeMissingException`, its `JsonRpcExceptionTranslator`,
  its `McpErrorHandler` (using `BearerTokenError`), the `-32011` constant; tests.
- `mocapi-spring-security-guards` (and/or core, per the wrinkle resolution):
  `ScopeGuard` change to signal missing scopes structurally; tests.
- `mocapi-autoconfigure`: register the oauth2 `McpErrorHandler` + translator.
- ADR-0029 (full bridge) + `authorization-model.md` update.

### Pros / cons
- **Pro:** full spec-shaped per-tool step-up; a reusable `McpErrorHandler` seam;
  reuses Spring's challenge machinery; core stays clean of OAuth2 *types*.
- **Con:** fights visibility ≡ invocation (info-leak or core Guard-SPI surface);
  more moving parts across 4 modules; the step-up UX it enables is arguably
  redundant with hiding for compliant clients.
- **Effort:** ~2–3 days incl. tests + ADR + design doc.

---

## Option B — Lean: native coarse step-up + keep per-tool hiding (recommended)

Do item (1); satisfy step-up at the granularity where it's meaningful and free;
consciously decline per-tool step-up on posture grounds.

### Design
1. **Item (1)** — the shared 401 enrichment above.
2. **Coarse, resource-level scopes** ("you need `SCOPE_mcp` to use this server at
   all") → **Spring Security's native filter-layer `403 insufficient_scope`**,
   already reachable via the existing `McpFilterChainCustomizer`
   (`.hasAuthority("SCOPE_…")` on `/mcp`). Zero new code; add a documented example
   + an integration test proving the native 403 + challenge.
3. **Per-tool scopes** → keep the current **hide + `-32010`** behaviour (ADR-0012).
   Optionally harden a call to a hidden tool from `-32010` to `-32601` (reveal
   nothing) — a separate, small decision.
4. ADR-0029 records: implement (1); adopt the native coarse path; **decline per-tool
   step-up**, citing visibility ≡ invocation + the info-leak.

### Files touched
- `mocapi-oauth2`: the 401 entry-point enrichment (item 1) + tests.
- `docs/guides/authorization.md`: document the coarse `insufficient_scope` recipe.
- An integration test asserting Spring's native coarse 403 challenge.
- ADR-0029 (lean) + `authorization-model.md` update.

### Pros / cons
- **Pro:** posture-consistent (hiding stays the strong per-tool story); minimal new
  surface; leans on Spring's native, well-tested machinery; satisfies the SHOULD at
  the level where step-up actually makes sense.
- **Con:** no per-tool step-up UX (deliberate); relies on users configuring a coarse
  `/mcp` scope to get the native 403.
- **Effort:** ~1 day incl. tests + ADR + design doc + guide.

---

## Comparison

| | Option A (full bridge) | Option B (lean, recommended) |
|---|---|---|
| Item (1) 401 enrichment | yes | yes |
| Coarse `403 insufficient_scope` | yes (via A's path or native) | yes (Spring native, free) |
| Per-tool step-up | yes | no (hide + `-32010`, by design) |
| New `McpErrorHandler` SPI | yes (reusable) | no |
| Touches core Guard SPI? | maybe (wrinkle (i)) | no |
| Consistent w/ ADR-0012 hiding | in tension | yes |
| Info-leak of hidden tools | possible (wrinkle (ii)) | no |
| Modules touched | 4 | 1–2 |
| Effort | ~2–3 days | ~1 day |

## Recommendation

**Option B.** Per-tool step-up genuinely conflicts with mocapi's visibility ≡
invocation posture, and the coarse case — where step-up is meaningful — is already
native in Spring. Option B satisfies the SHOULD where it applies, keeps the strong
hiding story, and adds almost no surface. The `McpErrorHandler` seam from Option A
is a nice extension point, but it shouldn't be justified *by* a feature that fights
the guard model — if we want it later (for some non-auth error mapping), it can land
on its own merits.

Pick one and I'll write the matching ADR-0029 + implement.
