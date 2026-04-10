# Uppercase enum names with @JsonValue for lowercase JSON

## What to build

All enums in `mocapi-model` that serialize as lowercase in the MCP spec
should use uppercase Java names with `@JsonValue` for lowercase JSON output.
This keeps Sonar happy and matches spec serialization.

Apply to all enums in `mocapi-model`:

```java
@JsonValue
public String toJson() { return name().toLowerCase(); }

@JsonCreator
public static Role fromJson(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }
```

Add both `@JsonValue` and `@JsonCreator` to be safe across Jackson versions.
The `@JsonCreator` static factory handles deserialization explicitly.

Enums to update:
- `Role` — `USER`, `ASSISTANT`
- `LoggingLevel` — `DEBUG`, `INFO`, `NOTICE`, `WARNING`, `ERROR`, `CRITICAL`, `ALERT`, `EMERGENCY`

Check for any other enums in the model module and apply the same pattern.

Also update any references in `mocapi-core` that use the old lowercase
enum constant names (e.g., `Role.user` → `Role.USER`).

## Acceptance criteria

- [ ] All model enums use uppercase constant names
- [ ] All model enums have `@JsonValue` returning lowercase
- [ ] Jackson round-trips correctly (serialize and deserialize)
- [ ] All references in mocapi-core updated
- [ ] Sonar has no complaints about enum naming
- [ ] All tests pass
- [ ] `mvn verify` passes
