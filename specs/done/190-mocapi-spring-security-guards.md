# `mocapi-spring-security-guards`: annotation-driven guards backed by Spring Security

## What to build

Ship `mocapi-spring-security-guards`: a drop-in module that reads
Spring-Security-aware annotations (`@RequiresScope`,
`@RequiresRole`) off user handler methods at startup and attaches
corresponding `Guard` implementations via the per-handler
customizer SPI. Applies to both list-time filtering (hidden if
denied) and call-time authorization (JSON-RPC `-32003` on deny),
via the Guard SPI from spec 187.

First real Guard implementation mocapi ships. Everything else
(tenant checks, rate-limit guards, custom RBAC) is user/3rd-party
code; this module demonstrates the pattern and covers the common
OAuth2 / role cases out of the box.

### Why this module, why not just `@PreAuthorize`?

Spring Security's method-security (`@PreAuthorize`) already
exists. Via CGLIB proxies it intercepts invocations and throws
`AccessDeniedException` on denial. So why ship this module?

1. **List-time filtering.** `@PreAuthorize` only fires at call
   time — AOP has no path to walk the handler inventory and
   ask "is this user entitled to see this?" Clients doing
   `tools/list` still see every handler. Our guard path
   handles both sites uniformly — denied handlers disappear
   from list responses.
2. **Proper error mapping.** An AOP-thrown
   `AccessDeniedException` bubbles out of `server.handleCall`
   as a generic `-32603 Internal error`. Guards produce
   `-32003 Forbidden: <reason>` — the protocol-right shape.
3. **No AOP requirement.** Users whose handler beans aren't
   CGLIB-proxied (e.g., final classes, non-Spring-managed
   beans) still get method-annotation-driven auth via this
   module — customizers read annotations at startup
   regardless of proxy status.

Users with `@PreAuthorize` already applied via method security
aren't harmed: guards check first, so the AOP advice never sees
denied calls. Not redundant, just layered.

### Annotations

Two annotations, both method-level, both repeatable-friendly:

```java
// mocapi-spring-security-guards — com.callibrity.mocapi.security.spring

@Retention(RUNTIME) @Target(METHOD)
public @interface RequiresScope {
    String[] value();       // all scopes required (AND)
}

@Retention(RUNTIME) @Target(METHOD)
public @interface RequiresRole {
    String[] value();       // any role grants access (OR)
}
```

Rationale for AND on scopes / OR on roles: matches Spring
Security's own convention — `hasAllScopes(...)` vs `hasAnyRole(...)`
is how their expression DSL reads, and it's how operators
already think about the two concepts.

Both annotations may coexist on the same method. The Guard SPI's
AND semantics ensure both must pass:

```java
@McpTool(name = "tenant_admin_op")
@RequiresScope("admin:write")
@RequiresRole("TENANT_ADMIN")
public void tenantAdminOp(...) { ... }
```

Caller must have scope `admin:write` AND role `TENANT_ADMIN`. Two
guards attach independently; either denying rejects.

### Guard implementations

Reach into `SecurityContextHolder` themselves — core SPI
(`Guard`) stays framework-free, per spec 187.

```java
public final class ScopeGuard implements Guard {

    private static final String SCOPE_PREFIX = "SCOPE_";

    private final Set<String> requiredScopes;

    public ScopeGuard(String... scopes) {
        if (scopes.length == 0) {
            throw new IllegalArgumentException("ScopeGuard requires at least one scope");
        }
        this.requiredScopes = Set.copyOf(List.of(scopes));
    }

    @Override
    public GuardDecision check() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new Deny("unauthenticated");
        }
        Set<String> scopes = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(SCOPE_PREFIX))
                .map(a -> a.substring(SCOPE_PREFIX.length()))
                .collect(toUnmodifiableSet());
        if (!scopes.containsAll(requiredScopes)) {
            Set<String> missing = new TreeSet<>(requiredScopes);
            missing.removeAll(scopes);
            return new Deny("missing scope(s): " + String.join(", ", missing));
        }
        return new Allow();
    }
}
```

