# Custom Parameter Resolvers

Handler methods normally take parameters that bind from the
JSON-RPC request arguments (tools / resource templates) or the
prompt arguments map. Sometimes you want a parameter to come from
**somewhere else entirely** — the current session, the Spring
Security `SecurityContextHolder`, a per-request ScopedValue, a
tenant-scoped bean lookup. That's what custom `ParameterResolver`s
are for.

Mocapi supports them via the customizer SPI (see
[customizers.md](customizers.md) for the broader pattern).

## Example — `@CurrentTenant String tenant`

You want `@CurrentTenant`-annotated parameters populated from a
session attribute, so tool code doesn't have to pluck it itself:

```java
@McpTool(name = "list_tenant_widgets")
public List<Widget> listTenantWidgets(@CurrentTenant String tenant) {
    return widgetService.listForTenant(tenant);
}
```

### The resolver

```java
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;
import java.util.Optional;

public final class CurrentTenantResolver implements ParameterResolver<JsonNode> {

    @Override
    public Optional<Binding<JsonNode>> bind(ParameterInfo info) {
        if (!info.parameter().isAnnotationPresent(CurrentTenant.class)
                || info.resolvedType() != String.class) {
            return Optional.empty();        // not our parameter; let the next resolver try
        }
        return Optional.of(arguments -> {
            // Runs per-invocation. `arguments` is the whole request
            // arguments JsonNode — we ignore it and pull from session.
            if (!McpSession.CURRENT.isBound()) {
                throw new IllegalStateException("@CurrentTenant requires a bound session");
            }
            return McpSession.CURRENT.get().attribute("tenant");
        });
    }
}
```

Two things to notice about the new (Methodical 0.7+) shape:

1. **`bind` runs once per parameter at startup.** Annotation
   lookup, type checks, any expensive setup (e.g., a cached
   `ObjectReader`) happens here — not per call.
2. **The returned `Binding` is the per-invocation hot path.** It
   receives the handler's argument object and returns the resolved
   value. Keep this method tight; it runs on every request.

### Attaching the resolver

```java
@Configuration
class ResolverWiring {

    @Bean
    CallToolHandlerCustomizer currentTenantResolverCustomizer() {
        CurrentTenantResolver resolver = new CurrentTenantResolver();
        return config -> config.resolver(resolver);
    }
}
```

If the same `@CurrentTenant` shows up on prompt methods or resource
templates, add the corresponding `GetPromptHandlerCustomizer` /
`ReadResourceTemplateHandlerCustomizer` beans. Each kind has its
own customizer type because the argument types differ (`JsonNode`
vs `Map<String, String>` vs `Object`).

## Resolution order

Mocapi and the transports provide structural resolvers already.
Customizer-added user resolvers slot in between mocapi's
**specific** and **catch-all** structural resolvers.

**Tools** (`JsonNode`):

```
McpToolContextResolver   (specific — matches McpToolContext parameters)
McpToolParamsResolver    (specific — matches @McpToolParams)
[ your custom resolvers, in customizer-bean @Order ]
Jackson3ParameterResolver   (catch-all — JSON body binding by name / index)
```

**Prompts / Resource Templates** (`Map<String, String>`):

```
[ your custom resolvers ]
StringMapArgResolver   (catch-all — name-based lookup + conversion)
```

**Resources** (`Object` / no args from request — resources don't
take user args):

```
McpTransportResolver    (specific)
McpSessionResolver      (specific)
[ your custom resolvers ]
(no catch-all — resource handlers typically take only
McpResourceContext-style scoped values)
```

Your custom resolver runs **before** the catch-all. Typical
design: guard it with an annotation or type check so it only
claims the parameter it's meant to. Uncclaimed parameters fall
through to the catch-all, which binds from the argument shape.

## Why "specific before catch-all" matters

The catch-all resolvers (`Jackson3ParameterResolver`,
`StringMapArgResolver`) claim **any** parameter they're handed —
they'll gladly try to JSON-deserialize `@CurrentTenant String` from
the arguments blob and produce a blank string (or throw, depending
on inputs). Putting user resolvers ahead of the catch-all gives
them a chance to claim the parameter by annotation before the
catch-all swallows it.

## Binding-time caching — the real performance win

Before Methodical 0.7 the `ParameterResolver` API was
`supports(info)` + `resolve(info, args)` — both called per
invocation. Now `bind(info)` runs once at handler construction
and returns a `Binding` that holds whatever state the resolver
needs.

For simple cases that's a convenience. For expensive cases it's a
real win — Jackson's `ObjectReader` creation, annotation lookups,
method-handle resolution, whatever per-parameter setup you need
— all happen once, then the hot path just consults captured
state.

```java
// Good — ObjectReader built once per parameter at bind
@Override
public Optional<Binding<JsonNode>> bind(ParameterInfo info) {
    if (!info.parameter().isAnnotationPresent(FromCookies.class)) {
        return Optional.empty();
    }
    ObjectReader reader = mapper.readerFor(info.resolvedType());
    String cookieName = info.parameter().getAnnotation(FromCookies.class).value();
    return Optional.of(args -> {
        String raw = currentRequest().getCookie(cookieName);
        return raw == null ? null : reader.readValue(raw);
    });
}
```

```java
// Avoid — rebuilding per call
@Override
public Optional<Binding<JsonNode>> bind(ParameterInfo info) {
    if (!info.parameter().isAnnotationPresent(FromCookies.class)) {
        return Optional.empty();
    }
    return Optional.of(args -> {
        ObjectReader reader = mapper.readerFor(info.resolvedType());  // per-call allocation
        String cookieName = info.parameter()
                .getAnnotation(FromCookies.class).value();             // per-call reflection
        String raw = currentRequest().getCookie(cookieName);
        return raw == null ? null : reader.readValue(raw);
    });
}
```

## Thread safety

Your `ParameterResolver` instance AND the `Binding` it returns must
be thread-safe.

`bind()` runs single-threaded at startup and can safely build
captured state. `Binding.resolve(argument)` runs concurrently on
every invocation — no mutable shared state without synchronization.

If you need per-invocation state, use a `ScopedValue` (bound by
an upstream filter/interceptor) or pull from
`SecurityContextHolder` / similar thread-local sources that have
their own propagation story.

## When NOT to write a ParameterResolver

- **Request-scoped data available on `McpSession.CURRENT`.** Just
  `McpSession.CURRENT.get().attribute(...)` inside the tool
  method. Cleaner than inventing a parameter resolver for
  single-use state.
- **Per-method policy / config.** That's closer to an
  interceptor than a resolver. Attach via
  `config.interceptor(...)` instead.
- **Authorization decisions.** Use a `Guard` (see
  [guards.md](guards.md)). Guards run before the invocation with
  proper error mapping to `-32003 Forbidden`; a parameter resolver
  that throws mid-invocation produces a less-clean error shape.

## Non-goals

- **Reordering structural resolvers.** You can't insert before
  `McpToolContextResolver` or after the Jackson catch-all; the
  structural slots are mocapi-owned. If you need catch-all
  behavior, write a resolver that claims parameters by a predicate
  so it runs ahead of the structural catch-all.
- **Removing structural resolvers.** Same reason.
- **A Spring `HandlerMethodArgumentResolver` bridge.** Mocapi's
  resolver SPI is from Methodical; Spring's is from Spring MVC.
  They're separate concerns at separate layers. If a Spring
  argument resolver helps, use it in your own Spring controllers,
  not here.
