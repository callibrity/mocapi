# Fix PromptMessage.content to be a single ContentBlock (spec fidelity)

## What to build

`model.PromptMessage` currently declares its `content` field as
`List<ContentBlock>`, but the MCP `schema/2025-11-25/schema.ts`
defines it as a **single** `ContentBlock`:

```typescript
export interface PromptMessage {
  role: Role;
  content: ContentBlock;
}
```

This divergence causes the `@modelcontextprotocol/conformance` suite's
`prompts-get-*` scenarios to fail (four tests: `prompts-get-simple`,
`prompts-get-with-args`, `prompts-get-embedded-resource`,
`prompts-get-with-image`), because mocapi emits `messages[].content`
as a JSON array when the spec requires a JSON object.

Fix it by correcting the model record to match schema.ts, and then
ripple the change through every call site that constructs or
consumes a `PromptMessage`.

### Model change

```java
// Today
public record PromptMessage(Role role, List<ContentBlock> content) {}

// After
public record PromptMessage(Role role, ContentBlock content) {}
```

Single block per message. This is a **breaking change** to the
model type — anything that constructs a `PromptMessage` with a list
of content blocks must be updated to construct one block per message
(and likely to produce more `PromptMessage` instances for
multi-content prompts).

### Call sites to update

Based on a grep of the current codebase:

**Model tests:**
- `mocapi-model/src/test/java/.../ProtocolTypesSerializationTest.java`
  - Line 31: `new PromptMessage(Role.USER, List.of(new TextContent(...)))` →
    `new PromptMessage(Role.USER, new TextContent(...))`
  - Line 37-38: assertions on `content()` as a list → direct
    `isInstanceOf` / field checks on the single block
  - Line 43: same pattern for the ASSISTANT message
  - Line 59: same assertion pattern

**Core tests:**
- `mocapi-core/src/test/java/.../prompts/McpPromptMethodsTest.java`
  - Line 54: `new PromptMessage(...)` with list → single block
  - Lines 86-88, 107, 109: `content().getFirst()` chains → direct
    `content()` access
- `mocapi-core/src/test/java/.../prompts/PromptsRegistryTest.java`
  - Line 58: `new PromptMessage(...)` with list → single block

**Compat (conformance) tests and tool definitions:**
- `mocapi-compat/src/main/java/.../conformance/ConformancePrompts.java`
  - Lines 126, 155, 185, 194, 216, 218: six `new PromptMessage(...)`
    constructions with list content. Each needs to either (a) pass
    the single block directly or (b) be split into multiple
    `PromptMessage` entries if the original intent was to carry
    multiple content blocks per message.

  Per the MCP spec, a single `PromptMessage` carries a single
  content block. If a conformance scenario needs multiple content
  blocks in sequence, they should be multiple `PromptMessage`
  entries in the `messages` list (each with its own role). Audit
  each call site in `ConformancePrompts` and decide per case:
  - Simple prompts with one text block → straightforward single-block
    construction
  - Prompts that currently bundle multiple content blocks (e.g.,
    text + embedded resource + image) → split into multiple
    `PromptMessage` entries, each carrying one block, with the role
    repeated as needed

### Spec fidelity check

After the change, the serialized output of a `PromptMessage` should
match the spec example exactly:

```json
{
  "role": "user",
  "content": {
    "type": "text",
    "text": "Hello"
  }
}
```

Not:

```json
{
  "role": "user",
  "content": [
    {
      "type": "text",
      "text": "Hello"
    }
  ]
}
```

## Acceptance criteria

### Model

- [ ] `com.callibrity.mocapi.model.PromptMessage` has `content` typed
      as `ContentBlock` (single), not `List<ContentBlock>`.
- [ ] The `import java.util.List;` line is removed from
      `PromptMessage.java` if it's no longer needed.
- [ ] `mocapi-model`'s `ProtocolTypesSerializationTest` round-trips
      a `PromptMessage` with a single `TextContent` block and asserts
      the JSON shape matches the spec: `{"role":"user","content":{"type":"text","text":"..."}}`
      — **not** an array.
