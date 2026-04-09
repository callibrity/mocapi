# Conformance tools for elicitation, sampling, and completion

## What to build

Add conformance test tools to the example app for the remaining failing
scenarios in `@modelcontextprotocol/conformance`.

### test_sampling

Tool name: `test_sampling`
Arguments: `prompt` (string, required)

Behavior: Send a `sampling/createMessage` request to the client. The request:
```json
{
  "messages": [{"role": "user", "content": {"type": "text", "text": "<prompt>"}}],
  "maxTokens": 100
}
```

Returns: `"LLM response: <response from sampling>"`

If the client doesn't support sampling, return an error.

Note: We need a `sample()` method on `McpStreamContext` that sends a
`sampling/createMessage` JSON-RPC call and waits for the response via Mailbox.

### test_elicitation

Tool name: `test_elicitation`
Arguments: `message` (string, required)

Behavior:
```java
ctx.elicit(message, schema -> schema
    .string("username", "User's response")
    .string("email", "User's email address")
    .required("username", "email"));
```

Returns: `"User response: <action: accept/decline/cancel, content: {...}>"`

### test_elicitation_sep1034_defaults

Tool name: `test_elicitation_sep1034_defaults`
No arguments.

Behavior: Use enums for the status field:
```java
enum Status { ACTIVE, INACTIVE, PENDING }

ctx.elicit("Enter defaults test data", schema -> schema
    .string("name", "Name", "John Doe")
    .integer("age", "Age", 30)
    .number("score", "Score", 95.5)
    .choose("status", Status.class, Status.ACTIVE)
    .bool("verified", "Verified", true));
```

The `Status` enum uses default `toString()` (matches `name()` lowercased
or as-is depending on conformance expectations — may need to override
`toString()` to produce lowercase values if the spec expects them).

Returns: `"Elicitation completed: action=<...>, content={...}"`

### test_elicitation_sep1330_enums

Tool name: `test_elicitation_sep1330_enums`
No arguments.

Behavior: All 5 enum variants using Java enums:

```java
enum UntitledOption { OPTION1, OPTION2, OPTION3 }

enum TitledOption {
    VALUE1("First Option"), VALUE2("Second Option"), VALUE3("Third Option");
    private final String title;
    // toString() returns title
}

ctx.elicit("Enum variants test", schema -> schema
    .choose("untitled_single", UntitledOption.class)
    .choose("titled_single", TitledOption.class)
    .chooseMany("untitled_multi", UntitledOption.class)
    .chooseMany("titled_multi", TitledOption.class));
```

The builder detects titled vs untitled by comparing `toString()` to `name()`.

For the legacy `enumNames` variant (variant 3 in the conformance spec), we may
need a special builder method or skip it if the conformance suite accepts the
`oneOf`/`const`/`title` format instead.

Returns: `"Elicitation completed: action=<...>, content={...}"`

### completion/complete support

Add a `McpCompletionMethods` `@JsonRpcService` that handles `completion/complete`.
For conformance, return empty completion arrays. Declare the capability in the
initialize response.

## Acceptance criteria

- [ ] `test_sampling` tool passes conformance
- [ ] `test_elicitation` tool passes conformance
- [ ] `test_elicitation_sep1034_defaults` tool passes conformance
- [ ] `test_elicitation_sep1330_enums` tool passes conformance
- [ ] `completion/complete` endpoint exists and passes conformance
- [ ] All existing tests pass
- [ ] `mvn verify` passes

## Implementation notes

- Sampling needs a new `sample()` method on `McpStreamContext` — same Mailbox
  pattern as elicitation but sends `sampling/createMessage`
- For `completion/complete`, declare the capability and return minimal response
- The conformance suite provides mock client responses for elicitation/sampling
- Enum `toString()` override is the mechanism for titled display names
