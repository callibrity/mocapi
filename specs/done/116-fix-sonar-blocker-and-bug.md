# Fix Sonar blocker and bug

## What to build

Two high-priority Sonar findings that need immediate attention.

### Issue 1: `ProgressNotification.METHOD` name clash (BLOCKER, `java:S1845`)

```java
// mocapi-model/src/main/java/com/callibrity/mocapi/model/ProgressNotification.java
public record ProgressNotification(String method, ProgressNotificationParams params) {

  public static final String METHOD = "notifications/progress";
}
```

Sonar flags this as a BLOCKER because the `METHOD` constant and the
`method()` accessor differ only by case, which is error-prone when
autocompleting or reading quickly. Rename the constant:

```java
public record ProgressNotification(String method, ProgressNotificationParams params) {

  public static final String METHOD_NAME = "notifications/progress";
}
```

Update every call site that references `ProgressNotification.METHOD`
to use `ProgressNotification.METHOD_NAME`.

### Issue 2: `CountdownTool.sendProgress` numeric promotion (BUG, `java:S2184`)

```java
// mocapi-example/src/main/java/com/callibrity/mocapi/example/tools/CountdownTool.java:30
for (int i = from; i > 0; i--) {
  ctx.sendProgress(from - i, from);   // ← S2184
```

`sendProgress(double, double)` takes doubles, and `from - i` is an
`int` subtraction that gets implicitly widened. Sonar flags this
because the author's intent is ambiguous — are they expecting
integer arithmetic or floating point? Fix by explicitly casting one
operand before the subtraction so the intent is clear:

```java
ctx.sendProgress((double) (from - i), from);
```

Or, cleaner, keep the loop variable as `double` from the start:

```java
for (double i = from; i > 0; i--) {
  ctx.sendProgress(from - i, from);
```

Pick whichever feels cleaner — the point is to make the double
context explicit.

## Acceptance criteria

- [ ] `ProgressNotification.METHOD` is renamed to
      `ProgressNotification.METHOD_NAME`.
- [ ] Every reference to `ProgressNotification.METHOD` in the
      reactor is updated. Grep to verify: `grep -rn
      "ProgressNotification.METHOD\b" --include="*.java"` returns
      no matches after the change.
- [ ] `CountdownTool.sendProgress(...)` cast/typing change resolves
      the `S2184` warning (one operand is explicitly double).
- [ ] `mvn verify` passes across the full reactor.
- [ ] Re-running the Sonar scan shows the BLOCKER and BUG removed
      from the project's open issues list.

## Implementation notes

- **`METHOD_NAME` is the canonical rename** for this kind of case —
  it's unambiguous, matches similar constants in the codebase
  (`ProgressNotification.METHOD_NAME`,
  `JsonRpcProtocol.VERSION`, etc.), and doesn't collide with the
  record accessor.
- **Callers are likely in `DefaultMcpStreamContext`** (the class
  that publishes progress notifications) and possibly in model
  tests. Grep first, update everywhere, then run the reactor.
- **The CountdownTool fix is a one-line cast.** No test changes
  needed — the behavior is byte-identical (integer arithmetic
  widened to double is mathematically the same as double
  arithmetic for the integer values in this loop).
- Commit suggestion: a single small commit covering both fixes.
  Blocker + bug fix together is a natural unit.
