# Merge all compat tests into one package

## What to build

Flatten the test structure in `mocapi-compat`. Remove the `conformance`
sub-package — all ITs live in `com.callibrity.mocapi.compat`. Merge unique
tests from the original ITs into the conformance ITs, delete duplicates.

### Flatten package structure

Move all classes from `com.callibrity.mocapi.compat.conformance` up to
`com.callibrity.mocapi.compat`. Delete the `conformance` sub-package.

The `src/main/java` conformance tools and application stay in whatever
package they're in (they serve the npx suite).

### Merge unique tests, delete duplicates

**Delete (duplicated by conformance ITs):**
- `SmokeIT` — covered by `FullConversationIT`
- `PingIT` (original) — covered by conformance `PingIT`
- `InitializationIT` — covered by `ServerInitializeIT`
- Basic tool invocation tests — covered by `ToolsCall*IT` classes

**Merge into existing conformance ITs (add as new test methods):**

Into `ServerInitializeIT`:
- `initializeDoesNotRequireSessionId`
- `notificationsInitializedReturns202`

Into `PostEndpointIT`:
- `missingAcceptHeaderReturns406`
- `acceptWithOnlyJsonReturns406`
- `acceptWithOnlySseReturns406`
- `acceptWithWildcardIsAccepted`
- `invalidJsonRpcVersionReturnsError`

Into `SessionManagementIT` (new — npx doesn't test this):
- `initializeReturnsSessionIdHeader`
- `postWithoutSessionIdOnNonInitializeReturns400`
- `postWithUnknownSessionIdReturns404`
- `deleteWithValidSessionReturns204`
- `deleteWithoutSessionIdReturns400`
- `deleteWithUnknownSessionIdReturns404`
- `postToDeletedSessionReturns404`

Into `DnsRebindingProtectionIT`:
- `postWithNoOriginIsAccepted`
- `getWithInvalidOriginReturns403`
- `deleteWithInvalidOriginReturns403`
- `getWithNoOriginIsAccepted`
- `deleteWithNoOriginIsAccepted`

Into `SseStreamIT` (new or merge with `ServerSseMultipleStreamsIT`):
- `getWithoutSseAcceptReturns406`
- `getWithNoAcceptHeaderReturns406`
- `getWithSseAcceptIsAccepted`
- `getWithoutSessionReturns400`
- `getWithUnknownSessionReturns404`
- `tamperedLastEventIdReturns400`

Into `ContentNegotiationIT` (new — npx doesn't test this):
- `initializeResponseIsJsonNotSse`
- `pingResponseIsJson`
- `streamingToolCallReturnsSseContentType`
- `nonStreamingToolReturnsJsonNotSse`

Into `ProtocolVersionNegotiationIT` (new — npx doesn't test this):
- `acceptsValidProtocolVersion`
- `rejectsUnrecognizedProtocolVersion`
- `missingProtocolVersionDefaultsToCurrent`

Keep as standalone (npx doesn't test):
- `ClientResponseIT` — client result/error handling
- `NotificationIT` — 202, no body, unknown method
- `FullConversationIT` — end-to-end scenario
- `ToolDiscoveryIT` — pagination tests

### Delete old support classes

If `CompatApplication` and `CompatTools` in the non-conformance package are
no longer needed (the conformance application covers everything), delete them.

## Acceptance criteria

- [ ] No `conformance` sub-package — all ITs in `com.callibrity.mocapi.compat`
- [ ] No duplicate test methods
- [ ] Unique tests from original ITs preserved in merged classes
- [ ] Dead IT classes deleted
- [ ] Dead support classes deleted
- [ ] All tests pass
- [ ] `mvn verify` passes
