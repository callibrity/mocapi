# Remove debug System.out.println from DefaultMethodSchemaGeneratorTest

## What to build

There's a leftover debug `System.out.println` statement in
`DefaultMethodSchemaGeneratorTest.java:40` that pretty-prints
the generated schema to stdout on every test run:

```java
@Test
void testMethodSchemaGeneration() throws Exception {
  var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);
  var method = HelloTool.class.getMethod("sayHello", String.class);

  var schema = generator.generateInputSchema(new HelloTool(), method);
  System.out.println(schema.toPrettyString());  // ← line 40, leftover debug output
  assertThat(schema).isNotNull();
  assertThat(schema.get("properties")).isNotNull();
}
```

This pollutes the Maven build log with a JSON schema dump on
every `mvn verify` or CI run. It's noise — the test assertions
already cover what the author wanted to verify, and the
printed schema served as a one-time sanity check during
development that wasn't cleaned up.

Delete the line. No replacement needed — the two existing
`assertThat` assertions already verify the schema is non-null
and has a `properties` field.

### Also audit for related issues

While in the file, verify:
1. There are no other `System.out` / `System.err` calls in
   the same test class.
2. The two assertions are chained per spec 119's AssertJ
   style (if Ralph's already landed 119). If not, this spec
   doesn't need to touch them — spec 119 will.
3. Nothing else in the test looks like leftover debug code
   (commented-out lines, TODO markers, etc.).

### Reactor-wide grep

Already confirmed via grep that this is the **only**
`System.out.println` or `System.err.println` in the entire
reactor's `src` directories. No other cleanup needed.

## Acceptance criteria

- [ ] Line 40 (or wherever the `System.out.println(schema.toPrettyString());`
      call lives at the time Ralph reaches this spec) is
      deleted from
      `mocapi-core/src/test/java/com/callibrity/mocapi/tools/schema/DefaultMethodSchemaGeneratorTest.java`.
- [ ] The two `assertThat(...)` assertions on the following
      lines remain unchanged.
- [ ] Running `mvn -pl mocapi-core test` no longer prints a
      JSON schema to stdout during the
      `DefaultMethodSchemaGeneratorTest` execution.
- [ ] `grep -rn "System\.out\.println\|System\.err\.println" mocapi-*/src/test`
      returns zero matches after this change.
- [ ] `grep -rn "System\.out\.println\|System\.err\.println" mocapi-*/src/main`
      returns zero matches (already true as of this spec's
      writing — no production code prints to stdout).
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **This is a one-line delete**. Don't bundle anything else
  into the commit.
- **Commit message suggestion**:
  `test: remove leftover debug println from DefaultMethodSchemaGeneratorTest`
- **No new tests needed** — the existing assertions cover the
  schema generation behavior.
- **If Sonar flagged this as `java:S106`** ("Replace this use
  of System.out or System.err by a logger"), the issue
  clears automatically when the line is removed. No
  additional action needed.
