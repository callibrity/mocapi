# Add long overload of sendProgress

## What to build

`McpStreamContext` currently declares only `sendProgress(double, double)`.
Many tool authors reporting progress think in integer units
("processed 50 of 100 items"), not floating-point fractions, and
having to write `ctx.sendProgress((double) processed, (double) total)`
or `ctx.sendProgress(processed, total)` (relying on implicit
widening) is awkward.

Add a convenience overload that takes `long` values:

```java
default void sendProgress(long progress, long total) {
  sendProgress((double) progress, (double) total);
}
```

The default method widens the long values to double and
delegates to the existing abstract `sendProgress(double, double)`
method. Implementers (`DefaultMcpStreamContext`) already
implement the double version; they get the long overload for
free as a default method on the interface.

### Behavioral equivalence

Passing `100L` via the long overload vs `100.0` via the double
overload produces byte-identical JSON on the wire — both end up
as the number `100` in the `ProgressNotificationParams.progress`
field. The double field on the wire has no precision loss for
long values in the safe integer range (up to 2^53), which is
more than enough for typical progress values.

### Method resolution implications

Java's primitive widening order for method overload resolution
is `int → long → float → double`. Callers passing literal
integers or `int` variables currently resolve to the double
overload via widening; after adding the long overload, they
resolve to the long overload instead (which then delegates to
double). The observable behavior is identical.

Callers passing `double` literals (e.g., `0.5`) continue to
resolve to the double overload unchanged.

Callers currently using `(double) (from - i)` casts no longer
need the cast — drop it. Example:

```java
// Before (after spec 116's cast fix)
ctx.sendProgress((double) (from - i), from);

// After (long overload available)
ctx.sendProgress(from - i, from);
```

### Update `CountdownTool`

`CountdownTool.countdown(...)` currently casts to double. Once
this spec lands, drop the cast:

```java
for (int i = from; i > 0; i--) {
  ctx.sendProgress(from - i, from);
  // ...
}
```

The `int` values widen to `long` to match the new overload,
which then delegates to the double implementation.

## Acceptance criteria

- [ ] `McpStreamContext` declares a `default` method
      `void sendProgress(long progress, long total)` that
      delegates to `sendProgress((double) progress, (double) total)`.
- [ ] The existing `void sendProgress(double progress, double total)`
      method remains unchanged as the abstract contract that
      implementations must provide.
- [ ] `DefaultMcpStreamContext` is unchanged — it already
      implements the double version, and inherits the long
      overload from the interface default method.
- [ ] `CountdownTool.countdown(...)` drops the `(double)` cast
      on the `from - i` argument. The resulting code reads
      `ctx.sendProgress(from - i, from);`.
- [ ] A new unit test in `DefaultMcpStreamContextTest` verifies:
  - Calling `ctx.sendProgress(5L, 10L)` publishes a
    `notifications/progress` event with `progress: 5` and
    `total: 10`.
  - Calling `ctx.sendProgress(5, 10)` (int literals) resolves
    to the long overload and produces the same output.
  - Calling `ctx.sendProgress(0.5, 1.0)` (double literals)
    resolves to the double overload.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Default method on the interface**: the long overload is a
  single-line delegation. Putting it as a `default` method on
  `McpStreamContext` means every implementer gets it for free
  without needing to implement it explicitly. Don't add it to
  `DefaultMcpStreamContext` directly — that would require
  every future implementer to also write the overload.
- **Don't deprecate the double overload**. Some callers
  legitimately have fractional progress values
  (e.g., `sendProgress(0.5, 1.0)` for "50% done"). Both forms
  coexist.
- **The `total` parameter convention**: MCP spec allows `total`
  to be null (omitted). We don't support a null total in the
  current API because primitive `double`/`long` can't be
  null. If nullable-total support becomes important, a future
  spec can add an `Optional`-based or overloaded variant. Out
  of scope here.
- **Method resolution gotcha**: if any existing test asserts
  on "sendProgress was called with a Double", updating to the
  long overload may break the assertion because the
  ArgumentCaptor will capture `long` values. Update any such
  tests to capture the correct primitive type or use
  `ArgumentCaptor<Number>`.
- **Commit granularity**: single commit for the interface
  addition, the CountdownTool update, and the test additions.
  Small, focused change.
