# Fix Sonar deprecation hygiene (S1874, S6355, S1133, S1123)

## What to build

Four related Sonar rules all dealing with `@Deprecated` API
handling — 26 issues total across the four rules. The common
theme: mocapi legitimately keeps some `@Deprecated` code (e.g.,
`LegacyTitledEnumSchema` and its builder, because the MCP spec
still defines the legacy titled-enum variant for backward
compatibility), and the deprecated code is exercised by tests.
Sonar flags both the "don't use deprecated APIs" cases and the
"you said it's deprecated but didn't document it" cases.

### Rule 1: `java:S1874` — using deprecated code (19 issues)

19 occurrences of code that legitimately uses `@Deprecated` APIs,
mostly tests that are explicitly testing the deprecated variants.
Breakdown:

| File | Count |
|---|---|
| `mocapi-core/src/test/.../elicitation/LegacyTitledEnumSchemaBuilderTest.java` | 6 |
| `mocapi-model/src/test/.../PrimitiveSchemaDefinitionHierarchyTest.java` | 4 |
| `mocapi-core/src/test/.../elicitation/PropertySchemaSerializationTest.java` | 1 |
| `mocapi-core/src/test/.../elicitation/RequestedSchemaBuilderTest.java` | 1 |
| `mocapi-model/src/main/java/.../EnumSchema.java` | 1 |
| `mocapi-core/src/test/.../elicitation/RequestedSchemaBuilderGoldenJsonTest.java` | 1 |
| `mocapi-model/src/test/.../RequestAndNotificationTypesSerializationTest.java` | 1 |
| `mocapi-model/src/main/java/.../LegacyTitledEnumSchema.java` | 1 |
| `mocapi-model/src/test/.../ElicitationTypesSerializationTest.java` | 1 |
| `mocapi-compat/src/main/.../ConformanceTools.java` | 1 |
| `mocapi-core/src/main/.../MocapiAutoConfiguration.java` | 1 |

**Fix strategy**: add `@SuppressWarnings("deprecation")` at the
narrowest scope possible — on the specific method, not the class.
In tests, the suppression is semantic: "I know I'm testing the
deprecated API, that's the point." In production code (`EnumSchema`,
`LegacyTitledEnumSchema`, `ConformanceTools`,
`MocapiAutoConfiguration`), the suppression means "I have to
support this for backward compatibility per the spec."

For the `LegacyTitledEnumSchema.java` and `EnumSchema.java` cases
where the deprecation is *on the class itself* and the warning is
that the sealed parent interface permits it, the suppression goes
on the `permits` clause or the `@JsonSubTypes` annotation, or at
the class level for the sealed parent.

### Rule 2: `java:S6355` — `@Deprecated` without `since` or `forRemoval` (3 issues)

| File |
|---|
| `mocapi-core/src/main/.../elicitation/RequestedSchemaBuilder.java` |
| `mocapi-model/src/main/.../LegacyTitledEnumSchema.java` |
| `mocapi-core/src/main/.../elicitation/LegacyTitledEnumSchemaBuilder.java` |

Fix: add `since` and `forRemoval` to the `@Deprecated` annotations:

```java
// Before
@Deprecated
public record LegacyTitledEnumSchema(...) implements PrimitiveSchemaDefinition { ... }

// After
@Deprecated(since = "0.0.1", forRemoval = false)
public record LegacyTitledEnumSchema(...) implements PrimitiveSchemaDefinition { ... }
```

The MCP spec defines `LegacyTitledEnumSchema` as deprecated but
**not** for removal (it's still there for clients that haven't
migrated away from `enumNames`). So `forRemoval = false` is the
accurate value. The `since` version should be the mocapi version
in which the type was introduced (check the git log for the
original commit; `"0.0.1"` is the likely answer since mocapi is
still pre-1.0).

### Rule 3: `java:S1133` — "remove this deprecated code someday" (2 issues)

This rule fires when `@Deprecated(forRemoval = true)` code is
still present. It's a gentle reminder, not a hard error — Sonar
wants to make sure the deprecation isn't forgotten. After fixing
S6355 (above) to set `forRemoval = false` where appropriate, the
S1133 instances should go away naturally. If any remain after
that, check whether the `forRemoval = true` is actually correct,
or whether the type should be permanently deprecated
(`forRemoval = false`).

