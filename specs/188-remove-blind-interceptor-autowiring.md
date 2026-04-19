# Remove blind bean-level `MethodInterceptor` autowiring from handler autoconfigs

## What to build

Delete the three `@Autowired(required = false)
List<MethodInterceptor<? super X>>` parameters from mocapi's handler
autoconfigs (and the corresponding parameters from the
`*Handlers.build(...)` helpers). Cross-cutting interceptor behavior
attaches via the customizer SPI (spec 180), period. No more silent
"any `MethodInterceptor` bean in the context gets applied to every
handler."

### Why

- **No in-tree users left.** MDC migrated off the bean-level path
  in spec 181. O11y never used it (spec 183). Guards attach via
  customizer (spec 187). The autowired lists are dead code kept
  for hypothetical external users.
- **Ripcurl 2.7.0 double-pickup hazard.** `DefaultAnnotationJsonRpcMethodProviderFactory`
  scans for `MethodInterceptor<? super JsonNode>` beans at the
  JSON-RPC dispatch layer. A bean matching that bound gets
  installed at both the JSON-RPC layer and the mocapi handler
  layer — two invocations per call, often misbehaving at the
  JSON-RPC layer (where our handler-kind / name resolution
  returns nothing sensible). The customizer tier is
  mocapi-internal and sidesteps this entirely.
- **Strictly weaker than customizers.** A bean-level interceptor
  list gives the user one thing: "apply this to every handler of
  this kind." A customizer gives the user that *and* per-handler
  metadata (descriptor, method, bean, annotations), conditional
  attachment, and ordering via Spring's `@Order`. There's no
  legitimate use case the bean-level path serves that a
  customizer doesn't.

### Migration for external users (if any)

Users who had a bean like:

```java
@Bean
MethodInterceptor<JsonNode> myToolInterceptor() {
    return new MyInterceptor();
}
```

rewrite as:

```java
@Bean
CallToolHandlerCustomizer myToolCustomizer() {
    return config -> config.interceptor(new MyInterceptor());
}
```

One line different. If they want the same interceptor applied to
multiple kinds, they contribute multiple customizers (one per
kind) — which is also the pattern MDC, o11y, and guards use.

Users who want *conditional* attachment (e.g., only on handlers
whose methods carry a particular annotation) now have it — the
old bean-level path couldn't do that at all:

```java
return config -> {
    if (config.method().isAnnotationPresent(Audited.class)) {
        config.interceptor(new AuditInterceptor());
    }
};
```

### Scope of changes

- **`MocapiServerToolsAutoConfiguration`**: drop `toolInterceptors`
  parameter; stop passing it to `CallToolHandlers.build`.
- **`MocapiServerPromptsAutoConfiguration`**: drop
  `promptInterceptors`; stop passing it to `GetPromptHandlers.build`.
- **`MocapiServerResourcesAutoConfiguration`**: drop
  `resourceInterceptors` and `resourceTemplateInterceptors`; stop
  passing them to the resource builders.
- **`CallToolHandlers.build`**: drop the `List<MethodInterceptor<? super JsonNode>>
  interceptors` parameter. Schema-validating trailing interceptor
  stays — that's kind-specific logic, not user-extensible.
- **`GetPromptHandlers.build`**: drop the interceptors parameter.
- **`ReadResourceHandlers.build`**: drop the interceptors parameter.
- **`ReadResourceTemplateHandlers.build`**: drop the interceptors parameter.
- **Unit tests** touching those builders: drop `List.of()` placeholder
  args.
- **Integration / autoconfig tests** asserting bean-list-based
  interceptor wiring (if any exist): remove, they're testing a
  removed path.

### Non-goals

- **Adding a resolver path to Config.** `ParameterResolver`s
  aren't autowired today (the resolver list is hardcoded in each
  autoconfig), so there's no blind-wiring issue there. If user
  demand for custom resolvers materializes, a separate spec adds
  `config.resolver(ParameterResolver<? super X>)` alongside
  `interceptor` and `guard`. Not part of this cleanup.
- **Breaking compat shim.** Pre-1.0, no deprecation cycle; the
  CHANGELOG migration note is the whole backwards-compat story.
- **Touching ripcurl's separate interceptor scan.** Ripcurl's
  `DefaultAnnotationJsonRpcMethodProviderFactory` still picks up
  `MethodInterceptor<? super JsonNode>` beans — that's ripcurl's
  JSON-RPC-level extension point, which is legitimate at its
  layer and not our concern to filter. This spec just ensures
  mocapi doesn't *also* apply those beans at the handler layer.

## Acceptance criteria

- [ ] `@Autowired(required = false) List<MethodInterceptor<? super ...>>`
      parameters removed from all three handler autoconfigs.
- [ ] `*Handlers.build(...)` helpers no longer accept an interceptor
      list parameter from external callers. (Kind-specific trailing
      interceptors, like the tool input-schema validator, are
      constructed inside the helper and stay.)
- [ ] All in-tree unit and integration tests still pass without
      passing interceptor lists to the builders.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Breaking changes`: entry
      noting that bean-level `MethodInterceptor<? super X>` wiring is
      removed; migration is "wrap in a customizer bean" with a one-
      sentence example.
- [ ] `docs/` — if any existing doc mentions the bean-level
      interceptor path as a supported extension point (I don't
      think we do — MDC and o11y docs already point at the
      customizer path), scrub it.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Implementation notes

- The autowired parameters are annotated `required = false`, so
  their removal is purely additive-delete; no `Optional` wrapper
  to unwrap, no nullability handling to collapse.
- The tool builder's `InputSchemaValidatingInterceptor` is not
  user-extensible and constructs in place. Leave it.
- After this spec, the only ways to attach a `MethodInterceptor`
  to a handler are: (a) via a `*HandlerCustomizer` bean, or
  (b) inside mocapi itself in trailing kind-specific positions
  (schema validation for tools). That's it, and that's the point.
