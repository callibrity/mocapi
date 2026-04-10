# Remove unnecessary double cast (Sonar S1905)

## What to build

SonarCloud flagged 1 `java:S1905` issue — "Remove this
unnecessary cast to double." This is a small, one-line fix
that pairs naturally with spec 133 (the `long` overload of
`sendProgress`).

### Background

Spec 116 (the earlier Sonar blocker/bug fix) added a
`(double)` cast inside `CountdownTool.countdown(...)` to
resolve Sonar rule S2184 ("Cast one of the operands of this
subtraction operation to a double"):

```java
for (int i = from; i > 0; i--) {
  ctx.sendProgress((double) (from - i), from);   // ← S116 added the cast
  // ...
}
```

The cast was necessary because `sendProgress` only had a
`double, double` overload, and Sonar wanted the `int`
subtraction `from - i` to be explicitly widened for clarity.

Once spec 133 lands (adding a default `void sendProgress(long,
long)` method on `McpStreamContext` that delegates to the
double overload), the cast becomes unnecessary because the
`int` subtraction resolves naturally to the `long` overload:

```java
for (int i = from; i > 0; i--) {
  ctx.sendProgress(from - i, from);   // ← resolves to sendProgress(long, long)
  // ...
}
```

`int` values widen to `long` via the language's standard
primitive conversion rules, and the long overload delegates
to the double one internally. The observable behavior is
identical; the code reads better.

### What to change

Exactly one file, exactly one line (the `sendProgress` call
inside the countdown loop):

- **File**: `mocapi-example/src/main/java/com/callibrity/mocapi/example/tools/CountdownTool.java`
- **Line**: ~30 (the `ctx.sendProgress((double) (from - i), from);` call)
- **Change**: drop the `(double)` cast

If Sonar flags additional `(double)` casts elsewhere (e.g., in
a test file that also casts for no reason), fix those too —
but the primary target is `CountdownTool`.

### Relationship to spec 132

Spec 132 (enforce void return for streaming tools) also
rewrites `CountdownTool` — it removes the non-void return and
switches to `ctx.sendResult(...)`. The two changes are
independent but touch the same file. Whichever spec lands
first, the other spec updates the same file again.

Specifically:
- If **132 lands first**: the file has `void countdown(...)`
  with `ctx.sendResult(...)` at the end. Dropping the
  `(double)` cast is the only change this spec needs to
  make.
- If **141 lands first**: the file has the old non-void
  return. Drop the cast here; spec 132 will remove the
  return later.

Either ordering works. No coordination needed beyond
awareness.

## Acceptance criteria

- [ ] The `(double) (from - i)` cast in `CountdownTool.java`
      is removed. The line reads
      `ctx.sendProgress(from - i, from);`.
- [ ] Any other `(double)` casts in the reactor that Sonar
      flags as `java:S1905` are also cleaned up. Use the
      Sonar API query below to find them all:
      ```bash
      curl -s "https://sonarcloud.io/api/issues/search?componentKeys=callibrity_mocapi&resolved=false&rules=java:S1905&ps=500" \
        | python3 -c "import json, sys; data = json.load(sys.stdin); [print(i['component'].replace('callibrity_mocapi:', '') + ':' + str(i['line'])) for i in data['issues']]"
      ```
- [ ] After the change, running the Sonar scan shows **zero**
      `java:S1905` issues in mocapi.
- [ ] `CountdownToolTest` (and any `CountdownToolIT`) continue
      to pass. The tests assert on behavior, not on the
      specific primitive type passed to `sendProgress`, so
      the signature resolution change from `(double, double)`
      to `(long, long)` is invisible to them.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Depends on spec 133**: removing the cast is only clean
  after the `sendProgress(long, long)` default method is
  available on `McpStreamContext`. If spec 133 hasn't landed
  yet, this spec's fix would resolve to `sendProgress(double,
  double)` via widening and Sonar would still flag the
  subtraction with S2184. Land 133 first.
- **Alternative ordering**: if spec 133 is blocked or
  delayed, this spec can be deferred — the `(double)` cast
  is harmless (just verbose), so there's no urgency.
- **Commit suggested**: one small, focused commit. Don't
  bundle with unrelated changes. Title:
  `fix: drop redundant (double) cast in CountdownTool (S1905)`.
