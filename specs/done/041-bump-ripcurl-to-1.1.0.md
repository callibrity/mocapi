# Bump RipCurl to 1.1.0

## What to build

Update RipCurl from 1.0.0 to 1.1.0. This gives us:

- Error codes on `JsonRpcProtocol` instead of `JsonRpcException`
- `ParameterResolutionException` maps to `INVALID_PARAMS` in the dispatcher
- Batch request support (not needed for MCP, but available)

### Update version

In parent `pom.xml`:

```xml
<ripcurl.version>1.1.0</ripcurl.version>
```

### Replace `JsonRpcException.CONSTANT` with `JsonRpcProtocol.CONSTANT`

All references to error code constants move:

- `JsonRpcException.INVALID_PARAMS` → `JsonRpcProtocol.INVALID_PARAMS`
- `JsonRpcException.INTERNAL_ERROR` → `JsonRpcProtocol.INTERNAL_ERROR`
- `JsonRpcException.METHOD_NOT_FOUND` → `JsonRpcProtocol.METHOD_NOT_FOUND`
- `JsonRpcException.INVALID_REQUEST` → `JsonRpcProtocol.INVALID_REQUEST`

## Acceptance criteria

- [ ] `ripcurl.version` is `1.1.0`
- [ ] All error code references use `JsonRpcProtocol.CONSTANT`
- [ ] All tests pass
- [ ] `mvn verify` passes
