# Remove manual JSON-RPC response building

## What to build

Replace all manual `ObjectNode` construction of JSON-RPC responses with RipCurl's
`JsonRpcResponse` record. The `buildJsonRpcResponse()` helper and any inline
`objectMapper.createObjectNode()` calls that construct JSON-RPC envelopes are
redundant now that RipCurl 0.7.0 provides typed records with automatic version
handling.

### Delete `buildJsonRpcResponse()` helper

Find and delete any helper methods that manually construct JSON-RPC response
`ObjectNode`s. Replace all call sites with `new JsonRpcResponse(result, id)`.

### Replace inline JSON-RPC envelope construction

Search for any `objectMapper.createObjectNode()` calls that build JSON-RPC
envelopes (setting `jsonrpc`, `id`, `result`, or `error` fields) and replace
with the appropriate RipCurl type:

- Success: `new JsonRpcResponse(result, id)`
- Notifications: `JsonRpcRequest.notification(method, params)`
- Requests: `JsonRpcRequest.request(method, params, id)`

### Use `JsonRpcProtocol.VERSION` for any remaining version references

Replace any hardcoded `"2.0"` strings related to JSON-RPC with
`JsonRpcProtocol.VERSION`.

## Acceptance criteria

- [ ] No `buildJsonRpcResponse()` helper methods exist
- [ ] No manual `ObjectNode` construction of JSON-RPC envelopes
- [ ] No hardcoded `"2.0"` strings for JSON-RPC version
- [ ] All JSON-RPC messages use RipCurl types (`JsonRpcResponse`, `JsonRpcRequest`)
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- RipCurl 0.7.0 is required for `JsonRpcResponse` pass-through and metadata support.
- Error responses may still need manual construction if RipCurl doesn't have a
  JSON-RPC error response type. If so, consider adding one to RipCurl or keeping
  a minimal error helper in Mocapi.
- Check `DefaultMcpStreamContext` for notification construction — those should use
  `JsonRpcRequest.notification()`.
