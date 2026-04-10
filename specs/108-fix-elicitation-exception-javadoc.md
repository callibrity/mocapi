# Fix stale javadoc references in elicitation exceptions

## What to build

Several elicitation exception classes in
`mocapi-core/.../stream/elicitation/` have javadoc that references
types and methods that no longer exist after the cleanup that landed
earlier this session:

- `McpElicitationNotSupportedException` — its javadoc says
  "Thrown when `elicitForm()` is called but the client did not
  declare elicitation support." But `elicitForm()` was deleted in
  spec 100. The current public API is `elicit(String, Consumer<RequestedSchemaBuilder>)`
  (and after spec 106, also `elicit(String, Consumer, Class<T>)`).
- Any other exception or elicit-adjacent class whose javadoc mentions
  `elicitForm`, `ElicitationResult`, `ElicitationAction`, or
  `BeanElicitationResult` — all of those types are gone.

Grep the entire `mocapi-core/src/main` tree for these stale names in
comments and javadoc blocks, and update them to reference the current
API (`elicit`, `model.ElicitResult`, `model.ElicitAction`).

## Acceptance criteria

### Grep-based verification

- [ ] `grep -r "elicitForm" mocapi-core/src/main` returns **zero**
      matches (any match, including javadoc comments, is a failure).
- [ ] `grep -r "BeanElicitationResult" mocapi-core/src/main` returns
      **zero** matches.
- [ ] `grep -r "ElicitationResult" mocapi-core/src/main` returns
      **zero** matches (the core facade was deleted; the model type
      is `ElicitResult`, no "ation" infix).
- [ ] `grep -r "ElicitationAction" mocapi-core/src/main` returns
      **zero** matches (deleted in favor of `model.ElicitAction`).

### Javadoc content

- [ ] `McpElicitationNotSupportedException`'s class-level javadoc
      references the current `elicit(String, Consumer)` method, not
      `elicitForm()`.
- [ ] `McpElicitationException`'s class-level javadoc is accurate
      and doesn't reference deleted types.
- [ ] `McpElicitationTimeoutException`'s class-level javadoc is
      accurate.
- [ ] Any `@see`, `@link`, or `@throws` references in other
      elicitation-package files point at types that still exist.

### Build

- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.
- [ ] If javadoc generation is part of the verify cycle (maven-javadoc-plugin
      in release profile), it produces no warnings about broken
      references.

## Implementation notes

- **Scope is docs-only.** No code behavior changes. No test changes.
- **Grep commands to run during implementation**:
  ```
  grep -rn "elicitForm" mocapi-core/src/main
  grep -rn "BeanElicitationResult" mocapi-core/src/main
  grep -rn "ElicitationResult" mocapi-core/src/main
  grep -rn "ElicitationAction" mocapi-core/src/main
  ```
  All four should produce no output by the end of this spec.
- **Also check `mocapi-core/src/test`** for test-level javadoc (less
  important but worth a pass). Tests may still reference old names in
  comments or `@DisplayName` annotations.
- **Model module**: run the same grep over `mocapi-model/src/main`.
  Model classes shouldn't reference the core facade types at all,
  but double-check for hygiene.
- **README and other `.md` files in the project root**: out of scope
  for this spec. Documentation updates in user-facing markdown
  belong in a separate spec (see spec 109 if written).
- **Suggested commit message**:
  ```
  docs: fix stale javadoc references to deleted elicitation types
  ```
  One commit covering all the grep-cleanup edits.
