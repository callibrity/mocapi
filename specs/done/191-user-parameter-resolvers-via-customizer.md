# User-specified `ParameterResolver`s via the customizer SPI

## What to build

Add `config.resolver(ParameterResolver<? super X>)` to every
`*HandlerConfig`, matching the `interceptor(...)` / `guard(...)`
mutators already on it. Hardcode the structural (mocapi-owned)
resolvers inside each `*Handlers` builder instead of passing them
through autoconfig as a parameter. Users attach their own
`ParameterResolver`s via customizer beans; those get layered
alongside the structural set so handler methods can declare
bespoke parameter types like `@CurrentTenant String tenant` or
`@Caller Authentication auth`.

### Why

Today the resolver list is hardcoded in each autoconfig and
passed through `*Handlers.build(...)`. That shape is two
wrongs on top of each other:

1. **Not blind-wired, but also not extensible.** It's not
   `@Autowired`, so there's no ripcurl-style hazard — but users
   can't actually add their own resolvers without forking the
   autoconfig. "Looks extensible, isn't."
2. **Plumbing mismatch with the customizer SPI.** Interceptors
   and guards flow through customizers; resolvers don't. Three
   kinds of cross-cutting attachment, two paths. Unify.

The structural resolvers (context resolver, Jackson deserializer
for tools, `StringMapArgResolver` for prompt/template args) are
mocapi's own wiring — they belong owned inside the builder, not
threaded through autoconfig.

### Shape

**Config mutator added:**

```java
public interface CallToolHandlerConfig {
    Tool descriptor();
    Method method();
    Object bean();
    CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);
    CallToolHandlerConfig guard(Guard guard);
    CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver);   // NEW
}
```

Same shape on `GetPromptHandlerConfig` (type param `? super Map<String, String>`),
`ReadResourceHandlerConfig` (type param `? super Object`),
`ReadResourceTemplateHandlerConfig` (type param `? super Map<String, String>`).

**Builder signature changes:** `*Handlers.build(...)` loses the
`List<ParameterResolver<? super X>> resolvers` parameter. For the
tool builder, gains `ObjectMapper objectMapper` (previously carried
indirectly via the passed-in resolvers). Other builders' signatures
simplify by one parameter.

**Inside the builder:** structural resolvers are constructed
locally. Customizer-added resolvers (collected from the Config
after customizers have run) are placed ahead of any catch-all
structural resolver in the final chain.

**Ordering rule, in full:** Methodical's resolver selection is
"first `supports()`-true wins." Order only affects correctness
when multiple resolvers could claim the same parameter — which
in mocapi's structural set only happens because of catch-alls:
`Jackson3ParameterResolver` (tools) and `StringMapArgResolver`
(prompts / resource templates) both claim any parameter they're
handed. Everything else is guarded by an annotation or specific
type check and won't fight other specific resolvers.

So the rule reduces to: **catch-all resolvers come last.** User
resolvers — expected to be guarded by their own annotation or
type check — can sit anywhere ahead of the catch-all; order
among specific resolvers doesn't matter. Builders place the
customizer-added set immediately before the catch-all slot,
which is the simplest "somewhere before the catch-all" choice.

### Example — `@CurrentTenant` resolver

User writes a resolver that pulls the tenant off the current
session:

```java
public final class CurrentTenantResolver implements ParameterResolver<JsonNode> {

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(CurrentTenant.class)
                && parameter.getType() == String.class;
    }

    @Override
    public Object resolve(Parameter parameter, JsonNode args) {
        if (!McpSession.CURRENT.isBound()) {
            throw new IllegalStateException("@CurrentTenant requires a bound session");
        }
        return McpSession.CURRENT.get().attribute("tenant");
    }
}
```

Attaches via a customizer bean:

```java
@Bean
CallToolHandlerCustomizer currentTenantResolverCustomizer() {
    CurrentTenantResolver resolver = new CurrentTenantResolver();
    return config -> config.resolver(resolver);
}
```

Handler method declares it:

```java
@McpTool(name = "list_tenant_widgets")
public List<Widget> listTenantWidgets(@CurrentTenant String tenant) {
    return widgetService.listForTenant(tenant);
}
```

