# Fix Sonar critical code smells (S1186, S115, S1192)

## What to build

13 CRITICAL-severity Sonar code smells across three rules. Each
rule is a simple, mechanical fix.

### Rule 1: `java:S1186` — empty method bodies (5 issues)

The five primitive elicitation schema builders all have an explicit
empty public no-arg constructor:

| File | Line |
|---|---|
| `BooleanSchemaBuilder.java` | 28 |
| `IntegerSchemaBuilder.java` | 30 |
| `NumberSchemaBuilder.java` | 30 |
| `RequestedSchemaBuilder.java` | 36 |
| `StringSchemaBuilder.java` | 32 |

Each looks like:

```java
public BooleanSchemaBuilder() {}
```

These explicit constructors don't do anything — all fields have
their defaults initialized inline. Java provides a public no-arg
default constructor when no constructors are declared, so the
simplest fix is to **delete the explicit empty constructor**. The
class still has a public no-arg constructor (supplied by the
compiler), callers see no difference.

### Rule 2: `java:S115` — constant naming convention (6 issues)

All six are in
`mocapi-compat/src/main/java/com/callibrity/mocapi/compat/conformance/ConformanceTools.java`:

- Lines 401, 402, 403: three constants
- Lines 418, 419, 420: three more constants

The Sonar regex is `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`. The constants
are currently `lowerCamelCase` or `value1/value2/value3`. Rename
them to `UPPER_SNAKE_CASE` (e.g., `VALUE_1`, `VALUE_2`, `VALUE_3`,
or whatever names match their semantic meaning).

**Context**: these are likely enum-style constants defined inside
test classes for titled enum conformance scenarios. Preserve their
serialized values (what they map to on the wire) — the rename is
Java-side only. If they're used as keys in a map or as DSL inputs,
verify the string value they produce hasn't changed.

### Rule 3: `java:S1192` — duplicated string literals (2 issues)

Two separate cases:

**Case 1**:
`mocapi-compat/src/main/java/.../conformance/ConformanceTools.java:279`
— the literal `"test_tool_with_logging"` appears 3 times.

Fix: extract a `private static final String`:

```java
private static final String TEST_TOOL_WITH_LOGGING = "test_tool_with_logging";
```

And replace each occurrence with the constant.

**Case 2**:
`mocapi-core/src/main/java/com/callibrity/mocapi/http/StreamableHttpController.java:160`
— the literal `"error"` appears 7 times.

Fix: extract a private constant:

```java
private static final String ERROR_KEY = "error";
```

And replace each occurrence. "Error" is a common literal so the
constant name should be specific enough to avoid collision —
`ERROR_KEY` or `ERROR_FIELD` is better than just `ERROR`.

## Acceptance criteria

### S1186 — empty constructors

- [ ] `BooleanSchemaBuilder` no longer has an explicit empty
      `public BooleanSchemaBuilder() {}` constructor.
- [ ] Same for `IntegerSchemaBuilder`, `NumberSchemaBuilder`,
      `RequestedSchemaBuilder`, `StringSchemaBuilder`.
- [ ] All five classes still expose a public no-arg constructor
      (supplied by the compiler) and callers compile unchanged.
- [ ] No call site that does `new BooleanSchemaBuilder()` etc.
      breaks.

### S115 — constant naming in ConformanceTools

- [ ] The six offending constants at lines 401-403 and 418-420 of
      `ConformanceTools.java` are renamed to match
      `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`.
- [ ] Their serialized wire values (the strings that end up in the
      JSON payload) are unchanged.
- [ ] Any references to the constants elsewhere in the file are
      updated to the new names.

### S1192 — duplicated literals

- [ ] `ConformanceTools`'s `"test_tool_with_logging"` literal is
      replaced by a `private static final String` constant used in
      all three places.
- [ ] `StreamableHttpController`'s `"error"` literal is replaced
      by a `private static final String ERROR_KEY` (or similar)
      constant used in all seven places.

### Build

- [ ] `mvn verify` passes across the full reactor.
- [ ] Re-running the Sonar scan shows the 13 CRITICAL issues
      removed (5× S1186, 6× S115, 2× S1192).

## Implementation notes

- **Commit granularity**: one commit per rule is reasonable, or
  bundle all three into a single "fix critical sonar issues"
  commit. Either works — all changes are mechanical.
- **Be careful with the constant renames in `ConformanceTools`**
  — these might be used as lookup keys in a switch expression or
  map. Verify the rename is purely Java-side and doesn't change
  any serialized output.
- **`StreamableHttpController.ERROR_KEY`**: the file already has
  a commit in the git history titled "fix: use ERROR_KEY constant
  in createErrorResponse" (commit `de2bd01`). That commit added
  the constant in one place; this spec finishes the job by
  applying it to the 7 remaining occurrences.
- **Empty constructors are a cost-free delete**. Removing them
  doesn't change the compiled bytecode in a way that affects
  runtime behavior, and the public no-arg constructor remains.
  Verify with `javap` or by recompiling downstream consumers if
  you're paranoid.
