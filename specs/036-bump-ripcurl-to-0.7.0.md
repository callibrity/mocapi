# Bump RipCurl to 0.7.0

## What to build

Update the RipCurl dependency version from 0.2.0 to 0.7.0. This is required for:

- `JsonRpcMethod.call(JsonRpcRequest)` returning `JsonRpcResponse` (0.7.0)
- `JsonRpcResponse` metadata support — `withMetadata()`, `getMetadata()` (0.6.0)
- `JsonRpcRequest.request()` and `notification()` static factories (0.5.0)
- `JsonRpcProtocol.VERSION` constant (0.4.0)
- `JsonRpcResponse(result, id)` convenience constructor (0.4.0)

### Update version property

In the parent `pom.xml`, change:

```xml
<ripcurl.version>0.7.0</ripcurl.version>
```

### Fix compilation errors

The `JsonRpcMethod` interface changed in 0.7.0 — `call()` now takes
`JsonRpcRequest` and returns `JsonRpcResponse`. Any code implementing or
calling this interface will need updating.

## Acceptance criteria

- [ ] `ripcurl.version` is `0.7.0`
- [ ] All code compiles against RipCurl 0.7.0
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- This is a breaking upgrade. The `JsonRpcMethod` SPI changed signature.
  Any custom `JsonRpcMethod` implementations need updating.
