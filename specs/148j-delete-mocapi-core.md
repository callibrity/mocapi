# Delete mocapi-core

## What to build

Remove `mocapi-core` from the reactor. Everything it provided is
now in `mocapi-protocol` and `mocapi-transport-streamable-http`.

### What to do

1. Remove `mocapi-core` from the starter pom.
2. Remove `<module>mocapi-core</module>` from parent pom.
3. Delete the `mocapi-core/` directory.
4. Update any remaining references (mocapi-compat, examples,
   etc.) that depended on mocapi-core transitively — they should
   now get everything via the starter.
5. Run `git grep mocapi-core` and fix any remaining references
   outside of `specs/done/`.

### What NOT to do

- Do NOT rush. Verify the build is green after each step.

## Acceptance criteria

- [ ] `mocapi-core` directory is deleted.
- [ ] Parent pom no longer lists it as a module.
- [ ] Starter no longer depends on it.
- [ ] `git grep "mocapi-core"` returns zero results outside
      `specs/done/`.
- [ ] `mvn clean verify` passes across the full reactor.
- [ ] MCP conformance suite: 39/39.
- [ ] Example apps boot and serve tools/list via curl.
