# Guards

> For the runtime model (where guards run in the chain, list-time vs. call-time
> evaluation, why visibility ≡ invocation) see
> [`../design/extension-spi.md`](../design/extension-spi.md#guard-spi) and
> [`../design/authorization-model.md`](../design/authorization-model.md).
> For the design rationale see [ADR-0012](../adr/0012-guard-spi.md).

Mocapi's **Guard SPI** lets a plugin decide, per handler, whether a caller
may see and invoke that handler. A guard that denies hides the handler from
list operations (`tools/list`, `prompts/list`, `resources/list`,
`resources/templates/list`) *and* rejects any call to it with a JSON-RPC
forbidden error. The two decisions are unified — if you can't call it, you
can't see it.

The core SPI lives in `com.callibrity.mocapi.server.guards`. Mocapi does not
own any auth model: each guard implementation reaches into its own
framework of choice (Spring Security's `SecurityContextHolder`,
`McpExchange.CURRENT`, a servlet request, a plain `ScopedValue`, …) for the
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
guard denies, the call throws a `JsonRpcException` with code `-32010`
(`JsonRpcErrorCodes.FORBIDDEN`; a mocapi-private code in JSON-RPC's
implementation-defined sub-range, ADR-0023 — the 2026-07-28 spec's
`MissingRequiredClientCapabilityError` lives at `-32021`) and message `"Forbidden: <reason>"`, where
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

**Protocol-level methods.** Calls that don't reach a user handler
(`server/discover`, the list methods' dispatch itself) don't pass through
guards; guards protect tool/prompt/resource handler invocations.

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
`-32010` with that reason at call time. See
[authorization.md](authorization.md) for the enterprise deployment
shape (`mocapi-oauth2` + `mocapi-spring-security-guards` + a transport
starter). Other guard packages (tenant checks, rate limits, custom auth
schemes) are user or third-party concerns.

### Externalizing scope/role values

Each element of `@RequiresScope`/`@RequiresRole`'s `value()` resolves
`${...}`/`#{...}` placeholders via the same `mcpAnnotationValueResolver`
convention as every other mocapi annotation (see
[Externalizing Metadata](externalizing-metadata.md)), resolved once at
handler-build (startup) time — before the `ScopeGuard`/`RoleGuard`
instance is constructed:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("${app.admin-scope}")
public void tenantAdminOp(...) { ... }
```

```properties
app.admin-scope=admin:write
```

A placeholder that fails to resolve fails application startup with
Spring's standard `PlaceholderResolutionException` — the same fail-fast
behavior as every other externalized mocapi annotation attribute.
