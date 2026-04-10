# Remove bean-based elicitation from McpStreamContext

## What to build

`McpStreamContext` currently exposes three elicitation entry points:

1. `<T> BeanElicitationResult<T> elicitForm(String message, Class<T> type)`
2. `<T> BeanElicitationResult<T> elicitForm(String message, TypeReference<T> type)`
3. `ElicitationResult elicit(String message, Consumer<ElicitationSchemaBuilder> schema)`

Variants (1) and (2) generate a JSON schema from a Java type via
`SchemaGenerator`, send the elicitation, and deserialize the client's
response back into that type. Variant (3) takes an inline schema built via
a fluent builder and returns an untyped `ElicitationResult` with
string/number/boolean getters.

Delete (1) and (2), along with everything that only exists to support
them. Keep (3) — the builder-based path — as the sole elicitation entry
point. The mocapi-compat conformance tools already use (3) exclusively
(verified), and (3) covers every MCP elicitation use case without
dragging Jakarta-validation reflection or Victools schema generation into
the elicitation path.

## Acceptance criteria

- [ ] Both `elicitForm` overloads are removed from
      `McpStreamContext`:
  - `<T> BeanElicitationResult<T> elicitForm(String message, Class<T> type)`
  - `<T> BeanElicitationResult<T> elicitForm(String message, TypeReference<T> type)`
- [ ] The `BeanElicitationResult` record
      (`mocapi-core/.../stream/elicitation/BeanElicitationResult.java`) is
      deleted.
- [ ] The `DefaultMcpStreamContext.elicitForm(...)` implementations,
      their `doElicit(..., Class<T>)` / `doElicit(..., TypeReference<T>)`
      helpers, and their `parseResponse(..., Class<T>)` /
      `parseResponse(..., TypeReference<T>)` helpers are all removed.
- [ ] The `generateSchema(Class<?>)` helper in
      `DefaultMcpStreamContext` is removed if it's only referenced by
      the deleted bean-elicit path. If it's used elsewhere, leave it.
- [ ] The `ElicitationSchemaValidator` call inside `generateSchema` is
      no longer invoked from the elicit flow. If `ElicitationSchemaValidator`
      has no other callers after this spec, delete it too.
- [ ] `SchemaGenerator` is no longer a constructor dependency of
      `DefaultMcpStreamContext` **if** it was only used by bean elicit.
      (It's shared with tool input-schema generation at the framework
      level, but that lives in `DefaultMethodSchemaGenerator`, not in
      the stream context — verify before removing from the stream
      context constructor.)
- [ ] The `elicit(String, Consumer<ElicitationSchemaBuilder>)` builder
      path is untouched and continues to work end-to-end.
- [ ] `McpElicitationNotSupportedException`'s javadoc, which says
      "Thrown when elicitForm() is called ...", is updated to reference
      `elicit()` instead — the exception itself stays.
- [ ] Delete all tests in `DefaultMcpStreamContextTest` that exercise
      `elicitForm` / `BeanElicitationResult`. Do **not** delete tests
      that cover the builder-based `elicit(...)` path. Approximately 12
      occurrences of `BeanElicitationResult`/`elicitForm` currently
      exist in that file — all go away.
- [ ] Delete tests in `ElicitationResultTest.java` that reference
      `BeanElicitationResult`. 4 occurrences exist currently.
- [ ] `mocapi-compat`'s `ConformanceTools` still compiles and its
      conformance suite still passes 39/39. Verified before the spec:
      `ConformanceTools` uses only `ctx.elicit(...)` (builder form).
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- Spec 098 (elicitation migration to model records) is expected to land
  before this spec runs. That's fine — 098 will migrate the bean-elicit
  path to use `ElicitRequestFormParams` / `ElicitResult`, and this spec
  then removes that migrated code entirely. The wasted work is
  acceptable because 098 also migrates the builder-based path, which we
  keep.
- The `ElicitationSchema` / `ElicitationSchemaBuilder` types support
  the builder-based path and must stay.
- The `ElicitationAction` enum in `mocapi-core` is used by the builder
  path's `ElicitationResult`, so it stays. (Model's `ElicitAction` is
  a different type for wire deserialization.)
- `McpElicitationException` and `McpElicitationTimeoutException` are
  shared by both paths and stay.
- Search the full reactor for any other references to
  `BeanElicitationResult` or `elicitForm` before deleting — grep at the
  time of writing this spec shows usage only in the two core files and
  the two test files named above, plus the javadoc comment in
  `McpElicitationNotSupportedException`.
- Keep the change purely subtractive — do not reorganize the
  elicit/sampling parsing helpers as part of this spec. Any
  consolidation of the remaining `parseRawResponse` /
  `parseSamplingResponse` code can be a follow-up.
- Rationale: the bean-based path duplicates what tool authors can
  already do with the builder — once an MCP client sends back a
  content map, the tool author can either use the typed getters on
  `ElicitationResult` or deserialize it themselves with the mapper they
  already have. The bean path was a convenience that pulled in an
  entire schema-generation dependency tree for every elicit call, and
  the conformance suite has never needed it.
