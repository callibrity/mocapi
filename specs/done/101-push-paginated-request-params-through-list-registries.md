# Push PaginatedRequestParams through registry list methods

## What to build

A new overload exists in `Cursors`:

```java
public static <T> Page<T> paginate(List<T> all, PaginatedRequestParams params, int pageSize) {
    return paginate(all, params == null ? null : params.cursor(), pageSize);
}
```

Use it to eliminate the manual cursor-extraction glue from every list
handler and registry in `mocapi-core`. Each MCP list method currently
does one of these shapes:

```java
// Handler (today)
public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
  String cursor = params != null ? params.cursor() : null;
  return toolsRegistry.listTools(cursor);
}

// Registry (today)
public ListToolsResult listTools(String cursor) {
  var page = Cursors.paginate(sortedDescriptors, cursor, pageSize);
  return new ListToolsResult(page.items(), page.nextCursor());
}
```

Migrate both ends so the typed record flows straight through:

```java
// Handler (target)
public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
  return toolsRegistry.listTools(params);
}

// Registry (target)
public ListToolsResult listTools(PaginatedRequestParams params) {
  var page = Cursors.paginate(sortedDescriptors, params, pageSize);
  return new ListToolsResult(page.items(), page.nextCursor());
}
```

No behavioral change on the wire — a null `params` or a record with a
null `cursor` both still paginate from the beginning, exactly as before.

## Acceptance criteria

### Registry method signatures

- [ ] `ToolsRegistry.listTools` takes a `PaginatedRequestParams` parameter
      (was `String cursor`).
- [ ] `PromptsRegistry.listPrompts` takes a `PaginatedRequestParams`
      parameter (was `String cursor`).
- [ ] `ResourcesRegistry.listResources` takes a `PaginatedRequestParams`
      parameter (was `String cursor`).
- [ ] `ResourcesRegistry.listResourceTemplates` takes a
      `PaginatedRequestParams` parameter (was `String cursor`).
- [ ] Each of the four registry methods calls
      `Cursors.paginate(list, params, pageSize)` via the new overload.
      No manual `params != null ? params.cursor() : null` guard should
      remain inside these methods — `Cursors` owns that check now.

### Handler methods

- [ ] `McpToolMethods.listTools` passes `params` straight to
      `toolsRegistry.listTools(params)` — no local `String cursor`
      variable.
- [ ] `McpPromptMethods.listPrompts` passes `params` straight to
      `promptsRegistry.listPrompts(params)` — no local `String cursor`
      variable.
- [ ] `McpResourceMethods.listResources` passes `params` straight to
      `resourcesRegistry.listResources(params)` — no local `String cursor`
      variable.
- [ ] `McpResourceMethods.listResourceTemplates` passes `params` straight
      to `resourcesRegistry.listResourceTemplates(params)` — no local
      `String cursor` variable.
- [ ] The string `params.cursor()` no longer appears anywhere in
      `mocapi-core/src/main`.

### Tests

- [ ] New unit tests for the `Cursors.paginate(List, PaginatedRequestParams, int)`
      overload in `CursorsTest` (or whatever the existing test class is
      named) covering:
  - null `params` → paginate from the beginning (equivalent to
    `paginate(list, (String) null, pageSize)`)
  - non-null `params` with null `cursor()` → same as null params
  - non-null `params` with a valid cursor → paginate from that cursor
  - non-null `params` with an invalid/out-of-range cursor → same
    error/fallback behavior as the `String` overload
  - Results must match the `String` overload byte-for-byte for the
    same logical inputs (assert equality of `Page.items()` and
    `Page.nextCursor()` between the two overloads).
- [ ] Unit tests for the four registries are updated to construct a
      `PaginatedRequestParams` (or pass `null`) instead of a raw cursor
      string. A registry test calling
      `registry.listTools(null)` must still work and return the first
      page — add an explicit assertion for this null-params case in
      each registry's test class if one doesn't already exist.
- [ ] The existing "paginate from cursor X" tests in each registry must
      still pass — they should now be written as
      `registry.listTools(new PaginatedRequestParams("cursor-X", null))`
      or equivalent.
- [ ] All existing handler-level tests continue to pass unchanged.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- `PaginatedRequestParams` has two fields: `cursor` (String) and `_meta`
  (RequestMeta). For this migration only `cursor` is consumed; the
  `_meta` field is a no-op on the read side.
- When updating registry tests, prefer a small helper like
  `private static PaginatedRequestParams cursor(String c) { return new
  PaginatedRequestParams(c, null); }` if the test file has many
  pagination calls, to keep the test code readable. Don't hoist this
  helper into production code.
- Do NOT touch the non-list registry methods (`callTool`, `lookup`,
  `readResource`, etc.) — this spec is scoped strictly to the four
  paginated list entry points.
- Do NOT modify `Cursors.paginate(List, String, int)` — the legacy
  overload stays so internal callers with just a cursor string (if any
  surface later) can still use it.
- The handlers and registries are in these files:
  - `mocapi-core/src/main/java/com/callibrity/mocapi/tools/McpToolMethods.java`
  - `mocapi-core/src/main/java/com/callibrity/mocapi/tools/ToolsRegistry.java`
  - `mocapi-core/src/main/java/com/callibrity/mocapi/prompts/McpPromptMethods.java`
  - `mocapi-core/src/main/java/com/callibrity/mocapi/prompts/PromptsRegistry.java`
  - `mocapi-core/src/main/java/com/callibrity/mocapi/resources/McpResourceMethods.java`
  - `mocapi-core/src/main/java/com/callibrity/mocapi/resources/ResourcesRegistry.java`
- Commit granularity: one registry + its handler per commit is a
  reasonable size for bisecting. Four small commits total.