### Rule 4: `java:S1123` — `@Deprecated` without `@deprecated` javadoc tag (2 issues)

The `@Deprecated` annotation requires a companion `@deprecated`
javadoc tag explaining:
1. What is deprecated
2. Why it's deprecated
3. What to use instead

Find the 2 offending classes and add the javadoc tag:

```java
/**
 * Legacy titled enum schema variant.
 *
 * @deprecated Use {@link TitledSingleSelectEnumSchema} or
 *     {@link TitledMultiSelectEnumSchema} instead. This variant
 *     exists for backward compatibility with pre-2025-11-25 MCP
 *     clients that use the {@code enumNames} array form. New
 *     schemas should use the {@code oneOf} / {@code anyOf} form.
 */
@Deprecated(since = "0.0.1", forRemoval = false)
public record LegacyTitledEnumSchema(...) ...
```

## Acceptance criteria

### S1874 suppressions

- [ ] Each of the 19 S1874 occurrences has a
      `@SuppressWarnings("deprecation")` at the narrowest useful
      scope (method-level preferred over class-level, unless the
      same method has many deprecated calls).
- [ ] The `@SuppressWarnings` is accompanied by a short comment
      explaining *why* the deprecated usage is legitimate (e.g.,
      "Legitimate — this test verifies the deprecated legacy
      variant continues to work per MCP spec backward
      compatibility.").

### S6355 — `@Deprecated` metadata

- [ ] `LegacyTitledEnumSchema.java` has
      `@Deprecated(since = "0.0.1", forRemoval = false)`.
- [ ] `LegacyTitledEnumSchemaBuilder.java` has the same.
- [ ] The `RequestedSchemaBuilder.java` deprecation gets its
      `since` and `forRemoval` added. Pick `forRemoval = true` or
      `false` based on whether mocapi plans to remove the method
      eventually — default to `false` if unsure.

### S1133 — `forRemoval = true` reminders

- [ ] After fixing S6355, re-verify that any remaining S1133
      issues correspond to types legitimately marked
      `forRemoval = true`. Document the removal plan in the javadoc
      (e.g., "Scheduled for removal in mocapi 1.0").

### S1123 — `@deprecated` javadoc tag

- [ ] Every `@Deprecated`-annotated type or method has a
      corresponding `@deprecated` javadoc tag explaining what to
      use instead.

### Build and Sonar

- [ ] `mvn verify` passes across the full reactor.
- [ ] Re-running the Sonar scan shows all 26 deprecation-related
      issues (19 + 3 + 2 + 2) removed.
- [ ] `javadoc` generation (via maven-javadoc-plugin in the
      release profile) produces no warnings about deprecation
      tags.

## Implementation notes

- **S1874 is the noisy one (19 issues)**, but each fix is a
  single-line annotation addition. Tests are the bulk — they're
  verifying the deprecated API, which is exactly when
  `@SuppressWarnings("deprecation")` is correct.
- **Prefer narrow scopes for suppressions**. Adding
  `@SuppressWarnings("deprecation")` to a whole class suppresses
  *all* deprecation warnings in that class, which can hide future
  unrelated issues. Method-level or statement-level (on the
  declaration of a local variable that holds a deprecated type)
  is better.
- **Document the "why"** with a comment next to every suppression.
  Without a comment, a future maintainer can't tell whether the
  suppression is "we're testing the legacy API on purpose" vs
  "someone was in a hurry and suppressed the warning to get the
  build green."
- **The MCP spec reference**: `LegacyTitledEnumSchema` exists
  because the spec's schema.ts defines it explicitly as a
  backward-compat variant. The deprecation is semantic ("don't
  use this in new code"), not destined for removal from the
  spec. That's why `forRemoval = false` is correct.
- **`EnumSchema.java` sealed parent case**: the S1874 on this file
  is likely about the `permits` clause or `@JsonSubTypes`
  including `LegacyTitledEnumSchema`. Add
  `@SuppressWarnings("deprecation")` at the interface level with
  a comment. The sealed parent needs to know about its deprecated
  child, there's no way around that.
- **Commit granularity**: one commit per rule is clean. Or bundle
  S6355/S1133/S1123 into one "deprecation metadata" commit and
  S1874 into a separate "deprecation suppressions" commit. Both
  approaches leave a reviewable diff.
