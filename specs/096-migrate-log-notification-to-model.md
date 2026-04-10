# Migrate log notification to LoggingMessageNotificationParams

## What to build

`DefaultMcpStreamContext.log(LoggingLevel, String, Object)` currently builds
the `notifications/message` params by hand as an `ObjectNode` with `level`,
`logger`, and `data` fields. The model module already defines
`LoggingMessageNotificationParams` that mirrors this shape exactly. Migrate
the method to construct the typed record and let Jackson serialize it,
mirroring the pattern already used by `sendProgress` (which now builds a
`ProgressNotificationParams` record and calls `publishNotification` with
`objectMapper.valueToTree(params)`).

## Acceptance criteria

- [ ] `DefaultMcpStreamContext.log(level, logger, data)` constructs a
      `LoggingMessageNotificationParams` record and publishes it via
      `publishNotification("notifications/message", valueToTree(record))`.
- [ ] No `ObjectNode` / `createObjectNode` / field-by-field `put`/`set`
      construction of the log params remains in `DefaultMcpStreamContext`.
- [ ] All existing unit tests in
      `DefaultMcpStreamContextTest` pass unchanged — the wire format must
      be byte-identical to the current output (the asserts check
      `level`, `logger`, `data` on the JSON; those must still hold).
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- `LoggingMessageNotificationParams` already exists in `mocapi-model`.
  Verify its field shape matches what the current code emits before
  migrating.
- The `data` field in the record is typed broadly enough to accept any
  serializable value — Jackson will handle strings, maps, lists, and
  arbitrary beans the same way the current
  `objectMapper.valueToTree(data)` call does.
- The level-threshold check (drop below threshold, default to WARNING when
  no session) stays exactly as it is — only the params construction
  changes.
- Reference pattern: look at `sendProgress` in the same file for the exact
  idiom.
