# Chain AssertJ assertions (Sonar S5853)

## What to build

The single largest Sonar rule violation in mocapi is
`java:S5853` — "Join these multiple assertions subject to one
assertion chain" — with **56 occurrences** across 11 test files.
Each is a MINOR code smell with a 5-minute effort estimate, and
the fix is purely mechanical: combine consecutive `assertThat(x)`
calls on the same subject into a single chained call.

### Example

Before:
```java
assertThat(response).isNotNull();
assertThat(response.status()).isEqualTo(200);
assertThat(response.body()).contains("ok");
```

After:
```java
assertThat(response)
    .isNotNull()
    .extracting(Response::status, Response::body)
    .containsExactly(200, "<body that contains ok>");
```

Or, for cases where extraction doesn't apply naturally, AssertJ's
`satisfies` pattern:

```java
assertThat(response)
    .isNotNull()
    .satisfies(r -> {
      assertThat(r.status()).isEqualTo(200);
      assertThat(r.body()).contains("ok");
    });
```

Or, the simplest form if the subject is the same across
consecutive assertions:

```java
assertThat(response).isNotNull().hasStatus(200).hasBodyContaining("ok");
```

The rule is triggered when two or more consecutive `assertThat`
calls target the **same subject** (or a subject that can be
reached via a single fluent chain). The fix is to merge them.

### Affected files

Per the Sonar scan, 56 issues are distributed across 11 files:

| File | Count |
|---|---|
| `mocapi-model/src/test/.../RequestAndNotificationTypesSerializationTest.java` | 14 |
| `mocapi-model/src/test/.../ElicitationTypesSerializationTest.java` | 13 |
| `mocapi-core/src/test/.../stream/elicitation/PropertySchemaSerializationTest.java` | 12 |
| `mocapi-model/src/test/.../PrimitiveSchemaDefinitionHierarchyTest.java` | 5 |
| `mocapi-model/src/test/.../RequestMetaAndProgressSerializationTest.java` | 3 |
| `mocapi-model/src/test/.../ProtocolTypesSerializationTest.java` | 2 |
| `mocapi-model/src/test/.../ContentBlockSerializationTest.java` | 2 |
| `mocapi-core/src/test/.../server/McpServerTest.java` | 2 |
| `mocapi-compat/src/test/.../ToolsCallElicitationIT.java` | 1 |
| `mocapi-compat/src/test/.../ToolsCallWithLoggingIT.java` | 1 |
| `mocapi-compat/src/test/.../ToolsCallWithProgressIT.java` | 1 |

The bulk is in the serialization tests — those tests typically
assert on multiple fields of the same deserialized record. They're
the easiest to fix because they follow the same shape.

### Fix pattern for serialization tests

Before:
```java
var result = mapper.readValue(json, InitializeResult.class);
assertThat(result.protocolVersion()).isEqualTo("2025-11-25");
assertThat(result.capabilities()).isNotNull();
assertThat(result.serverInfo().name()).isEqualTo("test");
```

After:
```java
var result = mapper.readValue(json, InitializeResult.class);
assertThat(result)
    .extracting(
        InitializeResult::protocolVersion,
        r -> r.capabilities() != null,
        r -> r.serverInfo().name())
    .containsExactly("2025-11-25", true, "test");
```

Or with `satisfies`:
```java
assertThat(result).satisfies(r -> {
  assertThat(r.protocolVersion()).isEqualTo("2025-11-25");
  assertThat(r.capabilities()).isNotNull();
  assertThat(r.serverInfo().name()).isEqualTo("test");
});
```

`satisfies` reads slightly better for multi-field assertions
because it preserves the individual assertion messages on failure.
`extracting(...).containsExactly(...)` is more compact but the
failure message just says "expected [a, b, c] but was [d, e, f]"
without calling out which specific field failed.

**Recommendation**: prefer `satisfies` for readability. Use
`extracting(...).containsExactly(...)` only when the test is
asserting on 2-3 simple primitive fields where the compactness is
a clear win.

## Acceptance criteria

- [ ] Running the Sonar scan after the changes shows **0** open
      `java:S5853` issues.
- [ ] Every test that previously had consecutive `assertThat`
      calls on the same subject has been refactored to a single
      chained call (via `satisfies`, `extracting`, or direct
      fluent assertions like `isNotNull().hasSize(3).contains(...)`).
- [ ] Test failure messages still identify the failing field —
      don't replace a clear per-field message with a confusing
      "containsExactly" failure unless the fields are obviously
      related.
- [ ] **No test behavior changes**. Every test that was passing
      before must still pass. Every test that was failing before
      (there shouldn't be any) must still fail. The refactor is
      purely cosmetic.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **This is the highest-volume spec in the Sonar cleanup series
  but the lowest-risk**. Every change is a local refactor inside
  a single test method; no cross-file coordination needed.
- **Work file by file, not issue by issue**. Open a file, find
  all `assertThat` clusters, rewrite them, run the test, move on.
- **Prefer `satisfies` over `extracting`** for readability. Only
  use `extracting` + `containsExactly` when the assertion is on
  2-3 simple fields and compactness matters.
- **Watch for `NotNull` → field access patterns**. If the original
  code does:
  ```java
  assertThat(result).isNotNull();
  assertThat(result.field()).isEqualTo("x");
  ```
  the refactor should preserve the null-check semantics:
  ```java
  assertThat(result).isNotNull().extracting(Record::field).isEqualTo("x");
  ```
  The `isNotNull()` on the `assertThat(result)` chain ensures the
  subsequent `extracting` call doesn't NPE.
- **Parameterized tests**: some of the assertions may be in
  `@ParameterizedTest` methods that run multiple scenarios. The
  refactor still applies — just make sure the chained assertion
  still makes sense for each parameter set.
- **Don't introduce new AssertJ dependencies**. Everything
  described here is in `org.assertj.core.api.Assertions` which
  is already on the test classpath.
- **Commit granularity**: one commit per file is fine, or one
  commit per test class nested structure (e.g., `PropertySchemaSerializationTest`
  as a single commit even though it has 12 issues). Splitting into
  56 micro-commits isn't useful.
- **Run the specific test after each file edit** to catch any
  accidental behavioral changes early:
  ```
  mvn -pl mocapi-model test -Dtest=RequestAndNotificationTypesSerializationTest
  ```
- **Don't change test method names or `@DisplayName`s**. Those
  are documentation that's unrelated to the refactor.
