# Update README for current elicitation / sampling / controller API

## What to build

The mocapi project `README.md` (and any other top-level markdown
docs like `docs/*.md` if they exist) may contain code examples and
descriptions that reference API surfaces that changed significantly
during this refactoring session:

- **Elicitation DSL**: builder classes were renamed
  (`StringPropertyBuilder` → `StringSchemaBuilder`,
  `ChooseOneBuilder` → `SingleSelectEnumSchemaBuilder`,
  `ElicitationSchemaBuilder` → `RequestedSchemaBuilder`, etc.), the
  `ElicitationSchema` wrapper was deleted, enum builders gained an
  explicit `titled(Function)` opt-in, and the core
  `ElicitationResult` facade was replaced by `model.ElicitResult`
  with the typed getters moved onto the model record.
- **Sampling**: `SamplingResult` was replaced by
  `model.CreateMessageResult` (per spec 107, if that has landed).
- **Tool execution errors**: unhandled exceptions from tool methods
  now become `CallToolResult(isError=true)` responses rather than
  JSON-RPC errors (per spec 094).
- **Controller**: `@RequestBody` now takes `JsonRpcMessage` directly
  via Jackson's `@JsonCreator` (shipped with ripcurl 2.2.0). The
  hand-rolled `JsonRpcMessage.parse(body)` call in example code is
  gone.
- **Bean binding**: the removed `elicitForm(Class<T>)` API has been
  replaced (in spec 106) by a cleaner `elicit(String, Consumer, Class<T>)`
  overload that keeps the DSL-built schema but returns a typed bean.

Update `README.md` to reflect the current state of each of these
areas. Specifically:

### Sections to audit and rewrite

1. **"Defining Tools"** — the `@ToolMethod` examples should still
   work as-is, but double-check that return types, parameter
   conventions, and any `McpStreamContext<O>` → `McpStreamContext<R>`
   references use the current generic parameter name.
2. **"Streaming and Progress"** — if the README shows
   `ctx.sendResponse(...)` it should now show `ctx.sendResult(...)`.
   The progress and log helpers are unchanged.
3. **"Elicitation"** — the biggest rewrite. Any code examples
   showing `elicitForm(Class<T>)` must be removed. Replace with
   examples using the builder-based `elicit(String, Consumer<RequestedSchemaBuilder>)`
   method. Show both the plain form (returning
   `model.ElicitResult`) and the bean-binding form (returning
   `Optional<T>`) if spec 106 has landed.
4. **"Sampling"** — if there are code examples, update them to use
   `model.CreateMessageResult` (per spec 107) instead of
   `SamplingResult`.
5. **"HTTP Transport"** — if there's any example showing the
   controller signature or body handling, update it to reflect the
   `@RequestBody JsonRpcMessage` pattern.
6. **"Tool Errors"** — add or update a section describing that
   unhandled tool exceptions become `CallToolResult(isError=true)`
   results visible to the LLM, not JSON-RPC errors. Reference the
   MCP spec's guidance.

### Version references

- Any Maven coordinate examples should pin concrete released
  versions (e.g., `mocapi-spring-boot-starter` at its current
  SNAPSHOT version, ripcurl at `2.2.0`). Do not use `${...}`
  property placeholders in README examples — they're confusing for
  a copy-paste reader who doesn't have the property defined.

## Acceptance criteria

### No stale references

- [ ] `grep -n "elicitForm" README.md` returns no matches.
- [ ] `grep -n "ChooseOneBuilder\|ChooseManyBuilder\|ChooseLegacyBuilder\|ElicitationSchemaBuilder\|StringPropertyBuilder\|IntegerPropertyBuilder\|NumberPropertyBuilder\|BooleanPropertyBuilder" README.md` returns no matches.
- [ ] `grep -n "ElicitationResult\|ElicitationAction" README.md`
      returns no matches (these are the deleted core types; the
      model versions are `ElicitResult` / `ElicitAction`).
- [ ] `grep -n "BeanElicitationResult" README.md` returns no matches.
- [ ] `grep -n "JsonRpcMessage.parse\|sendResponse(" README.md`
      returns no matches.
- [ ] `grep -n "SamplingResult" README.md` returns no matches if
      spec 107 has landed.

### Content checks

- [ ] The README's elicitation example uses
      `ctx.elicit(String, Consumer<RequestedSchemaBuilder>)` and
      references `model.ElicitResult` as the return type.
- [ ] If spec 106 has landed, the README also shows the
      `ctx.elicit(String, Consumer, Class<T>)` overload and
      demonstrates Optional<T> handling for the decline case.
- [ ] The README's sampling example uses
      `model.CreateMessageResult` (assuming spec 107 has landed).
- [ ] The README's tool-error example explains the
      `CallToolResult(isError=true)` pattern and contrasts it with
      JSON-RPC errors for protocol-level failures.
- [ ] Maven coordinate blocks show concrete versions, not `${...}`
      placeholders.
- [ ] Any inline Java code blocks compile in isolation (or at least
      look like they would — the README isn't verified by a build
      step, but the examples shouldn't contain obvious syntax
      errors or unresolvable symbols).

### Build

- [ ] `mvn verify` passes (should be unaffected since this is
      docs-only, but run it to confirm no spotless rule applies to
      README.md).

## Implementation notes

- **Docs-only change.** No code, no tests, no behavior.
- **Start by reading the current README** and identifying each
  section that shows code. Rewrite sections in place; don't reshape
  the README's top-level structure unless it's clearly broken.
- **Where to find the current API examples** if unsure:
  - `mocapi-example/` has working tool definitions
  - `mocapi-compat/.../conformance/ConformanceTools.java` has
    comprehensive elicit + sample usages
  - `DefaultMcpStreamContextTest` has unit tests for every fluent
    path
- **Do NOT write more than the README needs.** Resist the urge to
  document every renamed class; focus on what tool authors actually
  read to get started.
- **If the README already has a "Changelog" or "Breaking Changes"
  section**, add a 0.0.2 / 0.1.0 entry (or whatever version is
  next) summarizing the refactor: builder renames, facade
  consolidation, `@RequestBody JsonRpcMessage`, bean-oriented
  elicit overload, etc. Keep it short — link to the commit history
  for details.
- **Commit suggested message**:
  ```
  docs: update README for current elicitation, sampling, and controller API
  ```
