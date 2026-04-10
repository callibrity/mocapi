# Replace deprecated JsonNode.asText() with asString() (Sonar S1874)

## What to build

SonarCloud flagged 3 `java:S1874` issues — "Remove this use of
`asText`; it is deprecated." Jackson 3 (the
`tools.jackson.databind.*` package that mocapi uses) deprecated
`JsonNode.asText()` in favor of `JsonNode.asString()`. The two
methods are semantically identical for non-null JSON values,
but Jackson 3 prefers `asString()` for consistency with the
rest of the "typed coercion" API (`asInt()`, `asLong()`,
`asBoolean()`, `asDouble()`).

Find and replace every `JsonNode.asText()` call in the mocapi
codebase with `JsonNode.asString()`.

### Why this matters

- **Deprecation hygiene**: the same principle as spec 118 —
  when the spec/library tells us an API is deprecated, we
  migrate.
- **Jackson 3 consistency**: `asString()` is the blessed
  method name in Jackson 3. Using it throughout keeps the
  codebase aligned with the library's idiomatic style.
- **Not protected by the CLAUDE.md exception**: the
  `@SuppressWarnings("deprecation")` carve-out in CLAUDE.md
  is **specifically** for MCP-spec-mandated deprecated
  types (like `LegacyTitledEnumSchema`). Jackson's
  `asText()` → `asString()` migration doesn't qualify for
  the exception — it's a library modernization, not a
  spec-compliance requirement. Fix by migrating, not
  suppressing.

### Identifying the 3 occurrences

Get the exact list from Sonar:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=callibrity_mocapi&resolved=false&rules=java:S1874&ps=500" \
  | python3 -c "import json, sys; data = json.load(sys.stdin); [print(i['component'].replace('callibrity_mocapi:', '') + ':' + str(i['line']) + ' — ' + i['message'][:80]) for i in data['issues']]"
```

Note that `java:S1874` is a multi-purpose "deprecated API in
use" rule, so the query may also return the spec-mandated
`LegacyTitledEnumSchema` uses that spec 118 handled with
`@SuppressWarnings("deprecation")`. **Filter to just the
`asText` ones** — those are what this spec targets.

### Fix template

For each of the 3 call sites:

```java
// Before
String value = node.get("field").asText();

// After
String value = node.get("field").asString();
```

Identical return value for non-null nodes. For null nodes,
`asText()` returns the string `"null"` while `asString()`
returns the string `"null"` too (both coerce
`NullNode.getInstance()` to its string representation), so
there's no null-handling difference to worry about.

### If any call site passes a default argument

`JsonNode.asText(String defaultValue)` has an overload that
returns the default when the node is missing or can't be
coerced. The Jackson 3 equivalent is
`asString(String defaultValue)`. Migrate with the same
overload:

```java
// Before
String value = node.get("field").asText("fallback");

// After
String value = node.get("field").asString("fallback");
```

## Acceptance criteria

- [ ] Every `JsonNode.asText()` call in
      `mocapi-core/src/main`, `mocapi-model/src/main`, and
      `mocapi-compat/src/main` is replaced with
      `JsonNode.asString()`.
- [ ] Every `JsonNode.asText(String)` overload usage is
      replaced with `JsonNode.asString(String)`.
- [ ] `grep -rn "\.asText(" mocapi-*/src/main` returns zero
      matches after the change.
- [ ] `grep -rn "\.asText(" mocapi-*/src/test` is also
      cleaned up (test code deserves the same modernization,
      and Sonar may flag test-code deprecations too).
- [ ] After the changes, running the Sonar scan shows the
      3 `java:S1874` issues related to `asText` cleared.
      (Other `java:S1874` issues related to
      `LegacyTitledEnumSchema` — which are legitimately
      suppressed by spec 118 — remain unchanged.)
- [ ] All existing tests continue to pass.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **`asText` vs `asString` return-value equivalence**: for
  JSON primitive types (string, number, boolean), both
  methods return the string representation. For JSON null,
  both return `"null"`. For missing nodes (`node.get("absent")`
  returns `null`), both throw NPE. Confirmed in Jackson 3's
  Javadoc. The replacement is a mechanical rename with no
  behavior change.
- **IDE-assisted refactor**: IntelliJ IDEA's "Inspect Code"
  feature can find all deprecation warnings and offer
  quick-fix replacements. Use that to do the replacement in
  one pass, then commit.
- **Don't add `@SuppressWarnings("deprecation")`** for these.
  The CLAUDE.md exception is only for spec-mandated
  deprecated types. Jackson's API modernization doesn't
  qualify — fix the underlying issue (use the non-deprecated
  method).
- **Commit granularity**: single commit covering all 3 (or
  more, if tests also have `asText`) call sites. Small,
  focused, mechanical.
