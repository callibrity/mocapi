# Replace Thread.sleep in tests with Awaitility (Sonar S2925)

## What to build

SonarCloud flagged 8 occurrences of `java:S2925` — "Remove this
use of `Thread.sleep()`" — across mocapi's test tree. Sonar's
reasoning: `Thread.sleep(millis)` in a test is almost always a
code smell because it either (a) waits too long and slows the
suite down or (b) waits too short and produces flaky failures
on slower hardware. The canonical fix is to use
[Awaitility](http://www.awaitility.org/) for polling-with-timeout
semantics instead.

### Example transformation

**Before** (flaky, slow):
```java
sessionStore.save(session, Duration.ofSeconds(1));
Thread.sleep(2000);  // wait for expiration
assertThat(sessionStore.find(session.sessionId())).isEmpty();
```

**After** (tight, reliable):
```java
import static org.awaitility.Awaitility.await;

sessionStore.save(session, Duration.ofSeconds(1));
await()
    .atMost(Duration.ofSeconds(3))
    .until(() -> sessionStore.find(session.sessionId()).isEmpty());
```

Awaitility polls the condition on an interval (default 100ms)
up to a maximum duration, asserting success as soon as the
condition holds. Fast hardware finishes quickly; slow hardware
still completes correctly. No wasted wall time, no flakiness.

### Awaitility patterns the replacements will need

1. **Wait for a condition to become true** — the most common:
   ```java
   await().atMost(Duration.ofSeconds(3)).until(() -> predicate);
   ```
2. **Wait and then assert** — when the assertion itself is
   what you're polling:
   ```java
   await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
     assertThat(result).isNotNull();
     assertThat(result.field()).isEqualTo("expected");
   });
   ```
3. **Wait for a value to match**:
   ```java
   await().atMost(Duration.ofSeconds(3))
       .until(() -> cache.get(key), equalTo(expectedValue));
   ```
4. **Polling interval tuning** — default 100ms is almost
   always fine. Override only if a specific test needs
   sub-100ms or very-infrequent polling:
   ```java
   await().pollInterval(Duration.ofMillis(50))
          .atMost(Duration.ofSeconds(3))
          .until(...);
   ```

### Where the 8 occurrences are

Per the most recent Sonar scan, `Thread.sleep` is used in
various `mocapi-core/src/test` and possibly `mocapi-compat/src/test`
files. Before starting, re-run the Sonar query to get a fresh
list:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=callibrity_mocapi&resolved=false&rules=java:S2925&ps=500" \
  | python3 -c "import json, sys; data = json.load(sys.stdin); [print(i['component'].replace('callibrity_mocapi:', '') + ':' + str(i['line'])) for i in data['issues']]"
```

This produces the exact file+line list for all 8 occurrences.

### Legitimate `Thread.sleep` cases

A small number of `Thread.sleep` calls may be legitimate
(e.g., sleeping a known-fixed 500ms *inside* production code
that intentionally paces something — `CountdownTool` does
this). Those are in `src/main`, not `src/test`, and S2925
shouldn't flag them. **This spec is strictly about
`src/test`**; do not touch production-code `Thread.sleep`
unless Sonar is flagging a specific line number.

## Acceptance criteria

- [ ] The Awaitility dependency is added to the `mocapi-parent`
      pom's `<dependencyManagement>` section:
      ```xml
      <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <version>${awaitility.version}</version>
        <scope>test</scope>
      </dependency>
      ```
      with an `<awaitility.version>` property (check Maven
      Central for the current release; 4.2.x is current as of
      this spec's writing).
- [ ] Each mocapi module that has a test using `Thread.sleep`
      declares the test-scope Awaitility dependency in its own
      pom.xml (inheriting the version from the parent).
- [ ] Every `Thread.sleep` call flagged by Sonar in
      `src/test/java/**` is replaced with an Awaitility
      construct (`await().until(...)`, `await().untilAsserted(...)`,
      or similar). Timeouts are sized generously — use
      `atMost(Duration.ofSeconds(3))` or longer for operations
      that may take hundreds of milliseconds; don't tighten
      below reasonable hardware margins.
- [ ] After the changes, running the Sonar scan shows **zero**
      `java:S2925` issues in mocapi.
- [ ] All existing tests continue to pass — the Awaitility
      replacements must preserve the original test semantics
      (same assertions, same expected outcomes).
- [ ] No test that was passing before becomes flaky. Run each
      affected test class at least 10 times in a row to
      verify stability before merging.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Awaitility timeouts**: don't copy the previous
  `Thread.sleep(2000)` duration as the Awaitility `atMost()`.
  The sleep was a worst-case wait; Awaitility returns as
  soon as the condition holds, so a `3x` margin over the
  expected duration is safe. If the previous sleep was
  1000ms, use `atMost(Duration.ofSeconds(3))` for Awaitility.
- **Exception wrapping**: Awaitility wraps lambda exceptions
  in `ConditionTimeoutException`. If the original test
  expected a specific exception class, update the assertion
  accordingly or use `untilAsserted` which preserves the
  AssertJ assertion failure message directly.
- **Don't change test logic beyond the sleep replacement**.
  This spec is a narrow refactor; don't take the opportunity
  to rewrite entire test classes.
- **Run each affected test repeatedly**: flakiness is the
  whole reason Sonar flags `Thread.sleep`. The replacement
  must be MORE reliable, not just different. Run each class
  ~10 times in a row (`mvn -pl <module> test -Dtest=<Class>`
  in a loop) before marking the change done.
- **If a sleep genuinely can't be replaced** (e.g., the test
  is asserting that nothing happens for a fixed duration),
  consider whether the test should exist at all. If it must,
  a `@SuppressWarnings("java:S2925")` with a comment
  explaining why is acceptable — but the CLAUDE.md
  no-suppressions rule applies. Prefer refactoring.
- **Commit granularity**: one commit per test file, or bundle
  small files together. Eight occurrences across a handful
  of files is a reasonable single-PR scope.
