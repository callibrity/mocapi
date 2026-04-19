# LLM-friendly structured text for constraint-violation tool errors

## What to build

When Jakarta Bean Validation rejects a `tools/call` argument, the resulting
`CallToolResult` text content today is Hibernate's default string
(e.g. `shout.arg0: must not be blank, summarize.arg1: must be greater than or equal to 1`).
Replace that with a structured, parameter-named block so the calling LLM
can map the error back to specific arguments.

### Target output

Input:

```java
@ToolMethod(name = "summarize")
public Summary summarize(@NotBlank String text, @Min(1) @Max(100) int maxWords) {...}
```

Call with `text=""`, `maxWords=500` →

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

### File: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/McpToolsService.java`

In `invokeTool(...)`, add a specific catch **before** the existing generic
`catch (Exception e)` branch:

```java
} catch (ConstraintViolationException cve) {
  log.debug("Tool {} rejected by bean validation: {} violation(s)", name, cve.getConstraintViolations().size());
  return ConstraintViolationFormatter.toCallToolResult(cve);
} catch (Exception e) {
  // existing handling
}
```

### File: `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ConstraintViolationFormatter.java`

Package-private utility:

```java
final class ConstraintViolationFormatter {
  private ConstraintViolationFormatter() {}

  static CallToolResult toCallToolResult(ConstraintViolationException ex) {
    var body = new StringBuilder("Invalid input:");
    for (var v : ex.getConstraintViolations()) {
      body.append("\n- ").append(parameterPath(v)).append(": ").append(v.getMessage());
    }
    return new CallToolResult(List.of(new TextContent(body.toString(), null)), true, null);
  }

  private static String parameterPath(ConstraintViolation<?> violation) {
    // Strip the leading method-name + first parameter-kind node; return the
    // dot-separated remainder (the parameter name plus any nested field path).
    // Example: "summarize.text" -> "text"; "ship.address.zip" -> "address.zip".
  }
}
```

### Tests

`mocapi-server/src/test/java/.../ConstraintViolationFormatterTest.java`:

- Single flat violation on a string arg → `- text: must not be blank`.
- Two violations on different args → two lines in declaration order.
- Nested record violation (`@Valid Address` with `@Pattern` on `zip`) →
  `- address.zip: must match "\d{5}"`.
- Collection-element violation (`@Valid List<@NotBlank String>` parameter
  `tags`) → `- tags[1]: must not be blank` (or equivalent bracket
  notation — follow whatever Hibernate's path iteration produces for
  indexed elements).

Existing `JakartaValidationIntegrationTest` — replace the
`content().isNotEmpty()` assertion in `blank_tool_argument_returns_
callToolResult_with_isError_true` with:

```java
assertThat(((TextContent) result.content().getFirst()).text())
    .isEqualTo("Invalid input:\n- arg: must not be blank");
```

(or whichever parameter name the fixture tool uses — keep it exact).

## Acceptance criteria

- [ ] `ConstraintViolationFormatter` utility exists with the behavior
      described.
- [ ] `McpToolsService#invokeTool` has the specific catch *before* the
      generic one.
- [ ] Only the tool path changed — `ConstraintViolationExceptionTranslator`
      in ripcurl still handles the prompts/resources `-32602` response.
- [ ] New unit tests cover flat, nested, and collection-element cases.
- [ ] Integration test updated to assert the exact text format.
- [ ] `mvn verify` green.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Changed`: entry describing
      the new validation error format for `tools/call`, with a before /
      after snippet.
- [ ] `docs/validation.md` (if it exists) updated with the new format.

## Commit

Suggested commit message:

```
Format tool validation errors as a structured, LLM-friendly list

Replaces Hibernate's default "toolName.arg0: must not be blank" with
a multi-line "Invalid input:" block where each line names the actual
parameter and carries the violation message. Lets the calling LLM
self-correct on the specific argument that failed instead of parsing
a Java-flavored sentence out of the text content.
```

## Implementation notes

- `ConstraintViolation#getPropertyPath()` returns a `Path`; iterate its
  nodes and skip the first (the method name) plus the first
  `PARAMETER`-kind node's synthetic position — what you actually want is
  the parameter name plus any subsequent `PROPERTY` / `CONTAINER_ELEMENT`
  nodes.
- Confirm `-parameters` compilation is on (it is, in `mocapi-parent` pom
  — double-check before coding).
- Prompts and resources keep the existing JSON-RPC `-32602` path
  (handled upstream by ripcurl's translator); do not touch that path.
