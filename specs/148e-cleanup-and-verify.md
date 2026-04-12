# Cleanup and verify: remove dead code, run conformance suite

## What to build

Final cleanup after the protocol/transport wiring is complete.
Remove any dead code left over from the refactor, ensure all
tests pass, and verify the MCP conformance suite.

### Cleanup tasks

- Remove any unused imports across the codebase (spotless will
  catch most of these).
- Delete any classes/methods that were only used by the old
  controller path and are now unreachable.
- Remove the temporary Odyssey dependency from `mocapi-protocol`
  if `McpSessionService` has been fully refactored to use
  `McpTransport` (if not, document the remaining dependency as
  tech debt for a follow-up spec).
- Update `mocapi-compat` if any test infrastructure references
  deleted types.
- Update example app READMEs if they reference `mocapi-core`
  (the module no longer exists).
- Run `spotless:apply` across the reactor.

### Verification

- `mvn clean verify` green across the full reactor.
- MCP conformance suite: 39/39 passing against `mocapi-compat`.
- Smoke-test at least one example app (in-memory or nats) to
  verify real HTTP works end-to-end.

## Acceptance criteria

- [ ] `git grep "mocapi-core"` returns zero results outside of
      `specs/done/` and git history.
- [ ] No unused imports (spotless clean).
- [ ] No unreachable classes or methods.
- [ ] `mvn clean verify` green.
- [ ] MCP conformance suite: 39/39.
- [ ] At least one example app boots and serves `tools/list`
      successfully via curl.

## Implementation notes

- This is intentionally a small spec — it's the "did we miss
  anything?" sweep after the major refactor.
- If the Odyssey dependency can't be removed from
  `mocapi-protocol` yet, add a `// TODO: spec 149 — extract
  stream management to remove Odyssey dep` comment in the pom.