No reflection at call time beyond what Methodical already does;
the resolver's `supports` check short-circuits on annotation
presence.

### Autoconfig changes

Each `MocapiServer*AutoConfiguration`:

- Drops the local `List.of(...)` resolver construction.
- Drops the `resolvers` parameter when calling `*Handlers.build`.
- For tools: passes `ObjectMapper` into the builder (previously
  threaded via resolvers it constructed).

Customizer beans for resolvers — if any are shipped by mocapi
itself — follow the same pattern as guards: one autoconfig bean
per handler kind.

**Mocapi does not ship a built-in user-facing parameter resolver
in this spec.** This is infrastructure for user / 3rd-party
resolvers; no new `@CurrentTenant`-style annotations land here.

### Relationship to spec 188

Spec 188 removes the blind `@Autowired(required = false)
List<MethodInterceptor<...>>` parameters. This spec is the
resolver analog — except the resolver list was never blindly
wired, so the change here is about extensibility + consistency
rather than closing a hazard. Either order works, but since both
touch the same autoconfig and builder signatures, sequencing them
back-to-back (188 first, 191 second) minimizes churn.

## Acceptance criteria

- [ ] Each `*HandlerConfig` gains a
      `resolver(ParameterResolver<? super X>) -> *HandlerConfig`
      method.
- [ ] Each `*Handlers.build(...)` helper no longer accepts a
      `List<ParameterResolver<? super X>>` parameter.
- [ ] `CallToolHandlers.build` accepts an `ObjectMapper` parameter
      (previously threaded via the injected resolver list).
- [ ] Structural resolvers are constructed inside each builder.
      Customizer-added resolvers are collected from the Config
      and interleaved into the resolution chain per the order
      table above.
- [ ] `MocapiServerToolsAutoConfiguration`,
      `MocapiServerPromptsAutoConfiguration`,
      `MocapiServerResourcesAutoConfiguration` no longer construct
      resolver lists; they simply pass through customizers and
      whatever other params each builder needs.
- [ ] Unit tests per kind: register a customizer that attaches a
      `ParameterResolver` for a custom annotation + type; invoke
      the handler via a `MethodInvocation` stub; assert the
      resolver ran and its value was bound to the parameter.
- [ ] Unit test per kind: customizer-added resolver takes
      precedence over the structural catch-all when both could
      claim the parameter.
- [ ] Integration test: register a custom resolver via `@Bean`
      customizer, call through the Streamable HTTP transport,
      confirm the custom parameter is populated.
- [ ] `docs/tools-guide.md` and `docs/prompts-guide.md` get a
      short "Custom parameter resolvers" section demonstrating
      the customizer + `@CurrentTenant` pattern.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry noting
      per-handler custom `ParameterResolver` support via
      customizers.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Shipping a built-in `@CurrentTenant` / `@Caller` resolver.**
  This spec is the plumbing; resolver libraries can ship
  separately (`mocapi-current-session-resolvers` or in user
  code). If one of those becomes obviously table-stakes, file
  its own spec.
- **Reordering the existing structural resolver sequence.** Only
  change: the customizer-added slot is inserted per the table.
- **Changing how `ParameterResolver` itself works** — this
  stays Methodical's SPI as-is.
- **Autowiring user resolvers blindly from the context.** Users
  attach via customizers, same uniform pattern as interceptors
  and guards. No `@Autowired(required = false) List<ParameterResolver>`.

## Implementation notes

- The builder owns a small helper that produces its final
  resolver list: `List.of(...specific..., ...customizer..., ...catchall...)`.
  Keep the ordering logic in one place per kind.
- Spec 188's removal of the interceptor list parameter is the
  direct model for this spec's resolver-list parameter removal.
  Same pattern.
- `ParameterResolver` has no state; attaching the same resolver
  instance to many handlers is fine. Users typically construct
  one `@Bean`-scoped resolver and attach it via a customizer
  that references it.
- Resolver priority for the same parameter is "first wins," per
  Methodical's semantics. The order table determines the
  winner when two resolvers could both claim a parameter.