```java
public final class RoleGuard implements Guard {

    private static final String ROLE_PREFIX = "ROLE_";

    private final Set<String> allowedAuthorities;

    public RoleGuard(String... roles) {
        if (roles.length == 0) {
            throw new IllegalArgumentException("RoleGuard requires at least one role");
        }
        this.allowedAuthorities = Arrays.stream(roles)
                .map(r -> r.startsWith(ROLE_PREFIX) ? r : ROLE_PREFIX + r)
                .collect(toUnmodifiableSet());
    }

    @Override
    public GuardDecision check() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new Deny("unauthenticated");
        }
        boolean hasRole = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowedAuthorities::contains);
        if (!hasRole) {
            return new Deny("insufficient role");
        }
        return new Allow();
    }
}
```

Both guards are immutable; the scope / role sets close over at
construction. Reasonable constant-factor cost per call
(hash-set lookups). No reflection.

### Customizer attachment

Eight customizer beans: four handler kinds × two annotation
types. A helper avoids the cross-product boilerplate — see
"Implementation notes" below. Sketch:

```java
// mocapi-spring-security-guards — in the feature code module
// (NOT in mocapi-autoconfigure — see "Autoconfig wiring" below)

// One helper that knows how to attach both guards for one config:
static <C> void attachAnnotationGuards(
        C config, Method method, BiConsumer<C, Guard> applier) {
    RequiresScope scopeAnn = method.getAnnotation(RequiresScope.class);
    if (scopeAnn != null) {
        applier.accept(config, new ScopeGuard(scopeAnn.value()));
    }
    RequiresRole roleAnn = method.getAnnotation(RequiresRole.class);
    if (roleAnn != null) {
        applier.accept(config, new RoleGuard(roleAnn.value()));
    }
}
```

Autoconfig then has four one-liner customizer beans using that
helper.

### Autoconfig wiring

Lives in `mocapi-autoconfigure` (the consolidated autoconfigure
module from spec 186), in a new package
`com.callibrity.mocapi.security.spring.autoconfigure`:

```java
@AutoConfiguration
@ConditionalOnClass({ScopeGuard.class, Authentication.class})
public class MocapiSpringSecurityGuardsAutoConfiguration {

    @Bean CallToolHandlerCustomizer springSecurityToolGuardCustomizer() {
        return config -> SpringSecurityGuards.attach(
                config, config.method(), CallToolHandlerConfig::guard);
    }

    // ... same shape for prompt / resource / resource_template kinds
}
```

Conditional triggers:

- `@ConditionalOnClass(ScopeGuard.class)` — activates only when
  the `mocapi-spring-security-guards` module is on the
  classpath.
- `@ConditionalOnClass(Authentication.class)` — and only when
  Spring Security is on the classpath (defensive; if someone
  pulls in the module without Spring Security, autoconfig stays
  dormant instead of ClassNotFoundError-ing).

### Dependencies

`mocapi-spring-security-guards/pom.xml`:

- `com.callibrity.mocapi:mocapi-server` (for `Guard` SPI)
- `org.springframework.security:spring-security-core` (for
  `Authentication`, `SecurityContextHolder`, `GrantedAuthority`)

No transport dep. No dep on `mocapi-oauth2` — that module is
specifically about RFC 9728 protected-resource metadata; this
module is about reading auth state from SecurityContext. Users
can use this module without OAuth2 (e.g., a Spring app with
form-login populating SecurityContext the traditional way).

`mocapi-autoconfigure/pom.xml`: add `mocapi-spring-security-guards`
as `<optional>true</optional>` dependency (same pattern as every
other feature code module).

`mocapi-bom`: new entry.

### Example pom for an enterprise tenant-gated app

```xml
<!-- transport -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-streamable-http-spring-boot-starter</artifactId>
</dependency>

<!-- OAuth2 resource-server setup (MCP auth flow) -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-oauth2</artifactId>
</dependency>

<!-- scope / role guards driven off @RequiresScope / @RequiresRole -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-security-guards</artifactId>
</dependency>
```

Three deps. That's the whole enterprise authz story.

## Acceptance criteria

- [ ] New module `mocapi-spring-security-guards` under root
      `pom.xml` modules list.
- [ ] `mocapi-bom` entry.
- [ ] Classes in module:
      `RequiresScope`, `RequiresRole` (annotations);
      `ScopeGuard`, `RoleGuard` (guards);
      `SpringSecurityGuards` (static helper consolidating the
      attach-if-annotated logic).
