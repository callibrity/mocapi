# Validate bean elicitation schemas against MCP spec

## What to build

After `SchemaGenerator` produces a JSON Schema from a Java class, validate it
against the MCP elicitation spec before sending the request. Reject schemas
that would violate the spec with a clear error message.

### Validation rules

The generated schema must satisfy all of these:

1. Root type must be `"object"`
2. Each property must be one of the allowed `PrimitiveSchemaDefinition` types:
   - `"type": "string"` (with optional minLength, maxLength, pattern, format, enum, oneOf, default)
   - `"type": "integer"` (with optional minimum, maximum, default)
   - `"type": "number"` (with optional minimum, maximum, default)
   - `"type": "boolean"` (with optional default)
   - `"type": "array"` only if it matches the multi-select enum pattern
     (items with enum or anyOf containing const/title pairs)
3. No nested objects — property type `"object"` is rejected
4. No `$ref`, `$defs`, `allOf`, `not`, `if`/`then`/`else`
5. No array properties unless they match the multi-select pattern
6. `format` on strings must be one of: `"email"`, `"uri"`, `"date"`, `"date-time"`

### Where to validate

In `DefaultMcpStreamContext`, after `generateSchema(type)` produces the
`ObjectNode`, run the validation before calling `sendElicitationAndWait`.
Throw `McpElicitationException` with a message identifying the offending
property and why it's invalid.

### Error messages

Be specific:
- `"Property 'address' has type 'object' which is not allowed in MCP elicitation schemas. Only string, integer, number, boolean, and enum/multi-select are supported."`
- `"Property 'items' has type 'array' which does not match the MCP multi-select enum pattern."`
- `"Property 'email' has unsupported format 'hostname'. Allowed formats: email, uri, date, date-time."`

## Acceptance criteria

- [ ] Generated bean schemas are validated against MCP elicitation rules
- [ ] Nested object properties are rejected
- [ ] Non-enum array properties are rejected
- [ ] Unsupported formats are rejected
- [ ] `$ref`, `$defs`, `allOf`, etc. are rejected
- [ ] Error messages identify the offending property and reason
- [ ] Valid flat records (string, int, boolean, enum fields) still work
- [ ] All tests pass
- [ ] `mvn verify` passes
