# Fix remaining Sonar minor issues

## What to build

After specs 116 (blocker + bug), 117 (critical code smells), 118
(deprecation hygiene), and 119 (AssertJ chains) land, the
remaining open issues from the Sonar scan are a long tail of
~26 minor code smells distributed across ~13 rules. This spec
bundles them into a single cleanup pass because each is a
one-to-three line fix.

### Issue breakdown

| Rule | Count | Summary | Typical fix |
|---|---|---|---|
| `java:S5778` | 6 | Lambda with multiple throwing invocations | Extract the throwing call to a variable before the `assertThatThrownBy(() -> ...)` lambda |
| `java:S5838` | 2 | `assertThat(x).isEqualTo(0)` should use `isZero()` | Replace with `isZero()` |
| `java:S1130` | 2 | `throws Exception` declared but never thrown | Remove the unused declaration |
| `java:S2094` | 2 | Empty class | Remove it or make it an interface |
| `java:S7467` | 2 | Unused catch variable `e` → use `_` | Rename to unnamed pattern |
| `java:S1612` | 3 | Lambda should be a method reference | `s -> s.toUpperCase()` → `String::toUpperCase` |
| `java:S3400` | 1 | Method returns a constant | Replace with a constant declaration |
| `java:S6068` | 1 | Useless `eq(...)` in Mockito matcher | Drop the `eq` and pass the value directly |
| `java:S4144` | 1 | Two test methods have identical implementations | Parameterize or delete the duplicate |
| `java:S5976` | 1 | Three tests should be parameterized | Replace with `@ParameterizedTest` + `@ValueSource` |
| `java:S8432` | 1 | Useless `ScopedValue.Carrier` not run | Call `.run()` / `.call()` or remove |
| `java:S107` | 1 | Constructor with 9 parameters (max 7) | Add `@SuppressWarnings("java:S107")` with justification, OR introduce a params wrapper |
| `java:S1659` | 1 | Declare `sse` on a separate line | Split the declaration |

### Notes on specific fixes

**`java:S107` — constructor with too many parameters.**
`DefaultMcpStreamContext` has a 9-argument constructor. Splitting
into a params object or builder is possible but adds surface area.
The pragmatic fix is `@SuppressWarnings("java:S107")` with a
comment explaining "all 9 parameters are independent collaborators
injected by Spring; a wrapper would obscure the dependency graph."

**`java:S5778` — lambda with multiple throwing calls.**
Common pattern:
```java
assertThatThrownBy(() -> {
  var x = someCall();         // throws
  var y = anotherCall(x);     // also throws
  return y.field();
}).isInstanceOf(Foo.class);
```
Sonar wants the throwing call isolated:
```java
var x = someCall();              // if this one shouldn't throw
assertThatThrownBy(() -> anotherCall(x)).isInstanceOf(Foo.class);
```
Or if both *should* throw, refactor into two separate
`assertThatThrownBy` calls so each asserts on exactly one failure
mode.

**`java:S7467` — unnamed catch patterns.**
Java 21 introduced unnamed variables (`_`):
```java
// Before
try { ... } catch (Exception e) { /* ignored */ }

// After
try { ... } catch (Exception _) { /* ignored */ }
```
Mocapi targets Java 25, so this is available.

**`java:S4144` — duplicate test methods.**
Find the two methods with identical bodies, decide which to keep,
and delete the other. If both are meaningful (different test
names, same body), parameterize them with a common helper.

**`java:S5976` — parameterize three similar tests.**
Find the three tests, combine into a `@ParameterizedTest` driven
by `@ValueSource` or `@MethodSource`. Preserves the same coverage
with less code.

**`java:S8432` — unused `ScopedValue.Carrier`.**
Probably a test or initialization that built a `ScopedValue.Carrier`
via `.where(...)` but never called `.run(...)` or `.call(...)`.
Either complete the invocation or delete the carrier.

**`java:S6068` — useless `eq(...)` in Mockito.**
Mockito's `eq()` matcher is only needed when mixing with other
matchers. If all arguments in a `when(...)` call are literal
values, drop `eq` and pass them directly:
```java
// Before
when(foo.bar(eq("x"), eq(1))).thenReturn(...);
// After
when(foo.bar("x", 1)).thenReturn(...);
```

**`java:S2094` — empty class.**
Two empty classes exist. Determine if they're placeholders
(delete), interface candidates (convert to `interface`), or
legitimate marker classes (add a nested comment).

## Acceptance criteria

- [ ] Running the Sonar scan after this spec lands shows **zero**
      open issues for all 13 rules listed above.
- [ ] Each fix preserves test behavior — no test that was passing
      stops passing, no coverage is lost.
- [ ] `@SuppressWarnings` annotations are used only where a
      refactor would be objectively worse than the suppression.
      Each suppression is accompanied by a comment explaining the
      reasoning.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Work rule by rule, not file by file** — each rule has a
  distinct fix pattern, and batching rules keeps the mental model
  simple. Start with the simplest (S5838 — `isZero()` rename) and
  work up to the trickiest (S107 — constructor overload).
- **Don't use `@SuppressWarnings` as a crutch**. Only suppress
  when the refactor would be objectively worse. The constructor
  parameter count (S107) is the one case in this list where
  suppression is likely the right answer.
- **Some of these may have been fixed already** by other specs
  (e.g., spec 117 may have touched files that also had S5778
  issues). Re-run the Sonar scan at the start of this spec to
  get a fresh count — work off that snapshot rather than the
  list in this document.
- **Commit granularity**: one commit per rule, or bundle
  mechanically similar rules together (e.g., S5838 + S1612 +
  S7467 are all "rename to cleaner form" and could be one
  commit). Don't split into 26 commits.
- **Don't restructure code beyond what Sonar flags**. If you see
  a test that could be improved beyond the specific sonar issue,
  resist the urge to fix it in this spec — it's out of scope.
- **After the spec lands, the project should have a clean Sonar
  report** (or at least, every remaining issue should be a
  deliberately-accepted one, not a simple cleanup).
