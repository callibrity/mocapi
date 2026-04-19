# Guards

Mocapi's **Guard SPI** lets a plugin decide, per handler, whether a caller
may see and invoke that handler. A guard that denies hides the handler from
list operations (`tools/list`, `prompts/list`, `resources/list`,
`resources/templates/list`) *and* rejects any call to it with a JSON-RPC
forbidden error. The two decisions are unified — if you can't call it, you
can't see it.

The core SPI lives in `com.callibrity.mocapi.server.guards`. Mocapi does not
own any auth model: each guard implementation reaches into its own
framework of choice (Spring Security's `SecurityContextHolder`,
`McpSession.CURRENT`, a servlet request, a plain `ScopedValue`, …) for the
runtime state it needs.

## The SPI

Three types, no framework coupling:

```java
@FunctionalInterface
public interface Guard {
  GuardDecision check();
}

public sealed interface GuardDecision {
  record Allow() implements GuardDecision {}
  record Deny(String reason) implements GuardDecision {}
}
```

A `Guards.evaluate(List<Guard>)` helper walks the list with AND semantics
and short-circuits on the first `Deny`. Empty list → `Allow`.

## Attachment

Guards attach via the existing customizer SPI. Each handler-kind config
interface (`CallToolHandlerConfig`, `GetPromptHandlerConfig`,
`ReadResourceHandlerConfig`, `ReadResourceTemplateHandlerConfig`) has a
`guard(Guard)` mutator alongside `interceptor(...)`:

```java
@Bean
CallToolHandlerCustomizer scopeGuardCustomizer() {
  return config -> {
    RequiresScope annotation = config.method().getAnnotation(RequiresScope.class);
    if (annotation != null) {
      String required = annotation.value();
      config.guard(() -> scopesOfCurrentCaller().contains(required)
          ? new GuardDecision.Allow()
          : new GuardDecision.Deny("requires scope " + required));
    }
  };
}
```

The customizer runs once at handler-build time; the guard closes over
whatever annotation state it pulled (in this case, the required scope
string). The runtime check is a single method call — no reflection on the
hot path.

Multiple customizers may attach multiple guards to the same handler;
ordering is controlled by Spring's `@Order` on the customizer bean. Because
the semantics are AND with short-circuit, cheap checks can be registered
first for performance. Users wanting OR semantics implement that inside a
single guard class.

## Runtime semantics

**Call time.** After lookup, the service evaluates the guard list. If any
guard denies, the call throws a `JsonRpcException` with code `-32003`
(`JsonRpcErrorCodes.FORBIDDEN`) and message `"Forbidden: <reason>"`, where
`<reason>` comes from the first denying guard. Tools do *not* return
`CallToolResult.isError=true` for guard denies — that would invite an LLM
to "self-correct" on an auth failure, which is nonsense. Guard failure is
an infrastructure-level rejection, JSON-RPC error is the right shape.

**List time.** The list operation streams the registered handlers, filters
by guard evaluation, maps to descriptors, and paginates the filtered
result. Denied handlers do not appear in the response; the deny `reason` is
never surfaced at list time to avoid information leak.

**Interceptors versus guards.** Interceptors (MDC, o11y, input-schema
validation, user-attached logic) run *inside* the handler's invoker chain.
Guards run in the service layer *before* the invoker chain executes. A
denied call never reaches its interceptors at all.

**Initialize.** The `initialize` protocol call doesn't pass through any
handler, so guards don't apply to it.

## Reference implementation

The `mocapi-spring-security-guards` module is the first real Guard
implementation mocapi ships. It reads two method-level annotations off
user handler methods at startup and attaches the matching guards via the
customizer SPI:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("admin:write")          // all listed scopes required (AND)
@RequiresRole({"TENANT_ADMIN", "OPS"}) // any listed role grants access (OR)
public void tenantAdminOp(...) { ... }
```

Both guards (`ScopeGuard`, `RoleGuard`) read
`SecurityContextHolder.getContext().getAuthentication()` at call time —
no reflection on the hot path. Deny reasons include which scope(s) are
missing for the scope case, or `"insufficient role"` for the role case.
Denial of either hides the handler at list time and returns JSON-RPC
`-32003` with that reason at call time. See
[docs/authorization.md](authorization.md) for the enterprise deployment
shape (`mocapi-oauth2` + `mocapi-spring-security-guards` + a transport
starter). Other guard packages (tenant checks, rate limits, custom auth
schemes) are user or third-party concerns.
