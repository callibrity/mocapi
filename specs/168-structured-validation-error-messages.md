# Structured, LLM-friendly validation error messages

## What to build

When Jakarta Bean Validation rejects a tool-method argument, mocapi currently
lets the raw `ConstraintViolationException` propagate into
`McpToolsService.invokeTool`'s catch-all, which calls
`throwable.getMessage()` and wraps it as
`CallToolResult { isError: true, content: [TextContent(<message>)] }`.

The message produced by Hibernate Validator is Java-flavored and noisy:

```
shout.arg0: must not be blank, summarize.arg1: must be greater than or equal to 1
```

The calling LLM has to parse parameter slots like `arg0` out of a sentence.
Replace this with a structured, LLM-friendly text block that names each
offending parameter and quotes the constraint in plain language.

### Target output

Given a tool method:

```java
@ToolMethod(name = "summarize")
public Summary summarize(
    @NotBlank String text,
    @Min(1) @Max(100) int maxWords) { ... }
```

A call with `text=""`, `maxWords=500` produces:

```
CallToolResult {
  isError: true,
  content: [TextContent(
    "Invalid input:\n" +
    "- text: must not be blank\n" +
    "- maxWords: must be less than or equal to 100"
  )]
}
```

Each line: `- <parameter name>: <violation message>`. Parameter name comes
from the compiled `-parameters` bytecode (already enabled in mocapi's POM).
Violation message is the Jakarta-provided `getMessage()` — *without* the
`toolMethodName.argN:` prefix that Hibernate prepends.

### Where the mapping happens

Introduce a dedicated `ConstraintViolationException → CallToolResult`
handler inside `McpToolsService.invokeTool`'s catch block — check for the
exception type *before* the generic `catch (Exception)` branch.

The handler:

1. Iterates `ConstraintViolationException.getConstraintViolations()`.
2. For each violation, extracts the leaf path segment (the parameter name).
3. Emits one `- <param>: <message>` line.
4. Wraps the whole list as a single `TextContent` with `isError=true`.

### Nested / structured argument violations

For tools that accept a record parameter with annotated fields:

```java
record Address(@NotBlank String street, @Pattern(regexp = "\\d{5}") String zip) {}

@ToolMethod(name = "ship")
public Receipt ship(@Valid Address address) { ... }
```

The path from Hibernate is `ship.address.zip`. Trim the method name prefix
so the caller sees `- address.zip: must match "\d{5}"`.

### Prompts and resources stay the same

This spec *only* changes the tool path. Prompt and resource validation
errors already surface as JSON-RPC `-32602` with per-violation `data` —
that's the correct MCP shape for those handlers and is out of scope here.

## Acceptance criteria

- [ ] `McpToolsService` has a `ConstraintViolationException`-specific branch
      before the generic catch-all.
- [ ] Output text follows the exact format above: a header `"Invalid input:"`
      and one `- <path>: <message>` line per violation.
- [ ] Parameter name (not `arg0`) appears — method compiled with
      `-parameters`, verified in tests.
- [ ] Nested paths (record / bean fields) have the tool method name stripped
      from the prefix.
- [ ] Multiple violations produce multiple lines in declaration order.
- [ ] New unit tests in `mocapi-server` covering: single flat violation,
      multiple flat violations, nested record violation, violation on
      collection element (`@Valid` on `List<@NotBlank String>`).
- [ ] Existing `JakartaValidationIntegrationTest` updated to assert the new
      text format, not just `content().isNotEmpty()`.

## Implementation notes

- `ConstraintViolation.getPropertyPath()` returns a `Path`; iterate nodes,
  skip the first (method name) and first parameter node (since we only want
  the leaf-relative path from the parameter name downward).
- Getting the parameter name: `Path.Node#getName()` on the `PARAMETER` kind
  node yields `text` / `maxWords` / etc. when `-parameters` is on.
- The wrapping happens in mocapi's catch block — do not modify ripcurl's
  `ConstraintViolationExceptionTranslator`, which handles the JSON-RPC
  `-32602` path for prompts / resources and must keep doing so.
- Keep the existing generic-exception catch as the fallback for anything
  that isn't a `ConstraintViolationException`.
