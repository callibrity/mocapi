# Migrate MDC interceptor to the handler customizer SPI

## What to build

Move `McpMdcInterceptor` (shipped in spec 178) off the bean-level
`List<MethodInterceptor<? super ...>>` autowiring path and onto the
per-handler customizer SPI introduced in spec 180. After this change,
`mocapi-logging` attaches MDC via four `*HandlerCustomizer` beans —
one per handler kind — instead of one `MethodInterceptor<Object>` bean
picked up globally.

### Why

1. **Ripcurl 2.7.0 double-pickup.** Today
   `DefaultAnnotationJsonRpcMethodProviderFactory` scans for
   `MethodInterceptor<? super JsonNode>` beans at the JSON-RPC
   dispatch layer. A bean of type `MethodInterceptor<Object>` matches
   that bound and gets installed there too, in addition to mocapi's
   handler chains. For MDC that's wasted work — and at the
   JSON-RPC-dispatch layer `HandlerKinds.kindOf(method)` is called
   against ripcurl's own `@JsonRpcMethod` handlers (e.g. `tools/call`,
   `prompts/get`), which are **not** `@McpTool`/`@McpPrompt`-annotated.
   Kind comes back null, the handler-name MDC key gets skipped, and
   then the inner handler-layer invocation re-sets everything
   correctly. Benign today, but wrong in shape.

2. **Pattern consistency with spec 182 (o11y).** The o11y starter is
   customizer-based from day one. Leaving MDC on the bean path means
   two parallel tiers doing the same thing. Picking one — customizers
   — and retrofitting MDC keeps the contributor story clean.

3. **Zero per-call reflection.** The current interceptor calls
   `HandlerKinds.kindOf(method)` and `HandlerKinds.nameOf(method)` on
   every invocation. Customizer-based MDC bakes the kind/name strings
   into the interceptor at startup; the hot path only touches
   `MDC.put`/`MDC.remove` and a `ScopedValue.isBound()` check for
   session id.

### What changes in `mocapi-logging`

**Before (current):**

```
McpMdcInterceptor implements MethodInterceptor<Object>
MocapiLoggingAutoConfiguration exposes one `McpMdcInterceptor` @Bean
```

**After:**

```
McpMdcInterceptor                       (kind + name closed over, session from ScopedValue)
MocapiLoggingAutoConfiguration exposes four customizer @Beans:
  - CallToolHandlerCustomizer            → new McpMdcInterceptor("tool", toolName)
  - GetPromptHandlerCustomizer           → new McpMdcInterceptor("prompt", promptName)
  - ReadResourceHandlerCustomizer        → new McpMdcInterceptor("resource", uri)
  - ReadResourceTemplateHandlerCustomizer → new McpMdcInterceptor("resource_template", uriTemplate)
```

The interceptor becomes:

```java
public final class McpMdcInterceptor implements MethodInterceptor<Object> {

    private final String handlerKind;   // closed over at construction
    private final String handlerName;   // closed over at construction

    public McpMdcInterceptor(String handlerKind, String handlerName) {
        this.handlerKind = handlerKind;
        this.handlerName = handlerName;
    }

    @Override
    public Object intercept(MethodInvocation<?> invocation) {
        var sessionId = McpSession.CURRENT.isBound() ? McpSession.CURRENT.get().sessionId() : null;
        var added = new ArrayList<String>(3);
        putIfPresent(added, HANDLER_KIND, handlerKind);
        putIfPresent(added, HANDLER_NAME, handlerName);
        putIfPresent(added, SESSION, sessionId);
        try {
            return invocation.proceed();
        } finally {
            for (String key : added) {
                MDC.remove(key);
            }
        }
    }

    // putIfPresent / blankToNull unchanged
}
```

`HandlerKinds` is no longer called from the interceptor — the
autoconfig pulls names off each Config's descriptor directly
(`config.descriptor().name()` for tools / prompts,
`config.descriptor().uri()` for resources, `uriTemplate()` for
templates). `HandlerKinds` stays in `mocapi-api` — it remains useful
for any downstream code that needs to classify a `Method` outside of
a Config-carrying context — but no longer drives `mocapi-logging`.

### Autoconfig shape