- [ ] `MocapiSpringSecurityGuardsAutoConfiguration` lives in
      `mocapi-autoconfigure`, guarded by
      `@ConditionalOnClass({ScopeGuard.class, Authentication.class})`.
- [ ] Four customizer beans (one per handler kind) register in
      the autoconfig.
- [ ] `mocapi-autoconfigure` declares
      `mocapi-spring-security-guards` as
      `<optional>true</optional>`.
- [ ] `ScopeGuard` denies: unauthenticated; missing one or more
      required scopes (reason lists missing scopes). Allows when
      all required scopes present.
- [ ] `RoleGuard` denies: unauthenticated; no role intersection.
      Allows when authentication has any of the configured
      roles. Accepts both bare (`ADMIN`) and prefixed
      (`ROLE_ADMIN`) forms in the annotation value.
- [ ] Empty annotation value (`@RequiresScope({})` or
      `@RequiresRole({})`) throws at construction time
      (customizer startup fails fast).
- [ ] Both annotations on the same method → two guards attach,
      both must allow (AND via Guard SPI).
- [ ] Unit tests per guard: authentication scenarios
      (null / anonymous / authenticated-insufficient /
      authenticated-sufficient).
- [ ] Autoconfig tests: module on classpath without Spring
      Security → no beans. Module + Spring Security → four
      customizer beans registered. Module absent → no beans
      regardless of Spring Security.
- [ ] Integration test: register a bean with a
      `@RequiresScope`-annotated tool, drive through the
      Streamable HTTP transport with a mock `Authentication`
      populated / not populated on `SecurityContextHolder`,
      confirm: list-time descriptor present/absent; call-time
      success / `-32003 Forbidden` with the expected reason.
- [ ] `README.md` "Modules" section gains
      `mocapi-spring-security-guards` with a one-line
      description.
- [ ] `docs/guards.md` (from spec 187) cross-references this
      module as "the first real Guard implementation" and
      shows the annotation shape.
- [ ] `docs/authorization.md` gains a "Per-handler authorization"
      section describing the annotation pattern alongside the
      existing RFC 9728 OAuth2 flow.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry for
      the new module.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Honoring `@PreAuthorize` / `@Secured`.** SpEL-backed
  `@PreAuthorize` needs Spring Security's
  `AuthorizationManager` machinery to evaluate expressions
  cleanly — doable but heavier than simple annotations. If real
  demand shows up, a follow-up spec adds a
  `PreAuthorizeGuard` that delegates to a prebuilt
  `AuthorizationManager`.
- **Class-level annotations.** Method-level only for v1.
  Class-level (with inheritance semantics) is a small follow-up
  if users ask.
- **Custom-expression guards.** A user who needs "allow if tenant
  matches session attribute X AND caller has role Y" writes
  their own Guard. This module covers the common pre-built cases;
  it's not a full authz DSL.
- **Tenant extraction helpers.** Out of scope; user's tenant
  model is their own.
- **Replacing mocapi-oauth2.** Different concern.
  `mocapi-oauth2` ships the RFC 9728 protected-resource metadata
  document so clients can discover the authorization server;
  this module consumes the SecurityContext that Spring's
  resource-server populates after validating tokens. Orthogonal
  — typical enterprise deployments want both.

## Implementation notes

- Customizer beans read method annotations at startup; guards
  close over the required scopes / roles. No per-call
  annotation lookup.
- Prefer `@FunctionalInterface` + a helper method over
  hand-writing eight customizer methods. The attach-annotation
  logic is identical across kinds; differences are the Config
  type and the `guard` accessor reference, both parameterizable
  via a `BiConsumer<C, Guard>`.
- `SpringSecurityGuards.attach` is a static helper; no bean
  needed.
- `SecurityContextHolder.getContext().getAuthentication()` is
  a `ThreadLocal` read. Spec 182's context propagation ensures
  it's populated on the handler VT. Without spec 182, guards
  would see null auth on every call — this module is
  effectively blocked on spec 182 having shipped first.
- Test with `SecurityContextHolder.setContext(...)` in a
  try/finally around each assertion; do *not* leak
  SecurityContext between tests.
- If a user writes both `@RequiresScope({"admin:write"})` +
  `@RequiresRole("ADMIN")` on a method and wants one to imply
  the other, that's their combination logic; both guards
  attach independently and both must allow.
