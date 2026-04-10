# Tighten conformance IT assertions

## What to build

The conformance ITs currently use loose assertions like `isNotEmpty()` where
the npx suite verifies exact values. Tighten them to match what the npx
conformance suite actually checks.

### Exact value assertions

For each IT, verify the exact response content that the conformance suite
expects, not just "something is there."

**ToolsCallSimpleTextIT:**
- Assert `content[0].text` equals `"This is a simple text response for testing."`

**ToolsCallImageIT:**
- Assert `content[0].type` equals `"image"`
- Assert `content[0].mimeType` equals `"image/png"`
- Assert `content[0].data` is valid Base64

**ToolsCallAudioIT:**
- Assert `content[0].type` equals `"audio"`
- Assert `content[0].mimeType` equals `"audio/wav"`
- Assert `content[0].data` is valid Base64

**ToolsCallEmbeddedResourceIT:**
- Assert `content[0].resource.uri` equals `"test://embedded-resource"`
- Assert `content[0].resource.mimeType` equals `"text/plain"`
- Assert `content[0].resource.text` equals `"This is an embedded resource content."`

**ToolsCallMixedContentIT:**
- Assert 3 content items
- Assert first is text with exact value
- Assert second is image with `image/png`
- Assert third is resource with `test://mixed-content-resource`

**ToolsCallErrorIT:**
- Assert `isError` is `true`
- Assert `content[0].text` contains the error message

**ToolsCallWithProgressIT:**
- Verify progress notifications are sent (if testable via MockMvc async)

**ToolsCallWithLoggingIT:**
- Verify log notifications are sent (if testable via MockMvc async)

**ServerInitializeIT:**
- Assert `protocolVersion` equals `"2025-11-25"`
- Assert `serverInfo.name` is present
- Assert `capabilities.tools` is present
- Assert `capabilities.resources` is present
- Assert `capabilities.prompts` is present

**PingIT:**
- Assert response `result` is an empty object `{}`

**ResourcesReadTextIT:**
- Assert `contents[0].uri` equals `"test://static-text"`
- Assert `contents[0].text` equals exact expected text

**ResourcesReadBinaryIT:**
- Assert `contents[0].uri` equals `"test://static-binary"`
- Assert `contents[0].mimeType` equals `"image/png"`
- Assert `contents[0].blob` is valid Base64

**PromptsGetSimpleIT:**
- Assert `messages[0].role` equals `"user"`
- Assert `messages[0].content.text` equals `"This is a simple prompt for testing."`

**PromptsGetWithArgsIT:**
- Assert response includes both arg values in the text

### General tightening

- Replace `isNotEmpty()` with exact value checks where the expected value is known
- Replace `exists()` with specific value assertions
- Add `hasSize()` assertions on arrays where count matters

## Acceptance criteria

- [ ] All conformance ITs assert exact values matching npx expectations
- [ ] No `isNotEmpty()` where an exact value is known
- [ ] Array sizes verified where appropriate
- [ ] All tests pass
- [ ] `mvn verify` passes
