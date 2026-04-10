# Add assertions to assertion-less test cases (Sonar S2699)

## What to build

SonarCloud flagged 4 `java:S2699` BLOCKER issues — "Add at
least one assertion to this test case." These are tests that
invoke code but never assert anything about the result.
They're BLOCKER severity because an assertion-less test gives
false coverage confidence: the test passes even if the code
under test is completely broken, as long as it doesn't throw.

### Why this is the highest-priority cleanup

S2699 is the only BLOCKER-level rule still open in the project.
Assertion-less tests are worse than no tests — they show up in
coverage reports as "covered," meaning that code path has a
passing test, while the test actually validates nothing. Fix
these first.

### Identifying the 4 offenders

Get the exact list from Sonar:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=callibrity_mocapi&resolved=false&rules=java:S2699&ps=500" \
  | python3 -c "import json, sys; data = json.load(sys.stdin); [print(i['component'].replace('callibrity_mocapi:', '') + ':' + str(i['line']) + ' — ' + i['message'][:60]) for i in data['issues']]"
```

This produces the 4 file+line locations with the Sonar
messages.

### Two common failure modes

1. **Test invokes code but forgets to assert** — the author
   wrote `service.doThing(input);` and stopped. Fix: add
   `assertThat(...)` on the result or on observable state.

2. **Test asserts via a mock verification ONLY and Sonar
   doesn't recognize the Mockito `verify(...)` as an
   assertion** — Sonar's default config recognizes AssertJ,
   JUnit, Hamcrest, and Mockito `verify` as assertions. If
   Sonar is flagging a test with `verify(mock).something()`,
   something else is going on — possibly a Mockito
   `doReturn(...)` or `when(...)` that Sonar mistakes for
   the whole test body. Add an explicit `verify(...)` with
   `times(n)` or equivalent, or add an AssertJ assertion to
   make the intent unambiguous.

### Fix template

For each of the 4 tests:

1. **Read the test method** and understand what it's trying
   to verify.
2. **Add an assertion** on the thing the test should be
   checking:
   - If the method returns a value, assert on it
   - If the method has side effects on mocks, verify the
     expected interactions happened
   - If the method throws an expected exception, use
     `assertThatThrownBy(...)`
3. **Update the test name** if it doesn't already describe
   the expected behavior (e.g., rename
   `testDoThing` → `shouldDoThingWhenInputIsValid`).

## Acceptance criteria

- [ ] The exact 4 test cases flagged by Sonar for
      `java:S2699` are identified (run the query in the spec).
- [ ] Each test has at least one meaningful assertion added:
  - `assertThat(...)` — AssertJ on return values or state
  - `verify(mock).method(args)` — Mockito on expected
    interactions
  - `assertThatThrownBy(...)` — AssertJ on expected
    exceptions
- [ ] The added assertions are not pro forma — they must
      actually verify behavior that would fail if the code
      under test were broken.
- [ ] Test names are updated if they don't already describe
      the asserted behavior.
- [ ] After the changes, running the Sonar scan shows **zero**
      `java:S2699` issues.
- [ ] The 4 fixed tests continue to pass.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **S2699 is BLOCKER** — this spec is the highest priority
  in the Sonar cleanup series. An assertion-less test that
  passes is a *lie* about coverage, and that's the worst
  kind of test.
- **Don't just add `assertThat(true).isTrue()`** to silence
  Sonar. The point is meaningful verification, not silencing.
  If a test genuinely has nothing to assert (which would be
  suspicious), it should be deleted, not padded with fake
  assertions.
- **Mockito `verify` recognition**: Sonar's `java:S2699` rule
  has a `customAssertions` configuration that lists methods
  Sonar treats as assertions. By default, Mockito `verify` is
  recognized. If Sonar is still flagging a test that uses
  `verify`, check whether Sonar's rule configuration has
  been customized in the project's quality profile. If not,
  the test is calling `verify` in a way Sonar isn't parsing
  (e.g., `verify(mock).method()` without any chained
  assertion on the return value).
- **Sometimes the fix is to DELETE the test**: if a test was
  written as a smoke test ("does this method even exist?")
  and there's another test that covers the behavior
  meaningfully, the smoke test is redundant. Delete it and
  move on. Document the deletion in the commit message.
- **Commit granularity**: one commit per test file, or bundle
  all 4 tests into one commit if they're small.