- [ ] The existing `@JsonInclude(NON_NULL)` annotation (if present on
      `PromptMessage`) is preserved.

### Core tests

- [ ] `McpPromptMethodsTest` construction calls pass a single
      `ContentBlock` per `PromptMessage`. Assertion chains
      (`content().getFirst()`) are replaced with direct
      `content()` / `instanceof` checks.
- [ ] `PromptsRegistryTest` constructions updated similarly.
- [ ] All existing prompt-related assertions are updated to work
      with the single-block shape.

### Compat — `ConformancePrompts`

- [ ] All six `new PromptMessage(...)` constructions in
      `ConformancePrompts.java` produce single-block messages.
- [ ] Any scenario that previously bundled multiple content blocks
      in one `PromptMessage` is restructured into multiple
      `PromptMessage` entries in the parent `messages` list.
- [ ] The spec-expected output of each scenario (as documented in
      the test file comments, if any) matches the new shape.

### Conformance suite

- [ ] Running the `@modelcontextprotocol/conformance` test harness
      against a running mocapi server produces:
  - `prompts-get-simple`: passing
  - `prompts-get-with-args`: passing
  - `prompts-get-embedded-resource`: passing
  - `prompts-get-with-image`: passing
  - Every other scenario that was passing before: still passing
  (the only changes should be the four that were failing becoming
  four that pass)

### Build

- [ ] `mvn verify` passes across the full reactor.
- [ ] `mocapi-compat`'s internal JUnit-based `PromptsGet*IT` tests
      pass. Note: these tests assert on the JSON shape, and they
      currently expect `content` to be an array (e.g.,
      `jsonPath("$.result.messages[0].content[0].type").value("text")`).
      These assertions need to be updated to the single-object
      shape (`jsonPath("$.result.messages[0].content.type").value("text")`).

## Implementation notes

- **Root cause**: the model record was authored with `List<ContentBlock>`,
  likely by analogy with `CallToolResult.content()` (which IS a list
  per the spec). The two types' content shapes are different:
  `CallToolResult.content: ContentBlock[]` (array) vs
  `PromptMessage.content: ContentBlock` (single). This fix aligns
  `PromptMessage` with its spec definition.
- **Spec reference**:
  `https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/main/schema/2025-11-25/schema.ts`
  — search for `interface PromptMessage`.
- **Breaking change implications**: anyone who has built a
  `mocapi-core` prompt provider using `PromptMessage(role, List.of(block))`
  must update their call. The compiler will catch every site — it's
  a hard breaking change, not a silent behavior shift.
- **Commit granularity**: a single atomic commit that touches the
  model, all tests, and `ConformancePrompts`. The blast radius is
  small (one model record + ~6 call sites in tests and conformance),
  so splitting doesn't help.
- **Verify the fix in isolation before running the full reactor**:
  1. Update `model.PromptMessage` and its round-trip test in the
     model module.
  2. Run `mvn -pl mocapi-model test` — should be green.
  3. Update the core and compat tests.
  4. Run `mvn verify` across the full reactor.
  5. Start the conformance server and run the `npx` conformance
     suite. Verify the four failing scenarios now pass.
- **Also check**: grep the rest of the codebase for
  `PromptMessage(.*List.of\(` to catch any call site I may have
  missed. The list in this spec was compiled from a reactor-wide
  grep at the time of writing, but anything Ralph added later won't
  be in it.
- **Do NOT** touch `CallToolResult.content()` — that one IS
  `List<ContentBlock>` per the spec and is correct as-is. Only
  `PromptMessage.content` is wrong.
- **`PromptsGet*IT` JUnit tests in mocapi-compat** need their
  jsonPath assertions updated as part of this spec (they currently
  expect array shape). The tests were passing before because they
  reflected the buggy behavior; after this fix they'll pass against
  the correct single-object shape.
