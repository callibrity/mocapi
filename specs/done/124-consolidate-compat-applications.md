# Consolidate CompatApplication and CompatibilityApplication

## What to build

`mocapi-compat` currently has two `@SpringBootApplication` classes:

1. **`src/main/java/.../CompatibilityApplication.java`** — the
   "real" application. It has a `main(String[] args)` method that
   generates a random session-encryption master key at startup
   and wires it via
   `app.setDefaultProperties(Map.of("mocapi.session-encryption-master-key", encoded))`.
   This is the application that operators would actually run if
   they wanted a standalone compatibility demo.
2. **`src/test/java/.../CompatApplication.java`** — a trivial
   empty `@SpringBootApplication` class that exists solely so
   test classes can reference it as
   `@SpringBootTest(classes = CompatApplication.class)`.

The IT tests are currently split: some reference
`CompatibilityApplication.class`, others reference
`CompatApplication.class`. A grep over
`mocapi-compat/src/test/java`:

- ~27 files use `CompatibilityApplication.class` (the main one)
- ~13 files use `CompatApplication.class` (the test stub)

This is messy. Every test should use the same bootstrap class so
the Spring context under test matches what the production
application would boot. Consolidate on the real
`CompatibilityApplication` class and **delete** the test-only
`CompatApplication` stub.

### Required changes

1. **Delete `src/test/java/com/callibrity/mocapi/compat/CompatApplication.java`**.
2. **Update every `@SpringBootTest(classes = CompatApplication.class)`**
   to `@SpringBootTest(classes = CompatibilityApplication.class)`.
   Grep the test tree to find every occurrence and update in one
   pass:
   ```
   grep -rln "CompatApplication\b" mocapi-compat/src/test | \
     xargs sed -i '' 's/CompatApplication/CompatibilityApplication/g'
   ```
   (Or equivalent find/replace per your preferred tooling.)
3. **Remove any imports of `CompatApplication`** that were
   satisfied by the delete. The new `CompatibilityApplication`
   import may need to be added if the test was previously
   importing only the test stub.
4. **If any IT test currently relied on** `CompatApplication`
   providing a specific minimal context (e.g., a scenario that
   deliberately excluded some bean) **and** needed the exclusion
   to keep passing, that test should be updated to use
   `@TestConfiguration` + `@MockitoBean` to achieve the same
   effect against `CompatibilityApplication` — NOT by
   re-introducing a separate bootstrap class.

### Tools and test helpers

The user has noted that if any tools currently defined in
test-only code need to be moved into the production source to
support tests that previously used `CompatApplication`, **that's
acceptable**. Specifically:

- If there's a `ConformanceTools` or `CompatibilityTools` class
  whose methods or constants are referenced only by one IT test
  and happen to be declared in `src/test` to avoid "polluting"
  the main source: move them into `src/main` (alongside
  `CompatibilityApplication`) so the consolidated bootstrap can
  see them.
- Grep for `src/test` → `src/main` references (tests shouldn't
  be referencing `src/test` helper classes that won't exist
  anymore after the consolidation).
- If moving a test helper into main feels wrong (e.g., it
  contains mock setups), instead extract just the parts that
  the main app needs (example tool definitions, etc.) and leave
  the mock setup behind in the test tree.

### Master key handling

`CompatibilityApplication.main()` generates a random key at
startup via `SecureRandom`. For tests, this is fine — each test
run gets a random key, and the existing
`RandomMasterKeyInitializer` ApplicationContextInitializer
already handles the key-setup concern for tests that go through
the Spring test framework.

**Verify** that every IT test either uses
`RandomMasterKeyInitializer` (via
`@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)`)
or explicitly sets the master key via
`@TestPropertySource("mocapi.session-encryption-master-key=...")`.
If any test after consolidation starts failing because of a
missing master key, add the initializer to it.

## Acceptance criteria

- [ ] `src/test/java/com/callibrity/mocapi/compat/CompatApplication.java`
      does not exist.
- [ ] Grep for `CompatApplication\b` in
      `mocapi-compat/src/test/java` returns **zero** matches
      (every reference has been replaced with
      `CompatibilityApplication`).
- [ ] All IT tests in `mocapi-compat` use
      `@SpringBootTest(classes = CompatibilityApplication.class)`.
- [ ] Any test helper classes that had to be moved from
      `src/test` to `src/main` are placed alongside
      `CompatibilityApplication` in the
      `com.callibrity.mocapi.compat` (or
      `com.callibrity.mocapi.compat.conformance`) package.
- [ ] If any test helpers were moved to main, their javadoc
      explains that they're example tools for the compatibility
      scenarios, not production code.
- [ ] `mvn -pl mocapi-compat verify` passes — every IT test
      still runs and passes against
      `CompatibilityApplication.class`.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` test count is unchanged (or higher —
      never lower). The consolidation must not silently drop any
      tests.

## Implementation notes

- **Do the grep-and-replace in one commit** so the tree is in a
  consistent state. Leaving some tests on
  `CompatibilityApplication` and others on `CompatApplication`
  after a partial commit is confusing.
- **`CompatibilityApplication.main()` is package-private**
  (`static void main`). For Spring Boot to find and run it
  normally, it should be `public static void main`. Fix this as
  part of this spec — the current package-private visibility is
  likely an oversight (Sonar wouldn't have flagged it, but
  `mvn spring-boot:run` may not work without public).
- **Component scanning**: `CompatibilityApplication` is in the
  `com.callibrity.mocapi.compat` package; `@SpringBootApplication`
  defaults to scanning that package and its subpackages. Verify
  that every `@Component` / `@Service` / `@ToolService` / etc.
  used by the IT tests is reachable from that base package. If
  any test-only helper bean was in a different package, either
  move it under `com.callibrity.mocapi.compat` or explicitly
  import it via `@TestConfiguration`.
- **`CompatibilityTools` is the natural place for test-helper
  tools** that need to move from `src/test` to `src/main`. If
  such a class doesn't exist yet, create it alongside
  `CompatibilityApplication` and migrate helpers into it.
- **Don't introduce any production-code cruft** just to make
  tests happy. If moving something from `src/test` to `src/main`
  makes the main source noisier, reconsider whether the test
  could be restructured instead.
- **Commit suggested**: one commit covering all the grep-replace
  changes + any helper migrations. Small, focused, reviewable.