```java
@Bean
CallToolHandlerCustomizer mcpMdcToolCustomizer() {
    return config -> config.interceptor(
            new McpMdcInterceptor("tool", config.descriptor().name()));
}

@Bean
GetPromptHandlerCustomizer mcpMdcPromptCustomizer() {
    return config -> config.interceptor(
            new McpMdcInterceptor("prompt", config.descriptor().name()));
}

@Bean
ReadResourceHandlerCustomizer mcpMdcResourceCustomizer() {
    return config -> config.interceptor(
            new McpMdcInterceptor("resource", config.descriptor().uri()));
}

@Bean
ReadResourceTemplateHandlerCustomizer mcpMdcResourceTemplateCustomizer() {
    return config -> config.interceptor(
            new McpMdcInterceptor("resource_template", config.descriptor().uriTemplate()));
}
```

### Scope / behavior implications

**Where MDC context is visible:** Same scope as today — from the
moment a handler's Methodical invocation chain enters this
interceptor until it exits. Logs emitted from user handler code carry
the MDC context. Logs emitted **before** the handler is dispatched
(transport acceptance, JSON-RPC parsing, session lookup) do **not**
carry session/handler MDC keys. That was already the case under the
bean-level path — nothing regresses. A proper "MDC across the whole
request" story needs a transport-layer filter, which is out of scope.

**Ripcurl layer no longer runs MDC.** Today the ripcurl layer
double-sets MDC keys (badly — kind comes back null). After this
change, ripcurl's JSON-RPC dispatch runs no MDC code. If a
ripcurl-internal log line happened to show an MDC key today, it
won't after this change. Those log lines weren't useful (the keys
were stale from a previous invocation or null) — no regression worth
advertising, but call it out in the CHANGELOG for completeness.

### No bean-level path removal (yet)

This spec migrates `mocapi-logging` off the bean-level path but does
**not** remove the
`@Autowired(required = false) List<MethodInterceptor<? super ...>>`
parameters from the three `mocapi-server` autoconfigs. Rationale:
external users may have their own `MethodInterceptor` beans riding
that path, and we haven't given them deprecation time. Removing the
autowiring is a follow-up spec once 181 + 182 ship and the in-tree
story is 100% customizer-based.

## Acceptance criteria

- [ ] `McpMdcInterceptor` constructor takes `handlerKind` and
      `handlerName` as `String`s; instance fields are `final`.
- [ ] `McpMdcInterceptor.intercept` does not reference
      `HandlerKinds` or `invocation.method()`.
- [ ] `MocapiLoggingAutoConfiguration` no longer exposes an
      `McpMdcInterceptor` bean. It exposes exactly four customizer
      beans: `CallToolHandlerCustomizer`,
      `GetPromptHandlerCustomizer`, `ReadResourceHandlerCustomizer`,
      `ReadResourceTemplateHandlerCustomizer`.
- [ ] Each customizer reads the descriptor name / uri / uriTemplate
      off the config and bakes it into the interceptor.
- [ ] Unit tests in `mocapi-logging/src/test`: for each handler kind,
      construct a stub Config, run the customizer, invoke the
      attached interceptor through a `MethodInvocation` stub, assert
      the expected MDC keys are set inside `proceed()` and removed
      after.
- [ ] Bean-level MDC interceptor test (if one exists) removed — it
      tests behavior that no longer exists in this module.
- [ ] `docs/logging.md` updated if it describes the wiring (keep the
      Logback pattern snippet; only the "how it's wired" bit — if
      present — changes).
- [ ] `docs/observability-roadmap.md`: update the MDC shipped entry
      to mention customizer-based wiring.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Changed`: entry noting
      MDC now attaches per-handler via customizers; no user-visible
      behavior change (same keys, same scope).
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.
- [ ] Existing integration tests relying on MDC keys at
      handler-invocation time still pass untouched.

## Non-goals

- **Removing the bean-level interceptor autowiring in
  `mocapi-server`.** Handled in a follow-up spec after 181 + 182.
- **Broadening MDC coverage to pre-handler code paths** (transport,
  JSON-RPC dispatch). Needs a transport-layer filter — separate spec.
- **Adding `mcp.request` MDC key.** Blocked on a request-id scoped
  value (noted in spec 178 as a follow-up); unchanged by this spec.

## Implementation notes

- Depends on spec 180 (customizer SPI) landing first. Ralph runs
  specs in order, so 180 → 181 → 182 is the correct sequence.
- `mocapi-logging` already depends on `mocapi-api`; no new module
  dependencies needed.
- The current per-call `HandlerKinds.kindOf(method)` call cost is
  negligible, but removing it keeps the hot path cleanly free of
  reflection — matching spec 182's o11y interceptor shape.
