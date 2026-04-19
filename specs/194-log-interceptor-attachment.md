# Log when mocapi customizers attach interceptors to handlers

## What to build

Emit an INFO-level log line every time one of mocapi's built-in
customizers attaches an interceptor (or guard, or resolver) to a
handler. Same shape everywhere — handler kind, handler name, the
class attaching, the kind of thing being attached — so users
debugging "why don't my metrics show up?" / "is MDC wired?" /
"did my validation activate?" can see the wiring at boot time
instead of guessing.

### Why

Today the customizers are silent. A user who adds `mocapi-o11y`
to their pom has no visible confirmation that anything
happened until they successfully invoke a tool and query
`/actuator/metrics`. Debugging a misconfigured setup means
hitting `/actuator/conditions` and reading XML-shaped
autoconfig-match reports. That's a poor UX.

What logs at startup today:

- `McpToolsService` logs `Registered MCP tool: "<name>" (bean "<beanName>")` per tool.
- `McpPromptsService` — same shape for prompts.
- `McpResourcesService` / `McpResourceTemplatesService` — same for resources.

What doesn't log today:

- Nothing when `McpMdcInterceptor` gets attached.
- Nothing when `McpObservationInterceptor` gets attached.
- Nothing when Methodical's `JakartaValidationInterceptor` gets attached.
- Nothing when guards attach (future: same spot).
- Nothing when user `ParameterResolver`s attach.

### Shape of the log line

Consistent across every built-in customizer:

```
INFO  c.c.m.o.MocapiO11yAutoConfiguration - Attached McpObservationInterceptor to tool "get_weather"
INFO  c.c.m.l.McpMdcInterceptor         - Attached McpMdcInterceptor to prompt "summarize"
INFO  c.c.m.j.MocapiJakartaValidation... - Attached JakartaValidationInterceptor to resource_template "docs://pages/{slug}"
```

Format: `Attached <interceptor class simple name> to <kind> "<name>"`.

- **Interceptor class simple name**, not FQN — logs stay readable. Fully-qualified logger name already carries the module context.
- **`<kind>`** = `tool` / `prompt` / `resource` / `resource_template` — same strings used by o11y tags and HandlerKinds.
- **`<name>`** = descriptor name for tools / prompts, URI for resources, URI template for resource-templates — same value each customizer was already closing over.

Per invocation, not per customizer-attachment bulk summary. For an app with 12 tools + 3 prompts + 3 resources, each customizer emits 18 lines at boot. On a typical Spring Boot app that's dwarfed by existing autoconfig noise; if anyone complains about volume, DEBUG-level is an easy flip.

### Coverage

Apply to every mocapi-owned customizer that exists today (post
spec 186's consolidation):

| Autoconfig | Customizer beans | What logs |
|---|---|---|
| `MocapiLoggingAutoConfiguration` | 4 (one per handler kind) | `McpMdcInterceptor` attached |
| `MocapiO11yAutoConfiguration` | 4 | `McpObservationInterceptor` attached |
| `MocapiJakartaValidationAutoConfiguration` | 4 | `JakartaValidationInterceptor` attached |
| `MocapiSpringSecurityGuardsAutoConfiguration` (spec 190) | 4 | `ScopeGuard` and/or `RoleGuard` attached |

Each autoconfig class adds a logger, each customizer lambda
emits one line per handler it touches. No new helper class —
the log statement is four lines max, co-located with the
attachment logic, easy to grep for.

### Guards and resolvers

Same shape extends to:

- Guards (spec 187): `Attached <guard class> to <kind> "<name>"`
- Parameter resolvers (spec 191): `Attached <resolver class> resolver to <kind> "<name>"`

Distinguish attachment type in the message so log readers can
tell interceptors from guards from resolvers at a glance. Three
verbs: "Attached X interceptor", "Attached X guard", "Attached X
resolver". Or use one verb and include the kind: "Attached X
(interceptor) to ...". Either shape works — implementation
should pick one and be consistent.

### User-written customizers

Users' own customizers are their concern — mocapi doesn't wrap
or inspect them. If a user's customizer logs, great; if not, not
our problem. The value here is making mocapi's own wiring
visible, not enforcing a logging convention on third-party code.

### Log level

**INFO.** Reasons:

- Attaches happen once at startup. No hot-path cost.
- Startup telemetry is exactly the user-visible-value band INFO
  is for. DEBUG requires users to flip a level to diagnose
  wiring — that's the wrong default for "is my observability
  starter working."
- Matches the existing `Registered MCP tool: ...` INFO lines —
  same audience, same time-of-emission, same actionable info.

Users who want less noise can set
`logging.level.com.callibrity.mocapi=WARN` in their
application.properties. Standard Spring Boot mechanism, no new
properties.

### Non-goals

- **Logging per-invocation interceptor hits.** That's the o11y
  interceptor's job, not the customizer's. This spec is
  startup-only.
- **Logging *un*-attachment** (customizer decides not to attach
  to a particular handler, e.g., annotation not present).
  Silent skip is fine — logging every non-match would dominate
  the log at scale.
- **Logging user-written customizers' attachments.** See above.
- **Logging from the builder instead of the customizer.**
  The customizer is where the attachment decision is made and
  where the handler name is in scope; builder-level logging
  would require the customizer to pass context down, which
  is ceremony. Keep the log line inside the customizer
  lambda.
- **Structured / JSON log output.** Plain SLF4J. Users who
  want JSON configure their Logback / Log4j2 encoder; we emit
  events, they format.

## Acceptance criteria

- [ ] Every customizer bean in these autoconfigs emits exactly
      one INFO log line when it successfully attaches an
      interceptor / guard / resolver to a handler:
      `MocapiLoggingAutoConfiguration`,
      `MocapiO11yAutoConfiguration`,
      `MocapiJakartaValidationAutoConfiguration`,
      `MocapiSpringSecurityGuardsAutoConfiguration` (once spec
      190 lands).
- [ ] Log line format is consistent across all customizers:
      `Attached <interceptor/guard/resolver class simple name>
      [kind-word] to <kind> "<name>"`. Pick a distinguishing
      format for interceptor vs guard vs resolver and apply
      uniformly.
- [ ] Conditional customizers (e.g., spring-security-guards,
      which only attach when `@RequiresScope` / `@RequiresRole`
      is present on the method) log only when they actually
      attach. Silent skip when the condition fails.
- [ ] Logger category is the autoconfig class's FQN (standard
      SLF4J convention: `LoggerFactory.getLogger(getClass())`
      where applicable, or the class literal).
- [ ] Unit tests per autoconfig: invoke the customizer against
      a mock config, capture log output (Logback `ListAppender`
      or equivalent), assert the line was emitted with the
      expected content.
- [ ] `docs/logging.md` gains a short "Startup log lines"
      section listing which classes emit what at boot, so
      users can grep for them when debugging.
- [ ] No changes to user-written customizer requirements or
      documentation.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Implementation notes

- `Logger` field per autoconfig class, one line per customizer
  bean's lambda. Don't invent a central `AttachmentLogger`
  helper — the log call is one line, spreading context through
  a helper would be ceremony.
- Reading the log at runtime: `grep 'Attached'` on app stdout
  surfaces every mocapi wiring decision. That's the target UX.
- When this lands, the answer to "why don't my metrics show
  up?" becomes `grep 'Attached McpObservationInterceptor'`
  on startup logs. Zero lines = o11y autoconfig didn't
  activate; N lines = wired but the problem is elsewhere
  (exposure, tool invocation, etc.).
- No pom / dep changes; all four autoconfigs already have
  SLF4J on classpath.
