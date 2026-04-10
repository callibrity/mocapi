# Request metadata and progress types in mocapi-model

## What to build

Add the request metadata and progress notification types from schema.ts to
`mocapi-model`. These are shared across many request/response types.

### Request._meta

The `_meta` object appears on request params and includes `progressToken`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMeta(
    @JsonProperty("progressToken") Object progressToken  // string | number
) {}
```

`progressToken` is `string | number` in the spec. Use `Object` (or a sealed
type) in Java since Jackson will deserialize whichever the client sent.

### ProgressNotification

Server-to-client notification sent during long-running operations:

```java
public record ProgressNotification(
    String method,  // always "notifications/progress"
    ProgressNotificationParams params
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressNotificationParams(
    Object progressToken,  // string | number
    double progress,
    Double total,
    String message
) {}
```

### Result._meta

The `_meta` object also appears on results for arbitrary server metadata:

```java
// ObjectNode or Map<String, Object> — flexible JSON
```

For now, capture it as `ObjectNode` where needed. Don't create a wrapper type.

### TaskAugmentedRequestParams

Per schema.ts, many request param types extend `TaskAugmentedRequestParams`
which includes task-related metadata. Check the schema.ts for the exact
shape and add it if needed. This affects `ElicitRequestParams` and others.

### Tests

Unit tests verifying serialization of progress notifications and request meta.

## Acceptance criteria

- [ ] `RequestMeta` record with `progressToken` field
- [ ] `ProgressNotification` and `ProgressNotificationParams` records
- [ ] `TaskAugmentedRequestParams` if applicable per schema
- [ ] Jackson handles `string | number` progressToken via `Object` type
- [ ] Unit tests for serialization
- [ ] NO changes to mocapi-core — migration is a follow-up spec
- [ ] `mvn verify` passes
